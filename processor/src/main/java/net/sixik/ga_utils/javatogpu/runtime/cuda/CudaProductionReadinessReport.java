package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Aggregates CUDA staged-execution evidence into an explicit production-readiness gate.
 */
public record CudaProductionReadinessReport(
        CudaInventoryContractReport inventoryContract,
        CudaExecutionReadinessReport executionReadiness,
        SmokeSummary ptxSmokeSummary,
        SmokeSummary cubinSmokeSummary,
        SmokeSummary fatbinSmokeSummary,
        boolean productionPolicyAccepted
) {

    public static final String FORMAT = "javatogpu.cuda-production-readiness.v1";

    public CudaProductionReadinessReport {
        inventoryContract = inventoryContract == null
                ? CudaInventoryContractReport.inspectBuiltInProvider()
                : inventoryContract;
        executionReadiness = executionReadiness == null
                ? CudaExecutionReadinessReport.inspectBuiltIns()
                : executionReadiness;
        ptxSmokeSummary = ptxSmokeSummary == null ? SmokeSummary.missing("ptx") : ptxSmokeSummary;
        cubinSmokeSummary = cubinSmokeSummary == null ? SmokeSummary.missing("cubin") : cubinSmokeSummary;
        fatbinSmokeSummary = fatbinSmokeSummary == null ? SmokeSummary.missing("fatbin") : fatbinSmokeSummary;
    }

    public static CudaProductionReadinessReport inspect(
            Properties ptxSmokeSummary,
            Properties cubinSmokeSummary,
            Properties fatbinSmokeSummary
    ) {
        return new CudaProductionReadinessReport(
                CudaInventoryContractReport.inspectBuiltInProvider(),
                CudaExecutionReadinessReport.inspectBuiltIns(),
                SmokeSummary.from(ptxSmokeSummary, "ptx"),
                SmokeSummary.from(cubinSmokeSummary, "cubin"),
                SmokeSummary.from(fatbinSmokeSummary, "fatbin"),
                false
        );
    }

    public static CudaProductionReadinessReport inspect(Path ptxPath, Path cubinPath, Path fatbinPath) throws IOException {
        return inspect(load(ptxPath), load(cubinPath), load(fatbinPath));
    }

    public boolean blocked() {
        return !blockers().isEmpty();
    }

    public boolean reviewReady() {
        return !blocked() && !productionReady();
    }

    public boolean productionReady() {
        return !blocked() && productionExecutionEnabled() && productionPolicyAccepted;
    }

    public String status() {
        if (blocked()) {
            return "blocked";
        }
        return productionReady() ? "production-ready" : "review-ready";
    }

    public String firstBlocker() {
        return blockers().stream().findFirst().orElse("none");
    }

    public boolean productionExecutionEnabled() {
        return executionReadiness.cudaProvider()
                .map(provider -> provider.executionSupport().productionExecution())
                .orElse(false);
    }

    public boolean binaryEvidenceReady() {
        return cubinSmokeSummary.passedWithRichEvidence() && fatbinSmokeSummary.passedWithRichEvidence();
    }

    public boolean ptxEvidenceAcceptable() {
        if (ptxSmokeSummary.failed()) {
            return false;
        }
        return !ptxSmokeSummary.passed() || ptxSmokeSummary.passedWithRichEvidence();
    }

    public int binaryRichEvidenceCount() {
        int count = 0;
        count += cubinSmokeSummary.passedWithRichEvidence() ? 1 : 0;
        count += fatbinSmokeSummary.passedWithRichEvidence() ? 1 : 0;
        return count;
    }

    public boolean structValueEvidenceReady() {
        return cubinSmokeSummary.passedWithStructValueEvidence()
                && fatbinSmokeSummary.passedWithStructValueEvidence();
    }

    public boolean localStructEvidenceReady() {
        return cubinSmokeSummary.passedWithLocalStructEvidence()
                && fatbinSmokeSummary.passedWithLocalStructEvidence();
    }

    public List<String> blockers() {
        ArrayList<String> blockers = new ArrayList<>();
        if (!inventoryContract.ready()) {
            blockers.add("cuda-inventory-contract-not-ready:" + inventoryContract.firstBlocker());
        }
        if (!executionReadiness.ready()) {
            blockers.add("cuda-execution-readiness-not-ready:" + executionReadiness.firstBlocker());
        }
        addSmokeBlockers(blockers, ptxSmokeSummary, false);
        addSmokeBlockers(blockers, cubinSmokeSummary, true);
        addSmokeBlockers(blockers, fatbinSmokeSummary, true);
        if (productionExecutionEnabled() && !productionPolicyAccepted) {
            blockers.add("cuda-production-execution-enabled-without-policy");
        }
        return List.copyOf(blockers);
    }

    public List<String> remainingWork() {
        if (blocked()) {
            return List.of("resolve-blockers-before-production-review");
        }
        if (productionReady()) {
            return List.of();
        }
        ArrayList<String> work = new ArrayList<>(List.of(
                "cuda-production-policy-not-accepted",
                "cuda-runtime-auto-selection-not-enabled",
                "cuda-image-sampler-coverage-pending",
                "cuda-cross-device-validation-pending"
        ));
        if (!structValueEvidenceReady()) {
            work.add(3, "cuda-struct-value-real-kernel-validation-pending");
        }
        if (!localStructEvidenceReady()) {
            int insertionIndex = structValueEvidenceReady() ? 3 : 4;
            work.add(insertionIndex, "cuda-local-struct-coverage-pending");
        }
        return List.copyOf(work);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.productionReadiness"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.productionReadiness");
        return Collections.unmodifiableMap(fields);
    }

    public Properties toProperties() {
        Properties properties = new Properties();
        properties.setProperty("format", FORMAT);
        properties.setProperty("status", status());
        properties.setProperty("reviewReady", Boolean.toString(reviewReady()));
        properties.setProperty("productionReady", Boolean.toString(productionReady()));
        properties.setProperty("productionExecution.enabled", Boolean.toString(productionExecutionEnabled()));
        properties.setProperty("productionPolicy.accepted", Boolean.toString(productionPolicyAccepted));
        properties.setProperty("binaryEvidence.ready", Boolean.toString(binaryEvidenceReady()));
        properties.setProperty("binaryEvidence.rich.count", Integer.toString(binaryRichEvidenceCount()));
        properties.setProperty("structValueEvidence.ready", Boolean.toString(structValueEvidenceReady()));
        properties.setProperty("localStructEvidence.ready", Boolean.toString(localStructEvidenceReady()));
        properties.setProperty("ptxEvidence.acceptable", Boolean.toString(ptxEvidenceAcceptable()));
        properties.setProperty("inventory.status", inventoryContract.status());
        properties.setProperty("executionReadiness.status", executionReadiness.status());
        properties.setProperty("firstBlocker", firstBlocker());
        putSmokeProperties(properties, "smoke.ptx", ptxSmokeSummary);
        putSmokeProperties(properties, "smoke.cubin", cubinSmokeSummary);
        putSmokeProperties(properties, "smoke.fatbin", fatbinSmokeSummary);
        putList(properties, "blocker", blockers());
        putList(properties, "remainingWork", remainingWork());
        artifactFields("runtime.cuda.productionReadiness").forEach(properties::setProperty);
        return properties;
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA production readiness: ").append(status()).append('\n');
        builder.append("Inventory contract: ").append(inventoryContract.status()).append('\n');
        builder.append("Execution readiness: ").append(executionReadiness.status()).append('\n');
        builder.append("Production execution enabled: ").append(productionExecutionEnabled()).append('\n');
        builder.append("Production policy accepted: ").append(productionPolicyAccepted).append('\n');
        builder.append("Binary evidence: ").append(binaryRichEvidenceCount()).append("/2 rich lanes").append('\n');
        builder.append("Struct VALUE evidence: ").append(structValueEvidenceReady()).append('\n');
        builder.append("Local struct evidence: ").append(localStructEvidenceReady()).append('\n');
        builder.append("PTX evidence acceptable: ").append(ptxEvidenceAcceptable()).append('\n');
        builder.append("Smoke summaries:").append('\n');
        appendSmoke(builder, ptxSmokeSummary);
        appendSmoke(builder, cubinSmokeSummary);
        appendSmoke(builder, fatbinSmokeSummary);
        if (!blockers().isEmpty()) {
            builder.append('\n').append("Blockers:").append('\n');
            for (String blocker : blockers()) {
                builder.append("- ").append(blocker).append('\n');
            }
        }
        if (!remainingWork().isEmpty()) {
            builder.append('\n').append("Remaining work:").append('\n');
            for (String item : remainingWork()) {
                builder.append("- ").append(item).append('\n');
            }
        }
        return builder.toString();
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", "true");
        fields.put(prefix + ".format", FORMAT);
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".reviewReady", Boolean.toString(reviewReady()));
        fields.put(prefix + ".productionReady", Boolean.toString(productionReady()));
        fields.put(prefix + ".productionExecution.enabled", Boolean.toString(productionExecutionEnabled()));
        fields.put(prefix + ".productionPolicy.accepted", Boolean.toString(productionPolicyAccepted));
        fields.put(prefix + ".binaryEvidence.ready", Boolean.toString(binaryEvidenceReady()));
        fields.put(prefix + ".binaryEvidence.rich.count", Integer.toString(binaryRichEvidenceCount()));
        fields.put(prefix + ".structValueEvidence.ready", Boolean.toString(structValueEvidenceReady()));
        fields.put(prefix + ".localStructEvidence.ready", Boolean.toString(localStructEvidenceReady()));
        fields.put(prefix + ".ptxEvidence.acceptable", Boolean.toString(ptxEvidenceAcceptable()));
        fields.put(prefix + ".inventory.status", inventoryContract.status());
        fields.put(prefix + ".executionReadiness.status", executionReadiness.status());
        fields.put(prefix + ".firstBlocker", firstBlocker());
        putSmokeFields(fields, prefix + ".smoke.ptx", ptxSmokeSummary);
        putSmokeFields(fields, prefix + ".smoke.cubin", cubinSmokeSummary);
        putSmokeFields(fields, prefix + ".smoke.fatbin", fatbinSmokeSummary);
        putIndexedFields(fields, prefix + ".blocker", blockers());
        putIndexedFields(fields, prefix + ".remainingWork", remainingWork());
    }

    private static void addSmokeBlockers(List<String> blockers, SmokeSummary smokeSummary, boolean richRequired) {
        if (!smokeSummary.present()) {
            blockers.add("cuda-smoke-summary-missing:" + smokeSummary.expectedFormat());
            return;
        }
        if (smokeSummary.formatMismatch()) {
            blockers.add("cuda-smoke-summary-format-mismatch:expected-"
                    + smokeSummary.expectedFormat()
                    + ":actual-"
                    + smokeSummary.outputFormat());
        }
        if (smokeSummary.failed()) {
            blockers.add("cuda-smoke-summary-failed:"
                    + smokeSummary.expectedFormat()
                    + ":"
                    + smokeSummary.firstBlocker());
        }
        if (richRequired && !smokeSummary.passedWithRichEvidence()) {
            blockers.add("cuda-binary-smoke-rich-evidence-missing:"
                    + smokeSummary.expectedFormat()
                    + ":status-"
                    + smokeSummary.status()
                    + ":"
                    + smokeSummary.firstBlocker());
        }
        if (!richRequired && smokeSummary.passed() && !smokeSummary.passedWithRichEvidence()) {
            blockers.add("cuda-ptx-smoke-rich-evidence-incomplete:" + smokeSummary.firstBlocker());
        }
    }

    private static Properties load(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            return new Properties();
        }
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(path)) {
            properties.load(stream);
        }
        return properties;
    }

    private static void appendSmoke(StringBuilder builder, SmokeSummary summary) {
        builder.append("- ")
                .append(summary.expectedFormat())
                .append(": status=")
                .append(summary.status())
                .append(", rich=")
                .append(summary.richEvidence())
                .append(", realDriver=")
                .append(summary.realDriverCount())
                .append('/')
                .append(summary.executedCount())
                .append(", firstBlocker=")
                .append(summary.firstBlocker())
                .append('\n');
    }

    private static void putSmokeProperties(Properties properties, String prefix, SmokeSummary summary) {
        properties.setProperty(prefix + ".present", Boolean.toString(summary.present()));
        properties.setProperty(prefix + ".status", summary.status());
        properties.setProperty(prefix + ".outputFormat", summary.outputFormat());
        properties.setProperty(prefix + ".richEvidence", Boolean.toString(summary.richEvidence()));
        properties.setProperty(prefix + ".passedWithRichEvidence", Boolean.toString(summary.passedWithRichEvidence()));
        properties.setProperty(prefix + ".executed.count", Integer.toString(summary.executedCount()));
        properties.setProperty(prefix + ".evidence.count", Integer.toString(summary.evidenceCount()));
        properties.setProperty(prefix + ".evidence.realDriver.count", Integer.toString(summary.realDriverCount()));
        properties.setProperty(prefix + ".evidence.scenario.struct-value.realDriver.count", Integer.toString(summary.structValueRealDriverCount()));
        properties.setProperty(prefix + ".evidence.scenario.local-struct.realDriver.count", Integer.toString(summary.localStructRealDriverCount()));
        properties.setProperty(prefix + ".firstBlocker", summary.firstBlocker());
    }

    private static void putList(Properties properties, String prefix, List<String> values) {
        properties.setProperty(prefix + ".count", Integer.toString(values.size()));
        for (int index = 0; index < values.size(); index++) {
            properties.setProperty(prefix + "." + index, values.get(index));
        }
    }

    private static void putSmokeFields(Map<String, String> fields, String prefix, SmokeSummary summary) {
        fields.put(prefix + ".present", Boolean.toString(summary.present()));
        fields.put(prefix + ".status", summary.status());
        fields.put(prefix + ".outputFormat", summary.outputFormat());
        fields.put(prefix + ".richEvidence", Boolean.toString(summary.richEvidence()));
        fields.put(prefix + ".passedWithRichEvidence", Boolean.toString(summary.passedWithRichEvidence()));
        fields.put(prefix + ".executed.count", Integer.toString(summary.executedCount()));
        fields.put(prefix + ".evidence.count", Integer.toString(summary.evidenceCount()));
        fields.put(prefix + ".evidence.realDriver.count", Integer.toString(summary.realDriverCount()));
        fields.put(prefix + ".evidence.scenario.struct-value.realDriver.count", Integer.toString(summary.structValueRealDriverCount()));
        fields.put(prefix + ".evidence.scenario.local-struct.realDriver.count", Integer.toString(summary.localStructRealDriverCount()));
        fields.put(prefix + ".firstBlocker", summary.firstBlocker());
    }

    private static void putIndexedFields(Map<String, String> fields, String prefix, List<String> values) {
        fields.put(prefix + ".count", Integer.toString(values.size()));
        for (int index = 0; index < values.size(); index++) {
            fields.put(prefix + "." + index, values.get(index));
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value == null || value.isBlank() ? "0" : value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public record SmokeSummary(
            String expectedFormat,
            boolean present,
            String taskName,
            String status,
            String outputFormat,
            boolean richEvidence,
            int executedCount,
            int evidenceCount,
            int realDriverCount,
            int structValueRealDriverCount,
            int localStructRealDriverCount,
            String firstBlocker
    ) {

        public SmokeSummary {
            expectedFormat = normalize(expectedFormat, "unknown");
            taskName = normalize(taskName, "unknown");
            status = normalize(status, present ? "unknown" : "missing");
            outputFormat = normalize(outputFormat, expectedFormat);
            executedCount = Math.max(0, executedCount);
            evidenceCount = Math.max(0, evidenceCount);
            realDriverCount = Math.max(0, realDriverCount);
            structValueRealDriverCount = Math.max(0, structValueRealDriverCount);
            localStructRealDriverCount = Math.max(0, localStructRealDriverCount);
            firstBlocker = normalize(firstBlocker, present ? "none" : "summary-missing");
        }

        public static SmokeSummary missing(String expectedFormat) {
            return new SmokeSummary(expectedFormat, false, "missing", "missing", expectedFormat, false, 0, 0, 0, 0, 0, "summary-missing");
        }

        public static SmokeSummary from(Properties properties, String expectedFormat) {
            if (properties == null || properties.isEmpty()) {
                return missing(expectedFormat);
            }
            return new SmokeSummary(
                    expectedFormat,
                    true,
                    properties.getProperty("taskName", "unknown"),
                    properties.getProperty("status", "unknown"),
                    properties.getProperty("nvcc.outputFormat", expectedFormat),
                    Boolean.parseBoolean(properties.getProperty("realDriverExecutionEvidence.rich", "false")),
                    parseInt(properties.getProperty("test.executed.count")),
                    parseInt(properties.getProperty("evidence.count")),
                    parseInt(properties.getProperty("evidence.realDriver.count")),
                    parseInt(properties.getProperty("evidence.scenario.struct-value.realDriver.count")),
                    parseInt(properties.getProperty("evidence.scenario.local-struct.realDriver.count")),
                    properties.getProperty("firstBlocker", "none")
            );
        }

        public boolean passed() {
            return "passed".equals(status);
        }

        public boolean failed() {
            return "failed".equals(status);
        }

        public boolean passedWithRichEvidence() {
            return passed() && richEvidence && executedCount > 0 && realDriverCount >= executedCount;
        }

        public boolean passedWithStructValueEvidence() {
            return passedWithRichEvidence() && structValueRealDriverCount > 0;
        }

        public boolean passedWithLocalStructEvidence() {
            return passedWithRichEvidence() && localStructRealDriverCount > 0;
        }

        public boolean formatMismatch() {
            return present && !Objects.equals(expectedFormat, outputFormat);
        }

        private static String normalize(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }
    }
}
