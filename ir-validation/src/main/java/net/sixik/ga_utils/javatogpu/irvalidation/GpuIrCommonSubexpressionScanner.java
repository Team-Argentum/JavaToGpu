package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrFieldAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrStructInit;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrDoWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrExpressionStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrPrivateArrayDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitchCase;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Comparator;
import java.util.function.Function;

/**
 * Walks lowered IR and reports repeated pure expression fingerprints without rewriting the tree.
 */
public final class GpuIrCommonSubexpressionScanner {
    private final Function<GpuIrExpression, Optional<String>> fingerprint;
    private final GpuIrCommonSubexpressionScannerOptions options;

    public GpuIrCommonSubexpressionScanner() {
        this(new GpuIrExpressionFingerprint());
    }

    public GpuIrCommonSubexpressionScanner(GpuIrExpressionFingerprint fingerprint) {
        this(fingerprint, GpuIrCommonSubexpressionScannerOptions.diagnosticDefaults());
    }

    public GpuIrCommonSubexpressionScanner(
            GpuIrExpressionFingerprint fingerprint,
            GpuIrCommonSubexpressionScannerOptions options
    ) {
        this(fingerprint::fingerprint, options);
    }

    public GpuIrCommonSubexpressionScanner(GpuIrCanonicalExpressionFingerprint fingerprint) {
        this(fingerprint, GpuIrCommonSubexpressionScannerOptions.diagnosticDefaults());
    }

    public GpuIrCommonSubexpressionScanner(
            GpuIrCanonicalExpressionFingerprint fingerprint,
            GpuIrCommonSubexpressionScannerOptions options
    ) {
        this(fingerprint::fingerprint, options);
    }

    private GpuIrCommonSubexpressionScanner(
            Function<GpuIrExpression, Optional<String>> fingerprint,
            GpuIrCommonSubexpressionScannerOptions options
    ) {
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.options = Objects.requireNonNull(options, "options");
    }

    public static GpuIrCommonSubexpressionScanner canonical() {
        // Canonical mode groups safe commutative forms, but still reports all leaf repeats.
        return new GpuIrCommonSubexpressionScanner(new GpuIrCanonicalExpressionFingerprint());
    }

    public static GpuIrCommonSubexpressionScanner optimizerFocused() {
        // This is the scanner mode intended as input to future rewrite/candidate passes.
        return new GpuIrCommonSubexpressionScanner(
                new GpuIrCanonicalExpressionFingerprint(),
                GpuIrCommonSubexpressionScannerOptions.optimizerFocused()
        );
    }

    public GpuIrCommonSubexpressionReport scan(GpuIrMethod method) {
        Objects.requireNonNull(method, "method");
        Map<String, List<String>> locationsByFingerprint = new LinkedHashMap<>();
        for (int index = 0; index < method.statements().size(); index++) {
            scanStatement(method.statements().get(index), "stmt[" + index + "]", locationsByFingerprint);
        }

        List<GpuIrCommonSubexpression> candidates = locationsByFingerprint.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= options.minOccurrences())
                .map(entry -> new GpuIrCommonSubexpression(entry.getKey(), entry.getValue().size(), entry.getValue()))
                .sorted(Comparator.comparingInt(GpuIrCommonSubexpression::estimatedReuseSavings).reversed())
                .toList();
        return new GpuIrCommonSubexpressionReport(method.name(), candidates);
    }

    private void scanStatement(GpuIrStatement statement, String location, Map<String, List<String>> locationsByFingerprint) {
        if (statement instanceof GpuIrVariableDeclaration declaration) {
            scanExpression(declaration.initializer(), location + ".initializer", locationsByFingerprint);
        } else if (statement instanceof GpuIrPrivateArrayDeclaration declaration) {
            scanExpression(declaration.size(), location + ".size", locationsByFingerprint);
        } else if (statement instanceof GpuIrAssignment assignment) {
            scanExpression(assignment.target(), location + ".target", locationsByFingerprint);
            scanExpression(assignment.value(), location + ".value", locationsByFingerprint);
        } else if (statement instanceof GpuIrExpressionStatement expressionStatement) {
            scanExpression(expressionStatement.expression(), location + ".expression", locationsByFingerprint);
        } else if (statement instanceof GpuIrReturn gpuReturn) {
            scanExpression(gpuReturn.value(), location + ".return", locationsByFingerprint);
        } else if (statement instanceof GpuIrIf gpuIf) {
            scanExpression(gpuIf.condition(), location + ".condition", locationsByFingerprint);
            scanStatements(gpuIf.thenBranch(), location + ".then", locationsByFingerprint);
            scanStatements(gpuIf.elseBranch(), location + ".else", locationsByFingerprint);
        } else if (statement instanceof GpuIrForLoop loop) {
            scanStatement(loop.initializer(), location + ".initializer", locationsByFingerprint);
            scanExpression(loop.condition(), location + ".condition", locationsByFingerprint);
            scanStatement(loop.update(), location + ".update", locationsByFingerprint);
            scanStatements(loop.body(), location + ".body", locationsByFingerprint);
        } else if (statement instanceof GpuIrWhileLoop loop) {
            scanExpression(loop.condition(), location + ".condition", locationsByFingerprint);
            scanStatements(loop.body(), location + ".body", locationsByFingerprint);
        } else if (statement instanceof GpuIrDoWhileLoop loop) {
            scanStatements(loop.body(), location + ".body", locationsByFingerprint);
            scanExpression(loop.condition(), location + ".condition", locationsByFingerprint);
        } else if (statement instanceof GpuIrSwitch gpuSwitch) {
            scanExpression(gpuSwitch.selector(), location + ".selector", locationsByFingerprint);
            for (int index = 0; index < gpuSwitch.cases().size(); index++) {
                GpuIrSwitchCase switchCase = gpuSwitch.cases().get(index);
                for (int labelIndex = 0; labelIndex < switchCase.labels().size(); labelIndex++) {
                    scanExpression(switchCase.labels().get(labelIndex), location + ".case[" + index + "].label[" + labelIndex + "]", locationsByFingerprint);
                }
                scanStatements(switchCase.statements(), location + ".case[" + index + "]", locationsByFingerprint);
            }
        }
    }

    private void scanStatements(List<GpuIrStatement> statements, String location, Map<String, List<String>> locationsByFingerprint) {
        for (int index = 0; index < statements.size(); index++) {
            scanStatement(statements.get(index), location + ".stmt[" + index + "]", locationsByFingerprint);
        }
    }

    private void scanExpression(GpuIrExpression expression, String location, Map<String, List<String>> locationsByFingerprint) {
        if (expression == null) {
            return;
        }
        // We still walk through leaf expressions below, but optimizer-focused reports skip them
        // because repeated vars/literals are usually noise, not a profitable rewrite target.
        if (options.includeTrivialLeafExpressions() || !isTrivialLeafExpression(expression)) {
            fingerprint.apply(expression).ifPresent(value -> record(value, location, locationsByFingerprint));
        }

        if (expression instanceof GpuIrArrayAccess arrayAccess) {
            scanExpression(arrayAccess.index(), location + ".index", locationsByFingerprint);
        } else if (expression instanceof GpuIrFieldAccess fieldAccess) {
            scanExpression(fieldAccess.target(), location + ".target", locationsByFingerprint);
        } else if (expression instanceof GpuIrBinary binary) {
            scanExpression(binary.left(), location + ".left", locationsByFingerprint);
            scanExpression(binary.right(), location + ".right", locationsByFingerprint);
        } else if (expression instanceof GpuIrUnary unary) {
            scanExpression(unary.operand(), location + ".operand", locationsByFingerprint);
        } else if (expression instanceof GpuIrTernary ternary) {
            scanExpression(ternary.condition(), location + ".condition", locationsByFingerprint);
            scanExpression(ternary.whenTrue(), location + ".true", locationsByFingerprint);
            scanExpression(ternary.whenFalse(), location + ".false", locationsByFingerprint);
        } else if (expression instanceof GpuIrCast cast) {
            scanExpression(cast.expression(), location + ".expression", locationsByFingerprint);
        } else if (expression instanceof GpuIrStructInit structInit) {
            scanExpressionList(structInit.arguments(), location + ".arg", locationsByFingerprint);
        } else if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            scanExpression(intrinsicCall.receiver(), location + ".receiver", locationsByFingerprint);
            scanExpressionList(intrinsicCall.arguments(), location + ".arg", locationsByFingerprint);
        } else if (expression instanceof GpuIrHelperCall helperCall) {
            scanExpressionList(helperCall.arguments(), location + ".arg", locationsByFingerprint);
        }
    }

    private void scanExpressionList(List<GpuIrExpression> expressions, String location, Map<String, List<String>> locationsByFingerprint) {
        for (int index = 0; index < expressions.size(); index++) {
            scanExpression(expressions.get(index), location + "[" + index + "]", locationsByFingerprint);
        }
    }

    private void record(String fingerprint, String location, Map<String, List<String>> locationsByFingerprint) {
        locationsByFingerprint.computeIfAbsent(fingerprint, ignored -> new ArrayList<>()).add(location);
    }

    private boolean isTrivialLeafExpression(GpuIrExpression expression) {
        return expression instanceof GpuIrLiteral || expression instanceof GpuIrVariableRef;
    }
}
