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
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDump;
import net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCompileArtifactDumper;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactSnapshot;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileInvalidationStamp;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileProvenance;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeEquivalenceEvidence;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Writes a small synthetic backend-source promotion artifact through the normal runtime dump contract.
 *
 * <p>The fixture is intentionally tiny and diagnostic-only: it proves the reconstructed-source promotion gate shape can
 * be produced by {@link GpuRuntimeCompileArtifactDumper} without switching production OpenCL compilation to IrGpu yet.</p>
 */
public final class OpenClBackendSourcePromotionGateReporter {

    private static final String GATE_FILE_PROPERTY = "javatogpu.opencl.backendSourcePromotionGateFile";

    private OpenClBackendSourcePromotionGateReporter() {
    }

    public static void main(String[] args) throws IOException {
        String gatePath = System.getProperty(GATE_FILE_PROPERTY);
        if (gatePath == null || gatePath.isBlank()) {
            System.out.println("Skipped backend source promotion gate artifact because " + GATE_FILE_PROPERTY + " is not set");
            return;
        }

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot());
        String gateProperties = dump.artifact(GpuPromotionArtifactRegistry.BACKEND_SOURCE_PROMOTION_GATE);
        Path path = Paths.get(gatePath);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, gateProperties, StandardCharsets.UTF_8);
        System.out.println("Wrote backend source promotion gate artifact to " + path.toAbsolutePath());
    }

    static GpuRuntimeCompileArtifactSnapshot snapshot() {
        String source = """
                __kernel void jtg_kernel(__global int* output) {
                    output[0] = 1;
                    return;
                }
                """;
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/validation/BackendSourcePromotion/kernel.cl",
                source,
                List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "jtg_kernel",
                                "body\n  set output[0] = 1\n  return\n",
                                List.of(),
                                new IrGpuSourceLocation("synthetic-validation", "validation.BackendSourcePromotion", "kernel", 1, 1, 4, 1)
                        ))
                ),
                List.of(new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of())),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(IrGpuBackendOutput.openClSource(descriptor.kernelResource())),
                "opencl",
                "off"
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                source,
                descriptor.kernelResource(),
                "synthetic-opencl-source-promotion-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(artifact)
        );
        return GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "synthetic-source-promotion-v1"),
                GpuRuntimeCompileProvenance.from(request),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(artifact)),
                GpuRuntimeEquivalenceEvidence.passed(request, 1, 1, List.of("synthetic source-promotion parity fixture passed"))
        );
    }
}
