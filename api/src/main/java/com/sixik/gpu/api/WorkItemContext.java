package com.sixik.gpu.api;

/**
 * Положение выполняемого work-item в виртуальном NDRange: данные для JVM-реализаций builtins
 * {@code getGlobalId}, {@code getLocalSize} и т.п. (REWORK_ARCHITECTURE.md, O6).
 *
 * <p>Объект изменяемый и предназначен одному потоку: JVM-fallback создаёт его на запуск, затем в цикле по
 * work-item'ам вызывает {@link #setGlobalId(int, int, int)}. Данные плоские ({@code int[3]} на ось), без
 * аллокаций на work-item. Оси за пределами {@link #workDim()} ведут себя как в OpenCL: индексы 0, размеры 1.</p>
 *
 * <p>Связь величин по каждой оси {@code d}: {@code groupId = globalId / localSize},
 * {@code localId = globalId % localSize}, {@code numGroups = globalSize / localSize}. Формулы верны, потому что
 * OpenCL 1.2 требует {@code globalSize} кратным {@code localSize} (равномерные work-group), поэтому
 * {@code globalId = groupId * localSize + localId} однозначно.</p>
 */
public final class WorkItemContext {

    private static final int AXES = 3;

    private final int workDim;
    private final int[] globalSize = new int[AXES];
    private final int[] localSize = new int[AXES];
    private final int[] numGroups = new int[AXES];
    private final int[] globalId = new int[AXES];
    private final int[] localId = new int[AXES];
    private final int[] groupId = new int[AXES];

    /**
     * Создаёт контекст запуска; текущий work-item - с нулевыми индексами.
     *
     * @param workDim    размерность запуска: 1, 2 или 3
     * @param globalSize размеры NDRange по трём осям (все &gt; 0; оси от {@code workDim} и выше равны 1)
     * @param localSize  размеры work-group по трём осям (все &gt; 0, делят {@code globalSize}; оси от
     *                   {@code workDim} и выше равны 1)
     * @throws IllegalArgumentException при нарушении любого из условий
     */
    public WorkItemContext(int workDim, int[] globalSize, int[] localSize) {
        if (workDim < 1 || workDim > AXES) {
            throw new IllegalArgumentException("workDim must be 1..3: " + workDim);
        }
        if (globalSize.length != AXES || localSize.length != AXES) {
            throw new IllegalArgumentException("sizes are given for three axes");
        }
        this.workDim = workDim;
        for (int d = 0; d < AXES; d++) {
            if (globalSize[d] <= 0 || localSize[d] <= 0) {
                throw new IllegalArgumentException("sizes must be > 0 (axis " + d + ")");
            }
            if (d >= workDim && (globalSize[d] != 1 || localSize[d] != 1)) {
                throw new IllegalArgumentException("axis " + d + " outside workDim=" + workDim + " must have size 1");
            }
            if (globalSize[d] % localSize[d] != 0) {
                throw new IllegalArgumentException("globalSize[" + d + "]=" + globalSize[d]
                        + " is not a multiple of localSize[" + d + "]=" + localSize[d]);
            }
            this.globalSize[d] = globalSize[d];
            this.localSize[d] = localSize[d];
            this.numGroups[d] = globalSize[d] / localSize[d];
        }
    }

    /**
     * Создаёт контекст одномерного запуска.
     *
     * @param globalSize размер NDRange (&gt; 0)
     * @param localSize  размер work-group (&gt; 0, делит {@code globalSize})
     * @return контекст с {@code workDim = 1}
     * @throws IllegalArgumentException если условия не выполнены
     */
    public static WorkItemContext of1D(int globalSize, int localSize) {
        return new WorkItemContext(1, new int[]{globalSize, 1, 1}, new int[]{localSize, 1, 1});
    }

    /**
     * Переходит к work-item с заданным глобальным индексом; локальный индекс и индекс группы пересчитываются.
     *
     * @param x индекс по оси 0, {@code 0 <= x < globalSize[0]}
     * @param y индекс по оси 1 (для запуска с {@code workDim < 2} - 0)
     * @param z индекс по оси 2 (для запуска с {@code workDim < 3} - 0)
     * @throws IllegalArgumentException если индекс вне NDRange
     */
    public void setGlobalId(int x, int y, int z) {
        if (x < 0 || x >= globalSize[0] || y < 0 || y >= globalSize[1] || z < 0 || z >= globalSize[2]) {
            throw new IllegalArgumentException("index (" + x + "," + y + "," + z + ") outside NDRange ("
                    + globalSize[0] + "," + globalSize[1] + "," + globalSize[2] + ")");
        }
        globalId[0] = x;
        globalId[1] = y;
        globalId[2] = z;
        for (int d = 0; d < AXES; d++) {
            groupId[d] = globalId[d] / localSize[d];
            localId[d] = globalId[d] % localSize[d];
        }
    }

    /**
     * Размерность запуска.
     *
     * @return 1, 2 или 3
     */
    public int workDim() {
        return workDim;
    }

    /**
     * Глобальный индекс текущего work-item по оси.
     *
     * @param dim ось
     * @return глобальный индекс по оси; для оси вне {@code 0..2} - 0
     */
    public int globalId(int dim) {
        return inRange(dim) ? globalId[dim] : 0;
    }

    /**
     * Индекс текущего work-item внутри его work-group по оси.
     *
     * @param dim ось
     * @return индекс внутри work-group; для оси вне {@code 0..2} - 0
     */
    public int localId(int dim) {
        return inRange(dim) ? localId[dim] : 0;
    }

    /**
     * Индекс work-group текущего work-item по оси.
     *
     * @param dim ось
     * @return индекс work-group; для оси вне {@code 0..2} - 0
     */
    public int groupId(int dim) {
        return inRange(dim) ? groupId[dim] : 0;
    }

    /**
     * Размер всего NDRange по оси.
     *
     * @param dim ось
     * @return размер NDRange по оси; для оси вне {@code 0..2} - 1
     */
    public int globalSize(int dim) {
        return inRange(dim) ? globalSize[dim] : 1;
    }

    /**
     * Размер work-group по оси.
     *
     * @param dim ось
     * @return размер work-group по оси; для оси вне {@code 0..2} - 1
     */
    public int localSize(int dim) {
        return inRange(dim) ? localSize[dim] : 1;
    }

    /**
     * Число work-group по оси.
     *
     * @param dim ось
     * @return число work-group по оси; для оси вне {@code 0..2} - 1
     */
    public int numGroups(int dim) {
        return inRange(dim) ? numGroups[dim] : 1;
    }

    private static boolean inRange(int dim) {
        return dim >= 0 && dim < AXES;
    }
}
