package net.sixik.ga_utils.javatogpu.frontend.asm.experimental;

public sealed interface GpuAsmCondition permits GpuAsmCondition.Comparison, GpuAsmCondition.Truthy {

    record Comparison(String operator, GpuAsmExpr left, GpuAsmExpr right) implements GpuAsmCondition {
    }

    record Truthy(GpuAsmExpr expression) implements GpuAsmCondition {
    }
}
