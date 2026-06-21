package net.sixik.ga_utils.javatogpu.frontend.asm.experimental;

import org.objectweb.asm.Type;

import java.util.List;

/**
 * Optional tiny AST used only as a helper layer for generator authors.
 *
 * <p>Regular JavaToGpu users do not need this API.</p>
 */
public sealed interface GpuAsmExpr permits GpuAsmExpr.IntLiteral, GpuAsmExpr.LongLiteral, GpuAsmExpr.FloatLiteral,
        GpuAsmExpr.DoubleLiteral, GpuAsmExpr.LocalRef, GpuAsmExpr.ArrayLoad, GpuAsmExpr.Binary, GpuAsmExpr.Unary,
        GpuAsmExpr.Cast, GpuAsmExpr.StaticCall {

    record IntLiteral(int value) implements GpuAsmExpr {
    }

    record LongLiteral(long value) implements GpuAsmExpr {
    }

    record FloatLiteral(float value) implements GpuAsmExpr {
    }

    record DoubleLiteral(double value) implements GpuAsmExpr {
    }

    record LocalRef(String name) implements GpuAsmExpr {
    }

    record ArrayLoad(String arrayName, GpuAsmExpr index, Type elementType) implements GpuAsmExpr {
    }

    record Binary(String operator, GpuAsmExpr left, GpuAsmExpr right, Type resultType) implements GpuAsmExpr {
    }

    record Unary(String operator, GpuAsmExpr expression) implements GpuAsmExpr {
    }

    record Cast(Type targetType, GpuAsmExpr expression) implements GpuAsmExpr {
    }

    record StaticCall(
            String ownerInternalName,
            String methodName,
            String descriptor,
            Type returnType,
            List<GpuAsmExpr> arguments
    ) implements GpuAsmExpr {
    }
}
