package com.sixik.gpu.runtime;

import com.sixik.gpu.api.WorkItem;
import com.sixik.gpu.api.WorkItemContext;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Validator;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Хост-фасад: единственное место «ambient»-состояния (REWORK_ARCHITECTURE.md, 3.7; закрывает вопрос O2 - имя фасада).
 * Нужен, чтобы обычный вызов {@code Kernels.scale(a, b)} знал, где исполняться: weaver заменяет тело
 * {@code @Kernel}-метода вызовом {@link #invoke}, а он выбирает контекст и размеры запуска.
 *
 * <h2>Контекст</h2>
 * <ul>
 *   <li>явный, на время блока и на текущий поток: {@code try (var s = JavaToGpu.use(ctx)) { ... }};</li>
 *   <li>иначе ленивый процессный по умолчанию ({@link #defaultContext()}): создаётся при первом вызове по политике
 *       устройства, закрывается shutdown hook'ом (явный {@link #resetDefault()} тоже закрывает). Настроить его можно до
 *       первого использования: {@link #configureDefault}.</li>
 * </ul>
 *
 * <h2>Размер запуска (3.4)</h2>
 * <p>По умолчанию 1D, {@code global} = длина первого массива среди аргументов (первичный массив). Иначе явно:
 * {@code try (var s = JavaToGpu.launch(LaunchConfig.of2D(w, h))) { Kernels.blur(img, out); }}. Если размер нельзя
 * определить (нет массивов) и он не задан, это {@code RT-L02}.</p>
 *
 * <h2>JVM-fallback (3.5, 3.8)</h2>
 * <p>Если устройства нет или ядро не компилируется для него, а политика контекста {@link GpuContext.Fallback#ALLOWED},
 * ядро выполняется на JVM: последовательный цикл по {@code global id} поверх {@code <имя>$jvm} (тела одного
 * work-item'а, оставленного weaver'ом) под {@link WorkItem}; одно предупреждение {@link GpuEvent.Kind#FALLBACK} с
 * причиной. Атомики - обычные последовательные операции. Ядра с {@code barrier}/local-памятью на JVM не уходят
 * никогда ({@code RT-F01}). Ошибки пользователя (аргументы, размеры запуска) в fallback не маскируются.</p>
 *
 * <h2>Диагностики фасада</h2>
 * <p>{@code RT-F01} fallback невозможен (нужно устройство), {@code RT-F02} нет или повреждён IR-ресурс ядра (проект
 * собран без плагина?), {@code RT-F03} у класса нет {@code <имя>$jvm} (weave не выполнен), {@code RT-L02} размер
 * запуска не определён.</p>
 */
public final class JavaToGpu {

    /** Область действия переопределения контекста или размеров запуска; закрывается {@code try-with-resources}. */
    public interface Scope extends AutoCloseable {
        /** Снимает переопределение. */
        @Override
        void close();
    }

    private static final System.Logger LOG = System.getLogger("com.sixik.gpu");
    private static final String IR_PREFIX = "META-INF/gpu/ir/";

    private static final Object LOCK = new Object();
    private static GpuContext defaultContext;
    private static List<Diagnostic> defaultFailure;
    private static Consumer<GpuContext.Builder> defaultConfig = b -> {
    };
    private static boolean hookInstalled;

    private static final ThreadLocal<Deque<GpuContext>> CONTEXTS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<LaunchConfig>> CONFIGS = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Кэши привязаны к классу-владельцу ядра ({@link ClassValue}), а не к {@code kernelId}: IR-ресурс ищется в
     * загрузчике этого класса, и два класса с одинаковым IR из разных загрузчиков не должны делить ни модуль, ни
     * обработчик {@code $jvm}; кроме того, {@code ClassValue} не удерживает загрузчик от выгрузки.
     */
    private static final ClassValue<ConcurrentHashMap<String, IrModule>> MODULES = new ClassValue<>() {
        @Override
        protected ConcurrentHashMap<String, IrModule> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private static final ClassValue<ConcurrentHashMap<String, MethodHandle>> JVM_BODIES = new ClassValue<>() {
        @Override
        protected ConcurrentHashMap<String, MethodHandle> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private JavaToGpu() {
    }

    // =============================================================================================
    // контекст и размеры запуска
    // =============================================================================================

    /**
     * Делает {@code ctx} контекстом вызовов ядер в текущем потоке до закрытия области. Области вкладываются.
     *
     * @param ctx контекст
     * @return область; закрывать в обратном порядке открытия
     */
    public static Scope use(GpuContext ctx) {
        if (ctx == null) {
            throw new IllegalArgumentException("ctx обязателен");
        }
        return push(CONTEXTS.get(), ctx);
    }

    /**
     * Задаёт размеры запуска для вызовов ядер в текущем потоке до закрытия области.
     *
     * @param config размеры NDRange и work-group
     * @return область
     */
    public static Scope launch(LaunchConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config обязателен");
        }
        return push(CONFIGS.get(), config);
    }

    private static <T> Scope push(Deque<T> stack, T value) {
        stack.push(value);
        return new Scope() {
            private boolean closed;

            @Override
            public void close() {
                if (closed) {
                    return;
                }
                closed = true;
                if (stack.peek() != value) {
                    throw new IllegalStateException("области JavaToGpu закрываются не в порядке открытия");
                }
                stack.pop();
            }
        };
    }

    /**
     * Настраивает контекст по умолчанию (политика, приёмник событий, fallback) до его первого создания.
     *
     * @param config действие над построителем
     * @throws IllegalStateException если контекст по умолчанию уже создан (или его создание уже не удалось)
     */
    public static void configureDefault(Consumer<GpuContext.Builder> config) {
        synchronized (LOCK) {
            if (defaultContext != null || defaultFailure != null) {
                throw new IllegalStateException("контекст по умолчанию уже создан: настройте его до первого вызова ядра");
            }
            defaultConfig = config == null ? b -> {
            } : config;
        }
    }

    /**
     * Контекст по умолчанию: создаётся лениво при первом обращении и закрывается при завершении JVM.
     *
     * @return контекст либо пусто, если устройства нет (причина логируется один раз; повторных попыток нет до
     *         {@link #resetDefault()})
     */
    public static Optional<GpuContext> defaultContext() {
        synchronized (LOCK) {
            if (defaultContext != null && !defaultContext.isClosed()) {
                return Optional.of(defaultContext);
            }
            if (defaultFailure != null) {
                return Optional.empty();
            }
            GpuContext.Builder builder = GpuContext.builder();
            defaultConfig.accept(builder);
            Result<GpuContext> r = builder.tryBuild();
            if (!r.isOk()) {
                defaultFailure = r.diagnostics();
                LOG.log(System.Logger.Level.WARNING, "GPU недоступен, ядра будут выполняться на JVM: {0}", describe(defaultFailure));
                return Optional.empty();
            }
            defaultContext = r.value();
            if (!hookInstalled) {
                hookInstalled = true;
                Runtime.getRuntime().addShutdownHook(new Thread(JavaToGpu::resetDefault, "javatogpu-shutdown"));
            }
            return Optional.of(defaultContext);
        }
    }

    /**
     * Закрывает контекст по умолчанию и забывает его (и результат неудачного создания): следующее обращение создаст
     * его заново. Сбрасывает и настройку {@link #configureDefault}. Предназначено для тестов и завершения работы.
     */
    public static void resetDefault() {
        GpuContext toClose;
        synchronized (LOCK) {
            toClose = defaultContext;
            defaultContext = null;
            defaultFailure = null;
            defaultConfig = b -> {
            };
        }
        if (toClose != null) {
            toClose.close();
        }
    }

    // =============================================================================================
    // вызов ядра (его выдаёт weaver)
    // =============================================================================================

    /**
     * Точка входа делегата, который weaver вставил вместо тела {@code @Kernel}-метода. Обычным вызовом не пользуются.
     *
     * @param owner    класс с ядром (по его загрузчику ищутся IR-ресурс и {@code <имя>$jvm})
     * @param method   имя ядра
     * @param kernelId идентификатор ядра (хэш IR без отладочных данных)
     * @param args     аргументы вызова: Java-массивы и упакованные скаляры
     * @throws GpuException если ядро нельзя выполнить ни на устройстве, ни на JVM, либо аргументы не подходят
     */
    public static void invoke(Class<?> owner, String method, String kernelId, Object[] args) {
        IrModule module = module(owner, kernelId);
        LaunchConfig config = CONFIGS.get().peek();
        if (config == null) {
            config = primaryArrayConfig(args);
        }

        GpuContext ctx = CONTEXTS.get().peek();
        Optional<GpuContext> chosen = ctx != null ? Optional.of(ctx) : defaultContext();
        List<Diagnostic> reason;
        GpuContext.Fallback policy = GpuContext.Fallback.ALLOWED;
        if (chosen.isPresent()) {
            GpuContext c = chosen.get();
            policy = c.fallback();
            Result<CompiledKernel> compiled = c.compile(module);
            if (compiled.isOk()) {
                Result<LaunchStats> r = c.launch(compiled.value(), config, args);
                if (!r.isOk()) {
                    throw new GpuException(r.diagnostics());
                }
                return;
            }
            reason = compiled.diagnostics();
        } else {
            synchronized (LOCK) {
                reason = defaultFailure == null ? List.of() : defaultFailure;
            }
        }

        if (policy == GpuContext.Fallback.NEVER) {
            throw new GpuException(reason);
        }
        String why = describe(reason);
        if (chosen.isPresent()) {
            chosen.get().reportFallback(kernelId, why);
        }
        runOnJvm(owner, method, kernelId, module, config, args);
    }

    /**
     * Готовит повторные запуски {@code @Kernel}-метода с одними и теми же аргументами (горячий путь, см.
     * {@link PreparedLauncher}). Использует контекст из {@link #use} либо контекст по умолчанию. JVM-fallback здесь нет:
     * подготовленный запуск владеет буферами устройства, поэтому без устройства он невозможен.
     *
     * @param owner  класс с ядром
     * @param method имя ядра (перегрузок у ядер нет)
     * @param args   значения параметров ядра: {@link GpuBuffer}, Java-массивы и скаляры
     * @return запуск; закрывается пользователем (или вместе с контекстом)
     * @throws GpuException если ядро не найдено, устройства нет, компиляция или проверка аргументов не удались
     */
    public static PreparedLauncher prepare(Class<?> owner, String method, Object... args) {
        IrModule module = module(owner, kernelId(owner, method));
        GpuContext ctx = CONTEXTS.get().peek();
        if (ctx == null) {
            ctx = defaultContext().orElse(null);
        }
        if (ctx == null) {
            List<Diagnostic> reason;
            synchronized (LOCK) {
                reason = defaultFailure == null ? List.of() : defaultFailure;
            }
            throw new GpuException(reason);
        }
        Result<CompiledKernel> compiled = ctx.compile(module);
        if (!compiled.isOk()) {
            throw new GpuException(compiled.diagnostics());
        }
        Result<PreparedLauncher> prepared = ctx.prepare(compiled.value(), args);
        if (!prepared.isOk()) {
            throw new GpuException(prepared.diagnostics());
        }
        return prepared.value();
    }

    /**
     * Находит идентификатор ядра по классу и имени в индексах {@code META-INF/gpu/kernels.idx} загрузчика класса
     * (строки {@code класс TAB метод TAB дескриптор TAB id}).
     */
    private static String kernelId(Class<?> owner, String method) {
        ClassLoader loader = owner.getClassLoader() != null ? owner.getClassLoader() : ClassLoader.getSystemClassLoader();
        java.util.Set<String> ids = new java.util.TreeSet<>();
        try {
            java.util.Enumeration<java.net.URL> urls = loader.getResources("META-INF/gpu/kernels.idx");
            while (urls.hasMoreElements()) {
                try (InputStream in = urls.nextElement().openStream()) {
                    for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                        String[] row = line.strip().split("\t");
                        if (row.length == 4 && row[0].equals(owner.getName()) && row[1].equals(method)) {
                            ids.add(row[3]);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw fail("RT-F02", "индекс ядер не читается: " + e.getMessage());
        }
        if (ids.size() != 1) {
            throw fail("RT-F02", ids.isEmpty()
                    ? "ядро " + owner.getName() + "." + method + " не найдено в META-INF/gpu/kernels.idx"
                    : "ядро " + owner.getName() + "." + method + " неоднозначно: " + ids);
        }
        return ids.iterator().next();
    }

    // =============================================================================================
    // IR ядра и размеры
    // =============================================================================================

    private static IrModule module(Class<?> owner, String kernelId) {
        IrModule cached = MODULES.get(owner).get(kernelId);
        if (cached != null) {
            return cached;
        }
        ClassLoader loader = owner.getClassLoader() != null ? owner.getClassLoader() : ClassLoader.getSystemClassLoader();
        String path = IR_PREFIX + kernelId + ".gir";
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) {
                throw fail("RT-F02", "нет IR-ресурса " + path + " для ядра " + owner.getName()
                        + ": проект собран без annotation processor'а frontend-java?");
            }
            Result<IrModule> r = IrBinary.read(in.readAllBytes());
            if (!r.isOk()) {
                throw new GpuException(r.diagnostics());
            }
            MODULES.get(owner).put(kernelId, r.value());
            return r.value();
        } catch (IOException e) {
            throw fail("RT-F02", "IR-ресурс " + path + " не читается: " + e.getMessage());
        }
    }

    private static LaunchConfig primaryArrayConfig(Object[] args) {
        for (Object a : args) {
            if (a != null && a.getClass().isArray()) {
                int n = Marshal.length(a);
                if (n > 0) {
                    return LaunchConfig.of1D(n);
                }
                return LaunchConfig.of1D(1);
            }
        }
        throw fail("RT-L02", "размер запуска нельзя определить: у ядра нет массивов в аргументах, задайте его через "
                + "JavaToGpu.launch(LaunchConfig...)");
    }

    // =============================================================================================
    // JVM-fallback
    // =============================================================================================

    private static void runOnJvm(Class<?> owner, String method, String kernelId, IrModule module, LaunchConfig config,
                                 Object[] args) {
        int kernel = module.functionCount() - 1;
        if (Validator.validate(module).functions()[kernel].usesGroupSync()) {
            throw fail("RT-F01", "ядро " + owner.getName() + "." + method
                    + " использует barrier или local-память: на JVM оно не выполняется, нужно устройство");
        }
        MethodHandle body = JVM_BODIES.get(owner).computeIfAbsent(kernelId, k -> jvmBody(owner, method, args.length));
        int dim = config.dim();
        int[] global = new int[3];
        int[] local = new int[3];
        for (int d = 0; d < 3; d++) {
            long g = d < dim ? config.global(d) : 1;
            if (g > Integer.MAX_VALUE) {
                throw fail("RT-L01", "размер NDRange по оси " + d + " (" + g + ") не помещается в int: fallback на JVM невозможен");
            }
            global[d] = (int) g;
            int req = module.fReqWorkGroup(kernel, d);
            long given = d < dim ? config.local(d) : 1;
            local[d] = req > 0 ? req : (given > 0 ? (int) given : 1);
            if (global[d] % local[d] != 0) {
                throw fail("RT-L01", "ось " + d + ": размер NDRange " + global[d] + " не делится на размер группы " + local[d]);
            }
        }
        WorkItemContext ctx = new WorkItemContext(dim, global, local);
        Throwable[] failure = new Throwable[1];
        WorkItem.run(ctx, () -> {
            try {
                for (int z = 0; z < global[2]; z++) {
                    for (int y = 0; y < global[1]; y++) {
                        for (int x = 0; x < global[0]; x++) {
                            ctx.setGlobalId(x, y, z);
                            body.invoke(args);
                        }
                    }
                }
            } catch (Throwable t) {
                failure[0] = t;
            }
        });
        if (failure[0] instanceof RuntimeException e) {
            throw e;
        }
        if (failure[0] instanceof Error e) {
            throw e;
        }
        if (failure[0] != null) {
            throw new IllegalStateException(failure[0]);
        }
    }

    /** Находит {@code <имя>$jvm} и готовит обработчик, принимающий массив аргументов. */
    private static MethodHandle jvmBody(Class<?> owner, String method, int argCount) {
        String name = method + "$jvm";
        Method found = null;
        for (Method m : owner.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == argCount) {
                found = m;
                break;
            }
        }
        if (found == null) {
            throw fail("RT-F03", "у класса " + owner.getName() + " нет метода " + name
                    + ": weave-шаг не выполнен (подключите плагин или запустите weaver после компиляции)");
        }
        try {
            found.setAccessible(true);
            return MethodHandles.lookup().unreflect(found).asSpreader(Object[].class, argCount);
        } catch (IllegalAccessException e) {
            throw fail("RT-F03", "метод " + name + " недоступен: " + e.getMessage());
        }
    }

    // =============================================================================================
    // вспомогательное
    // =============================================================================================

    private static GpuException fail(String code, String message) {
        return new GpuException(List.of(new Diagnostic(code, Diagnostic.Severity.ERROR, -1, -1, message)));
    }

    private static String describe(List<Diagnostic> diagnostics) {
        List<String> parts = new ArrayList<>();
        for (Diagnostic d : diagnostics) {
            parts.add(d.code() + ": " + d.message());
        }
        return parts.isEmpty() ? "причина не указана" : String.join("; ", parts);
    }
}
