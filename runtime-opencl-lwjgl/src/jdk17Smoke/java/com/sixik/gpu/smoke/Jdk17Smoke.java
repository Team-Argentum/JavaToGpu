package com.sixik.gpu.smoke;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.DeviceFunction;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.codegen.opencl.ClProgram;
import com.sixik.gpu.codegen.opencl.EmitOptions;
import com.sixik.gpu.codegen.opencl.OpenClEmitter;
import com.sixik.gpu.frontend.asm.AsmJit;
import com.sixik.gpu.frontend.asm.ClassSource;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.runtime.CompiledProgram;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.LaunchConfig;

import java.util.Arrays;
import java.util.List;

/**
 * Проверка на JVM 17 (FRONTEND_ASM_RUNTIME.md, S8, шаг B6; задача {@code :runtime-opencl-lwjgl:jdk17Smoke}): путь
 * мода Minecraft 1.18-1.20.4 - байткод методов через {@code frontend-asm}, {@link ClProgram} для чужого рантайма и
 * {@link GpuContext#compileProgram} через бэкенд LWJGL. Собрано с {@code --release 17} и запускается на JDK 17: класс
 * новее 61 или API новее 17 в Java-17-модулях уронят его здесь, а не у игрока.
 *
 * <p>Без OpenCL-устройства проверяются фронтенд и генератор, а запуск пропускается; {@code -Dsmoke.requireDevice=true}
 * делает отсутствие устройства ошибкой. Код выхода {@code 0} - успех.</p>
 */
public final class Jdk17Smoke {

    private Jdk17Smoke() {
    }

    /** Общая функция трёх ядер. */
    @DeviceFunction
    static int sq(int x) {
        return x * x;
    }

    @Kernel
    public static void square(@Global int[] a) {
        int i = Gpu.getGlobalId(0);
        a[i] = sq(a[i]);
    }

    @Kernel
    public static void plusOne(@Global int[] a) {
        int i = Gpu.getGlobalId(0);
        a[i] = a[i] + 1;
    }

    @Kernel
    public static void fill(@Global int[] a) {
        int i = Gpu.getGlobalId(0);
        a[i] = sq(i);
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    public static void main(String[] args) {
        String expectJava = System.getProperty("smoke.expectJava");
        int feature = Runtime.version().feature();
        System.out.println("JVM " + Runtime.version() + " (" + System.getProperty("java.vendor") + ")");
        check(expectJava == null || Integer.parseInt(expectJava) == feature,
                "expected Java " + expectJava + ", running on " + feature);

        String owner = Jdk17Smoke.class.getName().replace('.', '/');
        List<String> names = List.of("square", "plusOne", "fill");
        List<AsmJit.Entry> entries = names.stream().map(n -> AsmJit.Entry.of(owner, n, "([I)V")).toList();
        AsmJit.Result jit = AsmJit.compile(new AsmJit.Unit(ClassSource.of(Jdk17Smoke.class.getClassLoader()), null,
                entries), AsmJit.Options.DEFAULT);
        check(jit.ok(), "frontend-asm: " + jit.diagnostics());
        System.out.println("frontend-asm: " + jit.kernels().size() + " kernels in " + jit.elapsed().toMillis()
                + " ms, moduleId " + jit.moduleId().substring(0, 12));

        Result<ClProgram> cl = OpenClEmitter.program(jit.module(), EmitOptions.RELEASE);
        check(cl.isOk(), "codegen: " + cl.diagnostics());
        for (ClProgram.KernelAbi k : cl.value().kernels()) {
            System.out.println("ClProgram: " + k.name() + " (" + k.debugName() + "), " + k.params().size() + " args");
        }
        check(cl.value().kernels().size() == 3, "three kernels expected");

        Result<GpuContext> opened = GpuContext.builder().ignoreEnvironment().tryBuild();
        if (!opened.isOk()) {
            check(!Boolean.getBoolean("smoke.requireDevice"), "no OpenCL device: " + opened.diagnostics());
            System.out.println("no OpenCL device, the launch is skipped: " + opened.diagnostics());
            return;
        }
        try (GpuContext ctx = opened.value()) {
            System.out.println("device: " + ctx.device().describe());
            Result<CompiledProgram> p = ctx.compileProgram(jit.module());
            check(p.isOk(), "compileProgram: " + p.diagnostics());
            int n = 1000;
            int[] a = new int[n];
            int[] expected = new int[n];
            for (int i = 0; i < n; i++) {
                a[i] = i - 7;
                expected[i] = (i - 7) * (i - 7) + 1;
            }
            for (int k = 0; k < 2; k++) {
                Result<?> r = ctx.launch(p.value().kernel(jit.kernels().get(k).function()), LaunchConfig.of1D(n),
                        (Object) a);
                check(r.isOk(), names.get(k) + ": " + r.diagnostics());
            }
            check(Arrays.equals(expected, a), "square + plusOne: " + Arrays.toString(Arrays.copyOf(a, 8)));
            Result<?> r = ctx.launch(p.value().kernel(jit.kernels().get(2).function()), LaunchConfig.of1D(n),
                    (Object) a);
            check(r.isOk(), "fill: " + r.diagnostics());
            for (int i = 0; i < n; i++) {
                check(a[i] == i * i, "fill: a[" + i + "] = " + a[i]);
            }
            System.out.println("compileProgram: 3 kernels ran on the device");
        }
        System.out.println("JDK 17 smoke: OK");
    }
}
