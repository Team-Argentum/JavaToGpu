package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.FloatPtr;
import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
import net.sixik.ga_utils.javatogpu.api.annotations.CCodeLibrary;
import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLQualifiers;

@CCodeLibrary
public final class PointerQualifierExample {

    private PointerQualifierExample() {
    }

    @CCode
    public static float read(@OpenCLQualifiers({"const"}) FloatPtr ptr) {
        return ptr.value;
    }
}
