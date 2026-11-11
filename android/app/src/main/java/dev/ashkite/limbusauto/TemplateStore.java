package dev.ashkite.limbusauto;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.SharedPreferences;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

final class TemplateStore {
    static final String WIN="win", FIRE="fire", FINISH="finish";
    static final int W=120, H=60;
    private TemplateStore() {}
    static File file(Context c, String label) { return new File(c.getFilesDir(), "patch_"+label+".png"); }
    static boolean exists(Context c, String label) { return file(c,label).isFile(); }
    static void save(Context c, String label, Bitmap src, int x, int y) throws IOException {
        if (!validLabel(label)) throw new IOException("Invalid action");
        int px=x-W/2, py=y-H/2;
        if(px<0 || py<0 || px+W>src.getWidth() || py+H>src.getHeight()) throw new IOException("Choose a point away from edge");
        Bitmap patch=Bitmap.createBitmap(src,px,py,W,H);
        try (FileOutputStream o=new FileOutputStream(file(c,label))) {
            if(!patch.compress(Bitmap.CompressFormat.PNG,100,o)) throw new IOException("PNG save failure");
        } finally { patch.recycle(); }
        SharedPreferences.Editor e=c.getSharedPreferences("templates",Context.MODE_PRIVATE).edit();
        e.putInt(label+"_x",x).putInt(label+"_y",y);
        e.putInt(label+"_w",src.getWidth()).putInt(label+"_h",src.getHeight()).apply();
    }
    static boolean validLabel(String s) { return WIN.equals(s)||FIRE.equals(s)||FINISH.equals(s); }
    static float score(Context c, String label, Bitmap frame) {
        if(!NativeVision.AVAILABLE || !exists(c,label)) return 0f;
        SharedPreferences pref=c.getSharedPreferences("templates",Context.MODE_PRIVATE);
        if(pref.getInt(label+"_w",-1)!=frame.getWidth() || pref.getInt(label+"_h",-1)!=frame.getHeight()) return 0f;
        int x=pref.getInt(label+"_x",-1), y=pref.getInt(label+"_y",-1);
        if(x-W/2<0 || y-H/2<0 || x+W/2>frame.getWidth() || y+H/2>frame.getHeight()) return 0f;
        Bitmap tpl=BitmapFactory.decodeFile(file(c,label).getAbsolutePath());
        if(tpl==null) return 0f;
        try {
            if(tpl.getWidth()!=W || tpl.getHeight()!=H) return 0f;
            int[] a=new int[W*H], b=new int[W*H];
            frame.getPixels(a,0,W,x-W/2,y-H/2,W,H);
            tpl.getPixels(b,0,W,0,0,W,H);
            return NativeVision.similarity(a,b);
        } finally { tpl.recycle(); }
    }
    static float centerX(Context c,String label) { return c.getSharedPreferences("templates",Context.MODE_PRIVATE).getInt(label+"_x",-1); }
    static float centerY(Context c,String label) { return c.getSharedPreferences("templates",Context.MODE_PRIVATE).getInt(label+"_y",-1); }
}
