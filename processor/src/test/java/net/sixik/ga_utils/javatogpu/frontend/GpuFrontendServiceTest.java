package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstant;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;
import net.sixik.ga_utils.javatogpu.frontend.validation.GpuValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuFrontendServiceTest {

    @Test
    void parsesAndValidatesGpuMethodInOneCall() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    for (int i = 0; i < 4; i++) {
                        output[i] = GPU.sin(input[i]);
                    }
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        ParsedGpuMethod method = service.parseAndValidate(methodSource);

        assertEquals("kernel", method.name());
    }

    @Test
    void parsesAndValidatesPrimitiveCastMethod() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int i = 0;
                    output[i] = GPU.sin((float) i);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();

        ParsedGpuMethod method = service.parseAndValidate(methodSource);

        assertEquals("kernel", method.name());
    }

    @Test
    void parsesAndValidatesBooleanLocalMethod() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    boolean enabled = input[0] > 0.0f;
                    if (enabled) {
                        output[0] = 1.0f;
                    }
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        ParsedGpuMethod method = service.parseAndValidate(methodSource);

        assertEquals("kernel", method.name());
    }

    @Test
    void parsesValidatesAndLowersGpuMethodInOneCall() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    float value = input[id];
                    output[id] = GPU.sin(value) * GPU.cos(value);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        GpuIrMethod method = service.parseValidateAndLower(methodSource);

        assertEquals("kernel", method.name());
        assertEquals(3, method.statements().size());
    }

    @Test
    void parsesValidatesLowersAndEmitsGpuMethodInOneCall() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    for (int i = 0; i < 4; i++) {
                        output[i] = GPU.sin(input[i]);
                    }
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    for (int i = 0; (i < 4); i = (i + 1)) {
                        output[i] = sin(input[i]);
                    }
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsFloatLiteralKernel() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    output[0] = GPU.sin((1.0f + 2.0f));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    output[0] = sin((1.0f + 2.0f));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsPrimitiveCastKernel() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int i = 0;
                    output[i] = GPU.sin((float) i);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int i = 0;
                    output[i] = sin(((float) i));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsKernelWithCCodeHelper() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = square(input[id]);
                }
                """;
        String helperSource = """
                @CCode(inline = true)
                float square(float value) {
                    return value * value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource, List.of(helperSource));

        assertEquals("""
                inline float jtg_fn_square_float(float value);

                inline float jtg_fn_square_float(float value) {
                    return (value * value);
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = jtg_fn_square_float(input[id]);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsKernelWithExternalInlineCCodeHelper() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = KernelMath.square(input[id]);
                }
                """;
        String helperSource = """
                @CCode(inline = true)
                float square(float value) {
                    return value * value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        ParsedGpuMethod kernelMethod = new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser()
                .parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod helperMethod = new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser()
                .parseMethod(helperSource, "KernelMath", "sample.KernelMath");

        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(helperMethod));

        assertEquals("""
                inline float jtg_fn_KernelMath_square_float(float value);

                inline float jtg_fn_KernelMath_square_float(float value) {
                    return (value * value);
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = jtg_fn_KernelMath_square_float(input[id]);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersScopedCCodeHelperWhenSignatureExistsInAnotherOwner() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = KernelMath.square(input[id]);
                }
                """;
        String kernelMathSource = """
                @CCode(inline = true)
                float square(float value) {
                    return value * value;
                }
                """;
        String fastMathSource = """
                @CCode(inline = true)
                float square(float value) {
                    return value + value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod kernelMathMethod = parser.parseMethod(kernelMathSource, "KernelMath", "sample.KernelMath");
        ParsedGpuMethod fastMathMethod = parser.parseMethod(fastMathSource, "FastMath", "sample.FastMath");

        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(kernelMathMethod, fastMathMethod));

        assertTrue(kernel.contains("inline float jtg_fn_KernelMath_square_float(float value);"));
        assertFalse(kernel.contains("jtg_fn_FastMath_square_float"));
        assertTrue(kernel.contains("output[id] = jtg_fn_KernelMath_square_float(input[id]);"));
    }

    @Test
    void rejectsAmbiguousUnscopedCCodeHelperCallAcrossOwners() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = square(input[id]);
                }
                """;
        String helperSource = """
                @CCode(inline = true)
                float square(float value) {
                    return value * value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod kernelMathMethod = parser.parseMethod(helperSource, "KernelMath", "sample.KernelMath");
        ParsedGpuMethod fastMathMethod = parser.parseMethod(helperSource, "FastMath", "sample.FastMath");

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> service.validateLowerAndEmit(kernelMethod, List.of(kernelMathMethod, fastMathMethod))
        );

        assertTrue(exception.getMessage().contains("Ambiguous @CCode helper call in @GPU method"));
        assertTrue(exception.getMessage().contains("square[float]"));
    }

    @Test
    void emitsOnlyReachableTransitiveCCodeHelpers() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = KernelMath.cube(input[id]);
                }
                """;
        String squareSource = """
                @CCode(inline = true)
                float square(float value) {
                    return value * value;
                }
                """;
        String cubeSource = """
                @CCode(inline = true)
                float cube(float value) {
                    return square(value) * value;
                }
                """;
        String unusedSource = """
                @CCode(inline = true)
                float unused(float value) {
                    return value + 1.0f;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod squareMethod = parser.parseMethod(squareSource, "KernelMath", "sample.KernelMath");
        ParsedGpuMethod cubeMethod = parser.parseMethod(cubeSource, "KernelMath", "sample.KernelMath");
        ParsedGpuMethod unusedMethod = parser.parseMethod(unusedSource, "KernelMath", "sample.KernelMath");

        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(squareMethod, cubeMethod, unusedMethod));

        assertTrue(kernel.contains("inline float jtg_fn_KernelMath_square_float(float value);"));
        assertTrue(kernel.contains("inline float jtg_fn_KernelMath_cube_float(float value);"));
        assertTrue(kernel.contains("return (jtg_fn_KernelMath_square_float(value) * value);"));
        assertTrue(kernel.contains("output[id] = jtg_fn_KernelMath_cube_float(input[id]);"));
        assertFalse(kernel.contains("jtg_fn_KernelMath_unused_float"));
    }

    @Test
    void rejectsRecursiveCCodeHelperCycle() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = KernelMath.ping(input[id]);
                }
                """;
        String pingSource = """
                @CCode(inline = true)
                float ping(float value) {
                    return pong(value);
                }
                """;
        String pongSource = """
                @CCode(inline = true)
                float pong(float value) {
                    return ping(value);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod pingMethod = parser.parseMethod(pingSource, "KernelMath", "sample.KernelMath");
        ParsedGpuMethod pongMethod = parser.parseMethod(pongSource, "KernelMath", "sample.KernelMath");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.validateLowerAndEmit(kernelMethod, List.of(pingMethod, pongMethod))
        );

        assertTrue(exception.getMessage().contains("Recursive @CCode helper calls are not supported"));
        assertTrue(exception.getMessage().contains("jtg_fn_KernelMath_ping_float"));
        assertTrue(exception.getMessage().contains("jtg_fn_KernelMath_pong_float"));
    }

    @Test
    void parsesValidatesLowersAndEmitsFloatPtrHelperMutation() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    FloatPtr ptr = new FloatPtr();
                    ptr.value = input[id];
                    Helpers.mutate(ptr);
                    output[id] = GPU.tan(ptr.value);
                }
                """;
        String helperSource = """
                @CCode
                void mutate(FloatPtr ptr) {
                    ptr.value = 50.0f;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod helperMethod = parser.parseMethod(helperSource, "Helpers", "sample.Helpers");

        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(helperMethod));

        assertEquals("""
                void jtg_fn_Helpers_mutate_FloatPtr(float* ptr);

                void jtg_fn_Helpers_mutate_FloatPtr(float* ptr) {
                    (*ptr) = 50.0f;
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    float ptr = 0.0f;
                    ptr = input[id];
                    jtg_fn_Helpers_mutate_FloatPtr((&ptr));
                    output[id] = tan(ptr);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsDoublePtrHelperMutation() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal double[] input, @GPUGlobal double[] output) {
                    int id = GPU.get_global_id(0);
                    DoublePtr ptr = new DoublePtr();
                    ptr.value = input[id];
                    Helpers.shift(ptr);
                    output[id] = GPU.sqrt(ptr.value);
                }
                """;
        String helperSource = """
                @CCode
                void shift(DoublePtr ptr) {
                    ptr.value = ptr.value + 2.0;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod helperMethod = parser.parseMethod(helperSource, "Helpers", "sample.Helpers");

        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(helperMethod));

        assertEquals("""
                void jtg_fn_Helpers_shift_DoublePtr(double* ptr);

                void jtg_fn_Helpers_shift_DoublePtr(double* ptr) {
                    (*ptr) = ((*ptr) + 2.0);
                }
                __kernel void jtg_kernel(__global double* input, __global double* output) {
                    int id = get_global_id(0);
                    double ptr = 0.0;
                    ptr = input[id];
                    jtg_fn_Helpers_shift_DoublePtr((&ptr));
                    output[id] = sqrt(ptr);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsNativeCCodeHelpers() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    FloatPtr ptr = new FloatPtr();
                    ptr.value = input[id];
                    output[id] = Helpers.myMath(input[id], 2.0f) + Helpers.myMathPtr(ptr, ptr);
                }
                """;
        String scalarHelperSource = "@CCode(code = \"\"\"\n"
                + "        return a + b * 50.0f;\n"
                + "        \"\"\")\n"
                + "native float myMath(float a, float b);";
        String pointerHelperSource = "@CCode(code = \"\"\"\n"
                + "        return (*a) + (*b) * 50.0f;\n"
                + "        \"\"\")\n"
                + "native float myMathPtr(FloatPtr a, FloatPtr b);";

        GpuFrontendService service = GpuFrontendService.createDefault();
        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod scalarHelperMethod = parser.parseMethod(scalarHelperSource, "Helpers", "sample.Helpers");
        ParsedGpuMethod pointerHelperMethod = parser.parseMethod(pointerHelperSource, "Helpers", "sample.Helpers");

        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(scalarHelperMethod, pointerHelperMethod));

        assertEquals("""
                float jtg_fn_Helpers_myMath_float_float(float a, float b);
                float jtg_fn_Helpers_myMathPtr_FloatPtr_FloatPtr(float* a, float* b);

                float jtg_fn_Helpers_myMath_float_float(float a, float b) {
                    return a + b * 50.0f;
                }
                float jtg_fn_Helpers_myMathPtr_FloatPtr_FloatPtr(float* a, float* b) {
                    return (*a) + (*b) * 50.0f;
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    float ptr = 0.0f;
                    ptr = input[id];
                    output[id] = (jtg_fn_Helpers_myMath_float_float(input[id], 2.0f) + jtg_fn_Helpers_myMathPtr_FloatPtr_FloatPtr((&ptr), (&ptr)));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsHelperCallWithScalarWidening() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = Helpers.c_code((id * 0.75f), 10, id);
                }
                """;
        String helperSource = """
                @CCode
                float c_code(float a, float b, float t) {
                    return a - (b + a) * t;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod helperMethod = parser.parseMethod(helperSource, "Helpers", "sample.Helpers");

        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(helperMethod));

        assertEquals("""
                float jtg_fn_Helpers_c_code_float_float_float(float a, float b, float t);

                float jtg_fn_Helpers_c_code_float_float_float(float a, float b, float t) {
                    return (a - ((b + a) * t));
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = jtg_fn_Helpers_c_code_float_float_float((id * 0.75f), 10, id);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsFloatPtrConstructorWithInitializer() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    FloatPtr ptr = new FloatPtr(input[id]);
                    output[id] = GPU.tan(ptr.value);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    float ptr = input[id];
                    output[id] = tan(ptr);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsDoublePtrConstructorWithInitializer() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal double[] input, @GPUGlobal double[] output) {
                    int id = GPU.get_global_id(0);
                    DoublePtr ptr = new DoublePtr(input[id] + 2.0);
                    output[id] = GPU.sqrt(ptr.value);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global double* input, __global double* output) {
                    int id = get_global_id(0);
                    double ptr = (input[id] + 2.0);
                    output[id] = sqrt(ptr);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsSameOwnerStaticFinalConstant() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = input[id] * SCALE;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        ParsedGpuMethod kernelMethod = new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser()
                .parseMethod(
                        methodSource,
                        "Demo",
                        "sample.Demo",
                        List.of(new ParsedGpuConstant("Demo", "sample.Demo", "SCALE", "float", "0.5f"))
                );

        String kernel = service.validateLowerAndEmit(kernelMethod, List.of());

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = (input[id] * 0.5f);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsQualifiedExternalStaticFinalConstant() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = input[id] * GpuUtils.SCALE;
                }
                """;
        String helperSource = """
                @CCode
                float noop(float value) {
                    return value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod helperMethod = parser.parseMethod(
                helperSource,
                "GpuUtils",
                "sample.GpuUtils",
                List.of(new ParsedGpuConstant("GpuUtils", "sample.GpuUtils", "SCALE", "float", "2.0f"))
        );

        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(helperMethod));

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = (input[id] * 2.0f);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsClassicSwitchCaseWithNestedBlock() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal double[] input, @GPUGlobal double[] output) {
                    int index = input[0] < 20.0 ? 1 : 0;
                    double value = input[0];
                    switch (index) {
                        case 1: {
                            value = 17 * (1 / value);
                        }
                        default:
                            break;
                    }
                    output[0] = value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global double* input, __global double* output) {
                    int index = ((input[0] < 20.0) ? 1 : 0);
                    double value = input[0];
                    switch (index) {
                        case 1:
                            value = (17 * (1 / value));
                        default:
                            break;
                    }
                    output[0] = value;
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsRawLongLiterals() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal long[] input, @GPUGlobal long[] output) {
                    long value = input[0] + 1L;
                    output[0] = value * 17L;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global long* input, __global long* output) {
                    long value = (input[0] + 1L);
                    output[0] = (value * 17L);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsByteShortAndCharLiteralArithmetic() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    short step = 2;
                    byte delta = 3;
                    output[0] = input[0] + step + delta + 'A';
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    short step = 2;
                    char delta = 3;
                    output[0] = (((input[0] + step) + delta) + 65);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsBooleanLocalKernel() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    boolean enabled = input[0] > 0.0f;
                    if (enabled) {
                        output[0] = 1.0f;
                    }
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    bool enabled = (input[0] > 0.0f);
                    if (enabled) {
                        output[0] = 1.0f;
                    }
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsDoubleMathKernel() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal double[] input, @GPUGlobal double[] output) {
                    int id = GPU.get_global_id(0);
                    double value = GPU.sqrt(input[id]) + GPU.pow(input[id], 2.0);
                    output[id] = GPU.max(value, GPU.log(input[id]));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global double* input, __global double* output) {
                    int id = get_global_id(0);
                    double value = (sqrt(input[id]) + pow(input[id], 2.0));
                    output[id] = max(value, log(input[id]));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsWorkItemAndBarrierBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int gid = GPU.get_global_id(0);
                    int lid = GPU.get_local_id(0);
                    int size = GPU.get_global_size(0);
                    int dim = GPU.get_work_dim();
                    GPU.barrier(GPU.CLK_LOCAL_MEM_FENCE | GPU.CLK_GLOBAL_MEM_FENCE);
                    output[gid] = gid + lid + size + dim;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int gid = get_global_id(0);
                    int lid = get_local_id(0);
                    int size = get_global_size(0);
                    int dim = get_work_dim();
                    barrier((1 | 2));
                    output[gid] = (((gid + lid) + size) + dim);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsOpenClCommonMathBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = GPU.clamp(input[id], 0.0f, 1.0f);
                    output[id] = GPU.mix(output[id], GPU.rsqrt(GPU.fmax(input[id], 1.0f)), 0.5f);
                    output[id] = GPU.mad(output[id], GPU.step(0.25f, output[id]), GPU.smoothstep(0.0f, 1.0f, output[id]));
                    output[id] = GPU.log2(GPU.length(output[id], 2.0f));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = clamp(input[id], 0.0f, 1.0f);
                    output[id] = mix(output[id], rsqrt(fmax(input[id], 1.0f)), 0.5f);
                    output[id] = mad(output[id], step(0.25f, output[id]), smoothstep(0.0f, 1.0f, output[id]));
                    output[id] = log2(hypot(output[id], 2.0f));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsAdditionalOpenClIntegerBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    int offset = GPU.get_global_offset(0);
                    int value = GPU.min(input[id], 64);
                    value = GPU.max(value, offset + 8);
                    value = GPU.clamp(value, 4, 32);
                    value = GPU.rotate(value, 1);
                    output[id] = GPU.popcount(value) + GPU.clz(value);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int id = get_global_id(0);
                    int offset = get_global_offset(0);
                    int value = min(input[id], 64);
                    value = max(value, (offset + 8));
                    value = clamp(value, 4, 32);
                    value = rotate(value, 1);
                    output[id] = (popcount(value) + clz(value));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsRoundAndSignBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    float value = GPU.round(input[id]) + GPU.sign(input[id] - 1.0f);
                    output[id] = GPU.abs(GPU.fract(value) - 0.5f);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    float value = (round(input[id]) + ((((input[id] - 1.0f)) > 0.0f) ? 1.0f : ((((input[id] - 1.0f)) < 0.0f) ? -1.0f : 0.0f)));
                    output[id] = fabs((((value) - floor(value)) - 0.5f));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsAngleAndConversionBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    float angle = GPU.degrees(GPU.atan2(input[id], 1.0f));
                    float limited = GPU.copysign(GPU.trunc(GPU.radians(angle)), input[id] - 2.0f);
                    int bits = GPU.as_int(limited);
                    output[id] = GPU.convert_int(limited) + GPU.popcount(bits);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global int* output) {
                    int id = get_global_id(0);
                    float angle = degrees(atan2(input[id], 1.0f));
                    float limited = copysign(trunc(radians(angle)), (input[id] - 2.0f));
                    int bits = as_int(limited);
                    output[id] = (convert_int(limited) + popcount(bits));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsDoubleConversionBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal double[] input, @GPUGlobal double[] output) {
                    int id = GPU.get_global_id(0);
                    double angle = GPU.acos(input[id]) + GPU.asin(input[id]) + GPU.atan(input[id]);
                    long bits = GPU.as_long(angle);
                    output[id] = GPU.as_double(bits) + GPU.convert_double(GPU.convert_long(angle));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global double* input, __global double* output) {
                    int id = get_global_id(0);
                    double angle = ((acos(input[id]) + asin(input[id])) + atan(input[id]));
                    long bits = as_long(angle);
                    output[id] = (as_double(bits) + convert_double(convert_long(angle)));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsAtomicBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] data, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    int previous = GPU.atomic_add(data, id, 2);
                    previous += GPU.atomic_sub(data, id, 1);
                    previous += GPU.atomic_inc(data, id);
                    previous += GPU.atomic_dec(data, id);
                    previous += GPU.atomic_cmpxchg(data, id, 5, 9);
                    previous += GPU.atomic_min(data, id, 3);
                    previous += GPU.atomic_max(data, id, 7);
                    previous += GPU.atomic_and(data, id, 15);
                    previous += GPU.atomic_or(data, id, 16);
                    output[id] = previous + GPU.atomic_xor(data, id, 31);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* data, __global int* output) {
                    int id = get_global_id(0);
                    int previous = atomic_add(&((data)[id]), 2);
                    previous = (previous + atomic_sub(&((data)[id]), 1));
                    previous = (previous + atomic_inc(&((data)[id])));
                    previous = (previous + atomic_dec(&((data)[id])));
                    previous = (previous + atomic_cmpxchg(&((data)[id]), 5, 9));
                    previous = (previous + atomic_min(&((data)[id]), 3));
                    previous = (previous + atomic_max(&((data)[id]), 7));
                    previous = (previous + atomic_and(&((data)[id]), 15));
                    previous = (previous + atomic_or(&((data)[id]), 16));
                    output[id] = (previous + atomic_xor(&((data)[id]), 31));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsLocalMemoryHelpers() {
        String methodSource = """
                @GPU
                void kernel(@GPUConstant float[] lookup, @GPULocal float[] scratch, @GPUGlobal float[] output) {
                    int gid = GPU.get_global_id(0);
                    int lid = GPU.get_local_id(0);
                    scratch[lid] = lookup[lid];
                    GPU.local_mem_fence();
                    GPU.local_barrier();
                    output[gid] = scratch[lid];
                    GPU.global_mem_fence();
                    GPU.global_barrier();
                    GPU.all_mem_fence();
                    GPU.all_barrier();
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__constant float* lookup, __local float* scratch, __global float* output) {
                    int gid = get_global_id(0);
                    int lid = get_local_id(0);
                    scratch[lid] = lookup[lid];
                    mem_fence(1);
                    barrier(1);
                    output[gid] = scratch[lid];
                    mem_fence(2);
                    barrier(2);
                    mem_fence((1 | 2));
                    barrier((1 | 2));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsImageAndSamplerBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image2DReadOnly inputImage, Image2DWriteOnly outputImage, Sampler sampler, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    Int2 coords = new Int2(id, 0);
                    Int4 pixel = GPU.read_imagei(inputImage, sampler, coords);
                    output[id] = pixel.x + GPU.get_image_width(inputImage) + GPU.get_image_height(outputImage);
                    GPU.write_imagef(outputImage, coords, new Float4(1.0f, 0.0f, 0.0f, 1.0f));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                    int id = get_global_id(0);
                    int2 coords = (int2)(id, 0);
                    int4 pixel = read_imagei(inputImage, sampler, coords);
                    output[id] = ((pixel.x + get_image_width(inputImage)) + get_image_height(outputImage));
                    write_imagef(outputImage, coords, (float4)(1.0f, 0.0f, 0.0f, 1.0f));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsCustomIntrinsicLibraryMethod() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = MyIntrinsics.twice(input[id]) + MyIntrinsics.SCALE;
                }
                """;
        String intrinsicSource = """
                @net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic(code = "(({0}) * 2.0f)")
                float twice(float value) {
                    return value * 2.0f;
                }
                """;

        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod intrinsicMethod = parser.parseMethod(
                intrinsicSource,
                "MyIntrinsics",
                "sample.MyIntrinsics",
                List.of(new ParsedGpuConstant("MyIntrinsics", "sample.MyIntrinsics", "SCALE", "float", "0.25f"))
        );

        GpuFrontendService service = GpuFrontendService.create(
                net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase.createDefault(List.of(intrinsicMethod))
        );
        String kernel = service.validateLowerAndEmit(kernelMethod, List.of());

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = (((input[id]) * 2.0f) + 0.25f);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsGpuStructAndAttributes() {
        String methodSource = """
                @OpenCLAttributes({"reqd_work_group_size(16, 1, 1)"})
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Vec2 point = new Vec2(input[id], (input[id] * 2.0f));
                    output[id] = point.x + point.y;
                }
                """;
        String structSource = """
                @GPUStruct
                @OpenCLAttributes({"packed"})
                class Vec2 {
                    float x;
                    @OpenCLAttributes({"aligned(8)"})
                    float y;
                }
                """;

        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser methodParser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        net.sixik.ga_utils.javatogpu.frontend.parser.GpuStructParser structParser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuStructParser();
        ParsedGpuMethod kernelMethod = methodParser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuStruct struct = structParser.parseStruct(structSource, "Vec2", "sample.Vec2");

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(), List.of(struct));

        assertEquals("""
                typedef struct __attribute__((packed)) {
                    float x;
                    float y __attribute__((aligned(8)));
                } Vec2;

                __attribute__((reqd_work_group_size(16, 1, 1))) __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    Vec2 point = (Vec2){input[id], (input[id] * 2.0f)};
                    output[id] = (point.x + point.y);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsNestedGpuStructsAndStructConstants() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Vec2 base = new Vec2(input[id], input[id] + Vec2.BIAS);
                    Line line = new Line(base, new Vec2(input[id] * Line.SCALE, input[id] * 4.0f));
                    output[id] = line.start.x + line.end.y;
                }
                """;
        String vec2Source = """
                @GPUStruct
                class Vec2 {
                    static final float BIAS = 1.0f;
                    float x;
                    float y;
                }
                """;
        String lineSource = """
                @GPUStruct
                class Line {
                    static final float SCALE = 0.5f;
                    Vec2 start;
                    Vec2 end;
                }
                """;

        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser methodParser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        net.sixik.ga_utils.javatogpu.frontend.parser.GpuStructParser structParser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuStructParser();
        ParsedGpuMethod kernelMethod = methodParser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuStruct vec2 = structParser.parseStruct(vec2Source, "Vec2", "sample.Vec2");
        ParsedGpuStruct line = structParser.parseStruct(lineSource, "Line", "sample.Line");

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(), List.of(vec2, line));

        assertEquals("""
                typedef struct{
                    float x;
                    float y;
                } Vec2;
                typedef struct{
                    Vec2 start;
                    Vec2 end;
                } Line;

                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    Vec2 base = (Vec2){input[id], (input[id] + 1.0f)};
                    Line line = (Line){base, (Vec2){(input[id] * 0.5f), (input[id] * 4.0f)}};
                    output[id] = (line.start.x + line.end.y);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorHelperProgram() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float2 base = new Float2(input[id], input[id] * 2.0f);
                    Float2 bias = new Float2(1.0f);
                    Float2 shifted = GpuUtils.add(base, bias);
                    output[id] = shifted.x + shifted.y;
                }
                """;
        String helperSource = """
                @CCode
                Float2 add(Float2 left, Float2 right) {
                    return new Float2(left.x + right.x, left.y + right.y);
                }
                """;

        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod helperMethod = parser.parseMethod(helperSource, "GpuUtils", "sample.GpuUtils");

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(helperMethod));

        assertEquals("""
                float2 jtg_fn_GpuUtils_add_Float2_Float2(float2 left, float2 right);

                float2 jtg_fn_GpuUtils_add_Float2_Float2(float2 left, float2 right) {
                    return (float2)((left.x + right.x), (left.y + right.y));
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    float2 base = (float2)(input[id], (input[id] * 2.0f));
                    float2 bias = (float2)(1.0f);
                    float2 shifted = jtg_fn_GpuUtils_add_Float2_Float2(base, bias);
                    output[id] = (shifted.x + shifted.y);
                }""", kernel);
    }

    @Test
    void rejectsNonScalarCastAtFrontendBoundary() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    output[0] = (float) input;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();

        assertThrows(GpuValidationException.class, () -> service.parseAndValidate(methodSource));
    }

    @Test
    void parsesValidatesLowersAndEmitsIfElseKernel() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    if (input[id] > 0.0f) {
                        output[id] = GPU.sin(input[id]);
                    } else {
                        output[id] = GPU.cos(input[id]);
                    }
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    if ((input[id] > 0.0f)) {
                        output[id] = sin(input[id]);
                    } else {
                        output[id] = cos(input[id]);
                    }
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsLogicalConditionKernel() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    if ((input[id] > 0.0f && input[id] < 10.0f) || !(input[id] > 100.0f)) {
                        output[id] = GPU.sin(input[id]);
                    } else {
                        output[id] = GPU.cos(input[id]);
                    }
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    if ((((input[id] > 0.0f) && (input[id] < 10.0f)) || (!(input[id] > 100.0f)))) {
                        output[id] = sin(input[id]);
                    } else {
                        output[id] = cos(input[id]);
                    }
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsElseIfKernel() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    if (input[id] > 10.0f) {
                        output[id] = GPU.sin(input[id]);
                    } else if (input[id] > 0.0f) {
                        output[id] = GPU.cos(input[id]);
                    } else {
                        output[id] = input[id];
                    }
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    if ((input[id] > 10.0f)) {
                        output[id] = sin(input[id]);
                    } else {
                        if ((input[id] > 0.0f)) {
                            output[id] = cos(input[id]);
                        } else {
                            output[id] = input[id];
                        }
                    }
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsTernaryKernel() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = input[id] > 0.0f ? GPU.sin(input[id]) : GPU.cos(input[id]);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = ((input[id] > 0.0f) ? sin(input[id]) : cos(input[id]));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsDivisionModuloKernel() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    int value = -input[id];
                    output[id] = (value / 2) % 3;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int id = get_global_id(0);
                    int value = (-input[id]);
                    output[id] = ((value / 2) % 3);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsBitwiseIntegerKernel() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = ((~input[id]) << 1) ^ ((input[id] >> 1) | (input[id] & 7));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int id = get_global_id(0);
                    output[id] = (((~input[id]) << 1) ^ ((input[id] >> 1) | (input[id] & 7)));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsCompoundAssignmentKernel() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    int value = input[id];
                    value += 2;
                    value <<= 1;
                    for (int i = 3; i > 0; i--) {
                        output[id] += i;
                    }
                    value--;
                    output[id] = value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int id = get_global_id(0);
                    int value = input[id];
                    value = (value + 2);
                    value = (value << 1);
                    for (int i = 3; (i > 0); i = (i - 1)) {
                        output[id] = (output[id] + i);
                    }
                    value = (value - 1);
                    output[id] = value;
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsWhileDoWhileAndSwitchKernel() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int i = 0;
                    while (i < 4) {
                        if ((i % 2) == 0) {
                            i++;
                            continue;
                        }
                        output[i] = input[i];
                        i++;
                    }
                    do {
                        i--;
                    } while (i > 0);
                    switch (input[0] & 3) {
                        case 0:
                            output[0] = 1;
                            break;
                        case 1:
                        case 2:
                            output[0] = 2;
                            break;
                        default:
                            output[0] = 3;
                    }
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int i = 0;
                    while ((i < 4)) {
                        if (((i % 2) == 0)) {
                            i = (i + 1);
                            continue;
                        }
                        output[i] = input[i];
                        i = (i + 1);
                    }
                    do {
                        i = (i - 1);
                    } while ((i > 0));
                    switch ((input[0] & 3)) {
                        case 0:
                            output[0] = 1;
                            break;
                        case 1:
                        case 2:
                            output[0] = 2;
                            break;
                        default:
                            output[0] = 3;
                    }
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsRuleStyleSwitchKernel() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    switch (input[0] & 3) {
                        case 0 -> output[0] = 1;
                        case 1 -> {
                            output[0] = 2;
                        }
                        default -> output[0] = 3;
                    }
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    switch ((input[0] & 3)) {
                        case 0:
                            output[0] = 1;
                            break;
                        case 1:
                            output[0] = 2;
                            break;
                        default:
                            output[0] = 3;
                            break;
                    }
                }""", kernel);
    }

    @Test
    void rejectsNonVoidGpuMethodAtFrontendBoundary() {
        String methodSource = """
                @GPU
                float kernel(@GPUGlobal float[] input) {
                    return input[0];
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();

        assertThrows(GpuValidationException.class, () -> service.parseAndValidate(methodSource));
    }

    @Test
    void rejectsArrayParametersWithoutGlobalAnnotationAtFrontendBoundary() {
        String methodSource = """
                @GPU
                void kernel(float[] input, @GPUGlobal float[] output) {
                    output[0] = input[0];
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();

        assertThrows(GpuValidationException.class, () -> service.parseAndValidate(methodSource));
    }
}
