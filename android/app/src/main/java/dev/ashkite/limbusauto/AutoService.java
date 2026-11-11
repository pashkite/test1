package dev.ashkite.limbusauto;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Path;
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
import java.io.File;
import java.io.FileOutputStream;

public final class AutoService extends AccessibilityService {
    private static final String GAME="com.ProjectMoon.LimbusCompany";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private LinearLayout overlay;
    private TextView status;
    private boolean visible=false, running=false, captureBusy=false, phaseFire=false;
    private int actions=0,misses=0,confirmed=0,finished=0;
    private long begun=0,phaseTime=0;
    private final Runnable loop=new Runnable() { @Override public void run() {
        if(!running) return;
        if(!visible) { stop("게임 화면 이탈");return; }
        if(SystemClock.elapsedRealtime()-begun>1_200_000L || actions>=60) {stop("안전 제한");return;}
        // Hide the controller while capturing so its controls cannot match templates.
        if(overlay!=null) overlay.setVisibility(View.INVISIBLE);
        handler.postDelayed(()->shot(frame->{
            if(overlay!=null)overlay.setVisibility(visible?View.VISIBLE:View.GONE);
            if(!running) {if(frame!=null)frame.recycle();return;}
            if(frame==null) {stop("캡처 불가");return;}
            try {analyze(frame);} finally {frame.recycle();}
        }),350);
    }};
    private interface Receiver { void accept(Bitmap bmp); }
    private void schedule(long millis) {handler.removeCallbacks(loop);handler.postDelayed(loop,millis);}
    private Button button(String s, Runnable f) {
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(10);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setPadding(1,0,1,0);b.setOnClickListener(v->f.run());return b;
    }
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);overlay.setPadding(4,4,4,4);
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.argb(232,25,30,40));bg.setCornerRadius(14);
        overlay.setBackground(bg);
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(11);status.setText("준비됨");
        overlay.addView(status);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(button("승률 등록",()->register(TemplateStore.WIN)));
        row.addView(button("전투 등록",()->register(TemplateStore.FIRE)));
        row.addView(button("종료 등록",()->register(TemplateStore.FINISH)));
        overlay.addView(row);
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.addView(button("시작",this::start)); controls.addView(button("중지",()->stop("사용자 중지")));
        overlay.addView(controls);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.END;lp.x=12;lp.y=80;
        wm.addView(overlay,lp);overlay.setVisibility(View.GONE);
        if(getRootInActiveWindow()!=null && getRootInActiveWindow().getPackageName()!=null) {
            visible=GAME.contentEquals(getRootInActiveWindow().getPackageName());
            overlay.setVisibility(visible?View.VISIBLE:View.GONE);
        }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event==null || event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName()==null) return;
        String pkg=event.getPackageName().toString();
        if(getPackageName().equals(pkg)) return;
        boolean now=GAME.equals(pkg);
        if(visible!=now) {
            visible=now;
            if(overlay!=null)overlay.setVisibility(now?View.VISIBLE:View.GONE);
            if(!now && running)stop("화면 전환");
        }
    }
    @Override public void onInterrupt() {stop("서비스 중단");}
    @Override public void onDestroy() {stop("서비스 종료");if(overlay!=null){try{wm.removeView(overlay);}catch(Exception ignored){}}super.onDestroy();}
    private void stop(String msg) {running=false;handler.removeCallbacks(loop);confirmed=0;finished=0;if(status!=null)status.setText("정지: "+msg);}
    private void start() {
        if(!NativeVision.AVAILABLE){status.setText("Rust 라이브러리 누락");return;}
        if(!visible){status.setText("림버스 화면에서 시작");return;}
        if(!TemplateStore.exists(this,TemplateStore.WIN)||!TemplateStore.exists(this,TemplateStore.FIRE)){status.setText("승률/전투 등록 필요");return;}
        running=true;phaseFire=false;actions=0;misses=0;confirmed=0;finished=0;
        begun=SystemClock.elapsedRealtime();phaseTime=begun;
        status.setText("자동 전투 시험 실행");schedule(900);
    }
    private void register(String label) {
        if(running || !visible || captureBusy) {status.setText("게임에서 중지 후 등록");return;}
        if(overlay!=null)overlay.setVisibility(View.INVISIBLE);
        handler.postDelayed(()->shot(bmp->{
            if(overlay!=null)overlay.setVisibility(visible?View.VISIBLE:View.GONE);
            if(bmp==null) {status.setText("화면 캡처 실패");return;}
            File file=new File(getCacheDir(),"registration.png");
            try(FileOutputStream o=new FileOutputStream(file)) {
                if(!bmp.compress(Bitmap.CompressFormat.PNG,100,o)) throw new Exception("PNG");
                Intent intent=new Intent(this,CalibrationActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("capture",file.getAbsolutePath());intent.putExtra("label",label);
                startActivity(intent);
            } catch(Exception e){status.setText("등록 화면 오류");}
            finally{bmp.recycle();}
        }),350);
    }
    private void shot(Receiver receiver) {
        if(captureBusy){receiver.accept(null);return;}
        captureBusy=true;
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
                @Override public void onSuccess(ScreenshotResult r) {
                    captureBusy=false;
                    HardwareBuffer hb=r.getHardwareBuffer();
                    Bitmap bmp=null;
                    try{
                        Bitmap wrapped=Bitmap.wrapHardwareBuffer(hb,r.getColorSpace());
                        if(wrapped!=null)bmp=wrapped.copy(Bitmap.Config.ARGB_8888,false);
                    }catch(Exception ignored){}finally{hb.close();}
                    receiver.accept(bmp);
                }
                @Override public void onFailure(int error){captureBusy=false;receiver.accept(null);}
            });
        }catch(Exception e){captureBusy=false;receiver.accept(null);}
    }
    private void analyze(Bitmap frame) {
        if(TemplateStore.exists(this,TemplateStore.FINISH)){
            if(TemplateStore.score(this,TemplateStore.FINISH,frame)>0.96f)finished++;else finished=0;
            if(finished>=2){stop("클리어 화면 확인");return;}
        }
        String label=phaseFire?TemplateStore.FIRE:TemplateStore.WIN;
        float confidence=TemplateStore.score(this,label,frame);
        if(confidence<0.945f){
            confirmed=0;misses++;
            if(phaseFire&&SystemClock.elapsedRealtime()-phaseTime>13000){
                phaseFire=false;phaseTime=SystemClock.elapsedRealtime();
            }
            if(misses>=30){stop("템플릿 인식 실패");return;}
            status.setText("탐색 "+label+" "+misses);schedule(2000);return;
        }
        misses=0;confirmed++;
        if(confirmed<2){status.setText("버튼 재확인 "+label);schedule(900);return;}
        confirmed=0;
        float x=TemplateStore.centerX(this,label),y=TemplateStore.centerY(this,label);
        if(x<0 || y<0){stop("좌표 없음");return;}
        Path p=new Path();p.moveTo(x,y);
        GestureDescription g=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(p,0,85)).build();
        boolean accepted=dispatchGesture(g,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription gesture) {
                if(!running)return;
                actions++;phaseFire=!phaseFire;phaseTime=SystemClock.elapsedRealtime();
                status.setText(label+" "+Math.round(confidence*100)+"% / "+actions);
                schedule(phaseFire?1200:5800);
            }
            @Override public void onCancelled(GestureDescription gesture){stop("터치 취소");}
        },handler);
        if(!accepted)stop("터치 거부");
    }
}
