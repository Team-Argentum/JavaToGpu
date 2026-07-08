package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassException;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrExpressionStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationPipelineTest {
    private final GpuIrOptimizationValidationPipeline pipeline = new GpuIrOptimizationValidationPipeline();

    @Test
    void combinesSafetyCseAndAutoVectorizationDiagnosticsWithoutMutatingIr() {
        GpuIrMethod irMethod = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x"))),
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                ))),
                new GpuIrReturn(null)
        ));
        GpuIrPassContext context = context(method(irMethod));

        GpuIrOptimizationValidationReport report = pipeline.validate(context);

        assertFalse(report.hasSafetyError());
        assertTrue(report.commonSubexpressionPreview().hasRewriteWork());
        assertTrue(report.autoVectorizationPreview().hasRewriteCandidates());
        assertTrue(report.hasBlockingDiagnostics());
        assertTrue(report.hasOptimizerDiagnostics());
        assertTrue(report.optimizerGateExplanation().blocked());
        assertEquals("autoVectorization", report.optimizerGateExplanation().source());
        assertEquals("guard.earlyExitBoundary", report.optimizerGateExplanation().family());
        assertEquals(java.util.Map.of("autoVectorization", 1L), report.optimizerGateSourceCounts());
        assertEquals(java.util.Map.of("guard.earlyExitBoundary", 1L), report.optimizerGateFamilyCounts());
        assertFalse(report.hasCommonSubexpressionDiagnostics());
        assertTrue(report.hasAutoVectorizationDiagnostics());
        assertEquals(1, report.commonSubexpressionInsertionCount());
        assertEquals(1, report.commonSubexpressionReplacementCount());
        assertEquals(0, report.commonSubexpressionSkippedCount());
        assertEquals(1, report.autoVectorizationRewriteCandidateCount());
        assertEquals(0, report.autoVectorizationWarningCount());
        assertEquals(0, report.autoVectorizationRejectionCount());
        assertEquals(1, report.optimizerDiagnosticCount());
        assertTrue(report.summary().contains("safety=ok"));
        assertTrue(report.summary().contains("optimizerDiagnostics=1"));
        assertTrue(report.summary().contains("optimizerGate={optimizer gate blocked=true source=autoVectorization family=guard.earlyExitBoundary"));
        assertTrue(report.summary().contains("optimizerGateSourceCounts={autoVectorization=1}"));
        assertTrue(report.summary().contains("optimizerGateFamilyCounts={guard.earlyExitBoundary=1}"));
        assertTrue(report.summary().contains("cse rewrite preview"));
        assertTrue(report.summary().contains("auto-vectorization preview"));
        assertTrue(report.summary().contains("rewriteCandidates=1"));
        assertTrue(report.summary().contains("insertions=1"));
        assertTrue(report.summary().contains("replacements=1"));
        assertTrue(report.summary().contains("skipped=0"));
        assertTrue(report.summary().contains("warnings=0"));
        assertTrue(report.summary().contains("rejections=0"));
        assertTrue(report.summary().contains("rewritePlanGuards=1"));
        assertTrue(report.summary().contains("earlyExitBoundary=1"));
        assertTrue(report.compactSummary().contains("ir optimization validation method=kernel"));
        assertTrue(report.compactSummary().contains("optimizerGateBlocked=true"));
        assertTrue(report.compactSummary().contains("optimizerGateSource=autoVectorization"));
        assertTrue(report.compactSummary().contains("optimizerGateFamily=guard.earlyExitBoundary"));
        assertTrue(report.compactSummary().contains("optimizerGateSourceCounts={autoVectorization=1}"));
        assertTrue(report.compactSummary().contains("optimizerGateFamilyCounts={guard.earlyExitBoundary=1}"));
        assertTrue(report.compactSummary().contains("runtimeEquivalenceDiagnosticFamilyCounts={other=1}"));
        assertTrue(report.detailedSummary().contains("runtimeEquivalenceDiagnosticFamilyCounts={other=1}"));
        assertTrue(report.compactSummary().contains("cseInsertions=1"));
        assertTrue(report.compactSummary().contains("cseLocalExpressionProvenCandidates=0"));
        assertTrue(report.compactSummary().contains("cseLocalExpressionHasEvidence=false"));
        assertTrue(report.compactSummary().contains("autoVectorizationCandidates=1"));
        assertTrue(report.compactSummary().contains("autoVectorizationRewriteReadiness=blockedByGuard"));
        assertTrue(report.compactSummary().contains("autoVectorizationRejections=0"));
        assertTrue(report.compactSummary().contains("autoVectorizationProofBundleUnsafeProofs=2"));
        assertTrue(report.compactSummary().contains("autoVectorizationProofBundleFirstUnsafeProof=rewritePlan@kernel"));
        assertFalse(report.compactSummary().contains("cse rewrite preview"));
        assertTrue(irMethod.statements().get(0) instanceof GpuIrVariableDeclaration);
    }

    @Test
    void capturesSafetyFailuresAndSkipsCsePlanningForMalformedIr() {
        GpuIrPassContext context = context(method(new GpuIrMethod("broken", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrVariableRef("missing"))
        ))));

        GpuIrOptimizationValidationReport report = pipeline.validate(context);

        assertTrue(report.hasSafetyError());
        assertTrue(report.optimizerGateExplanation().blocked());
        assertEquals("safety", report.optimizerGateExplanation().source());
        assertEquals("safety.error", report.optimizerGateExplanation().family());
        assertEquals(java.util.Map.of("safety", 1L), report.optimizerGateSourceCounts());
        assertEquals(java.util.Map.of("safety.error", 1L), report.optimizerGateFamilyCounts());
        assertTrue(report.safetyError().orElseThrow().contains("unknown variable reference: missing"));
        assertFalse(report.commonSubexpressionPreview().hasRewriteWork());
        assertEquals(GpuIrAutoVectorizationRewriteDryRunReadiness.SKIPPED, report.autoVectorizationRewriteDryRunReadiness());
        assertFalse(report.autoVectorizationRewriteDryRunSuccessful());
        assertEquals(1, report.autoVectorizationRewriteDryRunDiagnosticCount());
        assertTrue(report.autoVectorizationRewriteDryRunReport().firstDiagnostic().contains("dry-run skipped"));
        assertEquals(0, report.optimizerDiagnosticCount());
        assertTrue(report.summary().contains("safety=failed"));
        assertTrue(report.summary().contains("unknown variable reference: missing"));
        assertTrue(report.compactSummary().contains("autoVectorizationRewriteDryRunReadiness=skipped"));
        assertTrue(report.compactSummary().contains("optimizerGateSource=safety"));
        assertTrue(report.compactSummary().contains("optimizerGateSourceCounts={safety=1}"));
        assertTrue(report.compactSummary().contains("optimizerGateFamilyCounts={safety.error=1}"));
        assertTrue(report.compactSummary().contains("autoVectorizationRewriteDryRunSuccessful=false"));
    }

    @Test
    void classifiesOpaqueMutableHelperStorageArgumentsAsTypedSafetyGateFamily() {
        GpuIrCompiledMethod helper = helperMethod();
        GpuIrCompiledMethod kernel = method(
                new GpuIrMethod("opaqueMutableHelperArgument", List.of(
                        new GpuIrVariableDeclaration("boolean", "enabled", new GpuIrLiteral("true")),
                        new GpuIrExpressionStatement(new GpuIrHelperCall(
                                "jtg_write_helper",
                                "void",
                                List.of(new GpuIrTernary(
                                        new GpuIrVariableRef("enabled"),
                                        new GpuIrVariableRef("out"),
                                        new GpuIrVariableRef("out")
                                ))
                        ))
                )),
                List.of("jtg_write_helper")
        );

        GpuIrOptimizationValidationReport report = pipeline.validate(context(kernel, List.of(helper)));

        assertTrue(report.hasSafetyError());
        assertTrue(report.optimizerGateExplanation().blocked());
        assertEquals("safety", report.optimizerGateExplanation().source());
        assertEquals("safety.helperMutableStorageOpaqueArgument", report.optimizerGateExplanation().family());
        assertEquals(java.util.Map.of("safety", 1L), report.optimizerGateSourceCounts());
        assertEquals(java.util.Map.of("safety.helperMutableStorageOpaqueArgument", 1L), report.optimizerGateFamilyCounts());
        assertTrue(report.safetyError().orElseThrow()
                .contains("mutable helper argument target for jtg_write_helper must reference declared storage directly"));
        assertTrue(report.compactSummary().contains("optimizerGateFamily=safety.helperMutableStorageOpaqueArgument"));
        assertTrue(report.compactSummary().contains("optimizerGateFamilyCounts={safety.helperMutableStorageOpaqueArgument=1}"));
    }

    @Test
    void classifiesHelperSafetyFailuresAsTypedGateFamilies() {
        assertSafetyGateFamily(
                method(new GpuIrMethod("readOnlyMutableHelperArgument", List.of(
                        new GpuIrExpressionStatement(new GpuIrHelperCall(
                                "jtg_write_helper",
                                "void",
                                List.of(new GpuIrVariableRef("left"))
                        ))
                )), List.of("jtg_write_helper"), List.of(
                        new ParsedGpuParameter("x", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("y", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("z", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("left", "int[]", GpuAddressSpace.CONSTANT, false, List.of()),
                        new ParsedGpuParameter("out", "int[]", GpuAddressSpace.GLOBAL, false, List.of())
                )),
                List.of(helperMethod()),
                "safety.helperMutableStorageReadOnlyArgument",
                "read-only storage cannot be used as mutable helper argument target for jtg_write_helper: left"
        );
        assertSafetyGateFamily(
                method(new GpuIrMethod("helperArgumentCountMismatch", List.of(
                        new GpuIrExpressionStatement(new GpuIrHelperCall("jtg_write_helper", "void", List.of()))
                )), List.of("jtg_write_helper")),
                List.of(helperMethod()),
                "safety.helperArgumentCountMismatch",
                "helper call argument count mismatch for jtg_write_helper: expected 1 but got 0"
        );
        assertSafetyGateFamily(
                method(new GpuIrMethod("helperArgumentTypeMismatch", List.of(
                        new GpuIrExpressionStatement(new GpuIrHelperCall(
                                "jtg_flag_helper",
                                "void",
                                List.of(new GpuIrVariableRef("z"))
                        ))
                )), List.of("jtg_flag_helper")),
                List.of(helperMethod("flagHelper", "jtg_flag_helper", List.of(
                        new ParsedGpuParameter("flag", "boolean", GpuAddressSpace.PRIVATE, false, List.of())
                ))),
                "safety.helperArgumentTypeMismatch",
                "type mismatch in helper argument flag for jtg_flag_helper: expected boolean but got int"
        );
    }

    @Test
    void cseRewritePolicyBecomesFirstGateWhenOnlyCseBlocks() {
        GpuIrPassContext context = context(method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("z"), new GpuIrLiteral("1"))),
                new GpuIrAssignment(new GpuIrVariableRef("z"), new GpuIrLiteral("7")),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("z"), new GpuIrLiteral("1"))),
                new GpuIrReturn(null)
        ))));

        GpuIrOptimizationValidationReport report = pipeline.validate(context);

        assertFalse(report.hasSafetyError());
        assertFalse(report.hasAutoVectorizationDiagnostics());
        assertTrue(report.hasCommonSubexpressionDiagnostics());
        assertTrue(report.optimizerGateExplanation().blocked());
        assertEquals("cseRewritePolicy", report.optimizerGateExplanation().source());
        assertEquals("cseRewritePolicy.blockedBySkippedCandidate", report.optimizerGateExplanation().family());
        assertEquals(java.util.Map.of("cseRewritePolicy", 1L), report.optimizerGateSourceCounts());
        assertEquals(java.util.Map.of(
                "cseRewritePolicy.blockedBySkippedCandidate", 1L,
                "cseRewritePolicy.skipReason.MUTATED_BETWEEN_OCCURRENCES", 1L,
                "cseRewritePolicy.dominance.topLevelDownstreamReplacements", 1L
        ), report.optimizerGateFamilyCounts());
        assertEquals(GpuIrCommonSubexpressionRewriteReadiness.BLOCKED_BY_SKIPPED_CANDIDATE,
                report.commonSubexpressionArtifactSnapshot().rewritePolicy().readiness());
        assertFalse(report.commonSubexpressionArtifactSnapshot().rewritePolicy().canRewrite());
        assertEquals(1, report.optimizerDiagnosticCount());
        assertTrue(report.compactSummary().contains("optimizerGateSource=cseRewritePolicy"));
        assertTrue(report.compactSummary().contains("optimizerGateFamily=cseRewritePolicy.blockedBySkippedCandidate"));
        assertTrue(report.compactSummary().contains("optimizerGateSourceCounts={cseRewritePolicy=1}"));
        assertTrue(report.compactSummary().contains("cseRewritePolicyReadiness=blockedBySkippedCandidate"));
    }

    @Test
    void strictSafetyModeFailsOnlyOnSafetyErrors() {
        GpuIrOptimizationValidationPipeline strictPipeline = new GpuIrOptimizationValidationPipeline(
                GpuIrOptimizationValidationMode.STRICT_FAIL_ON_SAFETY_ERROR
        );
        GpuIrPassContext validContext = context(method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x"))),
                new GpuIrReturn(null)
        ))));
        GpuIrPassContext brokenContext = context(method(new GpuIrMethod("broken", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrVariableRef("missing"))
        ))));

        assertDoesNotThrow(() -> strictPipeline.validate(validContext));
        assertTrue(assertThrows(GpuIrPassException.class, () -> strictPipeline.validate(brokenContext))
                .getMessage().contains("unknown variable reference: missing"));
    }

    @Test
    void strictOptimizerModeFailsOnOptimizerDiagnostics() {
        GpuIrOptimizationValidationPipeline strictPipeline = new GpuIrOptimizationValidationPipeline(
                GpuIrOptimizationValidationMode.STRICT_FAIL_ON_OPTIMIZER_DIAGNOSTICS
        );
        GpuIrPassContext context = context(method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("z"), new GpuIrLiteral("1"))),
                new GpuIrAssignment(new GpuIrVariableRef("z"), new GpuIrLiteral("7")),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("z"), new GpuIrLiteral("1"))),
                fixedWidthLoop(5, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                ))),
                new GpuIrReturn(null)
        ))));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> strictPipeline.validate(context));

        assertTrue(exception.getMessage().contains("IR optimization validation failed for kernel"));
        assertTrue(exception.getMessage().contains("optimizer gate policy mode=STRICT_FAIL_ON_OPTIMIZER_DIAGNOSTICS blocked=true source=autoVectorization family=rejection.UNSUPPORTED_LANE_COUNT"));
        assertTrue(exception.getMessage().contains("optimizer gate blocked=true source=autoVectorization family=rejection.UNSUPPORTED_LANE_COUNT"));
        assertTrue(exception.getMessage().contains("optimizerGateSourceCounts={autoVectorization=1,cseRewritePolicy=1}"));
        assertTrue(exception.getMessage().contains("optimizerGateFamilyCounts={rejection.UNSUPPORTED_LANE_COUNT=1,cseRewritePolicy.blockedBySkippedCandidate=1,cseRewritePolicy.skipReason.MUTATED_BETWEEN_OCCURRENCES=1,cseRewritePolicy.dominance.topLevelDownstreamReplacements=1}"));
        assertTrue(exception.getMessage().contains("cseFirstSkippedDominance={MUTATED_BETWEEN_OCCURRENCES dominance=topLevelDownstreamReplacements"));
        assertTrue(exception.getMessage().contains("optimizerDiagnostics=2"));
        assertTrue(exception.getMessage().contains("MUTATED_BETWEEN_OCCURRENCES"));
        assertTrue(exception.getMessage().contains("UNSUPPORTED_LANE_COUNT"));
        assertTrue(exception.getMessage().contains("ir optimization validation"));
    }

    @Test
    void strictOptimizerModeIncludesFirstRewriteGuardDiagnostic() {
        GpuIrOptimizationValidationPipeline strictPipeline = new GpuIrOptimizationValidationPipeline(
                GpuIrOptimizationValidationMode.STRICT_FAIL_ON_OPTIMIZER_DIAGNOSTICS
        );
        GpuIrPassContext context = context(method(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                ))),
                new GpuIrReturn(null)
        ))));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> strictPipeline.validate(context));

        assertTrue(exception.getMessage().contains("IR optimization validation failed for kernel"));
        assertTrue(exception.getMessage().contains("optimizer gate policy mode=STRICT_FAIL_ON_OPTIMIZER_DIAGNOSTICS blocked=true source=autoVectorization family=guard.earlyExitBoundary"));
        assertTrue(exception.getMessage().contains("optimizer gate blocked=true source=autoVectorization family=guard.earlyExitBoundary"));
        assertTrue(exception.getMessage().contains("optimizerDiagnostics=1"));
        assertTrue(exception.getMessage().contains("rewritePlanGuardFamilies={earlyExitBoundary=1}"));
        assertTrue(exception.getMessage().contains("next statement stmt[1] is an early-exit boundary"));
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

    private GpuIrPassContext context(GpuIrCompiledMethod method, List<GpuIrCompiledMethod> helperMethods) {
        return new GpuIrPassContext(method, helperMethods, List.of(), true);
    }

    private void assertSafetyGateFamily(
            GpuIrCompiledMethod kernel,
            List<GpuIrCompiledMethod> helperMethods,
            String expectedFamily,
            String expectedMessage
    ) {
        GpuIrOptimizationValidationReport report = pipeline.validate(context(kernel, helperMethods));

        assertTrue(report.hasSafetyError());
        assertEquals("safety", report.optimizerGateExplanation().source());
        assertEquals(expectedFamily, report.optimizerGateExplanation().family());
        assertEquals(java.util.Map.of("safety", 1L), report.optimizerGateSourceCounts());
        assertEquals(java.util.Map.of(expectedFamily, 1L), report.optimizerGateFamilyCounts());
        assertTrue(report.safetyError().orElseThrow().contains(expectedMessage));
        assertTrue(report.compactSummary().contains("optimizerGateFamily=" + expectedFamily));
        assertTrue(report.compactSummary().contains("optimizerGateFamilyCounts={" + expectedFamily + "=1}"));
    }

    private GpuIrCompiledMethod method(GpuIrMethod irMethod) {
        return method(irMethod, List.of());
    }

    private GpuIrCompiledMethod method(GpuIrMethod irMethod, List<String> helperDependencies) {
        return method(irMethod, helperDependencies, List.of(
                new ParsedGpuParameter("x", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                new ParsedGpuParameter("y", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                new ParsedGpuParameter("z", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                new ParsedGpuParameter("left", "int[]", GpuAddressSpace.GLOBAL, false, List.of()),
                new ParsedGpuParameter("out", "int[]", GpuAddressSpace.GLOBAL, false, List.of())
        ));
    }

    private GpuIrCompiledMethod method(
            GpuIrMethod irMethod,
            List<String> helperDependencies,
            List<ParsedGpuParameter> parameters
    ) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                irMethod.name(),
                "void",
                parameters,
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
        return new GpuIrCompiledMethod(parsedMethod, irMethod, "jtg_kernel", helperDependencies);
    }

    private GpuIrCompiledMethod helperMethod() {
        return helperMethod("writeHelper", "jtg_write_helper", List.of(
                new ParsedGpuParameter("target", "int[]", GpuAddressSpace.GLOBAL, false, List.of())
        ));
    }

    private GpuIrCompiledMethod helperMethod(String methodName, String emittedName, List<ParsedGpuParameter> parameters) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                methodName,
                "void",
                parameters,
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
        return new GpuIrCompiledMethod(parsedMethod, new GpuIrMethod(methodName, List.of(new GpuIrReturn(null))), emittedName, List.of());
    }
}
