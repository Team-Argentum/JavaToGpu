package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongUnaryOperator;

/**
 * Анализ полного покрытия записью: какие параметры-массивы ядро гарантированно перезаписывает целиком.
 *
 * <p>Нужен рантайму, чтобы не загружать на устройство массив, который ядро только пишет. Загрузка такого массива нужна
 * лишь затем, чтобы элементы, которые ядро не тронуло, сохранили значения хоста. Если каждый элемент перезаписывается,
 * загрузка - пустая трата шины: для выходного массива на миллионы элементов это половина времени запуска.</p>
 *
 * <p><b>Распознаваемый шаблон.</b> Стейтмент верхнего уровня тела {@code a[idx] = v}, перед которым на верхнем уровне нет
 * стейтментов с {@code return}: такой стейтмент выполняется каждым work-item'ом. Индекс - линейная форма по осям
 * запуска:</p>
 * <pre>
 *   idx = g0 + g1 * S1 + g2 * S2        (gK = getGlobalId(K); слагаемые для осей 1, 2 необязательны)
 * </pre>
 * <p>где шаг {@code SK} - произведение множителей, известных до запуска: целых констант, {@code getGlobalSize(j)} и
 * скалярных {@code int}-параметров. Сложение и умножение раскрываются как многочлен (порядок слагаемых и множителей
 * любой, {@code (g2 * H + g1) * W + g0} тоже подходит), промежуточные значения могут лежать в локалях, которым присвоено
 * ровно один раз - объявлением на верхнем уровне - и чей адрес не берётся.</p>
 *
 * <p>Сам анализ не знает размеров, поэтому возвращает форму индекса ({@link Coverage}), а условие проверяет рантайм перед
 * запуском ({@link Coverage#covers}): используются оси {@code 0..d-1} подряд, шаг оси {@code K} равен
 * {@code G0 * ... * G(K-1)} и длина массива равна {@code G0 * ... * G(d-1)}. Тогда отображение work-item'ов осей
 * {@code 0..d-1} в индексы - биекция на {@code [0, length)}: каждый элемент записан (прочие оси лишь повторяют запись).
 * Анализ консервативен: всё, что не подходит под шаблон, считается непокрытым.</p>
 *
 * <p>Пользоваться результатом можно только вместе с проверкой рантайма, что параметр ядром не читается
 * ({@code Validator.ACCESS_READ}).</p>
 */
public final class WriteCoverage {

    private static final int GET_GLOBAL_ID = Builtins.idOf("getGlobalId");
    private static final int GET_GLOBAL_SIZE = Builtins.idOf("getGlobalSize");
    /** Число осей запуска. */
    private static final int AXES = 3;

    private WriteCoverage() {
    }

    /**
     * Множитель шага, известный до запуска.
     *
     * @param kind  вид
     * @param value константа ({@link Kind#CONST}), ось ({@link Kind#GLOBAL_SIZE}) либо индекс параметра
     *              ({@link Kind#PARAM})
     */
    public record Factor(Kind kind, long value) {

        /** Вид множителя. */
        public enum Kind {
            /** Целая константа. */
            CONST,
            /** {@code getGlobalSize(value)}. */
            GLOBAL_SIZE,
            /** Скалярный {@code int}-параметр ядра с индексом {@code value}. */
            PARAM
        }
    }

    /**
     * Форма индекса записи, покрывающей массив: {@code idx = sum(gK * strides[K])} по осям {@code 0..axes-1}.
     *
     * @param axes    число осей в индексе (1..3)
     * @param strides по оси - произведение множителей шага (у оси 0 обычно пустое, то есть 1)
     */
    public record Coverage(int axes, List<List<Factor>> strides) {

        /** Покрытие {@code a[getGlobalId(0)]}. */
        public static final Coverage LINEAR = new Coverage(1, List.of(List.of()));

        public Coverage {
            strides = strides.stream().map(List::copyOf).toList();
        }

        /**
         * Покрывает ли запись каждый элемент массива при данном запуске.
         *
         * @param length длина массива
         * @param global размеры запуска по осям 0..2
         * @param param  значение скалярного параметра по индексу либо {@code Long.MIN_VALUE}, если неизвестно
         * @return {@code true}, если каждый элемент {@code [0, length)} записан
         */
        public boolean covers(long length, long[] global, LongUnaryOperator param) {
            try {
                long expected = 1;
                for (int k = 0; k < axes; k++) {
                    long stride = 1;
                    for (Factor f : strides.get(k)) {
                        long v = switch (f.kind()) {
                            case CONST -> f.value();
                            case GLOBAL_SIZE -> global[(int) f.value()];
                            case PARAM -> param.applyAsLong((int) f.value());
                        };
                        if (v <= 0) {
                            return false; // неизвестный параметр, ноль или отрицательный шаг
                        }
                        stride = Math.multiplyExact(stride, v);
                    }
                    if (stride != expected || global[k] <= 0) {
                        return false;
                    }
                    expected = Math.multiplyExact(expected, global[k]);
                }
                return expected == length;
            } catch (ArithmeticException overflow) {
                return false;
            }
        }
    }

    /**
     * @param m      корректный модуль
     * @param kernel индекс ядра
     * @return по параметру: {@code true}, если ядро записывает каждый его элемент при подходящем запуске
     *         ({@link #coverage})
     */
    public static boolean[] fullyWrittenParams(IrModule m, int kernel) {
        Coverage[] c = coverage(m, kernel);
        boolean[] out = new boolean[c.length];
        for (int i = 0; i < c.length; i++) {
            out[i] = c[i] != null;
        }
        return out;
    }

    /**
     * @param m      корректный модуль
     * @param kernel индекс ядра
     * @return по параметру: форма индекса покрывающей записи либо {@code null}, если покрытие не доказано
     */
    public static Coverage[] coverage(IrModule m, int kernel) {
        Coverage[] out = new Coverage[m.fParamCount(kernel)];
        Codec.ModuleTree mt;
        try {
            mt = Codec.lift(m);
        } catch (UnsupportedOperationException e) {
            return out;
        }
        FnTree fn = mt.fns[kernel];
        if (fn.kind != IrModule.KERNEL) {
            return out;
        }
        Scan scan = new Scan(mt.source, fn);
        for (Tree s : fn.body.kids) {
            if (s.op == Op.LOCAL_DECL && s.kids.length == 1 && scan.writes[s.imm] == 1 && scan.tracked[s.imm]) {
                // значение локали неизменно после объявления: его форма подставляется при чтении
                scan.defs.put(s.imm, s.kids[0]);
            }
            if (s.op == Op.STORE && s.kids[0].op == Op.ELEM_PTR) {
                Tree ptr = s.kids[0];
                if (ptr.kids[0].op == Op.PARAM && out[ptr.kids[0].imm] == null) {
                    out[ptr.kids[0].imm] = scan.coverageOf(ptr.kids[1]);
                }
            }
            if (Costs.contains(s, Op.RETURN)) {
                // после возможного выхода следующие стейтменты выполняются не каждым work-item'ом
                break;
            }
        }
        return out;
    }

    /** Состояние разбора индексов одного ядра. */
    private static final class Scan {
        final int[] writes;
        final boolean[] tracked;
        /** Локали с единственным присваиванием, объявленные выше текущего стейтмента. */
        final Map<Integer, Tree> defs = new HashMap<>();

        Scan(IrModule m, FnTree fn) {
            this.writes = new int[fn.localCount];
            countWrites(fn.body, writes);
            this.tracked = Locals.tracked(fn, m);
        }

        /** Многочлен индекса: слагаемое на ось (множители шага) либо {@code null}, если форма не линейная. */
        Coverage coverageOf(Tree idx) {
            List<Factor>[] p = poly(idx, 0);
            if (p == null) {
                return null;
            }
            int axes = 0;
            while (axes < AXES && p[axes] != null) {
                axes++;
            }
            if (axes == 0) {
                return null;
            }
            for (int k = axes; k < AXES; k++) {
                if (p[k] != null) {
                    return null; // оси не подряд: g0 + g2 * S
                }
            }
            for (Factor f : p[0]) {
                if (f.kind() != Factor.Kind.CONST || f.value() != 1) {
                    return null; // шаг оси 0 обязан быть 1: g0 * 4 покрывает лишь каждый четвёртый элемент
                }
            }
            return new Coverage(axes, Arrays.asList(p).subList(0, axes));
        }

        /** Индекс как сумма {@code gK * шаг}; ось не больше одного раза. */
        @SuppressWarnings({"unchecked", "rawtypes"})
        private List<Factor>[] poly(Tree t, int depth) {
            if (depth > 32 || t.type != Types.I32) {
                return null;
            }
            if (t.op == Op.LOCAL_GET) {
                Tree def = defs.get(t.imm);
                return def == null ? null : poly(def, depth + 1);
            }
            int axis = axisOf(t, GET_GLOBAL_ID);
            if (axis >= 0) {
                List<Factor>[] p = new List[AXES];
                p[axis] = List.of();
                return p;
            }
            if (t.op == Op.ADD) {
                List<Factor>[] a = poly(t.kids[0], depth + 1);
                List<Factor>[] b = a == null ? null : poly(t.kids[1], depth + 1);
                if (b == null) {
                    return null;
                }
                for (int k = 0; k < AXES; k++) {
                    if (b[k] != null) {
                        if (a[k] != null) {
                            return null;
                        }
                        a[k] = b[k];
                    }
                }
                return a;
            }
            if (t.op == Op.MUL) {
                for (int side = 0; side < 2; side++) {
                    List<Factor> f = product(t.kids[1 - side], depth + 1);
                    if (f == null) {
                        continue;
                    }
                    List<Factor>[] p = poly(t.kids[side], depth + 1);
                    if (p == null) {
                        return null;
                    }
                    for (int k = 0; k < AXES; k++) {
                        if (p[k] != null) {
                            List<Factor> joined = new ArrayList<>(p[k]);
                            joined.addAll(f);
                            p[k] = joined;
                        }
                    }
                    return p;
                }
            }
            return null;
        }

        /** Произведение множителей, известных до запуска, либо {@code null}. */
        private List<Factor> product(Tree t, int depth) {
            if (depth > 32 || t.type != Types.I32) {
                return null;
            }
            if (t.op == Op.LOCAL_GET) {
                Tree def = defs.get(t.imm);
                return def == null ? null : product(def, depth + 1);
            }
            if (t.op == Op.CONST_INT) {
                return List.of(new Factor(Factor.Kind.CONST, t.k));
            }
            if (t.op == Op.PARAM) {
                return List.of(new Factor(Factor.Kind.PARAM, t.imm));
            }
            int axis = axisOf(t, GET_GLOBAL_SIZE);
            if (axis >= 0) {
                return List.of(new Factor(Factor.Kind.GLOBAL_SIZE, axis));
            }
            if (t.op == Op.MUL) {
                List<Factor> a = product(t.kids[0], depth + 1);
                List<Factor> b = a == null ? null : product(t.kids[1], depth + 1);
                if (b == null) {
                    return null;
                }
                List<Factor> out = new ArrayList<>(a);
                out.addAll(b);
                return out;
            }
            return null;
        }
    }

    /** Ось вызова {@code builtin(const)} либо -1. */
    private static int axisOf(Tree e, int builtin) {
        if (e.op == Op.INTRINSIC && e.imm == builtin && e.kids.length == 1 && e.kids[0].op == Op.CONST_INT
                && e.kids[0].k >= 0 && e.kids[0].k < AXES) {
            return (int) e.kids[0].k;
        }
        return -1;
    }

    private static void countWrites(Tree t, int[] writes) {
        if (t.op == Op.LOCAL_DECL || t.op == Op.LOCAL_SET) {
            writes[t.imm]++;
        }
        for (Tree c : t.kids) {
            countWrites(c, writes);
        }
    }
}
