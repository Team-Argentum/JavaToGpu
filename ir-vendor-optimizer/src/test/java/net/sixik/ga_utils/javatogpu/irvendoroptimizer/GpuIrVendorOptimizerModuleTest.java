package net.sixik.ga_utils.javatogpu.irvendoroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrOptimizationProposal;
import net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrOptimizationProposalDecision;
import net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrOptimizationProposalProvider;
import net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrOptimizationProposalRequest;
import net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrProposalRuntimeBridgePass;
import net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrVendorOptimizationProposalProvider;
import net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrVendorOptimizationProposalRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationPass;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrVendorOptimizerModuleTest {

    @Test
    void vendorProviderIsLoadedOnlyThroughVendorSpi() {
        List<GpuIrVendorOptimizationProposalProvider> vendorProviders = ServiceLoader
                .load(GpuIrVendorOptimizationProposalProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(provider -> provider.getClass().getName().startsWith(
                        "net.sixik.ga_utils.javatogpu.irvendoroptimizer"))
                .toList();
        List<GpuIrOptimizationProposalProvider> neutralProviders = ServiceLoader
                .load(GpuIrOptimizationProposalProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(provider -> provider.getClass().getName().startsWith(
                        "net.sixik.ga_utils.javatogpu.irvendoroptimizer"))
                .toList();
        List<GpuRuntimeIrOptimizationPass> runtimePasses = ServiceLoader
                .load(GpuRuntimeIrOptimizationPass.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(pass -> pass.getClass().getName().startsWith(
                        "net.sixik.ga_utils.javatogpu.irvendoroptimizer"))
                .toList();

        assertEquals(1, vendorProviders.size());
        assertInstanceOf(GpuIrNoOpVendorOptimizationProposalProvider.class, vendorProviders.get(0));
        assertTrue(neutralProviders.isEmpty());
        assertTrue(runtimePasses.isEmpty());
    }

    @Test
    void modulePublishesOnlyVendorProviderDescriptorEntry() throws Exception {
        List<String> vendorDescriptor = serviceDescriptorLines(GpuIrVendorOptimizationProposalProvider.class.getName());
        List<String> neutralDescriptor = serviceDescriptorLines(GpuIrOptimizationProposalProvider.class.getName());
        List<String> runtimeDescriptor = serviceDescriptorLines(GpuRuntimeIrOptimizationPass.class.getName());

        assertEquals(List.of(GpuIrNoOpVendorOptimizationProposalProvider.class.getName()), vendorDescriptor);
        assertTrue(neutralDescriptor.stream().noneMatch(value -> value.contains("irvendoroptimizer")));
        assertTrue(runtimeDescriptor.stream().noneMatch(value -> value.contains("irvendoroptimizer")));
    }

    @Test
    void registryAdapterRunsNoOpVendorProviderWithoutSelectingOptimizedIr() {
        IrGpuArtifact original = artifact("body\n");
        GpuIrVendorOptimizationProposalRegistry registry =
                GpuIrVendorOptimizationProposalRegistry.loadFromServiceLoader();

        GpuIrOptimizationProposal proposal = registry.asProposalProvider().propose(new GpuIrOptimizationProposalRequest(
                original,
                "diagnostic",
                false,
                Map.of(
                        "backendTarget", "OPENCL",
                        "deviceProfile.vendor", "NVIDIA",
                        "deviceProfile.label", "RTX"
                )
        ));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertSame(original, proposal.originalArtifact());
        assertEquals(GpuIrNoOpVendorOptimizationProposalProvider.PROVIDER_ID, proposal.optimizerId());
        assertTrue(proposal.diagnostics().get(0).contains("kept original IR selected"));
    }

    @Test
    void defaultRuntimeBridgeDoesNotAutoLoadVendorProviders() {
        GpuIrProposalRuntimeBridgePass bridge = new GpuIrProposalRuntimeBridgePass();

        assertTrue(bridge.proposalProviders().stream().noneMatch(provider ->
                provider.getClass().getName().startsWith("net.sixik.ga_utils.javatogpu.irvendoroptimizer")));
    }

    private static IrGpuArtifact artifact(String body) {
        IrGpuMethodBody methodBody = IrGpuMethodBody.entry(
                "test.Kernel.run",
                "run",
                body,
                List.of()
        );
        IrGpuModule module = new IrGpuModule("test.Kernel.run", "run", List.of(), List.of(), List.of(methodBody));
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                module,
                List.of(IrGpuBackendOutput.openClSource("test.cl")),
                "opencl",
                "off"
        );
    }

    private static List<String> serviceDescriptorLines(String serviceName) throws Exception {
        String resourceName = "META-INF/services/" + serviceName;
        ClassLoader classLoader = GpuIrVendorOptimizerModuleTest.class.getClassLoader();
        try (java.io.InputStream stream = classLoader.getResourceAsStream(resourceName)) {
            if (stream == null) {
                return List.of();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isBlank())
                        .filter(line -> !line.startsWith("#"))
                        .toList();
            }
        }
    }
}
