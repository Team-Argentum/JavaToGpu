package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilerFeedback;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilerFeedbackProvider;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilerFeedbackHarnessExampleTest {

    @Test
    void rendersCompilerFeedbackHarnessWithoutNativeCompiler() {
        String output = CompilerFeedbackHarnessExample.renderCompilerFeedbackHarnessPreview();

        assertTrue(output.contains("Backend compiler feedback harness:"), output);
        assertTrue(output.contains("status=recorded"), output);
        assertTrue(output.contains("backend=OPENCL"), output);
        assertTrue(output.contains("selectedProvider=examples.compiler-feedback.synthetic"), output);
        assertTrue(output.contains("available=true"), output);
        assertTrue(output.contains("effectiveRegisters=18"), output);
        assertTrue(output.contains("localMemoryBytes=512"), output);
        assertTrue(output.contains("occupancyPermille=875"), output);
        assertTrue(output.contains("no backend compiler or GPU is opened"), output);
    }

    @Test
    void exampleProviderParsesSyntheticCompilerMetrics() {
        GpuBackendCompilerFeedback feedback = new ExampleBackendCompilerFeedbackProvider()
                .inspect(CompilerFeedbackHarnessExample.exampleRequest())
                .orElseThrow();

        assertEquals("examples.compiler-feedback.synthetic", feedback.providerId());
        assertEquals(18, feedback.effectiveRegisterCount());
        assertEquals(512, feedback.localMemoryBytes());
        assertEquals(875, feedback.occupancyPermille());
        assertEquals("matched", feedback.rawFields().get("example.provider"));
    }

    @Test
    void serviceDescriptorRegistersExampleCompilerFeedbackProvider() throws Exception {
        String servicePath = "META-INF/services/" + GpuBackendCompilerFeedbackProvider.class.getName();
        Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(servicePath);
        StringBuilder descriptors = new StringBuilder();
        boolean found = false;
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (var input = resource.openStream()) {
                String descriptor = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                descriptors.append(resource).append(System.lineSeparator()).append(descriptor).append(System.lineSeparator());
                found |= descriptor.contains(ExampleBackendCompilerFeedbackProvider.class.getName());
            }
        }
        assertTrue(found, descriptors.toString());
    }
}
