package dev.ashkite.limbusauto;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.ArrayList;
import java.util.List;

/**
 * Experimental OCR-driven battle stepper. No screen coordinates are pre-registered.
 * Fails closed on unrecognized/missing text and on focus loss.
 */
public final class AutoService extends AccessibilityService {
    private static final String GAME="com.ProjectMoon.LimbusCompany";
    private static final long MAX_MILLIS=15*60_000L;
    private static final int MAX_TAPS=40;
    private static final int PHASE_WIN=1, PHASE_START=2;
    private final Handler h=new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private LinearLayout overlay;
    private TextView status;
    private TextView heading;
    private static final String UI_READY="READY - open Limbus battle";
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    private TextRecognizer korean, latin;
    private boolean gameVisible=false, running=false, captureBusy=false, recognizeBusy=false;
    private int phase=PHASE_WIN, taps=0, miss=0, steady=0, clearSteady=0;
    private long begun=0, stateSince=0;
    private Rect previousBox=null;
    private String lastForeground="대기 중";
    private long lastGameEvent=0L;
    private boolean isSystemPackage(String pkg) {
        return pkg.equals("android") || pkg.equals("com.android.systemui")
            || pkg.startsWith("com.google.android.inputmethod")
            || pkg.startsWith("com.samsung.android.honeyboard");
    }
    private String activeRootPackage() {
        try {
            android.view.accessibility.AccessibilityNodeInfo root=getRootInActiveWindow();
            if(root==null || root.getPackageName()==null) return null;
            return root.getPackageName().toString();
        } catch(Exception e){ return null; }
    }
    // A system dialog, game overlay, or IME is not proof that Limbus left the foreground.
    private boolean verifyGame() {
        String root=activeRootPackage();
        if(GAME.equals(root)) {
            gameVisible=true;
            lastGameEvent=SystemClock.elapsedRealtime();
            lastForeground=GAME;
            return true;
        }
        if(root!=null && !getPackageName().equals(root) && !isSystemPackage(root)) {
            gameVisible=false;
            lastForeground=root;
            return false;
        }
        return gameVisible;
    }

    private static final class Candidate {
        final int type;
        final Rect box;
        final String label;
        Candidate(int type, Rect box, String label) {this.type=type;this.box=new Rect(box);this.label=label;}
    }
    private void post(String message) { if(status!=null)status.setText(message); }
    private void next(long millis) { h.removeCallbacks(loop);if(running)h.postDelayed(loop,millis); }
    private final Runnable loop=()->{
        if(!running)return;
        if(!verifyGame()){stop("Game not detected");return;}
        if(taps>=MAX_TAPS||SystemClock.elapsedRealtime()-begun>=MAX_MILLIS){stop("Safety limit");return;}
        // Overlay stays visible; OCR candidates for battle controls must be in lower half.
        // This avoids an apparent missing-button problem while scanning.
        h.postDelayed(()->screenshot(frame->{
            if(!running){if(frame!=null)frame.recycle();return;}
            if(frame==null){stop("Screenshot unavailable");return;}
            recognize(frame);
        }),250);
    };
    private interface FrameCallback { void accept(Bitmap frame); }
    private Button button(String title, Runnable click) {
        Button b=new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(15f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.BLACK);
        b.setSingleLine(true);
        b.setMinimumWidth(dp(104));
        b.setMinWidth(dp(104));
        b.setMinHeight(dp(44));
        b.setMinimumHeight(dp(44));
        b.setPadding(dp(12),dp(5),dp(12),dp(5));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1f);
        lp.setMargins(dp(4),dp(4),dp(4),dp(4));
        b.setLayoutParams(lp);
        b.setOnClickListener(v->click.run());
        return b;
    }
    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        korean=TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
        latin=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        overlay=new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(10),dp(6),dp(10),dp(6));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(Color.argb(242,24,30,38));
        bg.setCornerRadius(dp(14));
        overlay.setBackground(bg);
        heading=new TextView(this);
        heading.setText("LIMBUS AUTO");
        heading.setTextColor(Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setTextSize(14f);
        heading.setGravity(Gravity.CENTER);
        overlay.addView(heading);
        status=new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13f);
        status.setTypeface(Typeface.DEFAULT);
        status.setGravity(Gravity.CENTER);
        status.setMaxLines(2);
        status.setText(UI_READY);
        status.setPadding(dp(8),dp(5),dp(8),dp(5));
        overlay.addView(status);
        LinearLayout buttons=new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_HORIZONTAL);
        // Fix a real width so weighted buttons cannot collapse in WRAP_CONTENT overlay.
        buttons.setLayoutParams(new LinearLayout.LayoutParams(dp(290),LinearLayout.LayoutParams.WRAP_CONTENT));
        buttons.addView(button("AUTO ON",this::begin));
        buttons.addView(button("STOP",()->stop("User stopped")));
        overlay.addView(buttons);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
        lp.x=0;lp.y=dp(18);
        wm.addView(overlay,lp);overlay.setVisibility(View.VISIBLE);
        try{
            if(getRootInActiveWindow()!=null&&getRootInActiveWindow().getPackageName()!=null){
                gameVisible=GAME.contentEquals(getRootInActiveWindow().getPackageName());
                if(gameVisible) lastGameEvent=SystemClock.elapsedRealtime();
                post(gameVisible?"READY - GAME FOUND":UI_READY);
            }
        }catch(Exception ignored){}
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null||event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||event.getPackageName()==null)return;
        String pkg=event.getPackageName().toString();
        if(pkg.equals(getPackageName()))return;
        if(GAME.equals(pkg)){
            gameVisible=true;lastGameEvent=SystemClock.elapsedRealtime();lastForeground=pkg;
            if(!running)post("READY - GAME FOUND");
            return;
        }
        // Only an actual non-system foreground app should invalidate the game state.
        // Never hide the control window on a mere window-state event.
        if(isSystemPackage(pkg))return;
        if(!verifyGame()){
            lastForeground=pkg;
            if(running)stop("Game left foreground");
            else post(UI_READY);
        }
    }
    @Override public void onInterrupt(){stop("서비스 중단");}
    @Override public void onDestroy(){
        stop("서비스 종료");
        if(overlay!=null&&wm!=null)try{wm.removeView(overlay);}catch(Exception ignored){}
        if(korean!=null)korean.close();if(latin!=null)latin.close();
        super.onDestroy();
    }
    private void stop(String reason){
        running=false;h.removeCallbacks(loop);previousBox=null;steady=0;clearSteady=0;
        post("정지: "+reason);
    }
    private void begin(){
        if(!NativeVision.AVAILABLE){post("ERROR - Rust unavailable");return;}
        if(!verifyGame()){
            post("WAIT - game not detected");return;
        }
        if(captureBusy||recognizeBusy){post("WAIT - screen busy");return;}
        phase=PHASE_WIN;taps=0;miss=0;steady=0;clearSteady=0;previousBox=null;
        begun=stateSince=SystemClock.elapsedRealtime();running=true;
        post("SCAN - win rate");next(500);
    }
    private void screenshot(FrameCallback callback){
        if(captureBusy){callback.accept(null);return;}
        captureBusy=true;
        try{
            takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
                @Override public void onSuccess(ScreenshotResult result){
                    captureBusy=false;
                    HardwareBuffer buffer=result.getHardwareBuffer();
                    Bitmap frame=null;
                    try{
                        Bitmap wrapped=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());
                        if(wrapped!=null)frame=wrapped.copy(Bitmap.Config.ARGB_8888,false);
                    }catch(Exception ignored){}finally{buffer.close();}
                    callback.accept(frame);
                }
                @Override public void onFailure(int code){captureBusy=false;callback.accept(null);}
            });
        }catch(Exception e){captureBusy=false;callback.accept(null);}
    }
    private void recognize(Bitmap frame){
        if(recognizeBusy){frame.recycle();next(1000);return;}
        recognizeBusy=true;
        InputImage input=InputImage.fromBitmap(frame,0);
        Task<Text> kr=korean.process(input);
        Task<Text> en=latin.process(input);
        Tasks.whenAll(kr,en).addOnCompleteListener(task->{
            recognizeBusy=false;
            if(!running){frame.recycle();return;}
            try{
                List<Candidate> found=new ArrayList<>();
                if(kr.isSuccessful())addCandidates(found,kr.getResult(),frame.getWidth(),frame.getHeight());
                if(en.isSuccessful())addCandidates(found,en.getResult(),frame.getWidth(),frame.getHeight());
                if(!kr.isSuccessful()&&!en.isSuccessful()){stop("글자 인식 실패");return;}
                decide(found);
            }catch(Exception ex){stop("인식 오류");}
            finally{frame.recycle();}
        });
    }
    private void addCandidates(List<Candidate> matches,Text text,int width,int height){
        for(Text.TextBlock block:text.getTextBlocks()){
            for(Text.Line line:block.getLines()){
                maybeAdd(matches,line.getText(),line.getBoundingBox(),width,height);
                for(Text.Element element:line.getElements()){
                    maybeAdd(matches,element.getText(),element.getBoundingBox(),width,height);
                }
            }
        }
    }
    private void maybeAdd(List<Candidate> found,String label,Rect box,int width,int height){
        if(box==null||label==null)return;
        int kind=NativeVision.classify(label);
        if(kind==0)return;
        if(box.width()<12||box.height()<9||box.centerX()<0||box.centerX()>width||
           box.centerY()<0||box.centerY()>height||box.width()>width*0.50f||box.height()>height*0.24f)return;
        // Restrict interactive controls to the lower portion of the battle screen.
        // Clear/VICTORY may appear in the upper portion.
        if(kind==1 && box.centerY()<height*0.48f)return;
        if(kind==2 && (box.centerY()<height*0.42f||box.centerX()<width*0.40f))return;
        found.add(new Candidate(kind,box,label));
    }
    private Candidate choose(List<Candidate> found,int target){
        Candidate best=null;
        for(Candidate c:found){
            if(c.type!=target)continue;
            if(best==null||c.box.centerY()>best.box.centerY())best=c;
        }
        return best;
    }
    private void decide(List<Candidate> found){
        if(choose(found,3)!=null){
            clearSteady++;
            if(clearSteady>=2){stop("Battle ended");return;}
        }else clearSteady=0;
        long now=SystemClock.elapsedRealtime();
        long elapsed=now-stateSince;
        if(elapsed>55_000L){stop("No recognized button - 55s");return;}
        Candidate c=choose(found,phase);
        if(c==null){
            steady=0;previousBox=null;miss++;
            post((phase==PHASE_WIN?"SCAN WIN ":"SCAN START ")+miss);
            next(1750);return;
        }
        miss=0;
        if(previousBox!=null&&Math.abs(previousBox.centerX()-c.box.centerX())<30 &&
            Math.abs(previousBox.centerY()-c.box.centerY())<30)steady++;
        else steady=1;
        previousBox=new Rect(c.box);
        if(steady<2){post("CONFIRM "+c.label);next(950);return;}
        steady=0;previousBox=null;
        if(!verifyGame()){stop("Game not foreground");return;}
        Path tap=new Path();tap.moveTo(c.box.centerX(),c.box.centerY());
        GestureDescription gesture=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(tap,0,95)).build();
        final int clicked=phase;
        boolean accepted=dispatchGesture(gesture,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription done){
                if(!running)return;
                taps++;stateSince=SystemClock.elapsedRealtime();
                phase=clicked==PHASE_WIN?PHASE_START:PHASE_WIN;
                post((clicked==PHASE_WIN?"WIN TAP ":"START TAP ")+taps+"/"+MAX_TAPS);
                next(clicked==PHASE_WIN?1400:7000);
            }
            @Override public void onCancelled(GestureDescription done){stop("Gesture cancelled");}
        },h);
        if(!accepted)stop("Gesture refused");
    }
}
