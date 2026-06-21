package net.sixik.ga_utils.javatogpu.frontend.ir.statement;

public sealed interface GpuIrStatement permits GpuIrAssignment, GpuIrBreak, GpuIrContinue, GpuIrDoWhileLoop, GpuIrExpressionStatement, GpuIrForLoop, GpuIrIf, GpuIrLoopBreak, GpuIrReturn, GpuIrSwitch, GpuIrVariableDeclaration, GpuIrWhileLoop {
}
