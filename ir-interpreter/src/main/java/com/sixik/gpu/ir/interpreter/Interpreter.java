package com.sixik.gpu.ir.interpreter;

import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Эталонный CPU-интерпретатор IR (REWORK_ARCHITECTURE.md, 5.16): исполняет ядро по виртуальному NDRange 1D/2D/3D
 * с семантикой, которую IR гарантирует всем бэкендам.
 *
 * <h2>Семантика</h2>
 * <ul>
 *   <li>целые - Java: знаковые {@code add/sub/mul/neg} оборачиваются, счётчик сдвига маскируется ({@code n & 31},
 *       {@code n & 63}), беззнаковые делят и сравниваются как беззнаковые;</li>
 *   <li>вещественные - IEEE 754 как Java {@code float/double}; {@code convert} вещественного в целое насыщается к
 *       {@code MIN/MAX}, {@code NaN -> 0};</li>
 *   <li>{@code select} ленив, {@code &&/||} (через {@code select}) не вычисляют лишнего;</li>
 *   <li>builtins - по таблице {@code builtins.def}: индексы work-item'а, {@code sin/cos} ({@link StrictMath}),
 *       {@code sqrt}, {@code fma}, {@code max/min} (семантика {@link Math#max}), {@code atomicAdd};</li>
 *   <li>work-item'ы исполняются последовательно в порядке {@code z, y, x}: для ядер без барьеров результат от порядка
 *       не зависит (барьеры и local memory - веха M10).</li>
 * </ul>
 *
 * <h2>Ошибки</h2>
 * <p>Метод {@link #run} не бросает исключений на данных, а возвращает {@link Result} с диагностиками:</p>
 * <ul>
 *   <li>{@code IR-*} - модуль не прошёл валидатор;</li>
 *   <li>{@code IN-A01..A03} - аргументы не соответствуют сигнатуре ядра;</li>
 *   <li>{@code IN-L01} - NDRange не согласован с ядром ({@code reqd_work_group_size}, делимость);</li>
 *   <li>{@code IN-U01} - в достижимом коде есть узлы, которые интерпретатор ещё не исполняет (M8-M10);</li>
 *   <li>{@code IN-R01..R04} - нарушения при исполнении: выход за границы, деление на ноль (в IR это
 *       «значение не определено», интерпретатор строже и сообщает о нём), исчерпанный бюджет шагов, плохая длина
 *       private-массива. Хостовые массивы при этом <b>не</b> изменяются.</li>
 * </ul>
 */
public final class Interpreter {

    private Interpreter() {
    }

    /**
     * Параметры запуска.
     *
     * @param maxSteps бюджет исполненных стейтментов на весь запуск; {@code 0} - без ограничения. Защищает тесты
     *                 от бесконечных циклов (диагностика {@code IN-R03}).
     */
    public record Options(long maxSteps) {
        /** Без ограничения шагов. */
        public static final Options DEFAULT = new Options(0);
    }

    /**
     * Итог успешного запуска.
     *
     * @param workItems число исполненных work-item'ов
     * @param steps     число исполненных стейтментов (для диагностики и оценки стоимости теста)
     */
    public record Execution(long workItems, long steps) {
    }

    /**
     * Исполняет последнюю функцию модуля (по соглашению фронтенда это ядро-точка входа) без ограничения шагов.
     *
     * @param m     модуль
     * @param range виртуальный NDRange
     * @param args  значения параметров ядра по порядку (см. таблицу соответствия в {@link Host}); массивы
     *              изменяются на месте так же, как при вызове Java-метода
     * @return итог либо диагностики
     */
    public static Result<Execution> run(IrModule m, NdRange range, Object... args) {
        return run(m, m.functionCount() - 1, range, Options.DEFAULT, args);
    }

    /**
     * Исполняет заданную функцию-ядро.
     *
     * @param m        модуль
     * @param function индекс ядра (например, {@code fallback}-варианта)
     * @param range    виртуальный NDRange
     * @param options  параметры запуска
     * @param args     значения параметров ядра по порядку
     * @return итог либо диагностики; при успехе в диагностиках могут быть предупреждения валидатора
     */
    public static Result<Execution> run(IrModule m, int function, NdRange range, Options options, Object[] args) {
        Validator.Report report = Validator.validate(m);
        if (report.hasErrors()) {
            return Result.failed(report.diagnostics());
        }
        if (function < 0 || function >= m.functionCount() || m.fKind(function) != IrModule.KERNEL) {
            return fail("IN-A00", function, "функция " + function + " не является ядром модуля");
        }
        List<Diagnostic> unsupported = Support.check(m, function);
        if (!unsupported.isEmpty()) {
            return Result.failed(unsupported);
        }

        int[] local = new int[3];
        List<Diagnostic> launch = resolveLaunch(m, function, range, local);
        if (!launch.isEmpty()) {
            return Result.failed(launch);
        }

        int params = m.fParamCount(function);
        if (args.length != params) {
            return fail("IN-A01", function, "ядро принимает " + params + " аргументов, передано " + args.length);
        }
        long[] argv = new long[params];
        long[][] hostBufs = new long[params][];
        Object[] hostObjects = new Object[params];
        int hostCount = bindArgs(m, function, args, argv, hostBufs, hostObjects);
        if (hostCount < 0) {
            return Result.failed(argumentErrors(m, function, args));
        }

        int[] global = new int[]{range.global(0), range.global(1), range.global(2)};
        Machine machine = new Machine(m, Arrays.copyOf(hostBufs, hostCount), range.dim(), global, local,
                options.maxSteps());
        Diagnostic fault = machine.run(function, argv);
        if (fault != null) {
            return Result.failed(List.of(fault));
        }

        // успех: записываем массивы обратно (одинаковый массив, переданный дважды, - один буфер)
        for (int b = 0; b < hostCount; b++) {
            Host.fromBuffer(hostBufs[b], hostObjects[b]);
        }
        List<Diagnostic> warnings = new ArrayList<>(report.diagnostics());
        return new Result<>(new Execution(range.workItems(), machine.steps()), warnings);
    }

    private static Result<Execution> fail(String code, int function, String message) {
        return Result.failed(List.of(new Diagnostic(code, Diagnostic.Severity.ERROR, function, -1, message)));
    }

    /**
     * Согласует NDRange с ядром и вычисляет итоговые размеры work-group.
     *
     * @param local выход: размеры work-group по трём осям
     * @return ошибки {@code IN-L01}; пусто при успехе
     */
    private static List<Diagnostic> resolveLaunch(IrModule m, int f, NdRange range, int[] local) {
        List<Diagnostic> out = new ArrayList<>();
        for (int d = 0; d < 3; d++) {
            int req = m.fReqWorkGroup(f, d);
            int given = range.local(d);
            if (req > 0) {
                if (given != 0 && given != req) {
                    out.add(launchError(f, "ось " + d + ": задан размер группы " + given
                            + ", а ядро требует reqd_work_group_size " + req));
                }
                local[d] = req;
            } else {
                local[d] = given != 0 ? given : 1;
            }
            if (d >= range.dim() && local[d] != 1) {
                out.add(launchError(f, "ось " + d + " вне размерности запуска (" + range.dim()
                        + "D), но размер группы " + local[d]));
            } else if (range.global(d) % local[d] != 0) {
                out.add(launchError(f, "ось " + d + ": размер NDRange " + range.global(d)
                        + " не делится на размер группы " + local[d]));
            }
        }
        return out;
    }

    private static Diagnostic launchError(int f, String message) {
        return new Diagnostic("IN-L01", Diagnostic.Severity.ERROR, f, -1, message);
    }

    /**
     * Переводит аргументы в каноническое представление: скаляры - в {@code argv}, массивы - в буферы
     * ({@code argv} хранит номер буфера). Один и тот же массив, переданный несколько раз, получает один буфер,
     * как в JVM, где обе ссылки указывают на один объект.
     *
     * @param hostBufs    выход: буферы по номерам
     * @param hostObjects выход: исходные хостовые массивы по номерам буферов
     * @return число уникальных хостовых буферов либо {@code -1}, если какой-либо аргумент не подошёл
     */
    private static int bindArgs(IrModule m, int f, Object[] args, long[] argv, long[][] hostBufs, Object[] hostObjects) {
        int count = 0;
        for (int i = 0; i < args.length; i++) {
            int t = m.fParamType(f, i);
            Object a = args[i];
            if (m.typeKind(t) == Types.KIND_ARRAY) {
                int elem = m.typeB(t);
                if (a == null || !Host.arrayMatches(elem, a)) {
                    return -1;
                }
                int buffer = -1;
                for (int b = 0; b < count; b++) {
                    if (hostObjects[b] == a) {
                        buffer = b;
                        break;
                    }
                }
                if (buffer < 0) {
                    buffer = count++;
                    hostBufs[buffer] = Host.toBuffer(elem, a);
                    hostObjects[buffer] = a;
                }
                argv[i] = buffer;
            } else if (Types.isScalar(t)) {
                if (a == null || !Host.scalarMatches(t, a)) {
                    return -1;
                }
                argv[i] = Host.toScalar(t, a);
            } else {
                return -1;
            }
        }
        return count;
    }

    /** Подробный список несоответствий аргументов сигнатуре (вызывается после неудачной привязки). */
    private static List<Diagnostic> argumentErrors(IrModule m, int f, Object[] args) {
        List<Diagnostic> out = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            int t = m.fParamType(f, i);
            Object a = args[i];
            String have = a == null ? "null" : a.getClass().getSimpleName();
            if (m.typeKind(t) == Types.KIND_ARRAY) {
                if (a == null || !Host.arrayMatches(m.typeB(t), a)) {
                    out.add(argError(f, i, "ожидается " + Host.arrayName(m.typeB(t)) + ", передано " + have));
                }
            } else if (Types.isScalar(t)) {
                if (a == null || !Host.scalarMatches(t, a)) {
                    out.add(argError(f, i, "ожидается " + Host.scalarName(t) + ", передано " + have));
                }
            } else {
                out.add(argError(f, i, "параметры этого вида (pointer/vector/struct) интерпретатор пока не принимает"));
            }
        }
        return out;
    }

    private static Diagnostic argError(int f, int index, String message) {
        return new Diagnostic("IN-A02", Diagnostic.Severity.ERROR, f, -1, "аргумент " + index + ": " + message);
    }
}
