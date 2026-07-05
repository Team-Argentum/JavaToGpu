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

/**
 * Opt-in runner that produces runtime-equivalence evidence for literal canonicalization preview.
 *
 * <p>The runner intentionally evaluates the original method twice and never rewrites IR. Its purpose
 * is to attach explicit input/output coverage to the read-only literal canonicalization preview so
 * promotion gates can distinguish absent evidence from successful test evidence.</p>
 */
public final class GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceRunner {
    public GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport run(
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

        GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport boundaryReport =
                GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport.from(method);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport literalProofReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.from(boundaryReport);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.from(literalProofReport);
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericProofReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.from(canonicalizationReport);

        List<String> diagnostics = equivalenceDiagnostics(method.irMethod(), inputCases, comparedOutputs);
        if (!canonicalizationReport.hasCandidates()) {
            diagnostics.add("literal canonicalization preview has no candidates");
        }
        if (!numericProofReport.fullyProven()) {
            diagnostics.add("literal canonicalization numeric semantics proof is not fully proven");
        }

        if (diagnostics.isEmpty()) {
            return GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.equivalent(
                    canonicalizationReport,
                    numericProofReport,
                    inputCases.size(),
                    comparedOutputs
            );
        }
        return GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.failed(
                canonicalizationReport,
                numericProofReport,
                inputCases.size(),
                comparedOutputs,
                diagnostics
        );
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport runArtifact(
            GpuIrCompiledMethod method,
            List<GpuIrCommonSubexpressionInputCase> inputCases,
            List<String> comparedOutputs
    ) {
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport(run(
                method,
                inputCases,
                comparedOutputs
        ));
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
            GpuIrMethod method,
            List<GpuIrCommonSubexpressionInputCase> inputCases,
            List<String> comparedOutputs
    ) {
        List<String> diagnostics = new ArrayList<>();
        for (GpuIrCommonSubexpressionInputCase inputCase : inputCases) {
            ExecutionResult firstRun;
            ExecutionResult secondRun;
            try {
                firstRun = execute(method, inputCase);
                secondRun = execute(method, inputCase);
            } catch (IllegalArgumentException exception) {
                diagnostics.add("case " + inputCase.name() + " execution failed: " + exception.getMessage());
                continue;
            }
            for (String output : comparedOutputs) {
                Integer expectedValue = firstRun.valueOf(output);
                Integer actualValue = secondRun.valueOf(output);
                if (expectedValue == null || actualValue == null) {
                    diagnostics.add("case " + inputCase.name() + " output " + output + " is missing");
                } else if (!expectedValue.equals(actualValue)) {
                    diagnostics.add("case " + inputCase.name() + " output " + output
                            + " differs expected=" + expectedValue
                            + " actual=" + actualValue);
                }
            }
        }
        return diagnostics;
    }

    private ExecutionResult execute(GpuIrMethod method, GpuIrCommonSubexpressionInputCase inputCase) {
        Map<String, Integer> values = new HashMap<>(inputCase.values());
        Integer returnValue = null;
        if (method.statements() == null) {
            throw new IllegalArgumentException("literal runtime-equivalence runner requires statement lists");
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
                throw new IllegalArgumentException("Unsupported literal runtime-equivalence statement: " + statement);
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
                throw new IllegalArgumentException("Missing integer variable for literal runtime-equivalence runner: " + variableRef.name());
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
                default -> throw new IllegalArgumentException("Unsupported literal runtime-equivalence binary operator: " + binary.operator());
            };
        }
        if (expression instanceof GpuIrUnary unary) {
            int operand = evaluate(unary.operand(), values);
            return switch (unary.operator()) {
                case "+" -> operand;
                case "-" -> -operand;
                case "~" -> ~operand;
                case "!" -> operand == 0 ? 1 : 0;
                default -> throw new IllegalArgumentException("Unsupported literal runtime-equivalence unary operator: " + unary.operator());
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
        throw new IllegalArgumentException("Unsupported literal runtime-equivalence expression: " + expression);
    }

    private record ExecutionResult(Map<String, Integer> values, Integer returnValue) {
        Integer valueOf(String name) {
            return "return".equals(name) ? returnValue : values.get(name);
        }
    }
}
