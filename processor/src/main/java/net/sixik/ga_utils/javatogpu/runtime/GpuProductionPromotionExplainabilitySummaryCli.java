package net.sixik.ga_utils.javatogpu.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Command-line entrypoint for CI summaries of production-promotion explainability artifacts. */
public final class GpuProductionPromotionExplainabilitySummaryCli {

    private GpuProductionPromotionExplainabilitySummaryCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2 || args[0] == null || args[0].isBlank()) {
            throw new IllegalArgumentException(
                    "Expected path to production-promotion explainability artifact and optional output properties path"
            );
        }
        Path artifactPath = Path.of(args[0]);
        if (!Files.isRegularFile(artifactPath)) {
            throw new IllegalStateException("Missing production-promotion explainability artifact: " + artifactPath);
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(artifactPath)) {
            properties.load(inputStream);
        }

        GpuProductionPromotionExplainabilitySummary summary =
                GpuProductionPromotionExplainabilitySummary.fromProperties(properties);
        String formatted = format(summary);
        if (args.length == 2 && args[1] != null && !args[1].isBlank()) {
            Path outputPath = Path.of(args[1]);
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputPath, formatted, StandardCharsets.UTF_8);
        }
        System.out.println("Production-promotion explainability summary: " + summary.historyStatus());
    }

    public static String format(GpuProductionPromotionExplainabilitySummary summary) {
        StringBuilder builder = new StringBuilder();
        builder.append("status=").append(summary.status()).append('\n');
        builder.append("contract.status=").append(summary.contractStatus()).append('\n');
        builder.append("contract.valid=").append(summary.contractValid()).append('\n');
        builder.append("contract.violation.0=").append(summary.firstViolation()).append('\n');
        builder.append("decision.mode=").append(summary.decisionMode()).append('\n');
        builder.append("productionSourceSwitchingAllowed=").append(summary.productionSourceSwitchingAllowed()).append('\n');
        builder.append("productionSourceSwitchingEnabled=").append(summary.productionSourceSwitchingEnabled()).append('\n');
        builder.append("productionMutationAllowed=").append(summary.productionMutationAllowed()).append('\n');
        builder.append("productionMutationEnabled=").append(summary.productionMutationEnabled()).append('\n');
        builder.append("blocker.count=").append(summary.blockerCount()).append('\n');
        builder.append("blocker.0=").append(summary.firstBlocker()).append('\n');
        builder.append("i3ReviewReady.count=").append(summary.i3ReviewReadyCount()).append('\n');
        builder.append("i3Blocked.count=").append(summary.i3BlockedCount()).append('\n');
        builder.append("i3SourceReady.count=").append(summary.i3SourceReadyCount()).append('\n');
        builder.append("i3SourceReady.all=").append(summary.i3SourceReadyAll()).append('\n');
        builder.append("backendPromotionArtifactSupport.complete=")
                .append(summary.backendPromotionArtifactSupportComplete())
                .append('\n');
        builder.append("backendPromotionArtifactSupport.missing.count=")
                .append(summary.backendPromotionArtifactSupportMissingCount())
                .append('\n');
        builder.append("historyStatus=").append(summary.historyStatus()).append('\n');
        return builder.toString();
    }
}
