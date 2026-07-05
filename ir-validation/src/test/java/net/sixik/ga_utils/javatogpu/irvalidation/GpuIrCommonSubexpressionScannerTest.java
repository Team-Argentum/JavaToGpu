package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrExpressionStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionScannerTest {
    private final GpuIrCommonSubexpressionScanner scanner = new GpuIrCommonSubexpressionScanner();

    @Test
    void reportsRepeatedPureExpressionsWithoutRewritingIr() {
        GpuIrBinary expression = new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1"));
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "a", expression),
                new GpuIrVariableDeclaration("int", "b", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1")))
        ));

        GpuIrCommonSubexpressionReport report = scanner.scan(method);

        assertTrue(report.hasCandidates());
        assertEquals("kernel", report.methodName());
        assertTrue(report.candidates().stream().anyMatch(candidate ->
                candidate.fingerprint().startsWith("binary(+")
                        && candidate.occurrenceCount() == 2
                        && candidate.locations().contains("stmt[0].initializer")
                        && candidate.locations().contains("stmt[1].initializer")
        ));
    }

    @Test
    void ignoresSideEffectingExpressionsAsReusableCandidates() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrExpressionStatement(new GpuIrIntrinsicCall(null, "barrier", "barrier(1)", "void", List.of())),
                new GpuIrExpressionStatement(new GpuIrIntrinsicCall(null, "barrier", "barrier(1)", "void", List.of()))
        ));

        GpuIrCommonSubexpressionReport report = scanner.scan(method);

        assertFalse(report.hasCandidates());
    }

    @Test
    void canonicalModeReportsCommutedPureExpressionsAsSameCandidate() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "a", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrVariableDeclaration("int", "b", new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")))
        ));

        GpuIrCommonSubexpressionReport defaultReport = scanner.scan(method);
        GpuIrCommonSubexpressionReport canonicalReport = GpuIrCommonSubexpressionScanner.canonical().scan(method);

        assertFalse(hasBinaryCandidate(defaultReport));
        assertTrue(hasBinaryCandidate(canonicalReport));
    }

    @Test
    void canonicalModeReportsAssociativeBitwiseExpressionsAsSameCandidate() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "a", new GpuIrBinary("^",
                        new GpuIrVariableRef("x"),
                        new GpuIrBinary("^", new GpuIrVariableRef("y"), new GpuIrVariableRef("z"))
                )),
                new GpuIrVariableDeclaration("int", "b", new GpuIrBinary("^",
                        new GpuIrBinary("^", new GpuIrVariableRef("z"), new GpuIrVariableRef("x")),
                        new GpuIrVariableRef("y")
                ))
        ));

        GpuIrCommonSubexpressionReport canonicalReport = GpuIrCommonSubexpressionScanner.canonical().scan(method);

        assertTrue(canonicalReport.candidates().stream().anyMatch(candidate ->
                candidate.fingerprint().startsWith("binary_assoc(^")
                        && candidate.occurrenceCount() == 2
                        && candidate.locations().contains("stmt[0].initializer")
                        && candidate.locations().contains("stmt[1].initializer")
        ));
    }

    @Test
    void canonicalModeReportsNestedSimpleArithmeticExpressionsAsSameCandidate() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "a", new GpuIrBinary("+",
                        new GpuIrVariableRef("x"),
                        new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("z"))
                )),
                new GpuIrVariableDeclaration("int", "b", new GpuIrBinary("+",
                        new GpuIrBinary("+", new GpuIrVariableRef("z"), new GpuIrVariableRef("x")),
                        new GpuIrVariableRef("y")
                ))
        ));

        GpuIrCommonSubexpressionReport canonicalReport = GpuIrCommonSubexpressionScanner.canonical().scan(method);

        assertTrue(canonicalReport.candidates().stream().anyMatch(candidate ->
                candidate.fingerprint().startsWith("binary_assoc_simple(+")
                        && candidate.occurrenceCount() == 2
                        && candidate.locations().contains("stmt[0].initializer")
                        && candidate.locations().contains("stmt[1].initializer")
        ));
    }

    @Test
    void canonicalModeReportsNestedSimpleMultiplicationExpressionsAsSameCandidate() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "a", new GpuIrBinary("*",
                        new GpuIrVariableRef("x"),
                        new GpuIrBinary("*", new GpuIrVariableRef("y"), new GpuIrVariableRef("z"))
                )),
                new GpuIrVariableDeclaration("int", "b", new GpuIrBinary("*",
                        new GpuIrBinary("*", new GpuIrVariableRef("z"), new GpuIrVariableRef("x")),
                        new GpuIrVariableRef("y")
                ))
        ));

        GpuIrCommonSubexpressionReport canonicalReport = GpuIrCommonSubexpressionScanner.canonical().scan(method);

        assertTrue(canonicalReport.candidates().stream().anyMatch(candidate ->
                candidate.fingerprint().startsWith("binary_assoc_simple(*")
                        && candidate.occurrenceCount() == 2
                        && candidate.locations().contains("stmt[0].initializer")
                        && candidate.locations().contains("stmt[1].initializer")
        ));
    }

    @Test
    void optimizerFocusedModeSkipsRepeatedLeafNoiseButKeepsUsefulExpressions() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "a", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1"))),
                new GpuIrVariableDeclaration("int", "b", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1")))
        ));

        GpuIrCommonSubexpressionReport report = GpuIrCommonSubexpressionScanner.optimizerFocused().scan(method);

        assertTrue(hasBinaryCandidate(report));
        assertFalse(report.candidates().stream().anyMatch(candidate -> candidate.fingerprint().startsWith("var(")));
        assertFalse(report.candidates().stream().anyMatch(candidate -> candidate.fingerprint().startsWith("literal(")));
    }

    @Test
    void customOptionsCanRaiseMinimumOccurrenceCount() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "a", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1"))),
                new GpuIrVariableDeclaration("int", "b", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1")))
        ));
        GpuIrCommonSubexpressionScanner strictScanner = new GpuIrCommonSubexpressionScanner(
                new GpuIrExpressionFingerprint(),
                new GpuIrCommonSubexpressionScannerOptions(3, true)
        );

        GpuIrCommonSubexpressionReport report = strictScanner.scan(method);

        assertFalse(report.hasCandidates());
    }

    private boolean hasBinaryCandidate(GpuIrCommonSubexpressionReport report) {
        return report.candidates().stream().anyMatch(candidate ->
                candidate.fingerprint().startsWith("binary(+")
                        && candidate.occurrenceCount() == 2
                        && candidate.locations().contains("stmt[0].initializer")
                        && candidate.locations().contains("stmt[1].initializer")
        );
    }
}
