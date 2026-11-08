package com.sixik.gpu.app.terrain;

/** Модель тика движка: проверка видимых чанков, бюджет работ и дополнительные проходы после проверки плотностей. */
public final class TerrainGameLoop {

    /** Последняя ревизия генерации каждого из 4225 чанков; -1 означает ещё не обработан. */
    private final long[] revisions = new long[TerrainBatch.VISIBLE_CHUNKS];
    /** Индексы выбранных чанков; память выделяется один раз. */
    private final int[] selected;
    /** Рабочая порция с постоянной длиной массивов. */
    private final TerrainBatch batch;
    /** Общее число законченных тиков. */
    private long tick;
    /** Число реальных вызовов генератора в последнем тике. */
    private int lastCalls;

    /**
     * Создаёт метаданные видимого окна.
     * @param batch бюджет чанков одного тика и геометрия образцов
     */
    public TerrainGameLoop(TerrainBatch batch) {
        this.batch = batch;
        selected = new int[batch.chunkX.length];
        java.util.Arrays.fill(revisions, -1);
    }

    /**
     * Проверяет всё окно, выбирает порцию и вызывает генератор до maxCalls раз.
     * Дополнительный проход с другим seed разрешён только если классификация результатов
     * нашла и воздух, и породу. Это модель проверок density/caves, а не полный генератор Minecraft.
     * Глобальная ревизия меняется после полного обхода окна, моделируя движение/инвалидацию мира.
     *
     * @param executor исполнитель, привязанный к этой порции
     * @param maxCalls максимум проходов за тик, [1, 3]
     * @return контрольная сумма классификации и количества вызовов
     * @throws IllegalArgumentException если максимум проходов вне диапазона
     * @throws IllegalStateException если не удалось набрать порцию из видимого окна
     */
    public long tick(TerrainExecutor executor, int maxCalls) {
        if (maxCalls < 1 || maxCalls > 3) {
            throw new IllegalArgumentException("maxCalls must be in [1, 3]");
        }
        int first = (int) ((tick * selected.length) % TerrainBatch.VISIBLE_CHUNKS);
        int count = 0;
        long check = 0;
        for (int scan = 0; scan < TerrainBatch.VISIBLE_CHUNKS; scan++) {
            int index = (first + scan) % TerrainBatch.VISIBLE_CHUNKS;
            int x = index % TerrainBatch.VIEW_DIAMETER - TerrainBatch.VIEW_RADIUS;
            int z = index / TerrainBatch.VIEW_DIAMETER - TerrainBatch.VIEW_RADIUS;
            boolean visible = Math.abs(x) <= TerrainBatch.VIEW_RADIUS && Math.abs(z) <= TerrainBatch.VIEW_RADIUS;
            long revision = (tick * selected.length + scan) / TerrainBatch.VISIBLE_CHUNKS;
            boolean dirty = revisions[index] <= revision;
            check += visible && dirty ? 1 : 0;
            if (visible && dirty && count < selected.length) {
                selected[count] = index;
                batch.chunkX[count] = x;
                batch.chunkZ[count] = z;
                count++;
            }
        }
        if (count != selected.length) {
            throw new IllegalStateException("Chunk budget not filled");
        }
        lastCalls = 0;
        for (int pass = 0; pass < maxCalls; pass++) {
            batch.seed = TerrainBatch.WORLD_SEED + pass * 0x632be5ab;
            float[] density = executor.compute();
            lastCalls++;
            int solid = 0;
            // Полная классификация результата нужна хостовой логике и входит в стоимость тика.
            for (int i = 0; i < density.length; i++) {
                solid += density[i] > 0f ? 1 : 0;
            }
            check = check * 31 + solid;
            if (solid == 0 || solid == density.length) {
                break;
            }
        }
        for (int i = 0; i < selected.length; i++) {
            revisions[selected[i]] = (tick * selected.length + i) / TerrainBatch.VISIBLE_CHUNKS + 1;
        }
        tick++;
        return check * 31 + lastCalls;
    }

    /**
     * Возвращает число выполненных проходов.
     * @return число запусков в последнем тике, от 1 до maxCalls
     */
    public int lastCalls() {
        return lastCalls;
    }
}
