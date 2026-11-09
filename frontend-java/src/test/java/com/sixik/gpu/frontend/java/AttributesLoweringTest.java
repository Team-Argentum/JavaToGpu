package com.sixik.gpu.frontend.java;

import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Атрибуты ядра, адресные пространства, @Local-массивы, device-функции и константы из другого класса. */
class AttributesLoweringTest {

    private static final String ATTRS = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.Global;
            import com.sixik.gpu.api.annotations.Kernel;
            import com.sixik.gpu.api.annotations.Local;
            import com.sixik.gpu.api.annotations.WorkGroup;
            import com.sixik.gpu.api.enums.InlinePolicy;

            public final class Attrs {
                @Kernel(inline = InlinePolicy.ALWAYS, optLevel = 2, workgroup = 32,
                        required = {"INT64_ATOMICS", "FP64"}, fallback = "processStepsSimple")
                public static void processSteps(@Global int[] input, @Global int[] output, @Global(constant = true) int a) {
                    int id = Gpu.getGlobalId(0);
                    int value = input[id];
                    int step = 0;
                    do {
                        value = Gpu.max(value, step) + a;
                        step++;
                    } while (step < 2);
                    output[id] = value;
                }

                @Kernel
                public static void processStepsSimple(@Global int[] input, @Global int[] output,
                                                      @Global(constant = true) int a) {
                    int id = Gpu.getGlobalId(0);
                    output[id] = Gpu.max(Gpu.max(input[id], 0) + a, 1) + a;
                }

                @Kernel
                @WorkGroup(x = 8, y = 4, z = 2)
                public static void spaces(@Global(constant = true) float[] table, @Local float[] scratch,
                                          @Global float[] out) {
                    int id = Gpu.getGlobalId(0);
                    scratch[0] = table[id];
                    out[id] = scratch[0];
                }

                @Kernel
                public static void localArray(@Global float[] in, @Global float[] out) {
                    @Local float[] tile = new float[64];
                    int l = Gpu.getLocalId(0);
                    tile[l] = in[Gpu.getGlobalId(0)];
                    out[Gpu.getGlobalId(0)] = tile[l];
                }
            }
            """;

    @Test
    void kernelAttributesFallbackAndAddressSpaces() {
        Golden.assertLowers("attributes.ir.txt", ATTRS);
    }

    @Test
    void requiredNamesAreCanonical() {
        FrontendHarness.Outcome a = FrontendHarness.compile(ATTRS);
        FrontendHarness.Outcome b = FrontendHarness.compile(ATTRS.replace("{\"INT64_ATOMICS\", \"FP64\"}",
                "{\"FP64\", \"INT64_ATOMICS\", \"FP64\"}"));
        assertTrue(a.success() && b.success(), a.describe() + b.describe());
        assertEquals(kernelIds(a), kernelIds(b), "order and repeats in required do not affect the hash");
    }

    @Test
    void helpersAndConstantsFromAnotherClass() {
        Golden.assertLowers("othercls.ir.txt", """
                package demo;

                import com.sixik.gpu.api.Gpu;
                import com.sixik.gpu.api.annotations.Global;
                import com.sixik.gpu.api.annotations.Kernel;

                public final class UsesLib {
                    @Kernel
                    public static void useLib(@Global float[] in, @Global float[] out) {
                        int id = Gpu.getGlobalId(0);
                        out[id] = MathLib.sq(in[id]) + MathLib.SCALE;
                    }
                }

                final class MathLib {
                    @com.sixik.gpu.api.annotations.Constant
                    static final float SCALE = 4.0f;

                    @com.sixik.gpu.api.annotations.DeviceFunction
                    static float sq(float x) {
                        return x * x * SCALE;
                    }
                }
                """);
    }

    @Test
    void hashIgnoresDebugInformation() {
        String plain = SmokeTest.COMPUTE_SINE;
        String noisy = plain.replace("int id =", "\n\n        /* comment */ int idx =").replace("[id]", "[idx]");
        FrontendHarness.Outcome a = FrontendHarness.compile(plain);
        FrontendHarness.Outcome b = FrontendHarness.compile(noisy);
        assertTrue(a.success() && b.success(), a.describe() + b.describe());
        IrModule ma = a.module();
        IrModule mb = b.module();
        assertEquals(IrBinary.kernelId(ma), IrBinary.kernelId(mb), "names, comments and positions do not change kernelId");
        assertNotEquals(Golden.dumpAll(a), Golden.dumpAll(b), "debug data differs in this case");
    }

    @Test
    void compilationIsDeterministic() {
        FrontendHarness.Outcome a = FrontendHarness.compile(ATTRS);
        FrontendHarness.Outcome b = FrontendHarness.compile(ATTRS);
        assertEquals(a.resources().keySet(), b.resources().keySet());
        for (var e : a.resources().entrySet()) {
            org.junit.jupiter.api.Assertions.assertArrayEquals(e.getValue(), b.resources().get(e.getKey()), e.getKey());
        }
    }

    private static java.util.Set<String> kernelIds(FrontendHarness.Outcome o) {
        java.util.Set<String> ids = new java.util.TreeSet<>();
        for (IrModule m : o.modules()) {
            ids.add(IrBinary.kernelId(m));
        }
        return ids;
    }
}
