package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight PTX header metadata used as evidence for CUDA driver compatibility checks.
 */
record CudaPtxCompatibilityMetadata(
        String ptxVersion,
        String targetLine,
        String targetKind,
        String targetArchitecture,
        String targetComputeCapability,
        int targetComputeCapabilityMajor,
        int targetComputeCapabilityMinor
) {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(?m)^\\s*\\.version\\s+([0-9]+(?:\\.[0-9]+)?)\\b");
    private static final Pattern TARGET_LINE_PATTERN = Pattern.compile("(?m)^\\s*\\.target\\s+([^\\r\\n]+)");
    private static final Pattern TARGET_ARCH_PATTERN = Pattern.compile("\\b(sm|compute)_([0-9]+[a-z]?)\\b");

    CudaPtxCompatibilityMetadata {
        ptxVersion = normalize(ptxVersion, "unknown");
        targetLine = normalize(targetLine, "unknown");
        targetKind = normalize(targetKind, "unknown");
        targetArchitecture = normalize(targetArchitecture, "unknown");
        targetComputeCapability = normalize(targetComputeCapability, "unknown");
        targetComputeCapabilityMajor = Math.max(-1, targetComputeCapabilityMajor);
        targetComputeCapabilityMinor = Math.max(-1, targetComputeCapabilityMinor);
    }

    static CudaPtxCompatibilityMetadata fromSource(String ptxSource) {
        String source = ptxSource == null ? "" : ptxSource;
        String version = firstMatch(VERSION_PATTERN, source, "unknown");
        String targetLine = firstMatch(TARGET_LINE_PATTERN, source, "unknown");
        Matcher targetMatcher = TARGET_ARCH_PATTERN.matcher(targetLine);
        if (!targetMatcher.find()) {
            return new CudaPtxCompatibilityMetadata(version, targetLine, "unknown", "unknown", "unknown", -1, -1);
        }

        String kind = targetMatcher.group(1);
        String architecture = kind + "_" + targetMatcher.group(2);
        String numericArchitecture = targetMatcher.group(2).replaceAll("[^0-9]", "");
        int major = -1;
        int minor = -1;
        if (!numericArchitecture.isBlank()) {
            int encodedCapability = Integer.parseInt(numericArchitecture);
            major = encodedCapability / 10;
            minor = encodedCapability % 10;
        }
        String computeCapability = major >= 0 && minor >= 0 ? major + "." + minor : "unknown";
        return new CudaPtxCompatibilityMetadata(
                version,
                targetLine,
                kind,
                architecture,
                computeCapability,
                major,
                minor
        );
    }

    boolean targetComputeCapabilityKnown() {
        return targetComputeCapabilityMajor >= 0 && targetComputeCapabilityMinor >= 0;
    }

    String diagnosticLine() {
        return "CUDA PTX metadata: version="
                + ptxVersion
                + ", target="
                + targetArchitecture
                + ", capability="
                + targetComputeCapability;
    }

    Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.ptxCompatibility"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".ptx.version", ptxVersion);
        fields.put(normalizedPrefix + ".target.line", targetLine);
        fields.put(normalizedPrefix + ".target.kind", targetKind);
        fields.put(normalizedPrefix + ".target.architecture", targetArchitecture);
        fields.put(normalizedPrefix + ".target.computeCapability", targetComputeCapability);
        fields.put(normalizedPrefix + ".target.computeCapability.known", Boolean.toString(targetComputeCapabilityKnown()));
        fields.put(normalizedPrefix + ".target.computeCapability.major", Integer.toString(targetComputeCapabilityMajor));
        fields.put(normalizedPrefix + ".target.computeCapability.minor", Integer.toString(targetComputeCapabilityMinor));
        fields.put("runtime.cuda.ptxCompatibility.present", "true");
        fields.put("runtime.cuda.ptxCompatibility.ptx.version", ptxVersion);
        fields.put("runtime.cuda.ptxCompatibility.target.architecture", targetArchitecture);
        fields.put("runtime.cuda.ptxCompatibility.target.computeCapability", targetComputeCapability);
        return Collections.unmodifiableMap(fields);
    }

    private static String firstMatch(Pattern pattern, String source, String fallback) {
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) {
            return fallback;
        }
        return normalize(matcher.group(1), fallback);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

record CudaPtxDeviceCompatibility(
        String status,
        String ptxTargetArchitecture,
        String ptxTargetComputeCapability,
        String deviceComputeCapability,
        List<String> blockers,
        List<String> diagnostics
) {

    CudaPtxDeviceCompatibility {
        status = status == null || status.isBlank() ? "unknown" : status.trim();
        ptxTargetArchitecture = ptxTargetArchitecture == null || ptxTargetArchitecture.isBlank()
                ? "unknown"
                : ptxTargetArchitecture.trim();
        ptxTargetComputeCapability = ptxTargetComputeCapability == null || ptxTargetComputeCapability.isBlank()
                ? "unknown"
                : ptxTargetComputeCapability.trim();
        deviceComputeCapability = deviceComputeCapability == null || deviceComputeCapability.isBlank()
                ? "unknown"
                : deviceComputeCapability.trim();
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    static CudaPtxDeviceCompatibility evaluate(
            CudaPtxCompatibilityMetadata metadata,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        CudaPtxCompatibilityMetadata ptxMetadata = metadata == null
                ? CudaPtxCompatibilityMetadata.fromSource("")
                : metadata;
        String deviceCapability = deviceComputeCapability(deviceProfile);
        if (!ptxMetadata.targetComputeCapabilityKnown()) {
            return skipped(
                    ptxMetadata,
                    deviceCapability,
                    "CUDA PTX target/device preflight skipped: PTX target compute capability is unknown"
            );
        }
        int[] parsedDeviceCapability = parseComputeCapability(deviceCapability);
        if (parsedDeviceCapability == null) {
            return skipped(
                    ptxMetadata,
                    deviceCapability,
                    "CUDA PTX target/device preflight skipped: CUDA device compute capability is unknown"
            );
        }
        int targetMajor = ptxMetadata.targetComputeCapabilityMajor();
        int targetMinor = ptxMetadata.targetComputeCapabilityMinor();
        int deviceMajor = parsedDeviceCapability[0];
        int deviceMinor = parsedDeviceCapability[1];
        if (targetMajor > deviceMajor || targetMajor == deviceMajor && targetMinor > deviceMinor) {
            String blocker = "cuda-ptx-target-too-new:ptx-"
                    + ptxMetadata.targetArchitecture()
                    + ":device-"
                    + deviceCapability;
            return new CudaPtxDeviceCompatibility(
                    "failed",
                    ptxMetadata.targetArchitecture(),
                    ptxMetadata.targetComputeCapability(),
                    deviceCapability,
                    List.of(blocker),
                    List.of("CUDA PTX target "
                            + ptxMetadata.targetComputeCapability()
                            + " is newer than selected device compute capability "
                            + deviceCapability)
            );
        }
        return new CudaPtxDeviceCompatibility(
                "passed",
                ptxMetadata.targetArchitecture(),
                ptxMetadata.targetComputeCapability(),
                deviceCapability,
                List.of(),
                List.of("CUDA PTX target "
                        + ptxMetadata.targetComputeCapability()
                        + " is compatible with selected device compute capability "
                        + deviceCapability)
        );
    }

    boolean passedOrSkipped() {
        return blockers.isEmpty();
    }

    Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.ptxDeviceCompatibility"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", status);
        fields.put(normalizedPrefix + ".passedOrSkipped", Boolean.toString(passedOrSkipped()));
        fields.put(normalizedPrefix + ".ptx.target.architecture", ptxTargetArchitecture);
        fields.put(normalizedPrefix + ".ptx.target.computeCapability", ptxTargetComputeCapability);
        fields.put(normalizedPrefix + ".device.computeCapability", deviceComputeCapability);
        fields.put(normalizedPrefix + ".blocker.count", Integer.toString(blockers.size()));
        for (int index = 0; index < blockers.size(); index++) {
            fields.put(normalizedPrefix + ".blocker." + index, blockers.get(index));
        }
        fields.put("runtime.cuda.ptxDeviceCompatibility.present", "true");
        fields.put("runtime.cuda.ptxDeviceCompatibility.status", status);
        fields.put("runtime.cuda.ptxDeviceCompatibility.passedOrSkipped", Boolean.toString(passedOrSkipped()));
        fields.put("runtime.cuda.ptxDeviceCompatibility.ptx.target.computeCapability", ptxTargetComputeCapability);
        fields.put("runtime.cuda.ptxDeviceCompatibility.device.computeCapability", deviceComputeCapability);
        return Collections.unmodifiableMap(fields);
    }

    private static CudaPtxDeviceCompatibility skipped(
            CudaPtxCompatibilityMetadata metadata,
            String deviceCapability,
            String diagnostic
    ) {
        return new CudaPtxDeviceCompatibility(
                "skipped",
                metadata.targetArchitecture(),
                metadata.targetComputeCapability(),
                deviceCapability,
                List.of(),
                List.of(diagnostic)
        );
    }

    private static String deviceComputeCapability(GpuRuntimeDeviceProfile profile) {
        if (profile == null || profile.backendTarget() != GpuBackendTarget.CUDA) {
            return "unknown";
        }
        return profile.cudaComputeCapability();
    }

    private static int[] parseComputeCapability(String value) {
        if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value) || "not-cuda".equalsIgnoreCase(value)) {
            return null;
        }
        String[] parts = value.trim().split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}

record CudaPtxDriverCompatibility(
        String status,
        String ptxVersion,
        int driverVersionRaw,
        String driverVersion,
        int requiredDriverVersionRaw,
        String requiredCudaRelease,
        String requiredDriverBranch,
        List<String> blockers,
        List<String> diagnostics
) {

    CudaPtxDriverCompatibility {
        status = status == null || status.isBlank() ? "unknown" : status.trim();
        ptxVersion = normalize(ptxVersion, "unknown");
        driverVersionRaw = Math.max(0, driverVersionRaw);
        driverVersion = normalize(driverVersion, "unknown");
        requiredDriverVersionRaw = Math.max(0, requiredDriverVersionRaw);
        requiredCudaRelease = normalize(requiredCudaRelease, "unknown");
        requiredDriverBranch = normalize(requiredDriverBranch, "unknown");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    static CudaPtxDriverCompatibility evaluate(CudaPtxCompatibilityMetadata metadata, int driverVersionRaw) {
        CudaPtxCompatibilityMetadata ptxMetadata = metadata == null
                ? CudaPtxCompatibilityMetadata.fromSource("")
                : metadata;
        String driverVersion = CudaDriverLibrary.formatDriverVersion(driverVersionRaw);
        if ("unknown".equals(ptxMetadata.ptxVersion())) {
            return skipped(
                    ptxMetadata.ptxVersion(),
                    driverVersionRaw,
                    driverVersion,
                    "CUDA PTX driver-version preflight skipped: PTX ISA version is unknown"
            );
        }
        PtxIsaRelease release = PtxIsaRelease.forPtxVersion(ptxMetadata.ptxVersion());
        if (release == null) {
            return skipped(
                    ptxMetadata.ptxVersion(),
                    driverVersionRaw,
                    driverVersion,
                    "CUDA PTX driver-version preflight skipped: PTX ISA version "
                            + ptxMetadata.ptxVersion()
                            + " is not in the conservative compatibility table"
            );
        }
        if (driverVersionRaw <= 0) {
            return skipped(
                    ptxMetadata.ptxVersion(),
                    driverVersionRaw,
                    driverVersion,
                    release,
                    "CUDA PTX driver-version preflight skipped: CUDA driver API version is unknown"
            );
        }
        if (driverVersionRaw < release.requiredDriverVersionRaw()) {
            String blocker = "cuda-driver-ptx-version-unsupported:ptx-"
                    + ptxMetadata.ptxVersion()
                    + ":driver-"
                    + driverVersion
                    + ":requires-"
                    + release.cudaRelease();
            return new CudaPtxDriverCompatibility(
                    "failed",
                    ptxMetadata.ptxVersion(),
                    driverVersionRaw,
                    driverVersion,
                    release.requiredDriverVersionRaw(),
                    release.cudaRelease(),
                    release.driverBranch(),
                    List.of(blocker),
                    List.of("CUDA PTX ISA "
                            + ptxMetadata.ptxVersion()
                            + " requires CUDA driver API "
                            + release.cudaRelease()
                            + " or newer; reported driver API is "
                            + driverVersion)
            );
        }
        return new CudaPtxDriverCompatibility(
                "passed",
                ptxMetadata.ptxVersion(),
                driverVersionRaw,
                driverVersion,
                release.requiredDriverVersionRaw(),
                release.cudaRelease(),
                release.driverBranch(),
                List.of(),
                List.of("CUDA PTX ISA "
                        + ptxMetadata.ptxVersion()
                        + " is compatible with reported CUDA driver API "
                        + driverVersion)
        );
    }

    boolean passedOrSkipped() {
        return blockers.isEmpty();
    }

    Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.ptxDriverCompatibility"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", status);
        fields.put(normalizedPrefix + ".passedOrSkipped", Boolean.toString(passedOrSkipped()));
        fields.put(normalizedPrefix + ".ptx.version", ptxVersion);
        fields.put(normalizedPrefix + ".driver.version.raw", Integer.toString(driverVersionRaw));
        fields.put(normalizedPrefix + ".driver.version", driverVersion);
        fields.put(normalizedPrefix + ".required.driver.version.raw", Integer.toString(requiredDriverVersionRaw));
        fields.put(normalizedPrefix + ".required.cudaRelease", requiredCudaRelease);
        fields.put(normalizedPrefix + ".required.driverBranch", requiredDriverBranch);
        fields.put(normalizedPrefix + ".blocker.count", Integer.toString(blockers.size()));
        for (int index = 0; index < blockers.size(); index++) {
            fields.put(normalizedPrefix + ".blocker." + index, blockers.get(index));
        }
        fields.put("runtime.cuda.ptxDriverCompatibility.present", "true");
        fields.put("runtime.cuda.ptxDriverCompatibility.status", status);
        fields.put("runtime.cuda.ptxDriverCompatibility.passedOrSkipped", Boolean.toString(passedOrSkipped()));
        fields.put("runtime.cuda.ptxDriverCompatibility.ptx.version", ptxVersion);
        fields.put("runtime.cuda.ptxDriverCompatibility.driver.version", driverVersion);
        fields.put("runtime.cuda.ptxDriverCompatibility.required.cudaRelease", requiredCudaRelease);
        return Collections.unmodifiableMap(fields);
    }

    private static CudaPtxDriverCompatibility skipped(
            String ptxVersion,
            int driverVersionRaw,
            String driverVersion,
            String diagnostic
    ) {
        return new CudaPtxDriverCompatibility(
                "skipped",
                ptxVersion,
                driverVersionRaw,
                driverVersion,
                0,
                "unknown",
                "unknown",
                List.of(),
                List.of(diagnostic)
        );
    }

    private static CudaPtxDriverCompatibility skipped(
            String ptxVersion,
            int driverVersionRaw,
            String driverVersion,
            PtxIsaRelease release,
            String diagnostic
    ) {
        return new CudaPtxDriverCompatibility(
                "skipped",
                ptxVersion,
                driverVersionRaw,
                driverVersion,
                release.requiredDriverVersionRaw(),
                release.cudaRelease(),
                release.driverBranch(),
                List.of(),
                List.of(diagnostic)
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record PtxIsaRelease(
            String ptxVersion,
            int requiredDriverVersionRaw,
            String cudaRelease,
            String driverBranch
    ) {

        private static final List<PtxIsaRelease> RELEASES = List.of(
                release("2.0", 3000, "3.0", "r195"),
                release("2.1", 3010, "3.1", "r256"),
                release("2.2", 3020, "3.2", "r260"),
                release("2.3", 4000, "4.0", "r270"),
                release("3.0", 4020, "4.2", "r295"),
                release("3.1", 5000, "5.0", "r302"),
                release("3.2", 5050, "5.5", "r319"),
                release("4.0", 6000, "6.0", "r331"),
                release("4.1", 6050, "6.5", "r340"),
                release("4.2", 7000, "7.0", "r346"),
                release("4.3", 7050, "7.5", "r352"),
                release("5.0", 8000, "8.0", "r361"),
                release("6.0", 9000, "9.0", "r384"),
                release("6.1", 9010, "9.1", "r390"),
                release("6.2", 9020, "9.2", "r396"),
                release("6.3", 10000, "10.0", "r400"),
                release("6.4", 10010, "10.1", "r418"),
                release("6.5", 10020, "10.2", "r440"),
                release("7.0", 11000, "11.0", "r445"),
                release("7.1", 11010, "11.1", "r455"),
                release("7.2", 11020, "11.2", "r460"),
                release("7.3", 11030, "11.3", "r465"),
                release("7.4", 11040, "11.4", "r470"),
                release("7.5", 11050, "11.5", "r495"),
                release("7.6", 11060, "11.6", "r510"),
                release("7.7", 11070, "11.7", "r515"),
                release("7.8", 11080, "11.8", "r520"),
                release("8.0", 12000, "12.0", "r525"),
                release("8.1", 12010, "12.1", "r530"),
                release("8.2", 12020, "12.2", "r535"),
                release("8.3", 12030, "12.3", "r545"),
                release("8.4", 12040, "12.4", "r550"),
                release("8.5", 12050, "12.5", "r555"),
                release("8.6", 12070, "12.7", "r565"),
                release("8.7", 12080, "12.8", "r570"),
                release("8.8", 12090, "12.9", "r575"),
                release("9.0", 13000, "13.0", "r580"),
                release("9.1", 13010, "13.1", "r590"),
                release("9.2", 13020, "13.2", "r595"),
                release("9.3", 13030, "13.3", "r610")
        );

        private static PtxIsaRelease forPtxVersion(String ptxVersion) {
            String normalized = normalize(ptxVersion, "unknown");
            return RELEASES.stream()
                    .filter(release -> release.ptxVersion().equals(normalized))
                    .findFirst()
                    .orElse(null);
        }

        private static PtxIsaRelease release(
                String ptxVersion,
                int requiredDriverVersionRaw,
                String cudaRelease,
                String driverBranch
        ) {
            return new PtxIsaRelease(ptxVersion, requiredDriverVersionRaw, cudaRelease, driverBranch);
        }
    }
}
