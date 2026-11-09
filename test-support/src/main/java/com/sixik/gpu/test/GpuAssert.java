package com.sixik.gpu.test;

import com.sixik.gpu.api.WorkItem;
import com.sixik.gpu.api.WorkItemContext;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Operands;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.runtime.CompiledKernel;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.GpuException;
import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.runtime.LaunchStats;
import com.sixik.gpu.test.KernelSample.Leg;
import org.opentest4j.AssertionFailedError;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Главное утверждение тестовой библиотеки: <b>одно и то же ядро даёт один и тот же результат</b> на JVM, в
 * IR-интерпретаторе и на устройстве (REWORK_ARCHITECTURE.md, 9.6).
 *
 * <pre>{@code
 * GpuAssert.sameResult(Kernels.class, "scale", in, out, 2f);          // размер запуска - длина первого массива
 * GpuAssert.sameResult(Kernels.class, "scale", KernelSample.named("big")
 *         .args(() -> new Object[]{data(), new float[N], 2f}).range(Range.of1D(N, 64)).build());
 * }</pre>
 *
 * <h2>Ветви</h2>
 * <ol>
 *   <li><b>JVM</b> - {@code <имя>$jvm}, если класс подменён weaver-ом, иначе сам метод; work-item-ы идут по очереди.
 *       Ядро с {@code barrier} или {@code @Local} на JVM не исполнить: ветвь пропускается, эталоном служит интерпретатор
 *       (и {@link KernelSample.Builder#expected ожидаемое}, если задано);</li>
 *   <li><b>INTERPRETER</b> - эталонный интерпретатор по IR из ресурсов класса ({@code JavaToGpu.loadModule});</li>
 *   <li><b>DEVICE</b> - реальное устройство; нет устройства или оно не поддерживает нужные возможности - ветвь
 *       пропускается и это видно в {@link Report}.</li>
 * </ol>
 *
 * <h2>Правила сравнения</h2>
 * <p>Эталон - JVM (или интерпретатор, если JVM пропущена); с {@code expected} эталон сверяется с ним тоже. Целые и логика
 * сравниваются точно. Вещественные: интерпретатор против эталона - в {@code ulps} образца (по умолчанию 0: те же
 * алгоритмы, побитово); устройство против эталона - в {@code ulps} образца либо в эвристическом допуске из IR: наибольший
 * ULP-допуск достижимых builtins для фактического типа результата (таблица 8.3) и 3 ULP, если есть вещественное деление
 * или остаток. Это допуск отдельных операций, а не доказанная граница ошибки всего ядра: накопление ошибки и вычитание
 * близких значений могут увеличить расстояние результата. Для таких образцов задавайте обоснованный
 * {@link KernelSample.Builder#ulps(int) явный допуск}. Исключение JVM (выход за границы, деление на ноль) означает, что интерпретатор <b>обязан</b> отказать;
 * устройство тогда не запускается - поведение не определено.</p>
 */
public final class GpuAssert {

    /** Допуск вещественного деления и остатка на устройстве, ULP (для OpenCL C деление без {@code -cl-fp32-correctly-rounded-divide-sqrt}). */
    static final int DEVICE_DIVISION_ULPS = 3;
    /** Бюджет шагов интерпретатора: зациклившееся ядро должно падать с {@code IN-R03}, а не вешать тест. */
    private static final Interpreter.Options BUDGET = new Interpreter.Options(200_000_000L);

    private GpuAssert() {
    }

    /**
     * Итог сравнения.
     *
     * @param kernel   {@code Класс.метод}
     * @param ran      ветви, которые выполнялись и совпали
     * @param skipped  пропущенные ветви и причина
     * @param jvmThrew JVM бросила исключение, а интерпретатор отказал, как и обязан
     * @param device   имя устройства, если ветвь DEVICE выполнялась, иначе {@code null}
     * @param ulps     допуск вещественных между устройством и эталоном
     */
    public record Report(String kernel, List<Leg> ran, Map<Leg, String> skipped, boolean jvmThrew, String device, int ulps) {

        /** @return {@code true}, если ветвь выполнялась */
        public boolean ranOn(Leg leg) {
            return ran.contains(leg);
        }
    }

    /**
     * Сравнивает ветви на заданных аргументах; размер запуска - 1D по длине первого массива. Аргументы копируются для
     * каждой ветви.
     *
     * @param owner  класс с ядром
     * @param kernel имя ядра
     * @param args   аргументы ядра
     * @return итог
     * @throws AssertionError если ветви разошлись
     */
    public static Report sameResult(Class<?> owner, String kernel, Object... args) {
        return sameResult(owner, kernel, KernelSample.of(kernel, args));
    }

    /**
     * Сравнивает ветви на образце; устройство - контекст по умолчанию {@code JavaToGpu}, если он есть.
     *
     * @param owner  класс с ядром
     * @param kernel имя ядра
     * @param sample образец
     * @return итог
     * @throws AssertionError если ветви разошлись
     */
    public static Report sameResult(Class<?> owner, String kernel, KernelSample sample) {
        GpuContext ctx = sample.legs().contains(Leg.DEVICE) ? JavaToGpu.defaultContext().orElse(null) : null;
        return sameResult(owner, kernel, sample, ctx);
    }

    /**
     * Сравнивает ветви на образце с заданным контекстом устройства.
     *
     * @param owner  класс с ядром
     * @param kernel имя ядра
     * @param sample образец
     * @param device контекст для ветви DEVICE либо {@code null} - ветвь пропускается
     * @return итог
     * @throws AssertionError если ветви разошлись или ветвь завершилась ошибкой
     */
    public static Report sameResult(Class<?> owner, String kernel, KernelSample sample, GpuContext device) {
        String title = owner.getSimpleName() + "." + kernel + " [" + sample.name() + "]";
        IrModule module;
        try {
            module = JavaToGpu.loadModule(owner, kernel);
        } catch (GpuException e) {
            throw new AssertionFailedError(title + ": kernel IR not found: " + e.getMessage()
                    + " (was the class built without the frontend-java annotation processor?)");
        }
        int fn = module.functionCount() - 1;
        boolean groupSync = Validator.validate(module).functions()[fn].usesGroupSync();
        Object[] first = sample.args();
        KernelSample.Range range = sample.rangeFor(first);
        Object[] expected = sample.expected();
        int deviceUlps = sample.ulps() >= 0 ? sample.ulps() : autoUlps(module);
        int interpreterUlps = Math.max(0, sample.ulps());

        List<Leg> ran = new ArrayList<>();
        Map<Leg, String> skipped = new EnumMap<>(Leg.class);
        Map<Leg, Object[]> results = new EnumMap<>(Leg.class);
        boolean jvmThrew = false;

        // ---- JVM ------------------------------------------------------------------------------
        Throwable jvmFailure = null;
        if (!sample.legs().contains(Leg.JVM)) {
            skipped.put(Leg.JVM, "excluded by the sample");
        } else if (groupSync) {
            skipped.put(Leg.JVM, "the kernel uses barrier or local memory: on the JVM work-items run one after another");
        } else {
            Object[] args = sample.args();
            jvmFailure = runOnJvm(owner, kernel, module, range, args);
            if (jvmFailure == null) {
                results.put(Leg.JVM, args);
                ran.add(Leg.JVM);
            } else {
                jvmThrew = true;
            }
        }

        // ---- интерпретатор ----------------------------------------------------------------------
        if (!sample.legs().contains(Leg.INTERPRETER)) {
            skipped.put(Leg.INTERPRETER, "excluded by the sample");
        } else {
            Object[] args = sample.args();
            Result<Interpreter.Execution> r = Interpreter.run(module, fn, range.toNdRange(), BUDGET, args);
            if (jvmFailure != null) {
                if (r.isOk()) {
                    throw new AssertionFailedError(title + ": the JVM threw " + describe(jvmFailure)
                            + ", but the interpreter finished successfully");
                }
                skipped.put(Leg.DEVICE, "the JVM threw an exception, device behavior is undefined");
                ran.add(Leg.INTERPRETER);
                return new Report(owner.getSimpleName() + "." + kernel, List.copyOf(ran), skipped, true, null, deviceUlps);
            }
            if (!r.isOk()) {
                throw new AssertionFailedError(title + ": interpreter refused: " + diagnostics(r.diagnostics()));
            }
            results.put(Leg.INTERPRETER, args);
            ran.add(Leg.INTERPRETER);
        }
        if (jvmFailure != null) {
            throw new AssertionFailedError(title + ": the JVM threw " + describe(jvmFailure)
                    + ", and the interpreter refusal cannot be checked: the INTERPRETER branch is excluded");
        }

        // ---- устройство -------------------------------------------------------------------------
        String deviceName = null;
        if (!sample.legs().contains(Leg.DEVICE)) {
            skipped.put(Leg.DEVICE, "excluded by the sample");
        } else if (device == null) {
            skipped.put(Leg.DEVICE, "no device");
        } else {
            Result<CompiledKernel> compiled = device.compile(module);
            if (!compiled.isOk()) {
                if (compiled.diagnostics().stream().anyMatch(d -> d.code().equals("RT-D02"))) {
                    skipped.put(Leg.DEVICE, "the device does not support the required features: " + diagnostics(compiled.diagnostics()));
                } else {
                    throw new AssertionFailedError(title + ": compilation for the device failed: "
                            + diagnostics(compiled.diagnostics()));
                }
            } else {
                if (sample.ulps() < 0) {
                    deviceUlps = autoUlps(module, compiled.value().function());
                }
                Object[] args = sample.args();
                Result<LaunchStats> r = device.launch(compiled.value(), range.toLaunchConfig(), args);
                if (!r.isOk()) {
                    throw new AssertionFailedError(title + ": launch on the device failed: " + diagnostics(r.diagnostics()));
                }
                results.put(Leg.DEVICE, args);
                ran.add(Leg.DEVICE);
                deviceName = device.device().describe();
            }
        }

        // ---- сравнение --------------------------------------------------------------------------
        Leg reference = results.containsKey(Leg.JVM) ? Leg.JVM : Leg.INTERPRETER;
        Object[] ref = results.get(reference);
        if (expected != null) {
            compare(title, "expected", reference.name(), expected, ref, 0, sample, true);
        }
        if (reference != Leg.INTERPRETER && results.containsKey(Leg.INTERPRETER)) {
            compare(title, reference.name(), "INTERPRETER", ref, results.get(Leg.INTERPRETER), interpreterUlps, sample, false);
        }
        if (results.containsKey(Leg.DEVICE)) {
            compare(title, reference.name(), "DEVICE", ref, results.get(Leg.DEVICE), deviceUlps, sample, false);
        }
        return new Report(owner.getSimpleName() + "." + kernel, List.copyOf(ran), skipped, false, deviceName, deviceUlps);
    }

    // =============================================================================================
    // ветви и сравнение
    // =============================================================================================

    private static void compare(String title, String expectedName, String actualName, Object[] expected, Object[] actual,
                                int ulps, KernelSample sample, boolean skipNullExpected) {
        for (int i = 0; i < Math.min(expected.length, actual.length); i++) {
            if (sample.ignored().contains(i) || (skipNullExpected && expected[i] == null)) {
                continue;
            }
            String why = Compare.difference(expected[i], actual[i], ulps);
            if (why != null) {
                throw new AssertionFailedError(title + ": argument " + i + " " + why + " [" + expectedName + " versus "
                        + actualName + ", tolerance " + ulps + " ULP]");
            }
        }
    }

    /** @return {@code null} при успехе либо исключение, брошенное ядром */
    private static Throwable runOnJvm(Class<?> owner, String kernel, IrModule module, KernelSample.Range range, Object[] args) {
        Method method = jvmMethod(owner, kernel, args);
        int fn = module.functionCount() - 1;
        int[] global = new int[3];
        int[] local = new int[3];
        for (int d = 0; d < 3; d++) {
            global[d] = Math.toIntExact(range.global(d));
            int req = module.fReqWorkGroup(fn, d);
            local[d] = d >= range.dim() ? 1 : (req > 0 ? req : (range.local(d) != 0 ? Math.toIntExact(range.local(d)) : 1));
        }
        WorkItemContext ctx = new WorkItemContext(range.dim(), global, local);
        Throwable[] failure = new Throwable[1];
        WorkItem.run(ctx, () -> {
            try {
                for (int z = 0; z < global[2]; z++) {
                    for (int y = 0; y < global[1]; y++) {
                        for (int x = 0; x < global[0]; x++) {
                            ctx.setGlobalId(x, y, z);
                            method.invoke(null, args);
                        }
                    }
                }
            } catch (InvocationTargetException e) {
                failure[0] = e.getCause() == null ? e : e.getCause();
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        });
        return failure[0];
    }

    /** {@code <имя>$jvm}, если класс подменён weaver-ом (тело ядра там уходит на устройство), иначе сам метод. */
    private static Method jvmMethod(Class<?> owner, String kernel, Object[] args) {
        Method original = null;
        Method body = null;
        for (Method m : owner.getDeclaredMethods()) {
            if (m.getName().equals(kernel + "$jvm")) {
                body = m;
            } else if (m.getName().equals(kernel)) {
                if (original != null) {
                    throw new IllegalArgumentException("the kernel has " + kernel + " has several methods: kernel overloads are not supported");
                }
                original = m;
            }
        }
        Method chosen = body != null ? body : original;
        if (chosen == null) {
            throw new IllegalArgumentException("in " + owner.getName() + " has no method " + kernel);
        }
        if (chosen.getParameterCount() != args.length) {
            throw new IllegalArgumentException("kernel " + kernel + " takes " + chosen.getParameterCount()
                    + " arguments, the sample gives " + args.length);
        }
        chosen.setAccessible(true);
        return chosen;
    }

    /**
     * Выбирает эвристический допуск последнего ядра модуля и достижимых device-функций.
     * Это максимум допусков отдельных операций, а не оценка распространения ошибки по программе.
     *
     * @param m модуль ядра
     * @return допуск в ULP, не меньше 0
     */
    static int autoUlps(IrModule m) {
        return autoUlps(m, m.functionCount() - 1);
    }

    /**
     * Выбирает допуск фактически скомпилированного варианта ядра. Обходит тела по операндам и вызовам;
     * неиспользуемые fallback-варианты не влияют на сравнение. Для вектора используется тип его компоненты.
     *
     * @param m модуль с валидным IR
     * @param function индекс выбранного ядра
     * @return максимум применимых допусков операций, не меньше 0; не является границей ошибки результата
     */
    static int autoUlps(IrModule m, int function) {
        boolean[] seen = new boolean[m.nodeCount()];
        int[] stack = new int[m.nodeCount()];
        int top = 0;
        int root = m.fBody(function);
        stack[top++] = root;
        seen[root] = true;
        int max = 0;
        while (top > 0) {
            int n = stack[--top];
            int op = m.op(n);
            int type = m.type(n);
            int scalar = m.typeKind(type) == Types.KIND_VEC ? m.typeA(type) : type;
            if (op == Op.INTRINSIC && Types.isFloat(scalar) && Builtins.isValid(m.a(n))) {
                int ulps = scalar == Types.F64 ? Builtins.ulp64(m.a(n)) : Builtins.ulp(m.a(n));
                max = Math.max(max, ulps);
            } else if ((op == Op.DIV || op == Op.REM) && Types.isFloat(scalar)) {
                max = Math.max(max, DEVICE_DIVISION_ULPS);
            }
            if (op == Op.CALL && m.fBodyKind(m.a(n)) == IrModule.BODY_IR) {
                int body = m.fBody(m.a(n));
                if (!seen[body]) {
                    seen[body] = true;
                    stack[top++] = body;
                }
            }
            for (int k = 0; k < Operands.count(m, n); k++) {
                int child = Operands.get(m, n, k);
                if (!seen[child]) {
                    seen[child] = true;
                    stack[top++] = child;
                }
            }
        }
        return max;
    }

    private static String describe(Throwable t) {
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
    }

    private static String diagnostics(List<Diagnostic> ds) {
        return String.join("; ", ds.stream().map(d -> d.code() + " " + d.message()).toList());
    }
}
