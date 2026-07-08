package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;

/**
 * Checks the current transitional contract between packaged IrGpu and generated OpenCL source.
 */
public final class OpenClIrGpuParityChecker {

    private OpenClIrGpuParityChecker() {
    }

    public static OpenClIrGpuParityResult check(GpuRuntimeCompileRequest compileRequest) {
        if (compileRequest == null) {
            return OpenClIrGpuParityResult.missingIrGpu("");
        }
        return compileRequest.irGpuArtifact()
                .map(artifact -> check(artifact, compileRequest.descriptor().kernelResource()))
                .orElseGet(() -> OpenClIrGpuParityResult.missingIrGpu(compileRequest.descriptor().kernelResource()));
    }

    public static OpenClIrGpuParityResult check(IrGpuArtifact artifact, String descriptorOpenClResource) {
        if (artifact == null) {
            return OpenClIrGpuParityResult.missingIrGpu(descriptorOpenClResource);
        }
        String derivedOpenClResource = artifact.derivedOpenClResource();
        IrGpuRegenerationMetadata regenerationMetadata = artifact.regenerationMetadata();
        if (!derivedOpenClResource.isBlank() && derivedOpenClResource.equals(descriptorOpenClResource)) {
            return OpenClIrGpuParityResult.compatible(
                    regenerationMetadata.backendNeutralSourceReady(),
                    regenerationMetadata.payloadFormat(),
                    regenerationMetadata.fallbackSource(),
                    regenerationMetadata.blockers(),
                    derivedOpenClResource,
                    descriptorOpenClResource
            );
        }
        return OpenClIrGpuParityResult.incompatible(
                regenerationMetadata.backendNeutralSourceReady(),
                regenerationMetadata.payloadFormat(),
                regenerationMetadata.fallbackSource(),
                regenerationMetadata.blockers(),
                derivedOpenClResource,
                descriptorOpenClResource
        );
    }
}
