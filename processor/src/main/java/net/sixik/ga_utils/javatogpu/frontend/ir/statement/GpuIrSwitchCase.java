package net.sixik.ga_utils.javatogpu.frontend.ir.statement;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;

import java.util.List;

public record GpuIrSwitchCase(
        List<GpuIrExpression> labels,
        List<GpuIrStatement> statements,
        boolean defaultCase
) {
}
