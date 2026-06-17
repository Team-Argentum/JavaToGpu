package net.sixik.ga_utils.javatogpu.frontend.ir.expression;

public sealed interface GpuIrExpression permits GpuIrArrayAccess, GpuIrBinary, GpuIrCast, GpuIrHelperCall, GpuIrIntrinsicCall, GpuIrLiteral, GpuIrTernary, GpuIrUnary, GpuIrVariableRef {
}
