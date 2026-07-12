package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrVendorOptimizationProposalRegistryTest {

    @Test
    void registryOrdersProvidersAndRejectsDuplicateIds() {
        GpuIrVendorOptimizationProposalRegistry registry = GpuIrVendorOptimizationProposalRegistry.of(List.of(
                new VendorProvider("z-provider", GpuBackendTarget.OPENCL, "AMD", 20, false),
                new VendorProvider("a-provider", GpuBackendTarget.OPENCL, "NVIDIA", 10, false),
                new VendorProvider("a-provider", GpuBackendTarget.OPENCL, "Intel", 30, false)
        ));

        assertEquals(2, registry.providers().size());
        assertEquals("a-provider", registry.providers().get(0).extensionId());
        assertEquals("z-provider", registry.providers().get(1).extensionId());
        assertEquals("duplicate extension id", registry.rejectionReasons().get("a-provider"));
    }

    @Test
    void adapterReturnsNoChangeWhenNoVendorProviderMatches() {
        IrGpuArtifact original = artifact("body\n");
        GpuIrOptimizationProposalRequest request = request(
                original,
                Map.of(
                        "backendTarget", "OPENCL",
                        "deviceProfile.vendor", "Intel",
                        "deviceProfile.deviceClass", "IGPU"
                )
        );
        GpuIrVendorOptimizationProposalRegistry registry = GpuIrVendorOptimizationProposalRegistry.of(List.of(
                new VendorProvider("nvidia-provider", GpuBackendTarget.OPENCL, "NVIDIA", 0, false)
        ));

        GpuIrOptimizationProposal proposal = registry.asProposalProvider().propose(request);

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertSame(original, proposal.originalArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("no vendor IR optimizer proposal provider matched"));
    }

    @Test
    void adapterRunsMatchingVendorProviderThroughCommonProposalContract() {
        IrGpuArtifact original = artifact("body\n");
        GpuIrOptimizationProposalRequest request = request(
                original,
                Map.of(
                        "backendTarget", "OPENCL",
                        "deviceProfile.vendor", "NVIDIA",
                        "deviceProfile.id", "gpu-0",
                        "deviceProfile.label", "RTX",
                        "deviceProfile.driverVersion", "595.97",
                        "deviceProfile.apiVersionText", "OpenCL 3.0",
                        "deviceProfile.deviceClass", "DGPU"
                )
        );
        GpuIrVendorOptimizationProposalRegistry registry = GpuIrVendorOptimizationProposalRegistry.of(List.of(
                new VendorProvider("nvidia-provider", GpuBackendTarget.OPENCL, "NVIDIA", 0, true)
        ));

        GpuIrOptimizationProposal proposal = registry.asProposalProvider().propose(request);

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        assertEquals("nvidia-provider", proposal.optimizerId());
        assertTrue(proposal.proofArtifact().fields().containsKey("deviceLabel"));
        assertEquals("RTX", proposal.proofArtifact().fields().get("deviceLabel"));
    }

    @Test
    void vendorRequestNormalizesUnknownFields() {
        GpuIrVendorOptimizationProposalRequest request = GpuIrVendorOptimizationProposalRequest.from(
                request(artifact("body\n"), Map.of("backendTarget", "bad-target"))
        );

        assertEquals(GpuBackendTarget.UNKNOWN, request.backendTarget());
        assertEquals("unknown", request.vendor());
        assertEquals(GpuDeviceClassTarget.UNKNOWN, request.deviceClass());
    }

    private static GpuIrOptimizationProposalRequest request(
            IrGpuArtifact artifact,
            Map<String, String> contextFields
    ) {
        return new GpuIrOptimizationProposalRequest(artifact, "diagnostic", false, contextFields);
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

    private record VendorProvider(
            String extensionId,
            GpuBackendTarget backendTarget,
            String vendor,
            int order,
            boolean proposes
    ) implements GpuIrVendorOptimizationProposalProvider {

        @Override
        public GpuIrOptimizationProposal proposeForVendor(GpuIrVendorOptimizationProposalRequest request) {
            if (!proposes) {
                return GpuIrOptimizationProposal.noChange(
                        extensionId(),
                        extensionVersion(),
                        request.originalArtifact(),
                        "vendor provider did not find a safe proposal"
                );
            }
            return GpuIrOptimizationProposal.proposed(
                    extensionId(),
                    extensionVersion(),
                    request.originalArtifact(),
                    artifact("body\n// vendor proposal\n"),
                    GpuRuntimeIrOptimizationProofArtifact.fromFields(
                            "vendor-test",
                            "proposal-only",
                            Map.of(
                                    "backendTarget", request.backendTarget().name(),
                                    "vendor", request.vendor(),
                                    "deviceLabel", request.deviceLabel()
                            )
                    ),
                    List.of("vendor provider proposed a candidate for " + request.vendor())
            );
        }

        @Override
        public GpuBackendTarget supportedBackendTarget() {
            return backendTarget;
        }

        @Override
        public String supportedVendor() {
            return vendor;
        }

        @Override
        public String extensionId() {
            return extensionId;
        }

        @Override
        public String extensionVersion() {
            return extensionId + ":1";
        }

        @Override
        public int extensionOrder() {
            return order;
        }
    }
}
