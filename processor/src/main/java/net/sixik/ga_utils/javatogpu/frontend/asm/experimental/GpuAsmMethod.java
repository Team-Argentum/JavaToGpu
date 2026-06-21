package net.sixik.ga_utils.javatogpu.frontend.asm.experimental;

import org.objectweb.asm.Type;

import java.util.List;

public record GpuAsmMethod(
        String name,
        Type returnType,
        List<GpuAsmParameter> parameters,
        List<GpuAsmStmt> statements
) {
}
