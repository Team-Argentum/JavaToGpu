package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrFieldAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrStructInit;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Opt-in prototype runner that packages rewrite metadata plus lightweight equivalence evidence.
 *
 * <p>The interpreter is intentionally narrow: it supports the integer-array expressions produced
 * by the current prototype rewrite families. It is a test/integration artifact helper, not a
 * production optimizer or runtime executor.</p>
 */
public final class GpuIrAutoVectorizationPrototypeArtifactRunner {
    private final BiFunction<
            GpuIrMethod,
            GpuIrAutoVectorizationPreview,
            GpuIrAutoVectorizationPrototypeRewriteReport
            > prototypeRewriter;

    public GpuIrAutoVectorizationPrototypeArtifactRunner() {
        this(new GpuIrAutoVectorizationRewriteApplicator());
    }

    public GpuIrAutoVectorizationPrototypeArtifactRunner(
            GpuIrAutoVectorizationRewriteApplicator applicator
    ) {
        Objects.requireNonNull(applicator, "applicator");
        this.prototypeRewriter = applicator::rewritePrototypeReport;
    }

    GpuIrAutoVectorizationPrototypeArtifactRunner(
            BiFunction<GpuIrMethod, GpuIrAutoVectorizationPreview, GpuIrAutoVectorizationPrototypeRewriteReport> prototypeRewriter
    ) {
        this.prototypeRewriter = Objects.requireNonNull(prototypeRewriter, "prototypeRewriter");
    }

    public GpuIrAutoVectorizationPrototypeArtifactReport run(
            GpuIrMethod method,
            GpuIrAutoVectorizationPreview preview,
            List<GpuIrAutoVectorizationPrototypeInputCase> inputCases,
            List<String> comparedOutputs
    ) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(inputCases, "inputCases");
        Objects.requireNonNull(comparedOutputs, "comparedOutputs");
        if (inputCases.isEmpty()) {
            throw new IllegalArgumentException("inputCases must not be empty");
        }
        if (inputCases.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("inputCases must not contain null entries");
        }
        validateComparedOutputs(comparedOutputs);

        GpuIrAutoVectorizationPrototypeRewriteReport rewriteReport = prototypeRewriter.apply(method, preview);
        EquivalenceEvaluation evaluation = evaluateEquivalence(
                method,
                rewriteReport.method(),
                inputCases,
                comparedOutputs
        );
        GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport equivalenceReport = evaluation.diagnostics().isEmpty()
                ? GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport.equivalent(
                        rewriteReport,
                        inputCases.size(),
                        comparedOutputs,
                        evaluation.caseEvidence()
                )
                : GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport.failed(
                        rewriteReport,
                        inputCases.size(),
                        comparedOutputs,
                        evaluation.diagnostics(),
                        evaluation.caseEvidence()
                );
        return new GpuIrAutoVectorizationPrototypeArtifactReport(equivalenceReport);
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

    private EquivalenceEvaluation evaluateEquivalence(
            GpuIrMethod original,
            GpuIrMethod rewritten,
            List<GpuIrAutoVectorizationPrototypeInputCase> inputCases,
            List<String> comparedOutputs
    ) {
        List<String> diagnostics = new ArrayList<>();
        List<GpuIrRuntimeEquivalenceCaseEvidence> caseEvidence = new ArrayList<>();
        for (GpuIrAutoVectorizationPrototypeInputCase inputCase : inputCases) {
            Map<String, String> inputs = new LinkedHashMap<>();
            inputCase.arrays().forEach((name, values) -> inputs.put(name, Arrays.toString(values)));
            Map<String, String> cpuReferenceOutputs = new LinkedHashMap<>();
            Map<String, String> preOptimizationOutputs = new LinkedHashMap<>();
            Map<String, String> postOptimizationOutputs = new LinkedHashMap<>();
            Map<String, String> tolerances = new LinkedHashMap<>();
            Map<String, Boolean> outputEquivalence = new LinkedHashMap<>();
            List<String> caseDiagnostics = new ArrayList<>();
            ExecutionResult originalResult;
            ExecutionResult rewrittenResult;
            try {
                originalResult = execute(original, inputCase);
                rewrittenResult = execute(rewritten, inputCase);
            } catch (IllegalArgumentException exception) {
                String diagnostic = "case " + inputCase.name() + " execution failed: " + exception.getMessage();
                diagnostics.add(diagnostic);
                caseDiagnostics.add(diagnostic);
                for (String output : comparedOutputs) {
                    cpuReferenceOutputs.put(output, GpuIrRuntimeEquivalenceCaseEvidence.NOT_RECORDED);
                    preOptimizationOutputs.put(output, GpuIrRuntimeEquivalenceCaseEvidence.NOT_RECORDED);
                    postOptimizationOutputs.put(output, GpuIrRuntimeEquivalenceCaseEvidence.NOT_RECORDED);
                    tolerances.put(output, "exact-int-lane");
                    outputEquivalence.put(output, false);
                }
                caseEvidence.add(new GpuIrRuntimeEquivalenceCaseEvidence(
                        inputCase.name(),
                        inputs,
                        cpuReferenceOutputs,
                        preOptimizationOutputs,
                        postOptimizationOutputs,
                        tolerances,
                        outputEquivalence,
                        caseDiagnostics
                ));
                continue;
            }
            for (String output : comparedOutputs) {
                int[] originalValues = originalResult.array(output);
                int[] rewrittenValues = rewrittenResult.array(output);
                String referenceValues = originalValues == null
                        ? GpuIrRuntimeEquivalenceCaseEvidence.NOT_RECORDED
                        : Arrays.toString(originalValues);
                String optimizedValues = rewrittenValues == null
                        ? GpuIrRuntimeEquivalenceCaseEvidence.NOT_RECORDED
                        : Arrays.toString(rewrittenValues);
                boolean matches = originalValues != null
                        && rewrittenValues != null
                        && Arrays.equals(originalValues, rewrittenValues);
                cpuReferenceOutputs.put(output, referenceValues);
                preOptimizationOutputs.put(output, referenceValues);
                postOptimizationOutputs.put(output, optimizedValues);
                tolerances.put(output, "exact-int-lane");
                outputEquivalence.put(output, matches);
                if (originalValues == null || rewrittenValues == null) {
                    String diagnostic = "case " + inputCase.name() + " output " + output + " is missing";
                    diagnostics.add(diagnostic);
                    caseDiagnostics.add(diagnostic);
                } else if (!Arrays.equals(originalValues, rewrittenValues)) {
                    String diagnostic = "case " + inputCase.name() + " output " + output
                            + " differs expected=" + Arrays.toString(originalValues)
                            + " actual=" + Arrays.toString(rewrittenValues);
                    diagnostics.add(diagnostic);
                    caseDiagnostics.add(diagnostic);
                }
            }
            caseEvidence.add(new GpuIrRuntimeEquivalenceCaseEvidence(
                    inputCase.name(),
                    inputs,
                    cpuReferenceOutputs,
                    preOptimizationOutputs,
                    postOptimizationOutputs,
                    tolerances,
                    outputEquivalence,
                    caseDiagnostics
            ));
        }
        return new EquivalenceEvaluation(diagnostics, caseEvidence);
    }

    private ExecutionResult execute(GpuIrMethod method, GpuIrAutoVectorizationPrototypeInputCase inputCase) {
        Map<String, Object> values = new HashMap<>();
        inputCase.arrays().forEach((name, array) -> values.put(name, array.clone()));
        executeStatements(method.statements(), values);
        return new ExecutionResult(values);
    }

    private void executeStatements(List<GpuIrStatement> statements, Map<String, Object> values) {
        if (statements == null) {
            throw new IllegalArgumentException("Prototype equivalence runner requires statement lists");
        }
        for (GpuIrStatement statement : statements) {
            if (statement instanceof GpuIrVariableDeclaration declaration) {
                values.put(declaration.name(), evaluate(declaration.initializer(), values));
            } else if (statement instanceof GpuIrAssignment assignment) {
                assign(assignment, values);
            } else if (statement instanceof GpuIrForLoop loop) {
                executeLoop(loop, values);
            } else {
                throw new IllegalArgumentException("Unsupported prototype equivalence statement: " + statement);
            }
        }
    }

    private void executeLoop(GpuIrForLoop loop, Map<String, Object> values) {
        executeStatements(List.of(loop.initializer()), values);
        while (evaluateInt(loop.condition(), values) != 0) {
            executeStatements(loop.body(), values);
            executeStatements(List.of(loop.update()), values);
        }
    }

    private void assign(GpuIrAssignment assignment, Map<String, Object> values) {
        if (assignment.target() instanceof GpuIrVariableRef variableRef) {
            values.put(variableRef.name(), evaluate(assignment.value(), values));
            return;
        }
        if (assignment.target() instanceof GpuIrArrayAccess arrayAccess) {
            int[] array = (int[]) values.get(arrayAccess.arrayName());
            if (array == null) {
                throw new IllegalArgumentException("Missing array for prototype equivalence runner: " + arrayAccess.arrayName());
            }
            array[evaluateInt(arrayAccess.index(), values)] = evaluateInt(assignment.value(), values);
            return;
        }
        throw new IllegalArgumentException("Unsupported prototype equivalence assignment target: " + assignment.target());
    }

    private Object evaluate(GpuIrExpression expression, Map<String, Object> values) {
        if (expression instanceof GpuIrStructInit structInit) {
            List<Integer> lanes = new ArrayList<>();
            for (GpuIrExpression argument : structInit.arguments()) {
                lanes.add(evaluateInt(argument, values));
            }
            return lanes;
        }
        return evaluateInt(expression, values);
    }

    private int evaluateInt(GpuIrExpression expression, Map<String, Object> values) {
        if (expression instanceof GpuIrLiteral literal) {
            return Integer.parseInt(literal.sourceText().replace("_", ""));
        }
        if (expression instanceof GpuIrVariableRef variableRef) {
            Object value = values.get(variableRef.name());
            if (!(value instanceof Integer integerValue)) {
                throw new IllegalArgumentException("Expected integer variable for prototype equivalence runner: " + variableRef.name());
            }
            return integerValue;
        }
        if (expression instanceof GpuIrArrayAccess arrayAccess) {
            int[] array = (int[]) values.get(arrayAccess.arrayName());
            if (array == null) {
                throw new IllegalArgumentException("Missing array for prototype equivalence runner: " + arrayAccess.arrayName());
            }
            return array[evaluateInt(arrayAccess.index(), values)];
        }
        if (expression instanceof GpuIrFieldAccess fieldAccess) {
            if (!(fieldAccess.target() instanceof GpuIrVariableRef variableRef)) {
                throw new IllegalArgumentException("Unsupported prototype equivalence vector field target: " + fieldAccess.target());
            }
            @SuppressWarnings("unchecked")
            List<Integer> lanes = (List<Integer>) values.get(variableRef.name());
            return lanes.get(vectorFieldIndex(fieldAccess.fieldName()));
        }
        if (expression instanceof GpuIrBinary binary) {
            int left = evaluateInt(binary.left(), values);
            int right = evaluateInt(binary.right(), values);
            return switch (binary.operator()) {
                case "+" -> left + right;
                case "-" -> left - right;
                case "*" -> left * right;
                case "&" -> left & right;
                case "|" -> left | right;
                case "^" -> left ^ right;
                case "<" -> left < right ? 1 : 0;
                default -> throw new IllegalArgumentException("Unsupported prototype equivalence binary operator: " + binary.operator());
            };
        }
        if (expression instanceof GpuIrUnary unary) {
            int operand = evaluateInt(unary.operand(), values);
            return switch (unary.operator()) {
                case "+" -> operand;
                case "-" -> -operand;
                case "~" -> ~operand;
                default -> throw new IllegalArgumentException("Unsupported prototype equivalence unary operator: " + unary.operator());
            };
        }
        throw new IllegalArgumentException("Unsupported prototype equivalence expression: " + expression);
    }

    private int vectorFieldIndex(String fieldName) {
        return switch (fieldName) {
            case "x", "s0" -> 0;
            case "y", "s1" -> 1;
            case "z", "s2" -> 2;
            case "w", "s3" -> 3;
            case "s4" -> 4;
            case "s5" -> 5;
            case "s6" -> 6;
            case "s7" -> 7;
            case "s8" -> 8;
            case "s9" -> 9;
            case "sa" -> 10;
            case "sb" -> 11;
            case "sc" -> 12;
            case "sd" -> 13;
            case "se" -> 14;
            case "sf" -> 15;
            default -> throw new IllegalArgumentException("Unsupported prototype equivalence vector field: " + fieldName);
        };
    }

    private record ExecutionResult(Map<String, Object> values) {
        int[] array(String name) {
            Object value = values.get(name);
            return value instanceof int[] array ? array.clone() : null;
        }
    }

    private record EquivalenceEvaluation(
            List<String> diagnostics,
            List<GpuIrRuntimeEquivalenceCaseEvidence> caseEvidence
    ) {
    }
}
