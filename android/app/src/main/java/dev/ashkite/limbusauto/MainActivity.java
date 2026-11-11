package dev.ashkite.limbusauto;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private int dp(int x) { return (int)(x*getResources().getDisplayMetrics().density+.5f); }
    private TextView text(String s,int size) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.DKGRAY); t.setPadding(0,dp(9),0,dp(9)); return t;
    }
    private void button(LinearLayout l,String title,View.OnClickListener click) {
        Button b=new Button(this); b.setText(title); b.setAllCaps(false); b.setOnClickListener(click); l.addView(b);
    }
    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(16),dp(20),dp(24));
        root.addView(text("Limbus Auto Rust · 실험판",24));
        root.addView(text("안드로이드 11+ / arm64. PC 연결 필요 없음. Rust 화면 비교 엔진 + 안드로이드 접근성 터치.\n\n" +
            "이 버전은 전투 중 승률·전투 실행 버튼의 고정 위치만 판단합니다. 스테이지 전체 및 거울 던전 완주는 지원하지 않습니다.",15));
        button(root,"1. 접근성 설정 열기",v->new AlertDialog.Builder(this)
            .setTitle("접근성 권한")
            .setMessage("자동 터치와 화면 캡처가 가능한 강력한 권한입니다. 직접 허용한 경우에만 작동하며 서버에 화면을 보내지 않습니다. 게임 정책에 따른 제재 위험이 있습니다. 진행할까요?")
            .setNegativeButton("취소",null)
            .setPositiveButton("열기",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show());
        button(root,"2. 림버스 실행",v->{
            Intent launch=getPackageManager().getLaunchIntentForPackage("com.ProjectMoon.LimbusCompany");
            if(launch==null) { Toast.makeText(this,"게임을 찾지 못함",Toast.LENGTH_LONG).show(); return; }
            startActivity(launch);
        });
        root.addView(text("3. 게임 내 플로팅 막대에서 '승률 등록'을 눌러 화면을 찍고, 나온 이미지에서 해당 버튼의 중앙을 누르세요. '전투 등록'도 반복합니다.\n" +
            "4. 선택적으로 '종료 등록'을 이용해 클리어 화면만의 고유한 영역을 저장하세요.\n" +
            "5. 게임을 띄우고 '시작'으로 시험합니다. 문제가 있으면 즉시 '중지'.\n\n" +
            "동일 해상도에서만 인식합니다. 2회 연속 확인, 최대 60터치/20분 제한. 특수 전투는 미지원.",15));
        root.addView(text("Rust 라이브러리 상태: "+(NativeVision.AVAILABLE?"로드됨":"누락됨 - 실행 불가"),15));
        root.addView(text("등록 상태: 승률 "+(TemplateStore.exists(this,TemplateStore.WIN)?"✓":"없음")+
            " / 전투 "+(TemplateStore.exists(this,TemplateStore.FIRE)?"✓":"없음")+
            " / 종료 "+(TemplateStore.exists(this,TemplateStore.FINISH)?"✓":"선택"),15));
        scroll.addView(root); setContentView(scroll);
    }
}
