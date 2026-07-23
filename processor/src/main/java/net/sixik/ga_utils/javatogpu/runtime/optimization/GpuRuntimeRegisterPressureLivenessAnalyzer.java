package net.sixik.ga_utils.javatogpu.runtime.optimization;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes conservative statement-level liveness for flattened typed IrGpu bodies.
 */
final class GpuRuntimeRegisterPressureLivenessAnalyzer {

    private static final int MAX_FIXED_POINT_ITERATIONS = 64;

    private final Map<Integer, IrGpuTypedNode> nodes = new HashMap<>();
    private final Map<String, Integer> variableWeights = new LinkedHashMap<>();
    private final Map<Integer, LiveState> liveStates = new HashMap<>();
    private final Map<Integer, CallSite> callSites = new LinkedHashMap<>();
    private final List<Integer> rootNodeIds;
    private final GpuRuntimeRegisterPressureVariableResolver.Resolution variableResolution;
    private int localRegisters;
    private int privateArrayRegisters;
    private int expressionPeakRegisters;
    private int peakLiveRegisters;

    private GpuRuntimeRegisterPressureLivenessAnalyzer(
            IrGpuTypedBody typedBody,
            Map<String, String> parameterTypes
    ) {
        for (IrGpuTypedNode node : typedBody.nodes()) {
            nodes.put(node.id(), node);
        }
        rootNodeIds = typedBody.rootNodeIds();
        variableResolution = GpuRuntimeRegisterPressureVariableResolver.resolve(typedBody, parameterTypes);
        variableWeights.putAll(variableResolution.variableWeights());
        localRegisters = variableResolution.localRegisters();
        privateArrayRegisters = variableResolution.privateArrayRegisters();
    }

    static Estimate analyze(
            IrGpuTypedBody typedBody,
            Map<String, String> parameterTypes
    ) {
        GpuRuntimeRegisterPressureLivenessAnalyzer analyzer =
                new GpuRuntimeRegisterPressureLivenessAnalyzer(
                        typedBody,
                        parameterTypes
                );
        return analyzer.analyze();
    }

    private Estimate analyze() {
        Set<String> liveAtEntry = analyzeSequence(
                rootNodeIds,
                Set.of(),
                FlowTargets.none()
        );
        peakLiveRegisters = Math.max(peakLiveRegisters, liveWeight(liveAtEntry));
        int estimatedRegisters = peakLiveRegisters;
        for (Map.Entry<Integer, LiveState> entry : liveStates.entrySet()) {
            IrGpuTypedNode node = nodes.get(entry.getKey());
            if (node == null) {
                continue;
            }
            LiveState state = entry.getValue();
            int liveInRegisters = liveWeight(state.liveIn());
            int liveOutRegisters = liveWeight(state.liveOut());
            int temporaryRegisters = statementTemporaryPressure(node);
            peakLiveRegisters = Math.max(peakLiveRegisters, Math.max(liveInRegisters, liveOutRegisters));
            expressionPeakRegisters = Math.max(expressionPeakRegisters, temporaryRegisters);
            collectStatementCallSites(node, liveInRegisters);
            estimatedRegisters = Math.max(
                    estimatedRegisters,
                    Math.max(liveOutRegisters, liveInRegisters + temporaryRegisters)
            );
        }
        return new Estimate(
                estimatedRegisters,
                localRegisters,
                privateArrayRegisters,
                peakLiveRegisters,
                expressionPeakRegisters,
                variableResolution.scopedVariableCount(),
                variableResolution.shadowedVariableCount(),
                variableResolution.unresolvedReferenceCount(),
                List.copyOf(callSites.values())
        );
    }

    private void collectStatementCallSites(IrGpuTypedNode node, int liveInRegisters) {
        for (Integer expressionId : statementExpressionIds(node)) {
            collectExpressionCallSites(expressionId, liveInRegisters, new HashSet<>());
        }
    }

    private void collectExpressionCallSites(
            Integer expressionId,
            int liveInRegisters,
            Set<Integer> visiting
    ) {
        if (expressionId == null) {
            return;
        }
        IrGpuTypedNode node = nodes.get(expressionId);
        if (node == null || !visiting.add(expressionId)) {
            return;
        }
        try {
            if ("GpuIrHelperCall".equals(node.kind())) {
                List<Integer> argumentIds = node.children().getOrDefault("arguments", List.of());
                LinkedHashSet<String> argumentUses = new LinkedHashSet<>();
                for (Integer argumentId : argumentIds) {
                    argumentUses.addAll(expressionUses(argumentId));
                }
                int argumentRegisters = liveWeight(argumentUses)
                        + expressionListTemporaryPressure(argumentIds);
                callSites.putIfAbsent(node.id(), new CallSite(
                        node.id(),
                        node.attributes().getOrDefault("helperName", "unknown"),
                        liveInRegisters,
                        argumentRegisters,
                        temporaryResultWeight(node.id())
                ));
            }
            for (Integer childId : orderedChildIds(node)) {
                collectExpressionCallSites(childId, liveInRegisters, visiting);
            }
        } finally {
            visiting.remove(expressionId);
        }
    }

    private static List<Integer> statementExpressionIds(IrGpuTypedNode node) {
        return switch (node.kind()) {
            case "GpuIrVariableDeclaration" -> childList(node, "initializer");
            case "GpuIrPrivateArrayDeclaration" -> childList(node, "size");
            case "GpuIrAssignment" -> concat(childList(node, "target"), childList(node, "value"));
            case "GpuIrReturn" -> childList(node, "value");
            case "GpuIrExpressionStatement" -> childList(node, "expression");
            case "GpuIrIf", "GpuIrWhileLoop", "GpuIrDoWhileLoop", "GpuIrForLoop" ->
                    childList(node, "condition");
            case "GpuIrSwitch" -> childList(node, "selector");
            case "GpuIrSwitchCase" -> childList(node, "labels");
            default -> List.of();
        };
    }

    private static List<Integer> childList(IrGpuTypedNode node, String name) {
        return node.children().getOrDefault(name, List.of());
    }

    private static List<Integer> concat(List<Integer> first, List<Integer> second) {
        ArrayList<Integer> values = new ArrayList<>(first.size() + second.size());
        values.addAll(first);
        values.addAll(second);
        return List.copyOf(values);
    }

    private Set<String> analyzeSequence(
            List<Integer> statementIds,
            Set<String> liveOut,
            FlowTargets targets
    ) {
        LinkedHashSet<String> live = copy(liveOut);
        List<Integer> ids = statementIds == null ? List.of() : statementIds;
        for (int index = ids.size() - 1; index >= 0; index--) {
            int statementId = ids.get(index);
            LiveState state = analyzeStatement(statementId, live, targets);
            liveStates.put(statementId, state);
            live = copy(state.liveIn());
        }
        return Set.copyOf(live);
    }

    private LiveState analyzeStatement(
            int statementId,
            Set<String> requestedLiveOut,
            FlowTargets targets
    ) {
        IrGpuTypedNode node = nodes.get(statementId);
        if (node == null) {
            return new LiveState(requestedLiveOut, requestedLiveOut);
        }
        Set<String> liveOut = switch (node.kind()) {
            case "GpuIrReturn" -> Set.of();
            case "GpuIrBreak", "GpuIrLoopBreak" -> targets.breakLive();
            case "GpuIrContinue" -> targets.continueLive();
            default -> Set.copyOf(requestedLiveOut);
        };
        Set<String> liveIn = switch (node.kind()) {
            case "GpuIrVariableDeclaration" -> applyUsesAndDefs(
                    liveOut,
                    expressionUses(firstChild(node, "initializer")),
                    Set.of(variableResolution.declarationKey(node))
            );
            case "GpuIrPrivateArrayDeclaration" -> applyUsesAndDefs(
                    liveOut,
                    expressionUses(firstChild(node, "size")),
                    Set.of(variableResolution.declarationKey(node))
            );
            case "GpuIrAssignment" -> assignmentLiveIn(node, liveOut);
            case "GpuIrReturn" -> expressionUses(firstChild(node, "value"));
            case "GpuIrExpressionStatement" -> union(
                    liveOut,
                    expressionUses(firstChild(node, "expression"))
            );
            case "GpuIrIf" -> ifLiveIn(node, liveOut, targets);
            case "GpuIrForLoop" -> forLoopLiveIn(node, liveOut, targets);
            case "GpuIrWhileLoop", "GpuIrDoWhileLoop" -> whileLoopLiveIn(node, liveOut);
            case "GpuIrSwitch" -> switchLiveIn(node, liveOut, targets);
            case "GpuIrSwitchCase" -> switchCaseLiveIn(node, liveOut, targets);
            case "GpuIrBreak", "GpuIrLoopBreak" -> targets.breakLive();
            case "GpuIrContinue" -> targets.continueLive();
            default -> union(liveOut, directExpressionUses(node));
        };
        LiveState state = new LiveState(liveIn, liveOut);
        liveStates.put(statementId, state);
        return state;
    }

    private Set<String> assignmentLiveIn(IrGpuTypedNode node, Set<String> liveOut) {
        Integer targetId = firstChild(node, "target");
        IrGpuTypedNode target = targetId == null ? null : nodes.get(targetId);
        Set<String> uses = expressionUses(firstChild(node, "value"));
        if (target != null && "GpuIrVariableRef".equals(target.kind())) {
            return applyUsesAndDefs(
                    liveOut,
                    uses,
                    Set.of(variableResolution.referenceKey(target))
            );
        }
        return union(liveOut, uses, expressionUses(targetId));
    }

    private Set<String> ifLiveIn(
            IrGpuTypedNode node,
            Set<String> liveOut,
            FlowTargets targets
    ) {
        Set<String> thenLive = analyzeSequence(
                node.children().getOrDefault("thenBranch", List.of()),
                liveOut,
                targets
        );
        Set<String> elseLive = analyzeSequence(
                node.children().getOrDefault("elseBranch", List.of()),
                liveOut,
                targets
        );
        return union(
                expressionUses(firstChild(node, "condition")),
                thenLive,
                elseLive
        );
    }

    private Set<String> whileLoopLiveIn(IrGpuTypedNode node, Set<String> liveOut) {
        Set<String> conditionUses = expressionUses(firstChild(node, "condition"));
        LinkedHashSet<String> loopHead = copy(union(liveOut, conditionUses));
        for (int iteration = 0; iteration < MAX_FIXED_POINT_ITERATIONS; iteration++) {
            FlowTargets loopTargets = new FlowTargets(liveOut, loopHead);
            Set<String> bodyLive = analyzeSequence(
                    node.children().getOrDefault("body", List.of()),
                    loopHead,
                    loopTargets
            );
            Set<String> next = union(liveOut, conditionUses, bodyLive);
            if (loopHead.equals(next)) {
                break;
            }
            loopHead = copy(next);
        }
        return Set.copyOf(loopHead);
    }

    private Set<String> forLoopLiveIn(
            IrGpuTypedNode node,
            Set<String> liveOut,
            FlowTargets outerTargets
    ) {
        Set<String> conditionUses = expressionUses(firstChild(node, "condition"));
        LinkedHashSet<String> loopHead = copy(union(liveOut, conditionUses));
        for (int iteration = 0; iteration < MAX_FIXED_POINT_ITERATIONS; iteration++) {
            Set<String> updateLive = analyzeSequence(
                    node.children().getOrDefault("update", List.of()),
                    loopHead,
                    new FlowTargets(liveOut, loopHead)
            );
            Set<String> bodyLive = analyzeSequence(
                    node.children().getOrDefault("body", List.of()),
                    updateLive,
                    new FlowTargets(liveOut, updateLive)
            );
            Set<String> next = union(liveOut, conditionUses, bodyLive);
            if (loopHead.equals(next)) {
                break;
            }
            loopHead = copy(next);
        }
        return analyzeSequence(
                node.children().getOrDefault("initializer", List.of()),
                loopHead,
                outerTargets
        );
    }

    private Set<String> switchLiveIn(
            IrGpuTypedNode node,
            Set<String> liveOut,
            FlowTargets outerTargets
    ) {
        LinkedHashSet<String> liveIn = copy(expressionUses(firstChild(node, "selector")));
        List<Integer> cases = node.children().getOrDefault("cases", List.of());
        Set<String> fallthroughLive = liveOut;
        for (int index = cases.size() - 1; index >= 0; index--) {
            int caseId = cases.get(index);
            LiveState caseState = analyzeStatement(
                    caseId,
                    fallthroughLive,
                    new FlowTargets(liveOut, outerTargets.continueLive())
            );
            liveIn.addAll(caseState.liveIn());
            fallthroughLive = caseState.liveIn();
        }
        liveIn.addAll(liveOut);
        return Set.copyOf(liveIn);
    }

    private Set<String> switchCaseLiveIn(
            IrGpuTypedNode node,
            Set<String> liveOut,
            FlowTargets targets
    ) {
        Set<String> statementsLive = analyzeSequence(
                node.children().getOrDefault("statements", List.of()),
                liveOut,
                targets
        );
        LinkedHashSet<String> liveIn = copy(statementsLive);
        for (Integer labelId : node.children().getOrDefault("labels", List.of())) {
            liveIn.addAll(expressionUses(labelId));
        }
        return Set.copyOf(liveIn);
    }

    private int statementTemporaryPressure(IrGpuTypedNode node) {
        return switch (node.kind()) {
            case "GpuIrVariableDeclaration" -> expressionTemporaryPressure(firstChild(node, "initializer"));
            case "GpuIrPrivateArrayDeclaration" -> expressionTemporaryPressure(firstChild(node, "size"));
            case "GpuIrAssignment" -> expressionListTemporaryPressure(List.of(
                    firstChild(node, "target"),
                    firstChild(node, "value")
            ));
            case "GpuIrReturn" -> expressionTemporaryPressure(firstChild(node, "value"));
            case "GpuIrExpressionStatement" -> expressionTemporaryPressure(firstChild(node, "expression"));
            case "GpuIrIf", "GpuIrWhileLoop", "GpuIrDoWhileLoop", "GpuIrForLoop" ->
                    expressionTemporaryPressure(firstChild(node, "condition"));
            case "GpuIrSwitch" -> expressionTemporaryPressure(firstChild(node, "selector"));
            case "GpuIrSwitchCase" -> expressionListTemporaryPressure(
                    node.children().getOrDefault("labels", List.of())
            );
            default -> 0;
        };
    }

    private int expressionListTemporaryPressure(List<Integer> expressionIds) {
        int peak = 0;
        int held = 0;
        for (Integer expressionId : expressionIds == null ? List.<Integer>of() : expressionIds) {
            if (expressionId == null) {
                continue;
            }
            int childPeak = expressionTemporaryPressure(expressionId);
            peak = Math.max(peak, held + childPeak);
            held += temporaryResultWeight(expressionId);
        }
        return peak;
    }

    private int expressionTemporaryPressure(Integer expressionId) {
        return expressionTemporaryPressure(expressionId, new HashSet<>());
    }

    private int expressionTemporaryPressure(Integer expressionId, Set<Integer> visiting) {
        if (expressionId == null) {
            return 0;
        }
        IrGpuTypedNode node = nodes.get(expressionId);
        if (node == null || !visiting.add(expressionId)) {
            return 0;
        }
        try {
            if ("GpuIrVariableRef".equals(node.kind())) {
                return 0;
            }
            if ("GpuIrLiteral".equals(node.kind())) {
                return GpuRuntimeRegisterPressureAnalyzer.literalWeight(
                        node.attributes().get("sourceText")
                );
            }
            if ("GpuIrTernary".equals(node.kind())) {
                return Math.max(
                        temporaryResultWeight(expressionId),
                        Math.max(
                                expressionTemporaryPressure(firstChild(node, "condition"), visiting),
                                Math.max(
                                        expressionTemporaryPressure(firstChild(node, "whenTrue"), visiting),
                                        expressionTemporaryPressure(firstChild(node, "whenFalse"), visiting)
                                )
                        )
                );
            }
            int peak = temporaryResultWeight(expressionId);
            int held = 0;
            for (Integer childId : orderedChildIds(node)) {
                int childPeak = expressionTemporaryPressure(childId, visiting);
                peak = Math.max(peak, held + childPeak);
                held += temporaryResultWeight(childId);
            }
            return peak;
        } finally {
            visiting.remove(expressionId);
        }
    }

    private int temporaryResultWeight(Integer expressionId) {
        IrGpuTypedNode node = expressionId == null ? null : nodes.get(expressionId);
        if (node == null || "GpuIrVariableRef".equals(node.kind())) {
            return 0;
        }
        return switch (node.kind()) {
            case "GpuIrLiteral" -> GpuRuntimeRegisterPressureAnalyzer.literalWeight(
                    node.attributes().get("sourceText")
            );
            case "GpuIrCast" -> GpuRuntimeRegisterPressureAnalyzer.typeWeight(
                    node.attributes().get("targetType")
            );
            case "GpuIrHelperCall", "GpuIrIntrinsicCall" -> GpuRuntimeRegisterPressureAnalyzer.typeWeight(
                    node.attributes().get("resultType")
            );
            case "GpuIrStructInit" -> GpuRuntimeRegisterPressureAnalyzer.typeWeight(
                    node.attributes().get("structType")
            );
            case "GpuIrReturn", "GpuIrAssignment", "GpuIrExpressionStatement",
                 "GpuIrIf", "GpuIrForLoop", "GpuIrWhileLoop", "GpuIrDoWhileLoop",
                 "GpuIrSwitch", "GpuIrSwitchCase", "GpuIrBreak", "GpuIrContinue",
                 "GpuIrLoopBreak", "GpuIrVariableDeclaration", "GpuIrPrivateArrayDeclaration" -> 0;
            default -> 1;
        };
    }

    private Set<String> directExpressionUses(IrGpuTypedNode node) {
        LinkedHashSet<String> uses = new LinkedHashSet<>();
        for (Integer childId : orderedChildIds(node)) {
            uses.addAll(expressionUses(childId));
        }
        return Set.copyOf(uses);
    }

    private Set<String> expressionUses(Integer expressionId) {
        LinkedHashSet<String> uses = new LinkedHashSet<>();
        collectExpressionUses(expressionId, uses, new HashSet<>());
        return Set.copyOf(uses);
    }

    private void collectExpressionUses(
            Integer expressionId,
            Set<String> uses,
            Set<Integer> visiting
    ) {
        if (expressionId == null) {
            return;
        }
        IrGpuTypedNode node = nodes.get(expressionId);
        if (node == null || !visiting.add(expressionId)) {
            return;
        }
        try {
            if ("GpuIrVariableRef".equals(node.kind())) {
                uses.add(variableResolution.referenceKey(node));
            } else if ("GpuIrArrayAccess".equals(node.kind())) {
                uses.add(variableResolution.arrayReferenceKey(node));
            }
            for (Integer childId : orderedChildIds(node)) {
                collectExpressionUses(childId, uses, visiting);
            }
        } finally {
            visiting.remove(expressionId);
        }
    }

    private int liveWeight(Set<String> liveVariables) {
        int registers = 0;
        for (String variable : liveVariables) {
            registers += variableWeights.getOrDefault(variable, 1);
        }
        return registers;
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... values) {
        LinkedHashSet<String> union = new LinkedHashSet<>();
        if (values != null) {
            for (Set<String> value : values) {
                if (value != null) {
                    union.addAll(value);
                }
            }
        }
        return Set.copyOf(union);
    }

    private static Set<String> applyUsesAndDefs(
            Set<String> liveOut,
            Set<String> uses,
            Set<String> definitions
    ) {
        LinkedHashSet<String> liveIn = copy(liveOut);
        liveIn.removeAll(definitions);
        liveIn.addAll(uses);
        return Set.copyOf(liveIn);
    }

    private static LinkedHashSet<String> copy(Set<String> values) {
        return new LinkedHashSet<>(values == null ? Set.of() : values);
    }

    private static Integer firstChild(IrGpuTypedNode node, String name) {
        List<Integer> childIds = node.children().getOrDefault(name, List.of());
        return childIds.isEmpty() ? null : childIds.get(0);
    }

    private static List<Integer> orderedChildIds(IrGpuTypedNode node) {
        ArrayList<Integer> childIds = new ArrayList<>();
        node.children().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> childIds.addAll(entry.getValue()));
        return childIds;
    }

    record Estimate(
            int estimatedRegisters,
            int localRegisters,
            int privateArrayRegisters,
            int peakLiveRegisters,
            int expressionPeakRegisters,
            int scopedVariableCount,
            int shadowedVariableCount,
            int unresolvedReferenceCount,
            List<CallSite> callSites
    ) {
        Estimate {
            callSites = callSites == null ? List.of() : List.copyOf(callSites);
        }
    }

    record CallSite(
            int nodeId,
            String helperName,
            int callerLiveRegisters,
            int argumentRegisters,
            int resultRegisters
    ) {
        CallSite {
            helperName = helperName == null || helperName.isBlank() ? "unknown" : helperName;
            callerLiveRegisters = Math.max(0, callerLiveRegisters);
            argumentRegisters = Math.max(0, argumentRegisters);
            resultRegisters = Math.max(0, resultRegisters);
        }
    }

    private record LiveState(Set<String> liveIn, Set<String> liveOut) {
        private LiveState {
            liveIn = liveIn == null ? Set.of() : Set.copyOf(liveIn);
            liveOut = liveOut == null ? Set.of() : Set.copyOf(liveOut);
        }
    }

    private record FlowTargets(Set<String> breakLive, Set<String> continueLive) {
        private FlowTargets {
            breakLive = breakLive == null ? Set.of() : Set.copyOf(breakLive);
            continueLive = continueLive == null ? Set.of() : Set.copyOf(continueLive);
        }

        private static FlowTargets none() {
            return new FlowTargets(Set.of(), Set.of());
        }
    }
}
