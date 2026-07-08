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

class GpuIrCommonSubexpressionReportTest {
    @Test
    void topCandidatesExcludeLeafNoiseAndSortByEstimatedSavings() {
        GpuIrCommonSubexpression leaf = new GpuIrCommonSubexpression("var(x)", 10, List.of("a", "b"));
        GpuIrCommonSubexpression small = new GpuIrCommonSubexpression("binary(+,var(a),var(b))", 2, List.of("c", "d"));
        GpuIrCommonSubexpression large = new GpuIrCommonSubexpression("binary(*,var(a),var(b))", 4, List.of("e", "f", "g", "h"));
        GpuIrCommonSubexpressionReport report = new GpuIrCommonSubexpressionReport("kernel", List.of(leaf, small, large));

        assertEquals(List.of(large, small), report.topCandidates());
    }

    @Test
    void rewriteReadyCandidatesKeepOnlyLocalReuseCandidates() {
        GpuIrCommonSubexpression helper = new GpuIrCommonSubexpression("helper(noise,int,var(x))", 4, List.of("a", "b", "c", "d"));
        GpuIrCommonSubexpression intrinsic = new GpuIrCommonSubexpression("intrinsic(native,tpl,int,receiver(null),var(x))", 3, List.of("e", "f", "g"));
        GpuIrCommonSubexpression local = new GpuIrCommonSubexpression("binary(+,var(a),var(b))", 2, List.of("h", "i"));
        GpuIrCommonSubexpression leaf = new GpuIrCommonSubexpression("literal(1)", 9, List.of("j", "k"));
        GpuIrCommonSubexpressionReport report = new GpuIrCommonSubexpressionReport("kernel", List.of(helper, intrinsic, local, leaf));

        assertEquals(List.of(local), report.rewriteReadyCandidates());
    }

    @Test
    void rewriteReadyCandidatesExcludeControlFlowBoundaryCandidates() {
        GpuIrCommonSubexpression branchCandidate = new GpuIrCommonSubexpression(
                "binary(+,var(a),var(b))",
                2,
                List.of("stmt[0].then.stmt[0].initializer", "stmt[0].else.stmt[0].initializer")
        );
        GpuIrCommonSubexpression straightLineCandidate = new GpuIrCommonSubexpression(
                "binary(*,var(a),var(b))",
                2,
                List.of("stmt[1].initializer", "stmt[2].initializer")
        );
        GpuIrCommonSubexpressionReport report = new GpuIrCommonSubexpressionReport(
                "kernel",
                List.of(branchCandidate, straightLineCandidate)
        );

        assertEquals(List.of(straightLineCandidate), report.rewriteReadyCandidates());
    }

    @Test
    void rewriteReadyCandidatesWithMethodExcludeCandidatesWithInterveningOperandMutation() {
        GpuIrCommonSubexpression unstable = new GpuIrCommonSubexpression(
                "binary(+,var(x),literal(1))",
                2,
                List.of("stmt[0].initializer", "stmt[2].initializer")
        );
        GpuIrCommonSubexpression stable = new GpuIrCommonSubexpression(
                "binary(+,var(y),literal(1))",
                2,
                List.of("stmt[0].initializer", "stmt[2].initializer")
        );
        GpuIrCommonSubexpressionReport report = new GpuIrCommonSubexpressionReport("kernel", List.of(unstable, stable));
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "a", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1"))),
                new GpuIrAssignment(new GpuIrVariableRef("x"), new GpuIrLiteral("2")),
                new GpuIrVariableDeclaration("int", "b", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1")))
        ));

        assertEquals(List.of(stable), report.rewriteReadyCandidates(method));
    }

    @Test
    void rewriteReadyCandidatesWithMethodIncludeProvenSameStatementOccurrences() {
        GpuIrCommonSubexpression sameStatementOnly = new GpuIrCommonSubexpression(
                "binary(*,var(x),var(y))",
                2,
                List.of("stmt[0].value.left", "stmt[0].value.right")
        );
        GpuIrCommonSubexpression laterStatement = new GpuIrCommonSubexpression(
                "binary(+,var(x),var(y))",
                2,
                List.of("stmt[0].value.left", "stmt[1].value")
        );
        GpuIrCommonSubexpressionReport report = new GpuIrCommonSubexpressionReport(
                "kernel",
                List.of(sameStatementOnly, laterStatement)
        );
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrAssignment(new GpuIrVariableRef("outB"), new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y")))
        ));

        assertEquals(List.of(sameStatementOnly, laterStatement), report.rewriteReadyCandidates(method));
    }
}
