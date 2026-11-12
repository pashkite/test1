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
    private Button emergencyStop;
    private LinearLayout actionButtons;
    private WindowManager.LayoutParams panelParams;
    private boolean collapsed=false;
    private float downRawX,downRawY;
    private int downPanelX,downPanelY;
    private boolean game=false,running=false,capturing=false;
    private int phase=PHASE_WIN,confirmed=0,taps=0,failures=0;
    private boolean waitingForIdle=false;
    private boolean scannedReady=false;
    private long waitingSince=0L,phaseSince=0L;
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
        dragTitle.setText("DRAG  ·  LIMBUS AUTO 0.5.1");
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
        emergencyStop=new Button(this);
        emergencyStop.setText("STOP");emergencyStop.setTextSize(11f);emergencyStop.setAllCaps(false);
        emergencyStop.setMinWidth(0);emergencyStop.setMinHeight(0);
        emergencyStop.setVisibility(View.GONE);
        emergencyStop.setOnClickListener(v->stop("Emergency stop"));
        header.addView(emergencyStop,new LinearLayout.LayoutParams(dp(72),dp(42)));
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
        dragTitle.setText(collapsed?(running?"RUN":"DRAG"):"DRAG  ·  LIMBUS AUTO 0.5.1");
        emergencyStop.setVisibility(collapsed&&running?View.VISIBLE:View.GONE);
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
        if(collapsed && dragTitle!=null)dragTitle.setText("DRAG");
        if(emergencyStop!=null)emergencyStop.setVisibility(View.GONE);
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

    private static final int IDLE=0,READY=1,UNKNOWN=2;
    private static final class Match {
        float wx,wy,gx,gy,idleScore,readyScore,orange;
        int state=UNKNOWN,w,h;
        boolean valid(){return state!=UNKNOWN;}
        String brief(){
            String what=state==IDLE?"IDLE":state==READY?"READY":"UNCERTAIN";
            return what+" I"+Math.round(idleScore*100)+" R"+Math.round(readyScore*100)+
                " O"+Math.round(orange*100)+
                " ("+Math.round(wx)+","+Math.round(wy)+")";
        }
    }
    private String diagnosis(Bitmap frame){
        if(!NativeVision.AVAILABLE)return "Native library missing";
        if(VisionTemplates.IDLE.length!=435||VisionTemplates.SELECTED.length!=435)
            return "Template file unavailable";
        int w=frame.getWidth(),h=frame.getHeight();
        if(w<=h*1.2f)return "Portrait frame "+w+"x"+h;
        int sw=Math.round(w*240f/h);
        if(sw<320||sw>1200)return "Ratio not supported "+w+"x"+h;
        return "Panel not found "+w+"x"+h;
    }
    private Match match(Bitmap frame){
        if(!NativeVision.AVAILABLE||VisionTemplates.IDLE.length!=435||
            VisionTemplates.SELECTED.length!=435)return null;
        int w=frame.getWidth(),ht=frame.getHeight();
        if(w<=ht*1.2f)return null;
        int sw=Math.round(w*240f/ht);
        if(sw<320||sw>1200)return null;
        Bitmap small=Bitmap.createScaledBitmap(frame,sw,240,true);
        int[] pixels=new int[sw*240];
        small.getPixels(pixels,0,sw,0,0,sw,240);
        if(small!=frame)small.recycle();
        float[] r=NativeVision.locateControls(pixels,sw,240,
            VisionTemplates.IDLE,VisionTemplates.SELECTED);
        if(r==null||r.length!=7||r[0]<0)return null;
        Match m=new Match();m.w=w;m.h=ht;
        m.wx=r[0]*w/sw;m.wy=r[1]*ht/240f;
        m.gx=r[2]*w/sw;m.gy=r[3]*ht/240f;
        m.idleScore=r[4];m.readyScore=r[5];m.orange=r[6];
        // Pattern score varies with UI scaling. Gear color independently
        // confirms whether the screen shows the highlighted START state.
        if(m.orange<=.22f && m.idleScore>=.73f &&
           m.idleScore>=m.readyScore-.07f)m.state=IDLE;
        if(m.orange>=.48f && m.readyScore>=.73f &&
           m.readyScore>=m.idleScore-.07f)m.state=READY;
        return m;
    }
    private void preview(){
        if(running){say("STOP before SCAN");return;}
        if(!gameForeground()){say("Open Limbus battle");return;}
        scannedReady=false;
        if(marker!=null)marker.setVisibility(View.GONE);
        say("Scanning idle/selected...");
        h.postDelayed(()->screenshot(frame->{
            if(frame==null){say("Screenshot unavailable");return;}
            try{
                Match m=match(frame);
                if(m==null){scannedReady=false;say(diagnosis(frame));return;}
                scannedReady=m.valid();
                say(m.brief());
                if(marker!=null){
                    marker.update(m.valid()?m.wx:-1,m.valid()?m.wy:-1,
                        m.valid()?m.gx:-1,m.valid()?m.gy:-1);
                    marker.setVisibility(View.VISIBLE);
                    h.postDelayed(()->{if(!running&&marker!=null)
                        marker.setVisibility(View.GONE);},7000);
                }
            }catch(Exception e){say("Scan failed");}
            finally{frame.recycle();}
        }),350);
    }
    private void begin(){
        if(!NativeVision.AVAILABLE){say("Rust library not loaded");return;}
        if(!scannedReady){say("SCAN first - check marker positions");return;}
        if(!gameForeground()){say("Open Limbus battle");return;}
        if(capturing){say("Capture busy");return;}
        if(marker!=null)marker.setVisibility(View.GONE);
        running=true;phase=PHASE_WIN;confirmed=0;taps=0;failures=0;
        lastX=-1;lastY=-1;waitingForIdle=false;
        startTime=phaseSince=SystemClock.elapsedRealtime();
        if(collapsed){dragTitle.setText("RUN");emergencyStop.setVisibility(View.VISIBLE);}
        say("AUTO - looking for battle controls");schedule(500);
    }
    private final Runnable loop=()->{
        if(!running)return;
        if(SystemClock.elapsedRealtime()-startTime>MAX_DURATION||taps>=MAX_TAPS){
            stop("Safety limit");return;
        }
        if(!gameForeground()){stop("Game not foreground");return;}
        if(marker!=null)marker.setVisibility(View.GONE);
        screenshot(frame->{
            if(frame==null){stop("Screenshot failed");return;}
            try{
                Match m=match(frame);
                if(m==null){failures++;if(failures>=12)stop(diagnosis(frame));
                    else schedule(1700);
                    return;
                }
                handle(m);
            }catch(Exception e){stop("Detection failed");}
            finally{frame.recycle();}
        });
    };
    private void handle(Match m){
        long now=SystemClock.elapsedRealtime();
        if(m.state==UNKNOWN){
            confirmed=0;failures++;
            if(failures>=12){stop("Uncertain button state - "+m.brief());return;}
            say("WAIT "+m.brief());schedule(1900);return;
        }
        // Never send another START gesture until the previous START state has disappeared.
        if(waitingForIdle){
            if(m.state==IDLE){waitingForIdle=false;phase=PHASE_WIN;phaseSince=now;}
            else {
                if(now-waitingSince>35000){stop("Still showing START");return;}
                say("WAIT next turn");schedule(1800);return;
            }
        }
        if(phase==PHASE_WIN && m.state==READY){
            // Someone may have selected win rate manually before pressing AUTO.
            phase=PHASE_FIRE;phaseSince=now;confirmed=0;lastX=-1;lastY=-1;
        }
        if(phase==PHASE_FIRE&&m.state==IDLE){
            if(now-phaseSince>6500){stop("Win selection not confirmed");return;}
            say("WAIT win selection");schedule(950);return;
        }
        if(phase==PHASE_WIN&&m.state!=IDLE || phase==PHASE_FIRE&&m.state!=READY){
            stop("Unexpected state");return;
        }
        float x=phase==PHASE_WIN?m.wx:m.gx;
        float y=phase==PHASE_WIN?m.wy:m.gy;
        if(x<0||y<0||x>=m.w||y>=m.h){stop("Out-of-range target");return;}
        failures=0;
        if(lastX>=0&&Math.abs(lastX-x)<m.h*.012f&&
           Math.abs(lastY-y)<m.h*.012f)confirmed++;
        else confirmed=1;
        lastX=x;lastY=y;
        if(confirmed<2){say("CHECK "+m.brief());schedule(900);return;}
        confirmed=0;lastX=-1;lastY=-1;
        if(!gameForeground()){stop("Game not foreground");return;}
        Path p=new Path();p.moveTo(x,y);
        GestureDescription g=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(p,0,110)).build();
        final int clicked=phase;
        if(!dispatchGesture(g,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription done){
                if(!running)return;
                taps++;lastTouch=SystemClock.elapsedRealtime();
                if(clicked==PHASE_WIN){
                    phase=PHASE_FIRE;phaseSince=lastTouch;
                }else{
                    phase=PHASE_WIN;waitingForIdle=true;waitingSince=lastTouch;
                }
                say((clicked==PHASE_WIN?"WIN":"START")+" tapped "+taps);
                schedule(clicked==PHASE_WIN?1550:8500);
            }
            @Override public void onCancelled(GestureDescription done){stop("Gesture cancelled");}
        },h))stop("Gesture rejected");
    }
}
