package com.sixik.gpu.app;

import com.sixik.gpu.runtime.GpuEvent;
import com.sixik.gpu.runtime.JavaToGpu;

import java.nio.file.Path;
import java.util.Arrays;

/** Запускает тестовый пример и проверяет результат обычного вызова GPU-ядра. */
public final class Main {

    /** Количество элементов по умолчанию; положительное целое число. */
    private static final int DEFAULT_SIZE = 1024;

    /** Каталог выгрузки сгенерированных {@code .cl} относительно рабочего каталога ({@code app/} под Gradle). */
    private static final Path DEFAULT_DUMP_DIRECTORY = Path.of("build", "opencl");

    /** Запрещает создание экземпляров точки входа. */
    private Main() {
    }

    /**
     * Вычисляет экспериментальное vectorAdd на OpenCL-устройстве либо через JVM-fallback и сверяет все элементы.
     *
     * @param args необязательное положительное количество элементов, по умолчанию 1024
     * @throws IllegalArgumentException если аргументов больше одного или размер не является положительным целым
     * @throws IllegalStateException если результат ядра отличается от ожидаемого
     */
    public static void main(String[] args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: app [positive-size]");
        }
        int size = args.length == 0 ? DEFAULT_SIZE : Integer.parseInt(args[0]);
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be positive");
        }

        JavaToGpu.configureDefault(builder -> builder
                .listener(Main::printEvent)
                .dumpSources(DEFAULT_DUMP_DIRECTORY));
        try {
            float[] left = new float[size];
            float[] right = new float[size];
            float[] output = new float[size];
            for (int i = 0; i < size; i++) {
                left[i] = i % 256;
                right[i] = 0.5f;
            }

            Kernels.BlockPos[] pos = new Kernels.BlockPos[size];
            Kernels.BlockPos[] out = new Kernels.BlockPos[size];
            for (int i = 0; i < size; i++) {
                Kernels.BlockPos p = new Kernels.BlockPos();

                p.x = i % 15;
                p.y = i % 256;
                p.z = 15 - i % 15;

                pos[i] = p;
                out[i] = new Kernels.BlockPos();
            }

            Kernels.vectorAdd(left, right, output);

            Kernels.calculatePositionRandom(pos, out);
            for (Kernels.BlockPos blockPos : out) {
                System.out.println(blockPos);
            }


            for (int i = 0; i < size; i++) {
                // Для заданных входов a >= 0 и b = 0.5 формула ядра равна 2*a + 1 + CONST точно в float.
                float expected = 2 * left[i] + 1f + Kernels.CONST;
                if (output[i] != expected) {
                    throw new IllegalStateException("Mismatch at " + i + ": expected " + expected + ", got " + output[i]);
                }
            }
            System.out.printf("vectorAdd: OK (%d elements)%n", size);
            System.out.println("First results: " + Arrays.toString(Arrays.copyOf(output, Math.min(size, 8))));
        } finally {
            JavaToGpu.resetDefault();
        }
    }

    /**
     * Выводит выбор устройства, факт запуска на нём или причину перехода на JVM.
     *
     * @param event событие runtime с диагностикой запуска
     */
    private static void printEvent(GpuEvent event) {
        if (event.kind() == GpuEvent.Kind.DEVICE_SELECTED || event.kind() == GpuEvent.Kind.KERNEL
                || event.kind() == GpuEvent.Kind.FALLBACK) {
            System.out.printf("[%s] %s %s%n", event.kind(), event.subject(), event.detail());
        }
    }
}
