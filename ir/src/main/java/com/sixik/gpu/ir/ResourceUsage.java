package com.sixik.gpu.ir;

import java.util.HashSet;
import java.util.Set;

/**
 * Память устройства, которую модуль занимает сам, без аргументов: {@code local}-массивы ядра и таблицы констант
 * ({@code CONST_TABLE}). Нужна для проверки пределов устройства ({@code CL_DEVICE_LOCAL_MEM_SIZE},
 * {@code CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE}) и рантаймом проекта ({@code RT-D04}), и чужим рантаймом по
 * {@code ClProgram}. Размеры элементов - по {@link AbiLayout}, как у массива в C ({@code float3} - 16 байт).
 */
public final class ResourceUsage {

    private ResourceUsage() {
    }

    /**
     * Сколько байт {@code local}-памяти объявляет ядро: сумма {@code длина × размер элемента} по узлам
     * {@code local_array_decl} верхнего уровня тела (правило IR-M02 запрещает их где-либо ещё).
     *
     * @param m      модуль
     * @param kernel индекс ядра
     * @return байты local-памяти на work-group
     */
    public static long localMemoryBytes(IrModule m, int kernel) {
        int body = m.fBody(kernel);
        long bytes = 0;
        if (body >= 0 && m.op(body) == Op.BLOCK) {
            for (int i = 0; i < m.b(body); i++) {
                int st = m.list(m.a(body) + i);
                if (m.op(st) == Op.LOCAL_ARRAY_DECL) {
                    int elem = Types.elem(m, m.fLocalType(kernel, m.a(st)));
                    bytes += (long) m.a(m.b(st)) * AbiLayout.sizeOf(m, elem);
                }
            }
        }
        return bytes;
    }

    /**
     * Суммарный размер различных таблиц констант модуля: одинаковые таблицы (тип элемента, начало и длина в пуле)
     * печатаются одним {@code __constant}-массивом программы, поэтому считаются один раз.
     *
     * @param m модуль
     * @return байты {@code __constant}-памяти области программы
     */
    public static long constantTableBytes(IrModule m) {
        Set<String> seen = new HashSet<>();
        long bytes = 0;
        for (int n = 0; n < m.nodeCount(); n++) {
            if (m.op(n) == Op.CONST_TABLE) {
                int elem = m.typeB(m.type(n));
                if (seen.add(elem + ":" + m.a(n) + ":" + m.b(n))) {
                    bytes += (long) m.b(n) * AbiLayout.sizeOf(m, elem);
                }
            }
        }
        return bytes;
    }
}
