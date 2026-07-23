package net.sixik.ga_utils.javatogpu.runtime.optimization;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves name-only typed IrGpu references to stable lexical declaration identities.
 */
final class GpuRuntimeRegisterPressureVariableResolver {

    private final Map<Integer, IrGpuTypedNode> nodes = new HashMap<>();
    private final Deque<LinkedHashMap<String, String>> scopes = new ArrayDeque<>();
    private final LinkedHashMap<Integer, String> declarationKeys = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, String> referenceKeys = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, String> arrayReferenceKeys = new LinkedHashMap<>();
    private final LinkedHashMap<String, Integer> variableWeights = new LinkedHashMap<>();
    private int localRegisters;
    private int privateArrayRegisters;
    private int scopedVariableCount;
    private int shadowedVariableCount;
    private int unresolvedReferenceCount;

    private GpuRuntimeRegisterPressureVariableResolver(
            IrGpuTypedBody typedBody,
            Map<String, String> parameterTypes
    ) {
        for (IrGpuTypedNode node : typedBody.nodes()) {
            nodes.put(node.id(), node);
        }
        scopes.push(new LinkedHashMap<>());
        int parameterIndex = 0;
        if (parameterTypes != null) {
            for (Map.Entry<String, String> parameter : parameterTypes.entrySet()) {
                String key = "parameter:" + parameterIndex + ":" + parameter.getKey();
                scopes.peek().put(parameter.getKey(), key);
                variableWeights.put(key, GpuRuntimeRegisterPressureAnalyzer.typeWeight(parameter.getValue()));
                scopedVariableCount++;
                parameterIndex++;
            }
        }
        resolveSequence(typedBody.rootNodeIds());
    }

    static Resolution resolve(
            IrGpuTypedBody typedBody,
            Map<String, String> parameterTypes
    ) {
        GpuRuntimeRegisterPressureVariableResolver resolver =
                new GpuRuntimeRegisterPressureVariableResolver(typedBody, parameterTypes);
        return new Resolution(
                resolver.declarationKeys,
                resolver.referenceKeys,
                resolver.arrayReferenceKeys,
                resolver.variableWeights,
                resolver.localRegisters,
                resolver.privateArrayRegisters,
                resolver.scopedVariableCount,
                resolver.shadowedVariableCount,
                resolver.unresolvedReferenceCount
        );
    }

    private void resolveSequence(List<Integer> statementIds) {
        if (statementIds == null) {
            return;
        }
        for (Integer statementId : statementIds) {
            resolveStatement(statementId);
        }
    }

    private void resolveStatement(Integer statementId) {
        IrGpuTypedNode node = statementId == null ? null : nodes.get(statementId);
        if (node == null) {
            return;
        }
        switch (node.kind()) {
            case "GpuIrVariableDeclaration" -> {
                resolveExpression(firstChild(node, "initializer"), new HashSet<>());
                declare(node, false);
            }
            case "GpuIrPrivateArrayDeclaration" -> {
                resolveExpression(firstChild(node, "size"), new HashSet<>());
                declare(node, true);
            }
            case "GpuIrAssignment" -> {
                resolveExpression(firstChild(node, "target"), new HashSet<>());
                resolveExpression(firstChild(node, "value"), new HashSet<>());
            }
            case "GpuIrReturn" -> resolveExpression(firstChild(node, "value"), new HashSet<>());
            case "GpuIrExpressionStatement" ->
                    resolveExpression(firstChild(node, "expression"), new HashSet<>());
            case "GpuIrIf" -> {
                resolveExpression(firstChild(node, "condition"), new HashSet<>());
                resolveNestedSequence(node.children().getOrDefault("thenBranch", List.of()));
                resolveNestedSequence(node.children().getOrDefault("elseBranch", List.of()));
            }
            case "GpuIrForLoop" -> resolveForLoop(node);
            case "GpuIrWhileLoop", "GpuIrDoWhileLoop" -> {
                resolveExpression(firstChild(node, "condition"), new HashSet<>());
                resolveNestedSequence(node.children().getOrDefault("body", List.of()));
            }
            case "GpuIrSwitch" -> resolveSwitch(node);
            case "GpuIrSwitchCase" -> resolveSwitchCase(node);
            default -> {
                for (Integer childId : orderedChildIds(node)) {
                    resolveExpression(childId, new HashSet<>());
                }
            }
        }
    }

    private void resolveForLoop(IrGpuTypedNode node) {
        pushScope();
        try {
            resolveSequence(node.children().getOrDefault("initializer", List.of()));
            resolveExpression(firstChild(node, "condition"), new HashSet<>());
            resolveNestedSequence(node.children().getOrDefault("body", List.of()));
            resolveSequence(node.children().getOrDefault("update", List.of()));
        } finally {
            popScope();
        }
    }

    private void resolveSwitch(IrGpuTypedNode node) {
        resolveExpression(firstChild(node, "selector"), new HashSet<>());
        pushScope();
        try {
            for (Integer caseId : node.children().getOrDefault("cases", List.of())) {
                IrGpuTypedNode switchCase = nodes.get(caseId);
                if (switchCase != null) {
                    resolveSwitchCase(switchCase);
                }
            }
        } finally {
            popScope();
        }
    }

    private void resolveSwitchCase(IrGpuTypedNode node) {
        for (Integer labelId : node.children().getOrDefault("labels", List.of())) {
            resolveExpression(labelId, new HashSet<>());
        }
        resolveSequence(node.children().getOrDefault("statements", List.of()));
    }

    private void resolveNestedSequence(List<Integer> statementIds) {
        pushScope();
        try {
            resolveSequence(statementIds);
        } finally {
            popScope();
        }
    }

    private void resolveExpression(Integer expressionId, Set<Integer> visiting) {
        if (expressionId == null) {
            return;
        }
        IrGpuTypedNode node = nodes.get(expressionId);
        if (node == null || !visiting.add(expressionId)) {
            return;
        }
        try {
            if ("GpuIrVariableRef".equals(node.kind())) {
                referenceKeys.put(node.id(), resolveReference(
                        node.id(),
                        node.attributes().getOrDefault("name", "unknown")
                ));
            } else if ("GpuIrArrayAccess".equals(node.kind())) {
                arrayReferenceKeys.put(node.id(), resolveReference(
                        node.id(),
                        node.attributes().getOrDefault("arrayName", "unknown")
                ));
            }
            for (Integer childId : orderedChildIds(node)) {
                resolveExpression(childId, visiting);
            }
        } finally {
            visiting.remove(expressionId);
        }
    }

    private void declare(IrGpuTypedNode node, boolean privateArray) {
        String name = node.attributes().getOrDefault("name", "unknown");
        if (lookup(name) != null) {
            shadowedVariableCount++;
        }
        String key = (privateArray ? "private:" : "local:") + node.id() + ":" + name;
        declarationKeys.put(node.id(), key);
        scopes.peek().put(name, key);
        int weight = privateArray
                ? privateArraySlots(node)
                : GpuRuntimeRegisterPressureAnalyzer.typeWeight(node.attributes().get("typeName"));
        variableWeights.put(key, weight);
        scopedVariableCount++;
        if (privateArray) {
            privateArrayRegisters += weight;
        } else {
            localRegisters += weight;
        }
    }

    private String resolveReference(int nodeId, String name) {
        String resolved = lookup(name);
        if (resolved != null) {
            return resolved;
        }
        String key = "unresolved:" + nodeId + ":" + name;
        variableWeights.putIfAbsent(key, 1);
        unresolvedReferenceCount++;
        return key;
    }

    private String lookup(String name) {
        for (Map<String, String> scope : scopes) {
            String key = scope.get(name);
            if (key != null) {
                return key;
            }
        }
        return null;
    }

    private int privateArraySlots(IrGpuTypedNode node) {
        int elementWeight = GpuRuntimeRegisterPressureAnalyzer.typeWeight(
                node.attributes().get("elementType")
        );
        Integer sizeNodeId = firstChild(node, "size");
        IrGpuTypedNode sizeNode = sizeNodeId == null ? null : nodes.get(sizeNodeId);
        int length = sizeNode == null || !"GpuIrLiteral".equals(sizeNode.kind())
                ? GpuRuntimeRegisterPressureAnalyzer.UNKNOWN_PRIVATE_ARRAY_SLOTS
                : GpuRuntimeRegisterPressureAnalyzer.parsePositiveLiteral(
                        sizeNode.attributes().get("sourceText"),
                        GpuRuntimeRegisterPressureAnalyzer.UNKNOWN_PRIVATE_ARRAY_SLOTS
                );
        return Math.min(
                GpuRuntimeRegisterPressureAnalyzer.MAX_PRIVATE_ARRAY_SLOTS,
                Math.max(1, length) * Math.max(1, elementWeight)
        );
    }

    private void pushScope() {
        scopes.push(new LinkedHashMap<>());
    }

    private void popScope() {
        scopes.pop();
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

    record Resolution(
            Map<Integer, String> declarationKeys,
            Map<Integer, String> referenceKeys,
            Map<Integer, String> arrayReferenceKeys,
            Map<String, Integer> variableWeights,
            int localRegisters,
            int privateArrayRegisters,
            int scopedVariableCount,
            int shadowedVariableCount,
            int unresolvedReferenceCount
    ) {
        Resolution {
            declarationKeys = Map.copyOf(declarationKeys);
            referenceKeys = Map.copyOf(referenceKeys);
            arrayReferenceKeys = Map.copyOf(arrayReferenceKeys);
            variableWeights = Map.copyOf(variableWeights);
            localRegisters = Math.max(0, localRegisters);
            privateArrayRegisters = Math.max(0, privateArrayRegisters);
            scopedVariableCount = Math.max(0, scopedVariableCount);
            shadowedVariableCount = Math.max(0, shadowedVariableCount);
            unresolvedReferenceCount = Math.max(0, unresolvedReferenceCount);
        }

        String declarationKey(IrGpuTypedNode node) {
            return declarationKeys.getOrDefault(
                    node.id(),
                    "unresolved-declaration:" + node.id() + ":"
                            + node.attributes().getOrDefault("name", "unknown")
            );
        }

        String referenceKey(IrGpuTypedNode node) {
            return referenceKeys.getOrDefault(
                    node.id(),
                    "unresolved:" + node.id() + ":"
                            + node.attributes().getOrDefault("name", "unknown")
            );
        }

        String arrayReferenceKey(IrGpuTypedNode node) {
            return arrayReferenceKeys.getOrDefault(
                    node.id(),
                    "unresolved:" + node.id() + ":"
                            + node.attributes().getOrDefault("arrayName", "unknown")
            );
        }
    }
}
