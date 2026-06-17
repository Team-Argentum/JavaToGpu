package net.sixik.ga_utils.javatogpu.frontend.ir.statement;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;

public record GpuIrReturn(
        GpuIrExpression value
) implements GpuIrStatement {
}
