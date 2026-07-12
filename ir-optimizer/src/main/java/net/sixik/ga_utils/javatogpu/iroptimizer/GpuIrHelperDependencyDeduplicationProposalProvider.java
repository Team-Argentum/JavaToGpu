package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Semantics-neutral metadata cleanup pass that removes duplicate helper dependencies.
 */
public final class GpuIrHelperDependencyDeduplicationProposalProvider implements GpuIrOptimizationProposalProvider {

    public static final String PROVIDER_ID = GpuIrOptimizerModule.MODULE_ID + ".helper-dependency-deduplication";
    public static final String PROVIDER_VERSION = PROVIDER_ID + ":1";

    @Override
    public GpuIrOptimizationProposal propose(GpuIrOptimizationProposalRequest request) {
        IrGpuArtifact original = request.originalArtifact();
        ArrayList<IrGpuMethodBody> rewrittenBodies = new ArrayList<>();
        int changedBodies = 0;
        int removedDependencies = 0;

        for (IrGpuMethodBody methodBody : original.module().methodBodies()) {
            List<String> deduplicated = deduplicate(methodBody.helperDependencies());
            if (!deduplicated.equals(methodBody.helperDependencies())) {
                changedBodies++;
                removedDependencies += methodBody.helperDependencies().size() - deduplicated.size();
                rewrittenBodies.add(copyWithHelperDependencies(methodBody, deduplicated));
            } else {
                rewrittenBodies.add(methodBody);
            }
        }

        if (changedBodies == 0) {
            return GpuIrOptimizationProposal.noChange(
                    extensionId(),
                    extensionVersion(),
                    original,
                    "IR helper dependency metadata is already deduplicated"
            );
        }

        IrGpuArtifact optimized = copyWithModule(original, copyWithBodies(original.module(), rewrittenBodies));
        return GpuIrOptimizationProposal.proposed(
                extensionId(),
                extensionVersion(),
                original,
                optimized,
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "ir-optimizer.helper-dependency-deduplication",
                        "semantics-neutral-metadata-cleanup",
                        Map.of(
                                "changedMethodBodies", Integer.toString(changedBodies),
                                "removedDuplicateDependencies", Integer.toString(removedDependencies),
                                "mutationRequired", "false",
                                "preservedOrder", "first-occurrence"
                        )
                ),
                List.of("deduplicated helper dependency metadata in " + changedBodies + " method body/bodies")
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
        return 300;
    }

    static List<String> deduplicate(List<String> helperDependencies) {
        if (helperDependencies == null || helperDependencies.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String dependency : helperDependencies) {
            if (dependency != null && !dependency.isBlank()) {
                unique.add(dependency);
            }
        }
        return List.copyOf(unique);
    }

    private static IrGpuMethodBody copyWithHelperDependencies(
            IrGpuMethodBody methodBody,
            List<String> helperDependencies
    ) {
        return new IrGpuMethodBody(
                methodBody.role(),
                methodBody.name(),
                methodBody.emittedName(),
                methodBody.format(),
                methodBody.body(),
                methodBody.typedBody(),
                methodBody.bodyIndex(),
                helperDependencies,
                methodBody.sourceLocation()
        );
    }

    private static IrGpuModule copyWithBodies(IrGpuModule module, List<IrGpuMethodBody> methodBodies) {
        return new IrGpuModule(
                module.entryMethod(),
                module.entryEmittedName(),
                module.entryOpenClAttributes(),
                module.entryAttributeMetadata(),
                module.helperMethods(),
                module.structs(),
                methodBodies
        );
    }

    private static IrGpuArtifact copyWithModule(IrGpuArtifact artifact, IrGpuModule module) {
        return new IrGpuArtifact(
                artifact.header(),
                module,
                artifact.entryParameters(),
                artifact.launchMetadata(),
                artifact.validationMetadata(),
                artifact.featureMetadata(),
                artifact.optimizerPolicyMetadata(),
                artifact.regenerationMetadata(),
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
