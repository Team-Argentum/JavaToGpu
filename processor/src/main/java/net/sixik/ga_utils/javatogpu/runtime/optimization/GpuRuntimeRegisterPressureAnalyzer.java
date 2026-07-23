package net.sixik.ga_utils.javatogpu.runtime.optimization;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModuleMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estimates value-register pressure from typed IrGpu without claiming backend compiler accuracy.
 *
 * <p>The analyzer uses backward statement liveness, fixed-point loop analysis, control-flow joins,
 * vector lane weights, private-array storage, and a Sethi-Ullman-like temporary estimate. Backend
 * compiler reports can refine this advisory model without changing the runtime artifact contract.</p>
 */
public final class GpuRuntimeRegisterPressureAnalyzer {

    public static final String ANALYSIS_VERSION = "register-pressure-interprocedural:v4";
    static final int UNKNOWN_PRIVATE_ARRAY_SLOTS = 8;
    static final int MAX_PRIVATE_ARRAY_SLOTS = 64;
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
        ArrayList<MethodAnalysis> analyses = new ArrayList<>();
        for (IrGpuMethodBody methodBody : artifact.module().methodBodies()) {
            analyses.add(analyzeMethod(artifact, methodBody, budget));
        }
        List<GpuRuntimeRegisterPressureCallEstimate> callEstimates = analyzeCalls(
                artifact,
                analyses,
                budget
        );
        return new GpuRuntimeRegisterPressureReport(
                budget,
                analyses.stream().map(MethodAnalysis::estimate).toList(),
                callEstimates
        );
    }

    private static MethodAnalysis analyzeMethod(
            IrGpuArtifact artifact,
            IrGpuMethodBody methodBody,
            GpuRuntimeRegisterPressureBudget budget
    ) {
        IrGpuTypedBody typedBody = methodBody.typedBody();
        if (typedBody == null || !typedBody.available()) {
            return new MethodAnalysis(
                    methodBody,
                    GpuRuntimeRegisterPressureMethodEstimate.unavailable(
                            methodBody.name(),
                            budget.valueRegisterBudget()
                    ),
                    List.of()
            );
        }
        LinkedHashMap<String, String> variableTypes = new LinkedHashMap<>();
        int parameterRegisters = parameterRegisters(artifact, methodBody, variableTypes);
        GpuRuntimeRegisterPressureLivenessAnalyzer.Estimate metrics =
                GpuRuntimeRegisterPressureLivenessAnalyzer.analyze(typedBody, variableTypes);
        int utilizationPermille = (int) Math.min(
                Integer.MAX_VALUE,
                Math.round((double) metrics.estimatedRegisters() * 1_000.0 / budget.valueRegisterBudget())
        );
        return new MethodAnalysis(
                methodBody,
                new GpuRuntimeRegisterPressureMethodEstimate(
                        methodBody.name(),
                        true,
                        metrics.estimatedRegisters(),
                        parameterRegisters,
                        metrics.localRegisters(),
                        metrics.privateArrayRegisters(),
                        metrics.peakLiveRegisters(),
                        metrics.expressionPeakRegisters(),
                        metrics.scopedVariableCount(),
                        metrics.shadowedVariableCount(),
                        metrics.unresolvedReferenceCount(),
                        budget.valueRegisterBudget(),
                        utilizationPermille,
                        GpuRuntimeRegisterPressureLevel.fromUtilization(true, utilizationPermille),
                        typedBody.nodes().size()
                ),
                metrics.callSites()
        );
    }

    private static List<GpuRuntimeRegisterPressureCallEstimate> analyzeCalls(
            IrGpuArtifact artifact,
            List<MethodAnalysis> analyses,
            GpuRuntimeRegisterPressureBudget budget
    ) {
        LinkedHashMap<String, MethodAnalysis> aliases = new LinkedHashMap<>();
        for (MethodAnalysis analysis : analyses) {
            aliases.put(analysis.methodBody().name(), analysis);
            aliases.put(analysis.methodBody().emittedName(), analysis);
        }
        Map<String, Boolean> inlineByAlias = inlineByAlias(artifact);
        Map<String, Set<String>> graph = callGraph(analyses, aliases);
        Map<String, Integer> effectiveMemo = new HashMap<>();
        ArrayList<GpuRuntimeRegisterPressureCallEstimate> calls = new ArrayList<>();
        for (MethodAnalysis caller : analyses) {
            for (GpuRuntimeRegisterPressureLivenessAnalyzer.CallSite callSite : caller.callSites()) {
                MethodAnalysis callee = aliases.get(callSite.helperName());
                boolean resolved = callee != null;
                boolean inline = resolved && inlineByAlias.getOrDefault(callSite.helperName(), false);
                boolean recursive = resolved && pathExists(
                        graph,
                        callee.canonicalName(),
                        caller.canonicalName(),
                        new HashSet<>()
                );
                int callerEstimate = caller.estimate().estimatedValueRegisters();
                int calleeEstimate = resolved
                        ? effectiveEstimate(callee, aliases, inlineByAlias, graph, effectiveMemo, new HashSet<>())
                        : 0;
                int reusableArgumentRegisters = resolved
                        ? Math.min(callee.estimate().parameterRegisters(), callSite.argumentRegisters())
                        : 0;
                int additionalFrameRegisters;
                int combinedEstimate;
                if (!resolved) {
                    additionalFrameRegisters = 0;
                    combinedEstimate = callerEstimate;
                } else if (recursive) {
                    additionalFrameRegisters = 0;
                    combinedEstimate = Math.max(callerEstimate, callee.estimate().estimatedValueRegisters());
                } else if (inline) {
                    additionalFrameRegisters = Math.max(0, calleeEstimate - reusableArgumentRegisters);
                    combinedEstimate = Math.max(
                            callerEstimate,
                            callSite.callerLiveRegisters()
                                    + additionalFrameRegisters
                                    + callSite.resultRegisters()
                    );
                } else {
                    additionalFrameRegisters = calleeEstimate;
                    combinedEstimate = Math.max(callerEstimate, calleeEstimate);
                }
                int utilizationPermille = utilizationPermille(
                        combinedEstimate,
                        budget.valueRegisterBudget()
                );
                calls.add(new GpuRuntimeRegisterPressureCallEstimate(
                        caller.methodBody().name(),
                        callSite.helperName(),
                        resolved ? callee.methodBody().name() : "unresolved",
                        callSite.nodeId(),
                        resolved,
                        inline,
                        recursive,
                        callSite.callerLiveRegisters(),
                        callSite.argumentRegisters(),
                        callSite.resultRegisters(),
                        calleeEstimate,
                        resolved ? callee.estimate().parameterRegisters() : 0,
                        additionalFrameRegisters,
                        combinedEstimate,
                        GpuRuntimeRegisterPressureLevel.fromUtilization(resolved, utilizationPermille)
                ));
            }
        }
        return List.copyOf(calls);
    }

    private static int effectiveEstimate(
            MethodAnalysis method,
            Map<String, MethodAnalysis> aliases,
            Map<String, Boolean> inlineByAlias,
            Map<String, Set<String>> graph,
            Map<String, Integer> memo,
            Set<String> visiting
    ) {
        String methodName = method.canonicalName();
        Integer cached = memo.get(methodName);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(methodName)) {
            return method.estimate().estimatedValueRegisters();
        }
        int effective = method.estimate().estimatedValueRegisters();
        for (GpuRuntimeRegisterPressureLivenessAnalyzer.CallSite callSite : method.callSites()) {
            MethodAnalysis callee = aliases.get(callSite.helperName());
            if (callee == null) {
                continue;
            }
            boolean recursive = pathExists(
                    graph,
                    callee.canonicalName(),
                    methodName,
                    new HashSet<>()
            );
            if (recursive) {
                effective = Math.max(effective, callee.estimate().estimatedValueRegisters());
                continue;
            }
            int calleeEffective = effectiveEstimate(
                    callee,
                    aliases,
                    inlineByAlias,
                    graph,
                    memo,
                    visiting
            );
            if (inlineByAlias.getOrDefault(callSite.helperName(), false)) {
                int reusableArguments = Math.min(
                        callee.estimate().parameterRegisters(),
                        callSite.argumentRegisters()
                );
                int additional = Math.max(0, calleeEffective - reusableArguments);
                effective = Math.max(
                        effective,
                        callSite.callerLiveRegisters() + additional + callSite.resultRegisters()
                );
            } else {
                effective = Math.max(effective, calleeEffective);
            }
        }
        visiting.remove(methodName);
        memo.put(methodName, effective);
        return effective;
    }

    private static Map<String, Boolean> inlineByAlias(IrGpuArtifact artifact) {
        LinkedHashMap<String, Boolean> inlineByAlias = new LinkedHashMap<>();
        for (IrGpuModuleMethod method : artifact.module().helperMethods()) {
            inlineByAlias.put(method.name(), method.inline());
            inlineByAlias.put(method.emittedName(), method.inline());
        }
        return Map.copyOf(inlineByAlias);
    }

    private static Map<String, Set<String>> callGraph(
            List<MethodAnalysis> analyses,
            Map<String, MethodAnalysis> aliases
    ) {
        LinkedHashMap<String, Set<String>> graph = new LinkedHashMap<>();
        for (MethodAnalysis analysis : analyses) {
            LinkedHashSet<String> callees = new LinkedHashSet<>();
            for (GpuRuntimeRegisterPressureLivenessAnalyzer.CallSite callSite : analysis.callSites()) {
                MethodAnalysis callee = aliases.get(callSite.helperName());
                if (callee != null) {
                    callees.add(callee.canonicalName());
                }
            }
            graph.put(analysis.canonicalName(), Set.copyOf(callees));
        }
        return Map.copyOf(graph);
    }

    private static boolean pathExists(
            Map<String, Set<String>> graph,
            String from,
            String target,
            Set<String> visiting
    ) {
        if (from.equals(target)) {
            return true;
        }
        if (!visiting.add(from)) {
            return false;
        }
        for (String next : graph.getOrDefault(from, Set.of())) {
            if (pathExists(graph, next, target, visiting)) {
                return true;
            }
        }
        return false;
    }

    private static int utilizationPermille(int registers, int budget) {
        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.round((double) Math.max(0, registers) * 1_000.0 / Math.max(1, budget))
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

    static int typeWeight(String typeName) {
        if (typeName == null || typeName.isBlank() || "unknown".equalsIgnoreCase(typeName)) {
            return 1;
        }
        String normalized = typeName
                .replace("java.lang.", "")
                .replace("net.sixik.ga_utils.javatogpu.api.", "")
                .replace("_", "")
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
        if ("void".equals(normalized)) {
            return 0;
        }
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

    static int literalWeight(String sourceText) {
        if (sourceText == null) {
            return 1;
        }
        String value = sourceText.trim().toLowerCase(java.util.Locale.ROOT);
        return value.endsWith("d") || value.endsWith("l") ? 2 : 1;
    }

    static int parsePositiveLiteral(String sourceText, int fallback) {
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

    private record MethodAnalysis(
            IrGpuMethodBody methodBody,
            GpuRuntimeRegisterPressureMethodEstimate estimate,
            List<GpuRuntimeRegisterPressureLivenessAnalyzer.CallSite> callSites
    ) {
        private MethodAnalysis {
            callSites = callSites == null ? List.of() : List.copyOf(callSites);
        }

        private String canonicalName() {
            return methodBody.name().isBlank() ? methodBody.emittedName() : methodBody.name();
        }
    }
}
