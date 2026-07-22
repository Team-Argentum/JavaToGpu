package net.sixik.ga_utils.javatogpu.runtime.validation;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourcePromotionWorkloadSummary;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Command-line entrypoint for CI summaries of backend source-promotion workload gates. */
public final class GpuBackendSourcePromotionWorkloadSummaryCli {

    private GpuBackendSourcePromotionWorkloadSummaryCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2 || args[0] == null || args[0].isBlank()) {
            throw new IllegalArgumentException(
                    "Expected path to backend source-promotion workload gate and optional output properties path"
            );
        }
        Path artifactPath = Path.of(args[0]);
        if (!Files.isRegularFile(artifactPath)) {
            throw new IllegalStateException("Missing backend source-promotion workload gate artifact: " + artifactPath);
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(artifactPath)) {
            properties.load(inputStream);
        }

        GpuBackendSourcePromotionWorkloadSummary summary =
                GpuBackendSourcePromotionWorkloadSummary.fromProperties(properties);
        String formatted = format(summary);
        if (args.length == 2 && args[1] != null && !args[1].isBlank()) {
            Path outputPath = Path.of(args[1]);
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputPath, formatted, StandardCharsets.UTF_8);
        }
        System.out.println("Backend source-promotion workload summary: " + summary.historyStatus());
    }

    public static String format(GpuBackendSourcePromotionWorkloadSummary summary) {
        StringBuilder builder = new StringBuilder();
        builder.append("status=").append(summary.status()).append('\n');
        builder.append("kernel.count=").append(summary.kernelCount()).append('\n');
        builder.append("reviewReady=").append(summary.reviewReady()).append('\n');
        builder.append("sourceParityMatched=").append(summary.sourceParityMatched()).append('\n');
        builder.append("runtimeEquivalencePassed=").append(summary.runtimeEquivalencePassed()).append('\n');
        builder.append("realWorkloadEvidence=").append(summary.realWorkloadEvidence()).append('\n');
        builder.append("productionSourceSwitching=").append(summary.productionSourceSwitching()).append('\n');
        builder.append("runtime.backend.source.productionPromotionOperatorAccepted.count=")
                .append(summary.productionPromotionOperatorAcceptedCount())
                .append('\n');
        builder.append("runtime.backend.source.productionPromotionOperatorAccepted.all=")
                .append(summary.productionPromotionOperatorAcceptedAll())
                .append('\n');
        builder.append("productionPromotionOperatorAccepted.count=")
                .append(summary.productionPromotionOperatorAcceptedCount())
                .append('\n');
        builder.append("productionPromotionOperatorAccepted.all=")
                .append(summary.productionPromotionOperatorAcceptedAll())
                .append('\n');
        builder.append("runtime.backend.source.decisions=").append(summary.sourceSwitchingDecisions()).append('\n');
        builder.append("sourceSwitching.decisions=").append(summary.sourceSwitchingDecisions()).append('\n');
        builder.append("runtime.backend.source.promotionFirstBlockers=").append(summary.sourcePromotionFirstBlockers()).append('\n');
        builder.append("sourcePromotionFirstBlockers=").append(summary.sourcePromotionFirstBlockers()).append('\n');
        builder.append("runtime.backend.source.promotionFirstBlockerFamilies=")
                .append(summary.sourcePromotionFirstBlockerFamilies())
                .append('\n');
        builder.append("sourcePromotionFirstBlockerFamilies=").append(summary.sourcePromotionFirstBlockerFamilies()).append('\n');
        appendFirstCount(
                builder,
                "runtime.backend.source.promotionFirstBlockerFamily",
                summary.sourcePromotionFirstBlockerFamilies()
        );
        appendFirstCount(builder, "sourcePromotionFirstBlockerFamily", summary.sourcePromotionFirstBlockerFamilies());
        builder.append("optimizerProofArtifact.count=").append(summary.optimizerProofArtifactCount()).append('\n');
        builder.append("optimizerProofArtifact.accepted.count=")
                .append(summary.optimizerAcceptedProofArtifactCount())
                .append('\n');
        builder.append("optimizerProofArtifact.blocking.count=")
                .append(summary.optimizerBlockingProofArtifactCount())
                .append('\n');
        builder.append("optimizerFamily.count=").append(summary.optimizerFamilyCount()).append('\n');
        builder.append("optimizerFamily.promotionReady.count=")
                .append(summary.optimizerFamilyPromotionReadyCount())
                .append('\n');
        builder.append("optimizerFamily.summary=").append(summary.optimizerFamilySummary()).append('\n');
        builder.append("optimizerFamilyPayload.complete.count=")
                .append(summary.optimizerFamilyPayloadCompleteCount())
                .append('\n');
        builder.append("optimizerFamilyPayload.complete.all=")
                .append(summary.optimizerFamilyPayloadCompleteAll())
                .append('\n');
        builder.append("runtimeExtensionParticipation.recordedKernel.count=")
                .append(summary.runtimeExtensionParticipationRecordedKernelCount())
                .append('\n');
        builder.append("runtimeExtensionParticipation.entry.count=")
                .append(summary.runtimeExtensionParticipationEntryCount())
                .append('\n');
        builder.append("runtimeExtensionParticipation.failedContinued.count=")
                .append(summary.runtimeExtensionParticipationFailedContinuedCount())
                .append('\n');
        builder.append("runtimeExtensionParticipation.failedClosed.count=")
                .append(summary.runtimeExtensionParticipationFailedClosedCount())
                .append('\n');
        builder.append("runtimeExtensionParticipation.sources=")
                .append(summary.runtimeExtensionParticipationSources())
                .append('\n');
        appendFirstCount(
                builder,
                "runtimeExtensionParticipation.source",
                summary.runtimeExtensionParticipationSources()
        );
        builder.append("historyStatus=").append(summary.historyStatus()).append('\n');
        return builder.toString();
    }

    private static void appendFirstCount(StringBuilder builder, String keyPrefix, String counts) {
        if (counts == null || counts.isBlank()) {
            builder.append(keyPrefix).append(".0.name=none\n");
            builder.append(keyPrefix).append(".0.count=0\n");
            return;
        }
        String first = counts.split(", ", 2)[0];
        int separator = first.lastIndexOf('=');
        if (separator <= 0 || separator == first.length() - 1) {
            builder.append(keyPrefix).append(".0.name=").append(first).append('\n');
            builder.append(keyPrefix).append(".0.count=0\n");
            return;
        }
        builder.append(keyPrefix).append(".0.name=").append(first, 0, separator).append('\n');
        builder.append(keyPrefix).append(".0.count=").append(first.substring(separator + 1)).append('\n');
    }
}
