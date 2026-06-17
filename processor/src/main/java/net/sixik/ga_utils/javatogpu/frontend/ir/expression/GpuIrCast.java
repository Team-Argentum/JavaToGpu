package net.sixik.ga_utils.javatogpu.frontend.ir.expression;

public record GpuIrCast(
        String targetType,
        GpuIrExpression expression
) implements GpuIrExpression {
}
