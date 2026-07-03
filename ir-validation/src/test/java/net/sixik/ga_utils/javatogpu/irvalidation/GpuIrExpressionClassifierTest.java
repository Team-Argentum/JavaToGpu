package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrExpressionClassifierTest {
    private final GpuIrExpressionClassifier classifier = new GpuIrExpressionClassifier();

    @Test
    void classifiesPlainArithmeticAsPure() {
        GpuIrBinary expression = new GpuIrBinary("+", new GpuIrLiteral("1"), new GpuIrLiteral("2"));

        assertEquals(GpuIrExpressionEffect.PURE, classifier.effectOf(expression));
        assertFalse(classifier.mayHaveSideEffects(expression));
    }

    @Test
    void classifiesHelpersAndSideEffectIntrinsicsAsSideEffecting() {
        GpuIrHelperCall helperCall = new GpuIrHelperCall("jtg_helper", "void", List.of());
        GpuIrIntrinsicCall barrier = new GpuIrIntrinsicCall(null, "barrier", "barrier(1)", "void", List.of());

        assertEquals(GpuIrExpressionEffect.SIDE_EFFECTING, classifier.effectOf(helperCall));
        assertEquals(GpuIrExpressionEffect.SIDE_EFFECTING, classifier.effectOf(barrier));
        assertTrue(classifier.mayHaveSideEffects(helperCall));
        assertTrue(classifier.mayHaveSideEffects(barrier));
    }

    @Test
    void propagatesNestedSideEffects() {
        GpuIrTernary expression = new GpuIrTernary(
                new GpuIrLiteral("1"),
                new GpuIrLiteral("2"),
                new GpuIrIntrinsicCall(null, "atomic_add", "atomic_add(&x, 1)", "int", List.of())
        );

        assertEquals(GpuIrExpressionEffect.SIDE_EFFECTING, classifier.effectOf(expression));
    }
}
