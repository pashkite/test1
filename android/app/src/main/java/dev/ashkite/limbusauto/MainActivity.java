package dev.ashkite.limbusauto;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;

public final class MainActivity extends Activity {
    private int dp(int x) { return (int)(x*getResources().getDisplayMetrics().density+.5f); }
    private TextView text(String s,int size) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size);t.setTextColor(Color.DKGRAY);t.setPadding(0,dp(9),0,dp(9));return t;
    }
    private void button(LinearLayout root,String label,View.OnClickListener handler){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(handler);root.addView(b);
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setPadding(dp(22),dp(20),dp(22),dp(20));root.setOrientation(LinearLayout.VERTICAL);
        root.addView(text("Limbus Auto OCR · v0.3.2",24));
        root.addView(text("PC 연결 없이 작동하는 Android 11+ ARM64 실험 앱.\n\n"+
            "버튼 위치를 등록할 필요 없이 한국어·영어 글자를 자동 탐색합니다. "+
            "Rust는 글자 분류를, ML Kit는 기기 내부 글자 인식을 담당합니다. "+
            "화면은 서버에 전송하지 않습니다.",15));
        button(root,"1. 접근성 기능 활성화",v->new AlertDialog.Builder(this)
            .setTitle("권한 및 주의사항").setMessage(
                "화면 캡처와 일반 터치 권한을 사용하는 실험용 앱입니다. "+
                "실제 게임에서 검증되지 않았습니다. 게임 운영 정책상 계정 제재 위험이 있습니다. "+
                "원치 않으면 접근성 서비스를 허용하지 마세요.")
            .setNegativeButton("취소",null)
            .setPositiveButton("설정 열기",(dialog,which)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show());
        button(root,"2. 림버스 실행",v->{
            Intent launch=getPackageManager().getLaunchIntentForPackage("com.ProjectMoon.LimbusCompany");
            if(launch!=null)startActivity(launch);
            else new AlertDialog.Builder(this).setMessage("림버스 앱을 찾지 못했어. 게임을 직접 실행해 줘.").setPositiveButton("확인",null).show();
        });
        root.addView(text("3. 화면 중앙 상단에 컨트롤러가 보이면 접근성 서비스가 실행 중입니다.\n게임 감지에 실패해도 버튼은 계속 표시됩니다.\n4. 게임에서 일반 전투 화면으로 이동합니다.\n"+
            "5. 컨트롤러 상태가 'READY - GAME FOUND'이면 ‘AUTO ON’을 누릅니다. ‘STOP’은 중지입니다.\n"+
            "6. 글자 인식이 2회 일치하면 ‘승률’→‘START/전투 시작’을 터치합니다.\n"+
            "7. 앱이 버튼을 찾지 못하거나 예상하지 못한 화면에 도달하면 자동으로 멈춥니다.\n\n"+
            "현재 제외: 스테이지 내부 이동·보상 선택·거울 던전·보스 기믹. "+
            "전투 시작이 글자 없는 아이콘인 경우 인식하지 못할 수 있습니다.\n\n"+
            "화면 인식과 터치 위치는 아직 실제 게임에서 검증되지 않았습니다. 반드시 지켜보며 시험하고, 문제가 있으면 즉시 'STOP'을 누르세요.",15));
        root.addView(text("Rust 엔진: "+(NativeVision.AVAILABLE?"정상 로드":"로드 실패"),15));
        scroll.addView(root);setContentView(scroll);
    }
}
