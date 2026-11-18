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
    private final Aggs.IrTypes types;
    private final Aggs aggs;

    Emitter(IrBuilder b, java.util.Map<String, Integer> functions, Aggs.IrTypes types, Aggs aggs) {
        this.b = b;
        this.functions = functions;
        this.types = types;
        this.aggs = aggs;
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
        List<Pending> outer = pending;
        pending = new ArrayList<>();
        int group = Integer.MIN_VALUE;
        for (int i = 0; i < stmts.size(); i++) {
            St s = stmts.get(i);
            if (s.group() != group) {
                group = s.group();
                // переменные, объявляемые этим исходным стейтментом, - раньше его временных локалей
                declareGroup(stmts, i, group);
            }
            flushPending(out, false);
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
            // вложенные операнды: operandsFrom внутреннего уровня сохраняет свои значения раньше внешнего, а внешний -
            // после того, как вычислил следующие за ними операнды (возможно, с новыми стейтментами); в списке внешние
            // объявления стоят раньше
            int insertAt = out.size();
            List<Integer> now = new ArrayList<>();
            for (List<Integer> g : spillGroups(stmts, i, spills, tEnd)) {
                Pending pg = new Pending(stmts, i, g, values, tEnd, insertAt);
                List<Integer> dl = new ArrayList<>();
                if (finish(pg, dl, false)) {
                    now.addAll(0, dl); // внешний уровень - раньше в списке
                } else {
                    pending.add(pg);
                }
            }
            out.addAll(insertAt, now);
            shiftPending(insertAt, now.size(), null);
            out.addAll(tr);
            i = tEnd - 1;
        }
        flushPending(out, true);
        pending = outer;
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = out.get(i);
        }
        return r;
    }

    /** Группа спиллов, ждущая вычисления операндов потребителя, которые идут после неё. */
    private static final class Pending {
        final List<St> stmts;
        final int i;
        final List<Integer> g;
        final int[] values;
        final int from;
        int insertAt;

        Pending(List<St> stmts, int i, List<Integer> g, int[] values, int from, int insertAt) {
            this.stmts = stmts;
            this.i = i;
            this.g = g;
            this.values = values;
            this.from = from;
            this.insertAt = insertAt;
        }
    }

    /** Группы спиллов, ждущие стейтментов своих операндов. */
    private List<Pending> pending = new ArrayList<>();

    /**
     * Объявляет временные локали группы, если операнды потребителя после них уже можно вычислить (их стейтменты
     * выданы): сначала узлы этих операндов, затем объявления и чтения.
     *
     * @param dest  куда добавить узлы объявлений
     * @param force объявить в любом случае (конец списка)
     * @return группа объявлена
     */
    private boolean finish(Pending pg, List<Integer> dest, boolean force) {
        List<Ex> later = laterOperands(pg.stmts, pg.i, pg.g, pg.from);
        if (!force && later != null) {
            for (Ex e : later) {
                if (!declared(e)) {
                    return false;
                }
            }
        }
        if (later != null) {
            for (Ex sib : later) {
                if (!ready.containsKey(sib) && declared(sib)) {
                    int n = full(sib);
                    ready.put(sib, n);
                }
            }
        }
        for (int j : pg.g) {
            Var v = pg.stmts.get(pg.i + j).var();
            declare(v, v.irType);
            dest.add(spanned(pg.stmts.get(pg.i + j), b.localDecl(v.irLocal, pg.values[j])));
            eager.put(v, b.localGet(v.irLocal));
        }
        return true;
    }

    /** Объявляет отложенные группы, чьи операнды готовы; объявления встают на своё место в списке. */
    private void flushPending(List<Integer> out, boolean force) {
        boolean progress = true;
        while (progress && !pending.isEmpty()) {
            progress = false;
            for (int k = 0; k < pending.size(); k++) {
                Pending pg = pending.get(k);
                List<Integer> made = new ArrayList<>();
                if (finish(pg, made, force)) {
                    pending.remove(k);
                    out.addAll(pg.insertAt, made);
                    shiftPending(pg.insertAt, made.size(), pg);
                    progress = true;
                    break;
                }
            }
        }
    }

    private void shiftPending(int at, int count, Pending except) {
        for (Pending p : pending) {
            if (p != except && p.insertAt > at) {
                p.insertAt += count;
            }
        }
    }

    /** Все временные локали, которые читает {@code e}, уже объявлены. */
    private static boolean declared(Ex e) {
        if (e.op == Ex.LOCAL_GET && e.var.irLocal < 0 && e.var.temp()) {
            return false;
        }
        for (Ex k : e.kids) {
            if (!declared(k)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Потребитель временной локали спилла: выражение (или стейтмент), в списке операндов которого стоит её чтение.
     *
     * @param owner    выражение или стейтмент
     * @param operands операнды одного вызова {@code operandsFrom} в порядке вычисления
     * @param pos      место чтения временной локали среди них
     */
    private record Consumer(Object owner, List<Ex> operands, int pos) {
    }

    /** Спиллы {@code [i, i + spills)}, сгруппированные по потребителю: сначала самый внутренний уровень. */
    private List<List<Integer>> spillGroups(List<St> stmts, int i, int spills, int from) {
        java.util.Map<Object, List<Integer>> byOwner = new java.util.IdentityHashMap<>();
        List<Object> owners = new ArrayList<>();
        Consumer[] cs = new Consumer[spills];
        for (int j = 0; j < spills; j++) {
            cs[j] = consumer(stmts, from, stmts.get(i + j).var());
            Object key = cs[j] == null ? Integer.valueOf(j) : cs[j].owner();
            if (!byOwner.containsKey(key)) {
                byOwner.put(key, new ArrayList<>());
                owners.add(key);
            }
            byOwner.get(key).add(j);
        }
        List<List<Integer>> groups = new ArrayList<>();
        for (Object o : owners) {
            groups.add(byOwner.get(o));
        }
        // верх стека - самый внутренний операнд
        groups.sort((x, y) -> Integer.compare(y.get(y.size() - 1), x.get(x.size() - 1)));
        return groups;
    }

    /** Операнды потребителя, которые frontend-java вычисляет до сохранения значений группы {@code g}. */
    private List<Ex> laterOperands(List<St> stmts, int i, List<Integer> g, int from) {
        Consumer c = null;
        int pos = -1;
        for (int j : g) {
            Consumer cj = consumer(stmts, from, stmts.get(i + j).var());
            if (cj == null) {
                return null;
            }
            c = cj;
            pos = Math.max(pos, cj.pos());
        }
        return c.operands().subList(pos + 1, c.operands().size());
    }

    private Consumer consumer(List<St> stmts, int from, Var v) {
        for (int k = from; k < stmts.size(); k++) {
            St s = stmts.get(k);
            List<Ex> ops = switch (s.kind()) {
                case St.STORE -> s.ordered() ? List.of(s.b(), s.c()) : List.of();
                case St.PSTORE -> s.aux() == 1 ? List.of(s.a().kids[0], s.a().kids[1], s.b()) : List.of();
                default -> List.of();
            };
            for (int p = 0; p < ops.size(); p++) {
                if (readsTemp(ops.get(p), v)) {
                    return new Consumer(s, ops, p);
                }
            }
            for (Ex root : new Ex[]{s.a(), s.b(), s.c()}) {
                Consumer c = root == null ? null : consumer(root, v);
                if (c != null) {
                    return c;
                }
            }
        }
        return null;
    }

    private static Consumer consumer(Ex e, Var v) {
        for (int p = 0; p < e.kids.length; p++) {
            if (readsTemp(e.kids[p], v)) {
                return new Consumer(e, java.util.Arrays.asList(e.kids), p);
            }
        }
        for (Ex k : e.kids) {
            Consumer c = consumer(k, v);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    /** Операнд - чтение временной локали {@code v} (возможно, под неявным приведением). */
    private static boolean readsTemp(Ex e, Var v) {
        Ex x = e.op == Ex.CONVERT && e.implicit ? e.kids[0] : e;
        return x.op == Ex.LOCAL_GET && x.var == v;
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
            // чтение отложенной временной локали создаётся при её объявлении
            if (!ready.containsKey(e) && declared(e)) {
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
            v.irLocal = b.declareLocal(irType >= 0 ? irType : types.value(v.kind, v.elemKind, v.space, v.cls));
            if (v.name != null) {
                b.nameLocal(v.irLocal, v.name);
            }
        }
    }

    private int stmt(St s) {
        switch (s.kind()) {
            case St.DECL -> {
                if (s.var().owned) {
                    // localPointer в frontend-java: тип ссылки создаётся раньше значения ячейки
                    b.ptr(AddressSpace.PRIVATE, Kinds.storageType(s.var().elemKind));
                }
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
            case St.PSTORE -> {
                int st;
                if (s.aux() == 1) {
                    // p.set(i, v): указатель, индекс и значение по порядку, затем адрес и запись
                    Ex ptr = s.a();
                    if (ptr.aux == 1) {
                        b.ptr(ptr.space, Kinds.storageType(ptr.elemKind));
                    }
                    int base = full(ptr.kids[0]);
                    int idxCore = core(ptr.kids[1]);
                    int valCore = core(s.b());
                    int addr = b.elemPtr(base, finish(ptr.kids[1], idxCore));
                    st = b.store(addr, finish(s.b(), valCore));
                } else {
                    int val = full(s.b());
                    st = b.store(full(s.a()), val);
                }
                afterWrite();
                return st;
            }
            case St.FSTORE -> {
                int node = full(s.b());
                int[] path = s.values;
                for (int k = path.length - 1; k >= 0; k--) {
                    int base = rootRead(s.a());
                    for (int i = 0; i < k; i++) {
                        base = b.fieldGet(base, path[i]);
                    }
                    node = b.fieldSet(base, path[k], node);
                }
                int st;
                if (s.a().op == Ex.LOCAL_GET) {
                    st = b.localSet(s.a().var.irLocal, node);
                } else {
                    int[] k = kids(s.a());
                    st = b.store(b.elemPtr(k[0], k[1]), node);
                }
                afterWrite();
                return st;
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

    /** Чтение корня записи поля заново ({@code rootRead} в frontend-java). */
    private int rootRead(Ex root) {
        if (root.op == Ex.LOCAL_GET) {
            return b.localGet(root.var.irLocal);
        }
        int[] k = kids(root);
        return b.load(b.elemPtr(k[0], k[1]));
    }

    /** {@code p.value++} в выражении: значение - local_get старого значения сразу за записью (MODE_OLD). */
    private void afterWrite() {
        if (oldValue != null) {
            eager.put(oldValue, b.localGet(oldValue.irLocal));
            oldValue = null;
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

    /** Нулевой агрегат ({@code zeroAgg} в frontend-java). */
    private int zeroAgg(String cls) {
        int t = types.of(cls);
        Aggs.Vec v = Aggs.vector(cls);
        if (v != null) {
            return b.splat(t, node(MethodLowerer.zero(v.scalarKind())));
        }
        List<org.objectweb.asm.tree.FieldNode> fs = aggs.fields(cls);
        int[] f = new int[fs.size()];
        for (int i = 0; i < f.length; i++) {
            Aggs.Shape sh = aggs.shape(fs.get(i).desc);
            f[i] = sh.kind() == Kinds.K_AGG ? zeroAgg(sh.cls()) : node(MethodLowerer.zero(sh.kind()));
        }
        return b.structBuild(t, f);
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
                if (e.kind == Kinds.K_AGG) {
                    types.of(e.cls);
                }
                int[] k = kids(e);
                return b.binary(e.aux, k[0], k[1]);
            }
            case Ex.NEG -> {
                if (e.kind == Kinds.K_AGG) {
                    types.of(e.cls);
                }
                return b.neg(kids(e)[0]);
            }
            case Ex.NOT -> {
                if (e.kind == Kinds.K_AGG) {
                    types.of(e.cls);
                }
                return b.not(kids(e)[0]);
            }
            case Ex.FIELD_GET -> {
                return b.fieldGet(kids(e)[0], e.aux);
            }
            case Ex.EXTRACT -> {
                return b.extract(kids(e)[0], e.aux);
            }
            case Ex.BUILD -> {
                int t = types.of(e.cls);
                int[] k = kids(e);
                return Aggs.vector(e.cls) != null ? b.build(t, k) : b.structBuild(t, k);
            }
            case Ex.SPLAT -> {
                int t = types.of(e.cls);
                return b.splat(t, kids(e)[0]);
            }
            case Ex.INSERT -> {
                types.of(e.cls);
                int[] k = kids(e);
                return b.insert(k[0], e.aux, k[1]);
            }
            case Ex.SHUFFLE -> {
                types.of(e.kids[0].cls);
                return b.shuffle(full(e.kids[0]), e.lanes);
            }
            case Ex.LOAD_PTR -> {
                return b.load(full(e.kids[0]));
            }
            case Ex.ELEM_PTR -> {
                if (e.aux == 1) {
                    b.ptr(e.space, Kinds.storageType(e.elemKind));
                }
                int[] k = kids(e);
                return b.elemPtr(k[0], k[1]);
            }
            case Ex.LOCAL_ADDR -> {
                return b.localAddr(e.var.irLocal);
            }
            case Ex.ZERO_AGG -> {
                return zeroAgg(e.cls);
            }
            case Ex.CONVERT -> {
                return b.convert(Kinds.irType(e.kind), full(e.kids[0]));
            }
            case Ex.BITCAST -> {
                return b.bitcast(Kinds.irType(e.kind), full(e.kids[0]));
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
