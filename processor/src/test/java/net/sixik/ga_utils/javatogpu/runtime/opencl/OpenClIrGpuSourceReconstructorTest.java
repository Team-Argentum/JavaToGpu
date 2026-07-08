package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModuleMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceReconstructionResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrArtifactLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClIrGpuSourceReconstructorTest {

    private static final String SIMPLE_IRGPU_SOURCE_RESOURCE = "javatogpu/runtime/opencl/integration/simple-irgpu-source-kernel.irgpu.properties";
    private static final String IMAGE_KERNEL_IRGPU_RESOURCE = "javatogpu/runtime/opencl/integration/image-kernel.irgpu.properties";

    @Test
    void reconstructsBackendNeutralReadyIrGpuSourceAndRecordsParityMatch() {
        String descriptorSource = """
                __kernel void jtg_kernel(__global int* output) {
                    output[0] = 1;
                    return;
                }
                """;
        IrGpuArtifact artifact = artifact("body\n  set output[0] = 1\n  return\n");

        GpuBackendSourceReconstructionResult result = OpenClIrGpuSourceReconstructor.INSTANCE.reconstruct(
                artifact,
                "javatogpu/sample/Demo/kernel.cl",
                descriptorSource
        );

        assertEquals(GpuBackendTarget.OPENCL, result.backendTarget());
        assertTrue(result.attempted());
        assertTrue(result.reconstructed());
        assertTrue(result.sourceAvailable());
        assertTrue(result.blockers().isEmpty());
        assertEquals("irgpu-backend-neutral-source", result.selectedSource());
        assertEquals("ir-text-v1", result.payloadFormat());
        assertEquals("opencl-irgpu-source-compile", result.runtimeLoadMode());
        assertEquals(descriptorSource, result.source());
        assertTrue(result.diagnostics().contains("sourceParity.checked=true"));
        assertTrue(result.diagnostics().contains("sourceParity.matched=true"));
        assertTrue(result.diagnostics().contains("OpenCL source assembler emitted entry kernel jtg_kernel"));
    }

    @Test
    void reportsParityMismatchWhenReconstructedSourceDiffersFromDescriptorSource() {
        IrGpuArtifact artifact = artifact("body\n  set output[0] = 2\n  return\n");

        GpuBackendSourceReconstructionResult result = OpenClIrGpuSourceReconstructor.INSTANCE.reconstruct(
                artifact,
                "javatogpu/sample/Demo/kernel.cl",
                """
                        __kernel void jtg_kernel(__global int* output) {
                            output[0] = 1;
                            return;
                        }
                        """
        );

        assertTrue(result.reconstructed());
        assertTrue(result.sourceAvailable());
        assertTrue(result.blockers().isEmpty());
        assertTrue(result.source().contains("output[0] = 2;"));
        assertTrue(result.diagnostics().contains("sourceParity.checked=true"));
        assertTrue(result.diagnostics().contains("sourceParity.matched=false"));
    }

    @Test
    void reconstructsPrivateArrayIrGpuSourceAndRecordsParityMatch() {
        String descriptorSource = """
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    float scratch[4];
                    scratch[0] = input[0];
                    output[0] = scratch[0];
                    return;
                }
                """;
        IrGpuArtifact artifact = artifact(
                """
                        body
                          private-array float scratch[4]
                          set scratch[0] = input[0]
                          set output[0] = scratch[0]
                          return
                        """,
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(
                        new IrGpuEntryParameter("input", "float[]", "GLOBAL", false, List.of()),
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of())
                )
        );

        GpuBackendSourceReconstructionResult result = OpenClIrGpuSourceReconstructor.INSTANCE.reconstruct(
                artifact,
                "javatogpu/sample/Demo/kernel.cl",
                descriptorSource
        );

        assertTrue(result.reconstructed());
        assertTrue(result.sourceAvailable());
        assertTrue(result.blockers().isEmpty());
        assertEquals(descriptorSource, result.source());
        assertTrue(result.diagnostics().contains("sourceParity.checked=true"));
        assertTrue(result.diagnostics().contains("sourceParity.matched=true"));
    }

    @Test
    void reconstructsControlFlowIrGpuSourceAndRecordsParityMatch() {
        String descriptorSource = """
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    for (int i = 0; (i < 4); i = (i + 1)) {
                        if ((input[i] > 0)) {
                            output[i] = input[i];
                        } else {
                            output[i] = 0;
                        }
                    }
                    switch (output[0]) {
                        case 0:
                            output[1] = 1;
                            break;
                        default:
                            output[1] = 2;
                    }
                    return;
                }
                """;
        IrGpuArtifact artifact = artifact(
                """
                        body
                          for init=(var int i = 0) cond=(i < 4) update=(set i = (i + 1))
                            if (input[i] > 0)
                              set output[i] = input[i]
                            else
                              set output[i] = 0
                          switch output[0]
                            case 0
                              set output[1] = 1
                              break
                            default
                              set output[1] = 2
                          return
                        """,
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(
                        new IrGpuEntryParameter("input", "int[]", "GLOBAL", false, List.of()),
                        new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of())
                )
        );

        GpuBackendSourceReconstructionResult result = OpenClIrGpuSourceReconstructor.INSTANCE.reconstruct(
                artifact,
                "javatogpu/sample/Demo/kernel.cl",
                descriptorSource
        );

        assertTrue(result.reconstructed());
        assertTrue(result.sourceAvailable());
        assertTrue(result.blockers().isEmpty());
        assertEquals(descriptorSource, result.source());
        assertTrue(result.diagnostics().contains("sourceParity.checked=true"));
        assertTrue(result.diagnostics().contains("sourceParity.matched=true"));
    }

    @Test
    void reconstructsExpressionHeavyIrGpuSourceAndRecordsParityMatch() {
        String descriptorSource = expressionHeavySource();
        IrGpuArtifact artifact = expressionHeavyArtifact();

        GpuBackendSourceReconstructionResult result = OpenClIrGpuSourceReconstructor.INSTANCE.reconstruct(
                artifact,
                "javatogpu/sample/Demo/kernel.cl",
                descriptorSource
        );

        assertTrue(result.reconstructed());
        assertTrue(result.sourceAvailable());
        assertTrue(result.blockers().isEmpty());
        assertEquals(descriptorSource, result.source());
        assertTrue(result.diagnostics().contains("sourceParity.checked=true"));
        assertTrue(result.diagnostics().contains("sourceParity.matched=true"));
        assertTrue(result.diagnostics().contains("OpenCL source assembler emitted 1 helper function(s)"));
    }

    @Test
    void reconstructsPackagedSimpleIrGpuResourceAndRecordsParityMatch() {
        IrGpuArtifact artifact = GpuRuntimeIrArtifactLoader.load(SIMPLE_IRGPU_SOURCE_RESOURCE, getClass().getClassLoader())
                .orElseThrow();
        String descriptorSource = """
                __kernel void gpu_irgpu_entry(__global const float* input, float scale, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = input[id] + scale;
                }
                """;

        GpuBackendSourceReconstructionResult result = OpenClIrGpuSourceReconstructor.INSTANCE.reconstruct(
                artifact,
                "inline://integration/simple-irgpu-source-kernel.cl",
                descriptorSource
        );

        assertTrue(result.reconstructed());
        assertTrue(result.sourceAvailable());
        assertTrue(result.blockers().isEmpty());
        assertEquals(descriptorSource, result.source());
        assertTrue(result.diagnostics().contains("sourceParity.checked=true"));
        assertTrue(result.diagnostics().contains("sourceParity.matched=true"));
        assertTrue(result.diagnostics().contains("OpenCL source assembler emitted entry kernel gpu_irgpu_entry"));
    }

    @Test
    void reconstructsPackagedImageIrGpuResourceAndRecordsParityMatch() {
        IrGpuArtifact artifact = GpuRuntimeIrArtifactLoader.load(IMAGE_KERNEL_IRGPU_RESOURCE, getClass().getClassLoader())
                .orElseThrow();
        String descriptorSource = """
                __kernel void gpu_image_entry(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                    int id = get_global_id(0);
                    int2 coords = (int2)(id, 0);
                    int4 pixel = read_imagei(inputImage, sampler, coords);
                    output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
                    write_imagef(outputImage, coords, (float4)(1.0f, 0.5f, 0.25f, 1.0f));
                }
                """;

        GpuBackendSourceReconstructionResult result = OpenClIrGpuSourceReconstructor.INSTANCE.reconstruct(
                artifact,
                "inline://integration/image-kernel.cl",
                descriptorSource
        );

        assertTrue(result.reconstructed());
        assertTrue(result.sourceAvailable());
        assertTrue(result.blockers().isEmpty());
        assertEquals(descriptorSource, result.source());
        assertTrue(result.diagnostics().contains("sourceParity.checked=true"));
        assertTrue(result.diagnostics().contains("sourceParity.matched=true"));
        assertTrue(result.diagnostics().contains("OpenCL source assembler emitted entry kernel gpu_image_entry"));
    }

    @Test
    void emitsDiagnosticSourceForTransitionalIrGpuButKeepsFallbackSelection() {
        IrGpuArtifact artifact = artifact(
                "body\n  set output[0] = 1\n  return\n",
                IrGpuRegenerationMetadata.transitionalIrText()
        );

        GpuBackendSourceReconstructionResult result = OpenClIrGpuSourceReconstructor.INSTANCE.reconstruct(
                artifact,
                "javatogpu/sample/Demo/kernel.cl",
                ""
        );

        assertTrue(result.attempted());
        assertTrue(result.reconstructed());
        assertTrue(result.sourceAvailable());
        assertEquals("derived-opencl-source", result.selectedSource());
        assertEquals("opencl-source-compile", result.runtimeLoadMode());
        assertTrue(result.blockers().isEmpty());
        assertTrue(result.source().contains("output[0] = 1;"));
        assertTrue(result.diagnostics().contains("sourceParity.checked=false"));
        assertTrue(result.diagnostics().contains("OpenCL source assembler emitted entry kernel jtg_kernel"));
    }

    private static IrGpuArtifact artifact(String body) {
        return artifact(body, IrGpuRegenerationMetadata.backendNeutralReady());
    }

    private static IrGpuArtifact artifact(String body, IrGpuRegenerationMetadata regenerationMetadata) {
        return artifact(
                body,
                regenerationMetadata,
                List.of(new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of()))
        );
    }

    private static IrGpuArtifact artifact(
            String body,
            IrGpuRegenerationMetadata regenerationMetadata,
            List<IrGpuEntryParameter> entryParameters
    ) {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry("kernel", "jtg_kernel", body, List.of()))
                ),
                entryParameters,
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                regenerationMetadata,
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact expressionHeavyArtifact() {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(new IrGpuModuleMethod(
                                "mix",
                                "jtg_fn_mix_float",
                                "float",
                                List.of(
                                        new IrGpuEntryParameter("a", "float", "VALUE", false, List.of()),
                                        new IrGpuEntryParameter("b", "float", "VALUE", false, List.of())
                                )
                        )),
                        List.of(),
                        List.of(
                                IrGpuMethodBody.entry(
                                        "kernel",
                                        "jtg_kernel",
                                        """
                                                body
                                                  var int id = intrinsic(get_global_id template="" args=[0])
                                                  var float value = cast<float>(helper(jtg_fn_mix_float args=[input[id], 1.0f]))
                                                  set output[id] = ((value > 0.0f) ? init<float2>(value, 1.0f).x : intrinsic(fabs template="fabs({0})" args=[value]))
                                                  return
                                                """,
                                        List.of("jtg_fn_mix_float")
                                ),
                                IrGpuMethodBody.helper(
                                        "mix",
                                        "jtg_fn_mix_float",
                                        """
                                                body
                                                  return (a + b)
                                                """,
                                        List.of()
                                )
                        )
                ),
                List.of(
                        new IrGpuEntryParameter("input", "float[]", "GLOBAL", false, List.of()),
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of())
                ),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static String expressionHeavySource() {
        return """
                float jtg_fn_mix_float(float a, float b);

                float jtg_fn_mix_float(float a, float b) {
                    return (a + b);
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    float value = ((float) jtg_fn_mix_float(input[id], 1.0f));
                    output[id] = ((value > 0.0f) ? (float2)(value, 1.0f).x : fabs(value));
                    return;
                }
                """;
    }
}
