package net.sixik.ga_utils.javatogpu.frontend.asm;

import java.util.List;

public record AsmControlFlowGraph(
        String ownerInternalName,
        String methodName,
        String methodDescriptor,
        List<AsmBasicBlock> blocks
) {

    public AsmBasicBlock entryBlock() {
        return blocks.isEmpty() ? null : blocks.get(0);
    }

    public AsmBasicBlock blockById(String blockId) {
        for (AsmBasicBlock block : blocks) {
            if (block.id().equals(blockId)) {
                return block;
            }
        }
        return null;
    }
}
