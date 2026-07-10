package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModuleMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estimates value-register pressure from typed IrGpu without claiming backend compiler accuracy.
 *
 * <p>The model intentionally keeps declared locals live for their containing block and uses a
 * Sethi-Ullman-like expression estimate. Backend compiler reports can replace this advisory model
 * later without changing the runtime analysis artifact contract.</p>
 */
public final class GpuRuntimeRegisterPressureAnalyzer {

    private static final int UNKNOWN_PRIVATE_ARRAY_SLOTS = 8;
    private static final int MAX_PRIVATE_ARRAY_SLOTS = 64;
    private static final Pattern VECTOR_TYPE = Pattern.compile(
            "(?:char|uchar|short|ushort|int|uint|long|ulong|float|double)(2|3|4|8|16)$"
    );

    private GpuRuntimeRegisterPressureAnalyzer() {
    }

    public static GpuRuntimeRegisterPressureReport analyze(
            IrGpuArtifact artifact,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        GpuRuntimeRegisterPressureBudget budget = GpuRuntimeRegisterPressureBudget.forDevice(deviceProfile);
        if (artifact == null || artifact.module() == null) {
            return new GpuRuntimeRegisterPressureReport(budget, List.of());
        }
        ArrayList<GpuRuntimeRegisterPressureMethodEstimate> estimates = new ArrayList<>();
        for (IrGpuMethodBody methodBody : artifact.module().methodBodies()) {
            estimates.add(analyzeMethod(artifact, methodBody, budget));
        }
        return new GpuRuntimeRegisterPressureReport(budget, estimates);
    }

    private static GpuRuntimeRegisterPressureMethodEstimate analyzeMethod(
            IrGpuArtifact artifact,
            IrGpuMethodBody methodBody,
            GpuRuntimeRegisterPressureBudget budget
    ) {
        IrGpuTypedBody typedBody = methodBody.typedBody();
        if (typedBody == null || !typedBody.available()) {
            return GpuRuntimeRegisterPressureMethodEstimate.unavailable(
                    methodBody.name(),
                    budget.valueRegisterBudget()
            );
        }
        LinkedHashMap<String, String> variableTypes = new LinkedHashMap<>();
        int parameterRegisters = parameterRegisters(artifact, methodBody, variableTypes);
        MethodAnalyzer analyzer = new MethodAnalyzer(typedBody, variableTypes, parameterRegisters);
        MethodMetrics metrics = analyzer.analyze();
        int utilizationPermille = (int) Math.min(
                Integer.MAX_VALUE,
                Math.round((double) metrics.estimatedRegisters() * 1_000.0 / budget.valueRegisterBudget())
        );
        return new GpuRuntimeRegisterPressureMethodEstimate(
                methodBody.name(),
                true,
                metrics.estimatedRegisters(),
                parameterRegisters,
                metrics.localRegisters(),
                metrics.privateArrayRegisters(),
                metrics.expressionPeakRegisters(),
                budget.valueRegisterBudget(),
                utilizationPermille,
                GpuRuntimeRegisterPressureLevel.fromUtilization(true, utilizationPermille),
                typedBody.nodes().size()
        );
    }

    private static int parameterRegisters(
            IrGpuArtifact artifact,
            IrGpuMethodBody methodBody,
            Map<String, String> variableTypes
    ) {
        List<IrGpuEntryParameter> parameters;
        if ("entry".equals(methodBody.role())) {
            parameters = artifact.entryParameters();
        } else {
            parameters = artifact.module().helperMethods().stream()
                    .filter(method -> method.name().equals(methodBody.name())
                            || method.emittedName().equals(methodBody.emittedName()))
                    .findFirst()
                    .map(IrGpuModuleMethod::parameters)
                    .orElse(List.of());
        }
        int registers = 0;
        for (IrGpuEntryParameter parameter : parameters) {
            variableTypes.put(parameter.name(), parameter.javaType());
            registers += typeWeight(parameter.javaType());
        }
        return registers;
    }

    private static final class MethodAnalyzer {

        private final Map<Integer, IrGpuTypedNode> nodes = new HashMap<>();
        private final Map<String, String> variableTypes;
        private final int parameterRegisters;
        private int localRegisters;
        private int privateArrayRegisters;
        private int expressionPeakRegisters;

        private MethodAnalyzer(
                IrGpuTypedBody typedBody,
                Map<String, String> variableTypes,
                int parameterRegisters
        ) {
            for (IrGpuTypedNode node : typedBody.nodes()) {
                nodes.put(node.id(), node);
            }
            this.variableTypes = new LinkedHashMap<>(variableTypes);
            this.parameterRegisters = parameterRegisters;
            this.rootNodeIds = typedBody.rootNodeIds();
        }

        private final List<Integer> rootNodeIds;

        private MethodMetrics analyze() {
            SequenceMetrics sequence = analyzeSequence(rootNodeIds, parameterRegisters);
            return new MethodMetrics(
                    Math.max(parameterRegisters, sequence.peakRegisters()),
                    localRegisters,
                    privateArrayRegisters,
                    expressionPeakRegisters
            );
        }

        private SequenceMetrics analyzeSequence(List<Integer> nodeIds, int incomingLiveRegisters) {
            int liveRegisters = incomingLiveRegisters;
            int peakRegisters = liveRegisters;
            for (Integer nodeId : nodeIds == null ? List.<Integer>of() : nodeIds) {
                IrGpuTypedNode node = nodes.get(nodeId);
                if (node == null) {
                    continue;
                }
                if ("GpuIrVariableDeclaration".equals(node.kind())) {
                    int initializerPeak = expressionPressure(firstChild(node, "initializer"), new HashSet<>());
                    expressionPeakRegisters = Math.max(expressionPeakRegisters, initializerPeak);
                    peakRegisters = Math.max(peakRegisters, liveRegisters + initializerPeak);
                    String typeName = node.attributes().getOrDefault("typeName", "unknown");
                    String name = node.attributes().getOrDefault("name", "unknown");
                    int weight = typeWeight(typeName);
                    variableTypes.put(name, typeName);
                    localRegisters += weight;
                    liveRegisters += weight;
                    peakRegisters = Math.max(peakRegisters, liveRegisters);
                    continue;
                }
                if ("GpuIrPrivateArrayDeclaration".equals(node.kind())) {
                    int sizePeak = expressionPressure(firstChild(node, "size"), new HashSet<>());
                    expressionPeakRegisters = Math.max(expressionPeakRegisters, sizePeak);
                    peakRegisters = Math.max(peakRegisters, liveRegisters + sizePeak);
                    int slots = privateArraySlots(node);
                    privateArrayRegisters += slots;
                    liveRegisters += slots;
                    peakRegisters = Math.max(peakRegisters, liveRegisters);
                    continue;
                }
                peakRegisters = Math.max(peakRegisters, statementPeak(node, liveRegisters));
            }
            return new SequenceMetrics(peakRegisters, liveRegisters);
        }

        private int statementPeak(IrGpuTypedNode node, int liveRegisters) {
            return switch (node.kind()) {
                case "GpuIrIf" -> Math.max(
                        liveRegisters + trackExpression(firstChild(node, "condition")),
                        Math.max(
                                analyzeSequence(node.children().getOrDefault("thenBranch", List.of()), liveRegisters)
                                        .peakRegisters(),
                                analyzeSequence(node.children().getOrDefault("elseBranch", List.of()), liveRegisters)
                                        .peakRegisters()
                        )
                );
                case "GpuIrForLoop" -> loopPeak(node, liveRegisters, true);
                case "GpuIrWhileLoop", "GpuIrDoWhileLoop" -> loopPeak(node, liveRegisters, false);
                case "GpuIrSwitch" -> switchPeak(node, liveRegisters);
                case "GpuIrSwitchCase" -> analyzeSequence(
                        node.children().getOrDefault("statements", List.of()),
                        liveRegisters
                ).peakRegisters();
                default -> liveRegisters + trackExpressionChildren(node);
            };
        }

        private int loopPeak(IrGpuTypedNode node, int liveRegisters, boolean hasInitializer) {
            int peak = liveRegisters;
            int loopLiveRegisters = liveRegisters;
            if (hasInitializer) {
                SequenceMetrics initializer = analyzeSequence(
                        node.children().getOrDefault("initializer", List.of()),
                        liveRegisters
                );
                peak = Math.max(peak, initializer.peakRegisters());
                loopLiveRegisters = initializer.endingLiveRegisters();
            }
            peak = Math.max(peak, loopLiveRegisters + trackExpression(firstChild(node, "condition")));
            peak = Math.max(peak, analyzeSequence(
                    node.children().getOrDefault("body", List.of()),
                    loopLiveRegisters
            ).peakRegisters());
            if (hasInitializer) {
                peak = Math.max(peak, analyzeSequence(
                        node.children().getOrDefault("update", List.of()),
                        loopLiveRegisters
                ).peakRegisters());
            }
            return peak;
        }

        private int switchPeak(IrGpuTypedNode node, int liveRegisters) {
            int peak = liveRegisters + trackExpression(firstChild(node, "selector"));
            for (Integer caseId : node.children().getOrDefault("cases", List.of())) {
                IrGpuTypedNode switchCase = nodes.get(caseId);
                if (switchCase == null) {
                    continue;
                }
                peak = Math.max(peak, liveRegisters + trackExpressions(
                        switchCase.children().getOrDefault("labels", List.of())
                ));
                peak = Math.max(peak, analyzeSequence(
                        switchCase.children().getOrDefault("statements", List.of()),
                        liveRegisters
                ).peakRegisters());
            }
            return peak;
        }

        private int trackExpressionChildren(IrGpuTypedNode node) {
            return trackExpressions(orderedChildIds(node));
        }

        private int trackExpressions(List<Integer> childIds) {
            int peak = 0;
            int held = 0;
            for (Integer childId : childIds == null ? List.<Integer>of() : childIds) {
                int childPeak = expressionPressure(childId, new HashSet<>());
                peak = Math.max(peak, held + childPeak);
                held += resultWeight(childId);
            }
            expressionPeakRegisters = Math.max(expressionPeakRegisters, peak);
            return peak;
        }

        private int trackExpression(Integer nodeId) {
            int peak = expressionPressure(nodeId, new HashSet<>());
            expressionPeakRegisters = Math.max(expressionPeakRegisters, peak);
            return peak;
        }

        private int expressionPressure(Integer nodeId, Set<Integer> visiting) {
            if (nodeId == null) {
                return 0;
            }
            IrGpuTypedNode node = nodes.get(nodeId);
            if (node == null || !visiting.add(nodeId)) {
                return 1;
            }
            try {
                if ("GpuIrTernary".equals(node.kind())) {
                    return Math.max(
                            resultWeight(nodeId),
                            Math.max(
                                    expressionPressure(firstChild(node, "condition"), visiting),
                                    Math.max(
                                            expressionPressure(firstChild(node, "whenTrue"), visiting),
                                            expressionPressure(firstChild(node, "whenFalse"), visiting)
                                    )
                            )
                    );
                }
                int peak = resultWeight(nodeId);
                int held = 0;
                for (Integer childId : orderedChildIds(node)) {
                    int childPeak = expressionPressure(childId, visiting);
                    peak = Math.max(peak, held + childPeak);
                    held += resultWeight(childId);
                }
                return peak;
            } finally {
                visiting.remove(nodeId);
            }
        }

        private int resultWeight(Integer nodeId) {
            IrGpuTypedNode node = nodeId == null ? null : nodes.get(nodeId);
            if (node == null) {
                return 0;
            }
            return switch (node.kind()) {
                case "GpuIrVariableRef" -> typeWeight(variableTypes.get(node.attributes().get("name")));
                case "GpuIrCast" -> typeWeight(node.attributes().get("targetType"));
                case "GpuIrHelperCall", "GpuIrIntrinsicCall" -> typeWeight(node.attributes().get("resultType"));
                case "GpuIrStructInit" -> typeWeight(node.attributes().get("structType"));
                case "GpuIrLiteral" -> literalWeight(node.attributes().get("sourceText"));
                case "GpuIrReturn", "GpuIrAssignment", "GpuIrExpressionStatement",
                     "GpuIrIf", "GpuIrForLoop", "GpuIrWhileLoop", "GpuIrDoWhileLoop",
                     "GpuIrSwitch", "GpuIrSwitchCase", "GpuIrBreak", "GpuIrContinue",
                     "GpuIrLoopBreak", "GpuIrVariableDeclaration", "GpuIrPrivateArrayDeclaration" -> 0;
                default -> 1;
            };
        }

        private int privateArraySlots(IrGpuTypedNode node) {
            int elementWeight = typeWeight(node.attributes().get("elementType"));
            Integer sizeNodeId = firstChild(node, "size");
            IrGpuTypedNode sizeNode = sizeNodeId == null ? null : nodes.get(sizeNodeId);
            int length = sizeNode == null || !"GpuIrLiteral".equals(sizeNode.kind())
                    ? UNKNOWN_PRIVATE_ARRAY_SLOTS
                    : parsePositiveLiteral(sizeNode.attributes().get("sourceText"), UNKNOWN_PRIVATE_ARRAY_SLOTS);
            return Math.min(MAX_PRIVATE_ARRAY_SLOTS, Math.max(1, length) * Math.max(1, elementWeight));
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
    }

    private static int typeWeight(String typeName) {
        if (typeName == null || typeName.isBlank() || "unknown".equalsIgnoreCase(typeName)) {
            return 1;
        }
        String normalized = typeName
                .replace("java.lang.", "")
                .replace("net.sixik.ga_utils.javatogpu.api.", "")
                .replace("_", "")
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
        if (normalized.endsWith("[]") || normalized.contains("ptr") || normalized.contains("pointer")) {
            return 1;
        }
        Matcher vectorMatcher = VECTOR_TYPE.matcher(normalized);
        if (vectorMatcher.find()) {
            int lanes = Integer.parseInt(vectorMatcher.group(1));
            int scalarWeight = normalized.startsWith("double") || normalized.startsWith("long") ? 2 : 1;
            return lanes * scalarWeight;
        }
        if (normalized.contains("double") || normalized.equals("long") || normalized.equals("ulong")) {
            return 2;
        }
        if (Character.isUpperCase(typeName.trim().charAt(0))) {
            return 4;
        }
        return 1;
    }

    private static int literalWeight(String sourceText) {
        if (sourceText == null) {
            return 1;
        }
        String value = sourceText.trim().toLowerCase(java.util.Locale.ROOT);
        return value.endsWith("d") || value.endsWith("l") ? 2 : 1;
    }

    private static int parsePositiveLiteral(String sourceText, int fallback) {
        if (sourceText == null || sourceText.isBlank()) {
            return fallback;
        }
        String normalized = sourceText.trim().replace("_", "");
        while (!normalized.isEmpty() && Character.isLetter(normalized.charAt(normalized.length() - 1))) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            return Math.max(1, Integer.parseInt(normalized));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private record SequenceMetrics(int peakRegisters, int endingLiveRegisters) {
    }

    private record MethodMetrics(
            int estimatedRegisters,
            int localRegisters,
            int privateArrayRegisters,
            int expressionPeakRegisters
    ) {
    }
}
