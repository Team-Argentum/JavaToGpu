package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Opt-in CSE artifact runner that packages planning metadata plus lightweight equivalence evidence.
 *
 * <p>The interpreter intentionally supports only the straight-line integer expressions used by the
 * current prototype CSE rewrite tests. It is a CI/test artifact helper, not a production executor.</p>
 */
public final class GpuIrCommonSubexpressionArtifactRunner {
    private final GpuIrCommonSubexpressionScanner scanner;
    private final GpuIrCommonSubexpressionRewritePlanner planner;
    private final BiFunction<
            GpuIrCompiledMethod,
            GpuIrCommonSubexpressionRewritePlanReport,
            GpuIrMethod
            > prototypeRewriter;

    public GpuIrCommonSubexpressionArtifactRunner() {
        this(
                GpuIrCommonSubexpressionScanner.optimizerFocused(),
                new GpuIrCommonSubexpressionRewritePlanner(),
                new GpuIrCommonSubexpressionRewriteApplicator()
        );
    }

    public GpuIrCommonSubexpressionArtifactRunner(
            GpuIrCommonSubexpressionScanner scanner,
            GpuIrCommonSubexpressionRewritePlanner planner,
            GpuIrCommonSubexpressionRewriteApplicator applicator
    ) {
        this(
                scanner,
                planner,
                (method, planReport) -> applicator.apply(method, planReport)
        );
        Objects.requireNonNull(applicator, "applicator");
    }

    GpuIrCommonSubexpressionArtifactRunner(
            GpuIrCommonSubexpressionScanner scanner,
            GpuIrCommonSubexpressionRewritePlanner planner,
            BiFunction<GpuIrCompiledMethod, GpuIrCommonSubexpressionRewritePlanReport, GpuIrMethod> prototypeRewriter
    ) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.prototypeRewriter = Objects.requireNonNull(prototypeRewriter, "prototypeRewriter");
    }

    public GpuIrCommonSubexpressionArtifactReport run(
            GpuIrCompiledMethod method,
            List<GpuIrCommonSubexpressionInputCase> inputCases,
            List<String> comparedOutputs
    ) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(inputCases, "inputCases");
        Objects.requireNonNull(comparedOutputs, "comparedOutputs");
        if (method.irMethod() == null) {
            throw new IllegalArgumentException("method must contain IR metadata");
        }
        validateInputCases(inputCases);
        validateComparedOutputs(comparedOutputs);

        GpuIrCommonSubexpressionReport cseReport = scanner.scan(method.irMethod());
        GpuIrCommonSubexpressionRewritePlanReport planReport = planner.planReport(method, cseReport);
        GpuIrMethod rewritten = prototypeRewriter.apply(method, planReport);
        List<String> diagnostics = equivalenceDiagnostics(method.irMethod(), rewritten, inputCases, comparedOutputs);
        GpuIrCommonSubexpressionRuntimeEquivalenceReport equivalenceReport = diagnostics.isEmpty()
                ? GpuIrCommonSubexpressionRuntimeEquivalenceReport.equivalent(
                        planReport,
                        inputCases.size(),
                        comparedOutputs
                )
                : GpuIrCommonSubexpressionRuntimeEquivalenceReport.failed(
                        planReport,
                        inputCases.size(),
                        comparedOutputs,
                        diagnostics
                );
        return new GpuIrCommonSubexpressionArtifactReport(equivalenceReport);
    }

    private void validateInputCases(List<GpuIrCommonSubexpressionInputCase> inputCases) {
        if (inputCases.isEmpty()) {
            throw new IllegalArgumentException("inputCases must not be empty");
        }
        if (inputCases.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("inputCases must not contain null entries");
        }
    }

    private void validateComparedOutputs(List<String> comparedOutputs) {
        if (comparedOutputs.isEmpty()) {
            throw new IllegalArgumentException("comparedOutputs must not be empty");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String output : comparedOutputs) {
            if (output == null || output.isBlank()) {
                throw new IllegalArgumentException("comparedOutputs must not contain blank entries");
            }
            if (!seen.add(output)) {
                throw new IllegalArgumentException("comparedOutputs must not contain duplicates: " + output);
            }
        }
    }

    private List<String> equivalenceDiagnostics(
            GpuIrMethod original,
            GpuIrMethod rewritten,
            List<GpuIrCommonSubexpressionInputCase> inputCases,
            List<String> comparedOutputs
    ) {
        List<String> diagnostics = new ArrayList<>();
        for (GpuIrCommonSubexpressionInputCase inputCase : inputCases) {
            ExecutionResult originalResult;
            ExecutionResult rewrittenResult;
            try {
                originalResult = execute(original, inputCase);
                rewrittenResult = execute(rewritten, inputCase);
            } catch (IllegalArgumentException exception) {
                diagnostics.add("case " + inputCase.name() + " execution failed: " + exception.getMessage());
                continue;
            }
            for (String output : comparedOutputs) {
                Integer originalValue = originalResult.valueOf(output);
                Integer rewrittenValue = rewrittenResult.valueOf(output);
                if (originalValue == null || rewrittenValue == null) {
                    diagnostics.add("case " + inputCase.name() + " output " + output + " is missing");
                } else if (!originalValue.equals(rewrittenValue)) {
                    diagnostics.add("case " + inputCase.name() + " output " + output
                            + " differs expected=" + originalValue
                            + " actual=" + rewrittenValue);
                }
            }
        }
        return diagnostics;
    }

    private ExecutionResult execute(GpuIrMethod method, GpuIrCommonSubexpressionInputCase inputCase) {
        Map<String, Integer> values = new HashMap<>(inputCase.values());
        Integer returnValue = null;
        if (method.statements() == null) {
            throw new IllegalArgumentException("CSE equivalence runner requires statement lists");
        }
        for (GpuIrStatement statement : method.statements()) {
            if (statement instanceof GpuIrVariableDeclaration declaration) {
                values.put(declaration.name(), evaluate(declaration.initializer(), values));
            } else if (statement instanceof GpuIrAssignment assignment && assignment.target() instanceof GpuIrVariableRef target) {
                values.put(target.name(), evaluate(assignment.value(), values));
            } else if (statement instanceof GpuIrReturn gpuReturn && gpuReturn.value() != null) {
                returnValue = evaluate(gpuReturn.value(), values);
                values.put("return", returnValue);
            } else {
                throw new IllegalArgumentException("Unsupported CSE equivalence statement: " + statement);
            }
        }
        return new ExecutionResult(values, returnValue);
    }

    private int evaluate(GpuIrExpression expression, Map<String, Integer> values) {
        if (expression instanceof GpuIrLiteral literal) {
            return Integer.parseInt(literal.sourceText().replace("_", "").replace("L", ""));
        }
        if (expression instanceof GpuIrVariableRef variableRef) {
            Integer value = values.get(variableRef.name());
            if (value == null) {
                throw new IllegalArgumentException("Missing integer variable for CSE equivalence runner: " + variableRef.name());
            }
            return value;
        }
        if (expression instanceof GpuIrBinary binary) {
            int left = evaluate(binary.left(), values);
            int right = evaluate(binary.right(), values);
            return switch (binary.operator()) {
                case "+" -> left + right;
                case "-" -> left - right;
                case "*" -> left * right;
                case "/" -> left / right;
                case "&" -> left & right;
                case "|" -> left | right;
                case "^" -> left ^ right;
                case "<" -> left < right ? 1 : 0;
                case ">" -> left > right ? 1 : 0;
                case "==" -> left == right ? 1 : 0;
                case "!=" -> left != right ? 1 : 0;
                default -> throw new IllegalArgumentException("Unsupported CSE equivalence binary operator: " + binary.operator());
            };
        }
        if (expression instanceof GpuIrUnary unary) {
            int operand = evaluate(unary.operand(), values);
            return switch (unary.operator()) {
                case "+" -> operand;
                case "-" -> -operand;
                case "~" -> ~operand;
                case "!" -> operand == 0 ? 1 : 0;
                default -> throw new IllegalArgumentException("Unsupported CSE equivalence unary operator: " + unary.operator());
            };
        }
        if (expression instanceof GpuIrCast cast) {
            return evaluate(cast.expression(), values);
        }
        if (expression instanceof GpuIrTernary ternary) {
            return evaluate(ternary.condition(), values) != 0
                    ? evaluate(ternary.whenTrue(), values)
                    : evaluate(ternary.whenFalse(), values);
        }
        throw new IllegalArgumentException("Unsupported CSE equivalence expression: " + expression);
    }

    private record ExecutionResult(Map<String, Integer> values, Integer returnValue) {
        Integer valueOf(String name) {
            return "return".equals(name) ? returnValue : values.get(name);
        }
    }
}
