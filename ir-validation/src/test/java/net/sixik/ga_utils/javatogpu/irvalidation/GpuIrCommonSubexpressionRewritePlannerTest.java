package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(List.of("stmt[0].initializer", "stmt[1].initializer"), plan.replacementLocations());
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
        GpuIrCommonSubexpressionReport report = new GpuIrCommonSubexpressionReport("kernel", List.of(
                new GpuIrCommonSubexpression("binary(+,var(x),var(y))", 2, List.of("stmt[0].initializer", "stmt[1].initializer")),
                new GpuIrCommonSubexpression("binary(*,var(z),literal(2))", 2, List.of("stmt[2].initializer", "stmt[4].initializer")),
                helper,
                branch
        ));

        GpuIrCommonSubexpressionRewritePlanReport planReport = planner.planReport(method, report);

        assertTrue(planReport.hasPlans());
        assertEquals(1, planReport.plans().size());
        assertEquals(List.of(
                GpuIrCommonSubexpressionSkipReason.MUTATED_BETWEEN_OCCURRENCES,
                GpuIrCommonSubexpressionSkipReason.NOT_LOCAL_REUSE,
                GpuIrCommonSubexpressionSkipReason.CONTROL_FLOW_BOUNDARY
        ), planReport.skippedCandidates().stream().map(GpuIrCommonSubexpressionSkippedCandidate::reason).toList());
    }
}
