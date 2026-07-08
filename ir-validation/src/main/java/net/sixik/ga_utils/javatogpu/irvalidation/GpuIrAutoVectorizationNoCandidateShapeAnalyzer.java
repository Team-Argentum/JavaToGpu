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
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Classifies why the auto-vectorization scanner produced no rewrite candidates.
 *
 * <p>This analyzer is deliberately read-only. It mirrors only the coarse shape checks needed for
 * diagnostics and never decides whether a rewrite is legal.</p>
 */
public final class GpuIrAutoVectorizationNoCandidateShapeAnalyzer {
    private static final Set<Integer> SUPPORTED_LANE_COUNTS = Set.of(2, 3, 4, 8, 16);

    public List<String> buckets(GpuIrMethod method) {
        return report(method).buckets();
    }

    public GpuIrAutoVectorizationNoCandidateShapeReport report(GpuIrMethod method) {
        ShapeStats stats = new ShapeStats();
        if (method == null || method.statements() == null) {
            stats.incompleteIrLocation = "method";
            String methodName = method == null || method.name() == null || method.name().isBlank()
                    ? "<missing>"
                    : method.name();
            List<String> buckets = List.of("incompleteIr");
            return new GpuIrAutoVectorizationNoCandidateShapeReport(methodName, buckets, examples(methodName, buckets, stats));
        }
        collectStatements(method.statements(), "stmt", stats);
        List<String> buckets = buckets(stats);
        return new GpuIrAutoVectorizationNoCandidateShapeReport(method.name(), buckets, examples(method.name(), buckets, stats));
    }

    private List<String> buckets(ShapeStats stats) {
        LinkedHashSet<String> buckets = new LinkedHashSet<>();
        if (stats.incompleteIrCount > 0) {
            buckets.add("incompleteIr");
        }
        if (stats.statementCount == 0) {
            buckets.add("emptyMethod");
        }
        if (stats.forLoopCount == 0 && stats.whileLikeLoopCount == 0) {
            buckets.add(stats.arrayAccessCount == 0 ? "scalarOnlyMethod" : "arrayWorkWithoutLoop");
        } else if (stats.forLoopCount == 0) {
            buckets.add("noForLoop");
        } else if (stats.fixedForLoopCount == 0) {
            buckets.add("unsupportedLoopShape");
        } else if (stats.supportedLaneLoopCount == 0) {
            buckets.add("noFixedWidthLaneLoop");
        } else if (stats.laneArrayAssignmentLoopCount == 0) {
            buckets.add("noLaneArrayAssignment");
        }
        if (buckets.isEmpty()) {
            buckets.add("scannerFoundNoVectorShape");
        }
        return List.copyOf(buckets);
    }

    private List<GpuIrAutoVectorizationNoCandidateExample> examples(
            String methodName,
            List<String> buckets,
            ShapeStats stats
    ) {
        List<GpuIrAutoVectorizationNoCandidateExample> values = new ArrayList<>();
        for (String bucket : buckets) {
            values.add(new GpuIrAutoVectorizationNoCandidateExample(
                    bucket,
                    methodName,
                    exampleLocation(bucket, stats),
                    exampleSummary(bucket, stats)
            ));
        }
        return values;
    }

    private String exampleLocation(String bucket, ShapeStats stats) {
        return switch (bucket) {
            case "incompleteIr" -> firstNonBlank(stats.incompleteIrLocation, "method");
            case "emptyMethod" -> "method";
            case "scalarOnlyMethod" -> firstNonBlank(stats.scalarLocation, "stmt[0]");
            case "arrayWorkWithoutLoop" -> firstNonBlank(stats.arrayAccessLocation, stats.arrayAssignmentLocation, "stmt[0]");
            case "noForLoop" -> firstNonBlank(stats.whileLikeLoopLocation, "stmt[0]");
            case "unsupportedLoopShape" -> firstNonBlank(stats.unsupportedForLoopLocation, stats.forLoopLocation, "stmt[0]");
            case "noFixedWidthLaneLoop" -> firstNonBlank(stats.fixedForLoopLocation, stats.forLoopLocation, "stmt[0]");
            case "noLaneArrayAssignment" -> firstNonBlank(stats.supportedLaneLoopLocation, stats.forLoopLocation, "stmt[0]");
            default -> firstNonBlank(stats.forLoopLocation, stats.scalarLocation, stats.arrayAccessLocation, "method");
        };
    }

    private String exampleSummary(String bucket, ShapeStats stats) {
        return switch (bucket) {
            case "incompleteIr" -> "IR is missing a method, statement list, statement, or nested block";
            case "emptyMethod" -> "method has no statements to scan for lane-wise work";
            case "scalarOnlyMethod" -> "method only contains scalar statements and no array lane work";
            case "arrayWorkWithoutLoop" -> "method touches arrays but has no loop shape for lane discovery";
            case "noForLoop" -> "method uses while-like loops but no supported fixed-width for-loop";
            case "unsupportedLoopShape" -> "for-loop does not match int i = 0; i < laneCount; i = i + 1";
            case "noFixedWidthLaneLoop" -> "fixed-width loop lane count is outside supported vector widths";
            case "noLaneArrayAssignment" -> "supported fixed-width loop has no array assignment indexed by the lane variable";
            default -> "scanner found no vector-shaped rewrite candidate";
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "method";
    }

    private void collectStatements(List<GpuIrStatement> statements, String locationPrefix, ShapeStats stats) {
        if (statements == null) {
            stats.incompleteIrCount++;
            stats.incompleteIrLocation = firstNonBlank(stats.incompleteIrLocation, locationPrefix);
            return;
        }
        for (int index = 0; index < statements.size(); index++) {
            collectStatement(statements.get(index), locationPrefix + "[" + index + "]", stats);
        }
    }

    private void collectStatement(GpuIrStatement statement, String location, ShapeStats stats) {
        if (statement == null) {
            stats.incompleteIrCount++;
            stats.incompleteIrLocation = firstNonBlank(stats.incompleteIrLocation, location);
            return;
        }
        stats.statementCount++;
        if (statement instanceof GpuIrForLoop loop) {
            collectForLoop(loop, location, stats);
        } else if (statement instanceof GpuIrWhileLoop loop) {
            stats.whileLikeLoopCount++;
            stats.whileLikeLoopLocation = firstNonBlank(stats.whileLikeLoopLocation, location);
            collectExpression(loop.condition(), location + ".condition", stats);
            collectStatements(loop.body(), location + ".body.stmt", stats);
        } else if (statement instanceof GpuIrDoWhileLoop loop) {
            stats.whileLikeLoopCount++;
            stats.whileLikeLoopLocation = firstNonBlank(stats.whileLikeLoopLocation, location);
            collectStatements(loop.body(), location + ".body.stmt", stats);
            collectExpression(loop.condition(), location + ".condition", stats);
        } else if (statement instanceof GpuIrIf gpuIf) {
            collectExpression(gpuIf.condition(), location + ".condition", stats);
            collectStatements(gpuIf.thenBranch(), location + ".then.stmt", stats);
            collectStatements(gpuIf.elseBranch(), location + ".else.stmt", stats);
        } else if (statement instanceof GpuIrSwitch gpuSwitch) {
            collectExpression(gpuSwitch.selector(), location + ".selector", stats);
            if (gpuSwitch.cases() == null) {
                stats.incompleteIrCount++;
                stats.incompleteIrLocation = firstNonBlank(stats.incompleteIrLocation, location + ".case");
            } else {
                for (int caseIndex = 0; caseIndex < gpuSwitch.cases().size(); caseIndex++) {
                    int currentCaseIndex = caseIndex;
                    var switchCase = gpuSwitch.cases().get(caseIndex);
                    if (switchCase == null) {
                        stats.incompleteIrCount++;
                        stats.incompleteIrLocation = firstNonBlank(stats.incompleteIrLocation, location + ".case[" + currentCaseIndex + "]");
                    } else {
                        collectStatements(switchCase.statements(), location + ".case[" + currentCaseIndex + "].stmt", stats);
                    }
                }
            }
        } else if (statement instanceof GpuIrAssignment assignment) {
            collectAssignment(assignment, location, stats);
        } else if (statement instanceof GpuIrVariableDeclaration declaration) {
            stats.scalarStatementCount++;
            stats.scalarLocation = firstNonBlank(stats.scalarLocation, location);
            collectExpression(declaration.initializer(), location + ".initializer", stats);
        } else if (statement instanceof GpuIrExpressionStatement expressionStatement) {
            stats.scalarStatementCount++;
            stats.scalarLocation = firstNonBlank(stats.scalarLocation, location);
            collectExpression(expressionStatement.expression(), location + ".expression", stats);
        } else if (statement instanceof GpuIrReturn gpuReturn) {
            stats.scalarStatementCount++;
            stats.scalarLocation = firstNonBlank(stats.scalarLocation, location);
            collectExpression(gpuReturn.value(), location + ".return", stats);
        } else if (statement instanceof GpuIrPrivateArrayDeclaration declaration) {
            stats.privateArrayDeclarationCount++;
            stats.scalarLocation = firstNonBlank(stats.scalarLocation, location);
            collectExpression(declaration.size(), location + ".size", stats);
        }
    }

    private void collectForLoop(GpuIrForLoop loop, String location, ShapeStats stats) {
        stats.forLoopCount++;
        stats.forLoopLocation = firstNonBlank(stats.forLoopLocation, location);
        Optional<LoopBounds> bounds = loopBounds(loop);
        if (bounds.isPresent()) {
            stats.fixedForLoopCount++;
            stats.fixedForLoopLocation = firstNonBlank(stats.fixedForLoopLocation, location);
            if (SUPPORTED_LANE_COUNTS.contains(bounds.get().laneCount())) {
                stats.supportedLaneLoopCount++;
                stats.supportedLaneLoopLocation = firstNonBlank(stats.supportedLaneLoopLocation, location);
                if (hasLaneArrayAssignment(loop.body(), bounds.get().inductionVariable())) {
                    stats.laneArrayAssignmentLoopCount++;
                }
            }
        } else {
            stats.unsupportedForLoopLocation = firstNonBlank(stats.unsupportedForLoopLocation, location);
        }
        collectStatement(loop.initializer(), location + ".initializer", stats);
        collectExpression(loop.condition(), location + ".condition", stats);
        collectStatement(loop.update(), location + ".update", stats);
        collectStatements(loop.body(), location + ".body.stmt", stats);
    }

    private void collectAssignment(GpuIrAssignment assignment, String location, ShapeStats stats) {
        if (assignment.target() instanceof GpuIrArrayAccess) {
            stats.arrayAssignmentCount++;
            stats.arrayAssignmentLocation = firstNonBlank(stats.arrayAssignmentLocation, location);
        } else {
            stats.scalarStatementCount++;
            stats.scalarLocation = firstNonBlank(stats.scalarLocation, location);
        }
        collectExpression(assignment.target(), location + ".target", stats);
        collectExpression(assignment.value(), location + ".value", stats);
    }

    private boolean hasLaneArrayAssignment(List<GpuIrStatement> body, String inductionVariable) {
        if (body == null) {
            return false;
        }
        for (GpuIrStatement statement : body) {
            if (statement instanceof GpuIrAssignment assignment
                    && assignment.target() instanceof GpuIrArrayAccess target
                    && isVariableRef(target.index(), inductionVariable)) {
                return true;
            }
        }
        return false;
    }

    private void collectExpression(GpuIrExpression expression, String location, ShapeStats stats) {
        if (expression == null) {
            return;
        }
        if (expression instanceof GpuIrArrayAccess arrayAccess) {
            stats.arrayAccessCount++;
            stats.arrayAccessLocation = firstNonBlank(stats.arrayAccessLocation, location);
            collectExpression(arrayAccess.index(), location + ".index", stats);
        } else if (expression instanceof GpuIrBinary binary) {
            collectExpression(binary.left(), location + ".left", stats);
            collectExpression(binary.right(), location + ".right", stats);
        } else if (expression instanceof GpuIrUnary unary) {
            collectExpression(unary.operand(), location + ".operand", stats);
        } else if (expression instanceof GpuIrCast cast) {
            collectExpression(cast.expression(), location + ".cast", stats);
        } else if (expression instanceof GpuIrTernary ternary) {
            collectExpression(ternary.condition(), location + ".condition", stats);
            collectExpression(ternary.whenTrue(), location + ".true", stats);
            collectExpression(ternary.whenFalse(), location + ".false", stats);
        } else if (expression instanceof GpuIrFieldAccess fieldAccess) {
            collectExpression(fieldAccess.target(), location + ".target", stats);
        } else if (expression instanceof GpuIrStructInit structInit) {
            if (structInit.arguments() != null) {
                for (int index = 0; index < structInit.arguments().size(); index++) {
                    collectExpression(structInit.arguments().get(index), location + ".arg[" + index + "]", stats);
                }
            }
        } else if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            collectExpression(intrinsicCall.receiver(), location + ".receiver", stats);
            if (intrinsicCall.arguments() != null) {
                for (int index = 0; index < intrinsicCall.arguments().size(); index++) {
                    collectExpression(intrinsicCall.arguments().get(index), location + ".arg[" + index + "]", stats);
                }
            }
        } else if (expression instanceof GpuIrHelperCall helperCall && helperCall.arguments() != null) {
            for (int index = 0; index < helperCall.arguments().size(); index++) {
                collectExpression(helperCall.arguments().get(index), location + ".arg[" + index + "]", stats);
            }
        }
    }

    private Optional<LoopBounds> loopBounds(GpuIrForLoop loop) {
        if (!(loop.initializer() instanceof GpuIrVariableDeclaration initializer)
                || !"int".equals(initializer.typeName())) {
            return Optional.empty();
        }
        Optional<Integer> start = intLiteral(initializer.initializer());
        if (start.isEmpty() || start.get() != 0) {
            return Optional.empty();
        }
        Optional<Integer> end = exclusiveUpperBound(loop.condition(), initializer.name());
        if (end.isEmpty()) {
            return Optional.empty();
        }
        if (!incrementsByOne(loop.update(), initializer.name())) {
            return Optional.empty();
        }
        return Optional.of(new LoopBounds(initializer.name(), start.get(), end.get()));
    }

    private Optional<Integer> exclusiveUpperBound(GpuIrExpression condition, String inductionVariable) {
        if (condition instanceof GpuIrBinary binary
                && "<".equals(binary.operator())
                && isVariableRef(binary.left(), inductionVariable)) {
            return intLiteral(binary.right());
        }
        return Optional.empty();
    }

    private boolean incrementsByOne(GpuIrStatement update, String inductionVariable) {
        if (!(update instanceof GpuIrAssignment assignment)
                || !isVariableRef(assignment.target(), inductionVariable)
                || !(assignment.value() instanceof GpuIrBinary binary)
                || !"+".equals(binary.operator())) {
            return false;
        }
        return (isVariableRef(binary.left(), inductionVariable) && intLiteral(binary.right()).orElse(Integer.MIN_VALUE) == 1)
                || (isVariableRef(binary.right(), inductionVariable) && intLiteral(binary.left()).orElse(Integer.MIN_VALUE) == 1);
    }

    private boolean isVariableRef(GpuIrExpression expression, String name) {
        return expression instanceof GpuIrVariableRef variableRef && name.equals(variableRef.name());
    }

    private Optional<Integer> intLiteral(GpuIrExpression expression) {
        if (expression instanceof GpuIrLiteral literal) {
            try {
                return Optional.of(Integer.parseInt(literal.sourceText().replace("_", "")));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private record LoopBounds(String inductionVariable, int startInclusive, int endExclusive) {
        int laneCount() {
            return endExclusive - startInclusive;
        }
    }

    private static final class ShapeStats {
        private int statementCount;
        private int scalarStatementCount;
        private int privateArrayDeclarationCount;
        private int arrayAssignmentCount;
        private int arrayAccessCount;
        private int forLoopCount;
        private int whileLikeLoopCount;
        private int fixedForLoopCount;
        private int supportedLaneLoopCount;
        private int laneArrayAssignmentLoopCount;
        private int incompleteIrCount;
        private String scalarLocation;
        private String arrayAssignmentLocation;
        private String arrayAccessLocation;
        private String forLoopLocation;
        private String whileLikeLoopLocation;
        private String unsupportedForLoopLocation;
        private String fixedForLoopLocation;
        private String supportedLaneLoopLocation;
        private String incompleteIrLocation;
    }
}
