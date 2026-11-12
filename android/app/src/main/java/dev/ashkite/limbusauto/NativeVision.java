package dev.ashkite.limbusauto;

public final class NativeVision {
    public static final boolean AVAILABLE;
    static {
        boolean ok;
        try { System.loadLibrary("limbus_core"); ok=true; }
        catch (UnsatisfiedLinkError e) { ok=false; }
        AVAILABLE=ok;
    }
    private NativeVision() {}
    /** Maps OCR text to known game actions: 0=unknown, 1=win rate, 2=start, 3=clear. */
    public static native int classify(String text);
    public static native float similarity(int[] left, int[] right);
}
