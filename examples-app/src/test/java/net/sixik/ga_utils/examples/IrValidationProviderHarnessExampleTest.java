package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationProvider;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationProviderHarness;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationProviderHarnessReport;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrValidationProviderHarnessExampleTest {
    @Test
    void rendersIrValidationProviderHarnessWithoutAnnotationProcessorOrGpu() {
        String output = IrValidationProviderHarnessExample.renderIrValidationProviderHarnessPreview();

        assertTrue(output.contains("IR validation provider harness:"), output);
        assertTrue(output.contains("status=completed"), output);
        assertTrue(output.contains("mode=DIAGNOSTIC"), output);
        assertTrue(output.contains("providers="), output);
        assertTrue(output.contains("executions="), output);
        assertTrue(output.contains("entries="), output);
        assertTrue(output.contains("allProvidersCompleted=true"), output);
        assertTrue(output.contains("firstBlocker=none"), output);
        assertTrue(output.contains("no GPU or annotation processor run is required"), output);
    }

    @Test
    void exampleProviderReportsSyntheticHelperAndKernelEntries() {
        GpuIrValidationProviderHarnessReport report = new GpuIrValidationProviderHarness(
                java.util.List.of(new ExampleIrValidationProvider())
        ).runSynthetic();

        assertEquals("completed", report.status());
        assertEquals(2, report.validationEntries().size());
        assertTrue(report.validationEntries().stream()
                .anyMatch(entry -> entry.methodName().equals("kernel") && entry.entryPoint()));
        assertTrue(report.validationEntries().stream()
                .anyMatch(entry -> entry.methodName().equals("helper") && !entry.entryPoint()));
    }

    @Test
    void serviceDescriptorRegistersExampleIrValidationProvider() throws Exception {
        String servicePath = "META-INF/services/" + GpuIrValidationProvider.class.getName();
        Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(servicePath);
        StringBuilder descriptors = new StringBuilder();
        boolean found = false;
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (var input = resource.openStream()) {
                String descriptor = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                descriptors.append(resource).append(System.lineSeparator()).append(descriptor).append(System.lineSeparator());
                found |= descriptor.contains(ExampleIrValidationProvider.class.getName());
            }
        }
        assertTrue(found, descriptors.toString());
    }
}
