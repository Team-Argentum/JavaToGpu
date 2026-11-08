package com.sixik.gpu.test;

import com.sixik.gpu.ir.interpreter.samples.LocalKernels;
import com.sixik.gpu.ir.interpreter.samples.SampleArgs;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import com.sixik.gpu.test.KernelSample.Leg;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code GpuAssert} на <b>каждом</b> ядре каталога образцов: JVM и интерпретатор совпадают, ядра с барьером сверяются с
 * ожидаемым, посчитанным на хосте. Ветвь устройства здесь отключена (тег {@code device} - {@link SampleDeviceTest}).
 */
class SampleCoverageTest {

    private static final long SEED = 20260101L;

    /** Образец для обычного ядра: случайные аргументы по сигнатуре, размер запуска - длина первого массива. */
    static KernelSample generic(SampleKernels.Kernel k) {
        // как в RuntimeDeviceTest: образцы пишут по нескольку ячеек на work-item, поэтому запуск короткий (16 work-item-ов);
        // ядро с обязательной группой запускается 2D 8x4
        if (k.label().equals("SmallTypeKernels.storage")) {
            return smallTypes();
        }
        var m = k.module();
        boolean grouped = m.fReqWorkGroup(m.functionCount() - 1, 0) > 0;
        KernelSample.Range range = grouped ? KernelSample.Range.of2D(8, 4) : KernelSample.Range.of1D(16, 1); // группа 1: размер группы по умолчанию выбирает драйвер, а образцы его читают
        return KernelSample.named(k.label() + " seed " + SEED).args(SampleArgs.random(k, SEED)).range(range).build();
    }

    /** Массивы {@code byte/short/char/boolean} {@code SampleArgs} не строит (в IR они int-подобные), собираем руками. */
    private static KernelSample smallTypes() {
        int n = 16;
        java.util.Random r = new java.util.Random(SEED);
        byte[] bytes = new byte[n];
        short[] shorts = new short[n];
        char[] chars = new char[n];
        boolean[] flags = new boolean[n];
        r.nextBytes(bytes);
        for (int i = 0; i < n; i++) {
            shorts[i] = (short) r.nextInt();
            chars[i] = (char) r.nextInt();
            flags[i] = r.nextBoolean();
        }
        bytes[0] = Byte.MIN_VALUE;
        bytes[1] = Byte.MAX_VALUE;
        shorts[0] = Short.MIN_VALUE;
        chars[0] = Character.MAX_VALUE;
        return KernelSample.named("small types").args(() -> new Object[]{bytes.clone(), shorts.clone(), chars.clone(),
                flags.clone(), new int[n * 8]}).build();
    }

    static boolean isGroupKernel(SampleKernels.Kernel k) {
        return k.owner() == LocalKernels.class;
    }

    @TestFactory
    Stream<DynamicTest> everyKernelAgreesBetweenJvmAndInterpreter() {
        return SampleKernels.all().stream().map(k -> DynamicTest.dynamicTest(k.label(), () -> {
            KernelSample sample = isGroupKernel(k) ? LocalSamples.all().get(k.label()) : generic(k);
            GpuAssert.Report report = GpuAssert.sameResult(k.owner(), k.method().getName(), sample, null);
            assertTrue(report.ranOn(Leg.INTERPRETER), k.label());
            if (isGroupKernel(k)) {
                // JVM пропущена у ядер с барьером/local (reduceSum, transpose, scan, histogram), остальные идут и на ней
                assertEquals(report.ranOn(Leg.JVM), !report.skipped().containsKey(Leg.JVM), report.toString());
            } else {
                assertTrue(report.ranOn(Leg.JVM), "ядро без барьера исполняется и на JVM: " + k.label());
            }
        }));
    }

    @Test
    void catalogueIsNotEmptyAndSpansAllKinds() {
        List<SampleKernels.Kernel> all = SampleKernels.all();
        assertTrue(all.size() >= 30, "каталог образцов: " + all.size());
        Set<String> owners = new TreeSet<>();
        all.forEach(k -> owners.add(k.owner().getSimpleName()));
        assertTrue(owners.containsAll(Set.of("IntKernels", "FloatKernels", "VectorKernels", "StructKernels",
                "PointerKernels", "LocalKernels", "MemoryKernels")), owners.toString());
    }

    @Test
    void everyGroupKernelHasAHostComputedSample() {
        Set<String> expected = new TreeSet<>();
        SampleKernels.all().stream().filter(SampleCoverageTest::isGroupKernel).forEach(k -> expected.add(k.label()));
        assertFalse(expected.isEmpty());
        assertEquals(expected, new TreeSet<>(LocalSamples.all().keySet()),
                "для нового ядра LocalKernels нужен образец в LocalSamples");
        for (KernelSample s : LocalSamples.all().values()) {
            assertTrue(s.expected() != null, s.name() + ": ядро группы без ожидаемого результата не проверить");
        }
    }
}
