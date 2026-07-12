package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Aggregates optional ir-optimizer evidence artifacts into the OpenCL validation report.
 */
record OpenClRuntimeIrOptimizerEvidenceSummary(String status, List<Entry> entries, String diagnostic) {

    private static final String ARTIFACT_FILE_NAME =
            GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT;

    OpenClRuntimeIrOptimizerEvidenceSummary {
        status = status == null || status.isBlank() ? "unknown" : status;
        entries = entries == null ? List.of() : List.copyOf(entries);
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    static OpenClRuntimeIrOptimizerEvidenceSummary notRecorded() {
        return new OpenClRuntimeIrOptimizerEvidenceSummary("not-recorded", List.of(), "");
    }

    static OpenClRuntimeIrOptimizerEvidenceSummary failed(Throwable failure) {
        return new OpenClRuntimeIrOptimizerEvidenceSummary(
                "failed",
                List.of(),
                failure == null ? "unknown failure" : failure.toString()
        );
    }

    static OpenClRuntimeIrOptimizerEvidenceSummary read(Path workloadGateFile) throws IOException {
        if (workloadGateFile == null || !Files.isRegularFile(workloadGateFile)) {
            return notRecorded();
        }
        Properties gate = loadProperties(workloadGateFile);
        int kernelCount = parseInt(gate.getProperty("kernel.count"), 0);
        if (kernelCount <= 0) {
            return notRecorded();
        }

        Path reportDirectory = workloadGateFile.getParent();
        Path artifactRoot = reportDirectory == null
                ? null
                : reportDirectory.resolve("runtime-compile-artifacts");
        Map<String, Properties> evidenceByResource = loadEvidenceArtifacts(artifactRoot);
        ArrayList<Entry> entries = new ArrayList<>();
        for (int index = 0; index < kernelCount; index++) {
            String resource = gate.getProperty("kernel." + index + ".sourceKernelResource", "unknown");
            entries.add(Entry.from(resource, evidenceByResource.get(resource)));
        }
        return new OpenClRuntimeIrOptimizerEvidenceSummary("recorded", entries, "");
    }

    int count(String expectedStatus) {
        int count = 0;
        for (Entry entry : entries) {
            if (expectedStatus.equals(entry.status())) {
                count++;
            }
        }
        return count;
    }

    int totalPassCount() {
        return entries.stream().mapToInt(Entry::passCount).sum();
    }

    int totalProposalOnlyCount() {
        return entries.stream().mapToInt(Entry::proposalOnlyCount).sum();
    }

    int totalSelectedOptimizedCount() {
        return entries.stream().mapToInt(Entry::selectedOptimizedCount).sum();
    }

    int totalRolledBackCount() {
        return entries.stream().mapToInt(Entry::rolledBackCount).sum();
    }

    int totalApprovalTemplatePendingCount() {
        return entries.stream().mapToInt(Entry::approvalTemplatePendingCount).sum();
    }

    int totalApprovalTemplateNotApplicableCount() {
        return entries.stream().mapToInt(Entry::approvalTemplateNotApplicableCount).sum();
    }

    int totalConstantFoldingPreviewPassCount() {
        return entries.stream().mapToInt(Entry::constantFoldingPreviewPassCount).sum();
    }

    int totalConstantFoldingPreviewCandidateCount() {
        return entries.stream().mapToInt(Entry::constantFoldingPreviewCandidateCount).sum();
    }

    int totalConstantFoldingPreviewSkippedCount() {
        return entries.stream().mapToInt(Entry::constantFoldingPreviewSkippedCount).sum();
    }

    int totalSafeLocalCsePreviewPassCount() {
        return entries.stream().mapToInt(Entry::safeLocalCsePreviewPassCount).sum();
    }

    int totalSafeLocalCsePreviewCandidateExpressionCount() {
        return entries.stream().mapToInt(Entry::safeLocalCsePreviewCandidateExpressionCount).sum();
    }

    int totalSafeLocalCsePreviewDuplicateExpressionCount() {
        return entries.stream().mapToInt(Entry::safeLocalCsePreviewDuplicateExpressionCount).sum();
    }

    int totalSafeLocalCsePreviewBlockedCount() {
        return entries.stream().mapToInt(Entry::safeLocalCsePreviewBlockedCount).sum();
    }

    int totalTypedDeadCodePreviewPassCount() {
        return entries.stream().mapToInt(Entry::typedDeadCodePreviewPassCount).sum();
    }

    int totalTypedDeadCodePreviewUnreachableNodeCount() {
        return entries.stream().mapToInt(Entry::typedDeadCodePreviewUnreachableNodeCount).sum();
    }

    int totalTypedDeadCodePreviewBlockedCount() {
        return entries.stream().mapToInt(Entry::typedDeadCodePreviewBlockedCount).sum();
    }

    int totalPreviewReadinessFamilyCount() {
        return (int) previewReadinessFamilies().stream()
                .filter(family -> !"not-recorded".equals(family.status()))
                .count();
    }

    int totalPreviewReadinessCandidateFamilyCount() {
        return (int) previewReadinessFamilies().stream()
                .filter(family -> family.candidateCount() > 0)
                .count();
    }

    int totalPreviewReadinessBlockedFamilyCount() {
        return (int) previewReadinessFamilies().stream()
                .filter(family -> "blocked-by-proof".equals(family.status()))
                .count();
    }

    String previewReadinessStatus() {
        List<PreviewFamilyReadiness> families = previewReadinessFamilies();
        if (families.stream().allMatch(family -> "not-recorded".equals(family.status()))) {
            return "not-recorded";
        }
        if (families.stream().anyMatch(family -> "blocked-by-proof".equals(family.status()))) {
            return "blocked-by-proof";
        }
        if (families.stream().anyMatch(family -> "ready-for-runtime-equivalence-review".equals(family.status()))) {
            return "ready-for-runtime-equivalence-review";
        }
        if (families.stream().anyMatch(family -> "candidates-recorded".equals(family.status()))) {
            return "candidates-recorded";
        }
        return "no-candidates";
    }

    String previewReadinessFamilySummary() {
        StringBuilder summary = new StringBuilder();
        for (PreviewFamilyReadiness family : previewReadinessFamilies()) {
            if (!summary.isEmpty()) {
                summary.append(", ");
            }
            summary.append(family.family()).append('=').append(family.status());
        }
        return summary.toString();
    }

    String runtimeEquivalenceReviewStatus() {
        return runtimeEquivalenceReviewEligible() ? "review-ready" : "blocked";
    }

    boolean runtimeEquivalenceReviewEligible() {
        return "ready-for-runtime-equivalence-review".equals(previewReadinessStatus());
    }

    boolean runtimeEquivalenceReviewRequired() {
        return totalPreviewReadinessCandidateFamilyCount() > 0;
    }

    String runtimeEquivalenceReviewFirstBlocker() {
        return switch (previewReadinessStatus()) {
            case "ready-for-runtime-equivalence-review" -> "none";
            case "not-recorded" -> "preview-readiness-not-recorded";
            case "no-candidates" -> "preview-readiness-no-candidates";
            case "blocked-by-proof" -> "preview-readiness-blocked-by-proof";
            case "candidates-recorded" -> "preview-readiness-candidates-not-proof-clean";
            default -> "preview-readiness-unknown";
        };
    }

    int totalReviewPackageRequiredCount() {
        return (int) entries.stream().filter(Entry::reviewPackageRequired).count();
    }

    int totalReviewPackageCompleteCount() {
        return (int) entries.stream().filter(Entry::reviewPackageComplete).count();
    }

    int totalReviewPackageProposalPassCount() {
        return entries.stream().mapToInt(Entry::reviewPackageProposalPassCount).sum();
    }

    int totalReviewPackagePendingApprovalCount() {
        return entries.stream().mapToInt(Entry::reviewPackagePendingApprovalCount).sum();
    }

    String reviewPackageStatus() {
        if (entries.isEmpty() || entries.stream().allMatch(entry -> "missing".equals(entry.status()))) {
            return "not-recorded";
        }
        if (totalReviewPackageRequiredCount() <= 0) {
            return "not-required";
        }
        return totalReviewPackageCompleteCount() == totalReviewPackageRequiredCount()
                ? "complete"
                : "pending-manual-review";
    }

    String reviewPackageFirstBlocker() {
        for (Entry entry : entries) {
            if (entry.reviewPackageRequired() && !entry.reviewPackageComplete()) {
                return entry.reviewPackageFirstBlocker();
            }
        }
        return "none";
    }

    private List<PreviewFamilyReadiness> previewReadinessFamilies() {
        return List.of(
                new PreviewFamilyReadiness(
                        "constant-folding",
                        previewFamilyStatus(
                                totalConstantFoldingPreviewPassCount(),
                                totalConstantFoldingPreviewCandidateCount(),
                                totalConstantFoldingPreviewSkippedCount() + totalConstantFoldingPreviewProofBlockerCount()
                        ),
                        totalConstantFoldingPreviewCandidateCount(),
                        totalConstantFoldingPreviewSkippedCount() + totalConstantFoldingPreviewProofBlockerCount()
                ),
                new PreviewFamilyReadiness(
                        "safe-local-cse",
                        previewFamilyStatus(
                                totalSafeLocalCsePreviewPassCount(),
                                totalSafeLocalCsePreviewDuplicateExpressionCount(),
                                totalSafeLocalCsePreviewBlockedCount() + totalSafeLocalCsePreviewProofBlockerCount()
                        ),
                        totalSafeLocalCsePreviewDuplicateExpressionCount(),
                        totalSafeLocalCsePreviewBlockedCount() + totalSafeLocalCsePreviewProofBlockerCount()
                ),
                new PreviewFamilyReadiness(
                        "typed-dead-code",
                        previewFamilyStatus(
                                totalTypedDeadCodePreviewPassCount(),
                                totalTypedDeadCodePreviewUnreachableNodeCount(),
                                totalTypedDeadCodePreviewBlockedCount() + totalTypedDeadCodePreviewProofBlockerCount()
                        ),
                        totalTypedDeadCodePreviewUnreachableNodeCount(),
                        totalTypedDeadCodePreviewBlockedCount() + totalTypedDeadCodePreviewProofBlockerCount()
                )
        );
    }

    private int totalConstantFoldingPreviewProofBlockerCount() {
        return entries.stream().mapToInt(Entry::constantFoldingPreviewProofBlockerCount).sum();
    }

    private int totalSafeLocalCsePreviewProofBlockerCount() {
        return entries.stream().mapToInt(Entry::safeLocalCsePreviewProofBlockerCount).sum();
    }

    private int totalTypedDeadCodePreviewProofBlockerCount() {
        return entries.stream().mapToInt(Entry::typedDeadCodePreviewProofBlockerCount).sum();
    }

    private static String previewFamilyStatus(int passCount, int candidateCount, int blockerCount) {
        if (passCount <= 0) {
            return "not-recorded";
        }
        if (candidateCount <= 0 && blockerCount <= 0) {
            return "no-candidates";
        }
        if (blockerCount > 0) {
            return "blocked-by-proof";
        }
        if (candidateCount > 0) {
            return "ready-for-runtime-equivalence-review";
        }
        return "candidates-recorded";
    }

    String providerSummary() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (Entry entry : entries) {
            for (Map.Entry<String, Integer> provider : entry.providerCounts().entrySet()) {
                counts.merge(provider.getKey(), provider.getValue(), Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return "none";
        }
        StringBuilder summary = new StringBuilder();
        counts.forEach((provider, count) -> {
            if (!summary.isEmpty()) {
                summary.append(", ");
            }
            summary.append(provider).append('=').append(count);
        });
        return summary.toString();
    }

    String toMarkdown() {
        StringBuilder markdown = new StringBuilder();
        markdown.append("## Runtime IR Optimizer Evidence\n\n");
        markdown.append("- Status: `").append(inline(status)).append("`\n");
        if (!diagnostic.isBlank()) {
            markdown.append("- Diagnostic: `").append(inline(diagnostic)).append("`\n\n");
            return markdown.toString();
        }
        markdown.append("- Kernel count: `").append(entries.size()).append("`\n");
        markdown.append("- Recorded kernels: `").append(count("recorded")).append("`\n");
        markdown.append("- Missing artifacts: `").append(count("missing")).append("`\n");
        markdown.append("- Proposal pass count: `").append(totalPassCount()).append("`\n");
        markdown.append("- Proposal-only count: `").append(totalProposalOnlyCount()).append("`\n");
        markdown.append("- Selected optimized count: `").append(totalSelectedOptimizedCount()).append("`\n");
        markdown.append("- Rolled back count: `").append(totalRolledBackCount()).append("`\n");
        markdown.append("- Approval templates pending: `").append(totalApprovalTemplatePendingCount()).append("`\n");
        markdown.append("- Approval templates not applicable: `").append(totalApprovalTemplateNotApplicableCount()).append("`\n");
        markdown.append("- Constant folding preview passes: `").append(totalConstantFoldingPreviewPassCount()).append("`\n");
        markdown.append("- Constant folding preview candidates: `").append(totalConstantFoldingPreviewCandidateCount()).append("`\n");
        markdown.append("- Constant folding preview skipped blockers: `").append(totalConstantFoldingPreviewSkippedCount()).append("`\n");
        markdown.append("- Safe local CSE preview passes: `").append(totalSafeLocalCsePreviewPassCount()).append("`\n");
        markdown.append("- Safe local CSE preview candidate expressions: `")
                .append(totalSafeLocalCsePreviewCandidateExpressionCount()).append("`\n");
        markdown.append("- Safe local CSE preview duplicate expressions: `")
                .append(totalSafeLocalCsePreviewDuplicateExpressionCount()).append("`\n");
        markdown.append("- Safe local CSE preview blockers: `")
                .append(totalSafeLocalCsePreviewBlockedCount()).append("`\n");
        markdown.append("- Typed dead-code preview passes: `").append(totalTypedDeadCodePreviewPassCount()).append("`\n");
        markdown.append("- Typed dead-code preview unreachable nodes: `")
                .append(totalTypedDeadCodePreviewUnreachableNodeCount()).append("`\n");
        markdown.append("- Typed dead-code preview blockers: `")
                .append(totalTypedDeadCodePreviewBlockedCount()).append("`\n");
        markdown.append("- Preview readiness status: `").append(previewReadinessStatus()).append("`\n");
        markdown.append("- Preview readiness families: `").append(inline(previewReadinessFamilySummary())).append("`\n");
        markdown.append("- Preview readiness recorded families: `").append(totalPreviewReadinessFamilyCount()).append("`\n");
        markdown.append("- Preview readiness candidate families: `")
                .append(totalPreviewReadinessCandidateFamilyCount()).append("`\n");
        markdown.append("- Preview readiness blocked families: `")
                .append(totalPreviewReadinessBlockedFamilyCount()).append("`\n");
        markdown.append("- Runtime-equivalence review status: `").append(runtimeEquivalenceReviewStatus()).append("`\n");
        markdown.append("- Runtime-equivalence review eligible: `").append(runtimeEquivalenceReviewEligible()).append("`\n");
        markdown.append("- Runtime-equivalence review required: `").append(runtimeEquivalenceReviewRequired()).append("`\n");
        markdown.append("- Runtime-equivalence review first blocker: `")
                .append(inline(runtimeEquivalenceReviewFirstBlocker())).append("`\n");
        markdown.append("- Runtime-equivalence review production mutation: `disabled`\n");
        markdown.append("- Runtime-equivalence review selected IR replacement: `disabled`\n");
        markdown.append("- Review package status: `").append(reviewPackageStatus()).append("`\n");
        markdown.append("- Review package required kernels: `").append(totalReviewPackageRequiredCount()).append("`\n");
        markdown.append("- Review package complete kernels: `").append(totalReviewPackageCompleteCount()).append("`\n");
        markdown.append("- Review package proposal passes: `").append(totalReviewPackageProposalPassCount()).append("`\n");
        markdown.append("- Review package pending approvals: `")
                .append(totalReviewPackagePendingApprovalCount()).append("`\n");
        markdown.append("- Review package first blocker: `")
                .append(inline(reviewPackageFirstBlocker())).append("`\n");
        markdown.append("- Review package manual review only: `true`\n");
        markdown.append("- Review package production mutation: `disabled`\n");
        markdown.append("- Review package selected IR replacement: `disabled`\n");
        markdown.append("- Providers: `").append(inline(providerSummary())).append("`\n\n");
        if (entries.isEmpty()) {
            return markdown.toString();
        }
        markdown.append("| Kernel resource | Status | Passes | Proposal-only | Selected | Rolled back | Approval pending | Approval N/A | CF candidates | CF skipped | CSE candidates | CSE duplicates | CSE blocked | TDC unreachable | TDC blocked | Review package | Review blocker | Providers |\n");
        markdown.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | --- |\n");
        for (Entry entry : entries) {
            markdown.append("| `").append(table(entry.kernelResource())).append("` | `")
                    .append(table(entry.status())).append("` | `")
                    .append(entry.passCount()).append("` | `")
                    .append(entry.proposalOnlyCount()).append("` | `")
                    .append(entry.selectedOptimizedCount()).append("` | `")
                    .append(entry.rolledBackCount()).append("` | `")
                    .append(entry.approvalTemplatePendingCount()).append("` | `")
                    .append(entry.approvalTemplateNotApplicableCount()).append("` | `")
                    .append(entry.constantFoldingPreviewCandidateCount()).append("` | `")
                    .append(entry.constantFoldingPreviewSkippedCount()).append("` | `")
                    .append(entry.safeLocalCsePreviewCandidateExpressionCount()).append("` | `")
                    .append(entry.safeLocalCsePreviewDuplicateExpressionCount()).append("` | `")
                    .append(entry.safeLocalCsePreviewBlockedCount()).append("` | `")
                    .append(entry.typedDeadCodePreviewUnreachableNodeCount()).append("` | `")
                    .append(entry.typedDeadCodePreviewBlockedCount()).append("` | `")
                    .append(table(entry.reviewPackageStatus())).append("` | `")
                    .append(table(entry.reviewPackageFirstBlocker())).append("` | `")
                    .append(table(formatProviderCounts(entry.providerCounts()))).append("` |\n");
        }
        markdown.append('\n');
        return markdown.toString();
    }

    private static Map<String, Properties> loadEvidenceArtifacts(Path artifactRoot) throws IOException {
        LinkedHashMap<String, Properties> artifacts = new LinkedHashMap<>();
        if (artifactRoot == null || !Files.isDirectory(artifactRoot)) {
            return artifacts;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(artifactRoot)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(candidate -> ARTIFACT_FILE_NAME.equals(candidate.getFileName().toString()))
                    .toList()) {
                Properties properties = loadProperties(path);
                String resource = firstNonBlank(
                        properties.getProperty("backendResource", ""),
                        properties.getProperty("kernelResource", "")
                );
                if (resource.isBlank()) {
                    resource = inferResourceFromSiblingArtifacts(path.getParent());
                }
                if (!resource.isBlank() && !"unknown".equals(resource)) {
                    artifacts.put(resource, properties);
                }
            }
        }
        return artifacts;
    }

    private static String inferResourceFromSiblingArtifacts(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return "";
        }
        for (String sibling : List.of(
                GpuRuntimeCompileArtifactDumper.RUNTIME_EXTENSION_PARTICIPATION_ARTIFACT,
                "backend-module.properties",
                "backend-diagnostics.properties"
        )) {
            Path path = directory.resolve(sibling);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            Properties properties = loadProperties(path);
            String resource = firstNonBlank(
                    properties.getProperty("backendResource", ""),
                    properties.getProperty("resource", "")
            );
            if (!resource.isBlank() && !"unknown".equals(resource)) {
                return resource;
            }
        }
        return "";
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String inline(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('`', '\'');
    }

    private static String table(String value) {
        return inline(value).replace("|", "\\|");
    }

    private static String formatProviderCounts(Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return "none";
        }
        StringBuilder summary = new StringBuilder();
        counts.forEach((provider, count) -> {
            if (!summary.isEmpty()) {
                summary.append(", ");
            }
            summary.append(provider).append('=').append(count);
        });
        return summary.toString();
    }

    private record PreviewFamilyReadiness(String family, String status, int candidateCount, int blockerCount) {
    }

    record Entry(
            String kernelResource,
            String status,
            int passCount,
            int proposalOnlyCount,
            int selectedOptimizedCount,
            int rolledBackCount,
            int approvalTemplatePendingCount,
            int approvalTemplateNotApplicableCount,
            int constantFoldingPreviewPassCount,
            int constantFoldingPreviewCandidateCount,
            int constantFoldingPreviewSkippedNonPlainLiteralCount,
            int constantFoldingPreviewSkippedDivideByZeroCount,
            int constantFoldingPreviewSkippedNonEvenDivisionCount,
            int constantFoldingPreviewSkippedUnsupportedOperatorCount,
            int constantFoldingPreviewSkippedNonLiteralOperandCount,
            boolean constantFoldingPreviewRuntimeEquivalenceRequiredBeforeRewrite,
            boolean constantFoldingPreviewApprovalRequiredBeforeRewrite,
            boolean constantFoldingPreviewIntegerOverflowProven,
            boolean constantFoldingPreviewFloatingPointRoundingProven,
            int safeLocalCsePreviewPassCount,
            int safeLocalCsePreviewExpressionCount,
            int safeLocalCsePreviewCandidateExpressionCount,
            int safeLocalCsePreviewDuplicateExpressionCount,
            int safeLocalCsePreviewEquivalenceClassCount,
            int safeLocalCsePreviewBlockedUnsupportedOperatorCount,
            int safeLocalCsePreviewBlockedImpureOperandCount,
            int safeLocalCsePreviewBlockedControlFlowBoundaryCount,
            boolean safeLocalCsePreviewRuntimeEquivalenceRequiredBeforeRewrite,
            boolean safeLocalCsePreviewApprovalRequiredBeforeRewrite,
            boolean safeLocalCsePreviewDominanceProven,
            boolean safeLocalCsePreviewSideEffectFreedomProven,
            int typedDeadCodePreviewPassCount,
            int typedDeadCodePreviewNodeCount,
            int typedDeadCodePreviewReachableNodeCount,
            int typedDeadCodePreviewUnreachableNodeCount,
            int typedDeadCodePreviewBlockedMissingRootCount,
            int typedDeadCodePreviewBlockedMissingChildReferenceCount,
            int typedDeadCodePreviewBlockedSideEffectingUnreachableNodeCount,
            boolean typedDeadCodePreviewRuntimeEquivalenceRequiredBeforeRewrite,
            boolean typedDeadCodePreviewApprovalRequiredBeforeRewrite,
            boolean typedDeadCodePreviewSideEffectFreedomProven,
            String reviewPackageStatus,
            boolean reviewPackageRequired,
            boolean reviewPackageComplete,
            String reviewPackageFirstBlocker,
            int reviewPackageProposalPassCount,
            int reviewPackagePendingApprovalCount,
            String reviewPackageRuntimeEquivalenceStatus,
            boolean reviewPackageManualReviewOnly,
            Map<String, Integer> providerCounts
    ) {

        Entry {
            kernelResource = normalize(kernelResource, "unknown");
            status = normalize(status, "unknown");
            passCount = Math.max(0, passCount);
            proposalOnlyCount = Math.max(0, proposalOnlyCount);
            selectedOptimizedCount = Math.max(0, selectedOptimizedCount);
            rolledBackCount = Math.max(0, rolledBackCount);
            approvalTemplatePendingCount = Math.max(0, approvalTemplatePendingCount);
            approvalTemplateNotApplicableCount = Math.max(0, approvalTemplateNotApplicableCount);
            constantFoldingPreviewPassCount = Math.max(0, constantFoldingPreviewPassCount);
            constantFoldingPreviewCandidateCount = Math.max(0, constantFoldingPreviewCandidateCount);
            constantFoldingPreviewSkippedNonPlainLiteralCount = Math.max(0, constantFoldingPreviewSkippedNonPlainLiteralCount);
            constantFoldingPreviewSkippedDivideByZeroCount = Math.max(0, constantFoldingPreviewSkippedDivideByZeroCount);
            constantFoldingPreviewSkippedNonEvenDivisionCount = Math.max(0, constantFoldingPreviewSkippedNonEvenDivisionCount);
            constantFoldingPreviewSkippedUnsupportedOperatorCount = Math.max(0, constantFoldingPreviewSkippedUnsupportedOperatorCount);
            constantFoldingPreviewSkippedNonLiteralOperandCount = Math.max(0, constantFoldingPreviewSkippedNonLiteralOperandCount);
            safeLocalCsePreviewPassCount = Math.max(0, safeLocalCsePreviewPassCount);
            safeLocalCsePreviewExpressionCount = Math.max(0, safeLocalCsePreviewExpressionCount);
            safeLocalCsePreviewCandidateExpressionCount = Math.max(0, safeLocalCsePreviewCandidateExpressionCount);
            safeLocalCsePreviewDuplicateExpressionCount = Math.max(0, safeLocalCsePreviewDuplicateExpressionCount);
            safeLocalCsePreviewEquivalenceClassCount = Math.max(0, safeLocalCsePreviewEquivalenceClassCount);
            safeLocalCsePreviewBlockedUnsupportedOperatorCount = Math.max(0, safeLocalCsePreviewBlockedUnsupportedOperatorCount);
            safeLocalCsePreviewBlockedImpureOperandCount = Math.max(0, safeLocalCsePreviewBlockedImpureOperandCount);
            safeLocalCsePreviewBlockedControlFlowBoundaryCount = Math.max(0, safeLocalCsePreviewBlockedControlFlowBoundaryCount);
            typedDeadCodePreviewPassCount = Math.max(0, typedDeadCodePreviewPassCount);
            typedDeadCodePreviewNodeCount = Math.max(0, typedDeadCodePreviewNodeCount);
            typedDeadCodePreviewReachableNodeCount = Math.max(0, typedDeadCodePreviewReachableNodeCount);
            typedDeadCodePreviewUnreachableNodeCount = Math.max(0, typedDeadCodePreviewUnreachableNodeCount);
            typedDeadCodePreviewBlockedMissingRootCount = Math.max(0, typedDeadCodePreviewBlockedMissingRootCount);
            typedDeadCodePreviewBlockedMissingChildReferenceCount = Math.max(0, typedDeadCodePreviewBlockedMissingChildReferenceCount);
            typedDeadCodePreviewBlockedSideEffectingUnreachableNodeCount = Math.max(0, typedDeadCodePreviewBlockedSideEffectingUnreachableNodeCount);
            reviewPackageStatus = normalize(reviewPackageStatus, "not-recorded");
            reviewPackageFirstBlocker = normalize(reviewPackageFirstBlocker, "none");
            reviewPackageProposalPassCount = Math.max(0, reviewPackageProposalPassCount);
            reviewPackagePendingApprovalCount = Math.max(0, reviewPackagePendingApprovalCount);
            reviewPackageRuntimeEquivalenceStatus = normalize(reviewPackageRuntimeEquivalenceStatus, "unknown");
            providerCounts = normalizeProviderCounts(providerCounts);
        }

        int constantFoldingPreviewSkippedCount() {
            return constantFoldingPreviewSkippedNonPlainLiteralCount
                    + constantFoldingPreviewSkippedDivideByZeroCount
                    + constantFoldingPreviewSkippedNonEvenDivisionCount
                    + constantFoldingPreviewSkippedUnsupportedOperatorCount
                    + constantFoldingPreviewSkippedNonLiteralOperandCount;
        }

        int safeLocalCsePreviewBlockedCount() {
            return safeLocalCsePreviewBlockedUnsupportedOperatorCount
                    + safeLocalCsePreviewBlockedImpureOperandCount
                    + safeLocalCsePreviewBlockedControlFlowBoundaryCount;
        }

        int typedDeadCodePreviewBlockedCount() {
            return typedDeadCodePreviewBlockedMissingRootCount
                    + typedDeadCodePreviewBlockedMissingChildReferenceCount
                    + typedDeadCodePreviewBlockedSideEffectingUnreachableNodeCount;
        }

        int constantFoldingPreviewProofBlockerCount() {
            if (constantFoldingPreviewCandidateCount <= 0) {
                return 0;
            }
            int blockers = 0;
            if (!constantFoldingPreviewIntegerOverflowProven) {
                blockers++;
            }
            if (!constantFoldingPreviewFloatingPointRoundingProven) {
                blockers++;
            }
            return blockers;
        }

        int safeLocalCsePreviewProofBlockerCount() {
            if (safeLocalCsePreviewDuplicateExpressionCount <= 0) {
                return 0;
            }
            int blockers = 0;
            if (!safeLocalCsePreviewDominanceProven) {
                blockers++;
            }
            if (!safeLocalCsePreviewSideEffectFreedomProven) {
                blockers++;
            }
            return blockers;
        }

        int typedDeadCodePreviewProofBlockerCount() {
            if (typedDeadCodePreviewUnreachableNodeCount <= 0) {
                return 0;
            }
            return typedDeadCodePreviewSideEffectFreedomProven ? 0 : 1;
        }

        static Entry from(String kernelResource, Properties properties) {
            if (properties == null || properties.isEmpty()) {
                return new Entry(kernelResource, "missing", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        false, false, false, false, 0, 0, 0, 0, 0, 0, 0, 0, false, false, false, false,
                        0, 0, 0, 0, 0, 0, 0, false, false, false,
                        "not-recorded", false, false, "review-package-not-recorded", 0, 0, "unknown", false,
                        Map.of());
            }
            return new Entry(
                    kernelResource,
                    properties.getProperty("status", "unknown"),
                    parseInt(properties.getProperty("pass.count"), 0),
                    parseInt(properties.getProperty("proposalOnly.count"), 0),
                    parseInt(properties.getProperty("selectedOptimized.count"), 0),
                    parseInt(properties.getProperty("rolledBack.count"), 0),
                    parseInt(properties.getProperty("approvalTemplate.pending.count"), 0),
                    parseInt(properties.getProperty("approvalTemplate.notApplicable.count"), 0),
                    parseInt(properties.getProperty("constantFoldingPreview.pass.count"), 0),
                    parseInt(properties.getProperty("constantFoldingPreview.candidate.count"), 0),
                    parseInt(properties.getProperty("constantFoldingPreview.skipped.nonPlainLiteral.count"), 0),
                    parseInt(properties.getProperty("constantFoldingPreview.skipped.divideByZero.count"), 0),
                    parseInt(properties.getProperty("constantFoldingPreview.skipped.nonEvenDivision.count"), 0),
                    parseInt(properties.getProperty("constantFoldingPreview.skipped.unsupportedOperator.count"), 0),
                    parseInt(properties.getProperty("constantFoldingPreview.skipped.nonLiteralOperand.count"), 0),
                    parseBoolean(properties.getProperty("constantFoldingPreview.runtimeEquivalenceRequiredBeforeRewrite")),
                    parseBoolean(properties.getProperty("constantFoldingPreview.approvalRequiredBeforeRewrite")),
                    parseBoolean(properties.getProperty("constantFoldingPreview.integerOverflowProven")),
                    parseBoolean(properties.getProperty("constantFoldingPreview.floatingPointRoundingProven")),
                    parseInt(properties.getProperty("safeLocalCsePreview.pass.count"), 0),
                    parseInt(properties.getProperty("safeLocalCsePreview.expression.count"), 0),
                    parseInt(properties.getProperty("safeLocalCsePreview.candidateExpression.count"), 0),
                    parseInt(properties.getProperty("safeLocalCsePreview.duplicateExpression.count"), 0),
                    parseInt(properties.getProperty("safeLocalCsePreview.equivalenceClass.count"), 0),
                    parseInt(properties.getProperty("safeLocalCsePreview.blocked.unsupportedOperator.count"), 0),
                    parseInt(properties.getProperty("safeLocalCsePreview.blocked.impureOperand.count"), 0),
                    parseInt(properties.getProperty("safeLocalCsePreview.blocked.controlFlowBoundary.count"), 0),
                    parseBoolean(properties.getProperty("safeLocalCsePreview.runtimeEquivalenceRequiredBeforeRewrite")),
                    parseBoolean(properties.getProperty("safeLocalCsePreview.approvalRequiredBeforeRewrite")),
                    parseBoolean(properties.getProperty("safeLocalCsePreview.dominanceProven")),
                    parseBoolean(properties.getProperty("safeLocalCsePreview.sideEffectFreedomProven")),
                    parseInt(properties.getProperty("typedDeadCodePreview.pass.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodePreview.node.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodePreview.reachableNode.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodePreview.unreachableNode.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodePreview.blocked.missingRoot.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodePreview.blocked.missingChildReference.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodePreview.blocked.sideEffectingUnreachableNode.count"), 0),
                    parseBoolean(properties.getProperty("typedDeadCodePreview.runtimeEquivalenceRequiredBeforeRewrite")),
                    parseBoolean(properties.getProperty("typedDeadCodePreview.approvalRequiredBeforeRewrite")),
                    parseBoolean(properties.getProperty("typedDeadCodePreview.sideEffectFreedomProven")),
                    properties.getProperty("reviewPackage.status", "not-recorded"),
                    parseBoolean(properties.getProperty("reviewPackage.required")),
                    parseBoolean(properties.getProperty("reviewPackage.complete")),
                    properties.getProperty("reviewPackage.firstBlocker", "none"),
                    parseInt(properties.getProperty("reviewPackage.proposalPass.count"), 0),
                    parseInt(properties.getProperty("reviewPackage.pendingApproval.count"), 0),
                    properties.getProperty("reviewPackage.runtimeEquivalence.status", "unknown"),
                    parseBoolean(properties.getProperty("reviewPackage.manualReviewOnly")),
                    parseProviderCounts(properties)
            );
        }

        private static boolean parseBoolean(String value) {
            return "true".equalsIgnoreCase(value)
                    || "yes".equalsIgnoreCase(value)
                    || "enabled".equalsIgnoreCase(value)
                    || "required".equalsIgnoreCase(value);
        }

        private static String normalize(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }

        private static Map<String, Integer> parseProviderCounts(Properties properties) {
            LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
            int count = parseInt(properties.getProperty("pass.count"), 0);
            for (int index = 0; index < count; index++) {
                String provider = properties.getProperty("pass." + index + ".passVersion", "");
                if (!provider.isBlank()) {
                    counts.merge(provider, 1, Integer::sum);
                }
            }
            return counts;
        }

        private static Map<String, Integer> normalizeProviderCounts(Map<String, Integer> values) {
            if (values == null || values.isEmpty()) {
                return Map.of();
            }
            LinkedHashMap<String, Integer> normalized = new LinkedHashMap<>();
            values.forEach((provider, count) -> {
                if (provider != null && !provider.isBlank() && count != null && count > 0) {
                    normalized.put(provider, count);
                }
            });
            return java.util.Collections.unmodifiableMap(normalized);
        }
    }
}
