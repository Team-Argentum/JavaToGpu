package net.sixik.ga_utils.javatogpu.frontend.asm;

import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import org.objectweb.asm.tree.MethodNode;

public record AsmGpuMethod(
        String ownerInternalName,
        ParsedGpuMethod parsedMethod,
        MethodNode methodNode
) {
}
