package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only preview of future literal arithmetic CSE rewrite operation shapes.
 *
 * <p>The preview is intentionally conservative: blocked preflight candidates still produce blocked
 * operation shapes for auditability, but no production fingerprint or IR mutation is enabled.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport(
        String methodName,
        String readiness,
        int candidateCount,
        int eligibleOperationCount,
        int blockedOperationCount,
        int uniqueCanonicalKeyCount,
        List<Operation> eligibleOperations,
        List<BlockedOperation> blockedOperations
) {
    public GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (readiness == null || readiness.isBlank()) {
            throw new IllegalArgumentException("readiness must not be blank");
        }
        if (candidateCount < 0) {
            throw new IllegalArgumentException("candidateCount must be non-negative");
        }
        if (eligibleOperationCount < 0) {
            throw new IllegalArgumentException("eligibleOperationCount must be non-negative");
        }
        if (blockedOperationCount < 0) {
            throw new IllegalArgumentException("blockedOperationCount must be non-negative");
        }
        if (uniqueCanonicalKeyCount < 0) {
            throw new IllegalArgumentException("uniqueCanonicalKeyCount must be non-negative");
        }
        eligibleOperations = List.copyOf(Objects.requireNonNull(eligibleOperations, "eligibleOperations"));
        blockedOperations = List.copyOf(Objects.requireNonNull(blockedOperations, "blockedOperations"));
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport from(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport preflightReport
    ) {
        Objects.requireNonNull(preflightReport, "preflightReport");
        List<Operation> eligibleOperations = preflightReport.eligibleCandidates().stream()
                .map(Operation::from)
                .toList();
        List<BlockedOperation> blockedOperations = preflightReport.blockedCandidates().stream()
                .map(BlockedOperation::from)
                .toList();
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport(
                preflightReport.methodName(),
                readiness(preflightReport, eligibleOperations, blockedOperations),
                preflightReport.candidateCount(),
                eligibleOperations.size(),
                blockedOperations.size(),
                preflightReport.uniqueCanonicalKeyCount(),
                eligibleOperations,
                blockedOperations
        );
    }

    public boolean hasEligibleOperations() {
        return eligibleOperationCount > 0;
    }

    public boolean hasBlockedOperations() {
        return blockedOperationCount > 0;
    }

    public Optional<Operation> firstEligibleOperation() {
        return eligibleOperations.stream().findFirst();
    }

    public Optional<BlockedOperation> firstBlockedOperation() {
        return blockedOperations.stream().findFirst();
    }

    public Map<String, Long> operationShapeCounts() {
        return eligibleOperations.stream()
                .map(Operation::operationShape)
                .collect(Collectors.groupingBy(
                        shape -> shape,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, Long> blockedOperationShapeCounts() {
        return blockedOperations.stream()
                .map(BlockedOperation::operationShape)
                .collect(Collectors.groupingBy(
                        shape -> shape,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, Long> blockerCounts() {
        return blockedOperations.stream()
                .flatMap(operation -> operation.blockers().stream())
                .collect(Collectors.groupingBy(
                        blocker -> blocker,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Readiness", readiness);
        values.put(prefix + "Candidates", Integer.toString(candidateCount));
        values.put(prefix + "EligibleOperations", Integer.toString(eligibleOperationCount));
        values.put(prefix + "BlockedOperations", Integer.toString(blockedOperationCount));
        values.put(prefix + "UniqueCanonicalKeys", Integer.toString(uniqueCanonicalKeyCount));
        values.put(prefix + "HasEligibleOperations", Boolean.toString(hasEligibleOperations()));
        values.put(prefix + "HasBlockedOperations", Boolean.toString(hasBlockedOperations()));
        values.put(prefix + "OperationShapeCounts", mapSummary(operationShapeCounts()));
        values.put(prefix + "BlockedOperationShapeCounts", mapSummary(blockedOperationShapeCounts()));
        values.put(prefix + "BlockerCounts", mapSummary(blockerCounts()));
        firstEligibleOperation().ifPresent(operation -> {
            values.put(prefix + "FirstEligibleLocation", operation.location());
            values.put(prefix + "FirstEligibleCanonicalKey", operation.canonicalKey());
            values.put(prefix + "FirstEligibleShape", operation.operationShape());
            values.put(prefix + "FirstEligibleSummary", operation.summary());
        });
        firstBlockedOperation().ifPresent(operation -> {
            values.put(prefix + "FirstBlockedLocation", operation.location());
            values.put(prefix + "FirstBlockedCanonicalKey", operation.canonicalKey());
            values.put(prefix + "FirstBlockedShape", operation.operationShape());
            values.put(prefix + "FirstBlockedBlockers", listSummary(operation.blockers()));
            values.put(prefix + "FirstBlockedReason", operation.firstBlocker().orElse(""));
            values.put(prefix + "FirstBlockedSummary", operation.summary());
        });
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticLiteralRewriteOperationPreview");
    }

    public String summary() {
        return "CSE simple arithmetic literal rewrite operation preview method=" + methodName
                + " readiness=" + readiness
                + " candidates=" + candidateCount
                + " eligibleOperations=" + eligibleOperationCount
                + " blockedOperations=" + blockedOperationCount
                + " uniqueCanonicalKeys=" + uniqueCanonicalKeyCount
                + " operationShapes=" + mapSummary(operationShapeCounts())
                + " blockedOperationShapes=" + mapSummary(blockedOperationShapeCounts())
                + " blockers=" + mapSummary(blockerCounts());
    }

    private static String readiness(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport preflightReport,
            List<Operation> eligibleOperations,
            List<BlockedOperation> blockedOperations
    ) {
        if (!preflightReport.hasCandidates()) {
            return "none";
        }
        if (!eligibleOperations.isEmpty() && blockedOperations.isEmpty()) {
            return "readyPreview";
        }
        return "blockedPreview";
    }

    private static String operationShape(String operatorTypeKey) {
        return "literalArithmeticCse(" + operatorTypeKey + ")";
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String listSummary(List<String> values) {
        return values.stream().collect(Collectors.joining(",", "[", "]"));
    }

    public record Operation(
            String location,
            String canonicalKey,
            String operatorTypeKey,
            String operationShape,
            List<String> literalSources
    ) {
        public Operation {
            if (location == null || location.isBlank()) {
                throw new IllegalArgumentException("location must not be blank");
            }
            if (canonicalKey == null || canonicalKey.isBlank()) {
                throw new IllegalArgumentException("canonicalKey must not be blank");
            }
            if (operatorTypeKey == null || operatorTypeKey.isBlank()) {
                throw new IllegalArgumentException("operatorTypeKey must not be blank");
            }
            if (operationShape == null || operationShape.isBlank()) {
                throw new IllegalArgumentException("operationShape must not be blank");
            }
            literalSources = List.copyOf(Objects.requireNonNull(literalSources, "literalSources"));
        }

        private static Operation from(GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport.Candidate candidate) {
            return new Operation(
                    candidate.location(),
                    candidate.canonicalKey(),
                    candidate.operatorTypeKey(),
                    GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport.operationShape(candidate.operatorTypeKey()),
                    candidate.literalSources()
            );
        }

        public String summary() {
            return "location=" + location
                    + " canonicalKey=" + canonicalKey
                    + " operatorTypeKey=" + operatorTypeKey
                    + " operationShape=" + operationShape
                    + " literalSources=" + listSummary(literalSources);
        }
    }

    public record BlockedOperation(
            String location,
            String canonicalKey,
            String operatorTypeKey,
            String operationShape,
            List<String> blockers,
            String summary
    ) {
        public BlockedOperation {
            if (location == null || location.isBlank()) {
                throw new IllegalArgumentException("location must not be blank");
            }
            if (canonicalKey == null || canonicalKey.isBlank()) {
                throw new IllegalArgumentException("canonicalKey must not be blank");
            }
            if (operatorTypeKey == null || operatorTypeKey.isBlank()) {
                throw new IllegalArgumentException("operatorTypeKey must not be blank");
            }
            if (operationShape == null || operationShape.isBlank()) {
                throw new IllegalArgumentException("operationShape must not be blank");
            }
            blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
            if (summary == null || summary.isBlank()) {
                throw new IllegalArgumentException("summary must not be blank");
            }
        }

        private static BlockedOperation from(GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport.BlockedCandidate candidate) {
            String operationShape = GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport.operationShape(candidate.operatorTypeKey());
            return new BlockedOperation(
                    candidate.location(),
                    candidate.canonicalKey(),
                    candidate.operatorTypeKey(),
                    operationShape,
                    candidate.blockers(),
                    "location=" + candidate.location()
                            + " canonicalKey=" + candidate.canonicalKey()
                            + " operationShape=" + operationShape
                            + " blockers=" + listSummary(candidate.blockers())
            );
        }

        public Optional<String> firstBlocker() {
            return blockers.stream().findFirst();
        }
    }
}
