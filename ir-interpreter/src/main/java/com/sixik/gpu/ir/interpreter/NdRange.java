package com.sixik.gpu.ir.interpreter;

/**
 * Виртуальный NDRange запуска: размерность (1, 2 или 3), размеры по осям и необязательные размеры work-group.
 *
 * <p>Размер work-group можно не задавать ({@code 0}): тогда интерпретатор берёт {@code reqd_work_group_size}
 * ядра, а если и его нет - {@code 1} по каждой оси (каждый work-item в своей группе). Ядро без барьеров не может
 * наблюдать разницу, кроме как через {@code getLocalId/getGroupId/getLocalSize/getNumGroups}.</p>
 *
 * <p>Объект неизменяем. Неверные размеры (не положительные) - ошибка программиста, поэтому фабрики бросают
 * {@link IllegalArgumentException}; согласование с ядром (делимость, {@code reqd_work_group_size}) проверяет
 * {@link Interpreter#run} и возвращает диагностику {@code IN-L01}.</p>
 */
public final class NdRange {

    private final int dim;
    private final int[] global = new int[]{1, 1, 1};
    private final int[] local = new int[]{0, 0, 0};

    private NdRange(int dim, int[] g, int[] l) {
        this.dim = dim;
        for (int d = 0; d < dim; d++) {
            if (g[d] <= 0) {
                throw new IllegalArgumentException("NDRange size on axis " + d + " must be > 0: " + g[d]);
            }
            if (l[d] < 0) {
                throw new IllegalArgumentException("work-group size on axis " + d + " cannot be < 0: " + l[d]);
            }
            global[d] = g[d];
            local[d] = l[d];
        }
    }

    /**
     * @param globalX размер по оси x
     * @return одномерный запуск, размер группы не задан
     */
    public static NdRange of1D(int globalX) {
        return new NdRange(1, new int[]{globalX, 1, 1}, new int[3]);
    }

    /**
     * @param globalX размер по оси x
     * @param localX  размер work-group по x ({@code 0} - не задан)
     * @return одномерный запуск
     */
    public static NdRange of1D(int globalX, int localX) {
        return new NdRange(1, new int[]{globalX, 1, 1}, new int[]{localX, 0, 0});
    }

    /**
     * @param globalX размер по оси x
     * @param globalY размер по оси y
     * @return двумерный запуск, размер группы не задан
     */
    public static NdRange of2D(int globalX, int globalY) {
        return new NdRange(2, new int[]{globalX, globalY, 1}, new int[3]);
    }

    /**
     * @param globalX размер по оси x
     * @param globalY размер по оси y
     * @param localX  размер work-group по x ({@code 0} - не задан)
     * @param localY  размер work-group по y ({@code 0} - не задан)
     * @return двумерный запуск
     */
    public static NdRange of2D(int globalX, int globalY, int localX, int localY) {
        return new NdRange(2, new int[]{globalX, globalY, 1}, new int[]{localX, localY, 0});
    }

    /**
     * @param globalX размер по оси x
     * @param globalY размер по оси y
     * @param globalZ размер по оси z
     * @return трёхмерный запуск, размер группы не задан
     */
    public static NdRange of3D(int globalX, int globalY, int globalZ) {
        return new NdRange(3, new int[]{globalX, globalY, globalZ}, new int[3]);
    }

    /**
     * @param globalX размер по оси x
     * @param globalY размер по оси y
     * @param globalZ размер по оси z
     * @param localX  размер work-group по x ({@code 0} - не задан)
     * @param localY  размер work-group по y ({@code 0} - не задан)
     * @param localZ  размер work-group по z ({@code 0} - не задан)
     * @return трёхмерный запуск
     */
    public static NdRange of3D(int globalX, int globalY, int globalZ, int localX, int localY, int localZ) {
        return new NdRange(3, new int[]{globalX, globalY, globalZ}, new int[]{localX, localY, localZ});
    }

    /**
     * @return размерность запуска: 1, 2 или 3
     */
    public int dim() {
        return dim;
    }

    /**
     * @param axis ось {@code 0..2}
     * @return размер NDRange по оси (1 для осей вне размерности)
     */
    public int global(int axis) {
        return global[axis];
    }

    /**
     * @param axis ось {@code 0..2}
     * @return заданный размер work-group по оси либо {@code 0}, если не задан
     */
    public int local(int axis) {
        return local[axis];
    }

    /**
     * @return общее число work-item'ов ({@code long}: произведение трёх {@code int} может не поместиться в {@code int})
     */
    public long workItems() {
        return (long) global[0] * global[1] * global[2];
    }
}
