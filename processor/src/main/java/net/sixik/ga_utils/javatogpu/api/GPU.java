package net.sixik.ga_utils.javatogpu.api;

public final class GPU {

    private GPU() {
    }

    public static float sin(float value) {
        throw unsupported();
    }

    public static double sin(double value) {
        throw unsupported();
    }

    public static float cos(float value) {
        throw unsupported();
    }

    public static double cos(double value) {
        throw unsupported();
    }

    public static float tan(float value) {
        throw unsupported();
    }

    public static double tan(double value) {
        throw unsupported();
    }

    public static float sqrt(float value) {
        throw unsupported();
    }

    public static double sqrt(double value) {
        throw unsupported();
    }

    public static float exp(float value) {
        throw unsupported();
    }

    public static double exp(double value) {
        throw unsupported();
    }

    public static float log(float value) {
        throw unsupported();
    }

    public static double log(double value) {
        throw unsupported();
    }

    public static float fabs(float value) {
        throw unsupported();
    }

    public static double fabs(double value) {
        throw unsupported();
    }

    public static float abs(float value) {
        throw unsupported();
    }

    public static double abs(double value) {
        throw unsupported();
    }

    public static float floor(float value) {
        throw unsupported();
    }

    public static double floor(double value) {
        throw unsupported();
    }

    public static float ceil(float value) {
        throw unsupported();
    }

    public static double ceil(double value) {
        throw unsupported();
    }

    public static float pow(float left, float right) {
        throw unsupported();
    }

    public static double pow(double left, double right) {
        throw unsupported();
    }

    public static float min(float left, float right) {
        throw unsupported();
    }

    public static double min(double left, double right) {
        throw unsupported();
    }

    public static float max(float left, float right) {
        throw unsupported();
    }

    public static double max(double left, double right) {
        throw unsupported();
    }

    public static int get_global_id(int dimension) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("GPU intrinsics are compile-time only");
    }
}
