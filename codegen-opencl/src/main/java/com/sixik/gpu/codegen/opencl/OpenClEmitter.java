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
 *   <li>{@code #pragma OPENCL FP_CONTRACT OFF} (кроме {@code fastMath}) - запрет слияния {@code a * b + c} в FMA, иначе
 *       результат зависел бы от компилятора (8.3);</li>
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
        Validator.Report report = Validator.validate(m);
        if (report.hasErrors()) {
            List<Diagnostic> errors = new ArrayList<>();
            for (Diagnostic d : report.diagnostics()) {
                if (d.isError()) {
                    errors.add(d);
                }
            }
            return Result.failed(errors);
        }
        if (m.fKind(kernel) != IrModule.KERNEL) {
            return fail("CG-A01", kernel, -1, "function " + kernel + " is not a kernel");
        }
        boolean[] reach = Support.reachable(m, kernel);
        List<Diagnostic> unsupported = Support.check(m, kernel, reach);
        if (!unsupported.isEmpty()) {
            return Result.failed(unsupported);
        }

        int features = report.functions()[kernel].requiredFeatures();
        Names global = new Names();
        String[] funcNames = new String[m.functionCount()];
        for (int f = 0; f < funcNames.length; f++) {
            if (reach[f]) {
                String wanted = options.debug() ? m.debug().functionName(f) : null;
                funcNames[f] = global.unique(wanted, (f == kernel ? "k" : "f") + f);
            }
        }

        Helpers helpers = new Helpers();
        Layouts layouts = new Layouts(m, Support.usedTypes(m, reach), options.debug(), global);
        StringBuilder bodies = new StringBuilder();
        List<KernelArg> args = new ArrayList<>();
        for (int f = 0; f < funcNames.length; f++) {
            if (!reach[f]) {
                continue;
            }
            FunctionEmitter fe = new FunctionEmitter(m, f, options, helpers, layouts, funcNames, global);
            if (f == kernel) {
                kernelArgs(m, f, fe, args);
            }
            if (!bodies.isEmpty()) {
                bodies.append('\n');
            }
            bodies.append(fe.emit(f == kernel));
        }

        StringBuilder src = new StringBuilder();
        src.append("// OpenCL C generated by JavaToGpu\n");
        if (!options.fastMath()) {
            src.append("#pragma OPENCL FP_CONTRACT OFF\n");
        }
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

        Artifact artifact = new Artifact(src.toString(), funcNames[kernel], args, features,
                options.fastMath() ? "-cl-fast-relaxed-math" : "", target, options);
        return new Result<>(artifact, report.diagnostics());
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

    private static Result<Artifact> fail(String code, int function, int node, String message) {
        return Result.failed(List.of(new Diagnostic(code, Diagnostic.Severity.ERROR, function, node, message)));
    }
}
