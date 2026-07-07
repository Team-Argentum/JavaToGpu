package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrStructInit;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitchCase;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationCandidateScannerTest {
    private final GpuIrAutoVectorizationCandidateScanner scanner = new GpuIrAutoVectorizationCandidateScanner();

    @Test
    void reportsFixedWidthLaneWiseArrayAssignmentsWithoutMutatingIr() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                                new GpuIrBinary("+",
                                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                                        new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                                )
                        ),
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("mask", new GpuIrVariableRef("i")),
                                new GpuIrBinary("^",
                                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                                        new GpuIrLiteral("7")
                                )
                        )
                ))
        ));

        GpuIrAutoVectorizationReport report = scanner.scan(method);

        assertTrue(report.hasCandidates());
        assertEquals("kernel", report.methodName());
        assertEquals(1, report.candidateCount());
        assertEquals(2, report.totalAssignmentCount());
        GpuIrAutoVectorizationCandidate candidate = report.candidates().get(0);
        assertEquals("stmt[0]", candidate.loopLocation());
        assertEquals("i", candidate.inductionVariable());
        assertEquals(0, candidate.startInclusive());
        assertEquals(4, candidate.endExclusive());
        assertEquals(4, candidate.laneCount());
        assertEquals(List.of("out", "mask"), candidate.targetArrays());
        assertEquals(List.of("left", "right", "out"), candidate.sourceArrays());
        assertEquals(List.of("target array `out` is also read in the loop body"), candidate.aliasWarnings());
        assertEquals(List.of(), candidate.repeatedTargetWarnings());
        assertEquals(List.of(), candidate.crossLaneReadWarnings());
        assertTrue(candidate.hasAliasWarnings());
        assertFalse(candidate.hasCrossLaneReadWarnings());
        assertTrue(candidate.hasWarnings());
        assertTrue(report.hasAliasWarnings());
        assertFalse(report.hasRepeatedTargetWarnings());
        assertFalse(report.hasCrossLaneReadWarnings());
        assertFalse(report.hasNonLaneReadWarnings());
        assertTrue(report.hasCandidateWarnings());
        assertEquals(List.of(candidate), report.candidatesWithAliasWarnings());
        assertEquals(List.of(candidate), report.candidatesWithWarnings());
        assertEquals(1, candidate.warningCount());
        assertEquals(1, report.totalWarningCount());
        assertEquals(java.util.Map.of("alias", 1L), report.warningFamilyCounts());
        assertEquals(List.of(GpuIrAutoVectorizationWarningDiagnostic.from(candidate)), report.previewWarningDiagnostics());
        assertEquals(1, report.previewWarningDiagnosticsByLocation().get("stmt[0]").size());
        assertEquals(0, candidate.priorityScore());
        assertFalse(candidate.isRewritePriorityCandidate());
        assertEquals(List.of(), report.rewritePriorityCandidates());
        assertEquals(2, candidate.assignmentCount());
        assertTrue(candidate.summary().contains("lanes=4"));
        assertTrue(candidate.summary().contains("sources=[left, right, out]"));
        assertTrue(candidate.summary().contains("priorityScore=0"));
        assertTrue(report.summary().contains("candidates=1"));
        assertTrue(report.summary().contains("warnings=1"));
    }

    @Test
    void reportsCrossLaneReadWarningsForOffsetArrayIndexes() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                                new GpuIrBinary("+",
                                        new GpuIrArrayAccess("left", new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                                        new GpuIrArrayAccess("right", new GpuIrBinary("-", new GpuIrVariableRef("i"), new GpuIrLiteral("1")))
                                )
                        )
                ))
        ));

        GpuIrAutoVectorizationReport report = scanner.scan(method);
        GpuIrAutoVectorizationCandidate candidate = report.candidates().get(0);

        assertTrue(report.hasCandidates());
        assertTrue(report.hasCrossLaneReadWarnings());
        assertTrue(report.hasCandidateWarnings());
        assertEquals(List.of(candidate), report.candidatesWithCrossLaneReadWarnings());
        assertEquals(List.of(), report.candidatesWithRepeatedTargetWarnings());
        assertEquals(List.of(), report.candidatesWithNonLaneReadWarnings());
        assertEquals(List.of(candidate), report.candidatesWithWarnings());
        assertEquals("stmt[0]", report.previewWarningDiagnostics().get(0).loopLocation());
        assertTrue(report.previewWarningDiagnostics().get(0).summary().contains("crossLaneReadWarnings"));
        assertEquals(List.of("left", "right"), candidate.sourceArrays());
        assertEquals(List.of(
                "array `left` is read at cross-lane offset +1 from `i`",
                "array `right` is read at cross-lane offset -1 from `i`"
        ), candidate.crossLaneReadWarnings());
        assertEquals(List.of(), candidate.repeatedTargetWarnings());
        assertEquals(List.of(), candidate.nonLaneReadWarnings());
        assertEquals(2, candidate.warningCount());
        assertEquals(2, report.totalWarningCount());
        assertEquals(java.util.Map.of("crossLaneRead", 1L), report.warningFamilyCounts());
        assertEquals(0, candidate.priorityScore());
        assertFalse(candidate.isRewritePriorityCandidate());
        assertTrue(candidate.hasCrossLaneReadWarnings());
        assertTrue(candidate.summary().contains("crossLaneReadWarnings"));
    }

    @Test
    void reportsNonLaneReadWarningsForUnknownArrayIndexes() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("j"))
                        )
                ))
        ));

        GpuIrAutoVectorizationReport report = scanner.scan(method);
        GpuIrAutoVectorizationCandidate candidate = report.candidates().get(0);

        assertTrue(report.hasCandidates());
        assertTrue(report.hasCandidateWarnings());
        assertTrue(report.hasNonLaneReadWarnings());
        assertEquals(List.of(candidate), report.candidatesWithNonLaneReadWarnings());
        assertEquals(List.of("left"), candidate.sourceArrays());
        assertEquals(List.of("array `left` is read with non-lane index `j` instead of `i`"), candidate.nonLaneReadWarnings());
        assertTrue(candidate.hasNonLaneReadWarnings());
        assertEquals(1, candidate.warningCount());
        assertEquals(1, report.totalWarningCount());
        assertEquals(java.util.Map.of("nonLaneRead", 1L), report.warningFamilyCounts());
        assertEquals(0, candidate.priorityScore());
        assertFalse(candidate.isRewritePriorityCandidate());
        assertEquals(List.of(candidate), report.candidatesWithWarnings());
        assertTrue(report.previewWarningDiagnostics().get(0).summary().contains("nonLaneReadWarnings"));
        assertTrue(candidate.summary().contains("nonLaneReadWarnings"));
    }

    @Test
    void reportsRepeatedTargetWarningsForMultipleWritesToSameLaneArray() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                        ),
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                        )
                ))
        ));

        GpuIrAutoVectorizationReport report = scanner.scan(method);
        GpuIrAutoVectorizationCandidate candidate = report.candidates().get(0);

        assertTrue(report.hasCandidates());
        assertTrue(report.hasCandidateWarnings());
        assertTrue(report.hasRepeatedTargetWarnings());
        assertEquals(List.of(candidate), report.candidatesWithRepeatedTargetWarnings());
        assertEquals(List.of("out"), candidate.targetArrays());
        assertEquals(List.of("target array `out` is written more than once in the loop body"), candidate.repeatedTargetWarnings());
        assertTrue(candidate.hasRepeatedTargetWarnings());
        assertEquals(1, candidate.warningCount());
        assertEquals(1, report.totalWarningCount());
        assertEquals(java.util.Map.of("repeatedTarget", 1L), report.warningFamilyCounts());
        assertEquals(0, candidate.priorityScore());
        assertFalse(candidate.isRewritePriorityCandidate());
        assertEquals(List.of(candidate), report.candidatesWithWarnings());
        assertTrue(report.previewWarningDiagnostics().get(0).summary().contains("repeatedTargetWarnings"));
        assertTrue(candidate.summary().contains("repeatedTargetWarnings"));
    }

    @Test
    void ranksWarningFreeCandidatesByDiagnosticPriorityScore() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("outA", new GpuIrVariableRef("i")),
                                new GpuIrBinary("+",
                                        new GpuIrArrayAccess("leftA", new GpuIrVariableRef("i")),
                                        new GpuIrArrayAccess("rightA", new GpuIrVariableRef("i"))
                                )
                        )
                )),
                fixedWidthLoop(8, List.of(
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("outB", new GpuIrVariableRef("i")),
                                new GpuIrBinary("+",
                                        new GpuIrArrayAccess("leftB", new GpuIrVariableRef("i")),
                                        new GpuIrArrayAccess("rightB", new GpuIrVariableRef("i"))
                                )
                        ),
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("maskB", new GpuIrVariableRef("i")),
                                new GpuIrBinary("^",
                                        new GpuIrArrayAccess("bitsB", new GpuIrVariableRef("i")),
                                        new GpuIrLiteral("3")
                                )
                        )
                ))
        ));

        GpuIrAutoVectorizationReport report = scanner.scan(method);
        List<GpuIrAutoVectorizationCandidate> ranked = report.rewritePriorityCandidates();

        assertEquals(2, ranked.size());
        assertEquals("stmt[1]", ranked.get(0).loopLocation());
        assertEquals(16, ranked.get(0).priorityScore());
        assertEquals("unknown", ranked.get(0).scalarElementType());
        assertEquals("unknownx8", ranked.get(0).vectorType());
        assertEquals("stmt[0]", ranked.get(1).loopLocation());
        assertEquals(4, ranked.get(1).priorityScore());
        assertTrue(ranked.stream().allMatch(GpuIrAutoVectorizationCandidate::isRewritePriorityCandidate));
        assertEquals(ranked, report.topCandidates());
        assertEquals(2, report.previewRewritePriorityCandidates().size());
        GpuIrAutoVectorizationPreview aggregatePreview = report.preview();
        assertTrue(aggregatePreview.hasRewriteCandidates());
        assertFalse(aggregatePreview.hasWarnings());
        assertFalse(aggregatePreview.hasRejections());
        assertTrue(aggregatePreview.hasBlockingDiagnostics());
        assertFalse(aggregatePreview.canApplyRewrite());
        assertTrue(aggregatePreview.hasPolicyBlockedRewrite());
        assertFalse(aggregatePreview.rewritePolicy().canRewrite());
        assertEquals(GpuIrAutoVectorizationRewriteReadiness.BLOCKED_BY_GUARD, aggregatePreview.rewritePolicy().readiness());
        assertEquals(2, aggregatePreview.rewriteCandidateCount());
        assertEquals(0, aggregatePreview.warningCount());
        assertEquals(0, aggregatePreview.rejectionCount());
        assertEquals(2, aggregatePreview.totalDiagnosticCount());
        assertEquals(java.util.Map.of(), aggregatePreview.warningFamilyCounts());
        assertEquals(java.util.Map.of("unknownx4", 1L, "unknownx8", 1L), aggregatePreview.vectorTypeCounts());
        assertTrue(aggregatePreview.firstBlockingDiagnosticSummary().orElseThrow().contains("unknown vector type"));
        assertEquals(
                GpuIrAutoVectorizationRewriteGuardFamily.UNKNOWN_VECTOR_TYPE,
                aggregatePreview.firstRewritePlanGuard().orElseThrow().family()
        );
        assertEquals(aggregatePreview.firstBlockingDiagnosticSummary().orElseThrow(), aggregatePreview.firstRewritePlanGuard().orElseThrow().summary());
        GpuIrAutoVectorizationRewritePlan rewritePlan = aggregatePreview.rewritePlan();
        assertTrue(rewritePlan.hasOperations());
        assertEquals(2, rewritePlan.candidateCount());
        assertTrue(rewritePlan.hasGuardDiagnostics());
        assertEquals(2, rewritePlan.rawInsertionPreviewCount());
        assertEquals(2, rewritePlan.rawReplacementPreviewCount());
        assertEquals(2, rewritePlan.rawInsertionOperationCount());
        assertEquals(2, rewritePlan.rawReplacementOperationCount());
        assertEquals(0, rewritePlan.insertionCount());
        assertEquals(0, rewritePlan.replacementCount());
        assertEquals(0, rewritePlan.operationCount());
        assertFalse(rewritePlan.rewritePolicy().canRewrite());
        assertEquals(GpuIrAutoVectorizationRewriteReadiness.BLOCKED_BY_GUARD, rewritePlan.rewritePolicy().readiness());
        assertEquals(2, rewritePlan.rewritePolicy().blockingGuards().size());
        assertEquals(GpuIrAutoVectorizationRewriteGuardFamily.UNKNOWN_VECTOR_TYPE, rewritePlan.rewritePolicy().firstBlockingGuard().orElseThrow().family());
        assertEquals(java.util.Map.of("unknownVectorType", 2L), rewritePlan.rewritePolicy().blockingGuardFamilyCounts());
        assertTrue(rewritePlan.rewritePolicy().summary().contains("blockingGuardFamilies={unknownVectorType=2}"));
        assertTrue(rewritePlan.guardDiagnostics().get(0).contains("unknown vector type"));
        assertEquals(rewritePlan.guardDiagnostics().get(0), rewritePlan.typedGuardDiagnostics().get(0).summary());
        assertEquals(GpuIrAutoVectorizationRewriteGuardFamily.UNKNOWN_VECTOR_TYPE, rewritePlan.typedGuardDiagnostics().get(0).family());
        assertEquals("stmt[1]", rewritePlan.typedGuardDiagnostics().get(0).location());
        assertEquals(java.util.Map.of("unknownVectorType", 2L), rewritePlan.guardFamilyCounts());
        assertEquals(2, aggregatePreview.unknownVectorProofSummary().diagnosticCount());
        assertEquals(java.util.Map.of("unknownVectorType", 2L), aggregatePreview.unknownVectorProofSummary().guardFamilyCounts());
        assertTrue(aggregatePreview.proofBundle().proofKinds().contains("unknownVector"));
        assertEquals("2", aggregatePreview.proofBundle()
                .artifactFields()
                .get("autoVectorizationProofBundleGuardFamily.unknownVectorType"));
        assertEquals("stmt[1]", rewritePlan.insertionOperations().get(0).loopLocation());
        assertEquals("unknownx8", rewritePlan.insertionOperations().get(0).vectorType());
        assertEquals(List.of("read leftB[i=0..7]", "read rightB[i=0..7]", "read bitsB[i=0..7]"), rewritePlan.insertionOperations().get(0).plannedVectorReads());
        assertEquals("stmt[1]", rewritePlan.replacementOperations().get(0).loopLocation());
        assertEquals("i", rewritePlan.replacementOperations().get(0).inductionVariable());
        assertEquals(List.of("write outB[i=0..7]", "write maskB[i=0..7]"), rewritePlan.replacementOperations().get(0).plannedVectorWrites());
        assertTrue(rewritePlan.insertionPreviews().get(0).contains("type=unknownx8"));
        assertTrue(rewritePlan.insertionPreviews().get(0).contains("read leftB[i=0..7]"));
        assertTrue(rewritePlan.replacementPreviews().get(0).contains("write outB[i=0..7]"));
        assertTrue(rewritePlan.summary().contains("operations=0"));
        assertTrue(rewritePlan.summary().contains("unknownVectorType=2"));
        assertTrue(rewritePlan.summary().contains("guardDiagnostics"));
        assertTrue(aggregatePreview.summary().contains("rewriteCandidates=2"));
        assertTrue(aggregatePreview.summary().contains("rewritePolicyCanRewrite=false"));
        assertTrue(aggregatePreview.summary().contains("rewritePolicyBlockingGuards=2"));
        assertTrue(aggregatePreview.summary().contains("canApplyRewrite=false"));
        assertTrue(aggregatePreview.summary().contains("proofBundleRewriteSafe=false"));
        assertTrue(aggregatePreview.summary().contains("proofBundleDiagnostics=4"));
        assertTrue(aggregatePreview.summary().contains("proofBundleUnsafeProofs=2"));
        assertTrue(aggregatePreview.summary().contains("proofBundleFirstUnsafeProof=rewritePlan@kernel"));
        assertEquals(List.of(
                "rewritePlan",
                "unknownVector",
                "controlFlowBoundary",
                "memoryLegality",
                "controlFlowBoundary",
                "memoryLegality"
        ), aggregatePreview.proofBundle().proofKinds());
        assertEquals(List.of(
                "rewritePlan",
                "unknownVector",
                "controlFlowBoundary",
                "memoryLegality"
        ), aggregatePreview.proofBundle().compactProofKinds());
        assertTrue(aggregatePreview.summary().contains("rewritePlanOperations=0"));
        assertTrue(aggregatePreview.summary().contains("vectorTypes="));
        assertTrue(aggregatePreview.summary().contains("unknownx8=1"));
        GpuIrAutoVectorizationRewriteCandidatePreview preview = report.previewRewritePriorityCandidates().get(0);
        assertEquals("stmt[1]", preview.loopLocation());
        assertEquals("i", preview.inductionVariable());
        assertEquals(0, preview.startInclusive());
        assertEquals(8, preview.endExclusive());
        assertEquals(8, preview.laneCount());
        assertEquals(2, preview.assignmentCount());
        assertEquals(16, preview.priorityScore());
        assertEquals("x8", preview.vectorWidth());
        assertEquals("unknown", preview.scalarElementType());
        assertEquals("unknownx8", preview.vectorType());
        assertEquals(List.of("write outB[i=0..7]", "write maskB[i=0..7]"), preview.plannedVectorWrites());
        assertEquals(List.of("read leftB[i=0..7]", "read rightB[i=0..7]", "read bitsB[i=0..7]"), preview.plannedVectorReads());
        assertEquals(List.of("outB", "maskB"), preview.targetArrays());
        assertTrue(preview.summary().contains("rewrite candidate"));
        assertTrue(preview.summary().contains("vectorWidth=x8"));
        assertTrue(preview.summary().contains("vectorType=unknownx8"));
        assertTrue(preview.summary().contains("laneRange=0..7"));
        assertTrue(report.summary().contains("rewritePreviews=2"));
    }

    @Test
    void compiledMethodPreviewUsesResolvedScalarElementType() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrBinary("+",
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                        )
                )))
        ));

        GpuIrAutoVectorizationReport report = scanner.scan(compiledMethod(method,
                parameter("left", "int[]"),
                parameter("right", "int[]"),
                parameter("out", "int[]")
        ));

        assertEquals(1, report.candidateCount());
        GpuIrAutoVectorizationCandidate candidate = report.candidates().get(0);
        assertEquals("int", candidate.scalarElementType());
        assertEquals("int4", candidate.vectorType());
        GpuIrAutoVectorizationRewriteCandidatePreview preview = report.previewRewritePriorityCandidates().get(0);
        assertEquals("int", preview.scalarElementType());
        assertEquals("int4", preview.vectorType());
        assertEquals(java.util.Map.of("int4", 1L), report.preview().vectorTypeCounts());
        assertTrue(preview.summary().contains("scalarElementType=int"));
        assertTrue(preview.summary().contains("vectorType=int4"));

        GpuIrAutoVectorizationRewritePolicy policy = report.preview().rewritePlan().rewritePolicy();
        assertTrue(policy.canRewrite());
        assertTrue(report.preview().canApplyRewrite());
        assertTrue(report.preview().proofBundle().rewriteSafe());
        assertEquals(List.of("rewritePlan", "memoryLegality"), report.preview().proofBundle().proofKinds());
        assertTrue(report.preview().summary().contains("proofBundleRewriteSafe=true"));
        assertTrue(report.preview().summary().contains("proofBundleDiagnostics=0"));
        assertTrue(report.preview().summary().contains("proofBundleUnsafeProofs=0"));
        assertFalse(report.preview().summary().contains("proofBundleFirstUnsafeProof="));
        assertFalse(report.preview().hasPolicyBlockedRewrite());
        assertEquals(GpuIrAutoVectorizationRewriteReadiness.READY, policy.readiness());
        assertEquals(1, policy.candidateCount());
        assertEquals(2, policy.plannedOperationCount());
        assertTrue(policy.firstBlockingGuard().isEmpty());
        assertTrue(report.preview().firstRewritePlanGuard().isEmpty());
        assertEquals(java.util.Map.of(), policy.blockingGuardFamilyCounts());
        assertTrue(policy.summary().contains("canRewrite=true"));
    }

    @Test
    void rewritePlanGuardsBackendSpecificVectorWidthsBeforeOperations() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(3, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));

        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method,
                parameter("left", "int[]"),
                parameter("out", "int[]")
        )).preview();

        assertTrue(preview.hasRewriteCandidates());
        assertEquals(1, preview.rewriteCandidateCount());
        assertEquals("int3", preview.rewriteCandidates().get(0).vectorType());
        assertTrue(preview.rewritePlan().hasGuardDiagnostics());
        assertEquals(0, preview.rewritePlan().operationCount());
        assertTrue(preview.rewritePlan().guardDiagnostics().get(0).contains("backend vector width x3"));
        assertEquals(
                GpuIrAutoVectorizationRewriteGuardFamily.BACKEND_VECTOR_WIDTH,
                GpuIrAutoVectorizationRewritePlan.guardFamilyType(preview.rewritePlan().guardDiagnostics().get(0))
        );
        assertEquals(
                java.util.Map.of(GpuIrAutoVectorizationRewriteGuardFamily.BACKEND_VECTOR_WIDTH, 1L),
                preview.rewritePlan().guardFamilyTypeCounts()
        );
        assertEquals(java.util.Map.of("backendVectorWidth", 1L), preview.rewritePlan().guardFamilyCounts());
        assertEquals(1, preview.backendProofSummary().diagnosticCount());
        assertEquals(java.util.Map.of("backendVectorWidth", 1L), preview.backendProofSummary().guardFamilyCounts());
        assertTrue(preview.proofBundle().proofKinds().contains("backend"));
        assertEquals("1", preview.proofBundle()
                .artifactFields()
                .get("autoVectorizationProofBundleGuardFamily.backendVectorWidth"));
        assertEquals(GpuIrAutoVectorizationRewriteReadiness.BLOCKED_BY_GUARD, preview.rewriteReadiness());
    }

    @Test
    void rewritePlanGuardsDoubleVectorsBeforeOperations() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));

        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method,
                parameter("left", "double[]"),
                parameter("out", "double[]")
        )).preview();

        assertTrue(preview.hasRewriteCandidates());
        assertEquals(1, preview.rewriteCandidateCount());
        assertEquals("double4", preview.rewriteCandidates().get(0).vectorType());
        assertTrue(preview.rewritePlan().hasGuardDiagnostics());
        assertEquals(0, preview.rewritePlan().operationCount());
        assertTrue(preview.rewritePlan().guardDiagnostics().get(0).contains("backend double vector type double4"));
        assertEquals(
                java.util.Map.of(GpuIrAutoVectorizationRewriteGuardFamily.BACKEND_DOUBLE_VECTOR, 1L),
                preview.rewritePlan().guardFamilyTypeCounts()
        );
        assertEquals(java.util.Map.of("backendDoubleVector", 1L), preview.rewritePlan().guardFamilyCounts());
        assertEquals(1, preview.backendProofSummary().diagnosticCount());
        assertEquals(java.util.Map.of("backendDoubleVector", 1L), preview.backendProofSummary().guardFamilyCounts());
        assertTrue(preview.proofBundle().proofKinds().contains("backend"));
        assertEquals(GpuIrAutoVectorizationRewriteReadiness.BLOCKED_BY_GUARD, preview.rewriteReadiness());
    }

    @Test
    void rewritePlanGuardsMemoryAddressSpacesBeforeOperations() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("input", new GpuIrVariableRef("i"))
                )))
        ));

        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method,
                parameter("input", "int[]", net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace.CONSTANT, false),
                parameter("out", "int[]", net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace.LOCAL, false)
        )).preview();

        assertTrue(preview.hasRewriteCandidates());
        assertEquals(1, preview.rewriteCandidateCount());
        assertTrue(preview.rewritePlan().hasGuardDiagnostics());
        assertEquals(0, preview.rewritePlan().operationCount());
        assertTrue(preview.rewritePlan().guardDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.contains("target array `out` uses local memory address space")));
        assertTrue(preview.rewritePlan().guardDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.contains("source array `input` uses constant memory address space")));
        assertEquals(
                java.util.Map.of(GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE, 2L),
                preview.rewritePlan().guardFamilyTypeCounts()
        );
        assertEquals(java.util.Map.of("memoryAddressSpace", 2L), preview.rewritePlan().guardFamilyCounts());
        assertEquals(GpuIrAutoVectorizationRewriteReadiness.BLOCKED_BY_GUARD, preview.rewriteReadiness());
    }

    @Test
    void rewritePlanGuardsReadOnlyGlobalParametersBeforeOperations() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("input", new GpuIrVariableRef("i"))
                )))
        ));

        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method,
                parameter("input", "int[]", net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace.GLOBAL, true),
                parameter("out", "int[]")
        )).preview();

        assertTrue(preview.hasRewriteCandidates());
        assertEquals(0, preview.rewritePlan().operationCount());
        assertTrue(preview.rewritePlan().guardDiagnostics().get(0)
                .contains("source array `input` uses constant memory address space"));
        assertEquals(java.util.Map.of("memoryAddressSpace", 1L), preview.rewritePlan().guardFamilyCounts());
    }

    @Test
    void rewritePlanGuardsMissingArrayParameterMetadataBeforeOperations() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("input", new GpuIrVariableRef("i"))
                )))
        ));

        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method,
                parameter("out", "int[]")
        )).preview();

        assertTrue(preview.hasRewriteCandidates());
        assertEquals(0, preview.rewritePlan().operationCount());
        assertTrue(preview.rewritePlan().guardDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.contains("source array `input` has no parameter metadata")));
        assertEquals(java.util.Map.of("memoryAddressSpace", 1L), preview.rewritePlan().guardFamilyCounts());
        assertFalse(preview.proofBundle().rewriteSafe());
        assertEquals(GpuIrAutoVectorizationRewriteReadiness.BLOCKED_BY_GUARD, preview.rewriteReadiness());
    }

    @Test
    void guardFamilyTypeClassifiesAllKnownRewriteGuards() {
        GpuIrAutoVectorizationRewriteGuardDiagnostic parsed = GpuIrAutoVectorizationRewriteGuardDiagnostic.fromLegacySummary(
                "guard stmt[0]: backend vector width x3 requires explicit ABI support before rewrite operations"
        );
        assertEquals("stmt[0]", parsed.location());
        assertEquals("guard stmt[0]: backend vector width x3 requires explicit ABI support before rewrite operations", parsed.summary());
        assertEquals(GpuIrAutoVectorizationRewriteGuardFamily.BACKEND_VECTOR_WIDTH, parsed.family());

        assertEquals(
                GpuIrAutoVectorizationRewriteGuardFamily.UNKNOWN_VECTOR_TYPE,
                GpuIrAutoVectorizationRewritePlan.guardFamilyType("guard stmt[0]: unknown vector type blocks rewrite operations")
        );
        assertEquals(
                GpuIrAutoVectorizationRewriteGuardFamily.BACKEND_VECTOR_WIDTH,
                GpuIrAutoVectorizationRewritePlan.guardFamilyType("guard stmt[0]: backend vector width x3 requires explicit ABI support before rewrite operations")
        );
        assertEquals(
                GpuIrAutoVectorizationRewriteGuardFamily.BACKEND_DOUBLE_VECTOR,
                GpuIrAutoVectorizationRewritePlan.guardFamilyType("guard stmt[0]: backend double vector type double4 requires explicit device capability support before rewrite operations")
        );
        assertEquals(
                GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE,
                GpuIrAutoVectorizationRewritePlan.guardFamilyType("guard stmt[0]: source array `input` uses constant memory address space before vector rewrite policy is proven")
        );
        assertEquals(
                GpuIrAutoVectorizationRewriteGuardFamily.TARGET_SOURCE_ALIAS,
                GpuIrAutoVectorizationRewritePlan.guardFamilyType("guard stmt[0]: target array `out` is also read by the candidate")
        );
        assertEquals(
                GpuIrAutoVectorizationRewriteGuardFamily.NEIGHBOR_SOURCE_WRITE,
                GpuIrAutoVectorizationRewritePlan.guardFamilyType("guard stmt[1]: previous statement stmt[0] writes source array `left`")
        );
        assertEquals(
                GpuIrAutoVectorizationRewriteGuardFamily.NEIGHBOR_TARGET_WRITE,
                GpuIrAutoVectorizationRewritePlan.guardFamilyType("guard stmt[0]: next statement stmt[1] writes target array `out`")
        );
        assertEquals(
                GpuIrAutoVectorizationRewriteGuardFamily.CONTROL_FLOW_BOUNDARY,
                GpuIrAutoVectorizationRewritePlan.guardFamilyType("guard stmt[1]: previous statement stmt[0] is a control-flow boundary before vector rewrite safety is proven")
        );
        assertEquals(
                GpuIrAutoVectorizationRewriteGuardFamily.EARLY_EXIT_BOUNDARY,
                GpuIrAutoVectorizationRewritePlan.guardFamilyType("guard stmt[1]: previous statement stmt[0] is an early-exit boundary before vector rewrite safety is proven")
        );
        assertEquals(
                GpuIrAutoVectorizationRewriteGuardFamily.OTHER,
                GpuIrAutoVectorizationRewritePlan.guardFamilyType("guard stmt[0]: future guard family")
        );
    }

    @Test
    void compiledMethodRejectsMixedScalarElementTypesBeforeRewritePreview() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrBinary("+",
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                        )
                )))
        ));

        GpuIrAutoVectorizationReport report = scanner.scan(compiledMethod(method,
                parameter("left", "int[]"),
                parameter("right", "float[]"),
                parameter("out", "int[]")
        ));

        assertFalse(report.hasCandidates());
        assertTrue(report.hasRejections());
        assertEquals(GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_ELEMENT_TYPE, report.rejections().get(0).reason());
        assertTrue(report.rejections().get(0).summary().contains("left=int"));
        assertTrue(report.rejections().get(0).summary().contains("right=float"));
        assertTrue(report.rejections().get(0).summary().contains("out=int"));
        assertFalse(report.preview().hasRewriteCandidates());
        assertTrue(report.preview().hasRejections());
    }

    @Test
    void compiledMethodRejectsFullyUnknownArrayElementTypesBeforeRewritePreview() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("missing", new GpuIrVariableRef("i"))
                )))
        ));

        GpuIrAutoVectorizationReport report = scanner.scan(compiledMethod(method));

        assertFalse(report.hasCandidates());
        assertTrue(report.hasRejections());
        assertEquals(GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_ELEMENT_TYPE, report.rejections().get(0).reason());
        assertTrue(report.rejections().get(0).summary().contains("out=null"));
        assertTrue(report.rejections().get(0).summary().contains("missing=null"));
    }

    @Test
    void rewritePlanGuardsNeighboringSourceMutationsBeforeOperations() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrArrayAccess("left", new GpuIrLiteral("0")), new GpuIrLiteral("7")),
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));

        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method,
                parameter("left", "int[]"),
                parameter("out", "int[]")
        )).preview();

        assertTrue(preview.hasRewriteCandidates());
        assertEquals(1, preview.rewriteCandidateCount());
        assertTrue(preview.rewriteCandidates().get(0).memoryGuardDiagnostics().get(0).contains("previous statement stmt[0] writes source array `left`"));
        assertEquals(
                GpuIrAutoVectorizationRewriteGuardFamily.NEIGHBOR_SOURCE_WRITE,
                preview.rewriteCandidates().get(0).memoryGuardDiagnosticDetails().get(0).family()
        );
        assertTrue(preview.rewritePlan().hasGuardDiagnostics());
        assertEquals(0, preview.rewritePlan().operationCount());
        assertTrue(preview.rewritePlan().guardDiagnostics().get(0).contains("previous statement stmt[0] writes source array `left`"));
        assertEquals(java.util.Map.of("neighborSourceWrite", 1L), preview.rewritePlan().guardFamilyCounts());
        assertEquals(1, preview.mutationProofSummary().diagnosticCount());
        assertEquals(java.util.Map.of("neighborSourceWrite", 1L), preview.mutationProofSummary().guardFamilyCounts());
        assertTrue(preview.proofBundle().proofKinds().contains("mutation"));
        assertEquals("1", preview.proofBundle()
                .artifactFields()
                .get("autoVectorizationProofBundleGuardFamily.neighborSourceWrite"));
    }

    @Test
    void rewritePlanGuardsNeighboringTargetMutationsBeforeOperations() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                ))),
                new GpuIrAssignment(new GpuIrArrayAccess("out", new GpuIrLiteral("0")), new GpuIrLiteral("3"))
        ));

        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method,
                parameter("left", "int[]"),
                parameter("out", "int[]")
        )).preview();

        assertTrue(preview.hasRewriteCandidates());
        assertTrue(preview.rewriteCandidates().get(0).memoryGuardDiagnostics().get(0).contains("next statement stmt[1] writes target array `out`"));
        assertEquals(
                GpuIrAutoVectorizationRewriteGuardFamily.NEIGHBOR_TARGET_WRITE,
                preview.rewriteCandidates().get(0).memoryGuardDiagnosticDetails().get(0).family()
        );
        assertTrue(preview.rewritePlan().hasGuardDiagnostics());
        assertEquals(0, preview.rewritePlan().operationCount());
        assertEquals(java.util.Map.of("neighborTargetWrite", 1L), preview.rewritePlan().guardFamilyCounts());
        assertEquals(1, preview.mutationProofSummary().diagnosticCount());
        assertEquals(java.util.Map.of("neighborTargetWrite", 1L), preview.mutationProofSummary().guardFamilyCounts());
        assertTrue(preview.proofBundle().proofKinds().contains("mutation"));
    }

    @Test
    void rewritePlanGuardsNonAdjacentSourceMutationsBeforeOperations() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrArrayAccess("left", new GpuIrLiteral("0")), new GpuIrLiteral("7")),
                new GpuIrVariableDeclaration("int", "unrelated", new GpuIrLiteral("1")),
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));

        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method,
                parameter("left", "int[]"),
                parameter("out", "int[]")
        )).preview();

        assertTrue(preview.hasRewriteCandidates());
        assertTrue(preview.rewritePlan().hasGuardDiagnostics());
        assertEquals(0, preview.rewritePlan().operationCount());
        assertTrue(preview.rewritePlan().guardDiagnostics().get(0).contains("previous statement stmt[0] writes source array `left`"));
        assertEquals(java.util.Map.of("neighborSourceWrite", 1L), preview.rewritePlan().guardFamilyCounts());
    }

    @Test
    void rewritePlanGuardsNonAdjacentTargetMutationsBeforeOperations() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                ))),
                new GpuIrVariableDeclaration("int", "unrelated", new GpuIrLiteral("1")),
                new GpuIrAssignment(new GpuIrArrayAccess("out", new GpuIrLiteral("0")), new GpuIrLiteral("3"))
        ));

        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method,
                parameter("left", "int[]"),
                parameter("out", "int[]")
        )).preview();

        assertTrue(preview.hasRewriteCandidates());
        assertTrue(preview.rewritePlan().hasGuardDiagnostics());
        assertEquals(0, preview.rewritePlan().operationCount());
        assertTrue(preview.rewritePlan().guardDiagnostics().get(0).contains("next statement stmt[2] writes target array `out`"));
        assertEquals(java.util.Map.of("neighborTargetWrite", 1L), preview.rewritePlan().guardFamilyCounts());
    }

    @Test
    void rewritePlanGuardsSiblingControlFlowBeforeOperations() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrIf(new GpuIrVariableRef("flag"), List.of(), List.of()),
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));

        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method,
                parameter("flag", "boolean"),
                parameter("left", "int[]"),
                parameter("out", "int[]")
        )).preview();

        assertTrue(preview.hasRewriteCandidates());
        assertTrue(preview.rewritePlan().hasGuardDiagnostics());
        assertEquals(0, preview.rewritePlan().operationCount());
        assertTrue(preview.rewritePlan().guardDiagnostics().get(0).contains("previous statement stmt[0] is a control-flow boundary"));
        assertEquals(java.util.Map.of("controlFlowBoundary", 1L), preview.rewritePlan().guardFamilyCounts());
    }

    @Test
    void rewritePlanGuardsSiblingEarlyExitBeforeOperations() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrReturn(null),
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));

        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method,
                parameter("left", "int[]"),
                parameter("out", "int[]")
        )).preview();

        assertTrue(preview.hasRewriteCandidates());
        assertTrue(preview.rewritePlan().hasGuardDiagnostics());
        assertEquals(0, preview.rewritePlan().operationCount());
        assertTrue(preview.rewritePlan().guardDiagnostics().get(0).contains("previous statement stmt[0] is an early-exit boundary"));
        assertEquals(java.util.Map.of("earlyExitBoundary", 1L), preview.rewritePlan().guardFamilyCounts());
    }

    @Test
    void rewritePlanGroupsTargetSourceAliasGuardFamilies() {
        GpuIrAutoVectorizationRewriteGuardDiagnostic memoryGuard = new GpuIrAutoVectorizationRewriteGuardDiagnostic(
                GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE,
                "stmt[0]",
                "source array `input` uses constant memory address space"
        );
        GpuIrAutoVectorizationRewriteCandidatePreview candidate = new GpuIrAutoVectorizationRewriteCandidatePreview(
                "stmt[0]",
                "i",
                0,
                4,
                4,
                1,
                4,
                "x4",
                "int",
                "int4",
                List.of("write out[i=0..3]"),
                List.of("read out[i=0..3]"),
                List.of(),
                List.of("out"),
                List.of("out")
        );

        GpuIrAutoVectorizationRewritePlan plan = new GpuIrAutoVectorizationPreview(
                "kernel",
                List.of(candidate),
                List.of(),
                List.of()
        ).rewritePlan();
        GpuIrAutoVectorizationPreview preview = new GpuIrAutoVectorizationPreview(
                "kernel",
                List.of(candidate),
                List.of(),
                List.of()
        );

        assertTrue(plan.hasGuardDiagnostics());
        assertEquals(0, plan.operationCount());
        assertEquals(java.util.Map.of("targetSourceAlias", 1L), plan.guardFamilyCounts());
        assertEquals(java.util.Map.of("targetSourceAlias", 1L), preview.mutationProofSummary().guardFamilyCounts());
        assertTrue(preview.proofBundle().proofKinds().contains("mutation"));
    }

    @Test
    void rewritePlanProofSummaryCombinesWarningsAndGuards() {
        GpuIrAutoVectorizationRewriteGuardDiagnostic memoryGuard = new GpuIrAutoVectorizationRewriteGuardDiagnostic(
                GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE,
                "stmt[0]",
                "source array `input` uses constant memory address space"
        );
        GpuIrAutoVectorizationRewriteCandidatePreview candidate = new GpuIrAutoVectorizationRewriteCandidatePreview(
                "stmt[0]",
                "i",
                0,
                4,
                4,
                1,
                4,
                "x4",
                "int",
                "int4",
                List.of("write out[i=0..3]"),
                List.of("read input[i=0..3]"),
                List.of(memoryGuard),
                List.of("out"),
                List.of("input")
        );
        GpuIrAutoVectorizationWarningDiagnostic warning = new GpuIrAutoVectorizationWarningDiagnostic(
                "stmt[0]",
                1,
                List.of("target array `out` is read inside the same loop body"),
                List.of(),
                List.of(),
                List.of()
        );
        GpuIrAutoVectorizationPreview preview = new GpuIrAutoVectorizationPreview(
                "kernel",
                List.of(candidate),
                List.of(warning),
                List.of()
        );

        GpuIrAutoVectorizationProofSummary summary = preview.rewritePlanProofSummary();

        assertFalse(summary.rewriteSafe());
        assertEquals("rewritePlan", summary.proofKind());
        assertEquals("kernel", summary.location());
        assertEquals(1, summary.warningCount());
        assertEquals(1, summary.guardDiagnosticCount());
        assertEquals(2, summary.diagnosticCount());
        assertEquals(java.util.Map.of("memoryAddressSpace", 1L), summary.guardFamilyCounts());
        assertEquals("2", summary.artifactFields("autoVectorizationProofRewritePlan")
                .get("autoVectorizationProofRewritePlanDiagnostics"));

        GpuIrAutoVectorizationProofBundle bundle = preview.proofBundle();

        assertFalse(bundle.rewriteSafe());
        assertEquals(1, bundle.summaries().size());
        assertEquals(List.of("rewritePlan"), bundle.proofKinds());
        assertEquals(1, bundle.warningCount());
        assertEquals(1, bundle.guardDiagnosticCount());
        assertEquals(2, bundle.diagnosticCount());
        assertEquals(java.util.Map.of("memoryAddressSpace", 1L), bundle.guardFamilyCounts());
    }

    @Test
    void rewritePlanGuardsTargetSourceAliasingBeforeOperations() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i"))
                )))
        ));

        GpuIrAutoVectorizationReport report = scanner.scan(compiledMethod(method,
                parameter("out", "int[]")
        ));

        assertTrue(report.hasCandidates());
        assertTrue(report.hasAliasWarnings());
        assertFalse(report.preview().hasRewriteCandidates());
        assertEquals(0, report.preview().rewritePlan().operationCount());
    }

    @Test
    void ignoresUnsupportedLaneCountsAndNonZeroStarts() {
        GpuIrMethod unsupportedWidth = new GpuIrMethod("kernel", List.of(fixedWidthLoop(5, List.of(
                new GpuIrAssignment(new GpuIrArrayAccess("out", new GpuIrVariableRef("i")), new GpuIrVariableRef("i"))
        ))));
        GpuIrMethod nonZeroStart = new GpuIrMethod("kernel", List.of(new GpuIrForLoop(
                new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("1")),
                new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral("4")),
                new GpuIrAssignment(new GpuIrVariableRef("i"), new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                List.of(new GpuIrAssignment(new GpuIrArrayAccess("out", new GpuIrVariableRef("i")), new GpuIrVariableRef("i")))
        )));

        assertFalse(scanner.scan(unsupportedWidth).hasCandidates());
        assertFalse(scanner.scan(nonZeroStart).hasCandidates());

        GpuIrAutoVectorizationReport unsupportedWidthReport = scanner.scan(unsupportedWidth);
        assertTrue(unsupportedWidthReport.hasRejections());
        assertEquals(1, unsupportedWidthReport.rejectionCount());
        assertEquals(GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_LANE_COUNT, unsupportedWidthReport.rejections().get(0).reason());
        assertTrue(unsupportedWidthReport.rejections().get(0).summary().contains("laneCount=5"));

        GpuIrAutoVectorizationReport nonZeroStartReport = scanner.scan(nonZeroStart);
        assertEquals(GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_LOOP_SHAPE, nonZeroStartReport.rejections().get(0).reason());
    }

    @Test
    void rejectsBodiesWithSideEffectsOrNonLaneTargets() {
        GpuIrMethod sideEffecting = new GpuIrMethod("kernel", List.of(fixedWidthLoop(4, List.of(
                new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrIntrinsicCall(null, "barrier", "barrier(1)", "void", List.of())
                )
        ))));
        GpuIrMethod nonLaneTarget = new GpuIrMethod("kernel", List.of(fixedWidthLoop(4, List.of(
                new GpuIrAssignment(new GpuIrArrayAccess("out", new GpuIrLiteral("0")), new GpuIrVariableRef("i"))
        ))));

        assertFalse(scanner.scan(sideEffecting).hasCandidates());
        assertFalse(scanner.scan(nonLaneTarget).hasCandidates());

        GpuIrAutoVectorizationReport sideEffectingReport = scanner.scan(sideEffecting);
        assertEquals(GpuIrAutoVectorizationRejectionReason.SIDE_EFFECTING_VALUE, sideEffectingReport.rejections().get(0).reason());
        assertEquals(1, sideEffectingReport.sideEffectProofSummaries().size());
        assertEquals("sideEffect", sideEffectingReport.sideEffectProofSummaries().get(0).proofKind());
        assertEquals("stmt[0]", sideEffectingReport.sideEffectProofSummaries().get(0).location());
        assertEquals(java.util.Map.of("sideEffect", 1L), sideEffectingReport.sideEffectProofSummaries().get(0).guardFamilyCounts());
        assertEquals(GpuIrAutoVectorizationProofDecisionStatus.BLOCKED_BY_SIDE_EFFECT, sideEffectingReport.preview().proofDecision().status());
        assertTrue(sideEffectingReport.preview().firstBlockingDiagnosticSummary().orElseThrow().contains("SIDE_EFFECTING_VALUE"));
        assertEquals("rejection.SIDE_EFFECTING_VALUE", sideEffectingReport.preview().firstBlockingDiagnosticFamily().orElseThrow());
        assertTrue(sideEffectingReport.preview().proofDecision().summary().contains("sideEffect@stmt[0]"));
        assertEquals("1", sideEffectingReport.preview().proofBundle()
                .artifactFields()
                .get("autoVectorizationProofBundleGuardFamily.sideEffect"));

        GpuIrAutoVectorizationReport nonLaneTargetReport = scanner.scan(nonLaneTarget);
        assertEquals(GpuIrAutoVectorizationRejectionReason.NON_LANE_TARGET, nonLaneTargetReport.rejections().get(0).reason());
        assertEquals(1L, nonLaneTargetReport.rejectionReasonCounts().get(GpuIrAutoVectorizationRejectionReason.NON_LANE_TARGET));
        assertEquals(1, nonLaneTargetReport.rejectionsByReason().get(GpuIrAutoVectorizationRejectionReason.NON_LANE_TARGET).size());
    }

    @Test
    void groupsMultipleRejectedLoopsByReason() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(5, List.of(
                        new GpuIrAssignment(new GpuIrArrayAccess("outA", new GpuIrVariableRef("i")), new GpuIrVariableRef("i"))
                )),
                fixedWidthLoop(7, List.of(
                        new GpuIrAssignment(new GpuIrArrayAccess("outB", new GpuIrVariableRef("i")), new GpuIrVariableRef("i"))
                )),
                fixedWidthLoop(4, List.of(
                        new GpuIrAssignment(new GpuIrArrayAccess("outC", new GpuIrLiteral("0")), new GpuIrVariableRef("i"))
                ))
        ));

        GpuIrAutoVectorizationReport report = scanner.scan(method);

        assertFalse(report.hasCandidates());
        assertTrue(report.hasRejections());
        assertEquals(3, report.rejectionCount());
        assertEquals(2L, report.rejectionReasonCounts().get(GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_LANE_COUNT));
        assertEquals(1L, report.rejectionReasonCounts().get(GpuIrAutoVectorizationRejectionReason.NON_LANE_TARGET));
        assertEquals(2, report.rejectionsByReason().get(GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_LANE_COUNT).size());
        assertTrue(report.summary().contains("rejections=3"));
        assertTrue(report.summary().contains("UNSUPPORTED_LANE_COUNT=2"));
        GpuIrAutoVectorizationPreview preview = report.preview();
        assertFalse(preview.hasRewriteCandidates());
        assertFalse(preview.hasWarnings());
        assertTrue(preview.hasRejections());
        assertTrue(preview.hasBlockingDiagnostics());
        assertEquals(3, preview.totalDiagnosticCount());
        assertEquals(2L, preview.rejectionReasonCounts().get(GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_LANE_COUNT));
        assertTrue(preview.firstBlockingDiagnosticSummary().orElseThrow().contains("UNSUPPORTED_LANE_COUNT"));
        assertTrue(preview.summary().contains("totalDiagnostics=3"));
    }

    @Test
    void reportsIncompleteIrDiagnosticsInsteadOfThrowing() {
        GpuIrMethod missingStatements = new GpuIrMethod("missingStatements", null);
        GpuIrMethod missingTopLevelStatement = new GpuIrMethod("missingTopLevelStatement", Arrays.asList((GpuIrStatement) null));
        GpuIrMethod missingLoopBody = new GpuIrMethod("missingLoopBody", List.of(new GpuIrForLoop(
                new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("0")),
                new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral("4")),
                new GpuIrAssignment(new GpuIrVariableRef("i"), new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                null
        )));
        GpuIrMethod missingNestedBranch = new GpuIrMethod("missingNestedBranch", List.of(new GpuIrIf(
                new GpuIrVariableRef("flag"),
                null,
                List.of()
        )));
        GpuIrMethod missingSwitchCases = new GpuIrMethod("missingSwitchCases", List.of(new GpuIrSwitch(
                new GpuIrVariableRef("selector"),
                null
        )));
        GpuIrMethod missingSwitchCase = new GpuIrMethod("missingSwitchCase", List.of(new GpuIrSwitch(
                new GpuIrVariableRef("selector"),
                Arrays.asList((GpuIrSwitchCase) null)
        )));

        assertIncompleteIr(missingStatements, "missing statement list");
        assertIncompleteIr(missingTopLevelStatement, "missing statement");
        assertIncompleteIr(missingLoopBody, "missing loop body");
        assertIncompleteIr(missingNestedBranch, "missing statement list");
        assertIncompleteIr(missingSwitchCases, "missing switch cases");
        assertIncompleteIr(missingSwitchCase, "missing switch case");
    }

    @Test
    void treatsIncompleteExpressionArgumentListsAsUnsafeWithoutThrowing() {
        GpuIrMethod missingStructArguments = new GpuIrMethod("missingStructArguments", List.of(fixedWidthLoop(4, List.of(
                new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrStructInit("Pair", null)
                )
        ))));
        GpuIrMethod missingIntrinsicArguments = new GpuIrMethod("missingIntrinsicArguments", List.of(fixedWidthLoop(4, List.of(
                new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrIntrinsicCall(null, "native_sin", "native_sin({0})", "float", null)
                )
        ))));
        GpuIrMethod missingHelperArguments = new GpuIrMethod("missingHelperArguments", List.of(fixedWidthLoop(4, List.of(
                new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrHelperCall("jtg_helper", "float", null)
                )
        ))));

        assertSideEffectingValue(missingStructArguments);
        assertSideEffectingValue(missingIntrinsicArguments);
        assertSideEffectingValue(missingHelperArguments);
    }

    @Test
    void reportAndCandidateValidateRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationReport("", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationCandidate(
                "stmt[0]",
                "i",
                0,
                4,
                3,
                List.of("out"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                1,
                "int",
                "int4"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationRejectionDiagnostic(
                "",
                GpuIrAutoVectorizationRejectionReason.EMPTY_BODY,
                "empty"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationWarningDiagnostic(
                "stmt[0]",
                1,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationRewriteCandidatePreview(
                "stmt[0]",
                "i",
                0,
                4,
                4,
                1,
                0,
                "x4",
                "int",
                "int4",
                List.of("write out[i=0..3]"),
                List.of(),
                List.of(),
                List.of("out"),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationPreview(
                "",
                List.of(),
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationPreview(
                "kernel",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("")
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationReport(
                "kernel",
                List.of(),
                List.of(),
                List.of("")
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationRewritePlan(
                "kernel",
                List.of(),
                List.of("extra insertion"),
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationRewritePlan(
                "kernel",
                List.of(),
                List.of(),
                List.of(),
                List.of("guard stmt[0]: unknown vector type blocks rewrite operations"),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationRewritePlan(
                "kernel",
                List.of(),
                List.of(),
                List.of(),
                List.of("guard stmt[0]: unknown vector type blocks rewrite operations"),
                List.of(new GpuIrAutoVectorizationRewriteGuardDiagnostic(
                        GpuIrAutoVectorizationRewriteGuardFamily.BACKEND_VECTOR_WIDTH,
                        "stmt[0]",
                        "backend vector width x3 requires explicit ABI support before rewrite operations"
                ))
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationRewritePolicy(
                "kernel",
                0,
                1,
                List.of()
        ));
    }

    private GpuIrForLoop fixedWidthLoop(int endExclusive, List<net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement> body) {
        return new GpuIrForLoop(
                new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("0")),
                new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral(Integer.toString(endExclusive))),
                new GpuIrAssignment(new GpuIrVariableRef("i"), new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                body
        );
    }

    private net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod compiledMethod(
            GpuIrMethod irMethod,
            net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter... parameters
    ) {
        net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod parsedMethod = new net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                irMethod.name(),
                "void",
                List.of(parameters),
                List.of(),
                List.of(),
                null,
                false,
                List.of(),
                null,
                "",
                null,
                false
        );
        return new net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod(parsedMethod, irMethod, "jtg_kernel", List.of());
    }

    private net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter parameter(String name, String type) {
        return parameter(name, type, net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace.GLOBAL, false);
    }

    private net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter parameter(
            String name,
            String type,
            net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace addressSpace,
            boolean constant
    ) {
        return new net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter(
                name,
                type,
                addressSpace,
                constant,
                List.of()
        );
    }

    private void assertIncompleteIr(GpuIrMethod method, String detail) {
        GpuIrAutoVectorizationReport report = scanner.scan(method);

        assertFalse(report.hasCandidates());
        assertTrue(report.hasRejections());
        assertTrue(report.noCandidateBuckets().contains("incompleteIr"));
        assertEquals(GpuIrAutoVectorizationRejectionReason.INCOMPLETE_IR, report.rejections().get(0).reason());
        assertTrue(report.rejections().get(0).summary().contains(detail));
    }

    private void assertSideEffectingValue(GpuIrMethod method) {
        GpuIrAutoVectorizationReport report = scanner.scan(method);

        assertFalse(report.hasCandidates());
        assertTrue(report.hasRejections());
        assertEquals(GpuIrAutoVectorizationRejectionReason.SIDE_EFFECTING_VALUE, report.rejections().get(0).reason());
    }
}
