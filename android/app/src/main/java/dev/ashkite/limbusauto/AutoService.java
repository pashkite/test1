package dev.ashkite.limbusauto;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Path;
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
    private TextRecognizer korean, latin;
    private boolean gameVisible=false, running=false, captureBusy=false, recognizeBusy=false;
    private int phase=PHASE_WIN, taps=0, miss=0, steady=0, clearSteady=0;
    private long begun=0, stateSince=0;
    private Rect previousBox=null;

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
        if(!gameVisible){stop("게임이 화면에 없음");return;}
        if(taps>=MAX_TAPS||SystemClock.elapsedRealtime()-begun>=MAX_MILLIS){stop("안전 제한");return;}
        // The overlay must not appear in the OCR screenshot.
        if(overlay!=null)overlay.setVisibility(View.INVISIBLE);
        h.postDelayed(()->screenshot(frame->{
            if(overlay!=null)overlay.setVisibility(gameVisible?View.VISIBLE:View.GONE);
            if(!running){if(frame!=null)frame.recycle();return;}
            if(frame==null){stop("화면 캡처 실패");return;}
            recognize(frame);
        }),250);
    };
    private interface FrameCallback { void accept(Bitmap frame); }
    private Button button(String title, Runnable click) {
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(11);
        b.setMinimumWidth(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinHeight(0);b.setPadding(4,0,4,0);
        b.setOnClickListener(v->click.run());return b;
    }
    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        korean=TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
        latin=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);overlay.setPadding(5,5,5,5);
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.argb(230,24,30,38));bg.setCornerRadius(14);overlay.setBackground(bg);
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(11);status.setText("준비 / 수동 등록 불필요");
        overlay.addView(status);
        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(button("자동 시작",this::begin));
        buttons.addView(button("정지",()->stop("사용자 중지")));
        overlay.addView(buttons);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.END;lp.x=12;lp.y=90;
        wm.addView(overlay,lp);overlay.setVisibility(View.GONE);
        try{
            if(getRootInActiveWindow()!=null&&getRootInActiveWindow().getPackageName()!=null){
                gameVisible=GAME.contentEquals(getRootInActiveWindow().getPackageName());
                overlay.setVisibility(gameVisible?View.VISIBLE:View.GONE);
            }
        }catch(Exception ignored){}
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null||event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||event.getPackageName()==null)return;
        String pkg=event.getPackageName().toString();
        if(pkg.equals(getPackageName()))return;
        boolean active=GAME.equals(pkg);
        if(active!=gameVisible){
            gameVisible=active;
            if(overlay!=null)overlay.setVisibility(active?View.VISIBLE:View.GONE);
            if(!active&&running)stop("다른 앱 전환");
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
        if(!NativeVision.AVAILABLE){post("Rust 라이브러리 없음");return;}
        if(!gameVisible){post("게임 화면에서 실행해 줘");return;}
        if(captureBusy||recognizeBusy){post("화면 인식 중");return;}
        phase=PHASE_WIN;taps=0;miss=0;steady=0;clearSteady=0;previousBox=null;
        begun=stateSince=SystemClock.elapsedRealtime();running=true;
        post("자동 승률 탐색");next(500);
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
            if(clearSteady>=2){stop("전투 종료 감지");return;}
        }else clearSteady=0;
        long now=SystemClock.elapsedRealtime();
        long elapsed=now-stateSince;
        if(elapsed>55_000L){stop("버튼 미발견 (55초)");return;}
        Candidate c=choose(found,phase);
        if(c==null){
            steady=0;previousBox=null;miss++;
            post((phase==PHASE_WIN?"승률":"전투 시작")+" 탐색 ("+miss+")");
            next(1750);return;
        }
        miss=0;
        if(previousBox!=null&&Math.abs(previousBox.centerX()-c.box.centerX())<30 &&
            Math.abs(previousBox.centerY()-c.box.centerY())<30)steady++;
        else steady=1;
        previousBox=new Rect(c.box);
        if(steady<2){post("인식 재확인: "+c.label);next(950);return;}
        steady=0;previousBox=null;
        if(!gameVisible){stop("게임이 화면에 없음");return;}
        Path tap=new Path();tap.moveTo(c.box.centerX(),c.box.centerY());
        GestureDescription gesture=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(tap,0,95)).build();
        final int clicked=phase;
        boolean accepted=dispatchGesture(gesture,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription done){
                if(!running)return;
                taps++;stateSince=SystemClock.elapsedRealtime();
                phase=clicked==PHASE_WIN?PHASE_START:PHASE_WIN;
                post((clicked==PHASE_WIN?"승률":"전투 시작")+" 누름 · "+taps+"/"+MAX_TAPS);
                next(clicked==PHASE_WIN?1400:7000);
            }
            @Override public void onCancelled(GestureDescription done){stop("터치 취소");}
        },h);
        if(!accepted)stop("터치 요청 거부");
    }
}
