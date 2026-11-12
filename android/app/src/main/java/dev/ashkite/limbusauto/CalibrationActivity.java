package dev.ashkite.limbusauto;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

public final class CalibrationActivity extends Activity {
    private Bitmap picture;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        String file=getIntent().getStringExtra("capture");
        String label=getIntent().getStringExtra("label");
        if(file==null || !TemplateStore.validLabel(label)) { finish();return; }
        picture=BitmapFactory.decodeFile(file);
        if(picture==null) { Toast.makeText(this,"캡처 불러오기 실패",Toast.LENGTH_LONG).show();finish();return; }
        setContentView(new View(this) {
            private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
            private final RectF dst=new RectF();
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(Color.BLACK);
                float scale=Math.min(getWidth()/(float)picture.getWidth(),getHeight()/(float)picture.getHeight());
                float width=picture.getWidth()*scale, height=picture.getHeight()*scale;
                dst.set((getWidth()-width)/2,(getHeight()-height)/2,(getWidth()+width)/2,(getHeight()+height)/2);
                canvas.drawBitmap(picture,null,dst,paint);
                Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);p.setTextSize(28f);
                canvas.drawText("Tap center of "+label+" button / Back to cancel",25,42,p);
            }
            @Override public boolean onTouchEvent(MotionEvent event) {
                if(event.getAction()!=MotionEvent.ACTION_UP) return true;
                if(!dst.contains(event.getX(),event.getY())) return true;
                int x=Math.round((event.getX()-dst.left)*picture.getWidth()/dst.width());
                int y=Math.round((event.getY()-dst.top)*picture.getHeight()/dst.height());
                try {
                    TemplateStore.save(CalibrationActivity.this,label,picture,x,y);
                    Toast.makeText(CalibrationActivity.this,"등록: "+label,Toast.LENGTH_LONG).show();
                    finish();
                } catch(Exception e) { Toast.makeText(CalibrationActivity.this,e.getMessage(),Toast.LENGTH_LONG).show(); }
                return true;
            }
        });
    }
    @Override public void onDestroy() {
        if(picture!=null) picture.recycle();
        super.onDestroy();
    }
}
