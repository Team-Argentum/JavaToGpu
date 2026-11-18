package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.frontend.common.KernelResources;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Validator;
import com.sixik.gpu.ir.optimizer.Reductions;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Фронтенд байткода (REWORK_ARCHITECTURE.md, раздел 17): читает class-файлы (ASM tree API) и строит тот же IR, что
 * {@code frontend-java} строит из исходников. Выход - те же ресурсы {@code META-INF/gpu/**}
 * ({@link KernelResources}), поэтому рантайм и weaver не знают, какой фронтенд их записал.
 *
 * <p>{@link #compile} - чистая функция: классы берутся из {@link ClassSource}, результат и диагностики - значения.
 * Сбой на одном ядре - {@code GPU0902} для этого ядра, остальные обрабатываются (17.12).</p>
 *
 * <p>Ядро сплетённого класса (weaver уже перенёс тело в {@code <имя>$jvm}) опознаётся по аннотации на исходном
 * методе, а тело берётся из {@code <имя>$jvm} (17.3) - так инкрементальная сборка видит неизменённые классы.</p>
 */
public final class AsmFrontend {

    /**
     * Настройки.
     *
     * @param strictBarriers барьер под расходящимся управлением - ошибка {@code GPU0131}, а не предупреждение
     *                       {@code GPU0130} (как {@code -Ajavatogpu.strictBarriers})
     */
    public record Options(boolean strictBarriers) {
        /** Настройки по умолчанию. */
        public static final Options DEFAULT = new Options(false);
    }

    /**
     * Построенное ядро.
     *
     * @param entry  строка индекса {@code kernels.idx}
     * @param module модуль IR
     * @param bytes  бинарный IR ({@link IrBinary#write})
     */
    public record Kernel(KernelResources.IndexEntry entry, IrModule module, byte[] bytes) {
    }

    /**
     * Результат.
     *
     * @param kernels     построенные ядра (без ядер с ошибками)
     * @param diagnostics все диагностики по порядку
     */
    public record Result(List<Kernel> kernels, List<AsmDiagnostic> diagnostics) {
        /** @return {@code true}, если среди диагностик нет ошибок */
        public boolean ok() {
            return diagnostics.stream().noneMatch(AsmDiagnostic::error);
        }
    }

    /**
     * Итог файловой обёртки {@link #compileTree}.
     *
     * @param kernels     число ядер в новом индексе
     * @param removed     удалено устаревших {@code .gir} (ядра, которых больше нет)
     * @param diagnostics все диагностики по порядку
     * @param messages    те же диагностики для журнала сборки: {@code pkg/Имя.java:строка: GPU####: текст (метод)}
     */
    public record Report(int kernels, int removed, List<AsmDiagnostic> diagnostics, List<String> messages) {
        /** @return {@code true}, если среди диагностик нет ошибок */
        public boolean ok() {
            return diagnostics.stream().noneMatch(AsmDiagnostic::error);
        }
    }

    private AsmFrontend() {
    }

    /**
     * Файловая обёртка (17.3): находит ядра во всех классах каталога, строит их IR и пишет ресурсы
     * {@code META-INF/gpu/**} туда же - как annotation processor. Индекс пересобирается по всем классам каталога (при
     * инкрементальной сборке неизменённые классы уже сплетены: тело ядра берётся из {@code <имя>$jvm}); каталог
     * {@code META-INF/gpu/ir/} принадлежит фронтенду - {@code .gir} вне нового индекса удаляются.
     *
     * @param classes   корень каталога классов модуля (выход {@code compileJava})
     * @param classpath classpath компиляции: каталоги и jar (device-функции других классов и библиотек, типы API)
     * @param options   настройки
     * @return отчёт; при ошибках ресурсы ядер без ошибок всё равно записаны
     * @throws UncheckedIOException при ошибке ввода-вывода
     */
    public static Report compileTree(Path classes, List<Path> classpath, Options options) {
        List<Path> roots = new ArrayList<>();
        roots.add(classes);
        roots.addAll(classpath);
        try (PathClassSource source = new PathClassSource(roots)) {
            Map<String, String> sourceFiles = new HashMap<>();
            List<String> kernelClasses = scan(classes, sourceFiles);
            Result r = compile(source, kernelClasses, options);
            Path irDir = classes.resolve(KernelResources.IR_DIR);
            Set<String> keep = new HashSet<>();
            List<KernelResources.IndexEntry> index = new ArrayList<>();
            for (Kernel k : r.kernels()) {
                Path file = classes.resolve(KernelResources.irPath(k.entry().kernelId()));
                keep.add(file.getFileName().toString());
                if (!Files.isRegularFile(file) || !Arrays.equals(Files.readAllBytes(file), k.bytes())) {
                    Files.createDirectories(file.getParent());
                    Files.write(file, k.bytes());
                }
                index.add(k.entry());
            }
            Path idx = classes.resolve(KernelResources.INDEX);
            if (index.isEmpty()) {
                Files.deleteIfExists(idx);
            } else {
                Files.createDirectories(idx.getParent());
                Files.writeString(idx, KernelResources.formatIndex(index), StandardCharsets.UTF_8);
            }
            int removed = 0;
            if (Files.isDirectory(irDir)) {
                try (Stream<Path> files = Files.list(irDir)) {
                    for (Path f : files.toList()) {
                        String n = f.getFileName().toString();
                        if (n.endsWith(KernelResources.IR_EXT) && !keep.contains(n)) {
                            Files.delete(f);
                            removed++;
                        }
                    }
                }
            }
            List<String> messages = new ArrayList<>();
            for (AsmDiagnostic d : r.diagnostics()) {
                messages.add(position(d, sourceFiles, source) + ": " + d.message()
                        + (d.method().isEmpty() ? "" : " (" + simpleClass(d.className()) + "." + d.method() + ")"));
            }
            return new Report(index.size(), removed, r.diagnostics(), List.copyOf(messages));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Классы каталога с {@code @Kernel}/{@code @Reduction}: только заголовки и аннотации, без кода. */
    private static List<String> scan(Path classes, Map<String, String> sourceFiles) throws IOException {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(classes)) {
            return out;
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(classes)) {
            files = walk.filter(f -> f.toString().endsWith(".class") && Files.isRegularFile(f)).sorted().toList();
        }
        for (Path f : files) {
            ClassNode cn = new ClassNode();
            try {
                new ClassReader(Files.readAllBytes(f)).accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            } catch (RuntimeException e) {
                continue; // повреждённый класс без ядер нам не нужен; класс с ядрами недоступен и weaver'у
            }
            if (cn.sourceFile != null) {
                sourceFiles.put(cn.name, cn.sourceFile);
            }
            for (MethodNode mn : cn.methods) {
                if (Annotations.find(mn.invisibleAnnotations, Annotations.KERNEL) != null
                        || Annotations.find(mn.invisibleAnnotations, Annotations.REDUCTION) != null) {
                    out.add(cn.name.replace('/', '.'));
                    break;
                }
            }
        }
        return out;
    }

    /** {@code pkg/Имя.java:строка} (17.12): имя файла из {@code SourceFile}, пакет из имени класса. */
    private static String position(AsmDiagnostic d, Map<String, String> sourceFiles, ClassSource source) {
        String internal = d.className().replace('.', '/');
        String file = sourceFiles.computeIfAbsent(internal, n -> {
            try {
                byte[] b = source.bytes(n);
                if (b == null) {
                    return null;
                }
                ClassNode cn = new ClassNode();
                new ClassReader(b).accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
                return cn.sourceFile;
            } catch (RuntimeException e) {
                return null;
            }
        });
        int slash = internal.lastIndexOf('/');
        String where = file == null ? d.className() : internal.substring(0, slash + 1) + file;
        return d.line() > 0 ? where + ":" + d.line() : where;
    }

    private static String simpleClass(String binary) {
        return binary.substring(binary.lastIndexOf('.') + 1);
    }

    /**
     * Строит IR всех ядер перечисленных классов.
     *
     * @param source  доступ к class-файлам
     * @param classes двоичные имена классов ({@code pkg.Outer$Inner})
     * @param options настройки
     * @return ядра и диагностики
     */
    public static Result compile(ClassSource source, List<String> classes, Options options) {
        // глубокая вложенность (B7, S9-1): рекурсивные проходы - на потоке с большим стеком
        return com.sixik.gpu.ir.DeepStack.call("javatogpu-frontend-asm", () -> compileHere(source, classes, options));
    }

    private static Result compileHere(ClassSource source, List<String> classes, Options options) {
        List<Kernel> kernels = new ArrayList<>();
        List<AsmDiagnostic> diags = new ArrayList<>();
        ModuleBuilder mb = new ModuleBuilder(source, diags);
        for (String cls : classes) {
            ClassNode cn = read(source, cls, diags);
            if (cn == null) {
                continue;
            }
            mb.add(cn);
            for (MethodNode mn : cn.methods) {
                boolean kernel = Annotations.find(mn.invisibleAnnotations, Annotations.KERNEL) != null;
                boolean reduction = Annotations.find(mn.invisibleAnnotations, Annotations.REDUCTION) != null;
                if (kernel || reduction) {
                    Kernel k = guarded(mb, cn, mn, reduction, options, diags);
                    if (k != null) {
                        kernels.add(k);
                    }
                }
            }
        }
        return new Result(kernels, diags);
    }

    private static ClassNode read(ClassSource source, String cls, List<AsmDiagnostic> diags) {
        byte[] bytes;
        try {
            bytes = source.bytes(cls.replace('.', '/'));
        } catch (RuntimeException e) {
            diags.add(AsmDiagnostic.of("GPU0306", cls, "", 0, cls, e));
            return null;
        }
        if (bytes == null) {
            diags.add(AsmDiagnostic.of("GPU0306", cls, "", 0, cls, "class file not found"));
            return null;
        }
        try {
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn, 0);
            return cn;
        } catch (RuntimeException e) {
            diags.add(AsmDiagnostic.of("GPU0306", cls, "", 0, cls, e));
            return null;
        }
    }

    /**
     * Любой сбой фронтенда на ядре - {@code GPU0902} для этого ядра, а не падение сборки. Общий путь для
     * {@link #compile} и {@link AsmJit#compile}: сборка модуля, валидатор, проверка шаблона {@code @Reduction}.
     */
    static Kernel guarded(ModuleBuilder mb, ClassNode cn, MethodNode mn, boolean reduction, Options options,
                                  List<AsmDiagnostic> diags) {
        String cls = cn.name.replace('/', '.');
        try {
            return compileKernel(mb, cn, mn, reduction, options, diags);
        } catch (RuntimeException e) {
            diags.add(AsmDiagnostic.of("GPU0902", cls, mn.name, 0, mn.name, e.getMessage() != null ? e.getMessage() : e));
            return null;
        } catch (StackOverflowError e) {
            diags.add(AsmDiagnostic.of("GPU0902", cls, mn.name, 0, mn.name, NESTING_TOO_DEEP));
            return null;
        }
    }

    /** Текст {@code GPU0902} при переполнении стека даже на потоке {@link com.sixik.gpu.ir.DeepStack}. */
    static final String NESTING_TOO_DEEP = "the nesting is too deep (stack overflow even with "
            + (com.sixik.gpu.ir.DeepStack.STACK_BYTES >> 20) + " MB of stack); split the method";

    private static Kernel compileKernel(ModuleBuilder mb, ClassNode cn, MethodNode mn, boolean reduction,
                                        Options options, List<AsmDiagnostic> diags) {
        String cls = cn.name.replace('/', '.');
        ModuleBuilder.Built built = mb.build(cn, mn);
        if (built == null) {
            return null;
        }
        IrModule m = built.module();
        int line = built.line();
        // exact (18.2-S6): из ядра достижимы только побитово точные операции
        boolean exactOk = true;
        for (com.sixik.gpu.frontend.common.ExactCheck.Problem pr : com.sixik.gpu.frontend.common.ExactCheck.check(m)) {
            String shown = m.debug().functionName(pr.kernel());
            diags.add(AsmDiagnostic.of("GPU0310", cls, mn.name, line, shown != null ? shown : mn.name, pr.operation(),
                    pr.reason()));
            exactOk = false;
        }
        if (!exactOk || !validate(m, options, cls, mn.name, line, diags)) {
            return null;
        }
        if (reduction) {
            var plan = Reductions.analyze(m);
            if (!plan.isOk()) {
                String why = plan.diagnostics().get(0).message();
                String prefix = "reduction " + mn.name + ": ";
                diags.add(AsmDiagnostic.of("GPU0018", cls, mn.name, line, mn.name,
                        why.startsWith(prefix) ? why.substring(prefix.length()) : why));
                return null;
            }
        }
        KernelResources.IndexEntry entry = new KernelResources.IndexEntry(cls, mn.name, mn.desc, IrBinary.kernelId(m));
        return new Kernel(entry, m, IrBinary.write(m));
    }

    private static int errorCount(List<AsmDiagnostic> diags) {
        int n = 0;
        for (AsmDiagnostic d : diags) {
            if (d.error()) {
                n++;
            }
        }
        return n;
    }

    /** Валидатор IR: {@code IR-B01} - {@code GPU0130/0131}, прочее - {@code GPU0901/0903} (как {@code ModuleCompiler}). */
    private static boolean validate(IrModule m, Options options, String cls, String method, int line,
                                    List<AsmDiagnostic> diags) {
        Validator.Report report = Validator.validate(m, new Validator.Options(options.strictBarriers()));
        boolean ok = true;
        for (Diagnostic d : report.diagnostics()) {
            if (d.code().equals("IR-B01")) {
                diags.add(AsmDiagnostic.of(d.isError() ? "GPU0131" : "GPU0130", cls, method, line, method, d.message()));
            } else {
                diags.add(AsmDiagnostic.of(d.isError() ? "GPU0901" : "GPU0903", cls, method, line, method, d));
            }
            ok &= !d.isError();
        }
        return ok;
    }
}
