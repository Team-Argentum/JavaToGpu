package com.sixik.gpu.app;

import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.test.GPUTest;
import com.sixik.gpu.test.GpuAssert;
import com.sixik.gpu.test.KernelSample;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Сверяет пример приложения с настоящим OpenCL-устройством отдельной задачей deviceTest. */
@Tag("device")
class KernelsDeviceTest {

    /**
     * Проверяет все три ветви исполнения и ожидаемый результат; без устройства тест пропускается.
     *
     * @param context доступный OpenCL-контекст, предоставленный расширением GPUTest
     */
    @GPUTest(requireDevice = true)
    void vectorAdd_device_matchesJvmAndInterpreter(GpuContext context) {
        KernelSample sample = KernelSample.named("vector-add")
                .args(() -> new Object[]{new float[]{1f, -2f, 0f, 4f}, new float[]{0.5f, 2f, -1f, 8f}, new float[4]})
                .expected(null, null, new float[]{1.5f, 0f, -1f, 12f})
                .build();
        GpuAssert.Report report = GpuAssert.sameResult(Kernels.class, "vectorAdd", sample, context);
        assertTrue(report.ranOn(KernelSample.Leg.DEVICE), report.toString());
    }
}
