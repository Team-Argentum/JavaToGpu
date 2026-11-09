package demo;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;

/** Ядро из test source set: проверка testAnnotationProcessor и weaving тестовых классов. */
public final class TestKernels {

    /** Не создаёт экземпляров: ядра статические. */
    private TestKernels() {
    }

    /** @param out массив длиной не меньше global size; каждый элемент получает 7 */
    @Kernel
    public static void fill(@Global int[] out) {
        out[Gpu.getGlobalId(0)] = 7;
    }
}
