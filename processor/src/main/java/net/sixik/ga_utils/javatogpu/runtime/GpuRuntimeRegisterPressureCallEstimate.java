package net.sixik.ga_utils.javatogpu.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Interprocedural pressure summary for one typed IrGpu helper call site.
 */
public record GpuRuntimeRegisterPressureCallEstimate(
        String callerMethod,
        String helperName,
        String resolvedMethod,
        int callNodeId,
        boolean resolved,
        boolean inline,
        boolean recursive,
        int callerLiveRegisters,
        int argumentRegisters,
        int resultRegisters,
        int calleeEstimatedRegisters,
        int calleeParameterRegisters,
        int additionalFrameRegisters,
        int combinedEstimatedRegisters,
        GpuRuntimeRegisterPressureLevel level
) {

    public GpuRuntimeRegisterPressureCallEstimate {
        callerMethod = normalize(callerMethod, "unknown");
        helperName = normalize(helperName, "unknown");
        resolvedMethod = normalize(resolvedMethod, "unresolved");
        callNodeId = Math.max(0, callNodeId);
        callerLiveRegisters = Math.max(0, callerLiveRegisters);
        argumentRegisters = Math.max(0, argumentRegisters);
        resultRegisters = Math.max(0, resultRegisters);
        calleeEstimatedRegisters = Math.max(0, calleeEstimatedRegisters);
        calleeParameterRegisters = Math.max(0, calleeParameterRegisters);
        additionalFrameRegisters = Math.max(0, additionalFrameRegisters);
        combinedEstimatedRegisters = Math.max(0, combinedEstimatedRegisters);
        level = level == null ? GpuRuntimeRegisterPressureLevel.UNAVAILABLE : level;
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "registerPressure.call" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".callerMethod", callerMethod);
        fields.put(normalizedPrefix + ".helperName", helperName);
        fields.put(normalizedPrefix + ".resolvedMethod", resolvedMethod);
        fields.put(normalizedPrefix + ".callNodeId", Integer.toString(callNodeId));
        fields.put(normalizedPrefix + ".resolved", Boolean.toString(resolved));
        fields.put(normalizedPrefix + ".inline", Boolean.toString(inline));
        fields.put(normalizedPrefix + ".recursive", Boolean.toString(recursive));
        fields.put(normalizedPrefix + ".callerLiveRegisters", Integer.toString(callerLiveRegisters));
        fields.put(normalizedPrefix + ".argumentRegisters", Integer.toString(argumentRegisters));
        fields.put(normalizedPrefix + ".resultRegisters", Integer.toString(resultRegisters));
        fields.put(normalizedPrefix + ".calleeEstimatedRegisters", Integer.toString(calleeEstimatedRegisters));
        fields.put(normalizedPrefix + ".calleeParameterRegisters", Integer.toString(calleeParameterRegisters));
        fields.put(normalizedPrefix + ".additionalFrameRegisters", Integer.toString(additionalFrameRegisters));
        fields.put(normalizedPrefix + ".combinedEstimatedRegisters", Integer.toString(combinedEstimatedRegisters));
        fields.put(normalizedPrefix + ".level", level.name().toLowerCase(java.util.Locale.ROOT));
        return Map.copyOf(fields);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
