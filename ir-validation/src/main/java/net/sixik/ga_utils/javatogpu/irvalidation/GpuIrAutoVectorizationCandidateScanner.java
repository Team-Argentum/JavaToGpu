package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
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
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds conservative fixed-width lane-wise loops that may become auto-vectorization inputs later.
 */
public final class GpuIrAutoVectorizationCandidateScanner {
    private static final Set<Integer> SUPPORTED_LANE_COUNTS = Set.of(2, 3, 4, 8, 16);

    private final GpuIrExpressionClassifier expressionClassifier;

    public GpuIrAutoVectorizationCandidateScanner() {
        this(new GpuIrExpressionClassifier());
    }

    public GpuIrAutoVectorizationCandidateScanner(GpuIrExpressionClassifier expressionClassifier) {
        this.expressionClassifier = expressionClassifier;
    }

    public GpuIrAutoVectorizationReport scan(GpuIrMethod method) {
        List<GpuIrAutoVectorizationCandidate> candidates = new ArrayList<>();
        List<GpuIrAutoVectorizationRejectionDiagnostic> rejections = new ArrayList<>();
        scanStatements(method.statements(), "stmt", candidates, rejections);
        return new GpuIrAutoVectorizationReport(method.name(), candidates, rejections);
    }

    private void scanStatements(
            List<GpuIrStatement> statements,
            String locationPrefix,
            List<GpuIrAutoVectorizationCandidate> candidates,
            List<GpuIrAutoVectorizationRejectionDiagnostic> rejections
    ) {
        for (int index = 0; index < statements.size(); index++) {
            scanStatement(statements.get(index), locationPrefix + "[" + index + "]", candidates, rejections);
        }
    }

    private void scanStatement(
            GpuIrStatement statement,
            String location,
            List<GpuIrAutoVectorizationCandidate> candidates,
            List<GpuIrAutoVectorizationRejectionDiagnostic> rejections
    ) {
        if (statement instanceof GpuIrForLoop loop) {
            ScanResult result = candidateFromLoop(location, loop);
            result.candidate().ifPresent(candidates::add);
            result.rejection().ifPresent(rejections::add);
            scanStatements(loop.body(), location + ".body.stmt", candidates, rejections);
        } else if (statement instanceof GpuIrIf gpuIf) {
            scanStatements(gpuIf.thenBranch(), location + ".then.stmt", candidates, rejections);
            scanStatements(gpuIf.elseBranch(), location + ".else.stmt", candidates, rejections);
        } else if (statement instanceof GpuIrWhileLoop loop) {
            scanStatements(loop.body(), location + ".body.stmt", candidates, rejections);
        } else if (statement instanceof GpuIrDoWhileLoop loop) {
            scanStatements(loop.body(), location + ".body.stmt", candidates, rejections);
        } else if (statement instanceof GpuIrSwitch gpuSwitch) {
            for (int caseIndex = 0; caseIndex < gpuSwitch.cases().size(); caseIndex++) {
                scanStatements(gpuSwitch.cases().get(caseIndex).statements(), location + ".case[" + caseIndex + "].stmt", candidates, rejections);
            }
        }
    }

    private ScanResult candidateFromLoop(String location, GpuIrForLoop loop) {
        Optional<LoopBounds> bounds = loopBounds(loop);
        if (bounds.isEmpty()) {
            return ScanResult.rejected(location, GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_LOOP_SHAPE, "requires int i = 0; i < laneCount; i = i + 1");
        }
        if (!SUPPORTED_LANE_COUNTS.contains(bounds.get().laneCount())) {
            return ScanResult.rejected(location, GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_LANE_COUNT, "laneCount=" + bounds.get().laneCount());
        }
        if (loop.body().isEmpty()) {
            return ScanResult.rejected(location, GpuIrAutoVectorizationRejectionReason.EMPTY_BODY, "loop body has no assignments");
        }

        LinkedHashSet<String> targetArrays = new LinkedHashSet<>();
        LinkedHashSet<String> sourceArrays = new LinkedHashSet<>();
        LinkedHashSet<String> crossLaneReadWarnings = new LinkedHashSet<>();
        int assignmentCount = 0;
        for (GpuIrStatement statement : loop.body()) {
            if (!(statement instanceof GpuIrAssignment assignment)) {
                return ScanResult.rejected(location, GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_BODY_STATEMENT, statement.getClass().getSimpleName());
            }
            if (!(assignment.target() instanceof GpuIrArrayAccess target)
                    || !isVariableRef(target.index(), bounds.get().inductionVariable())) {
                return ScanResult.rejected(location, GpuIrAutoVectorizationRejectionReason.NON_LANE_TARGET, "target must be array[indexVariable]");
            }
            if (expressionClassifier.mayHaveSideEffects(assignment.value())) {
                return ScanResult.rejected(location, GpuIrAutoVectorizationRejectionReason.SIDE_EFFECTING_VALUE, "assignment value may have side effects");
            }
            targetArrays.add(target.arrayName());
            collectArrayReads(assignment.value(), bounds.get().inductionVariable(), sourceArrays, crossLaneReadWarnings);
            assignmentCount++;
        }
        if (assignmentCount == 0 || targetArrays.isEmpty()) {
            return ScanResult.rejected(location, GpuIrAutoVectorizationRejectionReason.EMPTY_BODY, "loop body has no lane-wise assignments");
        }
        return ScanResult.accepted(new GpuIrAutoVectorizationCandidate(
                location,
                bounds.get().inductionVariable(),
                bounds.get().startInclusive(),
                bounds.get().endExclusive(),
                bounds.get().laneCount(),
                List.copyOf(targetArrays),
                List.copyOf(sourceArrays),
                aliasWarnings(targetArrays, sourceArrays),
                List.copyOf(crossLaneReadWarnings),
                assignmentCount
        ));
    }

    private List<String> aliasWarnings(Set<String> targetArrays, Set<String> sourceArrays) {
        List<String> warnings = new ArrayList<>();
        for (String targetArray : targetArrays) {
            if (sourceArrays.contains(targetArray)) {
                warnings.add("target array `" + targetArray + "` is also read in the loop body");
            }
        }
        return warnings;
    }

    private void collectArrayReads(
            GpuIrExpression expression,
            String inductionVariable,
            Set<String> sourceArrays,
            Set<String> crossLaneReadWarnings
    ) {
        if (expression == null) {
            return;
        }
        if (expression instanceof GpuIrArrayAccess arrayAccess) {
            sourceArrays.add(arrayAccess.arrayName());
            crossLaneOffset(arrayAccess.index(), inductionVariable)
                    .ifPresent(offset -> crossLaneReadWarnings.add("array `" + arrayAccess.arrayName()
                            + "` is read at cross-lane offset " + signedOffset(offset)
                            + " from `" + inductionVariable + "`"));
            collectArrayReads(arrayAccess.index(), inductionVariable, sourceArrays, crossLaneReadWarnings);
        } else if (expression instanceof GpuIrBinary binary) {
            collectArrayReads(binary.left(), inductionVariable, sourceArrays, crossLaneReadWarnings);
            collectArrayReads(binary.right(), inductionVariable, sourceArrays, crossLaneReadWarnings);
        } else if (expression instanceof GpuIrUnary unary) {
            collectArrayReads(unary.operand(), inductionVariable, sourceArrays, crossLaneReadWarnings);
        } else if (expression instanceof GpuIrTernary ternary) {
            collectArrayReads(ternary.condition(), inductionVariable, sourceArrays, crossLaneReadWarnings);
            collectArrayReads(ternary.whenTrue(), inductionVariable, sourceArrays, crossLaneReadWarnings);
            collectArrayReads(ternary.whenFalse(), inductionVariable, sourceArrays, crossLaneReadWarnings);
        } else if (expression instanceof GpuIrFieldAccess fieldAccess) {
            collectArrayReads(fieldAccess.target(), inductionVariable, sourceArrays, crossLaneReadWarnings);
        } else if (expression instanceof GpuIrStructInit structInit) {
            for (GpuIrExpression argument : structInit.arguments()) {
                collectArrayReads(argument, inductionVariable, sourceArrays, crossLaneReadWarnings);
            }
        } else if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            collectArrayReads(intrinsicCall.receiver(), inductionVariable, sourceArrays, crossLaneReadWarnings);
            for (GpuIrExpression argument : intrinsicCall.arguments()) {
                collectArrayReads(argument, inductionVariable, sourceArrays, crossLaneReadWarnings);
            }
        } else if (expression instanceof GpuIrHelperCall helperCall) {
            for (GpuIrExpression argument : helperCall.arguments()) {
                collectArrayReads(argument, inductionVariable, sourceArrays, crossLaneReadWarnings);
            }
        }
    }

    private Optional<Integer> crossLaneOffset(GpuIrExpression index, String inductionVariable) {
        if (!(index instanceof GpuIrBinary binary)) {
            return Optional.empty();
        }
        Optional<Integer> offset = switch (binary.operator()) {
            case "+" -> offsetFromAddition(binary, inductionVariable);
            case "-" -> isVariableRef(binary.left(), inductionVariable)
                    ? intLiteral(binary.right()).map(value -> -value)
                    : Optional.empty();
            default -> Optional.empty();
        };
        return offset.filter(value -> value != 0);
    }

    private Optional<Integer> offsetFromAddition(GpuIrBinary binary, String inductionVariable) {
        if (isVariableRef(binary.left(), inductionVariable)) {
            return intLiteral(binary.right());
        }
        if (isVariableRef(binary.right(), inductionVariable)) {
            return intLiteral(binary.left());
        }
        return Optional.empty();
    }

    private String signedOffset(int offset) {
        return offset > 0 ? "+" + offset : Integer.toString(offset);
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

    private record ScanResult(
            Optional<GpuIrAutoVectorizationCandidate> candidate,
            Optional<GpuIrAutoVectorizationRejectionDiagnostic> rejection
    ) {
        static ScanResult accepted(GpuIrAutoVectorizationCandidate candidate) {
            return new ScanResult(Optional.of(candidate), Optional.empty());
        }

        static ScanResult rejected(String location, GpuIrAutoVectorizationRejectionReason reason, String detail) {
            return new ScanResult(Optional.empty(), Optional.of(new GpuIrAutoVectorizationRejectionDiagnostic(location, reason, detail)));
        }
    }
}
