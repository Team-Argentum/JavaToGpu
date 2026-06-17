package net.sixik.ga_utils.javatogpu.frontend.ir.expression;

import java.util.List;

public record GpuIrHelperCall(
        String helperName,
        String resultType,
        List<GpuIrExpression> arguments
) implements GpuIrExpression {
}
