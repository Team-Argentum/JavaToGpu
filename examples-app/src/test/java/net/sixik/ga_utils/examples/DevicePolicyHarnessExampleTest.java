package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicy;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DevicePolicyHarnessExampleTest {

    @Test
    void rendersDevicePolicyHarnessWithoutNativeRuntime() {
        String output = DevicePolicyHarnessExample.renderDevicePolicyHarnessPreview();

        assertTrue(output.contains("Runtime device policy harness:"), output);
        assertTrue(output.contains("status=selected"), output);
        assertTrue(output.contains("backend=OPENCL"), output);
        assertTrue(output.contains("candidates=3"), output);
        assertTrue(output.contains("policyCount="), output);
        assertTrue(output.contains("executionCount="), output);
        assertTrue(output.contains("selectedDevice=Synthetic Discrete GPU"), output);
        assertTrue(output.contains("firstBlocker=none"), output);
        assertTrue(output.contains("allPoliciesSucceeded=true"), output);
        assertTrue(output.contains("no OpenCL/CUDA runtime is opened"), output);
    }

    @Test
    void serviceDescriptorRegistersExampleRuntimeDevicePolicy() throws Exception {
        String servicePath = "META-INF/services/" + GpuRuntimeDevicePolicy.class.getName();
        Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(servicePath);
        StringBuilder descriptors = new StringBuilder();
        boolean found = false;
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (var input = resource.openStream()) {
                String descriptor = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                descriptors.append(resource).append(System.lineSeparator()).append(descriptor).append(System.lineSeparator());
                found |= descriptor.contains(ExampleRuntimeDevicePolicy.class.getName());
            }
        }
        assertTrue(found, descriptors.toString());
    }
}
