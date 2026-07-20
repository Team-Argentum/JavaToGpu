package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendArtifactHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilationHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendDiscoveryContributor;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendHookRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendInvocationHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLoweringHook;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendHookServiceLoaderExampleTest {

    @Test
    void rendersServiceLoaderHookPreviewWithoutNativeOpenCl() {
        String output = BackendHookServiceLoaderExample.renderServiceLoaderHookPreview();

        assertTrue(output.contains("Backend hook ServiceLoader example:"), output);
        assertTrue(output.contains("loadedExampleHooks=5"), output);
        assertTrue(output.contains("discoveryHookCount=1"), output);
        assertTrue(output.contains("discoveryContribution=examples.backendHook.discovery.backendTarget=OPENCL"), output);
        assertTrue(output.contains("loweringHookStatus=applied"), output);
        assertTrue(output.contains("compilationHookStatus=applied"), output);
        assertTrue(output.contains("invocationHookStatus=applied"), output);
        assertTrue(output.contains("artifactContribution=examples.backendHook.artifact.status=succeeded"), output);
        assertTrue(output.contains("authorizationStatus=read-only-ready"), output);
        assertTrue(output.contains("authorizationExecutableHooks=1"), output);
        assertTrue(output.contains("read-only hooks observe receipts"), output);
    }

    @Test
    void serviceLoaderRegistryFindsExampleBackendHooks() {
        GpuBackendHookRegistry registry = GpuBackendHookRegistry.loadWithServiceLoader();

        long exampleHooks = registry.hooks().stream()
                .map(hook -> hook.extensionId())
                .filter(id -> id.startsWith("examples.backend-hook."))
                .count();

        assertEquals(5, exampleHooks);
        assertTrue(registry.toMarkdown().contains("examples.backend-hook.discovery"));
        assertTrue(registry.toMarkdown().contains("examples.backend-hook.artifact"));
    }

    @Test
    void serviceDescriptorsRegisterExampleBackendHooks() throws Exception {
        assertDescriptorContains(GpuBackendDiscoveryContributor.class, ExampleBackendDiscoveryHook.class);
        assertDescriptorContains(GpuBackendLoweringHook.class, ExampleBackendLoweringHook.class);
        assertDescriptorContains(GpuBackendCompilationHook.class, ExampleBackendCompilationHook.class);
        assertDescriptorContains(GpuBackendInvocationHook.class, ExampleBackendInvocationHook.class);
        assertDescriptorContains(GpuBackendArtifactHook.class, ExampleBackendArtifactHook.class);
    }

    private static void assertDescriptorContains(Class<?> serviceType, Class<?> implementationType) throws Exception {
        String servicePath = "META-INF/services/" + serviceType.getName();
        Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(servicePath);
        StringBuilder descriptors = new StringBuilder();
        boolean found = false;
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (var input = resource.openStream()) {
                String descriptor = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                descriptors.append(resource).append(System.lineSeparator()).append(descriptor).append(System.lineSeparator());
                found |= descriptor.contains(implementationType.getName());
            }
        }
        assertTrue(found, descriptors.toString());
    }
}
