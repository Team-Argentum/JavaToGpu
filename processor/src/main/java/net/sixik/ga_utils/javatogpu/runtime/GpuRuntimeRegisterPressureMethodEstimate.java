package net.sixik.ga_utils.javatogpu.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Advisory register-pressure estimate for one typed IrGpu method body.
 */
public record GpuRuntimeRegisterPressureMethodEstimate(
        String methodName,
        boolean available,
        int estimatedValueRegisters,
        int parameterRegisters,
        int localRegisters,
        int privateArrayRegisters,
        int peakLiveRegisters,
        int expressionPeakRegisters,
        int scopedVariableCount,
        int shadowedVariableCount,
        int unresolvedReferenceCount,
        int advisoryBudget,
        int utilizationPermille,
        GpuRuntimeRegisterPressureLevel level,
        int typedNodeCount
) {

    public GpuRuntimeRegisterPressureMethodEstimate {
        methodName = methodName == null || methodName.isBlank() ? "unknown" : methodName.trim();
        estimatedValueRegisters = Math.max(0, estimatedValueRegisters);
        parameterRegisters = Math.max(0, parameterRegisters);
        localRegisters = Math.max(0, localRegisters);
        privateArrayRegisters = Math.max(0, privateArrayRegisters);
        peakLiveRegisters = Math.max(0, peakLiveRegisters);
        expressionPeakRegisters = Math.max(0, expressionPeakRegisters);
        scopedVariableCount = Math.max(0, scopedVariableCount);
        shadowedVariableCount = Math.max(0, shadowedVariableCount);
        unresolvedReferenceCount = Math.max(0, unresolvedReferenceCount);
        advisoryBudget = Math.max(1, advisoryBudget);
        utilizationPermille = Math.max(0, utilizationPermille);
        level = level == null ? GpuRuntimeRegisterPressureLevel.UNAVAILABLE : level;
        typedNodeCount = Math.max(0, typedNodeCount);
    }

    public static GpuRuntimeRegisterPressureMethodEstimate unavailable(String methodName, int advisoryBudget) {
        return new GpuRuntimeRegisterPressureMethodEstimate(
                methodName,
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                advisoryBudget,
                0,
                GpuRuntimeRegisterPressureLevel.UNAVAILABLE,
                0
        );
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "registerPressure.method" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".name", methodName);
        fields.put(normalizedPrefix + ".available", Boolean.toString(available));
        fields.put(normalizedPrefix + ".estimatedValueRegisters", Integer.toString(estimatedValueRegisters));
        fields.put(normalizedPrefix + ".parameterRegisters", Integer.toString(parameterRegisters));
        fields.put(normalizedPrefix + ".localRegisters", Integer.toString(localRegisters));
        fields.put(normalizedPrefix + ".privateArrayRegisters", Integer.toString(privateArrayRegisters));
        fields.put(normalizedPrefix + ".peakLiveRegisters", Integer.toString(peakLiveRegisters));
        fields.put(normalizedPrefix + ".expressionPeakRegisters", Integer.toString(expressionPeakRegisters));
        fields.put(normalizedPrefix + ".scopedVariableCount", Integer.toString(scopedVariableCount));
        fields.put(normalizedPrefix + ".shadowedVariableCount", Integer.toString(shadowedVariableCount));
        fields.put(normalizedPrefix + ".unresolvedReferenceCount", Integer.toString(unresolvedReferenceCount));
        fields.put(normalizedPrefix + ".advisoryBudget", Integer.toString(advisoryBudget));
        fields.put(normalizedPrefix + ".utilizationPermille", Integer.toString(utilizationPermille));
        fields.put(normalizedPrefix + ".level", level.name().toLowerCase(java.util.Locale.ROOT));
        fields.put(normalizedPrefix + ".typedNodeCount", Integer.toString(typedNodeCount));
        return Map.copyOf(fields);
    }
}
