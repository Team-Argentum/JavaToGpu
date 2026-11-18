package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.Features;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;

import java.util.ArrayList;
import java.util.List;

/**
 * Кодогенератор OpenCL C: {@code emit(IrModule, Target) -> Artifact} (REWORK_ARCHITECTURE.md, 8, 8.A, 5.17).
 *
 * <p>Чистая функция: результат определяется только модулем, ядром, целью и параметрами; нет ни рантайма, ни файлов,
 * ни глобального состояния. Одинаковый вход даёт те же байты (имена выдаются по возрастанию индексов, вспомогательные
 * функции печатаются в фиксированном порядке).</p>
 *
 * <h2>Состав текста</h2>
 * <ol>
 *   <li>{@code #pragma OPENCL FP_CONTRACT OFF} всегда - запрет слияния {@code a * b + c} в FMA, иначе результат
 *       зависел бы от компилятора (8.3); разрешённое ядром слияние оптимизатор пишет явным {@code fma(...)};</li>
 *   <li>{@code #pragma OPENCL EXTENSION ... : enable} по маске {@code required_features} ядра (8.4);</li>
 *   <li>использованные вспомогательные функции ({@link Helpers});</li>
 *   <li>функции, достижимые из ядра, в порядке модуля (вызываемые раньше вызывающих); ядро - последней.</li>
 * </ol>
 *
 * <p>Ошибки возвращаются значениями: если модуль не проходит {@link Validator}, возвращаются его диагностики
 * ({@code IR-*}); {@code CG-A00} - нет такой функции, {@code CG-A01} - функция не ядро, {@code CG-U01} - конструкция
 * не входящая в v1 (нативные тела, {@code spec_const}, {@code __local}-параметры ядра, указатель-параметр ядра).</p>
 */
public final class OpenClEmitter {

    /**
     * Версия кодогенератора: часть ключа дискового кэша бинарей драйвера (REWORK_ARCHITECTURE.md, 9.3). Увеличивается при
     * любом изменении генерируемого текста или раскладки аргументов, чтобы старые образы не использовались. Ключ кэша
     * дополнительно содержит хэш самого исходника, поэтому забытое увеличение не приведёт к неверному ядру, а версия
     * нужна как явная и читаемая причина инвалидации.
     */
    public static final String VERSION = "1";

    private OpenClEmitter() {
    }

    /**
     * Генерирует OpenCL C для ядра модуля (по соглашению фронтенда ядро - последняя функция) в release-режиме.
     *
     * @param m      модуль
     * @param target цель
     * @return артефакт либо диагностики
     */
    public static Result<Artifact> emit(IrModule m, Target target) {
        if (m == null) {
            throw new IllegalArgumentException("module is required");
        }
        return emit(m, m.functionCount() - 1, target, EmitOptions.RELEASE);
    }

    /**
     * Генерирует OpenCL C для заданного ядра.
     *
     * @param m       модуль
     * @param kernel  индекс функции-ядра (например, {@code fallback}-варианта)
     * @param target  цель
     * @param options параметры генерации
     * @return артефакт (в диагностиках могут быть предупреждения валидатора) либо диагностики ошибок
     */
    public static Result<Artifact> emit(IrModule m, int kernel, Target target, EmitOptions options) {
        if (m == null || target == null || options == null) {
            throw new IllegalArgumentException("module, target and options are required");
        }
        if (kernel < 0 || kernel >= m.functionCount()) {
            return fail("CG-A00", kernel, -1, "no function in the module " + kernel);
        }
        Result<Program> p = emitKernels(m, new int[]{kernel}, target, options);
        if (!p.isOk()) {
            return Result.failed(p.diagnostics());
        }
        Program pr = p.value();
        Program.Kernel k = pr.kernels().get(0);
        return new Result<>(new Artifact(pr.source(), k.entry(), k.args(), pr.requiredFeatures(), pr.buildOptions(),
                target, options), p.diagnostics());
    }

    /**
     * Генерирует OpenCL C для всех ядер модуля одной программой (FRONTEND_ASM_RUNTIME.md, 18.2-S4, шаг B4): каждая
     * функция печатается один раз, все функции вида {@link IrModule#KERNEL} - {@code __kernel}. Флаги {@code fp} общей
     * функции - пересечение флагов всех ядер, которые её достигают; {@code native_*} - так же. Для модуля с одним
     * ядром текст совпадает с {@link #emit(IrModule, int, Target, EmitOptions)}.
     *
     * @param m       модуль
     * @param target  цель
     * @param options параметры генерации
     * @return программа либо диагностики ({@code CG-A01}, если в модуле нет ядер)
     */
    public static Result<Program> emitProgram(IrModule m, Target target, EmitOptions options) {
        if (m == null || target == null || options == null) {
            throw new IllegalArgumentException("module, target and options are required");
        }
        List<Integer> ks = new ArrayList<>();
        for (int f = 0; f < m.functionCount(); f++) {
            if (m.fKind(f) == IrModule.KERNEL) {
                ks.add(f);
            }
        }
        if (ks.isEmpty()) {
            Validator.Report report = Validator.validate(m);
            if (report.hasErrors()) {
                return Result.failed(errors(report));
            }
            return fail("CG-A01", -1, -1, "the module has no kernel");
        }
        return emitKernels(m, ks.stream().mapToInt(Integer::intValue).toArray(), target, options);
    }

    private static List<Diagnostic> errors(Validator.Report report) {
        List<Diagnostic> errors = new ArrayList<>();
        for (Diagnostic d : report.diagnostics()) {
            if (d.isError()) {
                errors.add(d);
            }
        }
        return errors;
    }

    private static Result<Program> emitKernels(IrModule m, int[] kernels, Target target, EmitOptions options) {
        Validator.Report report = Validator.validate(m);
        if (report.hasErrors()) {
            return Result.failed(errors(report));
        }
        int n = m.functionCount();
        boolean[] isKernel = new boolean[n];
        boolean[] reach = new boolean[n];
        boolean[][] reachOf = new boolean[kernels.length][];
        int[] fp = new int[n];
        java.util.Arrays.fill(fp, -1);
        int features = 0;
        List<Diagnostic> unsupported = new ArrayList<>();
        for (int i = 0; i < kernels.length; i++) {
            int k = kernels[i];
            if (m.fKind(k) != IrModule.KERNEL) {
                return fail("CG-A01", k, -1, "function " + k + " is not a kernel");
            }
            isKernel[k] = true;
            boolean[] r = Support.reachable(m, k);
            reachOf[i] = r;
            unsupported.addAll(Support.check(m, k, r));
            for (int f = 0; f < n; f++) {
                if (r[f]) {
                    reach[f] = true;
                    fp[f] &= m.fFp(k);
                }
            }
            features |= report.functions()[k].requiredFeatures();
        }
        if (!unsupported.isEmpty()) {
            return Result.failed(unsupported);
        }

        Names global = new Names();
        String[] funcNames = new String[n];
        for (int f = 0; f < n; f++) {
            if (reach[f]) {
                String wanted = options.debug() ? m.debug().functionName(f) : null;
                funcNames[f] = global.unique(wanted, (isKernel[f] ? "k" : "f") + f);
            }
        }

        Helpers helpers = new Helpers();
        Layouts layouts = new Layouts(m, Support.usedTypes(m, reach), options.debug(), global);
        StringBuilder bodies = new StringBuilder();
        java.util.Map<Integer, List<KernelArg>> args = new java.util.HashMap<>();
        for (int f = 0; f < n; f++) {
            if (!reach[f]) {
                continue;
            }
            // native_* - только для функций, чьи ядра все разрешили fp=native, и только там, где точность проверена
            int nativeMath = (fp[f] & IrModule.FP_NATIVE) != 0 ? target.nativeMath() : 0;
            FunctionEmitter fe = new FunctionEmitter(m, f, options, helpers, layouts, funcNames, global, nativeMath,
                    fp[f]);
            if (isKernel[f]) {
                List<KernelArg> a = new ArrayList<>();
                kernelArgs(m, f, fe, a);
                args.put(f, a);
            }
            if (!bodies.isEmpty()) {
                bodies.append('\n');
            }
            bodies.append(fe.emit(isKernel[f]));
        }

        StringBuilder src = new StringBuilder();
        src.append("// OpenCL C generated by JavaToGpu\n");
        src.append("#pragma OPENCL FP_CONTRACT OFF\n");
        if ((features & Features.FP64) != 0) {
            src.append("#pragma OPENCL EXTENSION cl_khr_fp64 : enable\n");
        }
        if ((features & Features.INT64_EXT_ATOMICS) != 0) {
            src.append("#pragma OPENCL EXTENSION cl_khr_int64_extended_atomics : enable\n");
        }
        if ((features & Features.INT64_ATOMICS) != 0) {
            src.append("#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable\n");
        }
        if ((features & Features.FP16) != 0) {
            src.append("#pragma OPENCL EXTENSION cl_khr_fp16 : enable\n");
        }
        src.append('\n');
        if (layouts.hasStructs()) {
            src.append(layouts.typedefs()).append('\n');
        }
        if (helpers.any()) {
            helpers.appendTo(src);
            src.append('\n');
        }
        src.append(bodies);

        // точное деление и sqrt - опция ужесточает математику; нужна, если её требует хотя бы одно ядро, не разрешившее
        // приближения (у одного ядра - как раньше)
        boolean exact = false;
        for (int i = 0; i < kernels.length; i++) {
            boolean approximate = (m.fFp(kernels[i]) & (IrModule.FP_RECIPROCAL | IrModule.FP_NATIVE)) != 0;
            exact |= !approximate && Support.usesFloatDivideOrSqrt(m, reachOf[i]);
        }
        String buildOptions = target.exactDivideSqrt() && exact ? Target.EXACT_DIVIDE_SQRT_OPTION : "";
        List<Program.Kernel> ks = new ArrayList<>();
        for (int f = 0; f < n; f++) {
            if (isKernel[f]) {
                ks.add(new Program.Kernel(f, funcNames[f], args.get(f)));
            }
        }
        if (kernels.length == 1) {
            ks = List.of(new Program.Kernel(kernels[0], funcNames[kernels[0]], args.get(kernels[0])));
        }
        return new Result<>(new Program(src.toString(), ks, features, buildOptions, target, options),
                report.diagnostics());
    }

    /** Раскрывает параметры ядра в аргументы OpenCL (8.2): массив даёт указатель и длину. */
    private static void kernelArgs(IrModule m, int f, FunctionEmitter fe, List<KernelArg> out) {
        for (int i = 0; i < m.fParamCount(f); i++) {
            int t = m.fParamType(f, i);
            if (m.typeKind(t) == Types.KIND_ARRAY) {
                out.add(new KernelArg(KernelArg.ARRAY_PTR, i, Types.elem(m, t), Types.space(m, t), fe.paramName(i)));
                out.add(new KernelArg(KernelArg.ARRAY_LEN, i, Types.I32, -1, fe.lengthName(i)));
            } else {
                out.add(new KernelArg(KernelArg.SCALAR, i, t, -1, fe.paramName(i)));
            }
        }
    }

    private static <T> Result<T> fail(String code, int function, int node, String message) {
        return Result.failed(List.of(new Diagnostic(code, Diagnostic.Severity.ERROR, function, node, message)));
    }
}
