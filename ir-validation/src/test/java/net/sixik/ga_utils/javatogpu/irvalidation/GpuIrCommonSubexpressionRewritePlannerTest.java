package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionRewritePlannerTest {
    private final GpuIrCommonSubexpressionRewritePlanner planner = new GpuIrCommonSubexpressionRewritePlanner();

    @Test
    void plansOnlyRewriteReadyStableCandidatesWithoutMutatingIr() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x"))),
                new GpuIrVariableDeclaration("int", "unstableA", new GpuIrBinary("*", new GpuIrVariableRef("z"), new GpuIrLiteral("2"))),
                new GpuIrAssignment(new GpuIrVariableRef("z"), new GpuIrLiteral("7")),
                new GpuIrVariableDeclaration("int", "unstableB", new GpuIrBinary("*", new GpuIrVariableRef("z"), new GpuIrLiteral("2")))
        ));
        GpuIrCommonSubexpressionReport report = GpuIrCommonSubexpressionScanner.optimizerFocused().scan(method);

        List<GpuIrCommonSubexpressionRewritePlan> plans = planner.plan(method, report);

        assertEquals(1, plans.size());
        GpuIrCommonSubexpressionRewritePlan plan = plans.getFirst();
        assertEquals("__gpu_cse_0", plan.temporaryName());
        assertTrue(plan.fingerprint().startsWith("binary(+"));
        assertEquals(1, plan.estimatedReuseSavings());
        assertEquals(0, plan.insertionStatementIndex());
        assertEquals("stmt[0].initializer", plan.insertionAnchorLocation());
        assertEquals(List.of("stmt[0].initializer", "stmt[1].initializer"), plan.replacementLocations());
        assertEquals(List.of("stmt[1].initializer"), plan.replacementLocationsAfterAnchor());
        assertEquals(1, plan.replacementCountAfterAnchor());
        assertTrue(plan.hasReplacementLocationsAfterAnchor());
        assertEquals(List.of(new GpuIrCommonSubexpressionRewriteEdit(
                "__gpu_cse_0",
                plan.fingerprint(),
                0,
                "stmt[0].initializer",
                "stmt[1].initializer"
        )), plan.previewReplacementEdits());
        assertEquals(new GpuIrCommonSubexpressionRewriteInsertion(
                "__gpu_cse_0",
                plan.fingerprint(),
                0,
                "stmt[0].initializer"
        ), plan.previewInsertion());
    }

    @Test
    void rewritePlansRequireAnExplicitAnchorOccurrence() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionRewritePlan(
                "__gpu_cse_0",
                "binary(+,var(x),var(y))",
                1,
                0,
                "stmt[0].initializer",
                List.of("stmt[1].initializer")
        ));
    }

    @Test
    void avoidsTemporaryNameCollisionsWithExistingLocals() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "__gpu_cse_0", new GpuIrLiteral("0")),
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")))
        ));
        GpuIrCommonSubexpressionReport report = GpuIrCommonSubexpressionScanner.optimizerFocused().scan(method);

        List<GpuIrCommonSubexpressionRewritePlan> plans = planner.plan(method, report);

        assertEquals(1, plans.size());
        assertEquals("__gpu_cse_1", plans.getFirst().temporaryName());
    }

    @Test
    void avoidsTemporaryNameCollisionsWithCompiledMethodParameters() {
        GpuIrMethod irMethod = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrAssignment(new GpuIrVariableRef("outB"), new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")))
        ));
        GpuIrCompiledMethod method = compiledMethod(irMethod, List.of(
                new ParsedGpuParameter("x", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                new ParsedGpuParameter("y", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                new ParsedGpuParameter("__gpu_cse_0", "int", GpuAddressSpace.PRIVATE, false, List.of())
        ));
        GpuIrCommonSubexpressionReport report = GpuIrCommonSubexpressionScanner.optimizerFocused().scan(irMethod);

        List<GpuIrCommonSubexpressionRewritePlan> plans = planner.plan(method, report);

        assertEquals(1, plans.size());
        assertEquals("__gpu_cse_1", plans.getFirst().temporaryName());
    }

    @Test
    void rewritePlansRequireAtLeastOnePostAnchorReplacement() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionRewritePlan(
                "__gpu_cse_0",
                "binary(+,var(x),var(y))",
                0,
                0,
                "stmt[0].initializer",
                List.of("stmt[0].initializer")
        ));
    }

    @Test
    void rewriteEditsRequireTopLevelReplacementLocationsAfterAnchor() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionRewriteEdit(
                "__gpu_cse_0",
                "binary(+,var(x),var(y))",
                2,
                "stmt[2].initializer",
                "stmt[1].initializer"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionRewriteEdit(
                "__gpu_cse_0",
                "binary(+,var(x),var(y))",
                0,
                "stmt[0].initializer",
                "helper[0].initializer"
        ));
    }

    @Test
    void planReportExplainsSkippedCandidates() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "stableA", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrVariableDeclaration("int", "stableB", new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x"))),
                new GpuIrVariableDeclaration("int", "unstableA", new GpuIrBinary("*", new GpuIrVariableRef("z"), new GpuIrLiteral("2"))),
                new GpuIrAssignment(new GpuIrVariableRef("z"), new GpuIrLiteral("7")),
                new GpuIrVariableDeclaration("int", "unstableB", new GpuIrBinary("*", new GpuIrVariableRef("z"), new GpuIrLiteral("2")))
        ));
        GpuIrCommonSubexpression helper = new GpuIrCommonSubexpression("helper(noise,int,var(x))", 2, List.of("stmt[0].initializer", "stmt[1].initializer"));
        GpuIrCommonSubexpression branch = new GpuIrCommonSubexpression("binary(-,var(x),var(y))", 2, List.of("stmt[0].then.stmt[0].initializer", "stmt[0].else.stmt[0].initializer"));
        GpuIrCommonSubexpression noDominance = new GpuIrCommonSubexpression("binary(/,var(a),var(b))", 2, List.of("stmt[5].initializer", "stmt[1].initializer"));
        GpuIrCommonSubexpressionReport report = new GpuIrCommonSubexpressionReport("kernel", List.of(
                new GpuIrCommonSubexpression("binary(+,var(x),var(y))", 2, List.of("stmt[0].initializer", "stmt[1].initializer")),
                new GpuIrCommonSubexpression("binary(*,var(z),literal(2))", 2, List.of("stmt[2].initializer", "stmt[4].initializer")),
                helper,
                branch,
                noDominance
        ));

        GpuIrCommonSubexpressionRewritePlanReport planReport = planner.planReport(method, report);

        assertTrue(planReport.hasPlans());
        assertEquals(1, planReport.plans().size());
        assertTrue(planReport.hasSkippedCandidates());
        assertEquals(1, planReport.insertionCount());
        assertEquals(1, planReport.replacementEditCount());
        assertEquals(4, planReport.skippedCandidateCount());
        assertEquals(1, planReport.previewInsertions().size());
        assertEquals("stmt[0].initializer", planReport.previewInsertions().getFirst().anchorLocation());
        assertEquals(0, planReport.previewInsertions().getFirst().anchor().topLevelStatementIndex().orElseThrow());
        assertEquals(1, planReport.previewReplacementEdits().size());
        assertEquals("stmt[1].initializer", planReport.previewReplacementEdits().getFirst().replacementLocation());
        assertEquals(0, planReport.previewReplacementEdits().getFirst().insertionAnchor().topLevelStatementIndex().orElseThrow());
        assertEquals(1, planReport.previewReplacementEdits().getFirst().replacement().topLevelStatementIndex().orElseThrow());
        GpuIrCommonSubexpressionRewritePreview preview = planReport.preview();
        assertTrue(preview.hasRewriteWork());
        assertEquals(1, preview.insertionCount());
        assertEquals(1, preview.replacementEditCount());
        assertEquals(4, preview.skippedCandidateCount());
        assertEquals(4, preview.skippedDiagnostics().size());
        GpuIrCommonSubexpressionSkippedDiagnostic firstDiagnostic = preview.skippedDiagnostics().getFirst();
        assertEquals("binary(*,var(z),literal(2))", firstDiagnostic.fingerprint());
        assertEquals(2, firstDiagnostic.occurrenceCount());
        assertEquals(List.of("stmt[2].initializer", "stmt[4].initializer"), firstDiagnostic.locations());
        assertEquals(GpuIrCommonSubexpressionKind.LOCAL_REUSE, firstDiagnostic.kind());
        assertEquals(GpuIrCommonSubexpressionScope.STRAIGHT_LINE, firstDiagnostic.scope());
        assertEquals(GpuIrCommonSubexpressionSkipReason.MUTATED_BETWEEN_OCCURRENCES, firstDiagnostic.reason());
        assertTrue(firstDiagnostic.summary().contains("MUTATED_BETWEEN_OCCURRENCES"));
        assertEquals(1, preview.skippedDiagnosticsByReason()
                .get(GpuIrCommonSubexpressionSkipReason.MUTATED_BETWEEN_OCCURRENCES)
                .size());
        assertEquals(1L, preview.skippedReasonCounts()
                .get(GpuIrCommonSubexpressionSkipReason.MUTATED_BETWEEN_OCCURRENCES));
        assertEquals(1, planReport.previewSkippedDiagnosticsByReason()
                .get(GpuIrCommonSubexpressionSkipReason.NOT_LOCAL_REUSE)
                .size());
        assertEquals(1L, planReport.skippedReasonCounts()
                .get(GpuIrCommonSubexpressionSkipReason.CONTROL_FLOW_BOUNDARY));
        assertEquals(List.of(
                GpuIrCommonSubexpressionSkipReason.MUTATED_BETWEEN_OCCURRENCES,
                GpuIrCommonSubexpressionSkipReason.NOT_LOCAL_REUSE,
                GpuIrCommonSubexpressionSkipReason.CONTROL_FLOW_BOUNDARY,
                GpuIrCommonSubexpressionSkipReason.NO_DOMINATING_FIRST_OCCURRENCE
        ), planReport.skippedCandidates().stream().map(GpuIrCommonSubexpressionSkippedCandidate::reason).toList());
    }

    @Test
    void skipsNestedCandidatesCoveredByPlannedParentRewrite() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("+",
                        new GpuIrBinary("*", new GpuIrVariableRef("x"), new GpuIrVariableRef("y")),
                        new GpuIrLiteral("1"))),
                new GpuIrAssignment(new GpuIrVariableRef("outB"), new GpuIrBinary("+",
                        new GpuIrBinary("*", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")),
                        new GpuIrLiteral("1")))
        ));
        GpuIrCommonSubexpressionReport report = new GpuIrCommonSubexpressionReport("kernel", List.of(
                new GpuIrCommonSubexpression(
                        "binary(+,binary(*,var(x),var(y)),literal(1))",
                        2,
                        List.of("stmt[0].value", "stmt[1].value")
                ),
                new GpuIrCommonSubexpression(
                        "binary(*,var(x),var(y))",
                        2,
                        List.of("stmt[0].value.left", "stmt[1].value.left")
                )
        ));

        GpuIrCommonSubexpressionRewritePlanReport planReport = planner.planReport(method, report);

        assertEquals(1, planReport.plans().size());
        assertEquals("stmt[0].value", planReport.plans().getFirst().insertionAnchorLocation());
        assertEquals(List.of("stmt[1].value"), planReport.plans().getFirst().replacementLocationsAfterAnchor());
        assertEquals(1, planReport.skippedCandidateCount());
        assertEquals(
                GpuIrCommonSubexpressionSkipReason.COVERED_BY_PARENT_REWRITE,
                planReport.skippedCandidates().getFirst().reason()
        );
    }

    private GpuIrCompiledMethod compiledMethod(GpuIrMethod irMethod, List<ParsedGpuParameter> parameters) {
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
        return new GpuIrCompiledMethod(parsedMethod, irMethod, "jtg_kernel", List.of());
    }
}
