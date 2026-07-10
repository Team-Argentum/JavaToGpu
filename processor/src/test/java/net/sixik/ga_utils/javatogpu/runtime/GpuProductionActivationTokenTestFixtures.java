package net.sixik.ga_utils.javatogpu.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GpuProductionActivationTokenTestFixtures {

    private GpuProductionActivationTokenTestFixtures() {
    }

    public static GpuProductionActivationToken token(
            GpuRuntimeDeviceProfile deviceProfile,
            String... kernelResources
    ) {
        try {
            String artifact = activationArtifact(deviceProfile, kernelResources);
            byte[] bytes = artifact.getBytes(StandardCharsets.UTF_8);
            Path path = Files.createTempFile("javatogpu-production-activation", ".properties");
            Files.write(path, bytes);
            return GpuProductionActivationToken.fromArtifact(
                    path,
                    GpuProductionActivationToken.sha256(bytes)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create production activation token fixture", exception);
        }
    }

    public static String activationArtifact(
            GpuRuntimeDeviceProfile deviceProfile,
            String... kernelResources
    ) {
        GpuRuntimeDeviceProfile device = deviceProfile == null
                ? GpuRuntimeDeviceProfile.generic(null, "unknown")
                : deviceProfile;
        StringBuilder builder = new StringBuilder()
                .append("formatVersion=1\n")
                .append("status=controlled-activation-ready\n")
                .append("activationReady=true\n")
                .append("activationScope=controlled-opt-in-only\n")
                .append("backendTarget=").append(device.backendTarget()).append('\n')
                .append("defaultRuntimeActivation=false\n")
                .append("defaultProductionSourceSwitching=disabled\n")
                .append("productionMutation=disabled\n")
                .append("manifest.approval.id=approval:test-activation\n")
                .append("manifest.candidateGitSha=ac3ba1f681666fc215f4113e5e2a4c66ef63999f\n")
                .append("deviceVendor=").append(device.vendor()).append('\n')
                .append("deviceLabel=").append(device.deviceLabel()).append('\n')
                .append("driverVersion=").append(device.driverVersion()).append('\n')
                .append("kernel.count=").append(kernelResources.length).append('\n')
                .append("controlledCoverage.count=").append(kernelResources.length).append('\n')
                .append("controlledCoverage.all=true\n")
                .append("operatorAcceptance.accepted.count=").append(kernelResources.length).append('\n')
                .append("operatorAcceptance.accepted.all=true\n")
                .append("operatorAcceptance.bound.count=").append(kernelResources.length).append('\n')
                .append("operatorAcceptance.bound.all=true\n");
        for (int index = 0; index < kernelResources.length; index++) {
            String prefix = "kernel." + index + ".";
            builder.append(prefix).append("resource=").append(kernelResources[index]).append('\n');
            builder.append(prefix).append("controlledStatus=passed\n");
            builder.append(prefix).append("operatorAcceptance.status=accepted\n");
            builder.append(prefix).append("operatorAcceptance.bound=true\n");
            builder.append(prefix).append("activationReady=true\n");
            builder.append(prefix).append("blocker.count=0\n");
        }
        return builder.append("blocker.count=0\n")
                .append("diagnostic=controlled opt-in activation is ready; default runtime activation remains disabled\n")
                .toString();
    }
}
