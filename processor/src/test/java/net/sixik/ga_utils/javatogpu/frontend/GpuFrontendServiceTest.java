package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.validation.GpuValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
