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
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only explanation for simple-associative arithmetic shapes blocked by numeric boundaries.
 *
 * <p>This report is intentionally diagnostic-only. It records nested arithmetic expressions that
 * look structurally close to the `binary_assoc_simple(...)` CSE slice but contain literals or casts,
 * so future work can add typed numeric proofs without guessing why the current pass stayed conservative.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport(
        String methodName,
        List<BlockedCandidate> blockedCandidates
) {
    private static final Set<String> SIMPLE_ASSOCIATIVE_ARITHMETIC_OPERATORS = Set.of("+", "*");

    public GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        blockedCandidates = List.copyOf(Objects.requireNonNull(blockedCandidates, "blockedCandidates"));
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport empty(String methodName) {
        return new GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport(methodName, List.of());
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport from(GpuIrCompiledMethod method) {
        Objects.requireNonNull(method, "method");
        if (method.irMethod() == null) {
            throw new IllegalArgumentException("method must contain IR metadata");
        }
        Analyzer analyzer = new Analyzer(method);
        return new GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport(
                method.irMethod().name(),
                analyzer.scan()
        );
    }

    public int blockedCandidateCount() {
        return blockedCandidates.size();
    }

    public int literalOperandCount() {
        return blockedCandidates.stream().mapToInt(BlockedCandidate::literalOperandCount).sum();
    }

    public int castOperandCount() {
        return blockedCandidates.stream().mapToInt(BlockedCandidate::castOperandCount).sum();
    }

    public boolean hasBlockedCandidates() {
        return !blockedCandidates.isEmpty();
    }

    public Optional<BlockedCandidate> firstBlockedCandidate() {
        return blockedCandidates.stream().findFirst();
    }

    public Map<String, Long> blockedReasonCounts() {
        return blockedCandidates.stream()
                .collect(Collectors.groupingBy(
                        BlockedCandidate::reason,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "BlockedCandidates", Integer.toString(blockedCandidateCount()));
        values.put(prefix + "LiteralOperands", Integer.toString(literalOperandCount()));
        values.put(prefix + "CastOperands", Integer.toString(castOperandCount()));
        values.put(prefix + "HasBlockedCandidates", Boolean.toString(hasBlockedCandidates()));
        values.put(prefix + "BlockedReasonCounts", mapSummary(blockedReasonCounts()));
        blockedReasonCounts().forEach((reason, count) ->
                values.put(prefix + "BlockedReason." + reason, Long.toString(count)));
        firstBlockedCandidate().ifPresent(candidate -> {
            values.put(prefix + "FirstBlockedLocation", candidate.location());
            values.put(prefix + "FirstBlockedOperator", candidate.operator());
            values.put(prefix + "FirstBlockedReason", candidate.reason());
            values.put(prefix + "FirstBlockedOperandTypes", listSummary(candidate.operandTypes()));
            values.put(prefix + "FirstBlockedLiteralSources", listSummary(candidate.literalSources()));
            values.put(prefix + "FirstBlockedCastTargets", listSummary(candidate.castTargets()));
            values.put(prefix + "FirstBlockedCastSourceTypes", listSummary(candidate.castSourceTypes()));
            values.put(prefix + "FirstBlockedSummary", candidate.summary());
        });
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticNumericBoundary");
    }

    public String summary() {
        return "CSE simple arithmetic numeric boundary method=" + methodName
                + " blockedCandidates=" + blockedCandidateCount()
                + " literalOperands=" + literalOperandCount()
                + " castOperands=" + castOperandCount()
                + " hasBlockedCandidates=" + hasBlockedCandidates()
                + " blockedReasons=" + blockedReasonCounts()
                + firstBlockedCandidate()
                .map(candidate -> " firstBlocked={" + candidate.summary() + "}")
                .orElse("");
    }

    public record BlockedCandidate(
            String location,
            String operator,
            String reason,
            List<String> operandTypes,
            List<String> literalSources,
            List<String> castTargets,
            List<String> castSourceTypes
    ) {
        public BlockedCandidate {
            if (location == null || location.isBlank()) {
                throw new IllegalArgumentException("location must not be blank");
            }
            if (operator == null || operator.isBlank()) {
                throw new IllegalArgumentException("operator must not be blank");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
            operandTypes = List.copyOf(Objects.requireNonNull(operandTypes, "operandTypes"));
            literalSources = List.copyOf(Objects.requireNonNull(literalSources, "literalSources"));
            castTargets = List.copyOf(Objects.requireNonNull(castTargets, "castTargets"));
            castSourceTypes = List.copyOf(Objects.requireNonNull(castSourceTypes, "castSourceTypes"));
        }

        public int literalOperandCount() {
            return literalSources.size();
        }

        public int castOperandCount() {
            return castTargets.size();
        }

        public String summary() {
            return "location=" + location
                    + " operator=" + operator
                    + " reason=" + reason
                    + " operandTypes=" + listSummary(operandTypes)
                    + " literalSources=" + listSummary(literalSources)
                    + " castTargets=" + listSummary(castTargets)
                    + " castSourceTypes=" + listSummary(castSourceTypes);
        }
    }

    private static final class Analyzer {
        private final GpuIrCompiledMethod method;
        private final GpuIrExpressionTypeResolver typeResolver = new GpuIrExpressionTypeResolver();
        private final List<BlockedCandidate> blockedCandidates = new ArrayList<>();

        private Analyzer(GpuIrCompiledMethod method) {
            this.method = method;
        }

        private List<BlockedCandidate> scan() {
            List<GpuIrStatement> statements = method.irMethod().statements();
            for (int index = 0; index < statements.size(); index++) {
                scanStatement(statements.get(index), "stmt[" + index + "]");
            }
            return blockedCandidates;
        }

        private void scanStatement(GpuIrStatement statement, String location) {
            if (statement instanceof GpuIrVariableDeclaration declaration) {
                scanExpression(declaration.initializer(), location + ".initializer");
            } else if (statement instanceof GpuIrPrivateArrayDeclaration declaration) {
                scanExpression(declaration.size(), location + ".size");
            } else if (statement instanceof GpuIrAssignment assignment) {
                scanExpression(assignment.target(), location + ".target");
                scanExpression(assignment.value(), location + ".value");
            } else if (statement instanceof GpuIrExpressionStatement expressionStatement) {
                scanExpression(expressionStatement.expression(), location + ".expression");
            } else if (statement instanceof GpuIrReturn gpuReturn) {
                scanExpression(gpuReturn.value(), location + ".return");
            } else if (statement instanceof GpuIrIf gpuIf) {
                scanExpression(gpuIf.condition(), location + ".condition");
                scanStatements(gpuIf.thenBranch(), location + ".then");
                scanStatements(gpuIf.elseBranch(), location + ".else");
            } else if (statement instanceof GpuIrForLoop loop) {
                scanStatement(loop.initializer(), location + ".initializer");
                scanExpression(loop.condition(), location + ".condition");
                scanStatement(loop.update(), location + ".update");
                scanStatements(loop.body(), location + ".body");
            } else if (statement instanceof GpuIrWhileLoop loop) {
                scanExpression(loop.condition(), location + ".condition");
                scanStatements(loop.body(), location + ".body");
            } else if (statement instanceof GpuIrDoWhileLoop loop) {
                scanStatements(loop.body(), location + ".body");
                scanExpression(loop.condition(), location + ".condition");
            } else if (statement instanceof GpuIrSwitch gpuSwitch) {
                scanExpression(gpuSwitch.selector(), location + ".selector");
                for (int index = 0; index < gpuSwitch.cases().size(); index++) {
                    GpuIrSwitchCase switchCase = gpuSwitch.cases().get(index);
                    for (int labelIndex = 0; labelIndex < switchCase.labels().size(); labelIndex++) {
                        scanExpression(switchCase.labels().get(labelIndex), location + ".case[" + index + "].label[" + labelIndex + "]");
                    }
                    scanStatements(switchCase.statements(), location + ".case[" + index + "]");
                }
            }
        }

        private void scanStatements(List<GpuIrStatement> statements, String location) {
            for (int index = 0; index < statements.size(); index++) {
                scanStatement(statements.get(index), location + ".stmt[" + index + "]");
            }
        }

        private void scanExpression(GpuIrExpression expression, String location) {
            if (expression == null) {
                return;
            }
            recordBlockedCandidate(expression, location);
            if (expression instanceof GpuIrArrayAccess arrayAccess) {
                scanExpression(arrayAccess.index(), location + ".index");
            } else if (expression instanceof GpuIrFieldAccess fieldAccess) {
                scanExpression(fieldAccess.target(), location + ".target");
            } else if (expression instanceof GpuIrBinary binary) {
                scanExpression(binary.left(), location + ".left");
                scanExpression(binary.right(), location + ".right");
            } else if (expression instanceof GpuIrUnary unary) {
                scanExpression(unary.operand(), location + ".operand");
            } else if (expression instanceof GpuIrTernary ternary) {
                scanExpression(ternary.condition(), location + ".condition");
                scanExpression(ternary.whenTrue(), location + ".true");
                scanExpression(ternary.whenFalse(), location + ".false");
            } else if (expression instanceof GpuIrCast cast) {
                scanExpression(cast.expression(), location + ".expression");
            } else if (expression instanceof GpuIrStructInit structInit) {
                scanExpressionList(structInit.arguments(), location + ".arg");
            } else if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
                scanExpression(intrinsicCall.receiver(), location + ".receiver");
                scanExpressionList(intrinsicCall.arguments(), location + ".arg");
            } else if (expression instanceof GpuIrHelperCall helperCall) {
                scanExpressionList(helperCall.arguments(), location + ".arg");
            }
        }

        private void scanExpressionList(List<GpuIrExpression> expressions, String location) {
            for (int index = 0; index < expressions.size(); index++) {
                scanExpression(expressions.get(index), location + "[" + index + "]");
            }
        }

        private void recordBlockedCandidate(GpuIrExpression expression, String location) {
            if (!(expression instanceof GpuIrBinary binary)
                    || !SIMPLE_ASSOCIATIVE_ARITHMETIC_OPERATORS.contains(binary.operator())
                    || !hasNestedSameOperator(binary.operator(), binary)) {
                return;
            }
            List<GpuIrExpression> operands = associativeOperands(binary.operator(), binary);
            List<GpuIrLiteral> literals = operands.stream()
                    .filter(GpuIrLiteral.class::isInstance)
                    .map(GpuIrLiteral.class::cast)
                    .toList();
            List<GpuIrCast> casts = operands.stream()
                    .filter(GpuIrCast.class::isInstance)
                    .map(GpuIrCast.class::cast)
                    .toList();
            if (literals.isEmpty() && casts.isEmpty()) {
                return;
            }
            blockedCandidates.add(new BlockedCandidate(
                    location,
                    binary.operator(),
                    blockedReason(literals, casts),
                    operands.stream().map(this::operandType).toList(),
                    literals.stream().map(GpuIrLiteral::sourceText).toList(),
                    casts.stream().map(GpuIrCast::targetType).toList(),
                    casts.stream().map(cast -> operandType(cast.expression())).toList()
            ));
        }

        private List<GpuIrExpression> associativeOperands(String operator, GpuIrExpression expression) {
            if (expression instanceof GpuIrBinary binary && operator.equals(binary.operator())) {
                return java.util.stream.Stream.concat(
                        associativeOperands(operator, binary.left()).stream(),
                        associativeOperands(operator, binary.right()).stream()
                ).toList();
            }
            return List.of(expression);
        }

        private boolean hasNestedSameOperator(String operator, GpuIrBinary binary) {
            return isSameOperatorBinary(operator, binary.left()) || isSameOperatorBinary(operator, binary.right());
        }

        private boolean isSameOperatorBinary(String operator, GpuIrExpression expression) {
            return expression instanceof GpuIrBinary binary && operator.equals(binary.operator());
        }

        private String operandType(GpuIrExpression expression) {
            if (expression instanceof GpuIrLiteral literal) {
                return literalType(literal.sourceText());
            }
            return typeResolver.typeOf(method, expression).orElse("unknown");
        }

        private String literalType(String sourceText) {
            if (sourceText == null) {
                return "unknown";
            }
            String normalized = sourceText.replace("_", "").trim().toLowerCase(java.util.Locale.ROOT);
            if (normalized.endsWith("f")) {
                return "float";
            }
            if (normalized.endsWith("d") || normalized.contains(".")) {
                return "double";
            }
            if (normalized.endsWith("l")) {
                return "long";
            }
            return "int";
        }

        private String blockedReason(List<GpuIrLiteral> literals, List<GpuIrCast> casts) {
            if (!literals.isEmpty() && !casts.isEmpty()) {
                return "literalAndCastOperands";
            }
            if (!literals.isEmpty()) {
                return "literalOperand";
            }
            return "castOperand";
        }
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String listSummary(List<String> values) {
        return String.join(",", values);
    }
}
