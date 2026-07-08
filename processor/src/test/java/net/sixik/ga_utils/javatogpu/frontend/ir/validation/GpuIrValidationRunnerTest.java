package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrValidationRunnerTest {
    @Test
    void skipsProvidersWhenValidationIsOff() {
        CapturingProvider provider = new CapturingProvider();
        GpuIrValidationRunner runner = new GpuIrValidationRunner(List.of(provider), GpuIrValidationMode.OFF);

        runner.run(method("kernel"), List.of(method("helper")), List.of());

        assertTrue(provider.requests.isEmpty());
    }

    @Test
    void runsProvidersForHelpersThenKernelWithRequestedMode() {
        CapturingProvider provider = new CapturingProvider();
        GpuIrValidationRunner runner = new GpuIrValidationRunner(List.of(provider), GpuIrValidationMode.STRICT_OPTIMIZER);

        runner.run(method("kernel"), List.of(method("helper")), List.of());

        assertEquals(2, provider.requests.size());
        assertEquals("helper", provider.requests.get(0).method().irMethod().name());
        assertEquals("kernel", provider.requests.get(1).method().irMethod().name());
        assertEquals(GpuIrValidationMode.STRICT_OPTIMIZER, provider.requests.get(0).mode());
        assertEquals(GpuIrValidationMode.STRICT_OPTIMIZER, provider.requests.get(1).mode());
        assertTrue(provider.requests.get(1).entryPoint());
    }

    private GpuIrCompiledMethod method(String name) {
        return new GpuIrCompiledMethod(parsedMethod(name), new GpuIrMethod(name, List.of()), "jtg_" + name, List.of());
    }

    private ParsedGpuMethod parsedMethod(String name) {
        return new ParsedGpuMethod(
                "Owner",
                "test.Owner",
                name,
                "void",
                List.of(),
                List.of(),
                List.of(),
                null,
                false,
                List.of(),
                null,
                "",
                null,
                false
        );
    }

    private static final class CapturingProvider implements GpuIrValidationProvider {
        private final List<GpuIrValidationRequest> requests = new ArrayList<>();

        @Override
        public void validate(GpuIrValidationRequest request) {
            requests.add(request);
        }
    }
}
