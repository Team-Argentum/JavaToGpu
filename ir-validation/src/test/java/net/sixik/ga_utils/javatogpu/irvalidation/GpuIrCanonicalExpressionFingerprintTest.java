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
    void associativeBitwiseExpressionsShareCanonicalFingerprint() {
        GpuIrBinary left = new GpuIrBinary("&",
                new GpuIrVariableRef("a"),
                new GpuIrBinary("&", new GpuIrVariableRef("b"), new GpuIrVariableRef("c"))
        );
        GpuIrBinary right = new GpuIrBinary("&",
                new GpuIrBinary("&", new GpuIrVariableRef("c"), new GpuIrVariableRef("a")),
                new GpuIrVariableRef("b")
        );

        assertEquals(fingerprint.fingerprint(left), fingerprint.fingerprint(right));
    }

    @Test
    void simpleAssociativeArithmeticExpressionsShareCanonicalFingerprint() {
        GpuIrBinary left = new GpuIrBinary("+",
                new GpuIrVariableRef("a"),
                new GpuIrBinary("+", new GpuIrVariableRef("b"), new GpuIrVariableRef("c"))
        );
        GpuIrBinary right = new GpuIrBinary("+",
                new GpuIrBinary("+", new GpuIrVariableRef("c"), new GpuIrVariableRef("a")),
                new GpuIrVariableRef("b")
        );

        assertEquals(fingerprint.fingerprint(left), fingerprint.fingerprint(right));
        assertTrue(fingerprint.fingerprint(left).orElseThrow().startsWith("binary_assoc_simple(+"));
    }

    @Test
    void simpleAssociativeMultiplicationExpressionsShareCanonicalFingerprint() {
        GpuIrBinary left = new GpuIrBinary("*",
                new GpuIrVariableRef("a"),
                new GpuIrBinary("*", new GpuIrVariableRef("b"), new GpuIrVariableRef("c"))
        );
        GpuIrBinary right = new GpuIrBinary("*",
                new GpuIrBinary("*", new GpuIrVariableRef("c"), new GpuIrVariableRef("a")),
                new GpuIrVariableRef("b")
        );

        assertEquals(fingerprint.fingerprint(left), fingerprint.fingerprint(right));
        assertTrue(fingerprint.fingerprint(left).orElseThrow().startsWith("binary_assoc_simple(*"));
    }

    @Test
    void arithmeticAssociativityRefusesLiteralOperandsUntilNumericSemanticsAreProven() {
        GpuIrBinary left = new GpuIrBinary("+",
                new GpuIrVariableRef("a"),
                new GpuIrBinary("+", new GpuIrVariableRef("b"), new GpuIrVariableRef("c"))
        );
        GpuIrBinary right = new GpuIrBinary("+",
                new GpuIrBinary("+", new GpuIrVariableRef("c"), new net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral("1")),
                new GpuIrVariableRef("b")
        );

        assertNotEquals(fingerprint.fingerprint(left), fingerprint.fingerprint(right));
    }

    @Test
    void multiplicationAssociativityRefusesLiteralOperandsUntilNumericSemanticsAreProven() {
        GpuIrBinary left = new GpuIrBinary("*",
                new GpuIrVariableRef("a"),
                new GpuIrBinary("*", new GpuIrVariableRef("b"), new GpuIrVariableRef("c"))
        );
        GpuIrBinary right = new GpuIrBinary("*",
                new GpuIrBinary("*", new GpuIrVariableRef("c"), new net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral("2")),
                new GpuIrVariableRef("b")
        );

        assertNotEquals(fingerprint.fingerprint(left), fingerprint.fingerprint(right));
    }

    @Test
    void mixedBitwiseOperatorsKeepNestedShape() {
        GpuIrBinary left = new GpuIrBinary("&",
                new GpuIrVariableRef("a"),
                new GpuIrBinary("|", new GpuIrVariableRef("b"), new GpuIrVariableRef("c"))
        );
        GpuIrBinary right = new GpuIrBinary("&",
                new GpuIrBinary("|", new GpuIrVariableRef("c"), new GpuIrVariableRef("a")),
                new GpuIrVariableRef("b")
        );

        assertNotEquals(fingerprint.fingerprint(left), fingerprint.fingerprint(right));
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
