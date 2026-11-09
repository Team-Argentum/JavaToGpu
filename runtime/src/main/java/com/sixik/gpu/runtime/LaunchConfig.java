package com.sixik.gpu.runtime;

/**
 * Размеры запуска: NDRange 1D/2D/3D и необязательный размер work-group (REWORK_ARCHITECTURE.md, 3.4: размеры задаёт
 * пользователь).
 *
 * <p>Если размер work-group не задан, его выбирает драйвер, а ядро не должно зависеть от {@code getLocalId} и
 * {@code getGroupId}. Если ядро требует {@code reqd_work_group_size}, размер берётся из ядра. Размер NDRange по
 * каждой оси должен делиться на размер work-group.</p>
 */
public final class LaunchConfig {

    private final int dim;
    private final long[] global = {1, 1, 1};
    private final long[] local = {0, 0, 0};

    private LaunchConfig(int dim, long[] g, long[] l) {
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

    /** @param x размер по X @return одномерный запуск, work-group выбирает драйвер */
    public static LaunchConfig of1D(long x) {
        return new LaunchConfig(1, new long[]{x, 1, 1}, new long[3]);
    }

    /** @param x размер по X @param lx work-group по X @return одномерный запуск */
    public static LaunchConfig of1D(long x, long lx) {
        return new LaunchConfig(1, new long[]{x, 1, 1}, new long[]{lx, 0, 0});
    }

    /** @param x размер по X @param y размер по Y @return двумерный запуск, work-group выбирает драйвер */
    public static LaunchConfig of2D(long x, long y) {
        return new LaunchConfig(2, new long[]{x, y, 1}, new long[3]);
    }

    /** @param x размер по X @param y размер по Y @param lx work-group по X @param ly по Y @return двумерный запуск */
    public static LaunchConfig of2D(long x, long y, long lx, long ly) {
        return new LaunchConfig(2, new long[]{x, y, 1}, new long[]{lx, ly, 0});
    }

    /** @param x размер по X @param y по Y @param z по Z @return трёхмерный запуск, work-group выбирает драйвер */
    public static LaunchConfig of3D(long x, long y, long z) {
        return new LaunchConfig(3, new long[]{x, y, z}, new long[3]);
    }

    /**
     * @param x  размер по X
     * @param y  размер по Y
     * @param z  размер по Z
     * @param lx work-group по X
     * @param ly work-group по Y
     * @param lz work-group по Z
     * @return трёхмерный запуск
     */
    public static LaunchConfig of3D(long x, long y, long z, long lx, long ly, long lz) {
        return new LaunchConfig(3, new long[]{x, y, z}, new long[]{lx, ly, lz});
    }

    /** @return размерность 1..3 */
    public int dim() {
        return dim;
    }

    /** @param axis ось 0..2 @return размер NDRange (1 для осей за пределами размерности) */
    public long global(int axis) {
        return global[axis];
    }

    /** @param axis ось 0..2 @return размер work-group либо 0, если не задан */
    public long local(int axis) {
        return local[axis];
    }

    /** @return {@code true}, если размер work-group задан хотя бы по одной оси */
    public boolean hasLocal() {
        for (int d = 0; d < dim; d++) {
            if (local[d] != 0) {
                return true;
            }
        }
        return false;
    }

    /** @return число work-item'ов */
    public long workItems() {
        return global[0] * global[1] * global[2];
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LaunchConfig[");
        for (int d = 0; d < dim; d++) {
            sb.append(d > 0 ? "x" : "").append(global[d]);
        }
        if (hasLocal()) {
            sb.append(" / ");
            for (int d = 0; d < dim; d++) {
                sb.append(d > 0 ? "x" : "").append(local[d]);
            }
        }
        return sb.append(']').toString();
    }
}
