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
    /** Rust implementation; left and right must have same dimensions. */
    public static native float similarity(int[] left, int[] right);
}
