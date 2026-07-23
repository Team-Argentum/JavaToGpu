package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuProductionActivationTokenTest {

    @Test
    void loadsArtifactAndMatchesRuntimeCompileContext() {
        GpuRuntimeDeviceProfile device = deviceProfile();
        GpuKernelDescriptor descriptor = descriptor("kernel-a.cl");
        GpuProductionActivationToken token = GpuProductionActivationTokenTestFixtures.token(
                device,
                "kernel-a.cl",
                "kernel-b.cl"
        );
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions
                .openClProductionIrGpuSource(List.of(), "vendor-tuned")
                .withProductionActivationToken(token);
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(descriptor, options, device);

        GpuProductionActivationToken.Result result = GpuProductionActivationToken.evaluate(request);

        assertTrue(result.accepted());
        assertEquals(token.tokenId(), result.tokenId());
        assertEquals(token.artifactSha256(), result.artifactSha256());
        assertTrue(options.backendOptions().productionActivationToken().isPresent());
        assertTrue(options.backendOptions().stableProperties().containsKey(
                GpuProductionActivationToken.PROPERTY_PREFIX + "artifactSha256"
        ));
    }

    @Test
    void rejectsArtifactWhenExpectedDigestDoesNotMatch() throws Exception {
        String artifact = GpuProductionActivationTokenTestFixtures.activationArtifact(
                deviceProfile(),
                "kernel-a.cl"
        );
        Path path = Files.createTempFile("javatogpu-production-activation-digest", ".properties");
        Files.writeString(path, artifact, StandardCharsets.UTF_8);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> GpuProductionActivationToken.fromArtifact(
                        path,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                )
        );

        assertTrue(exception.getMessage().contains("SHA-256 mismatch"));
    }

    @Test
    void loadsPortableActivationGateArtifactWithoutLegacyFieldNames() throws Exception {
        String artifactWithMirrors = GpuBackendSourcePromotionActivationGate.from(
                GpuBackendSourcePromotionActivationGateTest.properties(GpuBackendSourcePromotionManifestTest.candidateText()),
                GpuBackendSourcePromotionActivationGateTest.properties(
                        GpuBackendSourcePromotionActivationGateTest.manifestValidationText()
                ),
                GpuBackendSourcePromotionActivationGateTest.properties(
                        GpuBackendSourcePromotionActivationGateTest.controlledSourceSwitchingText(
                                "kernel-a.cl",
                                "kernel-b.cl"
                        )
                )
        ).toPropertiesText();
        String artifact = portableActivationGateOnly(artifactWithMirrors);
        byte[] bytes = artifact.getBytes(StandardCharsets.UTF_8);
        Path path = Files.createTempFile("javatogpu-production-activation-portable", ".properties");
        Files.write(path, bytes);

        GpuProductionActivationToken token = GpuProductionActivationToken.fromArtifact(
                path,
                GpuProductionActivationToken.sha256(bytes)
        );

        assertEquals("approval:test", token.approvalId());
        assertEquals("ac3ba1f681666fc215f4113e5e2a4c66ef63999f", token.candidateGitSha());
        assertEquals(GpuBackendTarget.OPENCL, token.backendTarget());
        assertEquals("NVIDIA Corporation", token.deviceVendor());
        assertEquals("NVIDIA CUDA / NVIDIA GeForce RTX 5070", token.deviceLabel());
        assertEquals("595.97", token.driverVersion());
        assertEquals(List.of("kernel-a.cl", "kernel-b.cl"), token.kernelResources());
    }

    @Test
    void blocksDifferentDeviceAndUnapprovedKernel() {
        GpuRuntimeDeviceProfile approvedDevice = deviceProfile();
        GpuProductionActivationToken token = GpuProductionActivationTokenTestFixtures.token(
                approvedDevice,
                "kernel-a.cl"
        );
        GpuRuntimeDeviceProfile differentDevice = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "Other GPU",
                "Other Vendor",
                "2.0",
                "OpenCL 3.0 Mock",
                -1L,
                32_768L,
                256L,
                -1L,
                true,
                true,
                true
        );
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions
                .openClProductionIrGpuSource(List.of(), "vendor-tuned")
                .withProductionActivationToken(token);
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor("kernel-b.cl"),
                options,
                differentDevice
        );

        GpuProductionActivationToken.Result result = GpuProductionActivationToken.evaluate(request);

        assertFalse(result.accepted());
        assertTrue(result.blockers().contains("production-activation-device-vendor-mismatch"));
        assertTrue(result.blockers().contains("production-activation-kernel-resource-not-approved"));
    }

    private static GpuRuntimeDeviceProfile deviceProfile() {
        return GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "Mock GPU",
                "Mock Vendor",
                "1.0",
                "OpenCL 3.0 Mock",
                -1L,
                32_768L,
                256L,
                -1L,
                true,
                true,
                true
        );
    }

    private static GpuKernelDescriptor descriptor(String resource) {
        return new GpuKernelDescriptor("gpu_kernel", resource, "", List.of());
    }

    private static String portableActivationGateOnly(String artifact) {
        StringBuilder builder = new StringBuilder("formatVersion=1\n");
        for (String line : artifact.split("\\R")) {
            if (line.startsWith("runtime.production.activationGate.")
                    || line.contains(".runtime.production.activationGate.")) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }
}
