package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.anotations.CCode;
import net.sixik.ga_utils.javatogpu.api.anotations.CCodeLibrary;
import net.sixik.ga_utils.javatogpu.api.anotations.OpenCLAttributes;

@CCodeLibrary
public final class ReusableMathLibrary {

    private static final float SCALE = 0.5f;

    private ReusableMathLibrary() {
    }

    @OpenCLAttributes({"always_inline"})
    @CCode(inline = true)
    public static float square(float value) {
        return (value * value) * SCALE;
    }

    @CCode
    public static float norm(float value) {
        return square(value) + 1.0f;
    }
}
