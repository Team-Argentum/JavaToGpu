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

class GpuIrCommonSubexpressionPipelineTest {
    @Test
    void optimizerFocusedPipelineKeepsOnlyCanonicalStableStraightLineCandidates() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrVariableDeclaration("int", "noise", new GpuIrVariableRef("x")),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x"))),
                new GpuIrVariableDeclaration("int", "unstableA", new GpuIrBinary("*", new GpuIrVariableRef("z"), new GpuIrLiteral("2"))),
                new GpuIrAssignment(new GpuIrVariableRef("z"), new GpuIrLiteral("7")),
                new GpuIrVariableDeclaration("int", "unstableB", new GpuIrBinary("*", new GpuIrVariableRef("z"), new GpuIrLiteral("2")))
        ));

        GpuIrCommonSubexpressionReport report = GpuIrCommonSubexpressionScanner.optimizerFocused().scan(method);
        List<GpuIrCommonSubexpression> rewriteReady = report.rewriteReadyCandidates(method);

        assertEquals(1, rewriteReady.size());
        GpuIrCommonSubexpression candidate = rewriteReady.get(0);
        assertTrue(candidate.fingerprint().startsWith("binary(+"));
        assertEquals(2, candidate.occurrenceCount());
        assertEquals(List.of("stmt[0].initializer", "stmt[2].initializer"), candidate.locations());
    }
}
