package dev.ashkite.limbusauto;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Screenshot template matching -> preview markers -> normal touch gestures.
 * Only attempts regular battles. No hard-coded screen coordinates or network access.
 */
public final class AutoService extends AccessibilityService {
    private static final String GAME="com.ProjectMoon.LimbusCompany";
    private static final long MAX_DURATION=15*60_000L;
    private static final int MAX_TAPS=40;
    private static final float GEAR_MIN=.75f, WIN_MIN=.72f;
    private static final int PHASE_WIN=0, PHASE_FIRE=1;
    private final Handler h=new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private LinearLayout overlay;
    private MarkerView marker;
    private TextView status;
    private TextView dragTitle;
    private Button collapseButton;
    private LinearLayout actionButtons;
    private WindowManager.LayoutParams panelParams;
    private boolean collapsed=false;
    private float downRawX,downRawY;
    private int downPanelX,downPanelY;
    private boolean game=false,running=false,capturing=false;
    private int phase=PHASE_WIN,confirmed=0,taps=0,failures=0;
    private long startTime=0,lastTouch=0;
    private float lastX=-1,lastY=-1,rememberGearX=-1,rememberGearY=-1;
    private int lastFrameWidth=0,lastFrameHeight=0;
    private float lastGearScore=0,lastWinScore=0;

    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private void say(String s){if(status!=null)status.setText(s);}
    private void schedule(long ms){h.removeCallbacks(loop);if(running)h.postDelayed(loop,ms);}
    private String gameRoot(){
        try{android.view.accessibility.AccessibilityNodeInfo root=getRootInActiveWindow();
            return root==null||root.getPackageName()==null?"":root.getPackageName().toString();}
        catch(Exception e){return "";}
    }
    private boolean gameForeground(){
        String pkg=gameRoot();
        if(GAME.equals(pkg)){game=true;return true;}
        if(!pkg.isEmpty() && !pkg.equals(getPackageName()) &&
           !pkg.equals("android") && !pkg.equals("com.android.systemui"))game=false;
        return game;
    }
    private Button button(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        b.setTextSize(15);b.setSingleLine(true);b.setMinWidth(dp(85));b.setMinHeight(dp(45));
        b.setPadding(dp(8),0,dp(8),0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1f);
        lp.setMargins(dp(3),dp(2),dp(3),dp(2));b.setLayoutParams(lp);
        b.setOnClickListener(v->action.run());return b;
    }
    private final class MarkerView extends View {
        private float xWin=-1,yWin=-1,xGear=-1,yGear=-1;
        MarkerView(){super(AutoService.this);setBackgroundColor(Color.TRANSPARENT);}
        void update(float wx,float wy,float gx,float gy){xWin=wx;yWin=wy;xGear=gx;yGear=gy;invalidate();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            // Screenshot/JNI coordinates are display-global, while this canvas is
            // local to an accessibility overlay and may have a display-cutout inset.
            // Correct for the actual window origin; do not alter gesture coordinates.
            int[] origin=new int[2];
            getLocationOnScreen(origin);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));
            if(xWin>=0){p.setColor(Color.GREEN);canvas.drawCircle(xWin-origin[0],yWin-origin[1],dp(18),p);}
            if(xGear>=0){p.setColor(Color.YELLOW);canvas.drawCircle(xGear-origin[0],yGear-origin[1],dp(22),p);}
        }
    }
    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        marker=new MarkerView();
        WindowManager.LayoutParams marks=new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT);
        marks.gravity=Gravity.TOP|Gravity.LEFT;wm.addView(marker,marks);marker.setVisibility(View.GONE);
        overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(8),dp(6),dp(8),dp(6));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.argb(238,25,29,39));bg.setCornerRadius(dp(12));
        overlay.setBackground(bg);
        // The title can be dragged and the controls can be collapsed without stopping a run.
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        dragTitle=new TextView(this);
        dragTitle.setText("DRAG  ·  LIMBUS AUTO 0.4.2");
        dragTitle.setTextColor(Color.WHITE);dragTitle.setTextSize(14f);
        dragTitle.setPadding(dp(8),dp(6),dp(8),dp(6));
        dragTitle.setGravity(Gravity.CENTER_VERTICAL);
        dragTitle.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downRawX=event.getRawX();downRawY=event.getRawY();
                    downPanelX=panelParams.x;downPanelY=panelParams.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    panelParams.x=downPanelX+Math.round(event.getRawX()-downRawX);
                    panelParams.y=downPanelY+Math.round(event.getRawY()-downRawY);
                    clampPanel();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    rememberPanel();
                    return true;
                default:return false;
            }
        });
        header.addView(dragTitle,new LinearLayout.LayoutParams(0,dp(42),1f));
        collapseButton=new Button(this);
        collapseButton.setAllCaps(false);collapseButton.setText("HIDE");
        collapseButton.setTextSize(12f);collapseButton.setMinHeight(0);collapseButton.setMinWidth(0);
        header.addView(collapseButton,new LinearLayout.LayoutParams(dp(72),dp(42)));
        collapseButton.setOnClickListener(v->toggleCompact());
        overlay.addView(header);
        status=new TextView(this);status.setText("READY - tap SCAN first");status.setTextSize(13);
        status.setTextColor(Color.WHITE);status.setGravity(Gravity.CENTER);status.setMaxLines(2);
        status.setPadding(dp(4),dp(4),dp(4),dp(4));overlay.addView(status);
        actionButtons=new LinearLayout(this);
        actionButtons.setOrientation(LinearLayout.HORIZONTAL);
        actionButtons.setLayoutParams(new LinearLayout.LayoutParams(dp(335),LinearLayout.LayoutParams.WRAP_CONTENT));
        actionButtons.addView(button("SCAN",this::preview));
        actionButtons.addView(button("AUTO",this::begin));
        actionButtons.addView(button("STOP",()->stop("User stopped")));
        overlay.addView(actionButtons);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT);
        // Top-left gravity makes offsets stable while moving the control window.
        lp.gravity=Gravity.TOP|Gravity.LEFT;
        DisplayMetrics dm=getResources().getDisplayMetrics();
        android.content.SharedPreferences prefs=getSharedPreferences("overlay_layout",MODE_PRIVATE);
        lp.x=prefs.getInt("x",Math.max(0,(dm.widthPixels-dp(360))/2));
        lp.y=prefs.getInt("y",dp(14));
        panelParams=lp;
        wm.addView(overlay,lp);
        overlay.post(this::clampPanel);
        if(prefs.getBoolean("collapsed",false))toggleCompact();
    }

    private void clampPanel(){
        if(wm==null||overlay==null||panelParams==null)return;
        DisplayMetrics dm=new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        // A visible, movable tab must stay reachable even if screen dimensions change.
        int width=overlay.getWidth()>0?overlay.getWidth():dp(collapsed?128:360);
        int height=overlay.getHeight()>0?overlay.getHeight():dp(collapsed?52:158);
        panelParams.x=Math.max(0,Math.min(panelParams.x,Math.max(0,dm.widthPixels-width)));
        panelParams.y=Math.max(0,Math.min(panelParams.y,Math.max(0,dm.heightPixels-height)));
        try{wm.updateViewLayout(overlay,panelParams);}catch(Exception ignored){}
    }
    private void rememberPanel(){
        if(panelParams!=null)getSharedPreferences("overlay_layout",MODE_PRIVATE)
            .edit().putInt("x",panelParams.x).putInt("y",panelParams.y).apply();
    }
    private void toggleCompact(){
        collapsed=!collapsed;
        // A collapsed panel is only a visual state. The automation loop keeps running.
        // OPEN then STOP remains available at all times.
        status.setVisibility(collapsed?View.GONE:View.VISIBLE);
        actionButtons.setVisibility(collapsed?View.GONE:View.VISIBLE);
        dragTitle.setText(collapsed?(running?"DRAG RUN":"DRAG"):"DRAG  ·  LIMBUS AUTO 0.4.2");
        dragTitle.setLayoutParams(collapsed
            ? new LinearLayout.LayoutParams(dp(72),dp(42))
            : new LinearLayout.LayoutParams(0,dp(42),1f));
        collapseButton.setText(collapsed?"OPEN":"HIDE");
        getSharedPreferences("overlay_layout",MODE_PRIVATE)
            .edit().putBoolean("collapsed",collapsed).apply();
        overlay.requestLayout();
        overlay.post(()->{clampPanel();rememberPanel();});
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e){
        if(e==null || e.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||e.getPackageName()==null)return;
        String pkg=e.getPackageName().toString();
        if(GAME.equals(pkg))game=true;
        else if(!pkg.equals(getPackageName())&&!pkg.equals("android")&&!pkg.equals("com.android.systemui")){
            if(!gameForeground()&&running)stop("Game changed");
        }
    }
    @Override public void onInterrupt(){stop("Accessibility interrupted");}
    @Override public void onDestroy(){
        stop("Service closed");
        if(marker!=null)try{wm.removeView(marker);}catch(Exception ignored){}
        if(overlay!=null)try{wm.removeView(overlay);}catch(Exception ignored){}
        super.onDestroy();
    }
    private void stop(String reason){
        running=false;h.removeCallbacks(loop);confirmed=0;lastX=-1;lastY=-1;
        if(collapsed && dragTitle!=null)dragTitle.setText("DRAG STOP");
        if(marker!=null)marker.setVisibility(View.GONE);
        say("STOP - "+reason);
    }
    private interface FrameCallback{void got(Bitmap frame);}
    private void screenshot(FrameCallback callback){
        if(capturing){callback.got(null);return;}
        capturing=true;
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
                @Override public void onSuccess(ScreenshotResult result){
                    capturing=false;Bitmap image=null;HardwareBuffer buf=result.getHardwareBuffer();
                    try{
                        Bitmap wrapped=Bitmap.wrapHardwareBuffer(buf,result.getColorSpace());
                        if(wrapped!=null)image=wrapped.copy(Bitmap.Config.ARGB_8888,false);
                    }catch(Exception ignored){}finally{buf.close();}
                    callback.got(image);
                }
                @Override public void onFailure(int code){capturing=false;callback.got(null);}
            });
        }catch(Exception e){capturing=false;callback.got(null);}
    }
    private static final class Match{
        float gx,gy,wx,wy,gs,ws;
        int w,h;
        boolean strong(){return gs>=GEAR_MIN&&ws>=WIN_MIN;}
        boolean plausible(){return gs>=.55f&&ws>=.62f;}
        String brief(){return "GEAR "+Math.round(gs*100)+"% / WIN "+Math.round(ws*100)+"%";}
    }
    private String diagnosis(Bitmap frame){
        if(!NativeVision.AVAILABLE)return "Rust native library unavailable";
        if(VisionTemplates.GEAR.length!=29*32)return "Gear template invalid";
        if(VisionTemplates.WIN.length!=31*15)return "Win template invalid";
        int w=frame.getWidth(),height=frame.getHeight();
        if(w<=height*1.2f)return "Portrait capture "+w+"x"+height;
        int scaledW=Math.round(w*240f/height);
        if(scaledW<320||scaledW>1200)return "Unsupported ratio "+w+"x"+height;
        return "Vision returned no coordinates ("+w+"x"+height+")";
    }
    private Match match(Bitmap frame){
        if(!NativeVision.AVAILABLE||VisionTemplates.GEAR.length!=29*32||
            VisionTemplates.WIN.length!=31*15)return null;
        int w=frame.getWidth(),height=frame.getHeight();
        if(w<=height*1.2f)return null;
        int scaledW=Math.round(w*240f/height);
        if(scaledW<320||scaledW>1200)return null;
        Bitmap small=Bitmap.createScaledBitmap(frame,scaledW,240,true);
        int[] pixels=new int[scaledW*240];small.getPixels(pixels,0,scaledW,0,0,scaledW,240);
        if(small!=frame)small.recycle();
        float[] result=NativeVision.findPair(pixels,scaledW,240,VisionTemplates.GEAR,VisionTemplates.WIN);
        if(result==null||result.length!=6)return null;
        Match m=new Match();m.w=w;m.h=height;
        m.gx=result[0]*w/scaledW;m.gy=result[1]*height/240f;
        m.wx=result[2]*w/scaledW;m.wy=result[3]*height/240f;
        m.gs=result[4];m.ws=result[5];return m;
    }
    private void preview(){
        if(collapsed){say("Open controls first");return;}
        if(running){say("STOP before SCAN");return;}
        if(!gameForeground()){say("Open Limbus battle first");return;}
        if(marker!=null)marker.setVisibility(View.GONE);
        say("Scanning icons...");
        h.postDelayed(()->screenshot(frame->{
            if(frame==null){say("Screenshot unavailable");return;}
            try{
                Match m=match(frame);
                if(m==null){say(diagnosis(frame));return;}
                say(m.brief()+(m.strong()?" - ready":" - uncertain"));
                android.util.Log.d("LimbusAuto", "SCAN match GEAR="+m.gx+","+m.gy+
                    " WIN="+m.wx+","+m.wy+" score="+m.brief());
                if(marker!=null){
                    marker.update(m.ws>=WIN_MIN?m.wx:-1,m.ws>=WIN_MIN?m.wy:-1,
                        m.gs>=GEAR_MIN?m.gx:-1,m.gs>=GEAR_MIN?m.gy:-1);
                    marker.setVisibility(View.VISIBLE);
                    h.postDelayed(()->{if(!running && marker!=null)marker.setVisibility(View.GONE);},7000);
                }
            }catch(Exception e){say("Scanner error");}
            finally{frame.recycle();}
        }),350);
    }
    private void begin(){
        if(collapsed){say("Open controls first");return;}
        if(!NativeVision.AVAILABLE){say("Rust unavailable");return;}
        if(!gameForeground()){say("Open Limbus battle first");return;}
        if(capturing){say("Screenshot busy");return;}
        if(marker!=null)marker.setVisibility(View.GONE);
        phase=PHASE_WIN;confirmed=0;taps=0;failures=0;lastX=-1;lastY=-1;
        startTime=SystemClock.elapsedRealtime();lastTouch=0;running=true;
        if(collapsed && dragTitle!=null)dragTitle.setText("DRAG RUN");
        say("AUTO - finding WIN + GEAR");schedule(500);
    }
    private final Runnable loop=()->{
        if(!running)return;
        // Keep scanning and tapping even while the controls are collapsed.
        if(SystemClock.elapsedRealtime()-startTime>MAX_DURATION||taps>=MAX_TAPS){stop("Safety limit");return;}
        if(!gameForeground()){stop("Game not foreground");return;}
        if(marker!=null)marker.setVisibility(View.GONE);
        screenshot(frame->{
            if(frame==null){stop("Screenshot failed");return;}
            try{
                Match m=match(frame);
                if(m==null){stop(diagnosis(frame));return;}
                handle(m);
            }catch(Exception e){stop("Vision exception");}
            finally{frame.recycle();}
        });
    };
    private void handle(Match m){
        float x=phase==PHASE_WIN?m.wx:m.gx;
        float y=phase==PHASE_WIN?m.wy:m.gy;
        boolean confident=phase==PHASE_WIN?m.strong():
            // A ready button may change its appearance, but must remain next to Win Rate.
            m.ws>=.62f && m.gs>=.52f &&
            Math.abs(m.gx-rememberGearX)<=m.h*.042f &&
            Math.abs(m.gy-rememberGearY)<=m.h*.042f;
        // Only one slow, bounded action after two aligned screenshots.
        if(!confident){
            confirmed=0;lastX=-1;failures++;
            if(failures>=15){stop("Icon not confidently matched - "+m.brief());return;}
            say("SCAN "+m.brief()+" ("+failures+")");
            schedule(2000);return;
        }
        failures=0;
        if(lastX>=0&&Math.abs(lastX-x)<m.h*.012f&&Math.abs(lastY-y)<m.h*.012f)confirmed++;
        else confirmed=1;
        lastX=x;lastY=y;
        if(confirmed<2){say("CHECK "+m.brief());schedule(900);return;}
        confirmed=0;lastX=-1;lastY=-1;
        if(!gameForeground()){stop("Game not foreground");return;}
        if(phase==PHASE_WIN){
            rememberGearX=m.gx;rememberGearY=m.gy;
        }
        Path p=new Path();p.moveTo(x,y);
        GestureDescription g=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(p,0,110)).build();
        final int phaseAtTouch=phase;
        boolean accepted=dispatchGesture(g,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription done){
                if(!running)return;
                taps++;lastTouch=SystemClock.elapsedRealtime();
                phase=phaseAtTouch==PHASE_WIN?PHASE_FIRE:PHASE_WIN;
                say((phaseAtTouch==PHASE_WIN?"WIN":"FIRE")+" tapped ("+taps+")");
                schedule(phaseAtTouch==PHASE_WIN?1800:8500);
            }
            @Override public void onCancelled(GestureDescription done){stop("Touch cancelled");}
        },h);
        if(!accepted)stop("Touch rejected");
    }
}
