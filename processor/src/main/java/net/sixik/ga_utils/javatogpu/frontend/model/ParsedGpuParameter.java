package net.sixik.ga_utils.javatogpu.frontend.model;

public record ParsedGpuParameter(
        String name,
        String javaType,
        GpuAddressSpace addressSpace,
        boolean constant,
        java.util.List<String> openClQualifiers
) {
}
