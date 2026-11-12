package tour.buffers;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.runtime.CompiledKernel;
import com.sixik.gpu.runtime.GpuBuffer;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.runtime.LaunchConfig;
import com.sixik.gpu.runtime.LaunchStats;
import com.sixik.gpu.runtime.PreparedLauncher;
import tour.Show;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Locale;

/**
 * Урок 10. Явный API: контекст, буферы, подготовленные запуски, виды без копирования.
 *
 * <p>Обычный вызов {@code Buffers.scale(...)} всё делает сам. Когда нужен контроль, есть явный слой:</p>
 * <ul>
 *   <li>{@code GpuContext} - устройство, кэш компиляции, очередь; создаётся построителем, закрывается
 *       ({@code try}-with-resources);</li>
 *   <li>{@code ctx.compile(module)} и {@code ctx.launch(kernel, config, args...)} - запуск с результатом
 *       {@code Result<LaunchStats>}: время передач и ядра, байты туда и обратно;</li>
 *   <li>{@code GpuBuffer} - память устройства, которой владеете вы: данные остаются там между запусками;</li>
 *   <li>{@code PreparedLauncher} - запуск, у которого всё уже привязано: повторный {@code run} стоит микросекунды,
 *       {@code set(i, v)} меняет аргумент;</li>
 *   <li>{@code readView()} / {@code writeView()} + {@code commitView()} - доступ к памяти буфера на хосте без
 *       копирования в {@code float[]}.</li>
 * </ul>
 * <p>Обычно это не нужно: фасад сам переиспользует подготовленные запуски, а {@code GpuFloatArray} держит данные на
 * устройстве (урок {@code resident}). Явный API нужен, когда важно время жизни памяти или нужна статистика.</p>
 */
public final class Buffers {

    private Buffers() {
    }

    /**
     * @param in  вход
     * @param out выход: {@code in * k + 1}
     * @param k   множитель
     */
    @Kernel
    public static void scale(@Global float[] in, @Global float[] out, float k) {
        int i = Gpu.getGlobalId(0);
        out[i] = in[i] * k + 1f;
    }

    /** Программа урока. */
    public static void run() {
        Result<GpuContext> rc = GpuContext.builder().tryBuild();
        if (!rc.isOk()) {
            System.out.println("This lesson needs an OpenCL device: " + rc.diagnostics());
            return;
        }
        int n = 1 << 20;
        float[] input = new float[n];
        for (int i = 0; i < n; i++) {
            input[i] = i;
        }
        try (GpuContext ctx = rc.value(); JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
            System.out.println("device: " + ctx.device().describe());

            Show.section("compile + launch with statistics");
            CompiledKernel k = ctx.compile(JavaToGpu.loadModule(Buffers.class, "scale")).value();
            float[] out = new float[n];
            LaunchStats st = ctx.launch(k, LaunchConfig.of1D(n), input, out, 2f).value();
            System.out.printf(Locale.ROOT, "kernel %s: up %d B in %.3f ms, kernel %.3f ms, down %d B in %.3f ms%n",
                    k.artifact().entry(), st.bytesUp(), st.uploadNanos() / 1e6, st.kernelNanos() / 1e6, st.bytesDown(),
                    st.readbackNanos() / 1e6);
            System.out.println("(out is overwritten entirely, so only the input was uploaded)");
            Show.check(out[10] == 21f, "launch result");

            Show.section("GpuBuffer: a chain without round trips");
            try (GpuBuffer a = ctx.upload(input);
                 GpuBuffer b = ctx.allocate(Types.F32, n)) {
                ctx.launch(k, LaunchConfig.of1D(n), a, b, 2f);   // b = 2a + 1, на устройстве
                ctx.launch(k, LaunchConfig.of1D(n), b, a, 0.5f); // a = b / 2 + 1 = a + 1.5
                float[] back = new float[n];
                a.read(back);
                Show.head("a after two kernels", back, 4);
                Show.check(back[3] == 4.5f, "buffer chain");

                Show.section("PreparedLauncher: everything bound once");
                try (PreparedLauncher p = JavaToGpu.prepare(Buffers.class, "scale", a, b, 3f)) {
                    Show.time("prepared run (data stays on the device)", Show.millis(20,
                            () -> p.run(LaunchConfig.of1D(n))));
                    p.set(2, 10f);                                // поменять скаляр без новой привязки
                    p.run(LaunchConfig.of1D(n));
                }

                Show.section("views: read and write buffer memory without a float[]");
                MemorySegment view = b.readView();                // b = 10a + 1
                System.out.println("b[3] via readView = " + view.getAtIndex(ValueLayout.JAVA_FLOAT, 3));
                MemorySegment w = a.writeView();
                for (int i = 0; i < n; i++) {
                    w.setAtIndex(ValueLayout.JAVA_FLOAT, i, 1f);  // заполнить вход прямо в закреплённой памяти
                }
                a.commitView();
                ctx.launch(k, LaunchConfig.of1D(n), a, b, 7f);
                System.out.println("b[0] after commitView + launch = " + b.readView().getAtIndex(ValueLayout.JAVA_FLOAT, 0));
                Show.check(b.readView().getAtIndex(ValueLayout.JAVA_FLOAT, 0) == 8f, "views");
            }
        }
    }
}
