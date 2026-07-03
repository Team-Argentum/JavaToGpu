package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrExpressionFingerprintTest {
    private final GpuIrExpressionFingerprint fingerprint = new GpuIrExpressionFingerprint();

    @Test
    void identicalPureExpressionsHaveIdenticalFingerprints() {
        GpuIrBinary left = new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1"));
        GpuIrBinary right = new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1"));

        assertEquals(fingerprint.fingerprint(left), fingerprint.fingerprint(right));
        assertTrue(fingerprint.fingerprint(left).orElseThrow().startsWith("binary(+"));
    }

    @Test
    void differentPureExpressionsHaveDifferentFingerprints() {
        GpuIrBinary left = new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1"));
        GpuIrBinary right = new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrLiteral("1"));

        assertNotEquals(fingerprint.fingerprint(left), fingerprint.fingerprint(right));
    }

    @Test
    void sideEffectingExpressionsDoNotProduceReusableFingerprints() {
        GpuIrIntrinsicCall barrier = new GpuIrIntrinsicCall(null, "barrier", "barrier(1)", "void", List.of());

        assertTrue(fingerprint.fingerprint(barrier).isEmpty());
    }
}
