package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable source, kernel, backend, device, and compile-option context attached to runtime failures.
 */
public record GpuRuntimeDiagnosticContext(
        GpuBackendTarget backendTarget,
        String kernelName,
        String kernelResource,
        String irGpuResource,
        String deviceId,
        String deviceLabel,
        String deviceVendor,
        List<String> compileArgs,
        String optimizationProfile,
        IrGpuSourceLocation sourceLocation,
        GpuRuntimeCallSite callSite
) {

    public GpuRuntimeDiagnosticContext(
            GpuBackendTarget backendTarget,
            String kernelName,
            String kernelResource,
            String irGpuResource,
            String deviceId,
            String deviceLabel,
            String deviceVendor,
            List<String> compileArgs,
            String optimizationProfile,
            IrGpuSourceLocation sourceLocation
    ) {
        this(
                backendTarget,
                kernelName,
                kernelResource,
                irGpuResource,
                deviceId,
                deviceLabel,
                deviceVendor,
                compileArgs,
                optimizationProfile,
                sourceLocation,
                GpuRuntimeCallSite.unknown()
        );
    }

    public GpuRuntimeDiagnosticContext {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        kernelName = normalize(kernelName, "unknown");
        kernelResource = normalize(kernelResource, "unknown");
        irGpuResource = normalize(irGpuResource, "unknown");
        deviceId = normalize(deviceId, "unknown");
        deviceLabel = normalize(deviceLabel, "unknown");
        deviceVendor = normalize(deviceVendor, "unknown");
        compileArgs = compileArgs == null ? List.of() : List.copyOf(compileArgs);
        optimizationProfile = normalize(optimizationProfile, "off");
        sourceLocation = sourceLocation == null ? IrGpuSourceLocation.unknown(kernelName) : sourceLocation;
        callSite = callSite == null ? GpuRuntimeCallSite.unknown() : callSite;
    }

    public static GpuRuntimeDiagnosticContext unknown() {
        return new GpuRuntimeDiagnosticContext(
                GpuBackendTarget.UNKNOWN,
                "unknown",
                "unknown",
                "unknown",
                "unknown",
                "unknown",
                "unknown",
                List.of(),
                "off",
                IrGpuSourceLocation.unknown("unknown"),
                GpuRuntimeCallSite.unknown()
        );
    }

    public static GpuRuntimeDiagnosticContext fromRequest(GpuRuntimeCompileRequest request) {
        if (request == null) {
            return unknown();
        }
        return from(
                request.descriptor(),
                request.irGpuArtifact(),
                Optional.ofNullable(request.deviceProfile()),
                request.options()
        );
    }

    public static GpuRuntimeDiagnosticContext from(
            GpuKernelDescriptor descriptor,
            Optional<IrGpuArtifact> artifact,
            Optional<GpuRuntimeDeviceProfile> deviceProfile,
            GpuRuntimeCompileOptions compileOptions
    ) {
        GpuKernelDescriptor kernelDescriptor = descriptor == null
                ? new GpuKernelDescriptor("unknown", "unknown", "", "unknown", List.of())
                : descriptor;
        Optional<IrGpuArtifact> irGpuArtifact = artifact == null ? Optional.empty() : artifact;
        Optional<GpuRuntimeDeviceProfile> profile = deviceProfile == null ? Optional.empty() : deviceProfile;
        GpuRuntimeCompileOptions options = compileOptions == null
                ? GpuRuntimeCompileOptions.defaults(GpuBackendTarget.UNKNOWN)
                : compileOptions;
        IrGpuSourceLocation location = irGpuArtifact
                .flatMap(value -> entrySourceLocation(value, kernelDescriptor.kernelName()))
                .orElse(IrGpuSourceLocation.unknown(kernelDescriptor.kernelName()));
        return new GpuRuntimeDiagnosticContext(
                options.backendTarget(),
                kernelDescriptor.kernelName(),
                kernelDescriptor.kernelResource(),
                kernelDescriptor.irGpuResource(),
                profile.map(GpuRuntimeDeviceProfile::deviceId).orElse("unknown"),
                profile.map(GpuRuntimeDeviceProfile::deviceLabel).orElse("unknown"),
                profile.map(GpuRuntimeDeviceProfile::vendor).orElse("unknown"),
                options.compileArgs(),
                options.optimizationProfile(),
                location,
                GpuRuntimeCallSite.unknown()
        );
    }

    public static GpuRuntimeDiagnosticContext fromSelection(GpuRuntimeDeviceSelection selection) {
        Optional<GpuRuntimeDeviceProfile> profile = selection == null
                ? Optional.empty()
                : selection.selectedDevice();
        return new GpuRuntimeDiagnosticContext(
                profile.map(GpuRuntimeDeviceProfile::backendTarget).orElse(GpuBackendTarget.UNKNOWN),
                "unknown",
                "unknown",
                "unknown",
                profile.map(GpuRuntimeDeviceProfile::deviceId).orElse("unknown"),
                profile.map(GpuRuntimeDeviceProfile::deviceLabel).orElse("unknown"),
                profile.map(GpuRuntimeDeviceProfile::vendor).orElse("unknown"),
                List.of(),
                "off",
                IrGpuSourceLocation.unknown("unknown"),
                GpuRuntimeCallSite.unknown()
        );
    }

    public static GpuRuntimeDiagnosticContext fromSnapshot(
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileArtifactSnapshot snapshot
    ) {
        if (snapshot == null) {
            return from(descriptor, Optional.empty(), Optional.empty(), null);
        }
        GpuRuntimeCompileProvenance provenance = snapshot.compileProvenance();
        Optional<IrGpuArtifact> artifact = snapshot.runtimeIrSelection().selectedArtifact()
                .or(() -> snapshot.optimizedIrGpuArtifact())
                .or(() -> snapshot.originalIrGpuArtifact());
        IrGpuSourceLocation location = artifact
                .flatMap(value -> entrySourceLocation(value, descriptor == null ? "unknown" : descriptor.kernelName()))
                .orElse(IrGpuSourceLocation.unknown(descriptor == null ? "unknown" : descriptor.kernelName()));
        Optional<GpuRuntimeDeviceProfile> selectedDevice = snapshot.deviceSelection()
                .flatMap(GpuRuntimeDeviceSelection::selectedDevice);
        return new GpuRuntimeDiagnosticContext(
                provenance.backendTarget(),
                descriptor == null ? "unknown" : descriptor.kernelName(),
                descriptor == null ? "unknown" : descriptor.kernelResource(),
                descriptor == null ? "unknown" : descriptor.irGpuResource(),
                selectedDevice.map(GpuRuntimeDeviceProfile::deviceId).orElse("unknown"),
                selectedDevice.map(GpuRuntimeDeviceProfile::deviceLabel).orElse(provenance.deviceLabel()),
                selectedDevice.map(GpuRuntimeDeviceProfile::vendor).orElse(provenance.vendor()),
                provenance.compileArgs(),
                provenance.optimizationProfile(),
                location,
                GpuRuntimeCallSite.unknown()
        );
    }

    public GpuRuntimeDiagnosticContext withCallSite(GpuRuntimeCallSite value) {
        return new GpuRuntimeDiagnosticContext(
                backendTarget,
                kernelName,
                kernelResource,
                irGpuResource,
                deviceId,
                deviceLabel,
                deviceVendor,
                compileArgs,
                optimizationProfile,
                sourceLocation,
                value
        );
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = normalize(prefix, "runtimeFailure");
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
        fields.put(normalizedPrefix + ".kernelName", kernelName);
        fields.put(normalizedPrefix + ".kernelResource", kernelResource);
        fields.put(normalizedPrefix + ".irGpuResource", irGpuResource);
        fields.put(normalizedPrefix + ".deviceId", deviceId);
        fields.put(normalizedPrefix + ".deviceLabel", deviceLabel);
        fields.put(normalizedPrefix + ".deviceVendor", deviceVendor);
        fields.put(normalizedPrefix + ".optimizationProfile", optimizationProfile);
        fields.put(normalizedPrefix + ".compileArg.count", Integer.toString(compileArgs.size()));
        for (int index = 0; index < compileArgs.size(); index++) {
            fields.put(normalizedPrefix + ".compileArg." + index, compileArgs.get(index));
        }
        fields.put(normalizedPrefix + ".sourceKind", sourceLocation.sourceKind());
        fields.put(normalizedPrefix + ".sourceOwner", sourceLocation.ownerQualifiedName());
        fields.put(normalizedPrefix + ".sourceMethod", sourceLocation.methodName());
        fields.put(normalizedPrefix + ".sourceBeginLine", Integer.toString(sourceLocation.beginLine()));
        fields.put(normalizedPrefix + ".sourceBeginColumn", Integer.toString(sourceLocation.beginColumn()));
        fields.put(normalizedPrefix + ".callSite.source", callSite.source());
        fields.put(normalizedPrefix + ".callSite.callerClassName", callSite.callerClassName());
        fields.put(normalizedPrefix + ".callSite.callerMethodName", callSite.callerMethodName());
        fields.put(normalizedPrefix + ".callSite.sourceName", callSite.sourceName());
        fields.put(normalizedPrefix + ".callSite.line", Integer.toString(callSite.line()));
        fields.put(normalizedPrefix + ".callSite.column", Integer.toString(callSite.column()));
        fields.put(normalizedPrefix + ".callSite.expression", callSite.expression());
        fields.put(normalizedPrefix + ".callSite.targetOwnerName", callSite.targetOwnerName());
        fields.put(normalizedPrefix + ".callSite.targetMethodName", callSite.targetMethodName());
        return Map.copyOf(fields);
    }

    public String sourceName() {
        if (callSite.knownRange()) {
            return callSite.sourceName();
        }
        if (!sourceLocation.ownerQualifiedName().isBlank()) {
            return sourceLocation.ownerQualifiedName()
                    + (sourceLocation.methodName().isBlank() ? "" : "#" + sourceLocation.methodName());
        }
        return !kernelResource.equals("unknown") ? kernelResource : kernelName;
    }

    private static Optional<IrGpuSourceLocation> entrySourceLocation(IrGpuArtifact artifact, String kernelName) {
        return artifact.module().methodBodies().stream()
                .filter(body -> isEntryBody(artifact, body, kernelName))
                .map(IrGpuMethodBody::sourceLocation)
                .findFirst();
    }

    private static boolean isEntryBody(IrGpuArtifact artifact, IrGpuMethodBody body, String kernelName) {
        return "entry".equals(body.role())
                || body.name().equals(artifact.module().entryMethod())
                || body.emittedName().equals(artifact.module().entryEmittedName())
                || body.emittedName().equals(kernelName);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
