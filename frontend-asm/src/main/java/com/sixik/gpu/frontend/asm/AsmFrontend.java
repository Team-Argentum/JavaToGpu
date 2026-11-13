package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.frontend.common.KernelAttributes;
import com.sixik.gpu.frontend.common.KernelResources;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;
import com.sixik.gpu.ir.optimizer.Reductions;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private static final String JVM_SUFFIX = "$jvm";

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
        for (String cls : classes) {
            ClassNode cn = read(source, cls, diags);
            if (cn == null) {
                continue;
            }
            for (MethodNode mn : cn.methods) {
                boolean kernel = Annotations.find(mn.invisibleAnnotations, Annotations.KERNEL) != null;
                boolean reduction = Annotations.find(mn.invisibleAnnotations, Annotations.REDUCTION) != null;
                if (kernel || reduction) {
                    Kernel k = guarded(cn, mn, kernel, reduction, options, diags);
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
    private static Kernel guarded(ClassNode cn, MethodNode mn, boolean kernel, boolean reduction, Options options,
                                  List<AsmDiagnostic> diags) {
        String cls = cn.name.replace('/', '.');
        try {
            return compileKernel(cn, mn, kernel, reduction, options, diags);
        } catch (RuntimeException e) {
            diags.add(AsmDiagnostic.of("GPU0902", cls, mn.name, 0, mn.name, e.getMessage() != null ? e.getMessage() : e));
            return null;
        }
    }

    private static Kernel compileKernel(ClassNode cn, MethodNode mn, boolean kernel, boolean reduction,
                                        Options options, List<AsmDiagnostic> diags) {
        String cls = cn.name.replace('/', '.');
        MethodNode body = bodyOf(cn, mn);
        MethodLowerer ml = new MethodLowerer(cls, body, mn.name, diags);
        int line = ml.firstLine();
        int before = errorCount(diags);
        if (kernel && reduction) {
            diags.add(AsmDiagnostic.of("GPU0017", cls, mn.name, line, mn.name));
            return null;
        }
        if ((mn.access & Opcodes.ACC_STATIC) == 0) {
            diags.add(AsmDiagnostic.of("GPU0001", cls, mn.name, line, mn.name, kernel ? "Kernel" : "Reduction"));
        }
        if ((mn.access & Opcodes.ACC_VARARGS) != 0 || mn.exceptions != null && !mn.exceptions.isEmpty()
                || mn.signature != null && mn.signature.startsWith("<")) {
            diags.add(AsmDiagnostic.of("GPU0007", cls, mn.name, line, mn.name));
        }
        if (kernel && ml.retKind != Kinds.K_VOID) {
            diags.add(AsmDiagnostic.of("GPU0002", cls, mn.name, line, mn.name));
        }
        if (reduction && (ml.retKind < Kinds.K_INT || ml.retKind > Kinds.K_DOUBLE)) {
            diags.add(AsmDiagnostic.of("GPU0016", cls, mn.name, line, mn.name));
        }
        KernelAttributes attrs = null;
        if (kernel) {
            AnnotationNode k = Annotations.find(mn.invisibleAnnotations, Annotations.KERNEL);
            AnnotationNode wg = Annotations.find(mn.invisibleAnnotations, Annotations.WORK_GROUP);
            Map<String, Object> explicit = Annotations.explicitValues(k);
            attrs = KernelAttributes.parse(mn.name, explicit, wg == null ? null : Annotations.explicitValues(wg),
                    (code, args) -> diags.add(AsmDiagnostic.of(code, cls, mn.name, line, args)));
            if (explicit.containsKey("fallback") && !"".equals(explicit.get("fallback"))) {
                throw new MethodLowerer.NotYet("@Kernel(fallback) (step A4)");
            }
        }
        boolean checked = ml.check();
        if (!checked || errorCount(diags) != before) {
            return null;
        }
        List<St> stmts = ml.execute();
        if (errorCount(diags) != before) {
            return null;
        }

        IrBuilder b = new IrBuilder();
        int n = ml.paramKinds.length;
        int[] ptypes = new int[n];
        for (int i = 0; i < n; i++) {
            ptypes[i] = ml.paramKinds[i] == Kinds.K_ARRAY
                    ? b.array(ml.paramSpaces[i], Kinds.storageType(ml.paramElems[i]))
                    : Kinds.irType(ml.paramKinds[i]);
        }
        int ret = ml.retKind == Kinds.K_VOID ? Types.VOID : Kinds.irType(ml.retKind);
        b.beginFunction(kernel ? IrModule.KERNEL : IrModule.DEVICE, ret, ptypes);
        if (attrs != null) {
            attrs.apply(b, -1);
        }
        b.nameFunction(mn.name);
        b.nameParams(ml.paramNames);
        List<Integer> top = new Emitter(b).body(ml.paramVars, ptypes, stmts);
        int[] root = new int[top.size()];
        for (int i = 0; i < root.length; i++) {
            root[i] = top.get(i);
        }
        b.endFunction(b.block(root));
        IrModule m = b.build();

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

    /** Тело ядра: у сплетённого класса - {@code <имя>$jvm} с тем же дескриптором, иначе сам метод. */
    private static MethodNode bodyOf(ClassNode cn, MethodNode mn) {
        for (MethodNode other : cn.methods) {
            if (other.name.equals(mn.name + JVM_SUFFIX) && other.desc.equals(mn.desc)) {
                return other;
            }
        }
        return mn;
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
