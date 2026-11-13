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

import java.util.ArrayList;
import java.util.List;

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

    private AsmFrontend() {
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

    /** Любой сбой фронтенда на ядре - {@code GPU0902} для этого ядра, а не падение сборки. */
    private static Kernel guarded(ModuleBuilder mb, ClassNode cn, MethodNode mn, boolean reduction, Options options,
                                  List<AsmDiagnostic> diags) {
        String cls = cn.name.replace('/', '.');
        try {
            return compileKernel(mb, cn, mn, reduction, options, diags);
        } catch (RuntimeException e) {
            diags.add(AsmDiagnostic.of("GPU0902", cls, mn.name, 0, mn.name, e.getMessage() != null ? e.getMessage() : e));
            return null;
        }
    }

    private static Kernel compileKernel(ModuleBuilder mb, ClassNode cn, MethodNode mn, boolean reduction,
                                        Options options, List<AsmDiagnostic> diags) {
        String cls = cn.name.replace('/', '.');
        ModuleBuilder.Built built = mb.build(cn, mn);
        if (built == null) {
            return null;
        }
        IrModule m = built.module();
        int line = built.line();
        if (!validate(m, options, cls, mn.name, line, diags)) {
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
