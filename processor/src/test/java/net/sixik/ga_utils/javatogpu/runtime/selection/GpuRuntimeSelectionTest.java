package net.sixik.ga_utils.javatogpu.runtime.selection;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendHookRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeApiVersion;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackend;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendDeviceSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendPolicy;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendSelectionOrchestrator;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscovery;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeSelectionResult;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GpuRuntimeSelectionTest {

    @Test
    void selectsBorrowedBackendThroughDomainFacade() {
        GpuRuntimeBackend backend = new MockBackend("Mock OpenCL");
        GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
                .preferBorrowedBackend(backend)
                .build();

        GpuRuntimeSelectionResult result = GpuRuntimeSelection.trySelect(policy);

        assertTrue(result.matched());
        assertSame(backend, result.selection().backend());
        assertEquals("Mock OpenCL", result.selection().report().backendName());
    }

    @Test
    void rootBackendSelectionOrchestratorDelegatesToSelectionSupport() {
        GpuRuntimeBackend backend = new MockBackend("Mock OpenCL");
        GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
                .preferBorrowedBackend(backend)
                .build();

        GpuRuntimeSelectionResult rootResult = GpuRuntimeBackendSelectionOrchestrator.select(policy);
        GpuRuntimeSelectionResult supportResult = GpuRuntimeBackendSelectionSupport.select(policy);

        assertTrue(rootResult.matched());
        assertTrue(supportResult.matched());
        assertSame(backend, rootResult.selection().backend());
        assertSame(backend, supportResult.selection().backend());
        assertEquals(supportResult.selection().report().backendName(), rootResult.selection().report().backendName());
        assertEquals(supportResult.candidateDecisions().size(), rootResult.candidateDecisions().size());
    }

    @Test
    void exposesPlannedUnavailableDiscoveryThroughDomainFacade() {
        GpuRuntimeDeviceDiscoveryResult result = GpuRuntimeSelection.plannedUnavailable(GpuBackendTarget.VULKAN);

        assertEquals(GpuBackendTarget.VULKAN, result.backendTarget());
        assertFalse(result.discoveryAvailable());
        assertEquals("backend-device-discovery-not-implemented", result.firstBlocker());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.contains("VULKAN")));
    }

    @Test
    void rootDeviceDiscoveryFacadeDelegatesToSelectionSupport() {
        GpuRuntimeDeviceDiscoveryResult rootResult = GpuRuntimeDeviceDiscovery.plannedUnavailable(
                GpuBackendTarget.METAL,
                GpuBackendHookRegistry.empty()
        );
        GpuRuntimeDeviceDiscoveryResult supportResult = GpuRuntimeDeviceDiscoverySupport.plannedUnavailable(
                GpuBackendTarget.METAL,
                GpuBackendHookRegistry.empty()
        );

        assertEquals(supportResult.backendTarget(), rootResult.backendTarget());
        assertEquals(supportResult.discoveryAvailable(), rootResult.discoveryAvailable());
        assertEquals(supportResult.firstBlocker(), rootResult.firstBlocker());
        assertEquals(supportResult.diagnostics(), rootResult.diagnostics());
    }

    @Test
    void rootBackendDeviceOrchestratorDelegatesToSelectionSupport() {
        GpuRuntimeBackend backend = new MockBackend("Mock OpenCL");
        GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
                .preferBorrowedBackend(backend)
                .build();
        GpuRuntimeDeviceDiscoveryCatalog catalog = GpuRuntimeDeviceDiscoveryCatalog.empty();

        GpuRuntimeBackendDeviceSelection rootSelection = GpuRuntimeBackendSelectionOrchestrator
                .selectWithDeviceDiscovery(policy, catalog);
        GpuRuntimeBackendDeviceSelection supportSelection = GpuRuntimeBackendDeviceSelectionSupport
                .selectWithDeviceDiscovery(policy, catalog);

        assertTrue(rootSelection.backendMatched());
        assertTrue(supportSelection.backendMatched());
        assertSame(backend, rootSelection.selectedBackend().orElseThrow().backend());
        assertSame(backend, supportSelection.selectedBackend().orElseThrow().backend());
        assertEquals(supportSelection.status(), rootSelection.status());
        assertEquals(supportSelection.summary(), rootSelection.summary());
    }

    private record MockBackend(String name) implements GpuRuntimeBackend {

        @Override
        public void invoke(GpuKernelInvocation invocation) {
            throw new UnsupportedOperationException("mock backend does not execute kernels");
        }

        @Override
        public GpuRuntimeBackendReport describeCapabilities() {
            return GpuRuntimeBackendReport.available(
                    GpuBackendTarget.OPENCL,
                    name,
                    "mock-device",
                    new GpuRuntimeApiVersion(3, 0),
                    "OpenCL 3.0 mock",
                    Set.of(),
                    1024L,
                    64L,
                    "mock backend for selection tests"
            );
        }
    }
}
