package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationArtifactSnapshotTest {
    @Test
    void artifactFieldsExposeStableReadOnlyAutoVectorizationSurface() {
        GpuIrMethod irMethod = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));
        GpuIrAutoVectorizationPreview preview = new GpuIrAutoVectorizationPlanningPass().preview(context(method(irMethod)));
        GpuIrAutoVectorizationRewriteApplicator applicator = new GpuIrAutoVectorizationRewriteApplicator();
        GpuIrAutoVectorizationRewriteDryRunReport dryRunReport = applicator.dryRun(irMethod, preview.rewritePlan());
        GpuIrAutoVectorizationResolvedRewriteOperations resolvedOperations = applicator.resolveOperations(irMethod, preview.rewritePlan());

        GpuIrAutoVectorizationArtifactSnapshot snapshot = new GpuIrAutoVectorizationArtifactSnapshot(
                preview,
                dryRunReport,
                resolvedOperations
        );
        Map<String, String> fields = snapshot.artifactFields("autoVectorization");
        Map<String, String> defaultFields = snapshot.artifactFields();

        assertEquals(1, snapshot.candidateCount());
        assertEquals(0, snapshot.warningCount());
        assertEquals(0, snapshot.rejectionCount());
        assertEquals("1", fields.get("autoVectorizationCandidates"));
        assertEquals("0", fields.get("autoVectorizationWarnings"));
        assertEquals("0", fields.get("autoVectorizationRejections"));
        assertEquals("ready", fields.get("autoVectorizationRewriteReadiness"));
        assertEquals("true", fields.get("autoVectorizationCanApplyRewrite"));
        assertEquals("readyForPrototypeRewrite", fields.get("autoVectorizationReadinessVerdict"));
        assertEquals("true", fields.get("autoVectorizationReadinessReadyForPrototypeRewrite"));
        assertEquals("[]", fields.get("autoVectorizationReadinessBlockingReasons"));
        assertEquals("notApplicable", fields.get("autoVectorizationNoCandidateReadiness"));
        assertEquals("false", fields.get("autoVectorizationNoCandidateNoCandidates"));
        assertEquals("none", fields.get("autoVectorizationNoCandidateFirstBucket"));
        assertEquals("ready", fields.get("autoVectorizationBlockerVerdict"));
        assertEquals("none", fields.get("autoVectorizationBlockerFirstFamily"));
        assertEquals("none", fields.get("autoVectorizationBlockerFirstRemainingWork"));
        assertEquals("auto-vectorization readiness ready", fields.get("autoVectorizationReadinessCiSummaryLine"));
        assertEquals("allow", fields.get("autoVectorizationProofDecisionStatus"));
        assertEquals("true", fields.get("autoVectorizationProofDecisionAllowRewrite"));
        assertEquals("", fields.get("autoVectorizationProofDecisionBlockingProofKinds"));
        assertEquals("1", fields.get("autoVectorizationRewritePlanCandidates"));
        assertEquals("1", fields.get("autoVectorizationRewritePlanInsertions"));
        assertEquals("1", fields.get("autoVectorizationRewritePlanReplacements"));
        assertEquals("2", fields.get("autoVectorizationRewritePlanOperations"));
        assertEquals("0", fields.get("autoVectorizationRewritePlanGuards"));
        assertEquals("true", fields.get("autoVectorizationRewritePolicyCanRewrite"));
        assertEquals("ready", fields.get("autoVectorizationRewritePolicyReadiness"));
        assertEquals("2", fields.get("autoVectorizationRewritePolicyPlannedOperations"));
        assertEquals("0", fields.get("autoVectorizationRewritePolicyBlockingGuards"));
        assertEquals("ready", fields.get("autoVectorizationRewriteDryRunReadiness"));
        assertEquals("true", fields.get("autoVectorizationRewriteDryRunSuccessful"));
        assertEquals("0", fields.get("autoVectorizationRewriteDryRunDiagnostics"));
        assertEquals("1", fields.get("autoVectorizationRewriteDryRunCandidates"));
        assertEquals("2", fields.get("autoVectorizationRewriteDryRunOperations"));
        assertEquals("1", fields.get("autoVectorizationResolvedRewriteInsertions"));
        assertEquals("1", fields.get("autoVectorizationResolvedRewriteReplacements"));
        assertEquals("2", fields.get("autoVectorizationResolvedRewriteOperations"));
        assertEquals("rewritePlan", fields.get("autoVectorizationProofRewritePlanKind"));
        assertEquals("true", fields.get("autoVectorizationProofRewritePlanRewriteSafe"));
        assertEquals("2", fields.get("autoVectorizationProofBundleProofs"));
        assertEquals("true", fields.get("autoVectorizationProofBundleRewriteSafe"));
        assertEquals("0", fields.get("autoVectorizationProofBundleDiagnostics"));
        assertEquals("ready", fields.get("autoVectorizationProofLayerReadinessVerdict"));
        assertEquals("true", fields.get("autoVectorizationProofLayerReadinessAllLayersReady"));
        assertEquals("0", fields.get("autoVectorizationProofLayerReadinessBlockingLayerCount"));
        assertEquals("none", fields.get("autoVectorizationProofLayerReadinessFirstBlockingLayer"));
        assertEquals("{int4=1}", fields.get("autoVectorizationVectorTypeCounts"));
        assertEquals("1", fields.get("autoVectorizationUniqueVectorTypes"));
        assertEquals("1", fields.get("autoVectorizationVectorType.int4"));
        assertEquals("{}", fields.get("autoVectorizationWarningFamilyCounts"));
        assertEquals("0", fields.get("autoVectorizationUniqueWarningFamilies"));
        assertEquals("{}", fields.get("autoVectorizationRejectionReasonCounts"));
        assertEquals("0", fields.get("autoVectorizationUniqueRejectionReasons"));
        assertEquals("1", defaultFields.get("autoVectorizationCandidates"));
        assertEquals("{int4=1}", defaultFields.get("autoVectorizationVectorTypeCounts"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
        assertTrue(fields.get("autoVectorizationResolvedRewriteFirstInsertion").contains("stmt[0]"));
        assertTrue(fields.get("autoVectorizationResolvedRewriteFirstReplacement").contains("stmt[0]"));
        assertTrue(snapshot.summary().contains("auto-vectorization artifact snapshot"));
        assertTrue(snapshot.summary().contains("readinessVerdict=readyForPrototypeRewrite"));
        assertTrue(snapshot.summary().contains("uniqueVectorTypes=1"));
        assertTrue(snapshot.summary().contains("uniqueWarningFamilies=0"));
        assertTrue(snapshot.summary().contains("uniqueRejectionReasons=0"));
        assertTrue(snapshot.summary().contains("resolvedRewriteOperations=2"));
    }

    private GpuIrForLoop fixedWidthLoop(int endExclusive, List<GpuIrStatement> body) {
        return new GpuIrForLoop(
                new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("0")),
                new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral(Integer.toString(endExclusive))),
                new GpuIrAssignment(new GpuIrVariableRef("i"), new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                body
        );
    }

    private GpuIrPassContext context(GpuIrCompiledMethod method) {
        return new GpuIrPassContext(method, List.of(), List.of(), true);
    }

    private GpuIrCompiledMethod method(GpuIrMethod irMethod) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                irMethod.name(),
                "void",
                List.of(
                        new ParsedGpuParameter("left", "int[]", GpuAddressSpace.GLOBAL, false, List.of()),
                        new ParsedGpuParameter("out", "int[]", GpuAddressSpace.GLOBAL, false, List.of())
                ),
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
        return new GpuIrCompiledMethod(parsedMethod, irMethod, "jtg_kernel", List.of());
    }
}
