package net.sixik.ga_utils.javatogpu.frontend.asm.experimental;

import org.objectweb.asm.Type;

public record GpuAsmParameter(
        String name,
        Type type
) {
}
