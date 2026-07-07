package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;

import java.util.List;

/**
 * Shared runtime summary for the transitional IrGpu source-selection state.
 */
public record GpuBackendIrGpuSourceSummary(
        boolean irGpuPresent,
        String derivedOpenClResource,
        boolean derivedResourceMatches,
        boolean irGpuSourceSelected,
        String selectedSource,
        String payloadFormat,
        List<String> blockers
) {

    public GpuBackendIrGpuSourceSummary {
        derivedOpenClResource = derivedOpenClResource == null ? "" : derivedOpenClResource;
        selectedSource = normalize(selectedSource, "descriptor-opencl-source");
        payloadFormat = normalize(payloadFormat, "unknown");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public static GpuBackendIrGpuSourceSummary from(IrGpuArtifact artifact, GpuBackendModuleArtifact module) {
        if (artifact == null) {
            return new GpuBackendIrGpuSourceSummary(
                    false,
                    "",
                    false,
                    false,
                    "descriptor-opencl-source",
                    "unknown",
                    List.of("irgpu-artifact-missing")
            );
        }

        GpuBackendModuleArtifact backendModule = module == null ? GpuBackendModuleArtifact.unknown() : module;
        IrGpuRegenerationMetadata metadata = artifact.regenerationMetadata();
        String derivedOpenClResource = artifact.derivedOpenClResource();
        boolean derivedResourceMatches = derivedOpenClResource.isBlank()
                || derivedOpenClResource.equals(backendModule.resource());
        boolean irGpuSourceSelected = metadata.backendNeutralSourceReady() && derivedResourceMatches;
        return new GpuBackendIrGpuSourceSummary(
                true,
                derivedOpenClResource,
                derivedResourceMatches,
                irGpuSourceSelected,
                irGpuSourceSelected ? "irgpu-backend-neutral-source" : metadata.fallbackSource(),
                metadata.payloadFormat(),
                metadata.blockers()
        );
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("irGpu.present=").append(irGpuPresent).append('\n');
        if (irGpuPresent) {
            builder.append("irGpu.derivedOpenClResource=").append(derivedOpenClResource).append('\n');
        }
        builder.append("derivedResourceMatches=").append(derivedResourceMatches).append('\n');
        builder.append("irGpuSourceSelected=").append(irGpuSourceSelected).append('\n');
        builder.append("selectedSource=").append(selectedSource).append('\n');
        builder.append("payloadFormat=").append(payloadFormat).append('\n');
        builder.append("blocker.count=").append(blockers.size()).append('\n');
        for (int index = 0; index < blockers.size(); index++) {
            builder.append("blocker.").append(index).append('=').append(blockers.get(index)).append('\n');
        }
        return builder.toString();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
