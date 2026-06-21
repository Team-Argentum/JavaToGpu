package net.sixik.ga_utils.javatogpu.frontend.asm.experimental;

import org.objectweb.asm.Type;

import java.util.List;

public sealed interface GpuAsmStmt permits GpuAsmStmt.LocalVar, GpuAsmStmt.AssignLocal, GpuAsmStmt.ArrayStore,
        GpuAsmStmt.IfElse, GpuAsmStmt.WhileLoop, GpuAsmStmt.DoWhileLoop, GpuAsmStmt.SwitchStmt, GpuAsmStmt.ExprStmt,
        GpuAsmStmt.BreakSwitch, GpuAsmStmt.BreakLoop, GpuAsmStmt.ContinueLoop, GpuAsmStmt.ReturnValue, GpuAsmStmt.ReturnVoid {

    record LocalVar(String name, Type type, GpuAsmExpr initializer) implements GpuAsmStmt {
    }

    record AssignLocal(String name, GpuAsmExpr value) implements GpuAsmStmt {
    }

    record ArrayStore(String arrayName, GpuAsmExpr index, GpuAsmExpr value, Type elementType) implements GpuAsmStmt {
    }

    record IfElse(GpuAsmCondition condition, List<GpuAsmStmt> thenBranch, List<GpuAsmStmt> elseBranch) implements GpuAsmStmt {
    }

    record WhileLoop(GpuAsmCondition condition, List<GpuAsmStmt> body) implements GpuAsmStmt {
    }

    record DoWhileLoop(List<GpuAsmStmt> body, GpuAsmCondition condition) implements GpuAsmStmt {
    }

    record SwitchStmt(GpuAsmExpr selector, List<GpuAsmSwitchCase> cases, List<GpuAsmStmt> defaultStatements) implements GpuAsmStmt {
    }

    record ExprStmt(GpuAsmExpr expression) implements GpuAsmStmt {
    }

    record BreakSwitch() implements GpuAsmStmt {
    }

    record BreakLoop() implements GpuAsmStmt {
    }

    record ContinueLoop() implements GpuAsmStmt {
    }

    record ReturnValue(GpuAsmExpr value) implements GpuAsmStmt {
    }

    record ReturnVoid() implements GpuAsmStmt {
    }
}
