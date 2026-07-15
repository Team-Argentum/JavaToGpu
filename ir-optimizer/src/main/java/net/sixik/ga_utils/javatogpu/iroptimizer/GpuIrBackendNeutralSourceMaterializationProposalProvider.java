package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrGpuSourceEmission;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Review-only materialization pass that marks reconstructable IrGpu source as backend-neutral-ready.
 */
public final class GpuIrBackendNeutralSourceMaterializationProposalProvider implements GpuIrOptimizationProposalProvider {

    public static final String PROVIDER_ID = GpuIrOptimizerModule.MODULE_ID + ".backend-neutral-source-materialization";
    public static final String PROVIDER_VERSION = PROVIDER_ID + ":1";

    @Override
    public GpuIrOptimizationProposal propose(GpuIrOptimizationProposalRequest request) {
        IrGpuArtifact original = request.originalArtifact();
        if (!"OPENCL".equals(request.contextFields().getOrDefault("backendTarget", ""))) {
            return GpuIrOptimizationProposal.noChange(
                    extensionId(),
                    extensionVersion(),
                    original,
                    "backend-neutral source materialization currently runs only for OpenCL runtime review"
            );
        }
        if (original.regenerationMetadata().backendNeutralSourceReady()) {
            return GpuIrOptimizationProposal.noChange(
                    extensionId(),
                    extensionVersion(),
                    original,
                    "IrGpu backend-neutral source is already materialized"
            );
        }

        OpenClIrGpuSourceEmission emission = OpenClIrGpuSourceEmission.inspect(original);
        if (!emission.sourceGenerated()) {
            String firstBlocker = emission.blockers().isEmpty() ? "source-emission-blocked" : emission.blockers().get(0);
            return GpuIrOptimizationProposal.noChange(
                    extensionId(),
                    extensionVersion(),
                    original,
                    "backend-neutral source materialization blocked: " + firstBlocker
            );
        }

        IrGpuArtifact optimized = copyWithRegenerationMetadata(original, IrGpuRegenerationMetadata.backendNeutralReady());
        return GpuIrOptimizationProposal.proposed(
                extensionId(),
                extensionVersion(),
                original,
                optimized,
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "ir-optimizer.backend-neutral-source-materialization",
                        "review-only-source-materialized",
                        proofFields(request, emission)
                ),
                List.of("materialized backend-neutral IrGpu source for review-only optimized backend dump")
        );
    }

    @Override
    public String extensionId() {
        return PROVIDER_ID;
    }

    @Override
    public String extensionVersion() {
        return PROVIDER_VERSION;
    }

    @Override
    public int extensionOrder() {
        return 350;
    }

    private static Map<String, String> proofFields(
            GpuIrOptimizationProposalRequest request,
            OpenClIrGpuSourceEmission emission
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("backendTarget", "OPENCL");
        fields.put("optimizerFamily", "backend-neutral-source-materialization");
        fields.put("rewrite.proposed", "true");
        fields.put("rewrite.materialized", "true");
        fields.put("sourceGenerated", "true");
        fields.put("sourceReady", "true");
        fields.put("sourceLength", Integer.toString(emission.source().length()));
        fields.put("mutationRequired", "false");
        fields.put("materializationOnly", "true");
        fields.put("productionAffecting", "false");
        fields.put("previewOnly", "false");
        fields.put("provider.mutatesOriginal", "false");
        fields.put("policy.mutationAllowed", Boolean.toString(request.mutationAllowed()));
        fields.put("proof.approvalRequiredBeforeProduction", "true");
        return Map.copyOf(fields);
    }

    private static IrGpuArtifact copyWithRegenerationMetadata(
            IrGpuArtifact artifact,
            IrGpuRegenerationMetadata regenerationMetadata
    ) {
        return new IrGpuArtifact(
                artifact.header(),
                artifact.module(),
                artifact.entryParameters(),
                artifact.launchMetadata(),
                artifact.validationMetadata(),
                artifact.featureMetadata(),
                artifact.optimizerPolicyMetadata(),
                regenerationMetadata,
                artifact.structMetadata(),
                artifact.constants(),
                artifact.constantData(),
                artifact.backendOutputs(),
                artifact.runtimeDefaultBackend(),
                artifact.runtimeOptimizationProfile(),
                artifact.methodDeviceConstraints(),
                artifact.methodFallbackVariants(),
                artifact.extensionParticipationMetadata()
        );
    }
}
