package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
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
