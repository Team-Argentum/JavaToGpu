package com.sixik.gpu.ir;

/**
 * Раскладка типов в памяти устройства (ABI): размер, выравнивание, смещения полей
 * (REWORK_ARCHITECTURE.md, 5.2, 5.3; O4 - живёт в {@code ir}, общая для кодгена и рантайма).
 *
 * <p>Правила совпадают с OpenCL C:</p>
 * <ul>
 *   <li>скаляр: размер = ширина типа, выравнивание = размер;</li>
 *   <li>вектор из {@code n} компонент по {@code s} байт: размер {@code s * n}, но для {@code n = 3}
 *       размер как у четырёх компонент ({@code sizeof(float3) == 16}); выравнивание = размер;</li>
 *   <li>указатель: 8 байт (v1 ориентирован на 64-битные устройства);</li>
 *   <li>struct: поля по порядку, каждое со смещением, кратным его выравниванию; выравнивание struct =
 *       максимум из выравниваний полей и явного {@code align}; размер округляется вверх до выравнивания.</li>
 * </ul>
 *
 * <p>Функции чистые: возвращают {@code -1}, если тип не имеет раскладки (массив, {@code VOID}, {@code BOOL},
 * зарезервированные виды).</p>
 */
public final class AbiLayout {

    private AbiLayout() {
    }

    /**
     * Размер значения типа в байтах.
     *
     * @param m модуль
     * @param t id типа
     * @return размер либо {@code -1}, если тип не имеет раскладки в памяти
     */
    public static int sizeOf(IrModule m, int t) {
        return layout(m, t, true);
    }

    /**
     * Выравнивание типа в байтах.
     *
     * @param m модуль
     * @param t id типа
     * @return выравнивание либо {@code -1}, если тип не имеет раскладки в памяти
     */
    public static int alignOf(IrModule m, int t) {
        return layout(m, t, false);
    }

    private static int layout(IrModule m, int t, boolean wantSize) {
        if (t < 0 || t >= m.typeCount()) {
            return -1;
        }
        if (Types.isScalar(t)) {
            if (t == Types.BOOL) {
                return -1;
            }
            int s = Types.bits(t) / 8;
            return s;
        }
        switch (m.typeKind(t)) {
            case Types.KIND_VEC -> {
                int elem = Types.bits(m.typeA(t)) / 8;
                int lanes = m.typeB(t);
                int size = elem * (lanes == 3 ? 4 : lanes);
                return size;
            }
            case Types.KIND_PTR -> {
                return 8;
            }
            case Types.KIND_STRUCT -> {
                return layoutStruct(m, t, wantSize);
            }
            default -> {
                return -1;
            }
        }
    }

    private static int layoutStruct(IrModule m, int t, boolean wantSize) {
        int offset = 0;
        int maxAlign = 1;
        for (int i = 0; i < m.structFieldCount(t); i++) {
            int ft = m.structFieldType(t, i);
            int fa = alignOf(m, ft);
            int fs = sizeOf(m, ft);
            if (fa <= 0 || fs < 0) {
                return -1;
            }
            offset = alignUp(offset, fa) + fs;
            maxAlign = Math.max(maxAlign, fa);
        }
        int align = Math.max(maxAlign, m.typeD(t));
        return wantSize ? alignUp(offset, align) : align;
    }

    /**
     * Смещение поля struct от начала значения.
     *
     * @param m          модуль
     * @param structType id struct-типа
     * @param field      индекс поля
     * @return смещение в байтах либо {@code -1}, если раскладка невозможна
     */
    public static int fieldOffset(IrModule m, int structType, int field) {
        int offset = 0;
        for (int i = 0; i <= field; i++) {
            int ft = m.structFieldType(structType, i);
            int fa = alignOf(m, ft);
            int fs = sizeOf(m, ft);
            if (fa <= 0 || fs < 0) {
                return -1;
            }
            offset = alignUp(offset, fa);
            if (i == field) {
                return offset;
            }
            offset += fs;
        }
        return -1;
    }

    private static int alignUp(int value, int align) {
        return (value + align - 1) / align * align;
    }
}
