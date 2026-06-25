package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.Float2;
import net.sixik.ga_utils.javatogpu.api.FloatPtr;
import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
import net.sixik.ga_utils.javatogpu.api.annotations.CCodeLibrary;

@CCodeLibrary
public final class GpuSupport {

    private static final float LIMIT = 32.0f;

    private GpuSupport() {
    }

    @CCode
    public static void clamp(FloatPtr ptr) {
        if (ptr.value > LIMIT) {
            ptr.value = LIMIT;
        }
    }

    @CCode(inline = true)
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @CCode
    public static Float2 add(Float2 left, Float2 right) {
        return new Float2(left.x + right.x, left.y + right.y);
    }

    @CCode(code = """
            return a + b * 50;
            """)
    public static native float rawBlend(float a, float b);
}
