package net.sixik.ga_utils.javatogpu.frontend.asm;

import java.util.List;

public record AsmBasicBlock(
        String id,
        String labelName,
        boolean entry,
        List<AsmInstruction> instructions,
        AsmTerminatorKind terminatorKind,
        List<String> successorIds
) {
}
