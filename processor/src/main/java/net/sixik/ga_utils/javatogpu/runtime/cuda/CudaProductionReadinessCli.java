package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Validates CUDA staged-execution evidence before any production CUDA path can be considered.
 */
public final class CudaProductionReadinessCli {
    private static final String REPORT_FILE_PROPERTY = "javatogpu.cuda.productionReadinessFile";
    private static final String PTX_SUMMARY_FILE_PROPERTY = "javatogpu.cuda.ptxSmokeSummaryFile";
    private static final String CUBIN_SUMMARY_FILE_PROPERTY = "javatogpu.cuda.cubinSmokeSummaryFile";
    private static final String FATBIN_SUMMARY_FILE_PROPERTY = "javatogpu.cuda.fatbinSmokeSummaryFile";

    private CudaProductionReadinessCli() {
    }

    public static void main(String[] args) throws IOException {
        Path reportFile = configuredPath(REPORT_FILE_PROPERTY, args.length > 0 ? args[0] : null);
        CudaProductionReadinessReport report = CudaProductionReadinessReport.inspect(
                configuredPath(PTX_SUMMARY_FILE_PROPERTY, null),
                configuredPath(CUBIN_SUMMARY_FILE_PROPERTY, null),
                configuredPath(FATBIN_SUMMARY_FILE_PROPERTY, null)
        );
        if (reportFile != null) {
            write(report, reportFile);
        }
        System.out.println(render(report, reportFile));
        if (report.blocked()) {
            System.exit(1);
        }
    }

    public static String render(CudaProductionReadinessReport report) {
        return render(report, null);
    }

    public static String render(CudaProductionReadinessReport report, Path reportFile) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA production readiness:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- reviewReady=").append(report.reviewReady()).append(System.lineSeparator());
        builder.append("- productionReady=").append(report.productionReady()).append(System.lineSeparator());
        builder.append("- productionExecutionEnabled=").append(report.productionExecutionEnabled()).append(System.lineSeparator());
        builder.append("- productionPolicyAccepted=").append(report.productionPolicyAccepted()).append(System.lineSeparator());
        builder.append("- inventoryStatus=").append(report.inventoryContract().status()).append(System.lineSeparator());
        builder.append("- executionReadinessStatus=").append(report.executionReadiness().status()).append(System.lineSeparator());
        builder.append("- binaryEvidenceReady=").append(report.binaryEvidenceReady()).append(System.lineSeparator());
        builder.append("- binaryRichEvidence=").append(report.binaryRichEvidenceCount()).append("/2").append(System.lineSeparator());
        builder.append("- structValueEvidenceReady=").append(report.structValueEvidenceReady()).append(System.lineSeparator());
        builder.append("- localStructEvidenceReady=").append(report.localStructEvidenceReady()).append(System.lineSeparator());
        builder.append("- ptxEvidenceAcceptable=").append(report.ptxEvidenceAcceptable()).append(System.lineSeparator());
        appendSmoke(builder, report.ptxSmokeSummary());
        appendSmoke(builder, report.cubinSmokeSummary());
        appendSmoke(builder, report.fatbinSmokeSummary());
        builder.append("- blockerCount=").append(report.blockers().size()).append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        builder.append("- remainingWorkCount=").append(report.remainingWork().size()).append(System.lineSeparator());
        for (String item : report.remainingWork()) {
            builder.append("- remainingWork=").append(item).append(System.lineSeparator());
        }
        if (reportFile != null) {
            builder.append("- reportFile=").append(reportFile).append(System.lineSeparator());
        }
        builder.append("- rule=review-ready is valid, but CUDA production remains disabled until policy and coverage are accepted")
                .append(System.lineSeparator());
        return builder.toString();
    }

    private static void appendSmoke(StringBuilder builder, CudaProductionReadinessReport.SmokeSummary summary) {
        builder.append("- smoke.")
                .append(summary.expectedFormat())
                .append("=status:")
                .append(summary.status())
                .append(",rich:")
                .append(summary.richEvidence())
                .append(",realDriver:")
                .append(summary.realDriverCount())
                .append('/')
                .append(summary.executedCount())
                .append(",firstBlocker:")
                .append(summary.firstBlocker())
                .append(System.lineSeparator());
    }

    private static Path configuredPath(String property, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = fallback;
        }
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value.trim());
    }

    private static void write(CudaProductionReadinessReport report, Path reportFile) throws IOException {
        Path parent = reportFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Properties properties = report.toProperties();
        try (OutputStream stream = Files.newOutputStream(reportFile)) {
            properties.store(stream, "JavaToGpu CUDA production readiness");
        }
    }
}
