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

public final class MainActivity extends Activity {
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int size){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);
        t.setTextColor(Color.DKGRAY);t.setPadding(0,dp(8),0,dp(8));return t;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(15),dp(20),dp(15));
        root.addView(text("Limbus Auto Rust 0.4",23));
        root.addView(text("실험용 아이콘 인식 버전 / Android 11 이상 / ARM64 / PC 연결 불필요",15));
        Button enable=new Button(this);enable.setText("1. 접근성 서비스 설정");
        enable.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("접근성 권한")
            .setMessage("게임 화면을 캡처하고 일반 터치를 자동 실행합니다. 안전과 계정 제재 위험을 이해한 경우에만 활성화하세요. 게임 서버 통신이나 메모리 변경 기능은 없습니다.")
            .setNegativeButton("취소",null)
            .setPositiveButton("설정 열기",(dialog,which)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show());
        root.addView(enable);
        Button launch=new Button(this);launch.setText("2. 림버스 실행");
        launch.setOnClickListener(v->{
            Intent game=getPackageManager().getLaunchIntentForPackage("com.ProjectMoon.LimbusCompany");
            if(game!=null)startActivity(game);
            else new AlertDialog.Builder(this).setMessage("림버스 앱을 직접 실행해 주세요.").setPositiveButton("확인",null).show();
        });root.addView(launch);
        root.addView(text("3. 일반 전투 화면을 띄우면 중앙 상단 조작창에 SCAN / AUTO / STOP 버튼이 나타납니다.\n"+
            "4. SCAN으로 먼저 초록색(승률)과 노란색(전투 시작) 표시가 실제 버튼 위에 있는지 확인하세요.\n"+
            "5. 둘 다 정확하게 표시될 때만 AUTO를 시험하세요. 정확하지 않거나 인식률이 낮으면 STOP을 누르세요.\n\n"+
            "사진 한 장으로 만든 패턴 시험판이라 기기 화면·UI 버전에 따라 인식 실패가 가능합니다. "+
            "보스전, 스테이지 내부 이동, 던전 경로·보상 자동화는 지원하지 않습니다.\n"+
            "수정 전과 마찬가지로 실제 게임 플레이 성공은 검증되지 않았습니다.",15));
        root.addView(text("Rust 라이브러리: "+(NativeVision.AVAILABLE?"정상":"로드 실패"),14));
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    }
}
