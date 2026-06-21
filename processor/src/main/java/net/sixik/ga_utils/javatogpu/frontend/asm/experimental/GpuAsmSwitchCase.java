package net.sixik.ga_utils.javatogpu.frontend.asm.experimental;

import java.util.List;

public record GpuAsmSwitchCase(
        int[] keys,
        List<GpuAsmStmt> statements,
        boolean fallThrough
) {
}
