package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.Types;

import java.util.ArrayList;
import java.util.List;

/**
 * Эмиссия стейтментов фронтенда в {@link IrBuilder} (REWORK_ARCHITECTURE.md, 17.7, шаг 5). Порядок создания узлов
 * и локалей повторяет {@code FunctionLowerer} из {@code frontend-java}, потому что от него зависят номера узлов, а
 * значит текст дампа и {@code kernelId} (критерий 17.14-3):
 * <ul>
 *   <li>операнды раньше родителя, слева направо; неявные приведения операндов - после всех операндов
 *   ({@code binary(op, conv(l), conv(r))});</li>
 *   <li>{@code a[i] = v} с чистыми операндами - значение первым, затем массив и индекс; если значение или индекс
 *   содержали эффекты - в порядке Java: индекс, значение, массив;</li>
 *   <li>локаль переменной объявляется в начале исходного стейтмента - раньше временных локалей, появившихся при
 *   вычислении её инициализатора.</li>
 * </ul>
 */
final class Emitter {

    private final IrBuilder b;

    Emitter(IrBuilder b) {
        this.b = b;
    }

    /**
     * Эмитирует тело функции.
     *
     * @param prologue   изменяемые параметры (элемент {@code null} - параметр не изменяется)
     * @param paramTypes типы IR параметров
     * @param body       стейтменты тела
     * @return узлы стейтментов верхнего уровня
     */
    List<Integer> body(Var[] prologue, int[] paramTypes, List<St> body) {
        List<Integer> out = new ArrayList<>();
        for (Var v : prologue) {
            if (v != null) {
                declare(v, paramTypes[v.paramIndex]);
                out.add(b.localDecl(v.irLocal, b.param(v.paramIndex)));
            }
        }
        int group = -1;
        for (int i = 0; i < body.size(); i++) {
            St s = body.get(i);
            if (s.group() != group) {
                group = s.group();
                // переменные, объявляемые этим исходным стейтментом, - раньше его временных локалей
                for (int j = i; j < body.size() && body.get(j).group() == group; j++) {
                    St d = body.get(j);
                    if (d.kind() == St.DECL && !d.var().temp()) {
                        declare(d.var(), d.var().irType);
                    }
                }
            }
            int node = stmt(s);
            if (s.line() > 0) {
                b.span(node, s.line(), 0);
            }
            out.add(node);
        }
        return out;
    }

    private void declare(Var v, int irType) {
        if (v.irLocal < 0) {
            v.irLocal = b.declareLocal(irType);
            if (v.name != null) {
                b.nameLocal(v.irLocal, v.name);
            }
        }
    }

    private int stmt(St s) {
        switch (s.kind()) {
            case St.DECL -> {
                declare(s.var(), s.var().irType);
                return b.localDecl(s.var().irLocal, full(s.a()));
            }
            case St.SET -> {
                return b.localSet(s.var().irLocal, full(s.a()));
            }
            case St.STORE -> {
                int arr;
                int idxCore;
                int valCore;
                if (s.ordered()) {
                    idxCore = core(s.b());
                    valCore = core(s.c());
                    arr = full(s.a());
                } else {
                    valCore = core(s.c());
                    arr = full(s.a());
                    idxCore = core(s.b());
                }
                int val = finish(s.c(), valCore);
                int idx = finish(s.b(), idxCore);
                int ptr = b.elemPtr(arr, idx);
                if (s.a().elemKind == Kinds.K_BOOLEAN) {
                    val = b.convert(Types.U8, val);
                }
                return b.store(ptr, val);
            }
            case St.EXPR -> {
                return b.exprStmt(full(s.a()));
            }
            case St.BARRIER -> {
                return b.barrier(s.aux());
            }
            case St.RETURN -> {
                return b.returnStmt(s.a() == null ? -1 : full(s.a()));
            }
            case St.ARRAY_DECL -> {
                Var v = s.var();
                declare(v, b.array(v.space, Kinds.storageType(v.elemKind)));
                int len = b.constI32(s.aux());
                return v.space == AddressSpace.LOCAL ? b.localArrayDecl(v.irLocal, len) : b.privateArrayDecl(v.irLocal, len);
            }
            default -> throw new IllegalStateException("statement kind " + s.kind());
        }
    }

    /** Узел выражения целиком (с собственным неявным приведением). */
    private int full(Ex e) {
        return finish(e, core(e));
    }

    /** Узел выражения без собственного неявного приведения: его создаёт родитель после соседних операндов. */
    private int core(Ex e) {
        if (e.op == Ex.CONVERT && e.implicit) {
            return full(e.kids[0]);
        }
        return node(e);
    }

    private int finish(Ex e, int coreNode) {
        return e.op == Ex.CONVERT && e.implicit ? b.convert(Kinds.irType(e.kind), coreNode) : coreNode;
    }

    private int[] kids(Ex e) {
        int[] n = new int[e.kids.length];
        for (int i = 0; i < n.length; i++) {
            n[i] = core(e.kids[i]);
        }
        for (int i = 0; i < n.length; i++) {
            n[i] = finish(e.kids[i], n[i]);
        }
        return n;
    }

    private int node(Ex e) {
        switch (e.op) {
            case Ex.CONST -> {
                return switch (e.kind) {
                    case Kinds.K_BOOLEAN -> b.constBool(e.bits != 0);
                    case Kinds.K_INT -> b.constI32((int) e.bits);
                    case Kinds.K_LONG -> b.constI64(e.bits);
                    case Kinds.K_FLOAT -> b.constF32(Float.intBitsToFloat((int) e.bits));
                    case Kinds.K_DOUBLE -> b.constF64(Double.longBitsToDouble(e.bits));
                    default -> b.constInt(Kinds.irType(e.kind), e.bits);
                };
            }
            case Ex.PARAM -> {
                return b.param(e.aux);
            }
            case Ex.LOCAL_GET -> {
                return b.localGet(e.var.irLocal);
            }
            case Ex.BINARY -> {
                int[] k = kids(e);
                return b.binary(e.aux, k[0], k[1]);
            }
            case Ex.NEG -> {
                return b.neg(kids(e)[0]);
            }
            case Ex.NOT -> {
                return b.not(kids(e)[0]);
            }
            case Ex.CONVERT -> {
                return b.convert(Kinds.irType(e.kind), full(e.kids[0]));
            }
            case Ex.LOAD -> {
                int[] k = kids(e);
                int n = b.load(b.elemPtr(k[0], k[1]));
                return e.kind == Kinds.K_BOOLEAN ? b.convert(Types.BOOL, n) : n;
            }
            case Ex.ARRAY_LEN -> {
                return b.arrayLen(full(e.kids[0]));
            }
            case Ex.INTRINSIC -> {
                return b.intrinsic(e.aux, kids(e));
            }
            default -> throw new IllegalStateException("expression op " + e.op + " cannot be emitted");
        }
    }
}
