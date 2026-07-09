package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstant;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstantData;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuConstantDataKind;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
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
    void compilesKernelAndReturnsIrGpuArtifact() {
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
        ParsedGpuMethod kernelMethod = new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser()
                .parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod helperMethod = new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser()
                .parseMethod(helperSource, "", "");

        GpuFrontendCompilationResult result = service.compile(
                kernelMethod,
                List.of(helperMethod),
                List.of(),
                "javatogpu/sample/Demo/kernel.cl"
        );

        assertTrue(result.openClSource().contains("inline float jtg_fn_square_float(float value);"));
        assertEquals("javatogpu.irgpu.v1", result.irGpuArtifact().header().format());
        assertEquals(1, result.irGpuArtifact().header().schemaVersion());
        assertEquals("java-source", result.irGpuArtifact().header().sourceFrontend());
        assertEquals("kernel", result.irGpuArtifact().module().entryMethod());
        assertEquals("jtg_kernel", result.irGpuArtifact().module().entryEmittedName());
        assertEquals(1, result.irGpuArtifact().module().helperMethods().size());
        assertEquals("square", result.irGpuArtifact().module().helperMethods().get(0).name());
        assertEquals("jtg_fn_square_float", result.irGpuArtifact().module().helperMethods().get(0).emittedName());
        assertEquals("float", result.irGpuArtifact().module().helperMethods().get(0).returnType());
        assertEquals(1, result.irGpuArtifact().module().helperMethods().get(0).parameters().size());
        assertEquals("value", result.irGpuArtifact().module().helperMethods().get(0).parameters().get(0).name());
        assertEquals("float", result.irGpuArtifact().module().helperMethods().get(0).parameters().get(0).javaType());
        assertEquals(2, result.irGpuArtifact().module().methodBodies().size());
        assertEquals("entry", result.irGpuArtifact().module().methodBodies().get(0).role());
        assertEquals("kernel", result.irGpuArtifact().module().methodBodies().get(0).name());
        assertEquals("jtg_kernel", result.irGpuArtifact().module().methodBodies().get(0).emittedName());
        assertEquals("ir-text-v1", result.irGpuArtifact().module().methodBodies().get(0).format());
        assertTrue(result.irGpuArtifact().module().methodBodies().get(0).typedBody().available());
        assertTrue(result.irGpuArtifact().module().methodBodies().get(0).typedBody().nodes().stream()
                .anyMatch(node -> "GpuIrHelperCall".equals(node.kind())));
        assertEquals("java-source", result.irGpuArtifact().module().methodBodies().get(0).sourceLocation().sourceKind());
        assertEquals("sample.Demo", result.irGpuArtifact().module().methodBodies().get(0).sourceLocation().ownerQualifiedName());
        assertEquals("kernel", result.irGpuArtifact().module().methodBodies().get(0).sourceLocation().methodName());
        assertTrue(result.irGpuArtifact().module().methodBodies().get(0).sourceLocation().knownRange());
        assertTrue(result.irGpuArtifact().module().methodBodies().get(0).body().contains("method jtg_kernel source=kernel"));
        assertTrue(result.irGpuArtifact().module().methodBodies().get(0).body().contains("set output[id] = helper(jtg_fn_square_float args=[input[id]])"));
        assertEquals("helper", result.irGpuArtifact().module().methodBodies().get(1).role());
        assertTrue(result.irGpuArtifact().module().methodBodies().get(1).typedBody().available());
        assertEquals("square", result.irGpuArtifact().module().methodBodies().get(1).name());
        assertEquals("java-source", result.irGpuArtifact().module().methodBodies().get(1).sourceLocation().sourceKind());
        assertTrue(result.irGpuArtifact().module().methodBodies().get(1).body().contains("return (value * value)"));
        assertEquals(1, result.irGpuArtifact().backendOutputs().size());
        assertEquals("opencl", result.irGpuArtifact().backendOutputs().get(0).backend());
        assertEquals("source", result.irGpuArtifact().backendOutputs().get(0).kind());
        assertEquals("opencl-c", result.irGpuArtifact().backendOutputs().get(0).format());
        assertEquals("javatogpu/sample/Demo/kernel.cl", result.irGpuArtifact().backendOutputs().get(0).resource());
        assertEquals("javatogpu/sample/Demo/kernel.cl", result.irGpuArtifact().derivedOpenClResource());

        assertEquals("javatogpu/sample/Demo/kernel.cl", result.openClResource());
        assertEquals("javatogpu/sample/Demo/kernel.irgpu.properties", result.irGpuResource());
        assertFalse(result.irGpuArtifact().regenerationMetadata().backendNeutralSourceReady());
        assertEquals("ir-text-v1", result.irGpuArtifact().regenerationMetadata().payloadFormat());
        assertEquals("derived-opencl-source", result.irGpuArtifact().regenerationMetadata().fallbackSource());
        assertTrue(result.irGpuArtifact().regenerationMetadata().blockers().contains("typed-body-regeneration-not-yet-available"));
        assertEquals("opencl", result.irGpuArtifact().runtimeDefaultBackend());
        assertEquals("off", result.irGpuArtifact().runtimeOptimizationProfile());

        GpuKernelDescriptor descriptor = result.toKernelDescriptor();
        assertEquals("jtg_kernel", descriptor.kernelName());
        assertEquals("javatogpu/sample/Demo/kernel.cl", descriptor.kernelResource());
        assertEquals("javatogpu/sample/Demo/kernel.irgpu.properties", descriptor.irGpuResource());
        assertEquals(result.openClSource(), descriptor.kernelSource());
        assertEquals(2, descriptor.parameterDescriptors().size());
        assertEquals("input", descriptor.parameterDescriptors().get(0).name());
        assertEquals("float[]", descriptor.parameterDescriptors().get(0).javaType());
        assertEquals(GpuKernelParameterAccess.READ_WRITE, descriptor.parameterDescriptors().get(0).access());
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
        assertTrue(exception.getMessage().contains("cast arguments to the exact signature"));
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
    void rejectsUnknownCCodeHelperCallWithQuickFixHint() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = MissingMath.square(input[id]);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> service.validateLowerAndEmit(kernelMethod, List.of())
        );

        assertTrue(exception.getMessage().contains("Unknown @CCode helper call in @GPU method: MissingMath.square"));
        assertTrue(exception.getMessage().contains("declare a matching @CCode helper"));
        assertTrue(exception.getMessage().contains("owner/name mismatch"));
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
    void parsesValidatesLowersAndEmitsEmbeddedGpuConstantDataArray() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = input[id] * LOOKUP[id];
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        ParsedGpuMethod kernelMethod = new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser()
                .parseMethod(
                        methodSource,
                        "Demo",
                        "sample.Demo",
                        List.of(),
                        List.of(new ParsedGpuConstantData("Demo", "sample.Demo", "LOOKUP", "float[]", "{0.25f, 0.5f, 0.25f}", GpuConstantDataKind.EMBEDDED))
                );

        String kernel = service.validateLowerAndEmit(kernelMethod, List.of());

        assertEquals("""
                __constant float LOOKUP[] = {0.25f, 0.5f, 0.25f};

                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = (input[id] * LOOKUP[id]);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsExternGpuConstantDataArray() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = input[id] + LOOKUP[id];
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        ParsedGpuMethod kernelMethod = new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser()
                .parseMethod(
                        methodSource,
                        "Demo",
                        "sample.Demo",
                        List.of(),
                        List.of(new ParsedGpuConstantData("Demo", "sample.Demo", "LOOKUP", "int[]", "null", GpuConstantDataKind.EXTERN))
                );

        String kernel = service.validateLowerAndEmit(kernelMethod, List.of());

        assertEquals("""
                extern __constant int LOOKUP[];

                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int id = get_global_id(0);
                    output[id] = (input[id] + LOOKUP[id]);
                }""", kernel);
    }

    @Test
    void rejectsExternGpuConstantDataWithoutNullDeclarationInitializer() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = input[id] + LOOKUP[id];
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        ParsedGpuMethod kernelMethod = new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser()
                .parseMethod(
                        methodSource,
                        "Demo",
                        "sample.Demo",
                        List.of(),
                        List.of(new ParsedGpuConstantData("Demo", "sample.Demo", "LOOKUP", "int[]", "{1, 2, 3}", GpuConstantDataKind.EXTERN))
                );

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> service.validateLowerAndEmit(kernelMethod, List.of())
        );

        assertTrue(exception.getMessage().contains("@GPUExternConstantData requires a declaration-only null initializer"));
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
    void parsesValidatesLowersAndEmitsVectorFloatDoubleConversions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal double[] output) {
                    int id = GPU.get_global_id(0);
                    Int2 a = new Int2(3, 5);
                    UShort2 b = new UShort2((short) 7, (short) 9);
                    Byte3 c = new Byte3((byte) 1, (byte) 2, (byte) 3);
                    ULong3 d = new ULong3(11L, 13L, 17L);
                    Float2 f0 = GPU.convert_float(a);
                    Float2 f1 = GPU.convert_float(b);
                    Double3 g0 = GPU.convert_double(c);
                    Double3 g1 = GPU.convert_double(d);
                    output[id] = f0.x + f0.y + f1.x + f1.y + g0.x + g0.y + g0.z + g1.x + g1.y + g1.z;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global double* output) {
                    int id = get_global_id(0);
                    int2 a = (int2)(3, 5);
                    ushort2 b = (ushort2)(((short) 7), ((short) 9));
                    char3 c = (char3)(((char) 1), ((char) 2), ((char) 3));
                    ulong3 d = (ulong3)(11L, 13L, 17L);
                    float2 f0 = convert_float(a);
                    float2 f1 = convert_float(b);
                    double3 g0 = convert_double(c);
                    double3 g1 = convert_double(d);
                    output[id] = (((((((((f0.x + f0.y) + f1.x) + f1.y) + g0.x) + g0.y) + g0.z) + g1.x) + g1.y) + g1.z);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsLongBitBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal long[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    long rotated = GPU.rotate(input[id], 3L);
                    output[id] = GPU.popcount(rotated) + GPU.clz(rotated);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global long* input, __global int* output) {
                    int id = get_global_id(0);
                    long rotated = rotate(input[id], 3L);
                    output[id] = (popcount(rotated) + clz(rotated));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsUnsignedConversionAndBitcastBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal double[] input2, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    UInt bits = GPU.as_uint(input[id]);
                    ULong wide = GPU.as_ulong(input2[id]);
                    UInt narrowed = GPU.convert_uint(input2[id]);
                    ULong converted = GPU.convert_ulong(input[id]);
                    float restoredFloat = GPU.as_float(bits);
                    double restoredDouble = GPU.as_double(wide);
                    output[id] = GPU.convert_int(restoredFloat + (float) restoredDouble) + narrowed.value + GPU.convert_int((double) converted.value);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global double* input2, __global int* output) {
                    int id = get_global_id(0);
                    uint bits = as_uint(input[id]);
                    ulong wide = as_ulong(input2[id]);
                    uint narrowed = ((uint) (input2[id]));
                    ulong converted = ((ulong) (input[id]));
                    float restoredFloat = as_float(bits.value);
                    double restoredDouble = as_double(wide.value);
                    output[id] = ((convert_int((restoredFloat + ((float) restoredDouble))) + narrowed) + convert_int(((double) converted)));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsAdditionalSignedScalarConversions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal long[] output) {
                    int id = GPU.get_global_id(0);
                    byte a = (byte) 3;
                    short b = (short) 5;
                    char c = 'A';
                    output[id] = GPU.convert_int(a) + GPU.convert_int(b) + GPU.convert_int(c)
                            + GPU.convert_long(a) + GPU.convert_long(b) + GPU.convert_long(c)
                            + GPU.convert_int(GPU.convert_float(c)) + GPU.convert_int(GPU.convert_double(b));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    char a = ((char) 3);
                    short b = ((short) 5);
                    ushort c = 65;
                    output[id] = (((((((convert_int(a) + convert_int(b)) + convert_int(c)) + convert_long(a)) + convert_long(b)) + convert_long(c)) + convert_int(convert_float(c))) + convert_int(convert_double(b)));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsAdditionalSaturatingScalarConversions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal long[] output) {
                    int id = GPU.get_global_id(0);
                    byte a = (byte) -3;
                    short b = (short) 320;
                    char c = 'C';
                    output[id] = GPU.convert_int_sat(a) + GPU.convert_int_sat(b) + GPU.convert_int_sat(c)
                            + GPU.convert_long_sat(a) + GPU.convert_long_sat(b) + GPU.convert_long_sat(c)
                            + GPU.convert_char_sat(b) + GPU.convert_uchar_sat(a).value
                            + GPU.convert_short_sat(c) + GPU.convert_ushort_sat(c).value
                            + GPU.convert_uint_sat(c).value + GPU.convert_ulong_sat(c).value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    char a = ((char) (-3));
                    short b = ((short) 320);
                    ushort c = 67;
                    output[id] = (((((((((((convert_int_sat(a) + convert_int_sat(b)) + convert_int_sat(c)) + convert_long_sat(a)) + convert_long_sat(b)) + convert_long_sat(c)) + convert_char_sat(b)) + convert_uchar_sat(a)) + convert_short_sat(c)) + convert_ushort_sat(c)) + convert_uint_sat(c)) + convert_ulong_sat(c));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsAdditionalUnsignedScalarConversions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal long[] output) {
                    int id = GPU.get_global_id(0);
                    byte a = (byte) 7;
                    short b = (short) 9;
                    char c = 'B';
                    UInt u0 = GPU.convert_uint(a);
                    UInt u1 = GPU.convert_uint(b);
                    UInt u2 = GPU.convert_uint(c);
                    ULong l0 = GPU.convert_ulong(a);
                    ULong l1 = GPU.convert_ulong(b);
                    ULong l2 = GPU.convert_ulong(c);
                    output[id] = GPU.convert_long(u0) + GPU.convert_long(u1) + GPU.convert_long(u2)
                            + GPU.convert_long(l0) + GPU.convert_long(l1) + GPU.convert_long(l2);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    char a = ((char) 7);
                    short b = ((short) 9);
                    ushort c = 66;
                    uint u0 = ((uint) (a));
                    uint u1 = ((uint) (b));
                    uint u2 = ((uint) (c));
                    ulong l0 = ((ulong) (a));
                    ulong l1 = ((ulong) (b));
                    ulong l2 = ((ulong) (c));
                    output[id] = (((((convert_long(u0) + convert_long(u1)) + convert_long(u2)) + convert_long(l0)) + convert_long(l1)) + convert_long(l2));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsUnsignedAliasNarrowConversions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    UByte a = new UByte((byte) 7);
                    UShort b = new UShort((short) 9);
                    UInt c = new UInt(11);
                    ULong d = new ULong(13L);
                    byte c0 = GPU.convert_char(a);
                    UByte c1 = GPU.convert_uchar(b);
                    short c2 = GPU.convert_short(c);
                    UShort c3 = GPU.convert_ushort(d);
                    output[id] = c0 + c1.value + c2 + c3.value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int id = get_global_id(0);
                    uchar a = ((uchar) ((char) 7));
                    ushort b = ((ushort) ((short) 9));
                    uint c = ((uint) 11);
                    ulong d = ((ulong) 13L);
                    char c0 = convert_char(a);
                    uchar c1 = convert_uchar(b);
                    short c2 = convert_short(c);
                    ushort c3 = convert_ushort(d);
                    output[id] = (((c0 + c1) + c2) + c3);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsUnsignedAliasNarrowSaturatingConversions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    UByte a = new UByte((byte) 7);
                    UShort b = new UShort((short) 320);
                    UInt c = new UInt(11);
                    ULong d = new ULong(13L);
                    byte c0 = GPU.convert_char_sat(a);
                    UByte c1 = GPU.convert_uchar_sat(b);
                    short c2 = GPU.convert_short_sat(c);
                    UShort c3 = GPU.convert_ushort_sat(d);
                    output[id] = c0 + c1.value + c2 + c3.value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int id = get_global_id(0);
                    uchar a = ((uchar) ((char) 7));
                    ushort b = ((ushort) ((short) 320));
                    uint c = ((uint) 11);
                    ulong d = ((ulong) 13L);
                    char c0 = convert_char_sat(a);
                    uchar c1 = convert_uchar_sat(b);
                    short c2 = convert_short_sat(c);
                    ushort c3 = convert_ushort_sat(d);
                    output[id] = (((c0 + c1) + c2) + c3);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsUnsignedAliasCoreWideSaturatingConversions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal long[] output) {
                    int id = GPU.get_global_id(0);
                    UByte a = new UByte((byte) 7);
                    UShort b = new UShort((short) 9);
                    UInt c = new UInt(11);
                    ULong d = new ULong(13L);
                    output[id] = GPU.convert_int_sat(a) + GPU.convert_int_sat(b) + GPU.convert_int_sat(c) + GPU.convert_int_sat(d)
                            + GPU.convert_long_sat(a) + GPU.convert_long_sat(b) + GPU.convert_long_sat(c) + GPU.convert_long_sat(d)
                            + GPU.convert_uint_sat(a).value + GPU.convert_uint_sat(b).value + GPU.convert_uint_sat(c).value + GPU.convert_uint_sat(d).value
                            + GPU.convert_ulong_sat(a).value + GPU.convert_ulong_sat(b).value + GPU.convert_ulong_sat(c).value + GPU.convert_ulong_sat(d).value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    uchar a = ((uchar) ((char) 7));
                    ushort b = ((ushort) ((short) 9));
                    uint c = ((uint) 11);
                    ulong d = ((ulong) 13L);
                    output[id] = (((((((((((((((convert_int_sat(a) + convert_int_sat(b)) + convert_int_sat(c)) + convert_int_sat(d)) + convert_long_sat(a)) + convert_long_sat(b)) + convert_long_sat(c)) + convert_long_sat(d)) + convert_uint_sat(a)) + convert_uint_sat(b)) + convert_uint_sat(c)) + convert_uint_sat(d)) + convert_ulong_sat(a)) + convert_ulong_sat(b)) + convert_ulong_sat(c)) + convert_ulong_sat(d));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsSignedNarrowSourceRegularConversions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    byte a = (byte) 7;
                    short b = (short) 9;
                    char c = 'D';
                    byte c0 = GPU.convert_char(a);
                    UByte c1 = GPU.convert_uchar(b);
                    short c2 = GPU.convert_short(c);
                    UShort c3 = GPU.convert_ushort(a);
                    output[id] = c0 + c1.value + c2 + c3.value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int id = get_global_id(0);
                    char a = ((char) 7);
                    short b = ((short) 9);
                    ushort c = 68;
                    char c0 = convert_char(a);
                    uchar c1 = convert_uchar(b);
                    short c2 = convert_short(c);
                    ushort c3 = convert_ushort(a);
                    output[id] = (((c0 + c1) + c2) + c3);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorCoreWideConversions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal long[] output) {
                    int id = GPU.get_global_id(0);
                    Float2 a = new Float2(1.5f, 2.5f);
                    Double3 b = new Double3(3.5, 4.5, 5.5);
                    Int2 i = GPU.convert_int(a);
                    UInt2 u = GPU.convert_uint(a);
                    Long3 l = GPU.convert_long(b);
                    ULong3 ul = GPU.convert_ulong(b);
                    output[id] = i.x + i.y + u.x + u.y + l.x + l.y + l.z + ul.x + ul.y + ul.z;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    float2 a = (float2)(1.5f, 2.5f);
                    double3 b = (double3)(3.5, 4.5, 5.5);
                    int2 i = convert_int(a);
                    uint2 u = convert_uint(a);
                    long3 l = convert_long(b);
                    ulong3 ul = convert_ulong(b);
                    output[id] = (((((((((i.x + i.y) + u.x) + u.y) + l.x) + l.y) + l.z) + ul.x) + ul.y) + ul.z);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorCoreWideSaturatingConversions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal long[] output) {
                    int id = GPU.get_global_id(0);
                    Float2 a = new Float2(-1.5f, 2.5f);
                    Double3 b = new Double3(3.5, 4.5, 5.5);
                    Int2 i = GPU.convert_int_sat(a);
                    UInt2 u = GPU.convert_uint_sat(a);
                    Long3 l = GPU.convert_long_sat(b);
                    ULong3 ul = GPU.convert_ulong_sat(b);
                    output[id] = i.x + i.y + u.x + u.y + l.x + l.y + l.z + ul.x + ul.y + ul.z;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    float2 a = (float2)((-1.5f), 2.5f);
                    double3 b = (double3)(3.5, 4.5, 5.5);
                    int2 i = convert_int_sat(a);
                    uint2 u = convert_uint_sat(a);
                    long3 l = convert_long_sat(b);
                    ulong3 ul = convert_ulong_sat(b);
                    output[id] = (((((((((i.x + i.y) + u.x) + u.y) + l.x) + l.y) + l.z) + ul.x) + ul.y) + ul.z);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsUnsignedNarrowVectorConversions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    Float2 a = new Float2(7.5f, 320.25f);
                    Double3 b = new Double3(-2.0, 500.0, 70000.0);
                    UByte2 c0 = GPU.convert_uchar(a);
                    UShort2 c1 = GPU.convert_ushort(a);
                    UByte3 c2 = GPU.convert_uchar_sat(b);
                    UShort3 c3 = GPU.convert_ushort_sat(b);
                    output[id] = c0.x + c0.y + c1.x + c1.y + c2.x + c2.y + c2.z + c3.x + c3.y + c3.z;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int id = get_global_id(0);
                    float2 a = (float2)(7.5f, 320.25f);
                    double3 b = (double3)((-2.0), 500.0, 70000.0);
                    uchar2 c0 = convert_uchar(a);
                    ushort2 c1 = convert_ushort(a);
                    uchar3 c2 = convert_uchar_sat(b);
                    ushort3 c3 = convert_ushort_sat(b);
                    output[id] = (((((((((c0.x + c0.y) + c1.x) + c1.y) + c2.x) + c2.y) + c2.z) + c3.x) + c3.y) + c3.z);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsSignedNarrowVectorConversions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    Float2 a = new Float2(7.5f, 320.25f);
                    Double3 b = new Double3(-2.0, 500.0, 70000.0);
                    Byte2 c0 = GPU.convert_char(a);
                    Short2 c1 = GPU.convert_short(a);
                    Byte3 c2 = GPU.convert_char_sat(b);
                    Short3 c3 = GPU.convert_short_sat(b);
                    output[id] = c0.x + c0.y + c1.x + c1.y + c2.x + c2.y + c2.z + c3.x + c3.y + c3.z;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int id = get_global_id(0);
                    float2 a = (float2)(7.5f, 320.25f);
                    double3 b = (double3)((-2.0), 500.0, 70000.0);
                    char2 c0 = convert_char(a);
                    short2 c1 = convert_short(a);
                    char3 c2 = convert_char_sat(b);
                    short3 c3 = convert_short_sat(b);
                    output[id] = (((((((((c0.x + c0.y) + c1.x) + c1.y) + c2.x) + c2.y) + c2.z) + c3.x) + c3.y) + c3.z);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorGeometricBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float3 a = new Float3(1.0f, 2.0f, 3.0f);
                    Float3 b = new Float3(4.0f, 5.0f, 6.0f);
                    Float3 normal = GPU.normalize(a);
                    Float3 c = GPU.cross(a, b);
                    output[id] = GPU.dot(a, b) + GPU.distance(a, b) + GPU.length(c) + normal.x;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float3 a = (float3)(1.0f, 2.0f, 3.0f);
                    float3 b = (float3)(4.0f, 5.0f, 6.0f);
                    float3 normal = normalize(a);
                    float3 c = cross(a, b);
                    output[id] = (((dot(a, b) + length((a) - (b))) + sqrt(dot(c, c))) + normal.x);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsDoubleVectorGeometricBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal double[] output) {
                    int id = GPU.get_global_id(0);
                    Double2 a = new Double2(1.0, 2.0);
                    Double2 b = new Double2(3.0, 4.0);
                    Double2 n = GPU.normalize(a);
                    output[id] = GPU.dot(a, b) + GPU.distance(a, b) + GPU.length(n);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global double* output) {
                    int id = get_global_id(0);
                    double2 a = (double2)(1.0, 2.0);
                    double2 b = (double2)(3.0, 4.0);
                    double2 n = normalize(a);
                    output[id] = ((dot(a, b) + length((a) - (b))) + sqrt(dot(n, n)));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsAdditionalIntegerCommonBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    int mul = GPU.mul24(input[id], 3);
                    int madd = GPU.mad24(mul, 2, 7);
                    int bits = GPU.bitselect(madd, 255, 15);
                    int packed = GPU.upsample((short) 1, (short) bits);
                    output[id] = GPU.select(bits, packed, bits > 0);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int id = get_global_id(0);
                    int mul = mul24(input[id], 3);
                    int madd = mad24(mul, 2, 7);
                    int bits = bitselect(madd, 255, 15);
                    int packed = upsample(((short) 1), ((short) bits));
                    output[id] = (((bits > 0)) ? (packed) : (bits));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorIntegerCommonBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    Int3 a = new Int3(2, 3, 4);
                    Int3 b = new Int3(5, 6, 7);
                    Int3 c = new Int3(1, 2, 3);
                    Int3 mul = GPU.mul24(a, b);
                    Int3 madd = GPU.mad24(mul, b, c);
                    output[id] = madd.x + madd.y + madd.z;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int id = get_global_id(0);
                    int3 a = (int3)(2, 3, 4);
                    int3 b = (int3)(5, 6, 7);
                    int3 c = (int3)(1, 2, 3);
                    int3 mul = mul24(a, b);
                    int3 madd = mad24(mul, b, c);
                    output[id] = ((madd.x + madd.y) + madd.z);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorIntegerHighHalfBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal long[] output) {
                    int id = GPU.get_global_id(0);
                    Int2 a = new Int2(10, 20);
                    Int2 b = new Int2(3, 5);
                    Int2 i = GPU.hadd(a, b).add(GPU.mul_hi(a, b));
                    Long2 x = new Long2(11L, 21L);
                    Long2 y = new Long2(7L, 9L);
                    Long2 l = GPU.rhadd(x, y).add(GPU.mad_hi(x, y, new Long2(1L, 2L)));
                    output[id] = i.x + i.y + l.x + l.y;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    int2 a = (int2)(10, 20);
                    int2 b = (int2)(3, 5);
                    int2 i = (hadd(a, b) + mul_hi(a, b));
                    long2 x = (long2)(11L, 21L);
                    long2 y = (long2)(7L, 9L);
                    long2 l = (rhadd(x, y) + mad_hi(x, y, (long2)(1L, 2L)));
                    output[id] = (((i.x + i.y) + l.x) + l.y);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorSaturatingArithmeticBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal long[] output) {
                    int id = GPU.get_global_id(0);
                    Int2 a = new Int2(10, 20);
                    Int2 b = new Int2(3, 4);
                    Int2 c = GPU.add_sat(a, b).add(GPU.mad_sat(a, b, new Int2(1, 2)));
                    Long2 x = new Long2(11L, 21L);
                    Long2 y = new Long2(5L, 7L);
                    Long2 z = GPU.sub_sat(x, y).add(GPU.mul_sat(x, y));
                    output[id] = c.x + c.y + z.x + z.y;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    int2 a = (int2)(10, 20);
                    int2 b = (int2)(3, 4);
                    int2 c = (add_sat(a, b) + mad_sat(a, b, (int2)(1, 2)));
                    long2 x = (long2)(11L, 21L);
                    long2 y = (long2)(5L, 7L);
                    long2 z = (sub_sat(x, y) + mul_sat(x, y));
                    output[id] = (((c.x + c.y) + z.x) + z.y);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorAbsDiffBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal long[] output) {
                    int id = GPU.get_global_id(0);
                    Int3 a = new Int3(10, 3, -4);
                    Int3 b = new Int3(2, 9, 5);
                    Int3 signed = GPU.abs_diff(a, b);
                    UInt8 c = new UInt8(30, 40, 50, 60, 70, 80, 90, 100);
                    UInt8 d = new UInt8(10, 15, 20, 25, 30, 35, 40, 45);
                    UInt8 unsigned = GPU.abs_diff(c, d);
                    output[id] = signed.x + signed.y + signed.z + unsigned.s0 + unsigned.s7;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    int3 a = (int3)(10, 3, (-4));
                    int3 b = (int3)(2, 9, 5);
                    int3 signed = abs_diff(a, b);
                    uint8 c = (uint8)(30, 40, 50, 60, 70, 80, 90, 100);
                    uint8 d = (uint8)(10, 15, 20, 25, 30, 35, 40, 45);
                    uint8 unsigned = abs_diff(c, d);
                    output[id] = ((((signed.x + signed.y) + signed.z) + unsigned.s0) + unsigned.s7);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorCommonBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float4 a = new Float4(1.0f, 2.0f, 3.0f, 4.0f);
                    Float4 b = new Float4(5.0f, 6.0f, 7.0f, 8.0f);
                    Float4 mixed = GPU.mix(a, b, 0.25f);
                    Float4 clipped = GPU.clamp(mixed, new Float4(0.0f), new Float4(6.0f));
                    Float4 stepped = GPU.step(new Float4(2.0f), clipped);
                    Float4 smoothed = GPU.smoothstep(new Float4(0.0f), new Float4(6.0f), clipped);
                    output[id] = stepped.x + smoothed.y;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float4 a = (float4)(1.0f, 2.0f, 3.0f, 4.0f);
                    float4 b = (float4)(5.0f, 6.0f, 7.0f, 8.0f);
                    float4 mixed = mix(a, b, 0.25f);
                    float4 clipped = clamp(mixed, (float4)(0.0f), (float4)(6.0f));
                    float4 stepped = step((float4)(2.0f), clipped);
                    float4 smoothed = smoothstep((float4)(0.0f), (float4)(6.0f), clipped);
                    output[id] = (stepped.x + smoothed.y);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsDoubleVectorCommonBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal double[] output) {
                    int id = GPU.get_global_id(0);
                    Double3 a = new Double3(1.0, 2.0, 3.0);
                    Double3 b = new Double3(4.0, 5.0, 6.0);
                    Double3 mixed = GPU.mix(a, b, 0.5);
                    Double3 clipped = GPU.clamp(mixed, new Double3(1.5), new Double3(5.0));
                    Double3 stepped = GPU.step(new Double3(2.0), clipped);
                    Double3 smoothed = GPU.smoothstep(new Double3(1.5), new Double3(5.0), clipped);
                    output[id] = stepped.x + smoothed.z;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global double* output) {
                    int id = get_global_id(0);
                    double3 a = (double3)(1.0, 2.0, 3.0);
                    double3 b = (double3)(4.0, 5.0, 6.0);
                    double3 mixed = mix(a, b, 0.5);
                    double3 clipped = clamp(mixed, (double3)(1.5), (double3)(5.0));
                    double3 stepped = step((double3)(2.0), clipped);
                    double3 smoothed = smoothstep((double3)(1.5), (double3)(5.0), clipped);
                    output[id] = (stepped.x + smoothed.z);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsBroadcastVectorCommonBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float2 a = new Float2(1.0f, 4.0f);
                    Float2 b = new Float2(3.0f, 8.0f);
                    Float2 mixed = GPU.mix(a, b, new Float2(0.25f, 0.75f));
                    Float2 stepped = GPU.step(2.0f, mixed);
                    Float2 smoothed = GPU.smoothstep(0.0f, 6.0f, mixed);
                    output[id] = stepped.x + stepped.y + smoothed.x + smoothed.y;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float2 a = (float2)(1.0f, 4.0f);
                    float2 b = (float2)(3.0f, 8.0f);
                    float2 mixed = mix(a, b, (float2)(0.25f, 0.75f));
                    float2 stepped = step(2.0f, mixed);
                    float2 smoothed = smoothstep(0.0f, 6.0f, mixed);
                    output[id] = (((stepped.x + stepped.y) + smoothed.x) + smoothed.y);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsScalarBroadcastVectorMinMaxClampBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float3 value = new Float3(-2.0f, 1.5f, 9.0f);
                    Float3 bounded = GPU.fmin(GPU.fmax(value, 0.5f), 6.0f);
                    Float3 clipped = GPU.clamp(bounded, 1.0f, 5.0f);
                    output[id] = clipped.x + clipped.y + clipped.z;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float3 value = (float3)((-2.0f), 1.5f, 9.0f);
                    float3 bounded = fmin(fmax(value, 0.5f), 6.0f);
                    float3 clipped = clamp(bounded, 1.0f, 5.0f);
                    output[id] = ((clipped.x + clipped.y) + clipped.z);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorMadBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float3 a = new Float3(1.0f, 2.0f, 3.0f);
                    Float3 b = new Float3(4.0f, 5.0f, 6.0f);
                    Float3 c = new Float3(0.5f, 1.0f, 1.5f);
                    Float3 value = GPU.mad(a, b, c);
                    output[id] = value.x + value.y + value.z;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float3 a = (float3)(1.0f, 2.0f, 3.0f);
                    float3 b = (float3)(4.0f, 5.0f, 6.0f);
                    float3 c = (float3)(0.5f, 1.0f, 1.5f);
                    float3 value = mad(a, b, c);
                    output[id] = ((value.x + value.y) + value.z);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorFmaBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal double[] output) {
                    int id = GPU.get_global_id(0);
                    Double3 a = new Double3(1.0, 2.0, 3.0);
                    Double3 b = new Double3(4.0, 5.0, 6.0);
                    Double3 c = new Double3(0.5, 1.0, 1.5);
                    Double3 value = GPU.fma(a, b, c);
                    output[id] = value.x + value.y + value.z;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global double* output) {
                    int id = get_global_id(0);
                    double3 a = (double3)(1.0, 2.0, 3.0);
                    double3 b = (double3)(4.0, 5.0, 6.0);
                    double3 c = (double3)(0.5, 1.0, 1.5);
                    double3 value = fma(a, b, c);
                    output[id] = ((value.x + value.y) + value.z);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsAdditionalScalarCommonBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    float a = GPU.minmag(input[id], -2.0f);
                    float b = GPU.maxmag(input[id], 0.5f);
                    float c = GPU.saturate(a + b);
                    output[id] = GPU.abs_diff((int) (a * 10.0f), (int) (b * 10.0f)) + GPU.convert_int(c * 5.0f);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global int* output) {
                    int id = get_global_id(0);
                    float a = minmag(input[id], (-2.0f));
                    float b = maxmag(input[id], 0.5f);
                    float c = saturate((a + b));
                    output[id] = (abs_diff(((int) (a * 10.0f)), ((int) (b * 10.0f))) + convert_int((c * 5.0f)));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsAdditionalVectorCommonBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float3 value = new Float3(-0.25f, 0.5f, 1.75f);
                    Float3 s = GPU.sign(value);
                    Float3 f = GPU.fract(value);
                    Float3 sat = GPU.saturate(value);
                    output[id] = s.x + f.y + sat.z;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float3 value = (float3)((-0.25f), 0.5f, 1.75f);
                    float3 s = sign(value);
                    float3 f = fract(value);
                    float3 sat = saturate(value);
                    output[id] = ((s.x + f.y) + sat.z);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsIntegerVectorCommonBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    Int4 a = new Int4(1, 8, 3, 10);
                    Int4 b = new Int4(5, 2, 7, 4);
                    Int4 lo = GPU.min(a, b);
                    Int4 hi = GPU.max(a, b);
                    Int4 clipped = GPU.clamp(hi, new Int4(2), new Int4(6));
                    output[id] = lo.x + lo.y + clipped.z + clipped.w;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int id = get_global_id(0);
                    int4 a = (int4)(1, 8, 3, 10);
                    int4 b = (int4)(5, 2, 7, 4);
                    int4 lo = min(a, b);
                    int4 hi = max(a, b);
                    int4 clipped = clamp(hi, (int4)(2), (int4)(6));
                    output[id] = (((lo.x + lo.y) + clipped.z) + clipped.w);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsUnsignedIntegerVectorCommonBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    UInt3 a = new UInt3(1, 8, 3);
                    UInt3 b = new UInt3(5, 2, 7);
                    UInt3 hi = GPU.max(a, b);
                    UInt3 clipped = GPU.clamp(hi, new UInt3(2), new UInt3(6));
                    UInt3 lo = GPU.min(clipped, new UInt3(5));
                    output[id] = lo.x + lo.y + lo.z;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int id = get_global_id(0);
                    uint3 a = (uint3)(1, 8, 3);
                    uint3 b = (uint3)(5, 2, 7);
                    uint3 hi = max(a, b);
                    uint3 clipped = clamp(hi, (uint3)(2), (uint3)(6));
                    uint3 lo = min(clipped, (uint3)(5));
                    output[id] = ((lo.x + lo.y) + lo.z);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsSmallUnsignedVectorCommonBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    UByte3 a = new UByte3((byte) 1, (byte) 8, (byte) 3);
                    UByte3 b = new UByte3((byte) 5, (byte) 2, (byte) 7);
                    UByte3 lo = GPU.min(a, b);
                    UShort4 c = new UShort4((short) 9, (short) 1, (short) 12, (short) 4);
                    UShort4 d = new UShort4((short) 2, (short) 5, (short) 7, (short) 10);
                    UShort4 hi = GPU.max(c, d);
                    UShort4 clipped = GPU.clamp(hi, new UShort4((short) 3), new UShort4((short) 8));
                    output[id] = lo.x + lo.y + lo.z + clipped.w;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int id = get_global_id(0);
                    uchar3 a = (uchar3)(((char) 1), ((char) 8), ((char) 3));
                    uchar3 b = (uchar3)(((char) 5), ((char) 2), ((char) 7));
                    uchar3 lo = min(a, b);
                    ushort4 c = (ushort4)(((short) 9), ((short) 1), ((short) 12), ((short) 4));
                    ushort4 d = (ushort4)(((short) 2), ((short) 5), ((short) 7), ((short) 10));
                    ushort4 hi = max(c, d);
                    ushort4 clipped = clamp(hi, (ushort4)(((short) 3)), (ushort4)(((short) 8)));
                    output[id] = (((lo.x + lo.y) + lo.z) + clipped.w);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsSignedNarrowVectorCommonBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    Byte3 a = new Byte3((byte) 1, (byte) 8, (byte) 3);
                    Byte3 b = new Byte3((byte) 5, (byte) 2, (byte) 7);
                    Byte3 lo = GPU.min(a, b);
                    Short4 c = new Short4((short) 9, (short) 1, (short) 12, (short) 4);
                    Short4 d = new Short4((short) 2, (short) 5, (short) 7, (short) 10);
                    Short4 hi = GPU.max(c, d);
                    Short4 clipped = GPU.clamp(hi, new Short4((short) 3), new Short4((short) 8));
                    output[id] = lo.x + lo.y + lo.z + clipped.w;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int id = get_global_id(0);
                    char3 a = (char3)(((char) 1), ((char) 8), ((char) 3));
                    char3 b = (char3)(((char) 5), ((char) 2), ((char) 7));
                    char3 lo = min(a, b);
                    short4 c = (short4)(((short) 9), ((short) 1), ((short) 12), ((short) 4));
                    short4 d = (short4)(((short) 2), ((short) 5), ((short) 7), ((short) 10));
                    short4 hi = max(c, d);
                    short4 clipped = clamp(hi, (short4)(((short) 3)), (short4)(((short) 8)));
                    output[id] = (((lo.x + lo.y) + lo.z) + clipped.w);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsWideUnsignedVectorCommonBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    UInt8 a = new UInt8(1, 8, 3, 9, 2, 7, 4, 6);
                    UInt8 b = new UInt8(5, 2, 7, 4, 6, 1, 9, 3);
                    UInt8 hi = GPU.max(a, b);
                    UInt8 clipped = GPU.clamp(hi, new UInt8(2), new UInt8(6));
                    UShort16 c = new UShort16((short) 9, (short) 1, (short) 12, (short) 4, (short) 8, (short) 3, (short) 15, (short) 2, (short) 5, (short) 11, (short) 6, (short) 13, (short) 7, (short) 10, (short) 14, (short) 16);
                    UShort16 d = new UShort16((short) 2, (short) 5, (short) 7, (short) 10, (short) 1, (short) 8, (short) 6, (short) 9, (short) 3, (short) 4, (short) 12, (short) 11, (short) 13, (short) 14, (short) 15, (short) 0);
                    UShort16 lo = GPU.min(c, d);
                    output[id] = clipped.s0 + clipped.s1 + clipped.s2 + lo.s0 + lo.s1 + lo.sf;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int id = get_global_id(0);
                    uint8 a = (uint8)(1, 8, 3, 9, 2, 7, 4, 6);
                    uint8 b = (uint8)(5, 2, 7, 4, 6, 1, 9, 3);
                    uint8 hi = max(a, b);
                    uint8 clipped = clamp(hi, (uint8)(2), (uint8)(6));
                    ushort16 c = (ushort16)(((short) 9), ((short) 1), ((short) 12), ((short) 4), ((short) 8), ((short) 3), ((short) 15), ((short) 2), ((short) 5), ((short) 11), ((short) 6), ((short) 13), ((short) 7), ((short) 10), ((short) 14), ((short) 16));
                    ushort16 d = (ushort16)(((short) 2), ((short) 5), ((short) 7), ((short) 10), ((short) 1), ((short) 8), ((short) 6), ((short) 9), ((short) 3), ((short) 4), ((short) 12), ((short) 11), ((short) 13), ((short) 14), ((short) 15), ((short) 0));
                    ushort16 lo = min(c, d);
                    output[id] = (((((clipped.s0 + clipped.s1) + clipped.s2) + lo.s0) + lo.s1) + lo.sf);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsAdditionalVectorMathBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float2 a = new Float2(-1.25f, 2.75f);
                    Float2 b = new Float2(2.0f, 3.0f);
                    Float2 abs = GPU.fabs(a);
                    Float2 floored = GPU.floor(a);
                    Float2 ceiled = GPU.ceil(a);
                    Float2 rounded = GPU.round(a);
                    Float2 powered = GPU.pow(abs, b);
                    Float2 limited = GPU.fmin(GPU.fmax(powered, new Float2(1.0f)), new Float2(8.0f));
                    Float2 inv = GPU.rsqrt(limited);
                    output[id] = floored.x + ceiled.y + rounded.x + inv.y;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float2 a = (float2)((-1.25f), 2.75f);
                    float2 b = (float2)(2.0f, 3.0f);
                    float2 abs = fabs(a);
                    float2 floored = floor(a);
                    float2 ceiled = ceil(a);
                    float2 rounded = round(a);
                    float2 powered = pow(abs, b);
                    float2 limited = fmin(fmax(powered, (float2)(1.0f)), (float2)(8.0f));
                    float2 inv = rsqrt(limited);
                    output[id] = (((floored.x + ceiled.y) + rounded.x) + inv.y);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsAdditionalVectorAngleBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float3 degrees = new Float3(90.0f, -180.0f, 45.0f);
                    Float3 radians = GPU.radians(degrees);
                    Float3 roundTrip = GPU.degrees(radians);
                    Float3 signed = GPU.copysign(roundTrip, new Float3(-1.0f, 1.0f, -1.0f));
                    output[id] = signed.x + signed.y + signed.z;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float3 degrees = (float3)(90.0f, (-180.0f), 45.0f);
                    float3 radians = radians(degrees);
                    float3 roundTrip = degrees(radians);
                    float3 signed = copysign(roundTrip, (float3)((-1.0f), 1.0f, (-1.0f)));
                    output[id] = ((signed.x + signed.y) + signed.z);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorMinMagMaxMagBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float4 a = new Float4(-1.0f, 2.5f, -4.0f, 0.25f);
                    Float4 b = new Float4(3.0f, -1.5f, 2.0f, -0.5f);
                    Float4 lo = GPU.minmag(a, b);
                    Float4 hi = GPU.maxmag(a, b);
                    output[id] = lo.x + lo.y + hi.z + hi.w;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float4 a = (float4)((-1.0f), 2.5f, (-4.0f), 0.25f);
                    float4 b = (float4)(3.0f, (-1.5f), 2.0f, (-0.5f));
                    float4 lo = minmag(a, b);
                    float4 hi = maxmag(a, b);
                    output[id] = (((lo.x + lo.y) + hi.z) + hi.w);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorTranscendentalBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float2 angle = new Float2(0.5f, 1.0f);
                    Float2 trig = GPU.sin(angle).add(GPU.cos(angle));
                    Float2 rooted = GPU.sqrt(GPU.exp(new Float2(1.0f, 4.0f)));
                    Float2 scaled = GPU.log2(GPU.exp(new Float2(2.0f, 8.0f)));
                    output[id] = trig.x + rooted.y + scaled.x + GPU.tan(angle).y;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float2 angle = (float2)(0.5f, 1.0f);
                    float2 trig = (sin(angle) + cos(angle));
                    float2 rooted = sqrt(exp((float2)(1.0f, 4.0f)));
                    float2 scaled = log2(exp((float2)(2.0f, 8.0f)));
                    output[id] = (((trig.x + rooted.y) + scaled.x) + tan(angle).y);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorHyperbolicBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float2 value = new Float2(0.25f, 0.5f);
                    Float2 hyper = GPU.sinh(value).add(GPU.cosh(value));
                    Float2 limited = GPU.tanh(value);
                    output[id] = hyper.x + hyper.y + limited.x + limited.y;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float2 value = (float2)(0.25f, 0.5f);
                    float2 hyper = (sinh(value) + cosh(value));
                    float2 limited = tanh(value);
                    output[id] = (((hyper.x + hyper.y) + limited.x) + limited.y);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsAdditionalVectorTranscendentalBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float2 value = new Float2(2.0f, 100.0f);
                    Float2 raised = GPU.exp2(new Float2(3.0f, 4.0f));
                    Float2 logged = GPU.log10(value);
                    output[id] = raised.x + raised.y + logged.x + logged.y;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float2 value = (float2)(2.0f, 100.0f);
                    float2 raised = exp2((float2)(3.0f, 4.0f));
                    float2 logged = log10(value);
                    output[id] = (((raised.x + raised.y) + logged.x) + logged.y);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorCbrtBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float2 value = new Float2(8.0f, 27.0f);
                    Float2 rooted = GPU.cbrt(value);
                    output[id] = rooted.x + rooted.y;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float2 value = (float2)(8.0f, 27.0f);
                    float2 rooted = cbrt(value);
                    output[id] = (rooted.x + rooted.y);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorHypotBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float2 x = new Float2(3.0f, 5.0f);
                    Float2 y = new Float2(4.0f, 12.0f);
                    Float2 value = GPU.hypot(x, y);
                    output[id] = value.x + value.y;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float2 x = (float2)(3.0f, 5.0f);
                    float2 y = (float2)(4.0f, 12.0f);
                    float2 value = hypot(x, y);
                    output[id] = (value.x + value.y);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorRemainderBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal double[] output) {
                    int id = GPU.get_global_id(0);
                    Double2 a = new Double2(5.5, 9.0);
                    Double2 b = new Double2(2.0, 4.0);
                    Double2 mod = GPU.fmod(a, b);
                    Double2 rem = GPU.remainder(a, b);
                    output[id] = mod.x + mod.y + rem.x + rem.y;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global double* output) {
                    int id = get_global_id(0);
                    double2 a = (double2)(5.5, 9.0);
                    double2 b = (double2)(2.0, 4.0);
                    double2 mod = fmod(a, b);
                    double2 rem = remainder(a, b);
                    output[id] = (((mod.x + mod.y) + rem.x) + rem.y);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorNextafterLdexpBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float2 start = new Float2(1.0f, 2.0f);
                    Float2 direction = new Float2(2.0f, 1.0f);
                    Float2 nudged = GPU.nextafter(start, direction);
                    Int2 exponents = new Int2(1, 2);
                    Float2 scaled = GPU.ldexp(nudged, exponents);
                    output[id] = scaled.x + scaled.y;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float2 start = (float2)(1.0f, 2.0f);
                    float2 direction = (float2)(2.0f, 1.0f);
                    float2 nudged = nextafter(start, direction);
                    int2 exponents = (int2)(1, 2);
                    float2 scaled = ldexp(nudged, exponents);
                    output[id] = (scaled.x + scaled.y);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorPownBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal double[] output) {
                    int id = GPU.get_global_id(0);
                    Double3 value = new Double3(2.0, 3.0, 4.0);
                    Int3 exponent = new Int3(3, 2, 1);
                    Double3 powered = GPU.pown(value, exponent);
                    output[id] = powered.x + powered.y + powered.z;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global double* output) {
                    int id = get_global_id(0);
                    double3 value = (double3)(2.0, 3.0, 4.0);
                    int3 exponent = (int3)(3, 2, 1);
                    double3 powered = pown(value, exponent);
                    output[id] = ((powered.x + powered.y) + powered.z);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorRootnBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float3 value = new Float3(8.0f, 27.0f, 16.0f);
                    Int3 exponent = new Int3(3, 3, 2);
                    Float3 rooted = GPU.rootn(value, exponent);
                    output[id] = rooted.x + rooted.y + rooted.z;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float3 value = (float3)(8.0f, 27.0f, 16.0f);
                    int3 exponent = (int3)(3, 3, 2);
                    float3 rooted = rootn(value, exponent);
                    output[id] = ((rooted.x + rooted.y) + rooted.z);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorPowrBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float2 value = new Float2(4.0f, 9.0f);
                    Float2 exponent = new Float2(0.5f, 0.5f);
                    Float2 powered = GPU.powr(value, exponent);
                    output[id] = powered.x + powered.y;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float2 value = (float2)(4.0f, 9.0f);
                    float2 exponent = (float2)(0.5f, 0.5f);
                    float2 powered = powr(value, exponent);
                    output[id] = (powered.x + powered.y);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsVectorInverseTrigBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float2 input = new Float2(0.25f, 0.5f);
                    Float2 arcs = GPU.asin(input).add(GPU.acos(input));
                    Float2 dirs = GPU.atan(input).add(GPU.atan2(input, new Float2(1.0f, 2.0f)));
                    output[id] = arcs.x + arcs.y + dirs.x + dirs.y;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    float2 input = (float2)(0.25f, 0.5f);
                    float2 arcs = (asin(input) + acos(input));
                    float2 dirs = (atan(input) + atan2(input, (float2)(1.0f, 2.0f)));
                    output[id] = (((arcs.x + arcs.y) + dirs.x) + dirs.y);
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
    void parsesValidatesLowersAndEmitsUnsignedImageBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image2DReadOnly inputImage, Image2DWriteOnly outputImage, Sampler sampler, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    Int2 coords = new Int2(id, 0);
                    UInt4 pixel = GPU.read_imageui(inputImage, sampler, coords);
                    output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
                    GPU.write_imageui(outputImage, coords, new UInt4(9, 10, 11, 12));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                    int id = get_global_id(0);
                    int2 coords = (int2)(id, 0);
                    uint4 pixel = read_imageui(inputImage, sampler, coords);
                    output[id] = (((pixel.x + pixel.y) + pixel.z) + pixel.w);
                    write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsImage3dBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image3DReadOnly inputImage, Image3DWriteOnly outputImage, Sampler sampler, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Int4 coords = new Int4(id, 0, 0, 0);
                    Float4 pixel = GPU.read_imagef(inputImage, sampler, coords);
                    output[id] = pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_depth(inputImage);
                    GPU.write_imagef(outputImage, coords, new Float4(0.25f, 0.5f, 0.75f, 1.0f));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image3d_t inputImage, write_only image3d_t outputImage, sampler_t sampler, __global float* output) {
                    int id = get_global_id(0);
                    int4 coords = (int4)(id, 0, 0, 0);
                    float4 pixel = read_imagef(inputImage, sampler, coords);
                    output[id] = ((((pixel.x + pixel.y) + pixel.z) + pixel.w) + get_image_depth(inputImage));
                    write_imagef(outputImage, coords, (float4)(0.25f, 0.5f, 0.75f, 1.0f));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsUnsignedImage3dBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image3DReadOnly inputImage, Image3DWriteOnly outputImage, Sampler sampler, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    Int4 coords = new Int4(id, 0, 0, 0);
                    UInt4 pixel = GPU.read_imageui(inputImage, sampler, coords);
                    output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
                    GPU.write_imageui(outputImage, coords, new UInt4(9, 10, 11, 12));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image3d_t inputImage, write_only image3d_t outputImage, sampler_t sampler, __global int* output) {
                    int id = get_global_id(0);
                    int4 coords = (int4)(id, 0, 0, 0);
                    uint4 pixel = read_imageui(inputImage, sampler, coords);
                    output[id] = (((pixel.x + pixel.y) + pixel.z) + pixel.w);
                    write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsSamplerlessImageBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image2DReadOnly inputImage, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    Int2 coords = new Int2(id, 0);
                    UInt4 pixel = GPU.read_imageui(inputImage, coords);
                    output[id] = pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_width(inputImage);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image2d_t inputImage, __global int* output) {
                    int id = get_global_id(0);
                    int2 coords = (int2)(id, 0);
                    uint4 pixel = read_imageui(inputImage, coords);
                    output[id] = ((((pixel.x + pixel.y) + pixel.z) + pixel.w) + get_image_width(inputImage));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsSamplerlessImage3dBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image3DReadOnly inputImage, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Int4 coords = new Int4(id, 0, 0, 0);
                    Float4 pixel = GPU.read_imagef(inputImage, coords);
                    output[id] = pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_depth(inputImage);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image3d_t inputImage, __global float* output) {
                    int id = get_global_id(0);
                    int4 coords = (int4)(id, 0, 0, 0);
                    float4 pixel = read_imagef(inputImage, coords);
                    output[id] = ((((pixel.x + pixel.y) + pixel.z) + pixel.w) + get_image_depth(inputImage));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsImageMetadataBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image2DReadOnly inputImage, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    int channelOrder = GPU.get_image_channel_order(inputImage);
                    int channelType = GPU.get_image_channel_data_type(inputImage);
                    output[id] = channelOrder == GPU.CL_RGBA && channelType == GPU.CL_UNSIGNED_INT32 ? 1 : 0;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image2d_t inputImage, __global int* output) {
                    int id = get_global_id(0);
                    int channelOrder = get_image_channel_order(inputImage);
                    int channelType = get_image_channel_data_type(inputImage);
                    output[id] = (((channelOrder == %d) && (channelType == %d)) ? 1 : 0);
                }""".formatted(org.lwjgl.opencl.CL10.CL_RGBA, org.lwjgl.opencl.CL10.CL_UNSIGNED_INT32), kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsImage3dMetadataBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image3DReadOnly inputImage, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    int channelOrder = GPU.get_image_channel_order(inputImage);
                    int channelType = GPU.get_image_channel_data_type(inputImage);
                    output[id] = channelOrder == GPU.CL_RGBA && channelType == GPU.CL_FLOAT ? GPU.get_image_depth(inputImage) : 0;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image3d_t inputImage, __global int* output) {
                    int id = get_global_id(0);
                    int channelOrder = get_image_channel_order(inputImage);
                    int channelType = get_image_channel_data_type(inputImage);
                    output[id] = (((channelOrder == %d) && (channelType == %d)) ? get_image_depth(inputImage) : 0);
                }""".formatted(org.lwjgl.opencl.CL10.CL_RGBA, org.lwjgl.opencl.CL10.CL_FLOAT), kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsExtendedImageMetadataBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image2DReadOnly inputImage, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    int mipLevels = GPU.get_image_num_mip_levels(inputImage);
                    int sampleCount = GPU.get_image_num_samples(inputImage);
                    output[id] = mipLevels + sampleCount + GPU.get_image_width(inputImage);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image2d_t inputImage, __global int* output) {
                    int id = get_global_id(0);
                    int mipLevels = get_image_num_mip_levels(inputImage);
                    int sampleCount = get_image_num_samples(inputImage);
                    output[id] = ((mipLevels + sampleCount) + get_image_width(inputImage));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsMipmappedImageMetadataBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image2DMipmappedReadOnly inputImage, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    int mipLevels = GPU.get_image_num_mip_levels(inputImage);
                    int sampleCount = GPU.get_image_num_samples(inputImage);
                    output[id] = mipLevels + sampleCount + GPU.get_image_width(inputImage) + GPU.get_image_height(inputImage);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image2d_t inputImage, __global int* output) {
                    int id = get_global_id(0);
                    int mipLevels = get_image_num_mip_levels(inputImage);
                    int sampleCount = get_image_num_samples(inputImage);
                    output[id] = (((mipLevels + sampleCount) + get_image_width(inputImage)) + get_image_height(inputImage));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsMipmappedFloatImageBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image2DMipmappedReadOnly inputImage, Image2DMipmappedWriteOnly outputImage, Sampler sampler, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    Int2 coords = new Int2(id, 0);
                    Float4 pixel = GPU.read_imagef(inputImage, sampler, coords);
                    output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w);
                    GPU.write_imagef(outputImage, coords, new Float4(1.0f, 0.5f, 0.25f, 1.0f));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                    int id = get_global_id(0);
                    int2 coords = (int2)(id, 0);
                    float4 pixel = read_imagef(inputImage, sampler, coords);
                    output[id] = ((int) (((pixel.x + pixel.y) + pixel.z) + pixel.w));
                    write_imagef(outputImage, coords, (float4)(1.0f, 0.5f, 0.25f, 1.0f));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsMsaaFloatImageBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image2DMsaaReadOnly inputImage, Image2DMsaaWriteOnly outputImage, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    Int2 coords = new Int2(id, 0);
                    int sampleCount = GPU.get_image_num_samples(inputImage);
                    int sampleIndex = sampleCount > 1 ? 1 : 0;
                    Float4 pixel = GPU.read_imagef(inputImage, coords, sampleIndex);
                    output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_width(inputImage) + GPU.get_image_height(inputImage));
                    GPU.write_imagef(outputImage, coords, sampleIndex, new Float4(1.0f, 0.5f, 0.25f, 1.0f));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image2d_msaa_t inputImage, write_only image2d_msaa_t outputImage, __global int* output) {
                    int id = get_global_id(0);
                    int2 coords = (int2)(id, 0);
                    int sampleCount = get_image_num_samples(inputImage);
                    int sampleIndex = ((sampleCount > 1) ? 1 : 0);
                    float4 pixel = read_imagef(inputImage, coords, sampleIndex);
                    output[id] = ((int) (((((pixel.x + pixel.y) + pixel.z) + pixel.w) + get_image_width(inputImage)) + get_image_height(inputImage)));
                    write_imagef(outputImage, coords, sampleIndex, (float4)(1.0f, 0.5f, 0.25f, 1.0f));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsMipmappedUIntImageBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image2DMipmappedReadOnly inputImage, Image2DMipmappedWriteOnly outputImage, Sampler sampler, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    Int2 coords = new Int2(id, 0);
                    UInt4 pixel = GPU.read_imageui(inputImage, sampler, coords);
                    output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
                    GPU.write_imageui(outputImage, coords, new UInt4(9, 10, 11, 12));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                    int id = get_global_id(0);
                    int2 coords = (int2)(id, 0);
                    uint4 pixel = read_imageui(inputImage, sampler, coords);
                    output[id] = (((pixel.x + pixel.y) + pixel.z) + pixel.w);
                    write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsUnsignedScalarAliasValues() {
        String methodSource = """
                @GPU
                void kernel(UInt bias, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    UInt limited = GPU.clamp(GPU.max(bias, new UInt(4)), new UInt(4), new UInt(32));
                    UInt result = GPU.min(limited, new UInt(17));
                    output[id] = result.value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(uint bias, __global int* output) {
                    int id = get_global_id(0);
                    uint limited = clamp(max(bias, ((uint) 4)), ((uint) 4), ((uint) 32));
                    uint result = min(limited, ((uint) 17));
                    output[id] = result;
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsCharAsUnsignedShort() {
        String methodSource = """
                @GPU
                void kernel(char code, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = code;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(ushort code, __global int* output) {
                    int id = get_global_id(0);
                    output[id] = code;
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsImage1dBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image1DReadOnly inputImage, Image1DWriteOnly outputImage, Sampler sampler, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    UInt4 pixel = GPU.read_imageui(inputImage, sampler, id);
                    output[id] = pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_width(inputImage);
                    GPU.write_imageui(outputImage, id, new UInt4(9, 10, 11, 12));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image1d_t inputImage, write_only image1d_t outputImage, sampler_t sampler, __global int* output) {
                    int id = get_global_id(0);
                    uint4 pixel = read_imageui(inputImage, sampler, id);
                    output[id] = ((((pixel.x + pixel.y) + pixel.z) + pixel.w) + get_image_width(inputImage));
                    write_imageui(outputImage, id, (uint4)(9, 10, 11, 12));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsImage1dArrayBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image1DArrayReadOnly inputImage, Image1DArrayWriteOnly outputImage, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    Int2 coords = new Int2(id, 0);
                    UInt4 pixel = GPU.read_imageui(inputImage, coords);
                    output[id] = pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_width(inputImage) + GPU.get_image_array_size(inputImage);
                    GPU.write_imageui(outputImage, coords, new UInt4(9, 10, 11, 12));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image1d_array_t inputImage, write_only image1d_array_t outputImage, __global int* output) {
                    int id = get_global_id(0);
                    int2 coords = (int2)(id, 0);
                    uint4 pixel = read_imageui(inputImage, coords);
                    output[id] = (((((pixel.x + pixel.y) + pixel.z) + pixel.w) + get_image_width(inputImage)) + get_image_array_size(inputImage));
                    write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsImage1dBufferBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image1DBufferReadOnly inputImage, Image1DBufferWriteOnly outputImage, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    Int4 pixel = GPU.read_imagei(inputImage, id);
                    output[id] = pixel.x + GPU.get_image_width(inputImage);
                    GPU.write_imagei(outputImage, id, new Int4(9, 10, 11, 12));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image1d_buffer_t inputImage, write_only image1d_buffer_t outputImage, __global int* output) {
                    int id = get_global_id(0);
                    int4 pixel = read_imagei(inputImage, id);
                    output[id] = (pixel.x + get_image_width(inputImage));
                    write_imagei(outputImage, id, (int4)(9, 10, 11, 12));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsImage2dArrayBuiltins() {
        String methodSource = """
                @GPU
                void kernel(Image2DArrayReadOnly inputImage, Image2DArrayWriteOnly outputImage, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    Int4 coords = new Int4(id, 0, 0, 0);
                    Float4 pixel = GPU.read_imagef(inputImage, coords);
                    output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w) + GPU.get_image_height(inputImage) + GPU.get_image_array_size(inputImage);
                    GPU.write_imagef(outputImage, coords, new Float4(0.25f, 0.5f, 0.75f, 1.0f));
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(read_only image2d_array_t inputImage, write_only image2d_array_t outputImage, __global int* output) {
                    int id = get_global_id(0);
                    int4 coords = (int4)(id, 0, 0, 0);
                    float4 pixel = read_imagef(inputImage, coords);
                    output[id] = ((((int) (((pixel.x + pixel.y) + pixel.z) + pixel.w)) + get_image_height(inputImage)) + get_image_array_size(inputImage));
                    write_imagef(outputImage, coords, (float4)(0.25f, 0.5f, 0.75f, 1.0f));
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
                @net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic(code = "(({0}) * 2.0f)")
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
    void parsesValidatesLowersAndEmitsHelperMethodAttributes() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = Helpers.doubleValue(input[id]);
                }
                """;
        String helperSource = """
                @OpenCLAttributes({"always_inline"})
                @CCode
                float doubleValue(float value) {
                    return value * 2.0f;
                }
                """;

        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod helperMethod = parser.parseMethod(helperSource, "Helpers", "sample.Helpers");

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(helperMethod));

        assertEquals("""
                __attribute__((always_inline)) float jtg_fn_Helpers_doubleValue_float(float value);

                __attribute__((always_inline)) float jtg_fn_Helpers_doubleValue_float(float value) {
                    return (value * 2.0f);
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = jtg_fn_Helpers_doubleValue_float(input[id]);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsConstOpenClQualifierOnHelperPointer() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    FloatPtr ptr = new FloatPtr(input[id]);
                    output[id] = Helpers.read(ptr);
                }
                """;
        String helperSource = """
                @CCode
                float read(@OpenCLQualifiers({"const"}) FloatPtr ptr) {
                    return ptr.value;
                }
                """;

        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod helperMethod = parser.parseMethod(helperSource, "Helpers", "sample.Helpers");

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(helperMethod));

        assertEquals("""
                float jtg_fn_Helpers_read_FloatPtr(const float* ptr);

                float jtg_fn_Helpers_read_FloatPtr(const float* ptr) {
                    return (*ptr);
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    float ptr = input[id];
                    output[id] = jtg_fn_Helpers_read_FloatPtr((&ptr));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsGlobalArrayAsTypedGlobalPointerHelperArgument() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = Helpers.read(input, id);
                }
                """;
        String helperSource = """
                @CCode
                float read(GlobalFloatPtr ptr, int index) {
                    return ptr.value + ptr.value;
                }
                """;

        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod helperMethod = parser.parseMethod(helperSource, "Helpers", "sample.Helpers");

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(helperMethod));

        assertEquals("""
                float jtg_fn_Helpers_read_GlobalFloatPtr_int(__global float* ptr, int index);

                float jtg_fn_Helpers_read_GlobalFloatPtr_int(__global float* ptr, int index) {
                    return ((*ptr) + (*ptr));
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = jtg_fn_Helpers_read_GlobalFloatPtr_int(input, id);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsAddressSpacePointerAddExpression() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = Helpers.readOffset(input, id);
                }
                """;
        String helperSource = """
                @CCode
                float readOffset(GlobalFloatPtr ptr, int index) {
                    return ptr.add(index).value;
                }
                """;

        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod helperMethod = parser.parseMethod(helperSource, "Helpers", "sample.Helpers");

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(helperMethod));

        assertEquals("""
                float jtg_fn_Helpers_readOffset_GlobalFloatPtr_int(__global float* ptr, int index);

                float jtg_fn_Helpers_readOffset_GlobalFloatPtr_int(__global float* ptr, int index) {
                    return (*((ptr) + (index)));
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = jtg_fn_Helpers_readOffset_GlobalFloatPtr_int(input, id);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsAddressSpacePointerSubExpression() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = Helpers.readBack(input, id);
                }
                """;
        String helperSource = """
                @CCode
                float readBack(GlobalFloatPtr ptr, int index) {
                    return ptr.add(index + 1).sub(1).value;
                }
                """;

        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuMethod helperMethod = parser.parseMethod(helperSource, "Helpers", "sample.Helpers");

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(helperMethod));

        assertEquals("""
                float jtg_fn_Helpers_readBack_GlobalFloatPtr_int(__global float* ptr, int index);

                float jtg_fn_Helpers_readBack_GlobalFloatPtr_int(__global float* ptr, int index) {
                    return (*((((ptr) + ((index + 1)))) - (1)));
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = jtg_fn_Helpers_readBack_GlobalFloatPtr_int(input, id);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsLocalTypedPointerViewVariable() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    GlobalFloatPtr ptr = input;
                    output[id] = ptr.add(id).value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    __global float* ptr = input;
                    output[id] = (*((ptr) + (id)));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsReinterpretedGlobalByteView() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal byte[] blob, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    GlobalBytePtr ptr = blob;
                    output[id] = ptr.add(id * 4).asIntPtr().value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global char* blob, __global int* output) {
                    int id = get_global_id(0);
                    __global char* ptr = blob;
                    output[id] = (*((__global int*) (((ptr) + ((id * 4))))));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsReinterpretedConstantByteView() {
        String methodSource = """
                @GPU
                void kernel(@GPUConstant byte[] blob, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    ConstantBytePtr ptr = blob;
                    output[id] = ptr.add(id * 4).asIntPtr().value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__constant char* blob, __global int* output) {
                    int id = get_global_id(0);
                    __constant char* ptr = blob;
                    output[id] = (*((__constant int*) (((ptr) + ((id * 4))))));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsReinterpretedLocalByteView() {
        String methodSource = """
                @GPU
                void kernel(@GPULocal byte[] blob, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    LocalBytePtr ptr = blob;
                    output[id] = ptr.add(id * 4).asIntPtr().value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__local char* blob, __global int* output) {
                    int id = get_global_id(0);
                    __local char* ptr = blob;
                    output[id] = (*((__local int*) (((ptr) + ((id * 4))))));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsReinterpretedGlobalByteViewAsChar() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal byte[] blob, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    GlobalBytePtr ptr = blob;
                    output[id] = ptr.add(id * 2).asCharPtr().value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global char* blob, __global int* output) {
                    int id = get_global_id(0);
                    __global char* ptr = blob;
                    output[id] = (*((__global char*) (((ptr) + ((id * 2))))));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsReinterpretedConstantByteViewAsShort() {
        String methodSource = """
                @GPU
                void kernel(@GPUConstant byte[] blob, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    ConstantBytePtr ptr = blob;
                    output[id] = ptr.add(id * 2).asShortPtr().value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__constant char* blob, __global int* output) {
                    int id = get_global_id(0);
                    __constant char* ptr = blob;
                    output[id] = (*((__constant short*) (((ptr) + ((id * 2))))));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsReinterpretedLocalByteViewAsLong() {
        String methodSource = """
                @GPU
                void kernel(@GPULocal byte[] blob, @GPUGlobal long[] output) {
                    int id = GPU.get_global_id(0);
                    LocalBytePtr ptr = blob;
                    output[id] = ptr.add(id * 8).asLongPtr().value;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__local char* blob, __global long* output) {
                    int id = get_global_id(0);
                    __local char* ptr = blob;
                    output[id] = (*((__local long*) (((ptr) + ((id * 8))))));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsPrivateFixedSizeScalarArray() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    float[] scratch = new float[8];
                    int id = GPU.get_global_id(0);
                    scratch[id] = input[id] * 2.0f;
                    output[id] = scratch[id];
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    float scratch[8];
                    int id = get_global_id(0);
                    scratch[id] = (input[id] * 2.0f);
                    output[id] = scratch[id];
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsPackedBlobOffsetViewPattern() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal byte[] blob, PackedNoiseView view, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    GlobalBytePtr root = blob;
                    int sampler = root.add(view.samplerOffset + id * 4).asIntPtr().value;
                    int density = root.add(view.densityOffset + id * 4).asIntPtr().value;
                    output[id] = sampler + density;
                }
                """;
        String structSource = """
                @GPUStruct
                class PackedNoiseView {
                    int samplerOffset;
                    int densityOffset;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        net.sixik.ga_utils.javatogpu.frontend.parser.GpuStructParser structParser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuStructParser();

        String kernel = service.validateLowerAndEmit(
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser().parseMethod(methodSource, "Demo", "sample.Demo"),
                List.of(),
                List.of(structParser.parseStruct(structSource, "PackedNoiseView", "sample.PackedNoiseView"))
        );

        assertEquals("""
                typedef struct{
                    int samplerOffset;
                    int densityOffset;
                } PackedNoiseView;

                __kernel void jtg_kernel(__global char* blob, PackedNoiseView view, __global int* output) {
                    int id = get_global_id(0);
                    __global char* root = blob;
                    int sampler = (*((__global int*) (((root) + ((view.samplerOffset + (id * 4)))))));
                    int density = (*((__global int*) (((root) + ((view.densityOffset + (id * 4)))))));
                    output[id] = (sampler + density);
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
    void emitsSimpleOpenClTypeNamesForQualifiedNestedGpuStructReferences() {
        String methodSource = """
                @GPU
                void kernel(Outer.SampleData sample, @GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Outer.Vec2 point = new Outer.Vec2(input[id], input[id] * 2.0f);
                    Outer.SampleData localSample = new Outer.SampleData(point, 0.5f, id);
                    output[id] = sample.point.x + localSample.point.y + sample.bias + localSample.index;
                }
                """;
        String vec2Source = """
                @GPUStruct
                class Vec2 {
                    float x;
                    float y;
                }
                """;
        String sampleDataSource = """
                @GPUStruct
                class SampleData {
                    Outer.Vec2 point;
                    float bias;
                    int index;
                }
                """;

        net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser methodParser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
        net.sixik.ga_utils.javatogpu.frontend.parser.GpuStructParser structParser =
                new net.sixik.ga_utils.javatogpu.frontend.parser.GpuStructParser();
        ParsedGpuMethod kernelMethod = methodParser.parseMethod(methodSource, "Demo", "sample.Demo");
        ParsedGpuStruct vec2 = structParser.parseStruct(vec2Source, "Outer.Vec2", "sample.Outer.Vec2");
        ParsedGpuStruct sampleData = structParser.parseStruct(sampleDataSource, "Outer.SampleData", "sample.Outer.SampleData");

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.validateLowerAndEmit(kernelMethod, List.of(), List.of(vec2, sampleData));

        assertEquals("""
                typedef struct{
                    float x;
                    float y;
                } Vec2;
                typedef struct{
                    Vec2 point;
                    float bias;
                    int index;
                } SampleData;

                __kernel void jtg_kernel(SampleData sample, __global float* input, __global float* output) {
                    int id = get_global_id(0);
                    Vec2 point = (Vec2){input[id], (input[id] * 2.0f)};
                    SampleData localSample = (SampleData){point, 0.5f, id};
                    output[id] = (((sample.point.x + localSample.point.y) + sample.bias) + localSample.index);
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

    @Test
    void parsesValidatesLowersAndEmitsAdditionalUnsignedIntegerCommonBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    UShort hi = new UShort((short) 1);
                    UShort lo = new UShort((short) 2);
                    UInt packed = GPU.upsample(hi, lo);
                    UInt mask = new UInt(-1);
                    UInt chosen = GPU.select(new UInt(10), packed, mask);
                    UInt mixed = GPU.bitselect(chosen, new UInt(255), new UInt(15));
                    UInt rotated = GPU.rotate(mixed, new UInt(3));
                    output[id] = GPU.popcount(rotated) + GPU.clz(rotated);
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int id = get_global_id(0);
                    ushort hi = ((ushort) ((short) 1));
                    ushort lo = ((ushort) ((short) 2));
                    uint packed = upsample(hi, lo);
                    uint mask = ((uint) (-1));
                    uint chosen = select(((uint) 10), packed, mask);
                    uint mixed = bitselect(chosen, ((uint) 255), ((uint) 15));
                    uint rotated = rotate(mixed, ((uint) 3));
                    output[id] = (popcount(rotated) + clz(rotated));
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsUnsignedVectorMaskBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    UInt2 left = new UInt2(1, 2);
                    UInt2 right = new UInt2(10, 20);
                    UInt2 mask = new UInt2(-1, 0);
                    UInt2 selected = GPU.select(left, right, mask);
                    UInt2 mixed = GPU.bitselect(selected, new UInt2(7, 8), new UInt2(3, 12));
                    UInt2 rotated = GPU.rotate(mixed, new UInt2(1, 2));
                    output[id] = rotated.x + rotated.y;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int id = get_global_id(0);
                    uint2 left = (uint2)(1, 2);
                    uint2 right = (uint2)(10, 20);
                    uint2 mask = (uint2)((-1), 0);
                    uint2 selected = select(left, right, mask);
                    uint2 mixed = bitselect(selected, (uint2)(7, 8), (uint2)(3, 12));
                    uint2 rotated = rotate(mixed, (uint2)(1, 2));
                    output[id] = (rotated.x + rotated.y);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsWideUnsignedVectorMaskAndBitBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    UInt8 left = new UInt8(1, 2, 3, 4, 5, 6, 7, 8);
                    UInt8 right = new UInt8(11, 12, 13, 14, 15, 16, 17, 18);
                    UInt8 mask = new UInt8(-1, 0, -1, 0, -1, 0, -1, 0);
                    UInt8 selected = GPU.select(left, right, mask);
                    UInt8 mixed = GPU.bitselect(selected, new UInt8(3), new UInt8(1, 2, 4, 8, 16, 32, 64, 128));
                    UInt8 rotated = GPU.rotate(mixed, new UInt8(1));
                    Int8 counts = GPU.popcount(rotated);
                    Int8 zeros = GPU.clz(rotated);
                    output[id] = counts.s0 + counts.s7 + zeros.s0 + zeros.s7;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int id = get_global_id(0);
                    uint8 left = (uint8)(1, 2, 3, 4, 5, 6, 7, 8);
                    uint8 right = (uint8)(11, 12, 13, 14, 15, 16, 17, 18);
                    uint8 mask = (uint8)((-1), 0, (-1), 0, (-1), 0, (-1), 0);
                    uint8 selected = select(left, right, mask);
                    uint8 mixed = bitselect(selected, (uint8)(3), (uint8)(1, 2, 4, 8, 16, 32, 64, 128));
                    uint8 rotated = rotate(mixed, (uint8)(1));
                    int8 counts = popcount(rotated);
                    int8 zeros = clz(rotated);
                    output[id] = (((counts.s0 + counts.s7) + zeros.s0) + zeros.s7);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsUnsignedNarrowVectorMaskBuiltins() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    UByte4 left = new UByte4((byte) 1, (byte) 2, (byte) 3, (byte) 4);
                    UByte4 right = new UByte4((byte) 9, (byte) 8, (byte) 7, (byte) 6);
                    UByte4 mask = new UByte4((byte) -1, (byte) 0, (byte) -1, (byte) 0);
                    UByte4 selected = GPU.select(left, right, mask);
                    UByte4 mixed = GPU.bitselect(selected, new UByte4((byte) 3), new UByte4((byte) 1, (byte) 2, (byte) 4, (byte) 8));
                    UByte4 rotated = GPU.rotate(mixed, new UByte4((byte) 1));
                    UShort4 low = new UShort4((short) 10, (short) 20, (short) 30, (short) 40);
                    UShort4 high = new UShort4((short) 1, (short) 2, (short) 3, (short) 4);
                    UShort4 ushortMask = new UShort4((short) -1, (short) 0, (short) -1, (short) 0);
                    UShort4 merged = GPU.bitselect(low, high, ushortMask);
                    UShort4 turned = GPU.rotate(merged, new UShort4((short) 1));
                    output[id] = rotated.x + rotated.z + turned.x + turned.z;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int id = get_global_id(0);
                    uchar4 left = (uchar4)(((char) 1), ((char) 2), ((char) 3), ((char) 4));
                    uchar4 right = (uchar4)(((char) 9), ((char) 8), ((char) 7), ((char) 6));
                    uchar4 mask = (uchar4)(((char) (-1)), ((char) 0), ((char) (-1)), ((char) 0));
                    uchar4 selected = select(left, right, mask);
                    uchar4 mixed = bitselect(selected, (uchar4)(((char) 3)), (uchar4)(((char) 1), ((char) 2), ((char) 4), ((char) 8)));
                    uchar4 rotated = rotate(mixed, (uchar4)(((char) 1)));
                    ushort4 low = (ushort4)(((short) 10), ((short) 20), ((short) 30), ((short) 40));
                    ushort4 high = (ushort4)(((short) 1), ((short) 2), ((short) 3), ((short) 4));
                    ushort4 ushortMask = (ushort4)(((short) (-1)), ((short) 0), ((short) (-1)), ((short) 0));
                    ushort4 merged = bitselect(low, high, ushortMask);
                    ushort4 turned = rotate(merged, (ushort4)(((short) 1)));
                    output[id] = (((rotated.x + rotated.z) + turned.x) + turned.z);
                }""", kernel);
    }

    @Test
    void parsesValidatesLowersAndEmitsWideUnsignedIntegerVectorConversions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    UInt8 a = new UInt8(1, 2, 3, 4, 5, 6, 7, 8);
                    UInt16 b = new UInt16(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
                    ULong8 c = new ULong8(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
                    ULong16 d = new ULong16(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L);
                    Int8 ai = GPU.convert_int(a);
                    Int16 bi = GPU.convert_int_sat(b);
                    Int8 ci = GPU.convert_int(c);
                    Int16 di = GPU.convert_int_sat(d);
                    UInt8 au = GPU.convert_uint(ai);
                    UInt16 bu = GPU.convert_uint(bi);
                    output[id] = ai.s0 + ai.s7 + bi.s0 + bi.sf + ci.s0 + ci.s7 + di.s0 + di.sf + au.s0 + au.s7 + bu.s0 + bu.sf;
                }
                """;

        GpuFrontendService service = GpuFrontendService.createDefault();
        String kernel = service.parseValidateLowerAndEmit(methodSource);

        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int id = get_global_id(0);
                    uint8 a = (uint8)(1, 2, 3, 4, 5, 6, 7, 8);
                    uint16 b = (uint16)(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
                    ulong8 c = (ulong8)(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
                    ulong16 d = (ulong16)(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L);
                    int8 ai = convert_int(a);
                    int16 bi = convert_int_sat(b);
                    int8 ci = convert_int(c);
                    int16 di = convert_int_sat(d);
                    uint8 au = convert_uint(ai);
                    uint16 bu = convert_uint(bi);
                    output[id] = (((((((((((ai.s0 + ai.s7) + bi.s0) + bi.sf) + ci.s0) + ci.s7) + di.s0) + di.sf) + au.s0) + au.s7) + bu.s0) + bu.sf);
                }""", kernel);
    }
}
