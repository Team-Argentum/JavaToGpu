package net.sixik.ga_utils.javatogpu.frontend.intrinsics;

import java.util.List;

public record GpuIntrinsic(
        String ownerSimpleName,
        String ownerQualifiedName,
        String javaName,
        int arity,
        boolean instanceMethod,
        GpuIntrinsicKind kind,
        String backendName,
        String codeTemplate,
        String resultType,
        List<String> argumentTypes
) {
}
