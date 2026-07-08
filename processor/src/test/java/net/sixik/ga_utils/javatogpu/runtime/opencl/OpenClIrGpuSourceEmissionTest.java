package net.sixik.ga_utils.javatogpu.runtime.opencl;

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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClIrGpuSourceEmissionTest {

    @Test
    void emitsBackendNeutralReadyEntrySourceDirectlyFromIrGpuBody() {
        IrGpuArtifact artifact = artifact("body\n  set output[0] = 1\n  return\n");

        OpenClIrGpuSourceEmission emission = OpenClIrGpuSourceEmission.inspect(artifact);

        assertTrue(emission.sourceGenerated());
        assertTrue(emission.blockers().isEmpty());
        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    output[0] = 1;
                    return;
                }
                """, emission.source());
        assertTrue(emission.diagnostics().contains("irgpu-entry-jtg_kernel-parsed.statement.count=2"));
        assertTrue(emission.diagnostics().contains("OpenCL source assembler emitted entry kernel jtg_kernel"));
        assertTrue(emission.diagnostics().contains("OpenCL source assembler produced a diagnostic IrGpu-derived source payload"));
    }

    @Test
    void reportsMissingEntryParameterMetadataAsSourceGenerationBlocker() {
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry("kernel", "jtg_kernel", "body\n  return\n", List.of()))
                ),
                List.of(),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );

        OpenClIrGpuSourceEmission emission = OpenClIrGpuSourceEmission.inspect(artifact);

        assertFalse(emission.sourceGenerated());
        assertTrue(emission.source().isBlank());
        assertTrue(emission.blockers().contains("irgpu-entry-parameter-metadata-missing"));
        assertTrue(emission.diagnostics().contains("irgpu-entry-jtg_kernel-parsed.statement.count=1"));
    }

    @Test
    void emitsPrivateArrayDeclarationsFromIrGpuBody() {
        IrGpuArtifact artifact = artifact("""
                body
                  private-array float scratch[4]
                  set scratch[0] = input[0]
                  set output[0] = scratch[0]
                  return
                """, List.of(
                new IrGpuEntryParameter("input", "float[]", "GLOBAL", false, List.of()),
                new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of())
        ));

        OpenClIrGpuSourceEmission emission = OpenClIrGpuSourceEmission.inspect(artifact);

        assertTrue(emission.sourceGenerated());
        assertTrue(emission.blockers().isEmpty());
        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    float scratch[4];
                    scratch[0] = input[0];
                    output[0] = scratch[0];
                    return;
                }
                """, emission.source());
    }

    @Test
    void emitsControlFlowBlocksFromIrGpuBody() {
        IrGpuArtifact artifact = artifact("""
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
                """, List.of(
                new IrGpuEntryParameter("input", "int[]", "GLOBAL", false, List.of()),
                new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of())
        ));

        OpenClIrGpuSourceEmission emission = OpenClIrGpuSourceEmission.inspect(artifact);

        assertTrue(emission.sourceGenerated());
        assertTrue(emission.blockers().isEmpty());
        assertEquals("""
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
                """, emission.source());
    }

    @Test
    void emitsExpressionHeavySourceDirectlyFromIrGpuBody() {
        IrGpuArtifact artifact = expressionHeavyArtifact();

        OpenClIrGpuSourceEmission emission = OpenClIrGpuSourceEmission.inspect(artifact);

        assertTrue(emission.sourceGenerated());
        assertTrue(emission.blockers().isEmpty());
        assertEquals(expressionHeavySource(), emission.source());
        assertTrue(emission.diagnostics().contains("irgpu-helper-jtg_fn_mix_float-parsed.statement.count=1"));
        assertTrue(emission.diagnostics().contains("OpenCL source assembler emitted 1 helper function(s)"));
    }

    private static IrGpuArtifact artifact(String body) {
        return artifact(body, List.of(new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of())));
    }

    private static IrGpuArtifact artifact(String body, List<IrGpuEntryParameter> entryParameters) {
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
                IrGpuRegenerationMetadata.backendNeutralReady(),
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
