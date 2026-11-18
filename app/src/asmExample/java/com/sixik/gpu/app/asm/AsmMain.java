package com.sixik.gpu.app.asm;

import com.sixik.gpu.api.types.Float4;
import com.sixik.gpu.runtime.JavaToGpu;

/**
 * Пример фронтенда байткода: ядра {@link AsmKernels} вызываются обычным вызовом Java-метода, как и ядра, собранные
 * processor'ом. Запуск: {@code ./gradlew :app:runAsmExample}. На машине с OpenCL ядра идут на устройство, без него - на
 * JVM-fallback; результат сверяется с той же формулой на Java.
 */
public final class AsmMain {

    /** Число пикселей и частиц. */
    private static final int SIZE = 4096;

    /** Допуск сверки: устройство может отличаться от JVM на несколько ULP. */
    private static final float EPS = 1e-5f;

    /** Запрещает создание экземпляров точки входа. */
    private AsmMain() {
    }

    /**
     * Запускает оба ядра примера и сверяет результаты.
     *
     * @param args не используются
     * @throws IllegalStateException если результат ядра отличается от ожидаемого
     */
    public static void main(String[] args) {
        System.out.println("device: " + JavaToGpu.defaultContext().map(c -> c.device().describe()).orElse("none (JVM fallback)"));

        Float4[] colors = new Float4[SIZE];
        float[] gray = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            colors[i] = new Float4((i % 256) / 255f, ((i * 7) % 256) / 255f, ((i * 13) % 256) / 255f, 1f);
        }
        AsmKernels.grayscale(colors, gray, 1.5f);
        for (int i = 0; i < SIZE; i++) {
            Float4 c = colors[i];
            float expected = Math.min(1f, Math.max(0f, (0.2126f * c.x + 0.7152f * c.y + 0.0722f * c.z) * 1.5f));
            check(Math.abs(gray[i] - expected) <= EPS, "grayscale", i, expected, gray[i]);
        }
        System.out.printf("grayscale: OK (%d pixels), first: %.4f %.4f %.4f%n", SIZE, gray[0], gray[1], gray[2]);

        AsmKernels.Particle[] ps = new AsmKernels.Particle[SIZE];
        for (int i = 0; i < SIZE; i++) {
            ps[i] = new AsmKernels.Particle(new Float4(i, 0, 0, 0), new Float4(1, 2, -1, 0), (i % 4) / 4f);
        }
        float dt = 0.125f;
        AsmKernels.step(ps, dt);
        for (int i = 0; i < SIZE; i++) {
            float life0 = (i % 4) / 4f;
            boolean alive = life0 > 0f;
            float x = alive ? i + dt : i;
            float life = alive ? Math.max(0f, life0 - dt) : 0f;
            check(Math.abs(ps[i].pos.x - x) <= EPS && Math.abs(ps[i].life - life) <= EPS, "step", i, x, ps[i].pos.x);
        }
        System.out.println("step: OK, particle 1 -> " + ps[1]);
    }

    private static void check(boolean ok, String kernel, int i, float expected, float actual) {
        if (!ok) {
            throw new IllegalStateException(kernel + ": mismatch at " + i + ": expected " + expected + ", got " + actual);
        }
    }
}
