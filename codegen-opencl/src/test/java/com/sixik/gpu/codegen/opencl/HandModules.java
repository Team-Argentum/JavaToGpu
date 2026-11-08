package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Модули IR, собранные вручную: то, чего нельзя написать на Java (беззнаковые типы, {@code bitcast}, все пары
 * преобразований, {@code continue} в цикле со сложным шагом) или нужно проверить точечно (константы-крайности,
 * builtins на всех типах, atomics, массивы-параметры device-функций).
 *
 * <p>Каждый модуль - ядро с массивами-параметрами: входы читаются из {@code in*}, результаты операций пишутся в
 * {@code out*} по константным индексам. Одни и те же модули используются golden-тестами (текст C) и device-тестом
 * (исполнение на реальном драйвере против {@code ir-interpreter}).</p>
 */
final class HandModules {

    private HandModules() {
    }

    /** Числовые типы хранения в порядке, фиксирующем имена модулей и порядок параметров. */
    static final int[] NUMERIC = {Types.I8, Types.U8, Types.I16, Types.U16, Types.I32, Types.U32, Types.I64,
            Types.U64, Types.F32, Types.F64};

    /**
     * @return все ручные модули по именам (порядок вставки)
     */
    static Map<String, IrModule> all() {
        Map<String, IrModule> m = new LinkedHashMap<>();
        for (int t : new int[]{Types.I32, Types.U32, Types.I64, Types.U64, Types.F32, Types.F64}) {
            m.put("ops_" + name(t), ops(t));
        }
        for (int t : NUMERIC) {
            m.put("convert_from_" + name(t), conversions(t));
        }
        m.put("bitcasts", bitcasts());
        m.put("bools", bools());
        m.put("control", control());
        m.put("device_functions", deviceFunctions());
        m.put("constants", constants());
        m.put("builtins", builtins());
        m.put("atomics", atomics());
        m.put("work_group_attr", workGroupAttribute());
        return m;
    }

    /**
     * @param t скалярный тип IR
     * @return короткое имя типа для имён модулей и локалей
     */
    static String name(int t) {
        return switch (t) {
            case Types.BOOL -> "bool";
            case Types.I8 -> "i8";
            case Types.U8 -> "u8";
            case Types.I16 -> "i16";
            case Types.U16 -> "u16";
            case Types.I32 -> "i32";
            case Types.U32 -> "u32";
            case Types.I64 -> "i64";
            case Types.U64 -> "u64";
            case Types.F32 -> "f32";
            default -> "f64";
        };
    }

    // ---------------------------------------------------------------------------------------------
    // каркас
    // ---------------------------------------------------------------------------------------------

    /** Построитель ядра: стейтменты верхнего уровня копятся в списке. */
    private static final class K {
        final IrBuilder b = new IrBuilder();
        final List<Integer> st = new ArrayList<>();

        int global(int elem) {
            return b.array(AddressSpace.GLOBAL, elem);
        }

        /** Указатель на элемент массива-параметра с константным индексом. */
        int at(int param, int idx) {
            return b.elemPtr(b.param(param), b.constI32(idx));
        }

        int get(int param, int idx) {
            return b.load(at(param, idx));
        }

        void put(int param, int idx, int value) {
            st.add(b.store(at(param, idx), value));
        }

        /** Объявляет локаль и сразу инициализирует. */
        int local(int type, String name, int init) {
            int l = b.declareLocal(type);
            b.nameLocal(l, name);
            st.add(b.localDecl(l, init));
            return l;
        }

        /** Константа типа {@code t} (для вещественных - из {@code long}-значения как числа). */
        int cst(int t, long v) {
            return switch (t) {
                case Types.F32 -> b.constF32((float) v);
                case Types.F64 -> b.constF64((double) v);
                default -> b.constInt(t, v);
            };
        }

        IrModule finish() {
            b.endFunction(b.block(st.stream().mapToInt(Integer::intValue).toArray()));
            return b.build();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // арифметика, битовые операции, сдвиги, сравнения - по типу
    // ---------------------------------------------------------------------------------------------

    /**
     * Все операции типа {@code t} над парой {@code a = in[0]}, {@code b = in[1]}; результаты - в {@code out[0..]}.
     * Порядок результатов фиксирован и известен device-тесту: {@link #opsResultCount(int)}.
     *
     * @param t {@code I32, U32, I64, U64, F32, F64}
     * @return модуль
     */
    static IrModule ops(int t) {
        K k = new K();
        int arr = k.global(t);
        k.b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr);
        k.b.nameFunction("ops_" + name(t));
        k.b.nameParams("in", "out");
        int la = k.local(t, "a", k.get(0, 0));
        int lb = k.local(t, "b", k.get(0, 1));
        int idx = 0;
        boolean fl = Types.isFloat(t);
        int[] arith = {Op.ADD, Op.SUB, Op.MUL, Op.DIV, Op.REM};
        for (int op : arith) {
            k.put(1, idx++, k.b.binary(op, k.b.localGet(la), k.b.localGet(lb)));
        }
        k.put(1, idx++, k.b.neg(k.b.localGet(la)));
        if (!fl) {
            for (int op : new int[]{Op.AND, Op.OR, Op.XOR, Op.SHL, Op.SHR, Op.USHR}) {
                k.put(1, idx++, k.b.binary(op, k.b.localGet(la), k.b.localGet(lb)));
            }
            k.put(1, idx++, k.b.not(k.b.localGet(la)));
            int bits = Types.bits(t);
            for (int op : new int[]{Op.SHL, Op.SHR, Op.USHR}) {
                for (int count : new int[]{1, bits - 1, bits, bits + 1, 2 * bits + 3}) {
                    k.put(1, idx++, k.b.binary(op, k.b.localGet(la), k.cst(t, count)));
                }
            }
            // константа в левой части: проверяет приведение константного операнда к беззнаковому типу
            k.put(1, idx++, k.b.binary(Op.ADD, k.cst(t, 1), k.b.localGet(la)));
            k.put(1, idx++, k.b.binary(Op.MUL, k.cst(t, -3), k.b.localGet(lb)));
            k.put(1, idx++, k.b.binary(Op.SUB, k.b.localGet(la), k.cst(t, -1)));
        }
        for (int op : new int[]{Op.EQ, Op.NE, Op.LT, Op.LE, Op.GT, Op.GE}) {
            int cond = k.b.binary(op, k.b.localGet(la), k.b.localGet(lb));
            k.put(1, idx++, k.b.select(cond, k.cst(t, 1), k.cst(t, 0)));
        }
        int max = Builtins.idOf("max");
        int min = Builtins.idOf("min");
        k.put(1, idx++, k.b.intrinsic(max, k.b.localGet(la), k.b.localGet(lb)));
        k.put(1, idx++, k.b.intrinsic(min, k.b.localGet(la), k.b.localGet(lb)));
        // вложенность и приоритеты: (a + b) * (a - b), -(a * b), (a + b) < (a - b) ? a : b
        int sum = k.b.binary(Op.ADD, k.b.localGet(la), k.b.localGet(lb));
        int diff = k.b.binary(Op.SUB, k.b.localGet(la), k.b.localGet(lb));
        k.put(1, idx++, k.b.binary(Op.MUL, sum, diff));
        k.put(1, idx++, k.b.neg(k.b.binary(Op.MUL, k.b.localGet(la), k.b.localGet(lb))));
        int cmp = k.b.binary(Op.LT, k.b.binary(Op.ADD, k.b.localGet(la), k.b.localGet(lb)),
                k.b.binary(Op.SUB, k.b.localGet(la), k.b.localGet(lb)));
        k.put(1, idx++, k.b.select(cmp, k.b.localGet(la), k.b.localGet(lb)));
        return k.finish();
    }

    /**
     * @param t тип
     * @return сколько значений записывает {@link #ops(int)} в {@code out}
     */
    static int opsResultCount(int t) {
        boolean fl = Types.isFloat(t);
        return 5 + 1 + (fl ? 0 : 6 + 1 + 15 + 3) + 6 + 2 + 3;
    }

    // ---------------------------------------------------------------------------------------------
    // преобразования
    // ---------------------------------------------------------------------------------------------

    /** Количество входов в модулях преобразований. */
    static final int CONVERT_INPUTS = 8;

    /**
     * Преобразование каждого из {@link #CONVERT_INPUTS} входов типа {@code from} во все числовые типы.
     * Параметры: {@code in} и по массиву {@code out_<тип>} для каждого из {@link #NUMERIC}.
     *
     * @param from исходный числовой тип
     * @return модуль
     */
    static IrModule conversions(int from) {
        K k = new K();
        k.b.beginFunction(IrModule.KERNEL, Types.VOID, paramTypesForConversion(k.b, from));
        k.b.nameFunction("convert_from_" + name(from));
        String[] names = new String[1 + NUMERIC.length];
        names[0] = "in";
        for (int i = 0; i < NUMERIC.length; i++) {
            names[1 + i] = "out_" + name(NUMERIC[i]);
        }
        k.b.nameParams(names);
        for (int i = 0; i < CONVERT_INPUTS; i++) {
            for (int j = 0; j < NUMERIC.length; j++) {
                k.put(1 + j, i, k.b.convert(NUMERIC[j], k.get(0, i)));
            }
        }
        return k.finish();
    }

    private static int[] paramTypesForConversion(IrBuilder b, int from) {
        int[] p = new int[1 + NUMERIC.length];
        p[0] = b.array(AddressSpace.GLOBAL, from);
        for (int i = 0; i < NUMERIC.length; i++) {
            p[1 + i] = b.array(AddressSpace.GLOBAL, NUMERIC[i]);
        }
        return p;
    }

    /** Переинтерпретация битов: {@code I32 <-> U32 <-> F32}, {@code I64 <-> U64 <-> F64} и обратно. */
    static IrModule bitcasts() {
        K k = new K();
        int[] p = {
                k.b.array(AddressSpace.GLOBAL, Types.I32), k.b.array(AddressSpace.GLOBAL, Types.I64),
                k.b.array(AddressSpace.GLOBAL, Types.U32), k.b.array(AddressSpace.GLOBAL, Types.F32),
                k.b.array(AddressSpace.GLOBAL, Types.U64), k.b.array(AddressSpace.GLOBAL, Types.F64),
                k.b.array(AddressSpace.GLOBAL, Types.I32), k.b.array(AddressSpace.GLOBAL, Types.I64)};
        k.b.beginFunction(IrModule.KERNEL, Types.VOID, p);
        k.b.nameFunction("bitcasts");
        k.b.nameParams("in32", "in64", "out_u32", "out_f32", "out_u64", "out_f64", "back32", "back64");
        for (int i = 0; i < 4; i++) {
            k.put(2, i, k.b.bitcast(Types.U32, k.get(0, i)));
            k.put(3, i, k.b.bitcast(Types.F32, k.get(0, i)));
            k.put(4, i, k.b.bitcast(Types.U64, k.get(1, i)));
            k.put(5, i, k.b.bitcast(Types.F64, k.get(1, i)));
            k.put(6, i, k.b.bitcast(Types.I32, k.b.bitcast(Types.F32, k.get(0, i))));
            k.put(7, i, k.b.bitcast(Types.I64, k.b.bitcast(Types.U64, k.get(1, i))));
        }
        return k.finish();
    }

    /** Логика: {@code and/or/xor/not} на {@code bool}, {@code bool <-> int}, сравнения и {@code select}. */
    static IrModule bools() {
        K k = new K();
        int ai = k.b.array(AddressSpace.GLOBAL, Types.I32);
        int af = k.b.array(AddressSpace.GLOBAL, Types.F32);
        k.b.beginFunction(IrModule.KERNEL, Types.VOID, ai, af, ai);
        k.b.nameFunction("bools");
        k.b.nameParams("in", "inf", "out");
        int x = k.local(Types.I32, "x", k.get(0, 0));
        int y = k.local(Types.I32, "y", k.get(0, 1));
        int p = k.local(Types.BOOL, "p", k.b.binary(Op.LT, k.b.localGet(x), k.b.localGet(y)));
        int q = k.local(Types.BOOL, "q", k.b.binary(Op.NE, k.b.localGet(x), k.cst(Types.I32, 0)));
        int idx = 0;
        int i1 = k.cst(Types.I32, 1);
        int i0 = k.cst(Types.I32, 0);
        k.put(2, idx++, k.b.select(k.b.binary(Op.AND, k.b.localGet(p), k.b.localGet(q)), i1, i0));
        k.put(2, idx++, k.b.select(k.b.binary(Op.OR, k.b.localGet(p), k.b.localGet(q)), i1, i0));
        k.put(2, idx++, k.b.select(k.b.binary(Op.XOR, k.b.localGet(p), k.b.localGet(q)), i1, i0));
        k.put(2, idx++, k.b.select(k.b.not(k.b.localGet(p)), i1, i0));
        k.put(2, idx++, k.b.select(k.b.binary(Op.EQ, k.b.localGet(p), k.b.localGet(q)), i1, i0));
        k.put(2, idx++, k.b.select(k.b.binary(Op.NE, k.b.localGet(p), k.b.localGet(q)), i1, i0));
        k.put(2, idx++, k.b.convert(Types.I32, k.b.localGet(p)));
        k.put(2, idx++, k.b.convert(Types.I32, k.b.convert(Types.BOOL, k.b.localGet(x))));
        k.put(2, idx++, k.b.select(k.b.not(k.b.binary(Op.AND, k.b.localGet(p),
                k.b.binary(Op.GT, k.b.localGet(y), k.b.localGet(x)))), k.cst(Types.I32, 7), k.cst(Types.I32, 9)));
        k.put(2, idx++, k.b.select(k.b.constBool(true), k.cst(Types.I32, 11), k.cst(Types.I32, 12)));
        k.put(2, idx++, k.b.convert(Types.I32, k.b.constBool(false)));
        return k.finish();
    }

    // ---------------------------------------------------------------------------------------------
    // управление потоком
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code continue} в цикле со сложным шагом (переход на метку), {@code do-while}, {@code switch} по
     * {@code I32} и {@code U32} с отрицательными и «большими» метками, {@code default} посередине, цепочка
     * {@code else if}. Вход {@code in[0]} - число {@code n}; результаты - {@code out[0..8]}.
     */
    static IrModule control() {
        K k = new K();
        int ai = k.b.array(AddressSpace.GLOBAL, Types.I32);
        k.b.beginFunction(IrModule.KERNEL, Types.VOID, ai, ai);
        k.b.nameFunction("control");
        k.b.nameParams("in", "out");
        IrBuilder b = k.b;
        int n = k.local(Types.I32, "n", k.get(0, 0));

        // 1) шаг со вторым объявлением: цикл превращается в while + метка; continue -> goto
        int acc = k.local(Types.I32, "acc", k.cst(Types.I32, 0));
        int i = k.local(Types.I32, "i", k.cst(Types.I32, 0));
        int next = b.declareLocal(Types.I32);
        b.nameLocal(next, "next");
        int skip = b.ifStmt(b.binary(Op.EQ, b.binary(Op.REM, b.localGet(i), k.cst(Types.I32, 3)), k.cst(Types.I32, 0)),
                b.block(b.continueStmt()), -1);
        int add = b.localSet(acc, b.binary(Op.ADD, b.localGet(acc), b.binary(Op.MUL, b.localGet(i), b.localGet(n))));
        int update = b.block(b.localDecl(next, b.binary(Op.ADD, b.localGet(i), k.cst(Types.I32, 1))),
                b.localSet(i, b.localGet(next)));
        k.st.add(b.loop(b.binary(Op.LT, b.localGet(i), k.cst(Types.I32, 10)), b.block(skip, add), update));
        k.put(1, 0, b.localGet(acc));

        // 2) простой шаг, внутри switch с continue, обращённым к циклу
        int j = k.local(Types.I32, "j", k.cst(Types.I32, 0));
        int sum = k.local(Types.I32, "sum", k.cst(Types.I32, 0));
        int key = b.binary(Op.AND, b.binary(Op.ADD, b.localGet(n), b.localGet(j)), k.cst(Types.I32, 3));
        int sw = b.switchStmt(key, new boolean[]{false, false, true},
                new int[]{0, 1, 0},
                new int[]{b.block(b.continueStmt()),
                        b.block(b.localSet(sum, b.binary(Op.ADD, b.localGet(sum), k.cst(Types.I32, 10)))),
                        b.block(b.localSet(sum, b.binary(Op.ADD, b.localGet(sum), k.cst(Types.I32, 100))))});
        int tail = b.localSet(sum, b.binary(Op.ADD, b.localGet(sum), b.localGet(j)));
        k.st.add(b.loop(b.binary(Op.LT, b.localGet(j), k.cst(Types.I32, 6)), b.block(sw, tail),
                b.localSet(j, b.binary(Op.ADD, b.localGet(j), k.cst(Types.I32, 1)))));
        k.put(1, 1, b.localGet(sum));

        // 3) do-while с continue (идёт на проверку условия)
        int d = k.local(Types.I32, "d", k.cst(Types.I32, 0));
        int body = b.block(
                b.localSet(d, b.binary(Op.ADD, b.localGet(d), k.cst(Types.I32, 2))),
                b.ifStmt(b.binary(Op.GT, b.localGet(d), k.cst(Types.I32, 6)), b.block(b.continueStmt()), -1),
                b.localSet(d, b.binary(Op.ADD, b.localGet(d), k.cst(Types.I32, 1))));
        k.st.add(b.doWhile(body, b.binary(Op.LT, b.localGet(d), k.cst(Types.I32, 20))));
        k.put(1, 2, b.localGet(d));

        // 4) switch по I32: отрицательные метки, default посередине, проваливание
        int r = k.local(Types.I32, "r", k.cst(Types.I32, 0));
        int key64 = b.binary(Op.SUB, b.localGet(n), k.cst(Types.I32, 2));
        int sw64 = b.switchStmt(key64, new boolean[]{false, true, false, false},
                new int[]{-2, 0, 5, 7},
                new int[]{
                        b.block(b.localSet(r, b.binary(Op.ADD, b.localGet(r), k.cst(Types.I32, 1)))),
                        b.block(b.localSet(r, b.binary(Op.ADD, b.localGet(r), k.cst(Types.I32, 20))), b.breakStmt()),
                        b.block(b.localSet(r, b.binary(Op.ADD, b.localGet(r), k.cst(Types.I32, 300))), b.breakStmt()),
                        b.block(b.localSet(r, b.binary(Op.ADD, b.localGet(r), k.cst(Types.I32, 4000))))});
        k.st.add(sw64);
        k.put(1, 3, b.localGet(r));

        // 5) switch по U32: метка -1 это 0xFFFFFFFF
        int r2 = k.local(Types.I32, "r2", k.cst(Types.I32, 0));
        int keyU = b.convert(Types.U32, b.binary(Op.SUB, b.localGet(n), k.cst(Types.I32, 1)));
        int swU = b.switchStmt(keyU, new boolean[]{false, false, true}, new int[]{-1, 3, 0},
                new int[]{
                        b.block(b.localSet(r2, k.cst(Types.I32, 111)), b.breakStmt()),
                        b.block(b.localSet(r2, k.cst(Types.I32, 333)), b.breakStmt()),
                        b.block(b.localSet(r2, k.cst(Types.I32, 999)))});
        k.st.add(swU);
        k.put(1, 4, b.localGet(r2));

        // 6) цепочка else if
        int c = k.local(Types.I32, "c", k.cst(Types.I32, 0));
        int chain = b.ifStmt(b.binary(Op.LT, b.localGet(n), k.cst(Types.I32, 0)),
                b.block(b.localSet(c, k.cst(Types.I32, 1))),
                b.block(b.ifStmt(b.binary(Op.EQ, b.localGet(n), k.cst(Types.I32, 0)),
                        b.block(b.localSet(c, k.cst(Types.I32, 2))),
                        b.block(b.ifStmt(b.binary(Op.LT, b.localGet(n), k.cst(Types.I32, 10)),
                                b.block(b.localSet(c, k.cst(Types.I32, 3))),
                                b.block(b.localSet(c, k.cst(Types.I32, 4))))))));
        k.st.add(chain);
        k.put(1, 5, b.localGet(c));

        // 7) while(true) с break и вложенный цикл; break выходит только из внутреннего
        int w = k.local(Types.I32, "w", k.cst(Types.I32, 0));
        int cnt = k.local(Types.I32, "cnt", k.cst(Types.I32, 0));
        int inner = b.loop(b.constBool(true),
                b.block(b.localSet(cnt, b.binary(Op.ADD, b.localGet(cnt), k.cst(Types.I32, 1))),
                        b.ifStmt(b.binary(Op.GE, b.localGet(cnt), k.cst(Types.I32, 5)), b.block(b.breakStmt()), -1)),
                -1);
        k.st.add(b.loop(b.binary(Op.LT, b.localGet(w), k.cst(Types.I32, 3)),
                b.block(inner, b.localSet(w, b.binary(Op.ADD, b.localGet(w), k.cst(Types.I32, 1)))), -1));
        k.put(1, 6, b.binary(Op.ADD, b.localGet(w), b.binary(Op.MUL, b.localGet(cnt), k.cst(Types.I32, 100))));

        // 8) пустые ветви и пустой блок
        k.st.add(b.ifStmt(b.binary(Op.GT, b.localGet(n), k.cst(Types.I32, 1000000)), b.block(), b.block()));
        k.put(1, 7, k.cst(Types.I32, 77));
        return k.finish();
    }

    // ---------------------------------------------------------------------------------------------
    // функции и массивы-параметры
    // ---------------------------------------------------------------------------------------------

    /** Device-функции с массивами {@code global/private/constant}, булевым и 64-битным результатом. */
    static IrModule deviceFunctions() {
        IrBuilder b = new IrBuilder();
        int gf = b.array(AddressSpace.GLOBAL, Types.F32);
        int cf = b.array(AddressSpace.CONSTANT, Types.F32);
        int pi = b.array(AddressSpace.PRIVATE, Types.I32);
        int gi = b.array(AddressSpace.GLOBAL, Types.I32);

        // f0: float sumArr(global float[] a) - цикл по длине массива
        int f0 = b.beginFunction(IrModule.DEVICE, Types.F32, gf);
        b.nameFunction("sumArr");
        b.nameParams("a");
        int s = b.declareLocal(Types.F32);
        b.nameLocal(s, "s");
        int i = b.declareLocal(Types.I32);
        b.nameLocal(i, "i");
        int loop = b.loop(b.binary(Op.LT, b.localGet(i), b.arrayLen(b.param(0))),
                b.block(b.localSet(s, b.binary(Op.ADD, b.localGet(s), b.load(b.elemPtr(b.param(0), b.localGet(i)))))),
                b.localSet(i, b.binary(Op.ADD, b.localGet(i), b.constI32(1))));
        b.endFunction(b.block(b.localDecl(s, b.constF32(0f)), b.localDecl(i, b.constI32(0)), loop,
                b.returnStmt(b.localGet(s))));

        // f1: void fill(private int[] a, int base) - пишет в private-массив вызывающего
        int f1 = b.beginFunction(IrModule.DEVICE, Types.VOID, pi, Types.I32);
        b.nameFunction("fill");
        b.nameParams("a", "base");
        int i1 = b.declareLocal(Types.I32);
        b.nameLocal(i1, "i");
        int loop1 = b.loop(b.binary(Op.LT, b.localGet(i1), b.arrayLen(b.param(0))),
                b.block(b.store(b.elemPtr(b.param(0), b.localGet(i1)),
                        b.binary(Op.ADD, b.param(1), b.binary(Op.MUL, b.localGet(i1), b.localGet(i1))))),
                b.localSet(i1, b.binary(Op.ADD, b.localGet(i1), b.constI32(1))));
        b.endFunction(b.block(b.localDecl(i1, b.constI32(0)), loop1));

        // f2: bool isOdd(long x)
        int f2 = b.beginFunction(IrModule.DEVICE, Types.BOOL, Types.I64);
        b.nameFunction("isOdd");
        b.nameParams("x");
        b.endFunction(b.block(b.returnStmt(b.binary(Op.NE, b.binary(Op.AND, b.param(0), b.constI64(1)),
                b.constI64(0)))));

        // f3: float scaled(constant float[] c, int i)
        int f3 = b.beginFunction(IrModule.DEVICE, Types.F32, cf, Types.I32);
        b.nameFunction("scaled");
        b.nameParams("c", "i");
        b.endFunction(b.block(b.returnStmt(b.binary(Op.MUL, b.load(b.elemPtr(b.param(0), b.param(1))),
                b.constF32(2f)))));

        // f4: long widen(int x) - 64-битный результат, вызывает isOdd
        int f4 = b.beginFunction(IrModule.DEVICE, Types.I64, Types.I32);
        b.nameFunction("widen");
        b.nameParams("x");
        int w = b.convert(Types.I64, b.param(0));
        b.endFunction(b.block(b.returnStmt(b.select(b.call(f2, w), b.binary(Op.MUL, w, b.constI64(3)),
                b.binary(Op.SUB, w, b.constI64(1))))));

        // kernel
        b.beginFunction(IrModule.KERNEL, Types.VOID, gf, cf, gf, gi);
        b.nameFunction("device_functions");
        b.nameParams("data", "table", "out_f", "out_i");
        int priv = b.declareLocal(pi);
        b.nameLocal(priv, "scratch");
        int sum = b.declareLocal(Types.I32);
        b.nameLocal(sum, "sum");
        int j = b.declareLocal(Types.I32);
        b.nameLocal(j, "j");
        int sumLoop = b.loop(b.binary(Op.LT, b.localGet(j), b.arrayLen(b.localGet(priv))),
                b.block(b.localSet(sum, b.binary(Op.ADD, b.localGet(sum),
                        b.load(b.elemPtr(b.localGet(priv), b.localGet(j)))))),
                b.localSet(j, b.binary(Op.ADD, b.localGet(j), b.constI32(1))));
        b.endFunction(b.block(
                b.store(b.elemPtr(b.param(2), b.constI32(0)), b.call(f0, b.param(0))),
                b.store(b.elemPtr(b.param(2), b.constI32(1)), b.call(f3, b.param(1), b.constI32(2))),
                b.privateArrayDecl(priv, b.constI32(5)),
                b.exprStmt(b.call(f1, b.localGet(priv), b.constI32(7))),
                b.localDecl(sum, b.constI32(0)),
                b.localDecl(j, b.constI32(0)),
                sumLoop,
                b.store(b.elemPtr(b.param(3), b.constI32(0)), b.localGet(sum)),
                b.store(b.elemPtr(b.param(3), b.constI32(1)), b.arrayLen(b.param(3))),
                b.store(b.elemPtr(b.param(3), b.constI32(2)), b.arrayLen(b.localGet(priv))),
                b.store(b.elemPtr(b.param(3), b.constI32(3)),
                        b.convert(Types.I32, b.call(f4, b.load(b.elemPtr(b.param(3), b.constI32(0))))))));
        return b.build();
    }

    // ---------------------------------------------------------------------------------------------
    // константы
    // ---------------------------------------------------------------------------------------------

    /** Предельные значения всех типов, особые {@code float/double}. Результаты - {@code out_*[0..]}. */
    static IrModule constants() {
        K k = new K();
        int[] t = {Types.I8, Types.U8, Types.I16, Types.U16, Types.I32, Types.U32, Types.I64, Types.U64,
                Types.F32, Types.F64};
        int[] p = new int[t.length];
        for (int i = 0; i < t.length; i++) {
            p[i] = k.b.array(AddressSpace.GLOBAL, t[i]);
        }
        k.b.beginFunction(IrModule.KERNEL, Types.VOID, p);
        k.b.nameFunction("constants");
        String[] names = new String[t.length];
        for (int i = 0; i < t.length; i++) {
            names[i] = "out_" + name(t[i]);
        }
        k.b.nameParams(names);
        IrBuilder b = k.b;
        long[][] ints = {
                {Byte.MIN_VALUE, Byte.MAX_VALUE, -1, 0},
                {0, 127, 128, 255},
                {Short.MIN_VALUE, Short.MAX_VALUE, -1, 0},
                {0, 255, 32768, 65535},
                {Integer.MIN_VALUE, Integer.MAX_VALUE, -1, 0},
                {0, 1, 0x80000000L, 0xFFFFFFFFL},
                {Long.MIN_VALUE, Long.MAX_VALUE, -1, 0},
                {0, 1, Long.MIN_VALUE, -1L}};
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 4; j++) {
                k.put(i, j, b.constInt(t[i], ints[i][j]));
            }
        }
        float[] fs = {0.5f, -0.0f, 0.0f, 1.0e-5f, 3.4028235e38f, Float.MIN_VALUE, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NaN, Float.intBitsToFloat(0x7fc12345), 16777216f, -2.5f, 1.17549435e-38f};
        for (int j = 0; j < fs.length; j++) {
            k.put(8, j, b.constF32(fs[j]));
        }
        double[] ds = {0.5, -0.0, 0.0, 1.0e-5, 1.7976931348623157e308, Double.MIN_VALUE, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NaN, Double.longBitsToDouble(0x7ff8123456789abcL), 9007199254740992.0,
                -2.5, 2.2250738585072014e-308, 0.1};
        for (int j = 0; j < ds.length; j++) {
            k.put(9, j, b.constF64(ds[j]));
        }
        return k.finish();
    }

    // ---------------------------------------------------------------------------------------------
    // builtins
    // ---------------------------------------------------------------------------------------------

    /**
     * Все builtins, кроме atomics: идентификаторы по осям 0..3, размеры, математика на {@code F32/F64}, {@code max/min}
     * на всех арифметических типах, {@code assumeUniform}. Параметры: {@code in_f32}, {@code in_f64},
     * {@code in_i32} (по 3 значения), результаты - {@code out_i32}, {@code out_f32}, {@code out_f64}.
     */
    static IrModule builtins() {
        K k = new K();
        IrBuilder b = k.b;
        int af32 = b.array(AddressSpace.GLOBAL, Types.F32);
        int af64 = b.array(AddressSpace.GLOBAL, Types.F64);
        int ai32 = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, af32, af64, ai32, ai32, af32, af64);
        b.nameFunction("builtins");
        b.nameParams("in_f32", "in_f64", "in_i32", "out_i32", "out_f32", "out_f64");
        // линейный номер work-item'а: каждый пишет в свой непересекающийся участок выходных массивов
        int gid = Builtins.idOf("getGlobalId");
        int gsz = Builtins.idOf("getGlobalSize");
        int lin = b.binary(Op.ADD, b.intrinsic(gid, b.constI32(0)),
                b.binary(Op.MUL, b.intrinsic(gsz, b.constI32(0)),
                        b.binary(Op.ADD, b.intrinsic(gid, b.constI32(1)),
                                b.binary(Op.MUL, b.intrinsic(gsz, b.constI32(1)), b.intrinsic(gid, b.constI32(2))))));
        int item = k.local(Types.I32, "item", lin);
        int bi = k.local(Types.I32, "base_i", b.binary(Op.MUL, b.localGet(item), b.constI32(BUILTINS_I32_RESULTS)));
        int bf = k.local(Types.I32, "base_f", b.binary(Op.MUL, b.localGet(item), b.constI32(BUILTINS_FLOAT_RESULTS)));
        int idx = 0;
        String[] idFns = {"getGlobalId", "getLocalId", "getGroupId", "getGlobalSize", "getLocalSize", "getNumGroups"};
        for (String name : idFns) {
            for (int axis = 0; axis < 4; axis++) {
                putAt(k, 3, bi, idx++, b.intrinsic(Builtins.idOf(name), b.constI32(axis)));
            }
        }
        putAt(k, 3, bi, idx++, b.intrinsic(Builtins.idOf("getWorkDim")));
        // индекс оси - не константа
        putAt(k, 3, bi, idx++, b.intrinsic(Builtins.idOf("getGlobalId"), k.get(2, 0)));
        int fi = 0;
        for (String name : new String[]{"sin", "cos", "sqrt"}) {
            putAt(k, 4, bf, fi, b.intrinsic(Builtins.idOf(name), k.get(0, 0)));
            putAt(k, 5, bf, fi++, b.intrinsic(Builtins.idOf(name), k.get(1, 0)));
            putAt(k, 4, bf, fi, b.intrinsic(Builtins.idOf(name), k.get(0, 1)));
            putAt(k, 5, bf, fi++, b.intrinsic(Builtins.idOf(name), k.get(1, 1)));
        }
        int fma = Builtins.idOf("fma");
        putAt(k, 4, bf, fi, b.intrinsic(fma, k.get(0, 0), k.get(0, 1), k.get(0, 2)));
        putAt(k, 5, bf, fi++, b.intrinsic(fma, k.get(1, 0), k.get(1, 1), k.get(1, 2)));
        for (String name : new String[]{"max", "min"}) {
            int id = Builtins.idOf(name);
            for (int a = 0; a < 3; a++) {
                putAt(k, 4, bf, fi, b.intrinsic(id, k.get(0, a), k.get(0, (a + 1) % 3)));
                putAt(k, 5, bf, fi++, b.intrinsic(id, k.get(1, a), k.get(1, (a + 1) % 3)));
                putAt(k, 3, bi, idx++, b.intrinsic(id, k.get(2, a), k.get(2, (a + 1) % 3)));
                putAt(k, 3, bi, idx++, b.convert(Types.I32, b.intrinsic(id,
                        b.convert(Types.U32, k.get(2, a)), b.convert(Types.U32, k.get(2, (a + 1) % 3)))));
                putAt(k, 3, bi, idx++, b.convert(Types.I32, b.intrinsic(id,
                        b.convert(Types.I64, k.get(2, a)), b.convert(Types.I64, k.get(2, (a + 1) % 3)))));
                putAt(k, 3, bi, idx++, b.convert(Types.I32, b.intrinsic(id,
                        b.convert(Types.U64, k.get(2, a)), b.convert(Types.U64, k.get(2, (a + 1) % 3)))));
            }
        }
        putAt(k, 3, bi, idx++, b.intrinsic(Builtins.idOf("assumeUniform"), k.get(2, 0)));
        putAt(k, 4, bf, fi, b.intrinsic(Builtins.idOf("assumeUniform"), k.get(0, 0)));
        putAt(k, 5, bf, fi++, b.intrinsic(Builtins.idOf("assumeUniform"), k.get(1, 0)));
        return k.finish();
    }

    /** Запись {@code param[base + idx] = value} с базой в локали. */
    private static void putAt(K k, int param, int baseLocal, int idx, int value) {
        int offset = k.b.binary(Op.ADD, k.b.localGet(baseLocal), k.b.constI32(idx));
        k.st.add(k.b.store(k.b.elemPtr(k.b.param(param), offset), value));
    }

    /** Количество результатов {@link #builtins()} в {@code out_i32}. */
    static final int BUILTINS_I32_RESULTS = 6 * 4 + 2 + 2 * 3 * 4 + 1;
    /** Количество результатов {@link #builtins()} в {@code out_f32} и {@code out_f64}. */
    static final int BUILTINS_FLOAT_RESULTS = 6 + 1 + 6 + 1;

    /**
     * {@code atomicAdd} на {@code I32, U32, I64, U64}: каждый work-item прибавляет к счётчику свой номер.
     * Прежнее значение (порядок исполнения не определён) сверяется только по знаку: оно не меньше нуля.
     */
    static IrModule atomics() {
        K k = new K();
        IrBuilder b = k.b;
        int[] t = {Types.I32, Types.U32, Types.I64, Types.U64};
        int[] p = new int[t.length + 1];
        for (int i = 0; i < t.length; i++) {
            p[i] = b.array(AddressSpace.GLOBAL, t[i]);
        }
        p[t.length] = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, p);
        b.nameFunction("atomics");
        b.nameParams("c_i32", "c_u32", "c_i64", "c_u64", "olds");
        int atomic = Builtins.idOf("atomicAdd");
        int gid = k.local(Types.I32, "gid", b.intrinsic(Builtins.idOf("getGlobalId"), b.constI32(0)));
        for (int i = 0; i < t.length; i++) {
            k.st.add(b.exprStmt(b.intrinsic(atomic, k.at(i, 0), b.convert(t[i], b.localGet(gid)))));
        }
        int old = k.local(Types.I32, "old", b.intrinsic(atomic, k.at(0, 1), b.constI32(5)));
        k.st.add(b.store(b.elemPtr(b.param(t.length), b.localGet(gid)),
                b.select(b.binary(Op.GE, b.localGet(old), b.constI32(0)), b.constI32(1), b.constI32(0))));
        return k.finish();
    }

    /** Ядро с обязательным размером work-group и device-функцией с {@code fallback}-соседом. */
    static IrModule workGroupAttribute() {
        K k = new K();
        int a = k.b.array(AddressSpace.GLOBAL, Types.F32);
        k.b.beginFunction(IrModule.KERNEL, Types.VOID, a);
        k.b.requireWorkGroup(64, 2, 1);
        k.b.nameFunction("reqd_wg");
        k.b.nameParams("data");
        int g = k.local(Types.I32, "g", k.b.intrinsic(Builtins.idOf("getGlobalId"), k.b.constI32(0)));
        k.st.add(k.b.store(k.b.elemPtr(k.b.param(0), k.b.localGet(g)), k.b.constF32(1.5f)));
        return k.finish();
    }
}
