package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuAttributeMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModuleMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuStructFieldMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuStructMetadata;
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
    void reconstructsElseIfIrGpuSourceAndRecordsParityMatch() {
        String descriptorSource = """
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int value = input[0];
                    if ((value > 0)) {
                        output[0] = value;
                    } else {
                        if ((value < 0)) {
                            output[0] = (0 - value);
                        } else {
                            output[0] = 0;
                        }
                    }
                    return;
                }
                """;
        IrGpuArtifact artifact = artifact(
                """
                        body
                          var int value = input[0]
                          if (value > 0)
                            set output[0] = value
                          else if (value < 0)
                            set output[0] = (0 - value)
                          else
                            set output[0] = 0
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
    void reconstructsStructInitializerIrGpuSourceAndRecordsParityMatch() {
        String descriptorSource = """
                typedef struct{
                    float x;
                    float y;
                } Vec2;

                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    Vec2 value = (Vec2){input[0], (input[0] + 1.0f)};
                    output[0] = value.x;
                    return;
                }
                """;
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of("Vec2"),
                        List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "jtg_kernel",
                                """
                                        body
                                          var Vec2 value = init<Vec2>(input[0], (input[0] + 1.0f))
                                          set output[0] = value.x
                                          return
                                        """,
                                List.of()
                        ))
                ),
                List.of(
                        new IrGpuEntryParameter("input", "float[]", "GLOBAL", false, List.of()),
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of())
                ),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(new IrGpuStructMetadata(
                        "sample.Vec2",
                        "Vec2",
                        List.of(
                                new IrGpuStructFieldMetadata("x", "float", List.of()),
                                new IrGpuStructFieldMetadata("y", "float", List.of())
                        ),
                        List.of()
                )),
                List.of(),
                List.of(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
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
        assertTrue(result.diagnostics().contains("OpenCL source assembler emitted 1 struct typedef(s)"));
    }

    @Test
    void reconstructsPackedStructIrGpuSourceAndRecordsParityMatch() {
        String descriptorSource = """
                typedef struct __attribute__((packed)) {
                    int offset;
                    int bias;
                } PackedView;

                __kernel void jtg_kernel(PackedView view, __global int* output) {
                    output[0] = (view.offset + view.bias);
                    return;
                }
                """;
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of("PackedView"),
                        List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "jtg_kernel",
                                """
                                        body
                                          set output[0] = (view.offset + view.bias)
                                          return
                                        """,
                                List.of()
                        ))
                ),
                List.of(
                        new IrGpuEntryParameter("view", "PackedView", "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of())
                ),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(new IrGpuStructMetadata(
                        "sample.PackedView",
                        "PackedView",
                        List.of(
                                new IrGpuStructFieldMetadata("offset", "int", List.of()),
                                new IrGpuStructFieldMetadata("bias", "int", List.of())
                        ),
                        List.of("packed"),
                        List.of(new IrGpuAttributeMetadata("packed", "true", "GPUPacked"))
                )),
                List.of(),
                List.of(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
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
    void reconstructsPrivatePointerLikeLocalIrGpuSourceAndRecordsParityMatch() {
        String descriptorSource = """
                void jtg_fn_clamp_FloatPtr(float* ptr);

                void jtg_fn_clamp_FloatPtr(float* ptr) {
                    if (((*ptr) > 32.0F)) {
                        (*ptr) = 32.0F;
                    }
                }
                __kernel void basicMath(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    float ptr = input[id];
                    jtg_fn_clamp_FloatPtr((&ptr));
                    output[id] = ptr;
                }
                """;
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "basicMath",
                        List.of(new IrGpuModuleMethod(
                                "clamp",
                                "jtg_fn_clamp_FloatPtr",
                                "void",
                                List.of(new IrGpuEntryParameter("ptr", "FloatPtr", "PRIVATE", false, List.of()))
                        )),
                        List.of(),
                        List.of(
                                IrGpuMethodBody.entry(
                                        "kernel",
                                        "basicMath",
                                        """
                                                body
                                                  var int id = intrinsic(get_global_id template="" args=[0])
                                                  var FloatPtr ptr = input[id]
                                                  expr helper(jtg_fn_clamp_FloatPtr args=[(&ptr)])
                                                  set output[id] = ptr
                                                """,
                                        List.of("jtg_fn_clamp_FloatPtr")
                                ),
                                IrGpuMethodBody.helper(
                                        "clamp",
                                        "jtg_fn_clamp_FloatPtr",
                                        """
                                                body
                                                  if ((*ptr) > 32.0F)
                                                    set (*ptr) = 32.0F
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
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/basicMath.cl")),
                "opencl",
                "off"
        );

        GpuBackendSourceReconstructionResult result = OpenClIrGpuSourceReconstructor.INSTANCE.reconstruct(
                artifact,
                "javatogpu/sample/Demo/basicMath.cl",
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
    void reconstructsElseIfChainIrGpuSourceAndRecordsParityMatch() {
        String descriptorSource = """
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int value = input[0];
                    if ((value > 1)) {
                        output[0] = 3;
                    } else {
                        if ((value == 1)) {
                            output[0] = 2;
                        } else {
                            if ((value == 0)) {
                                output[0] = 1;
                            } else {
                                output[0] = 0;
                            }
                        }
                    }
                    return;
                }
                """;
        IrGpuArtifact artifact = artifact(
                """
                        body
                          var int value = input[0]
                          if (value > 1)
                            set output[0] = 3
                          else if (value == 1)
                            set output[0] = 2
                          else if (value == 0)
                            set output[0] = 1
                          else
                            set output[0] = 0
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
    void reconstructsLoopBreakContinueIrGpuSourceAndRecordsParityMatch() {
        String descriptorSource = """
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int sum = 0;
                    for (int i = 0; (i < 8); i = (i + 1)) {
                        if ((input[i] < 0)) {
                            continue;
                        }
                        if ((input[i] == 0)) {
                            break;
                        }
                        sum = (sum + input[i]);
                    }
                    output[0] = sum;
                    return;
                }
                """;
        IrGpuArtifact artifact = artifact(
                """
                        body
                          var int sum = 0
                          for init=(var int i = 0) cond=(i < 8) update=(set i = (i + 1))
                            if (input[i] < 0)
                              continue
                            if (input[i] == 0)
                              break
                            set sum = (sum + input[i])
                          set output[0] = sum
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
    void reconstructsWhileBreakContinueIrGpuSourceAndRecordsParityMatch() {
        String descriptorSource = """
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int i = 0;
                    int sum = 0;
                    while ((i < 8)) {
                        if ((input[i] < 0)) {
                            i = (i + 1);
                            continue;
                        }
                        if ((input[i] == 0)) {
                            break;
                        }
                        sum = (sum + input[i]);
                        i = (i + 1);
                    }
                    output[0] = sum;
                    return;
                }
                """;
        IrGpuArtifact artifact = artifact(
                """
                        body
                          var int i = 0
                          var int sum = 0
                          while (i < 8)
                            if (input[i] < 0)
                              set i = (i + 1)
                              continue
                            if (input[i] == 0)
                              break
                            set sum = (sum + input[i])
                            set i = (i + 1)
                          set output[0] = sum
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
    void reconstructsDoWhileBreakContinueIrGpuSourceAndRecordsParityMatch() {
        String descriptorSource = """
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int i = 0;
                    int sum = 0;
                    do {
                        if ((input[i] < 0)) {
                            i = (i + 1);
                            continue;
                        }
                        if ((input[i] == 0)) {
                            break;
                        }
                        sum = (sum + input[i]);
                        i = (i + 1);
                    } while ((i < 8));
                    output[0] = sum;
                    return;
                }
                """;
        IrGpuArtifact artifact = artifact(
                """
                        body
                          var int i = 0
                          var int sum = 0
                          do
                            if (input[i] < 0)
                              set i = (i + 1)
                              continue
                            if (input[i] == 0)
                              break
                            set sum = (sum + input[i])
                            set i = (i + 1)
                          while (i < 8)
                          set output[0] = sum
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
    void reconstructsSwitchMultiLabelIrGpuSourceAndRecordsParityMatch() {
        String descriptorSource = """
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int selector = input[0];
                    switch (selector) {
                        case 0:
                        case 1:
                            output[0] = 10;
                            break;
                        case 2:
                            output[0] = 20;
                            break;
                        default:
                            output[0] = 30;
                    }
                    return;
                }
                """;
        IrGpuArtifact artifact = artifact(
                """
                        body
                          var int selector = input[0]
                          switch selector
                            case 0,1
                              set output[0] = 10
                              break
                            case 2
                              set output[0] = 20
                              break
                            default
                              set output[0] = 30
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
    void reconstructsHelperControlFlowIrGpuSourceAndRecordsParityMatch() {
        String descriptorSource = """
                int jtg_fn_clamp_accumulate(__global int* input, int limit);

                int jtg_fn_clamp_accumulate(__global int* input, int limit) {
                    int sum = 0;
                    for (int i = 0; (i < limit); i = (i + 1)) {
                        if ((input[i] < 0)) {
                            continue;
                        } else {
                            if ((input[i] == 0)) {
                                break;
                            } else {
                                sum = (sum + input[i]);
                            }
                        }
                    }
                    return sum;
                }
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    output[0] = jtg_fn_clamp_accumulate(input, 8);
                    return;
                }
                """;
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(new IrGpuModuleMethod(
                                "clampAccumulate",
                                "jtg_fn_clamp_accumulate",
                                "int",
                                List.of(
                                        new IrGpuEntryParameter("input", "int[]", "GLOBAL", false, List.of()),
                                        new IrGpuEntryParameter("limit", "int", "PRIVATE", false, List.of())
                                )
                        )),
                        List.of(),
                        List.of(
                                IrGpuMethodBody.helper(
                                        "clampAccumulate",
                                        "jtg_fn_clamp_accumulate",
                                        """
                                                body
                                                  var int sum = 0
                                                  for init=(var int i = 0) cond=(i < limit) update=(set i = (i + 1))
                                                    if (input[i] < 0)
                                                      continue
                                                    else if (input[i] == 0)
                                                      break
                                                    else
                                                      set sum = (sum + input[i])
                                                  return sum
                                                """,
                                        List.of()
                                ),
                                IrGpuMethodBody.entry(
                                        "kernel",
                                        "jtg_kernel",
                                        """
                                                body
                                                  set output[0] = helper(jtg_fn_clamp_accumulate args=[input, 8])
                                                  return
                                                """,
                                        List.of("jtg_fn_clamp_accumulate")
                                )
                        )
                ),
                List.of(
                        new IrGpuEntryParameter("input", "int[]", "GLOBAL", false, List.of()),
                        new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of())
                ),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
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
        assertTrue(result.diagnostics().contains("OpenCL source assembler emitted 1 helper function(s)"));
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
