package redsmods.flashplus.depth;

/**
 * Public API for the depth-capture state added to PerfectFrames by
 * PerfectFramesMixin.
 */
public final class DepthFrames {

    private DepthFrames() {}
    private static volatile boolean captureDepth = false;
    public  static org.joml.Matrix4f worldMatrix  = new org.joml.Matrix4f();

    public static void setCaptureDepth(boolean state) {
        captureDepth = state;
    }

    public static boolean isCapturingDepth() {
        return captureDepth;
    }
}