package com.sixik.gpu.test;

import com.sixik.gpu.ir.interpreter.samples.SampleArgs;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.test.KernelSample.Leg;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Сверка каталога образцов с реальным устройством: JVM (или интерпретатор), интерпретатор и OpenCL дают один результат
 * в допуске из IR. Без драйвера тесты пропускаются. Запуск: {@code ./gradlew :test-support:deviceTest}.
 */
@Tag("device")
class SampleDeviceTest {

    @TestFactory
    Stream<DynamicTest> everyComparableKernelAgreesWithTheDevice() {
        return SampleKernels.all().stream()
                .filter(k -> SampleCoverageTest.isGroupKernel(k)
                        ? LocalSamples.comparableOnDevice(k.label())
                        : !SampleArgs.notComparableOnDevice(k.label()))
                .map(k -> DynamicTest.dynamicTest(k.label(), () -> {
                    GpuContext ctx = com.sixik.gpu.runtime.JavaToGpu.defaultContext().orElse(null);
                    org.junit.jupiter.api.Assumptions.assumeTrue(ctx != null, "no OpenCL device");
                    KernelSample sample = SampleCoverageTest.isGroupKernel(k)
                            ? LocalSamples.all().get(k.label()) : SampleCoverageTest.generic(k);
                    GpuAssert.Report report = GpuAssert.sameResult(k.owner(), k.method().getName(), sample, ctx);
                    // ветвь могла быть пропущена только из-за недостающей возможности устройства (RT-D02)
                    assertTrue(report.ranOn(Leg.DEVICE) || report.skipped().containsKey(Leg.DEVICE), report.toString());
                    if (!report.ranOn(Leg.DEVICE)) {
                        System.out.println("skipped on the device: " + report);
                    }
                }));
    }
}
