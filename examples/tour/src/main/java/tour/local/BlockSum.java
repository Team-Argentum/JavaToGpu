package tour.local;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.annotations.Local;
import com.sixik.gpu.api.annotations.WorkGroup;
import com.sixik.gpu.runtime.GpuException;
import tour.Show;

/**
 * Урок 5. Work-group, local-память и барьеры.
 *
 * <p>Work-item'ы объединяются в группы ({@code @WorkGroup(x = 256)} - ровно по 256; global size обязан делиться на
 * 256). У группы есть быстрая общая {@code @Local}-память, а {@code Gpu.barrier()} ждёт, пока до этой точки дойдут все
 * work-item'ы группы - только после этого можно читать то, что записали соседи. {@code getLocalId(0)} - номер внутри
 * группы, {@code getGroupId(0)} - номер группы.</p>
 *
 * <p>Здесь группа складывает свои 256 чисел деревом: на каждом шаге половина work-item'ов прибавляет значение соседа,
 * за 8 шагов сумма группы оказывается в {@code tile[0]}. Хосту остаётся сложить суммы групп.</p>
 *
 * <p>Барьер, до которого доходят не все work-item'ы группы, - зависание или неопределённое поведение: компилятор
 * предупреждает о таком барьере ({@code GPU0130}). Ядро с барьером или local-памятью нельзя исполнить на JVM
 * ({@code RT-F01}): нужен OpenCL. Для проверки без железа есть интерпретатор IR в {@code test-support} - см.
 * {@code BlockSumTest}.</p>
 *
 * <p>Обычно такую свёртку писать вручную не нужно: {@code @Reduction} (урок {@code reduce}) делает то же сам.</p>
 */
public final class BlockSum {

    /** Размер группы. */
    public static final int GROUP = 256;

    private BlockSum() {
    }

    /**
     * Сумма каждой группы из {@link #GROUP} элементов.
     *
     * @param in      вход, длина кратна {@link #GROUP}
     * @param partial сумма группы {@code g} - в {@code partial[g]}
     */
    @Kernel
    @WorkGroup(x = GROUP)
    public static void groupSums(@Global int[] in, @Global int[] partial) {
        @Local int[] tile = new int[GROUP];
        int lid = Gpu.getLocalId(0);
        tile[lid] = in[Gpu.getGlobalId(0)];
        Gpu.barrier();                               // все записали свой элемент
        for (int step = GROUP / 2; step > 0; step >>= 1) {
            if (lid < step) {
                tile[lid] += tile[lid + step];
            }
            Gpu.barrier();                           // шаг закончен у всех - можно читать результат
        }
        if (lid == 0) {
            partial[Gpu.getGroupId(0)] = tile[0];
        }
    }

    /** Программа урока. */
    public static void run() {
        int n = GROUP * 4096;
        int[] in = new int[n];
        long want = 0;
        for (int i = 0; i < n; i++) {
            in[i] = (i * 7) % 100;
            want += in[i];
        }
        int[] partial = new int[n / GROUP];
        try {
            groupSums(in, partial);
        } catch (GpuException e) {
            System.out.println("This lesson needs an OpenCL device: kernels with barriers or local memory");
            System.out.println("cannot run on the JVM. The driver said: " + e.getMessage());
            return;
        }
        long got = 0;
        for (int p : partial) {
            got += p;
        }
        Show.head("group sums", partial, 6);
        System.out.println("total " + got + " (expected " + want + ")");
        Show.check(got == want, "block sums add up to the total");
    }
}
