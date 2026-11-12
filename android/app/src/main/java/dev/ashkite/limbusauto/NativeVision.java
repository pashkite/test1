package dev.ashkite.limbusauto;
public final class NativeVision {
    public static final boolean AVAILABLE;
    static { boolean loaded;try { System.loadLibrary("limbus_core"); loaded=true; }
        catch(UnsatisfiedLinkError e){loaded=false;} AVAILABLE=loaded; }
    private NativeVision(){}
    public static native int classify(String text);
    public static native float similarity(int[] left,int[] right);
    /**
     * Native Rust normalized cross-correlation search on a height-240 landscape screenshot.
     * Returns gearX,gearY,winX,winY,gearScore,winScore in normalized pixels.
     */
    public static native float[] findPair(int[] image,int width,int height,byte[] gear,byte[] win);
}
