package com.sixik.gpu.app.terrain;

/** Плоские предвыделенные данные порции чанков: координаты и массив плотностей. */
public final class TerrainBatch {

    /** Радиус видимости в чанках; окно включает центральный чанк. */
    public static final int VIEW_RADIUS = 32;
    /** Сторона окна видимости в чанках, 2*32+1. */
    public static final int VIEW_DIAMETER = 65;
    /** Число чанков окна видимости, 65^2. */
    public static final int VISIBLE_CHUNKS = VIEW_DIAMETER * VIEW_DIAMETER;
    /** Высота чанка в блоках. */
    public static final int HEIGHT = 360;
    /** Детерминированный seed мира. */
    public static final int WORLD_SEED = 0x1234abcd;

    /** Способ размещения образцов; разреженный включает границы ячеек интерполяции. */
    public enum Sampling {
        /** Один образец на блок: 16*360*16 = 92160. */
        DENSE(16, 360, 1, 1),
        /** Сетка ячеек 4*8*4: 5*46*5 = 1150 узлов с верхними границами. */
        COARSE(5, 46, 4, 8);

        /** Число образцов на каждой горизонтальной оси. */
        public final int horizontal;
        /** Число вертикальных образцов. */
        public final int vertical;
        /** Горизонтальный шаг, блоки. */
        public final int stepXZ;
        /** Вертикальный шаг, блоки. */
        public final int stepY;

        /**
         * Задаёт геометрию сетки.
         * @param horizontal число узлов X/Z
         * @param vertical число узлов Y
         * @param stepXZ шаг X/Z в блоках
         * @param stepY шаг Y в блоках
         */
        Sampling(int horizontal, int vertical, int stepXZ, int stepY) {
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.stepXZ = stepXZ;
            this.stepY = stepY;
        }

        /**
         * Возвращает размер сетки одного чанка.
         * @return число вычислений шума на чанк
         */
        public int samplesPerChunk() {
            return horizontal * horizontal * vertical;
        }
    }

    /** Геометрия образцов. */
    public final Sampling sampling;
    /** Координаты X чанков, в чанках; длина задаёт размер порции. */
    public final int[] chunkX;
    /** Координаты Z чанков, в чанках. */
    public final int[] chunkZ;
    /** Выходные плотности, в условных блоках высоты. */
    public final float[] output;
    /** Seed текущего прохода. */
    public int seed = WORLD_SEED;
    /** Число октав; по умолчанию шесть, разрешено [1, 12]. */
    public int octaves = 6;

    /**
     * Выделяет память один раз и выбирает первые чанки окна.
     * @param chunks число чанков, [1, 4225]
     * @param sampling способ дискретизации
     * @throws IllegalArgumentException если размер порции вне диапазона
     */
    public TerrainBatch(int chunks, Sampling sampling) {
        if (chunks < 1 || chunks > VISIBLE_CHUNKS) {
            throw new IllegalArgumentException("chunks must be in [1, 4225]");
        }
        this.sampling = sampling;
        chunkX = new int[chunks];
        chunkZ = new int[chunks];
        output = new float[Math.multiplyExact(chunks, sampling.samplesPerChunk())];
        moveTo(0);
    }

    /**
     * Выбирает последовательные чанки видимого окна, с оборотом индекса.
     * @param first первый индекс в окне, неотрицательный
     */
    public void moveTo(int first) {
        for (int chunk = 0; chunk < chunkX.length; chunk++) {
            int index = (first + chunk) % VISIBLE_CHUNKS;
            chunkX[chunk] = index % VIEW_DIAMETER - VIEW_RADIUS;
            chunkZ[chunk] = index / VIEW_DIAMETER - VIEW_RADIUS;
        }
    }

    /**
     * Генерирует диапазон выходных образцов на CPU, используя ту же математику, что GPU.
     * @param from включительный индекс, не меньше нуля
     * @param to исключительный индекс, не больше output.length
     */
    public void computeCpu(int from, int to) {
        Sampling grid = sampling;
        for (int i = from; i < to; i++) {
            output[i] = TerrainNoise.sample(i, chunkX, chunkZ, grid.horizontal, grid.vertical,
                    grid.stepXZ, grid.stepY, seed, octaves);
        }
    }
}
