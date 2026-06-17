package net.sixik.ga_utils.javatogpu.frontend.ir.statement;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;

import java.util.List;

public record GpuIrWhileLoop(
        GpuIrExpression condition,
        List<GpuIrStatement> body
) implements GpuIrStatement {
}
