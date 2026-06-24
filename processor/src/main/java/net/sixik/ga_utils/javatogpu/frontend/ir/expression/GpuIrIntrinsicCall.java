package net.sixik.ga_utils.javatogpu.frontend.ir.expression;

import java.util.List;

public record GpuIrIntrinsicCall(
        GpuIrExpression receiver,
        String backendName,
        String codeTemplate,
        String resultType,
        List<GpuIrExpression> arguments
) implements GpuIrExpression {
}
