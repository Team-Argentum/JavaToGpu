package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionMutationGuardTest {
    private final GpuIrCommonSubexpressionMutationGuard guard = new GpuIrCommonSubexpressionMutationGuard();

    @Test
    void rejectsCandidateWhenReferencedVariableIsAssignedBetweenOccurrences() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "a", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1"))),
                new GpuIrAssignment(new GpuIrVariableRef("x"), new GpuIrLiteral("2")),
                new GpuIrVariableDeclaration("int", "b", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1")))
        ));
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "binary(+,var(x),literal(1))",
                2,
                List.of("stmt[0].initializer", "stmt[2].initializer")
        );

        assertFalse(guard.isStableBetweenOccurrences(method, candidate));
    }

    @Test
    void acceptsCandidateWhenOnlyUnrelatedVariablesAreAssignedBetweenOccurrences() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "a", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1"))),
                new GpuIrAssignment(new GpuIrVariableRef("y"), new GpuIrLiteral("2")),
                new GpuIrVariableDeclaration("int", "b", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1")))
        ));
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "binary(+,var(x),literal(1))",
                2,
                List.of("stmt[0].initializer", "stmt[2].initializer")
        );

        assertTrue(guard.isStableBetweenOccurrences(method, candidate));
    }

    @Test
    void rejectsCandidateWhenReferencedArrayIsAssignedBetweenOccurrences() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "a", new GpuIrArrayAccess("values", new GpuIrLiteral("0"))),
                new GpuIrAssignment(new GpuIrArrayAccess("values", new GpuIrLiteral("0")), new GpuIrLiteral("2")),
                new GpuIrVariableDeclaration("int", "b", new GpuIrArrayAccess("values", new GpuIrLiteral("0")))
        ));
        GpuIrCommonSubexpression candidate = new GpuIrCommonSubexpression(
                "array(values,literal(0))",
                2,
                List.of("stmt[0].initializer", "stmt[2].initializer")
        );

        assertFalse(guard.isStableBetweenOccurrences(method, candidate));
    }
}
