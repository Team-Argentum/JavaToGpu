package com.sixik.gpu.test;

import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.JavaToGpu;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Расширение {@link GPUTest}: пропуск тестов без устройства и передача {@link GpuContext} параметром метода.
 *
 * <p>Контекст - {@code JavaToGpu.defaultContext()}: общий для процесса, создаётся лениво и закрывается при его
 * завершении; закрывать его в тесте не нужно.</p>
 */
public final class GpuTestExtension implements BeforeEachCallback, ParameterResolver {

    @Override
    public void beforeEach(ExtensionContext context) {
        GPUTest marker = context.getRequiredTestMethod().getAnnotation(GPUTest.class);
        if (marker != null && marker.requireDevice()) {
            Assumptions.assumeTrue(JavaToGpu.defaultContext().isPresent(), "нет OpenCL-устройства");
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext extension) {
        return parameter.getParameter().getType() == GpuContext.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext extension) {
        return JavaToGpu.defaultContext().orElseGet(() -> {
            Assumptions.abort("нет OpenCL-устройства: тест с параметром GpuContext пропущен");
            throw new ParameterResolutionException("недостижимо");
        });
    }
}
