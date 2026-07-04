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
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrDoWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrLoopBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds conservative fixed-width lane-wise loops that may become auto-vectorization inputs later.
 */
public final class GpuIrAutoVectorizationCandidateScanner {
    private static final Set<Integer> SUPPORTED_LANE_COUNTS = Set.of(2, 3, 4, 8, 16);

    private final GpuIrExpressionClassifier expressionClassifier;
    private final GpuIrExpressionTypeResolver typeResolver;

    public GpuIrAutoVectorizationCandidateScanner() {
        this(new GpuIrExpressionClassifier(), new GpuIrExpressionTypeResolver());
    }

    public GpuIrAutoVectorizationCandidateScanner(GpuIrExpressionClassifier expressionClassifier) {
        this(expressionClassifier, new GpuIrExpressionTypeResolver());
    }

    public GpuIrAutoVectorizationCandidateScanner(
            GpuIrExpressionClassifier expressionClassifier,
            GpuIrExpressionTypeResolver typeResolver
    ) {
        this.expressionClassifier = expressionClassifier;
        this.typeResolver = typeResolver;
    }

    public GpuIrAutoVectorizationReport scan(GpuIrMethod method) {
        return scan(method, TypeLookup.unknown());
    }

    public GpuIrAutoVectorizationReport scan(GpuIrCompiledMethod method) {
        if (method == null) {
            return scan((GpuIrMethod) null);
        }
        return scan(method.irMethod(), TypeLookup.compiled(method, typeResolver));
    }

    private GpuIrAutoVectorizationReport scan(GpuIrMethod method, TypeLookup typeLookup) {
        List<GpuIrAutoVectorizationCandidate> candidates = new ArrayList<>();
        List<GpuIrAutoVectorizationRejectionDiagnostic> rejections = new ArrayList<>();
        if (method == null) {
            rejections.add(new GpuIrAutoVectorizationRejectionDiagnostic(
                    "method",
                    GpuIrAutoVectorizationRejectionReason.INCOMPLETE_IR,
                    "missing method"
            ));
            return new GpuIrAutoVectorizationReport("<missing>", candidates, rejections);
        }
        scanStatements(method.statements(), "stmt", candidates, rejections, typeLookup);
        return new GpuIrAutoVectorizationReport(method.name(), candidates, rejections);
    }

    private void scanStatements(
            List<GpuIrStatement> statements,
            String locationPrefix,
            List<GpuIrAutoVectorizationCandidate> candidates,
            List<GpuIrAutoVectorizationRejectionDiagnostic> rejections,
            TypeLookup typeLookup
    ) {
        if (statements == null) {
            rejections.add(new GpuIrAutoVectorizationRejectionDiagnostic(
                    locationPrefix,
                    GpuIrAutoVectorizationRejectionReason.INCOMPLETE_IR,
                    "missing statement list"
            ));
            return;
        }
        for (int index = 0; index < statements.size(); index++) {
            scanStatement(statements.get(index), locationPrefix + "[" + index + "]", statements, index, locationPrefix, candidates, rejections, typeLookup);
        }
    }

    private void scanStatement(
            GpuIrStatement statement,
            String location,
            List<GpuIrStatement> siblingStatements,
            int statementIndex,
            String locationPrefix,
            List<GpuIrAutoVectorizationCandidate> candidates,
            List<GpuIrAutoVectorizationRejectionDiagnostic> rejections,
            TypeLookup typeLookup
    ) {
        if (statement == null) {
            rejections.add(new GpuIrAutoVectorizationRejectionDiagnostic(
                    location,
                    GpuIrAutoVectorizationRejectionReason.INCOMPLETE_IR,
                    "missing statement"
            ));
        } else if (statement instanceof GpuIrForLoop loop) {
            ScanResult result = candidateFromLoop(location, loop, typeLookup, siblingStatements, statementIndex, locationPrefix);
            result.candidate().ifPresent(candidates::add);
            result.rejection().ifPresent(rejections::add);
            scanStatements(loop.body(), location + ".body.stmt", candidates, rejections, typeLookup);
        } else if (statement instanceof GpuIrIf gpuIf) {
            scanStatements(gpuIf.thenBranch(), location + ".then.stmt", candidates, rejections, typeLookup);
            scanStatements(gpuIf.elseBranch(), location + ".else.stmt", candidates, rejections, typeLookup);
        } else if (statement instanceof GpuIrWhileLoop loop) {
            scanStatements(loop.body(), location + ".body.stmt", candidates, rejections, typeLookup);
        } else if (statement instanceof GpuIrDoWhileLoop loop) {
            scanStatements(loop.body(), location + ".body.stmt", candidates, rejections, typeLookup);
        } else if (statement instanceof GpuIrSwitch gpuSwitch) {
            if (gpuSwitch.cases() == null) {
                rejections.add(new GpuIrAutoVectorizationRejectionDiagnostic(
                        location + ".case",
                        GpuIrAutoVectorizationRejectionReason.INCOMPLETE_IR,
                        "missing switch cases"
                ));
                return;
            }
            for (int caseIndex = 0; caseIndex < gpuSwitch.cases().size(); caseIndex++) {
                if (gpuSwitch.cases().get(caseIndex) == null) {
                    rejections.add(new GpuIrAutoVectorizationRejectionDiagnostic(
                            location + ".case[" + caseIndex + "]",
                            GpuIrAutoVectorizationRejectionReason.INCOMPLETE_IR,
                            "missing switch case"
                    ));
                } else {
                    scanStatements(gpuSwitch.cases().get(caseIndex).statements(), location + ".case[" + caseIndex + "].stmt", candidates, rejections, typeLookup);
                }
            }
        }
    }

    private ScanResult candidateFromLoop(
            String location,
            GpuIrForLoop loop,
            TypeLookup typeLookup,
            List<GpuIrStatement> siblingStatements,
            int statementIndex,
            String locationPrefix
    ) {
        Optional<LoopBounds> bounds = loopBounds(loop);
        if (bounds.isEmpty()) {
            return ScanResult.rejected(location, GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_LOOP_SHAPE, "requires int i = 0; i < laneCount; i = i + 1");
        }
        if (!SUPPORTED_LANE_COUNTS.contains(bounds.get().laneCount())) {
            return ScanResult.rejected(location, GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_LANE_COUNT, "laneCount=" + bounds.get().laneCount());
        }
        if (loop.body() == null) {
            return ScanResult.rejected(location, GpuIrAutoVectorizationRejectionReason.INCOMPLETE_IR, "missing loop body");
        }
        if (loop.body().isEmpty()) {
            return ScanResult.rejected(location, GpuIrAutoVectorizationRejectionReason.EMPTY_BODY, "loop body has no assignments");
        }

        LinkedHashSet<String> targetArrays = new LinkedHashSet<>();
        LinkedHashSet<String> sourceArrays = new LinkedHashSet<>();
        LinkedHashSet<String> repeatedTargetWarnings = new LinkedHashSet<>();
        LinkedHashSet<String> crossLaneReadWarnings = new LinkedHashSet<>();
        LinkedHashSet<String> nonLaneReadWarnings = new LinkedHashSet<>();
        LinkedHashMap<String, String> arrayElementTypes = new LinkedHashMap<>();
        int assignmentCount = 0;
        for (GpuIrStatement statement : loop.body()) {
            if (statement == null) {
                return ScanResult.rejected(location, GpuIrAutoVectorizationRejectionReason.INCOMPLETE_IR, "missing loop body statement");
            }
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
            collectTypedArrayAccess(target, arrayElementTypes, typeLookup);
            if (!targetArrays.add(target.arrayName())) {
                repeatedTargetWarnings.add("target array `" + target.arrayName() + "` is written more than once in the loop body");
            }
            collectArrayReads(assignment.value(), bounds.get().inductionVariable(), sourceArrays, crossLaneReadWarnings, nonLaneReadWarnings, arrayElementTypes, typeLookup);
            assignmentCount++;
        }
        if (assignmentCount == 0 || targetArrays.isEmpty()) {
            return ScanResult.rejected(location, GpuIrAutoVectorizationRejectionReason.EMPTY_BODY, "loop body has no lane-wise assignments");
        }
        Optional<String> scalarElementType = scalarElementType(arrayElementTypes, typeLookup.requiresKnownTypes());
        if (scalarElementType.isEmpty()) {
            return ScanResult.rejected(
                    location,
                    GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_ELEMENT_TYPE,
                    "requires one supported scalar element type across lane reads/writes; observed=" + arrayElementTypes
            );
        }
        List<GpuIrAutoVectorizationRewriteGuardDiagnostic> memoryGuardDiagnostics = new ArrayList<>(blockMutationGuards(
                location,
                siblingStatements,
                statementIndex,
                locationPrefix,
                targetArrays,
                sourceArrays
        ));
        memoryGuardDiagnostics.addAll(memoryAddressSpaceGuards(location, targetArrays, sourceArrays, typeLookup));
        return ScanResult.accepted(new GpuIrAutoVectorizationCandidate(
                location,
                bounds.get().inductionVariable(),
                bounds.get().startInclusive(),
                bounds.get().endExclusive(),
                bounds.get().laneCount(),
                List.copyOf(targetArrays),
                List.copyOf(sourceArrays),
                aliasWarnings(targetArrays, sourceArrays),
                List.copyOf(repeatedTargetWarnings),
                List.copyOf(crossLaneReadWarnings),
                List.copyOf(nonLaneReadWarnings),
                memoryGuardDiagnostics,
                assignmentCount,
                scalarElementType.orElseThrow(),
                vectorType(scalarElementType.orElseThrow(), bounds.get().laneCount())
        ));
    }

    private List<GpuIrAutoVectorizationRewriteGuardDiagnostic> memoryAddressSpaceGuards(
            String location,
            Set<String> targetArrays,
            Set<String> sourceArrays,
            TypeLookup typeLookup
    ) {
        List<GpuIrAutoVectorizationRewriteGuardDiagnostic> diagnostics = new ArrayList<>();
        for (String targetArray : targetArrays) {
            typeLookup.parameter(targetArray).ifPresent(parameter -> {
                if (parameter.addressSpace() == GpuAddressSpace.CONSTANT || parameter.constant()) {
                    diagnostics.add(rewriteGuard(
                            GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE,
                            location,
                            "target array `" + targetArray
                                    + "` uses read-only memory address space before vector rewrite safety is proven"
                    ));
                } else if (parameter.addressSpace() == GpuAddressSpace.LOCAL) {
                    diagnostics.add(rewriteGuard(
                            GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE,
                            location,
                            "target array `" + targetArray
                                    + "` uses local memory address space before vector rewrite safety is proven"
                    ));
                }
            });
        }
        for (String sourceArray : sourceArrays) {
            typeLookup.parameter(sourceArray).ifPresent(parameter -> {
                if (parameter.addressSpace() == GpuAddressSpace.CONSTANT || parameter.constant()) {
                    diagnostics.add(rewriteGuard(
                            GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE,
                            location,
                            "source array `" + sourceArray
                                    + "` uses constant memory address space before vector rewrite policy is proven"
                    ));
                } else if (parameter.addressSpace() == GpuAddressSpace.LOCAL) {
                    diagnostics.add(rewriteGuard(
                            GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE,
                            location,
                            "source array `" + sourceArray
                                    + "` uses local memory address space before vector rewrite policy is proven"
                    ));
                }
            });
        }
        return diagnostics;
    }

    private List<GpuIrAutoVectorizationRewriteGuardDiagnostic> blockMutationGuards(
            String location,
            List<GpuIrStatement> statements,
            int statementIndex,
            String locationPrefix,
            Set<String> targetArrays,
            Set<String> sourceArrays
    ) {
        List<GpuIrAutoVectorizationRewriteGuardDiagnostic> diagnostics = new ArrayList<>();
        for (int siblingIndex = 0; siblingIndex < statements.size(); siblingIndex++) {
            if (siblingIndex == statementIndex) {
                continue;
            }
            String side = siblingIndex < statementIndex ? "previous" : "next";
            collectBlockMutationGuard(diagnostics, location, statements.get(siblingIndex), siblingIndex, locationPrefix, side, targetArrays, sourceArrays);
        }
        return diagnostics;
    }

    private void collectBlockMutationGuard(
            List<GpuIrAutoVectorizationRewriteGuardDiagnostic> diagnostics,
            String location,
            GpuIrStatement sibling,
            int siblingIndex,
            String locationPrefix,
            String side,
            Set<String> targetArrays,
            Set<String> sourceArrays
    ) {
        if (isEarlyExitBoundary(sibling)) {
            diagnostics.add(rewriteGuard(
                    GpuIrAutoVectorizationRewriteGuardFamily.EARLY_EXIT_BOUNDARY,
                    location,
                    side + " statement " + locationPrefix + "[" + siblingIndex + "]"
                            + " is an early-exit boundary before vector rewrite safety is proven"
            ));
            return;
        }
        if (isControlFlowBoundary(sibling)) {
            diagnostics.add(rewriteGuard(
                    GpuIrAutoVectorizationRewriteGuardFamily.CONTROL_FLOW_BOUNDARY,
                    location,
                    side + " statement " + locationPrefix + "[" + siblingIndex + "]"
                            + " is a control-flow boundary before vector rewrite safety is proven"
            ));
            return;
        }
        if (!(sibling instanceof GpuIrAssignment assignment) || !(assignment.target() instanceof GpuIrArrayAccess target)) {
            return;
        }
        String arrayName = target.arrayName();
        String neighborLocation = locationPrefix + "[" + siblingIndex + "]";
        if (sourceArrays.contains(arrayName)) {
            diagnostics.add(rewriteGuard(
                    GpuIrAutoVectorizationRewriteGuardFamily.NEIGHBOR_SOURCE_WRITE,
                    location,
                    side + " statement " + neighborLocation + " writes source array `" + arrayName + "`"
            ));
        }
        if (targetArrays.contains(arrayName)) {
            diagnostics.add(rewriteGuard(
                    GpuIrAutoVectorizationRewriteGuardFamily.NEIGHBOR_TARGET_WRITE,
                    location,
                    side + " statement " + neighborLocation + " writes target array `" + arrayName + "`"
            ));
        }
    }

    private GpuIrAutoVectorizationRewriteGuardDiagnostic rewriteGuard(
            GpuIrAutoVectorizationRewriteGuardFamily family,
            String location,
            String message
    ) {
        return new GpuIrAutoVectorizationRewriteGuardDiagnostic(family, location, message);
    }

    private boolean isEarlyExitBoundary(GpuIrStatement statement) {
        return statement instanceof GpuIrReturn
                || statement instanceof GpuIrBreak
                || statement instanceof GpuIrContinue
                || statement instanceof GpuIrLoopBreak;
    }

    private boolean isControlFlowBoundary(GpuIrStatement statement) {
        if (statement instanceof GpuIrForLoop loop && isVectorShapedLoop(loop)) {
            return false;
        }
        return statement instanceof GpuIrIf
                || statement instanceof GpuIrForLoop
                || statement instanceof GpuIrWhileLoop
                || statement instanceof GpuIrDoWhileLoop
                || statement instanceof GpuIrSwitch;
    }

    private boolean isVectorShapedLoop(GpuIrForLoop loop) {
        Optional<LoopBounds> bounds = loopBounds(loop);
        if (bounds.isEmpty() || !SUPPORTED_LANE_COUNTS.contains(bounds.get().laneCount()) || loop.body() == null || loop.body().isEmpty()) {
            return false;
        }
        for (GpuIrStatement statement : loop.body()) {
            if (!(statement instanceof GpuIrAssignment assignment)
                    || !(assignment.target() instanceof GpuIrArrayAccess target)
                    || !isVariableRef(target.index(), bounds.get().inductionVariable())
                    || expressionClassifier.mayHaveSideEffects(assignment.value())) {
                return false;
            }
        }
        return true;
    }

    private Optional<String> scalarElementType(Map<String, String> arrayElementTypes, boolean requiresKnownTypes) {
        if (requiresKnownTypes && arrayElementTypes.values().stream().anyMatch(type -> type == null || type.isBlank())) {
            return Optional.empty();
        }
        LinkedHashSet<String> knownTypes = new LinkedHashSet<>(arrayElementTypes.values());
        knownTypes.removeIf(type -> type == null || type.isBlank());
        if (knownTypes.isEmpty()) {
            return requiresKnownTypes ? Optional.empty() : Optional.of("unknown");
        }
        if (knownTypes.size() != 1) {
            return Optional.empty();
        }
        String type = GpuTypeSupport.declaredType(knownTypes.iterator().next());
        if (!GpuTypeSupport.isSupportedScalarType(type) || "boolean".equals(type)) {
            return Optional.empty();
        }
        return Optional.of(type);
    }

    private String vectorType(String scalarElementType, int laneCount) {
        return "unknown".equals(scalarElementType) ? "unknownx" + laneCount : scalarElementType + laneCount;
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
            Set<String> crossLaneReadWarnings,
            Set<String> nonLaneReadWarnings,
            Map<String, String> arrayElementTypes,
            TypeLookup typeLookup
    ) {
        if (expression == null) {
            return;
        }
        if (expression instanceof GpuIrArrayAccess arrayAccess) {
            sourceArrays.add(arrayAccess.arrayName());
            collectTypedArrayAccess(arrayAccess, arrayElementTypes, typeLookup);
            Optional<Integer> laneRelativeOffset = laneRelativeOffset(arrayAccess.index(), inductionVariable);
            if (laneRelativeOffset.isPresent() && laneRelativeOffset.get() != 0) {
                crossLaneReadWarnings.add("array `" + arrayAccess.arrayName()
                            + "` is read at cross-lane offset " + signedOffset(laneRelativeOffset.get())
                            + " from `" + inductionVariable + "`");
            } else if (laneRelativeOffset.isEmpty()) {
                nonLaneReadWarnings.add("array `" + arrayAccess.arrayName()
                        + "` is read with non-lane index " + indexSummary(arrayAccess.index())
                        + " instead of `" + inductionVariable + "`");
            }
            collectArrayReads(arrayAccess.index(), inductionVariable, sourceArrays, crossLaneReadWarnings, nonLaneReadWarnings, arrayElementTypes, typeLookup);
        } else if (expression instanceof GpuIrBinary binary) {
            collectArrayReads(binary.left(), inductionVariable, sourceArrays, crossLaneReadWarnings, nonLaneReadWarnings, arrayElementTypes, typeLookup);
            collectArrayReads(binary.right(), inductionVariable, sourceArrays, crossLaneReadWarnings, nonLaneReadWarnings, arrayElementTypes, typeLookup);
        } else if (expression instanceof GpuIrUnary unary) {
            collectArrayReads(unary.operand(), inductionVariable, sourceArrays, crossLaneReadWarnings, nonLaneReadWarnings, arrayElementTypes, typeLookup);
        } else if (expression instanceof GpuIrCast cast) {
            collectArrayReads(cast.expression(), inductionVariable, sourceArrays, crossLaneReadWarnings, nonLaneReadWarnings, arrayElementTypes, typeLookup);
        } else if (expression instanceof GpuIrTernary ternary) {
            collectArrayReads(ternary.condition(), inductionVariable, sourceArrays, crossLaneReadWarnings, nonLaneReadWarnings, arrayElementTypes, typeLookup);
            collectArrayReads(ternary.whenTrue(), inductionVariable, sourceArrays, crossLaneReadWarnings, nonLaneReadWarnings, arrayElementTypes, typeLookup);
            collectArrayReads(ternary.whenFalse(), inductionVariable, sourceArrays, crossLaneReadWarnings, nonLaneReadWarnings, arrayElementTypes, typeLookup);
        } else if (expression instanceof GpuIrFieldAccess fieldAccess) {
            collectArrayReads(fieldAccess.target(), inductionVariable, sourceArrays, crossLaneReadWarnings, nonLaneReadWarnings, arrayElementTypes, typeLookup);
        } else if (expression instanceof GpuIrStructInit structInit) {
            if (structInit.arguments() == null) {
                return;
            }
            for (GpuIrExpression argument : structInit.arguments()) {
                collectArrayReads(argument, inductionVariable, sourceArrays, crossLaneReadWarnings, nonLaneReadWarnings, arrayElementTypes, typeLookup);
            }
        } else if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            collectArrayReads(intrinsicCall.receiver(), inductionVariable, sourceArrays, crossLaneReadWarnings, nonLaneReadWarnings, arrayElementTypes, typeLookup);
            if (intrinsicCall.arguments() == null) {
                return;
            }
            for (GpuIrExpression argument : intrinsicCall.arguments()) {
                collectArrayReads(argument, inductionVariable, sourceArrays, crossLaneReadWarnings, nonLaneReadWarnings, arrayElementTypes, typeLookup);
            }
        } else if (expression instanceof GpuIrHelperCall helperCall) {
            if (helperCall.arguments() == null) {
                return;
            }
            for (GpuIrExpression argument : helperCall.arguments()) {
                collectArrayReads(argument, inductionVariable, sourceArrays, crossLaneReadWarnings, nonLaneReadWarnings, arrayElementTypes, typeLookup);
            }
        }
    }

    private void collectTypedArrayAccess(
            GpuIrArrayAccess arrayAccess,
            Map<String, String> arrayElementTypes,
            TypeLookup typeLookup
    ) {
        arrayElementTypes.putIfAbsent(arrayAccess.arrayName(), typeLookup.elementType(arrayAccess).orElse(null));
    }

    private Optional<Integer> laneRelativeOffset(GpuIrExpression index, String inductionVariable) {
        if (isVariableRef(index, inductionVariable)) {
            return Optional.of(0);
        }
        if (!(index instanceof GpuIrBinary binary)) {
            return Optional.empty();
        }
        return switch (binary.operator()) {
            case "+" -> offsetFromAddition(binary, inductionVariable);
            case "-" -> isVariableRef(binary.left(), inductionVariable)
                    ? intLiteral(binary.right()).map(value -> -value)
                    : Optional.empty();
            default -> Optional.empty();
        };
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

    private String indexSummary(GpuIrExpression index) {
        if (index instanceof GpuIrVariableRef variableRef) {
            return "`" + variableRef.name() + "`";
        }
        if (index instanceof GpuIrLiteral literal) {
            return literal.sourceText();
        }
        if (index instanceof GpuIrBinary binary) {
            return indexSummary(binary.left()) + " " + binary.operator() + " " + indexSummary(binary.right());
        }
        return index.getClass().getSimpleName();
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

    private record TypeLookup(GpuIrCompiledMethod method, GpuIrExpressionTypeResolver typeResolver) {
        static TypeLookup unknown() {
            return new TypeLookup(null, null);
        }

        static TypeLookup compiled(GpuIrCompiledMethod method, GpuIrExpressionTypeResolver typeResolver) {
            return new TypeLookup(method, typeResolver);
        }

        boolean requiresKnownTypes() {
            return method != null;
        }

        Optional<String> elementType(GpuIrArrayAccess arrayAccess) {
            if (method == null || typeResolver == null) {
                return Optional.empty();
            }
            return typeResolver.typeOf(method, arrayAccess);
        }

        Optional<ParsedGpuParameter> parameter(String name) {
            if (method == null || method.parsedMethod() == null || method.parsedMethod().parameters() == null) {
                return Optional.empty();
            }
            return method.parsedMethod().parameters().stream()
                    .filter(parameter -> name.equals(parameter.name()))
                    .findFirst();
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
