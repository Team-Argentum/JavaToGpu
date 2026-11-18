package com.sixik.gpu.app.terrain;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.DeviceFunction;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.annotations.Local;
import com.sixik.gpu.api.annotations.Reduction;
import com.sixik.gpu.api.annotations.WorkGroup;
import com.sixik.gpu.api.types.GpuFloatArray;
import com.sixik.gpu.api.types.GpuIntArray;

/** Общая для CPU и GPU математика: 3D value noise, domain warp и многослойная плотность мира. */
public final class TerrainNoise {

    /** Запрещает создание экземпляров математической библиотеки. */
    private TerrainNoise() {
    }

    /**
     * Хеширует узел решётки в безразмерный диапазон [-1, 1).
     * Переполнение int является частью хеша; младшие 24 бита точно представимы в float.
     *
     * @param x целая координата X решётки
     * @param y целая координата Y решётки
     * @param z целая координата Z решётки
     * @param seed seed мира, произвольный int
     * @return псевдослучайное значение узла
     */
    @DeviceFunction
    public static float lattice(int x, int y, int z, int seed) {
        int h = x * 0x1f123bb5 ^ y * 0x5f356495 ^ z * 0x6c8e9cf5 ^ seed;
        h = (h ^ (h >>> 16)) * 0x7feb352d;
        h = (h ^ (h >>> 15)) * 0x846ca68b;
        h = h ^ (h >>> 16);
        return (h & 0x00ffffff) * 0x1.0p-23f - 1f;
    }

    /**
     * Вычисляет quintic fade, используемый в шуме Перлина: f(t)=6t^5-15t^4+10t^3.
     * Первая и вторая производные на границах равны нулю.
     *
     * @param t безразмерная доля внутри ячейки, [0, 1]
     * @return вес интерполяции, [0, 1]
     */
    @DeviceFunction
    public static float fade(float t) {
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    /**
     * Интерполирует восемь хешированных углов ячейки с quintic-весами по трём осям.
     * Формула: сумма h(i,j,k)*wx(i)*wy(j)*wz(k), где веса — fade доли координаты
     * либо 1-fade, а h — {@link #lattice}. Это value noise, а не gradient Perlin.
     *
     * @param x конечная координата X в единицах решётки, |x| меньше 2^24
     * @param y конечная координата Y в единицах решётки, |y| меньше 2^24
     * @param z конечная координата Z в единицах решётки, |z| меньше 2^24
     * @param seed seed мира
     * @return безразмерный шум приблизительно в [-1, 1]
     */
    @DeviceFunction
    public static float noise(float x, float y, float z, int seed) {
        int ix = (int) x;
        int iy = (int) y;
        int iz = (int) z;
        if (x < ix) {
            ix--;
        }
        if (y < iy) {
            iy--;
        }
        if (z < iz) {
            iz--;
        }
        float u = fade(x - ix);
        float v = fade(y - iy);
        float w = fade(z - iz);
        float a = lattice(ix, iy, iz, seed);
        float b = lattice(ix + 1, iy, iz, seed);
        float c = lattice(ix, iy + 1, iz, seed);
        float d = lattice(ix + 1, iy + 1, iz, seed);
        float e = lattice(ix, iy, iz + 1, seed);
        float f = lattice(ix + 1, iy, iz + 1, seed);
        float g = lattice(ix, iy + 1, iz + 1, seed);
        float h = lattice(ix + 1, iy + 1, iz + 1, seed);
        float ab = a + u * (b - a);
        float cd = c + u * (d - c);
        float ef = e + u * (f - e);
        float gh = g + u * (h - g);
        float lower = ab + v * (cd - ab);
        float upper = ef + v * (gh - ef);
        return lower + w * (upper - lower);
    }

    /**
     * Вычисляет плотность D=48*sum(a_k*N(2^k*p+warp,seed+k))- (y-144).
     * Здесь p=(x,y,z)/64; a_k=0.5^(k+1); warp=4*N(p/2,seed xor 0x51ed270b).
     * Положительная плотность означает твёрдую породу. Один образец при шести октавах
     * требует семи вычислений 3D noise, включая warp (56 хешей углов).
     *
     * @param x X в блоках мира, в сценариях около [-520, 520]
     * @param y Y в блоках мира, [0, 360]
     * @param z Z в блоках мира, в сценариях около [-520, 520]
     * @param seed seed мира; меняется между дополнительными проходами
     * @param octaves число октав, [1, 12]
     * @return плотность в условных блоках высоты
     */
    @DeviceFunction
    public static float density(float x, float y, float z, int seed, int octaves) {
        float px = x * 0.015625f;
        float py = y * 0.015625f;
        float pz = z * 0.015625f;
        float warp = 4f * noise(px * 0.5f, py * 0.5f, pz * 0.5f, seed ^ 0x51ed270b);
        float sum = 0f;
        float amplitude = 0.5f;
        for (int octave = 0; octave < octaves; octave++) {
            sum += amplitude * noise(px + warp, py - warp, pz + warp, seed + octave * 1013);
            px *= 2f;
            py *= 2f;
            pz *= 2f;
            amplitude *= 0.5f;
        }
        return sum * 48f - (y - 144f);
    }

    /**
     * Преобразует линейный индекс в координату образца и вычисляет её плотность.
     * Порядок хранения: chunk, Z, Y, X; X меняется быстрее остальных.
     *
     * @param index индекс в выходном плоском массиве
     * @param chunkX X каждого чанка, в чанках
     * @param chunkZ Z каждого чанка, в чанках
     * @param horizontal число образцов по X и Z на чанк
     * @param vertical число образцов по Y на чанк
     * @param stepXZ шаг по горизонтали в блоках
     * @param stepY шаг по вертикали в блоках
     * @param seed seed прохода
     * @param octaves число октав, [1, 12]
     * @return плотность одного образца
     */
    @DeviceFunction
    public static float sample(int index, @Global int[] chunkX, @Global int[] chunkZ,
                               int horizontal, int vertical, int stepXZ, int stepY, int seed, int octaves) {
        int perChunk = horizontal * horizontal * vertical;
        int chunk = index / perChunk;
        return sampleIn(index % perChunk, chunkX[chunk], chunkZ[chunk], horizontal, vertical, stepXZ, stepY, seed,
                octaves);
    }

    /**
     * Плотность образца {@code local} внутри чанка с координатами {@code (chunkX, chunkZ)}.
     *
     * @param local индекс образца внутри чанка (X меняется быстрее, затем Y, затем Z)
     * @param chunkX X чанка, в чанках
     * @param chunkZ Z чанка, в чанках
     * @param horizontal число образцов по X и Z на чанк
     * @param vertical число образцов по Y на чанк
     * @param stepXZ шаг по горизонтали в блоках
     * @param stepY шаг по вертикали в блоках
     * @param seed seed прохода
     * @param octaves число октав, [1, 12]
     * @return плотность одного образца
     */
    @DeviceFunction
    public static float sampleIn(int local, int chunkX, int chunkZ,
                                 int horizontal, int vertical, int stepXZ, int stepY, int seed, int octaves) {
        int x = chunkX * 16 + (local % horizontal) * stepXZ;
        int y = ((local / horizontal) % vertical) * stepY;
        int z = chunkZ * 16 + (local / (horizontal * vertical)) * stepXZ;
        return density(x, y, z, seed, octaves);
    }

    /**
     * Генерирует один образец плотности на work-item; размер запуска задаётся явно.
     *
     * @param output выходные плотности, длина равна числу work-item
     * @param chunkX X чанков
     * @param chunkZ Z чанков
     * @param horizontal число горизонтальных образцов
     * @param vertical число вертикальных образцов
     * @param stepXZ горизонтальный шаг в блоках
     * @param stepY вертикальный шаг в блоках
     * @param seed seed прохода
     * @param octaves число октав, [1, 12]
     */
    @Kernel
    public static void generate(@Global float[] output, @Global int[] chunkX, @Global int[] chunkZ,
                                int horizontal, int vertical, int stepXZ, int stepY, int seed, int octaves) {
        int index = Gpu.getGlobalId(0);
        output[index] = sample(index, chunkX, chunkZ, horizontal, vertical, stepXZ, stepY, seed, octaves);
    }

    /**
     * То же, что {@link #generate}, но на массивах, данные которых живут на устройстве между вызовами: плотности
     * остаются там для следующих ядер ({@link #countSolid}) и читаются на хост только по требованию.
     *
     * @param output выходные плотности, длина равна числу work-item
     * @param chunkX X чанков
     * @param chunkZ Z чанков
     * @param horizontal число горизонтальных образцов
     * @param vertical число вертикальных образцов
     * @param stepXZ горизонтальный шаг в блоках
     * @param stepY вертикальный шаг в блоках
     * @param seed seed прохода
     * @param octaves число октав, [1, 12]
     */
    @Kernel
    public static void generateResident(GpuFloatArray output, GpuIntArray chunkX, GpuIntArray chunkZ,
                                        int horizontal, int vertical, int stepXZ, int stepY, int seed, int octaves) {
        int index = Gpu.getGlobalId(0);
        int perChunk = horizontal * horizontal * vertical;
        int chunk = index / perChunk;
        output.set(index, sampleIn(index % perChunk, chunkX.get(chunk), chunkZ.get(chunk), horizontal, vertical,
                stepXZ, stepY, seed, octaves));
    }

    /**
     * Классификация на устройстве: сколько образцов «порода» ({@code density > 0}). Каждый work-item считает свою
     * частичную сумму, хосту остаётся сложить их - маленький массив вместо всех плотностей.
     *
     * <p>Work-item {@code g} обходит образцы {@code g, g + P, g + 2P, ...} ({@code P} - число work-item'ов): соседние
     * work-item'ы читают соседние элементы, и доступ к памяти объединяется (coalescing). Сплошной блок на work-item
     * был бы втрое медленнее: соседи читали бы адреса на расстоянии блока.</p>
     *
     * @param partial частичные суммы, по одной на work-item (длина задаёт размер запуска)
     * @param density плотности
     */
    @Kernel
    public static void countSolid(GpuIntArray partial, GpuFloatArray density) {
        int g = Gpu.getGlobalId(0);
        int stride = partial.length();
        int n = density.length();
        int count = 0;
        for (int i = g; i < n; i += stride) {
            if (density.get(i) > 0f) {
                count++;
            }
        }
        partial.set(g, count);
    }

    /**
     * Слитые {@link #generateResident} и {@link #countSolid} с той же формой запуска, что у {@code countSolid}: work-item
     * {@code g} вычисляет плотности образцов {@code g, g + P, ...} и сразу считает «породу»; плотности не пишутся в
     * память. Опыт для оценки слияния ядер (REWORK_ARCHITECTURE.md, 7.1).
     *
     * @param partial частичные суммы, по одной на work-item (длина задаёт размер запуска)
     * @param chunkX X чанков
     * @param chunkZ Z чанков
     * @param samples число образцов порции
     * @param horizontal число горизонтальных образцов
     * @param vertical число вертикальных образцов
     * @param stepXZ горизонтальный шаг в блоках
     * @param stepY вертикальный шаг в блоках
     * @param seed seed прохода
     * @param octaves число октав, [1, 12]
     */
    @Kernel
    public static void countSolidFusedStrided(GpuIntArray partial, GpuIntArray chunkX, GpuIntArray chunkZ, int samples,
                                              int horizontal, int vertical, int stepXZ, int stepY, int seed,
                                              int octaves) {
        int g = Gpu.getGlobalId(0);
        int stride = partial.length();
        int perChunk = horizontal * horizontal * vertical;
        int count = 0;
        for (int i = g; i < samples; i += stride) {
            int chunk = i / perChunk;
            if (sampleIn(i % perChunk, chunkX.get(chunk), chunkZ.get(chunk), horizontal, vertical, stepXZ, stepY, seed,
                    octaves) > 0f) {
                count++;
            }
        }
        partial.set(g, count);
    }

    /**
     * Число образцов «порода» ({@code density > 0}) порции - свёртка: последовательный цикл, который рантайм исполняет
     * на устройстве параллельно (work-item на образец, суммы групп в local-памяти), плотности не пишутся в память.
     *
     * @param chunkX X чанков
     * @param chunkZ Z чанков
     * @param samples число образцов порции
     * @param horizontal число горизонтальных образцов
     * @param vertical число вертикальных образцов
     * @param stepXZ горизонтальный шаг в блоках
     * @param stepY вертикальный шаг в блоках
     * @param seed seed прохода
     * @param octaves число октав, [1, 12]
     * @return число образцов с положительной плотностью
     */
    @Reduction
    public static int solidCount(GpuIntArray chunkX, GpuIntArray chunkZ, int samples, int horizontal, int vertical,
                                 int stepXZ, int stepY, int seed, int octaves) {
        int perChunk = horizontal * horizontal * vertical;
        int count = 0;
        for (int i = 0; i < samples; i++) {
            int chunk = i / perChunk;
            if (sampleIn(i % perChunk, chunkX.get(chunk), chunkZ.get(chunk), horizontal, vertical, stepXZ, stepY, seed,
                    octaves) > 0f) {
                count++;
            }
        }
        return count;
    }

    /** Размер work-group ядра {@link #countSolidFusedGroups}. */
    public static final int FUSED_GROUP = 256;

    /**
     * Слитые {@link #generateResident} и {@link #countSolid} с формой запуска {@code generate}: work-item на образец,
     * «порода» суммируется деревом в local-памяти, {@code partial[группа]} - сумма группы. Запуск - число образцов,
     * округлённое вверх до {@link #FUSED_GROUP}. Опыт для оценки слияния ядер (REWORK_ARCHITECTURE.md, 7.1).
     *
     * @param partial суммы групп, длина не меньше числа групп
     * @param chunkX X чанков
     * @param chunkZ Z чанков
     * @param samples число образцов порции
     * @param horizontal число горизонтальных образцов
     * @param vertical число вертикальных образцов
     * @param stepXZ горизонтальный шаг в блоках
     * @param stepY вертикальный шаг в блоках
     * @param seed seed прохода
     * @param octaves число октав, [1, 12]
     */
    @Kernel
    @WorkGroup(x = FUSED_GROUP)
    public static void countSolidFusedGroups(GpuIntArray partial, GpuIntArray chunkX, GpuIntArray chunkZ, int samples,
                                             int horizontal, int vertical, int stepXZ, int stepY, int seed,
                                             int octaves) {
        @Local int[] tile = new int[FUSED_GROUP];
        int index = Gpu.getGlobalId(0);
        int lid = Gpu.getLocalId(0);
        int solid = 0;
        if (index < samples) {
            int perChunk = horizontal * horizontal * vertical;
            int chunk = index / perChunk;
            if (sampleIn(index % perChunk, chunkX.get(chunk), chunkZ.get(chunk), horizontal, vertical, stepXZ, stepY,
                    seed, octaves) > 0f) {
                solid = 1;
            }
        }
        tile[lid] = solid;
        Gpu.barrier();
        for (int s = FUSED_GROUP / 2; s > 0; s >>= 1) {
            if (lid < s) {
                tile[lid] += tile[lid + s];
            }
            Gpu.barrier();
        }
        if (lid == 0) {
            partial.set(Gpu.getGroupId(0), tile[0]);
        }
    }
}
