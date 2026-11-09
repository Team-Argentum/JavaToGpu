package demo;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;

/** Минимальное ядро независимого приложения-потребителя. */
public final class Kernels {

    /** Не создаёт экземпляров: ядра статические. */
    private Kernels() {
    }

    /**
     * Вычисляет out[i] = in[i] * factor + 1 для текущего work-item.
     *
     * @param in входной массив длиной не меньше global size
     * @param out выходной массив длиной не меньше global size
     * @param factor множитель без единиц измерения
     */
    @Kernel
    public static void scale(@Global float[] in, @Global float[] out, float factor) {
        int i = Gpu.getGlobalId(0);
        out[i] = in[i] * factor + 1f;
    }
}
