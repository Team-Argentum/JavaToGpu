package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Semantics-neutral proposal pass that canonicalizes IR text bodies only.
 */
public final class GpuIrTextCanonicalizationProposalProvider implements GpuIrOptimizationProposalProvider {

    public static final String PROVIDER_ID = GpuIrOptimizerModule.MODULE_ID + ".text-canonicalization";
    public static final String PROVIDER_VERSION = PROVIDER_ID + ":1";

    @Override
    public GpuIrOptimizationProposal propose(GpuIrOptimizationProposalRequest request) {
        IrGpuArtifact original = request.originalArtifact();
        List<IrGpuMethodBody> canonicalBodies = new ArrayList<>();
        int changedBodies = 0;

        for (IrGpuMethodBody methodBody : original.module().methodBodies()) {
            String canonicalBody = canonicalize(methodBody.body());
            if (!canonicalBody.equals(methodBody.body())) {
                changedBodies++;
            }
            canonicalBodies.add(copyWithBody(methodBody, canonicalBody));
        }

        if (changedBodies == 0) {
            return GpuIrOptimizationProposal.noChange(
                    extensionId(),
                    extensionVersion(),
                    original,
                    "IR text bodies are already canonical"
            );
        }

        IrGpuArtifact optimized = copyWithModule(original, copyWithBodies(original.module(), canonicalBodies));
        return GpuIrOptimizationProposal.proposed(
                extensionId(),
                extensionVersion(),
                original,
                optimized,
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "ir-optimizer.text-canonicalization",
                        "semantics-neutral-text-normalization",
                        Map.of(
                                "changedMethodBodies", Integer.toString(changedBodies),
                                "mutationRequired", "false",
                                "normalizations", "crlf-to-lf,trailing-whitespace"
                        )
                ),
                List.of("canonicalized " + changedBodies + " IR text method body/bodies")
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
        return 200;
    }

    static String canonicalize(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        String normalized = body.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(stripTrailingWhitespace(lines[index]));
        }
        return builder.toString();
    }

    private static String stripTrailingWhitespace(String line) {
        int end = line.length();
        while (end > 0) {
            char value = line.charAt(end - 1);
            if (value != ' ' && value != '\t') {
                break;
            }
            end--;
        }
        return end == line.length() ? line : line.substring(0, end);
    }

    private static IrGpuMethodBody copyWithBody(IrGpuMethodBody methodBody, String body) {
        return new IrGpuMethodBody(
                methodBody.role(),
                methodBody.name(),
                methodBody.emittedName(),
                methodBody.format(),
                body,
                methodBody.typedBody(),
                methodBody.bodyIndex(),
                methodBody.helperDependencies(),
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
