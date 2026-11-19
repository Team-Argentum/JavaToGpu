package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Validator;

import java.util.List;

/**
 * Буферы ядра без перекрытий: {@code restrict} на {@code __global}-параметрах {@code __kernel}.
 *
 * <p>{@code restrict} обещает компилятору, что объект, который ядро меняет через параметр, не доступен через другие
 * параметры. Тогда компилятор переставляет загрузки и записи и читает через кэш только для чтения (на NVIDIA -
 * {@code ld.global.nc}, а записи в один элемент в цикле сворачивает в одну). В Java же один массив можно передать в
 * ядро дважды ({@code add(a, a)}): рантайм делает из него один буфер, и обещание нарушено.</p>
 *
 * <p>Поэтому квалификатор печатается макросом {@link #MACRO}: текст программы один, а сборка с опцией
 * {@link #ALIASED_OPTION} снимает {@code restrict} со всех параметров. Перед запуском вызывающий проверяет аргументы
 * ({@link #conflict}) и при перекрытии запускает вариант, собранный с этой опцией. Чтение одного буфера через два
 * параметра перекрытием не считается: {@code restrict} нарушается только изменением объекта.</p>
 */
public final class Aliasing {

    /** Макрос квалификатора в тексте программы: {@code restrict} либо пусто при {@link #ALIASED_OPTION}. */
    public static final String MACRO = "JTG_RESTRICT";

    /** Опция {@code clBuildProgram}, снимающая {@code restrict}: для запуска с перекрывающимися буферами. */
    public static final String ALIASED_OPTION = "-DJTG_ALIASED";

    /** Определение {@link #MACRO} в начале программы. */
    static final String PREAMBLE = "#ifdef JTG_ALIASED\n#define " + MACRO + "\n#else\n#define " + MACRO
            + " restrict\n#endif\n";

    private Aliasing() {
    }

    /**
     * @param arg аргумент ядра
     * @return {@code true}, если параметр напечатан с {@code restrict}: указатель на {@code __global}-буфер
     */
    public static boolean restricted(KernelArg arg) {
        return arg.kind() == KernelArg.ARRAY_PTR && arg.space() == AddressSpace.GLOBAL;
    }

    /**
     * Нарушит ли запуск обещание {@code restrict}: один буфер стоит в двух параметрах-массивах, хотя бы один из них
     * помечен {@code restrict} и хотя бы один ядро меняет. Для своего рантайма поверх {@link ClProgram} -
     * {@link ClProgram.KernelAbi#aliasConflict}.
     *
     * @param args    аргументы ядра
     * @param access  доступ по параметру IR ({@link Validator#ACCESS_READ}, {@link Validator#ACCESS_WRITE})
     * @param handles дескриптор буфера по параметру IR; {@code 0} - не буфер
     * @return {@code true} - запускать вариант, собранный с {@link #ALIASED_OPTION}
     */
    public static boolean conflict(List<KernelArg> args, int[] access, long[] handles) {
        for (int i = 0; i < args.size(); i++) {
            KernelArg a = args.get(i);
            if (a.kind() != KernelArg.ARRAY_PTR || handles[a.param()] == 0) {
                continue;
            }
            for (int j = i + 1; j < args.size(); j++) {
                KernelArg b = args.get(j);
                if (b.kind() != KernelArg.ARRAY_PTR || handles[a.param()] != handles[b.param()]) {
                    continue;
                }
                boolean writes = ((access[a.param()] | access[b.param()]) & Validator.ACCESS_WRITE) != 0;
                if (writes && (restricted(a) || restricted(b))) {
                    return true;
                }
            }
        }
        return false;
    }
}
