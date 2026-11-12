package dev.ashkite.limbusauto;
public final class NativeVision {
    public static final boolean AVAILABLE;
    static{boolean ok;try{System.loadLibrary("limbus_core");ok=true;}
        catch(UnsatisfiedLinkError e){ok=false;}AVAILABLE=ok;}
    private NativeVision(){}
    public static native int classify(String text);
    public static native float similarity(int[] left,int[] right);
    public static native float[] findPair(int[] pixels,int width,int height,byte[] gear,byte[] win);
    /** WinX, WinY, GearX, GearY, idleScore, selectedScore, orangeFraction. */
    public static native float[] locateControls(int[] pixels,int width,int height,byte[] idle,byte[] selected);
}
