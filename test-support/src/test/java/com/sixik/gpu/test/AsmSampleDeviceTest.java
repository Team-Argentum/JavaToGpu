package com.sixik.gpu.test;

import com.sixik.gpu.frontend.asm.AsmFrontend;
import com.sixik.gpu.frontend.asm.ClassSource;
import com.sixik.gpu.ir.interpreter.samples.SampleArgs;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.test.KernelSample.Leg;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.lang.invoke.MethodType;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Критерий 17.14-4: каталог образцов на IR, построенном {@code frontend-asm} из class-файлов, - те же ветви JVM,
 * интерпретатор и OpenCL, те же образцы и допуски, что у {@link SampleDeviceTest} для IR {@code frontend-java}.
 * Запуск: {@code ./gradlew :test-support:deviceTest}; без драйвера тесты пропускаются.
 */
@Tag("device")
class AsmSampleDeviceTest {

    @TestFactory
    Stream<DynamicTest> everyComparableKernelAgreesWithTheDeviceOnAsmIr() {
        return SampleKernels.all().stream()
                .filter(k -> SampleCoverageTest.isGroupKernel(k)
                        ? LocalSamples.comparableOnDevice(k.label())
                        : !SampleArgs.notComparableOnDevice(k.label()))
                .map(k -> DynamicTest.dynamicTest(k.label(), () -> {
                    GpuContext ctx = com.sixik.gpu.runtime.JavaToGpu.defaultContext().orElse(null);
                    Assumptions.assumeTrue(ctx != null, "no OpenCL device");
                    String desc = MethodType.methodType(k.method().getReturnType(), k.method().getParameterTypes())
                            .toMethodDescriptorString();
                    AsmFrontend.Result r = AsmFrontend.compile(ClassSource.of(getClass().getClassLoader()),
                            List.of(k.owner().getName()), AsmFrontend.Options.DEFAULT);
                    AsmFrontend.Kernel asm = r.kernels().stream()
                            .filter(x -> x.entry().method().equals(k.method().getName())
                                    && x.entry().descriptor().equals(desc))
                            .findFirst().orElse(null);
                    assertNotNull(asm, k.label() + ": " + r.diagnostics());
                    KernelSample sample = SampleCoverageTest.isGroupKernel(k)
                            ? LocalSamples.all().get(k.label()) : SampleCoverageTest.generic(k);
                    GpuAssert.Report report = GpuAssert.sameResult(k.owner(), k.method().getName(), asm.module(), sample, ctx);
                    assertTrue(report.ranOn(Leg.DEVICE) || report.skipped().containsKey(Leg.DEVICE), report.toString());
                    if (!report.ranOn(Leg.DEVICE)) {
                        System.out.println("skipped on the device: " + report);
                    }
                }));
    }
}
