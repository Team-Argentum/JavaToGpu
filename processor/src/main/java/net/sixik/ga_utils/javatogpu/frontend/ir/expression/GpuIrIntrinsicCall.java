package net.sixik.ga_utils.javatogpu.frontend.ir.expression;

import java.util.List;

public record GpuIrIntrinsicCall(
        GpuIrExpression receiver,
        String backendName,
        String codeTemplate,
        String resultType,
        List<GpuIrExpression> arguments,
        List<String> argumentTypes
) implements GpuIrExpression {
    public GpuIrIntrinsicCall(
            GpuIrExpression receiver,
            String backendName,
            String codeTemplate,
            String resultType,
            List<GpuIrExpression> arguments
    ) {
        this(receiver, backendName, codeTemplate, resultType, arguments, List.of());
    }

    public GpuIrIntrinsicCall {
        arguments = arguments == null ? null : List.copyOf(arguments);
        argumentTypes = argumentTypes == null ? List.of() : List.copyOf(argumentTypes);
    }
}
