package com.sixik.gpu.ir;

/**
 * Address space указателей и массивов (REWORK_ARCHITECTURE.md, 5.5).
 *
 * <p>Значения - числовые константы (а не enum), потому что они лежат в колонке таблицы типов
 * и пишутся в бинарный формат как есть.</p>
 */
public final class AddressSpace {

    private AddressSpace() {
    }

    /** Глобальная память устройства ({@code __global}). */
    public static final int GLOBAL = 0;
    /** Локальная память рабочей группы ({@code __local}). */
    public static final int LOCAL = 1;
    /** Константная память, только чтение ({@code __constant}). */
    public static final int CONSTANT = 2;
    /** Приватная память work-item ({@code __private}). */
    public static final int PRIVATE = 3;

    /**
     * @param space значение address space
     * @return {@code true}, если значение одно из четырёх допустимых
     */
    public static boolean isValid(int space) {
        return space >= GLOBAL && space <= PRIVATE;
    }

    /**
     * Короткое имя для дампов и диагностик.
     *
     * @param space значение address space
     * @return {@code global/local/constant/private}, иначе {@code "space?"}
     */
    public static String name(int space) {
        return switch (space) {
            case GLOBAL -> "global";
            case LOCAL -> "local";
            case CONSTANT -> "constant";
            case PRIVATE -> "private";
            default -> "space?";
        };
    }
}
