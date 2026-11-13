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
    private final java.util.Map<String, Integer> functions;
    /**
     * {@code local_get} временной локали, созданный сразу за её объявлением (как {@code frontend-java}: результат
     * эффектного вызова и сохранённое значение операнда - узел {@code local_get}, созданный тут же); забирается первым
     * чтением.
     */
    private final java.util.Map<Var, Integer> eager = new java.util.IdentityHashMap<>();
    /** Узлы значений стека, созданные заранее ({@link #materialize}); забираются первым родителем. */
    private final java.util.Map<Ex, Integer> ready = new java.util.IdentityHashMap<>();
    /** Временная локаль старого значения {@code x++}, ждущая записи {@code x}. */
    private Var oldValue;

    /**
     * @param b         builder модуля
     * @param functions уже построенные функции модуля: ключ ({@code MethodLowerer.key}) → индекс
     */
    Emitter(IrBuilder b, java.util.Map<String, Integer> functions) {
        this.b = b;
        this.functions = functions;
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
        for (int n : list(body)) {
            out.add(n);
        }
        return out;
    }

    /** Узлы стейтментов списка по порядку. */
    private int[] list(List<St> stmts) {
        List<Integer> out = new ArrayList<>();
        int group = Integer.MIN_VALUE;
        for (int i = 0; i < stmts.size(); i++) {
            St s = stmts.get(i);
            if (s.group() != group) {
                group = s.group();
                // переменные, объявляемые этим исходным стейтментом, - раньше его временных локалей
                declareGroup(stmts, i, group);
            }
            materialize(s);
            int spills = 0;
            while (i + spills < stmts.size() - 1 && isSpill(stmts.get(i + spills))) {
                spills++;
            }
            if (spills == 0) {
                emit(s, out);
                continue;
            }
            // operandsFrom в frontend-java: открытые значения сохраняются после того, как операнд выдал свои
            // стейтменты, а в списке встают перед ними; само значение вычислено раньше - на своём месте
            int[] values = new int[spills];
            for (int j = 0; j < spills; j++) {
                values[j] = full(stmts.get(i + j).a());
            }
            int t = i + spills;
            int tEnd = t + 1;
            St trigger = stmts.get(t);
            if (trigger.kind() == St.DECL && trigger.aux() == 2 && tEnd < stmts.size()) {
                tEnd++; // операнд x++: старое значение и запись x
            }
            List<Integer> tr = new ArrayList<>();
            for (int k = t; k < tEnd; k++) {
                St ts = stmts.get(k);
                if (ts.group() != group) {
                    group = ts.group();
                    declareGroup(stmts, k, group);
                }
                materialize(ts);
                emit(ts, tr);
            }
            for (int j = 0; j < spills; j++) {
                Var v = stmts.get(i + j).var();
                declare(v, v.irType);
                out.add(spanned(stmts.get(i + j), b.localDecl(v.irLocal, values[j])));
                eager.put(v, b.localGet(v.irLocal));
            }
            out.addAll(tr);
            i = tEnd - 1;
        }
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = out.get(i);
        }
        return r;
    }

    private void emit(St s, List<Integer> out) {
        if (s.kind() == St.SHORT) {
            int[] two = shortCircuit(s);
            out.add(two[0]);
            out.add(spanned(s, two[1]));
        } else {
            out.add(spanned(s, stmt(s)));
        }
    }

    /**
     * Значения, которые лежали на стеке, когда появился стейтмент: в {@code frontend-java} их узлы уже созданы (операнд
     * вычисляется сразу), поэтому они создаются раньше узлов стейтмента и берутся первым родителем.
     */
    private void materialize(St s) {
        if (s.ready == null) {
            return;
        }
        for (Ex e : s.ready) {
            if (!ready.containsKey(e)) {
                ready.put(e, full(e));
            }
        }
    }

    /**
     * {@code shortCircuit} в {@code frontend-java}: первый операнд, стейтменты и значение второго, затем
     * {@code tmp = первый; if (tmp | !tmp) { стейтменты; tmp = второй; }} - узлы в этом порядке.
     *
     * @return объявление временной локали и {@code if}
     */
    private int[] shortCircuit(St s) {
        int cx = full(s.a());
        int[] body = list(s.body);
        int cy = full(s.b());
        Var v = s.var();
        declare(v, v.irType);
        int d = b.localDecl(v.irLocal, cx);
        int get = b.localGet(v.irLocal);
        int guard = s.aux() == 1 ? get : b.not(get);
        int set = b.localSet(v.irLocal, cy);
        int[] all = java.util.Arrays.copyOf(body, body.length + 1);
        all[body.length] = set;
        int iff = b.ifStmt(guard, b.block(all), -1);
        eager.put(v, b.localGet(v.irLocal));
        return new int[]{d, iff};
    }

    private static boolean isSpill(St s) {
        return s.kind() == St.DECL && s.aux() == 1;
    }

    private void declareGroup(List<St> stmts, int from, int group) {
        for (int j = from; j < stmts.size() && stmts.get(j).group() == group; j++) {
            St d = stmts.get(j);
            if (d.kind() == St.DECL && !d.var().temp()) {
                declare(d.var(), d.var().irType);
            }
        }
    }

    private int spanned(St s, int node) {
        if (s.line() > 0) {
            b.span(node, s.line(), 0);
        }
        return node;
    }

    private int blockOf(List<St> stmts) {
        return b.block(list(stmts));
    }

    /** Шаг цикла: один стейтмент - сам, несколько - блок (как {@code forStmt} в {@code frontend-java}). */
    private int update(List<St> upd) {
        if (upd == null || upd.isEmpty()) {
            return -1;
        }
        int[] n = list(upd);
        return n.length == 1 ? n[0] : b.block(n);
    }

    private int loop(St s) {
        if (s.pre == null) {
            int c = s.a() == null ? b.constBool(true) : full(s.a());
            int body = blockOf(s.body);
            return b.loop(c, body, update(s.update));
        }
        // условие с эффектами: loop(true, { pre; if (!c) break; body }, update)
        int[] pre = list(s.pre);
        int c = full(s.a());
        int body = blockOf(s.body);
        int upd = update(s.update);
        int guard = b.ifStmt(b.not(c), b.block(b.breakStmt()), -1);
        int t = b.constBool(true);
        int[] inner = new int[pre.length + 2];
        System.arraycopy(pre, 0, inner, 0, pre.length);
        inner[pre.length] = guard;
        inner[pre.length + 1] = body;
        return b.loop(t, b.block(inner), upd);
    }

    private int switchStmt(St s) {
        int key = full(s.a());
        int[] bodies = new int[s.values.length];
        int label = 0;
        for (int g = 0; g < s.cases.size(); g++) {
            int[] nodes = list(s.cases.get(g));
            for (int j = 0; j < s.sizes[g]; j++) {
                // метки группы без собственного тела «проваливаются» в тело последней (как в frontend-java)
                bodies[label++] = j == s.sizes[g] - 1 ? b.block(nodes) : b.block();
            }
        }
        return b.switchStmt(key, s.defaults, s.values, bodies);
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
                int d = b.localDecl(s.var().irLocal, full(s.a()));
                if (s.aux() == 2) {
                    oldValue = s.var();
                }
                if (s.var().temp() && (s.a().op == Ex.CALL || s.a().op == Ex.INTRINSIC)) {
                    eager.put(s.var(), b.localGet(s.var().irLocal));
                }
                return d;
            }
            case St.SET -> {
                int set = b.localSet(s.var().irLocal, full(s.a()));
                if (oldValue != null) {
                    // x++ в выражении: значение - local_get старого значения сразу за записью (MODE_OLD)
                    eager.put(oldValue, b.localGet(oldValue.irLocal));
                    oldValue = null;
                }
                return set;
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
            case St.IF -> {
                int c = full(s.a());
                int th = blockOf(s.body);
                int el = s.alt == null ? -1 : blockOf(s.alt);
                return b.ifStmt(c, th, el);
            }
            case St.LOOP -> {
                return loop(s);
            }
            case St.DO_WHILE -> {
                int body = blockOf(s.body);
                return b.doWhile(body, full(s.a()));
            }
            case St.SWITCH -> {
                return switchStmt(s);
            }
            case St.BREAK -> {
                return b.breakStmt();
            }
            case St.CONTINUE -> {
                return b.continueStmt();
            }
            case St.BLOCK -> {
                return blockOf(s.body);
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
        Integer pre = ready.remove(e);
        if (pre != null) {
            return pre;
        }
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
        Integer made = ready.remove(e);
        if (made != null) {
            return made;
        }
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
                Integer pre = eager.remove(e.var);
                return pre != null ? pre : b.localGet(e.var.irLocal);
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
            case Ex.CALL -> {
                Integer f = functions.get(e.target);
                if (f == null) {
                    throw new IllegalStateException("function " + e.target + " is not built before the caller");
                }
                return b.call(f, kids(e));
            }
            case Ex.SELECT -> {
                // как conditional() в frontend-java: условие, затем каждое плечо целиком (с приведением)
                int c = full(e.kids[0]);
                int x = full(e.kids[1]);
                int y = full(e.kids[2]);
                return b.select(c, x, y);
            }
            case Ex.LAND -> {
                int c = full(e.kids[0]);
                int d = full(e.kids[1]);
                return b.select(c, d, b.constBool(false));
            }
            case Ex.LOR -> {
                int c = full(e.kids[0]);
                int d = full(e.kids[1]);
                int t = b.constBool(true);
                return b.select(c, t, d);
            }
            default -> throw new IllegalStateException("expression op " + e.op + " cannot be emitted");
        }
    }
}
