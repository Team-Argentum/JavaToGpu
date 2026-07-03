package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCanonicalExpressionFingerprintTest {
    private final GpuIrCanonicalExpressionFingerprint fingerprint = new GpuIrCanonicalExpressionFingerprint();

    @Test
    void commutativeExpressionsShareCanonicalFingerprint() {
        GpuIrBinary left = new GpuIrBinary("+", new GpuIrVariableRef("a"), new GpuIrVariableRef("b"));
        GpuIrBinary right = new GpuIrBinary("+", new GpuIrVariableRef("b"), new GpuIrVariableRef("a"));

        assertEquals(fingerprint.fingerprint(left), fingerprint.fingerprint(right));
    }

    @Test
    void nonCommutativeExpressionsKeepOperandOrder() {
        GpuIrBinary left = new GpuIrBinary("-", new GpuIrVariableRef("a"), new GpuIrVariableRef("b"));
        GpuIrBinary right = new GpuIrBinary("-", new GpuIrVariableRef("b"), new GpuIrVariableRef("a"));

        assertNotEquals(fingerprint.fingerprint(left), fingerprint.fingerprint(right));
    }

    @Test
    void sideEffectingExpressionsDoNotProduceCanonicalFingerprints() {
        GpuIrIntrinsicCall barrier = new GpuIrIntrinsicCall(null, "barrier", "barrier(1)", "void", List.of());

        assertTrue(fingerprint.fingerprint(barrier).isEmpty());
    }
}
