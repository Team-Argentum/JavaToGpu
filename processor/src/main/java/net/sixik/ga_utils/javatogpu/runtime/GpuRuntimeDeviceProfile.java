package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;

import java.util.Locale;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record GpuRuntimeDeviceProfile(
        GpuBackendTarget backendTarget,
        String backendName,
        String deviceId,
        String deviceLabel,
        String vendor,
        String driverVersion,
        String apiVersionText,
        String compilerVersion,
        GpuDeviceClassTarget deviceClass,
        long computeUnits,
        long globalMemoryBytes,
        long localMemoryBytes,
        long maxWorkGroupSize,
        long preferredVectorWidthFloat,
        boolean unifiedMemory,
        boolean supportsDoublePrecision,
        boolean supportsImages,
        boolean supportsImage3dWrites,
        boolean supportsAtomics,
        boolean supportsSubgroups,
        String platformName,
        String platformVersion
) {

    private static final Pattern CUDA_RUNTIME_VERSION = Pattern.compile(
            "\\bCUDA\\s+([0-9]+(?:\\.[0-9]+)*|unknown)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CUDA_COMPUTE_CAPABILITY = Pattern.compile(
            "\\bcompute\\s+capability\\s+([0-9]+(?:\\.[0-9]+)*|unknown)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public GpuRuntimeDeviceProfile(
            GpuBackendTarget backendTarget,
            String backendName,
            String deviceId,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText,
            GpuDeviceClassTarget deviceClass,
            long computeUnits,
            long globalMemoryBytes,
            long localMemoryBytes,
            long maxWorkGroupSize,
            long preferredVectorWidthFloat,
            boolean unifiedMemory,
            boolean supportsDoublePrecision,
            boolean supportsImages,
            boolean supportsImage3dWrites,
            boolean supportsAtomics,
            boolean supportsSubgroups,
            String platformName,
            String platformVersion
    ) {
        this(
                backendTarget,
                backendName,
                deviceId,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                "unknown",
                deviceClass,
                computeUnits,
                globalMemoryBytes,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                unifiedMemory,
                supportsDoublePrecision,
                supportsImages,
                supportsImage3dWrites,
                supportsAtomics,
                supportsSubgroups,
                platformName,
                platformVersion
        );
    }

    public GpuRuntimeDeviceProfile(
            GpuBackendTarget backendTarget,
            String backendName,
            String deviceId,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText,
            GpuDeviceClassTarget deviceClass,
            long computeUnits,
            long globalMemoryBytes,
            long localMemoryBytes,
            long maxWorkGroupSize,
            long preferredVectorWidthFloat,
            boolean unifiedMemory,
            boolean supportsDoublePrecision,
            boolean supportsImages,
            boolean supportsSubgroups
    ) {
        this(
                backendTarget,
                backendName,
                deviceId,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                "unknown",
                deviceClass,
                computeUnits,
                globalMemoryBytes,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                unifiedMemory,
                supportsDoublePrecision,
                supportsImages,
                false,
                false,
                supportsSubgroups,
                "unknown",
                "unknown"
        );
    }

    public GpuRuntimeDeviceProfile(
            GpuBackendTarget backendTarget,
            String backendName,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText
    ) {
        this(
                backendTarget,
                backendName,
                "unknown",
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                GpuDeviceClassTarget.UNKNOWN,
                -1L,
                -1L,
                -1L,
                -1L,
                -1L,
                false,
                false,
                false,
                false
        );
    }

    public GpuRuntimeDeviceProfile(
            GpuBackendTarget backendTarget,
            String backendName,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText,
            long computeUnits,
            long localMemoryBytes,
            long maxWorkGroupSize,
            long preferredVectorWidthFloat,
            boolean supportsDoublePrecision,
            boolean supportsImages,
            boolean supportsSubgroups
    ) {
        this(
                backendTarget,
                backendName,
                "unknown",
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                GpuDeviceClassTarget.UNKNOWN,
                computeUnits,
                -1L,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                false,
                supportsDoublePrecision,
                supportsImages,
                supportsSubgroups
        );
    }

    public GpuRuntimeDeviceProfile {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        backendName = normalize(backendName);
        deviceId = normalize(deviceId);
        deviceLabel = normalize(deviceLabel);
        vendor = normalize(vendor);
        driverVersion = normalize(driverVersion);
        apiVersionText = normalize(apiVersionText);
        compilerVersion = normalize(compilerVersion);
        deviceClass = normalizeDeviceClass(deviceClass);
        platformName = normalize(platformName);
        platformVersion = normalize(platformVersion);
        computeUnits = normalizeLong(computeUnits);
        globalMemoryBytes = normalizeLong(globalMemoryBytes);
        localMemoryBytes = normalizeLong(localMemoryBytes);
        maxWorkGroupSize = normalizeLong(maxWorkGroupSize);
        preferredVectorWidthFloat = normalizeLong(preferredVectorWidthFloat);
    }

    public static GpuRuntimeDeviceProfile generic(GpuBackendTarget backendTarget, String backendName) {
        return new GpuRuntimeDeviceProfile(
                backendTarget,
                backendName,
                "unknown",
                "unknown",
                "unknown",
                "unknown",
                "unknown",
                "unknown",
                GpuDeviceClassTarget.UNKNOWN,
                -1L,
                -1L,
                -1L,
                -1L,
                -1L,
                false,
                false,
                false,
                false,
                false,
                false,
                "unknown",
                "unknown"
        );
    }

    public static GpuRuntimeDeviceProfile openCl(
            String backendName,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText,
            long computeUnits,
            long localMemoryBytes,
            long maxWorkGroupSize,
            long preferredVectorWidthFloat,
            boolean supportsDoublePrecision,
            boolean supportsImages,
            boolean supportsSubgroups
    ) {
        return openCl(
                backendName,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                computeUnits,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                supportsDoublePrecision,
                supportsImages,
                false,
                false,
                supportsSubgroups
        );
    }

    public static GpuRuntimeDeviceProfile openCl(
            String backendName,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText,
            long computeUnits,
            long localMemoryBytes,
            long maxWorkGroupSize,
            long preferredVectorWidthFloat,
            boolean supportsDoublePrecision,
            boolean supportsImages,
            boolean supportsImage3dWrites,
            boolean supportsAtomics,
            boolean supportsSubgroups
    ) {
        return openCl(
                backendName,
                "unknown",
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                GpuDeviceClassTarget.UNKNOWN,
                computeUnits,
                -1L,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                false,
                supportsDoublePrecision,
                supportsImages,
                supportsImage3dWrites,
                supportsAtomics,
                supportsSubgroups
        );
    }

    public static GpuRuntimeDeviceProfile openCl(
            String backendName,
            String deviceId,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText,
            GpuDeviceClassTarget deviceClass,
            long computeUnits,
            long globalMemoryBytes,
            long localMemoryBytes,
            long maxWorkGroupSize,
            long preferredVectorWidthFloat,
            boolean unifiedMemory,
            boolean supportsDoublePrecision,
            boolean supportsImages,
            boolean supportsSubgroups
    ) {
        return openCl(
                backendName,
                deviceId,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                "unknown",
                "unknown",
                deviceClass,
                computeUnits,
                globalMemoryBytes,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                unifiedMemory,
                supportsDoublePrecision,
                supportsImages,
                supportsSubgroups
        );
    }

    public static GpuRuntimeDeviceProfile openCl(
            String backendName,
            String deviceId,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText,
            GpuDeviceClassTarget deviceClass,
            long computeUnits,
            long globalMemoryBytes,
            long localMemoryBytes,
            long maxWorkGroupSize,
            long preferredVectorWidthFloat,
            boolean unifiedMemory,
            boolean supportsDoublePrecision,
            boolean supportsImages,
            boolean supportsImage3dWrites,
            boolean supportsAtomics,
            boolean supportsSubgroups
    ) {
        return openCl(
                backendName,
                deviceId,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                "unknown",
                "unknown",
                deviceClass,
                computeUnits,
                globalMemoryBytes,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                unifiedMemory,
                supportsDoublePrecision,
                supportsImages,
                supportsImage3dWrites,
                supportsAtomics,
                supportsSubgroups
        );
    }

    public static GpuRuntimeDeviceProfile openCl(
            String backendName,
            String deviceId,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText,
            String compilerVersion,
            String platformName,
            String platformVersion,
            GpuDeviceClassTarget deviceClass,
            long computeUnits,
            long globalMemoryBytes,
            long localMemoryBytes,
            long maxWorkGroupSize,
            long preferredVectorWidthFloat,
            boolean unifiedMemory,
            boolean supportsDoublePrecision,
            boolean supportsImages,
            boolean supportsImage3dWrites,
            boolean supportsAtomics,
            boolean supportsSubgroups
    ) {
        return new GpuRuntimeDeviceProfile(
                GpuBackendTarget.OPENCL,
                backendName,
                deviceId,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                compilerVersion,
                deviceClass,
                computeUnits,
                globalMemoryBytes,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                unifiedMemory,
                supportsDoublePrecision,
                supportsImages,
                supportsImage3dWrites,
                supportsAtomics,
                supportsSubgroups,
                platformName,
                platformVersion
        );
    }

    public static GpuRuntimeDeviceProfile openCl(
            String backendName,
            String deviceId,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText,
            String platformName,
            String platformVersion,
            GpuDeviceClassTarget deviceClass,
            long computeUnits,
            long globalMemoryBytes,
            long localMemoryBytes,
            long maxWorkGroupSize,
            long preferredVectorWidthFloat,
            boolean unifiedMemory,
            boolean supportsDoublePrecision,
            boolean supportsImages,
            boolean supportsSubgroups
    ) {
        return openCl(
                backendName,
                deviceId,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                platformName,
                platformVersion,
                deviceClass,
                computeUnits,
                globalMemoryBytes,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                unifiedMemory,
                supportsDoublePrecision,
                supportsImages,
                false,
                false,
                supportsSubgroups
        );
    }

    public static GpuRuntimeDeviceProfile openCl(
            String backendName,
            String deviceId,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText,
            String platformName,
            String platformVersion,
            GpuDeviceClassTarget deviceClass,
            long computeUnits,
            long globalMemoryBytes,
            long localMemoryBytes,
            long maxWorkGroupSize,
            long preferredVectorWidthFloat,
            boolean unifiedMemory,
            boolean supportsDoublePrecision,
            boolean supportsImages,
            boolean supportsImage3dWrites,
            boolean supportsSubgroups
    ) {
        return openCl(
                backendName,
                deviceId,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                platformName,
                platformVersion,
                deviceClass,
                computeUnits,
                globalMemoryBytes,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                unifiedMemory,
                supportsDoublePrecision,
                supportsImages,
                supportsImage3dWrites,
                false,
                supportsSubgroups
        );
    }

    public static GpuRuntimeDeviceProfile openCl(
            String backendName,
            String deviceId,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText,
            String platformName,
            String platformVersion,
            GpuDeviceClassTarget deviceClass,
            long computeUnits,
            long globalMemoryBytes,
            long localMemoryBytes,
            long maxWorkGroupSize,
            long preferredVectorWidthFloat,
            boolean unifiedMemory,
            boolean supportsDoublePrecision,
            boolean supportsImages,
            boolean supportsImage3dWrites,
            boolean supportsAtomics,
            boolean supportsSubgroups
    ) {
        return openCl(
                backendName,
                deviceId,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                "unknown",
                platformName,
                platformVersion,
                deviceClass,
                computeUnits,
                globalMemoryBytes,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                unifiedMemory,
                supportsDoublePrecision,
                supportsImages,
                supportsImage3dWrites,
                supportsAtomics,
                supportsSubgroups
        );
    }

    public static GpuRuntimeDeviceProfile cuda(
            String deviceId,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText,
            GpuDeviceClassTarget deviceClass,
            long globalMemoryBytes,
            String platformName,
            String platformVersion
    ) {
        return new GpuRuntimeDeviceProfile(
                GpuBackendTarget.CUDA,
                "CUDA",
                deviceId,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                "unknown",
                deviceClass,
                -1L,
                globalMemoryBytes,
                -1L,
                -1L,
                -1L,
                false,
                false,
                false,
                false,
                false,
                false,
                platformName,
                platformVersion
        );
    }

    public GpuRuntimeDeviceProfile withBackendName(String value) {
        return new GpuRuntimeDeviceProfile(
                backendTarget,
                value,
                deviceId,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                compilerVersion,
                deviceClass,
                computeUnits,
                globalMemoryBytes,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                unifiedMemory,
                supportsDoublePrecision,
                supportsImages,
                supportsImage3dWrites,
                supportsAtomics,
                supportsSubgroups,
                platformName,
                platformVersion
        );
    }

    public String cudaRuntimeVersion() {
        if (backendTarget != GpuBackendTarget.CUDA) {
            return "not-cuda";
        }
        String fromApiText = firstMatch(CUDA_RUNTIME_VERSION, apiVersionText);
        return "unknown".equals(fromApiText)
                ? firstMatch(CUDA_RUNTIME_VERSION, platformVersion)
                : fromApiText;
    }

    public String cudaComputeCapability() {
        if (backendTarget != GpuBackendTarget.CUDA) {
            return "not-cuda";
        }
        return firstMatch(CUDA_COMPUTE_CAPABILITY, apiVersionText);
    }

    public Set<GpuRuntimeCapability> runtimeCapabilities() {
        EnumSet<GpuRuntimeCapability> capabilities = EnumSet.noneOf(GpuRuntimeCapability.class);
        if (deviceClass != GpuDeviceClassTarget.UNKNOWN) {
            capabilities.add(GpuRuntimeCapability.DEVICE_CLASS);
        }
        if (!"unknown".equals(driverVersion)) {
            capabilities.add(GpuRuntimeCapability.DRIVER_VERSION);
        }
        if (!"unknown".equals(apiVersionText)) {
            capabilities.add(GpuRuntimeCapability.RUNTIME_VERSION);
        }
        if (!"unknown".equals(compilerVersion)) {
            capabilities.add(GpuRuntimeCapability.COMPILER_VERSION);
        }
        if (computeUnits > 0L) {
            capabilities.add(GpuRuntimeCapability.COMPUTE_UNITS);
        }
        if (globalMemoryBytes > 0L) {
            capabilities.add(GpuRuntimeCapability.GLOBAL_MEMORY);
        }
        if (localMemoryBytes > 0L) {
            capabilities.add(GpuRuntimeCapability.LOCAL_MEMORY);
        }
        if (maxWorkGroupSize > 0L) {
            capabilities.add(GpuRuntimeCapability.MAX_WORK_GROUP_SIZE);
        }
        if (preferredVectorWidthFloat > 0L) {
            capabilities.add(GpuRuntimeCapability.PREFERRED_FLOAT_VECTOR_WIDTH);
            capabilities.add(GpuRuntimeCapability.VECTOR_TYPES);
        }
        if (unifiedMemory) {
            capabilities.add(GpuRuntimeCapability.UNIFIED_MEMORY);
        }
        if (supportsDoublePrecision) {
            capabilities.add(GpuRuntimeCapability.FP64);
        }
        if (supportsImages) {
            capabilities.add(GpuRuntimeCapability.IMAGES);
            capabilities.add(GpuRuntimeCapability.IMAGE_ABI);
        }
        if (supportsImage3dWrites) {
            capabilities.add(GpuRuntimeCapability.IMAGES);
            capabilities.add(GpuRuntimeCapability.IMAGE_ABI);
            capabilities.add(GpuRuntimeCapability.IMAGE_3D_WRITES);
        }
        if (supportsAtomics) {
            capabilities.add(GpuRuntimeCapability.ATOMICS);
        }
        if (supportsSubgroups) {
            capabilities.add(GpuRuntimeCapability.SUBGROUPS);
        }
        if (backendTarget == GpuBackendTarget.CUDA && !"unknown".equals(cudaComputeCapability())) {
            capabilities.add(GpuRuntimeCapability.COMPUTE_CAPABILITY);
        }
        if (backendTarget == GpuBackendTarget.OPENCL || backendTarget == GpuBackendTarget.CUDA) {
            capabilities.add(GpuRuntimeCapability.ADDRESS_SPACE_GLOBAL);
            capabilities.add(GpuRuntimeCapability.ADDRESS_SPACE_LOCAL);
            capabilities.add(GpuRuntimeCapability.ADDRESS_SPACE_CONSTANT);
            capabilities.add(GpuRuntimeCapability.STRUCT_ABI);
        }
        return capabilities.isEmpty() ? Set.of() : Collections.unmodifiableSet(capabilities);
    }

    public boolean supportsCapability(GpuRuntimeCapability capability) {
        return capability != null && runtimeCapabilities().contains(capability);
    }

    public Map<String, String> capabilityFacts() {
        LinkedHashMap<String, String> facts = new LinkedHashMap<>();
        facts.put("backendTarget", backendTarget.name());
        facts.put("backendName", backendName);
        facts.put("deviceId", deviceId);
        facts.put("deviceLabel", deviceLabel);
        facts.put("vendor", vendor);
        facts.put("driverVersion", driverVersion);
        facts.put("apiVersionText", apiVersionText);
        facts.put("compilerVersion", compilerVersion);
        facts.put("deviceClass", deviceClass.name().toLowerCase(Locale.ROOT));
        facts.put("platformName", platformName);
        facts.put("platformVersion", platformVersion);
        facts.put("computeUnits", Long.toString(computeUnits));
        facts.put("globalMemoryBytes", Long.toString(globalMemoryBytes));
        facts.put("localMemoryBytes", Long.toString(localMemoryBytes));
        facts.put("maxWorkGroupSize", Long.toString(maxWorkGroupSize));
        facts.put("preferredVectorWidthFloat", Long.toString(preferredVectorWidthFloat));
        facts.put("unifiedMemory", Boolean.toString(unifiedMemory));
        facts.put("supportsDoublePrecision", Boolean.toString(supportsDoublePrecision));
        facts.put("supportsImages", Boolean.toString(supportsImages));
        facts.put("supportsImage3dWrites", Boolean.toString(supportsImage3dWrites));
        facts.put("supportsAtomics", Boolean.toString(supportsAtomics));
        facts.put("supportsSubgroups", Boolean.toString(supportsSubgroups));
        if (backendTarget == GpuBackendTarget.CUDA) {
            facts.put("cuda.runtimeVersion", cudaRuntimeVersion());
            facts.put("cuda.computeCapability", cudaComputeCapability());
        }
        int index = 0;
        for (GpuRuntimeCapability capability : runtimeCapabilities()) {
            facts.put("capability." + index, capability.key());
            facts.put("capability." + capability.key(), "true");
            index++;
        }
        facts.put("capability.count", Integer.toString(index));
        return Collections.unmodifiableMap(facts);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static long normalizeLong(long value) {
        return value < 0L ? -1L : value;
    }

    private static GpuDeviceClassTarget normalizeDeviceClass(GpuDeviceClassTarget value) {
        return value == null || value == GpuDeviceClassTarget.ANY
                ? GpuDeviceClassTarget.UNKNOWN
                : value;
    }

    private static String firstMatch(Pattern pattern, String value) {
        String text = normalize(value);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : "unknown";
    }
}
