package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationOutcome;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationPass;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationStage;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizerRegistry;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrProposalRuntimeBridgePassTest {

    @Test
    void runtimeBridgeIsTheOnlyServiceLoadedRuntimePassFromModule() {
        List<GpuRuntimeIrOptimizationPass> passes = ServiceLoader
                .load(GpuRuntimeIrOptimizationPass.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(pass -> pass.getClass().getName().startsWith("net.sixik.ga_utils.javatogpu.iroptimizer"))
                .toList();

        assertEquals(1, passes.size());
        assertInstanceOf(GpuIrProposalRuntimeBridgePass.class, passes.get(0));
        assertEquals(GpuIrProposalRuntimeBridgePass.PASS_VERSION, passes.get(0).passVersion());
    }

    @Test
    void bridgeLoadsProposalProvidersInDeterministicOrder() {
        GpuIrProposalRuntimeBridgePass bridge = new GpuIrProposalRuntimeBridgePass();

        assertEquals(6, bridge.proposalProviders().size());
        assertInstanceOf(GpuIrNoOpProposalProvider.class, bridge.proposalProviders().get(0));
        assertInstanceOf(GpuIrTextCanonicalizationProposalProvider.class, bridge.proposalProviders().get(1));
        assertInstanceOf(GpuIrHelperDependencyDeduplicationProposalProvider.class, bridge.proposalProviders().get(2));
        assertInstanceOf(GpuIrConstantFoldingPreviewProposalProvider.class, bridge.proposalProviders().get(3));
        assertInstanceOf(GpuIrSafeLocalCsePreviewProposalProvider.class, bridge.proposalProviders().get(4));
        assertInstanceOf(GpuIrTypedDeadCodePreviewProposalProvider.class, bridge.proposalProviders().get(5));
    }

    @Test
    void modulePublishesOnlyBackendNeutralProposalProviderDescriptorEntries() throws Exception {
        List<String> neutralDescriptor = serviceDescriptorLines(GpuIrOptimizationProposalProvider.class.getName());
        List<String> vendorDescriptor = serviceDescriptorLines(GpuIrVendorOptimizationProposalProvider.class.getName());

        assertTrue(neutralDescriptor.contains(GpuIrNoOpProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrTextCanonicalizationProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrHelperDependencyDeduplicationProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrConstantFoldingPreviewProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrSafeLocalCsePreviewProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrTypedDeadCodePreviewProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.stream().noneMatch(value -> value.contains("irvendoroptimizer")));
        assertTrue(vendorDescriptor.isEmpty());
    }

    @Test
    void bridgeKeepsCanonicalizationProposalOnlyByDefault() {
        IrGpuArtifact original = artifact("body  \r\n  return original\t\r\n");

        GpuRuntimeIrOptimizationReport report = new GpuIrProposalRuntimeBridgePass()
                .run(new GpuRuntimeIrOptimizationRequest(request(original), Optional.of(original)));

        assertSame(original, report.artifact().orElseThrow());
        assertFalse(report.requiresRollback());
        assertEquals(6, report.passReports().size());
        assertEquals(GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY, report.passReports().get(1).stage());
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, report.passReports().get(1).outcome());
        assertEquals("proposal-only", report.passReports().get(1).proofStatus());
        assertTrue(report.passReports().get(1).diagnostics().contains(
                "optimized artifact validated but mutation is disabled; original IR remains selected"
        ));
    }

    @Test
    void bridgeCanSelectOptimizedArtifactWhenExplicitlyAllowed() {
        IrGpuArtifact original = artifact("body  \r\n  return original\t\r\n");
        GpuIrProposalRuntimeBridgePass bridge = new GpuIrProposalRuntimeBridgePass(
                List.of(new GpuIrTextCanonicalizationProposalProvider()),
                GpuIrOptimizationSandwichRunner.alwaysValid(),
                true
        );

        GpuRuntimeIrOptimizationReport report = bridge
                .run(new GpuRuntimeIrOptimizationRequest(request(original), Optional.of(original)));

        assertEquals("body\n  return original\n", report.artifact().orElseThrow().module().methodBodies().get(0).body());
        assertEquals(1, report.passReports().size());
        assertEquals(GpuRuntimeIrOptimizationOutcome.APPLIED, report.passReports().get(0).outcome());
        assertEquals("optimized-selected", report.passReports().get(0).proofStatus());
        assertFalse(report.requiresRollback());
    }

    @Test
    void bridgeRollsBackWhenSandwichRejectsOptimizedArtifact() {
        IrGpuArtifact original = artifact("body  \r\n  return original\t\r\n");
        GpuIrOptimizationValidationGate validationGate = validationRequest -> {
            if (validationRequest.stage() == GpuIrOptimizationValidationStage.OPTIMIZED_AFTER) {
                return GpuIrOptimizationValidationResult.invalid(
                        validationRequest.stage(),
                        "optimized-invalid",
                        "bridge test rejected optimized artifact"
                );
            }
            return GpuIrOptimizationValidationResult.valid(validationRequest.stage());
        };
        GpuIrProposalRuntimeBridgePass bridge = new GpuIrProposalRuntimeBridgePass(
                List.of(new GpuIrTextCanonicalizationProposalProvider()),
                new GpuIrOptimizationSandwichRunner(validationGate),
                true
        );

        GpuRuntimeIrOptimizationReport report = bridge
                .run(new GpuRuntimeIrOptimizationRequest(request(original), Optional.of(original)));

        assertSame(original, report.artifact().orElseThrow());
        assertTrue(report.requiresRollback());
        assertEquals(GpuRuntimeIrOptimizationOutcome.ROLLED_BACK, report.passReports().get(0).outcome());
        assertEquals("optimized-invalid", report.passReports().get(0).proofStatus());
        assertEquals("optimized artifact rejected by validation sandwich", report.passReports().get(0).rollbackReason());
    }

    @Test
    void runtimeRegistrySeesBridgeButKeepsOriginalIrByDefault() {
        IrGpuArtifact original = artifact("body  \r\n  return original\t\r\n");

        GpuRuntimeIrOptimizationReport report = GpuRuntimeIrOptimizerRegistry.loadFromServiceLoader()
                .optimizeWithReport(new GpuRuntimeIrOptimizationRequest(request(original), Optional.of(original)));

        assertSame(original, report.artifact().orElseThrow());
        assertTrue(report.passReports().stream().anyMatch(passReport ->
                GpuIrProposalRuntimeBridgePass.PASS_VERSION.equals(passReport.optimizerVersion())
                        || passReport.optimizerVersion().startsWith(GpuIrOptimizerModule.MODULE_ID)
        ));
    }

    private static GpuRuntimeCompileRequest request(IrGpuArtifact artifact) {
        GpuRuntimeDeviceProfile profile = GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "test-device");
        return new GpuRuntimeCompileRequest(
                new GpuKernelDescriptor("run", "test.Kernel.run", "", "test.Kernel.run.irgpu", List.of()),
                GpuRuntimeCompileOptions.openCl(List.of(), "diagnostic"),
                profile,
                Optional.of(artifact)
        );
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
        ClassLoader classLoader = GpuIrProposalRuntimeBridgePassTest.class.getClassLoader();
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
