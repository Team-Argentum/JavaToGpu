package net.sixik.ga_utils.javatogpu.frontend.asm;

import org.objectweb.asm.tree.AbstractInsnNode;

public record AsmInstruction(
        int instructionIndex,
        int opcode,
        String opcodeName,
        int lineNumber,
        AbstractInsnNode node
) {
}
