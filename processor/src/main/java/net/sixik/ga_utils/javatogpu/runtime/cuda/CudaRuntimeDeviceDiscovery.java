package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelection;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CUDA inventory discovery backed by {@code nvidia-smi}.
 *
 * <p>This adapter deliberately does not compile or execute CUDA kernels. It only proves that CUDA-capable NVIDIA
 * devices can be surfaced through the same backend-neutral discovery result used by OpenCL.</p>
 */
public final class CudaRuntimeDeviceDiscovery {

    public static final String NVIDIA_SMI_PROPERTY = "javatogpu.cuda.nvidiaSmiPath";
    public static final String NVIDIA_SMI_ENVIRONMENT = "JTG_CUDA_NVIDIA_SMI";

    private static final long TOOL_TIMEOUT_SECONDS = 5L;
    private static final int MAX_OUTPUT_BYTES = 256 * 1024;
    private static final Pattern CUDA_VERSION = Pattern.compile("CUDA\\s+Version\\s*:\\s*([0-9]+(?:\\.[0-9]+)*)");
    private static final Pattern FIRST_NUMBER = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)");
    private static final List<String> RICH_QUERY = List.of(
            "--query-gpu=index,uuid,name,memory.total,driver_version,compute_cap",
            "--format=csv,noheader,nounits"
    );
    private static final List<String> BASIC_QUERY = List.of(
            "--query-gpu=index,uuid,name,memory.total,driver_version",
            "--format=csv,noheader,nounits"
    );

    private CudaRuntimeDeviceDiscovery() {
    }

    /**
     * Discovers CUDA-visible NVIDIA devices and runs the shared deterministic device policy over them.
     */
    public static GpuRuntimeDeviceDiscoveryResult discover(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry
    ) {
        GpuRuntimeCompileOptions resolvedOptions = normalizeCudaOptions(compileOptions);
        GpuRuntimeDevicePolicyRegistry resolvedRegistry = Objects.requireNonNullElseGet(
                devicePolicyRegistry,
                GpuRuntimeDevicePolicyRegistry::loadWithBuiltIns
        );
        Optional<Path> nvidiaSmi = findTool();
        if (nvidiaSmi.isEmpty()) {
            return GpuRuntimeDeviceDiscoveryResult.unavailable(
                    GpuBackendTarget.CUDA,
                    "CUDA",
                    "cuda-nvidia-smi-not-found",
                    new IllegalStateException("nvidia-smi was not found in configuration, common install paths, or PATH")
            );
        }

        try {
            ArrayList<String> diagnostics = new ArrayList<>();
            ToolResult query = runTool(nvidiaSmi.orElseThrow(), RICH_QUERY);
            boolean richQuery = true;
            if (!query.successful() || query.output().isBlank()) {
                diagnostics.add("nvidia-smi rich CUDA inventory query failed; falling back to basic inventory: "
                        + oneLine(query.diagnostic(), "query failed"));
                query = runTool(nvidiaSmi.orElseThrow(), BASIC_QUERY);
                richQuery = false;
            }
            if (!query.successful()) {
                return GpuRuntimeDeviceDiscoveryResult.unavailable(
                        GpuBackendTarget.CUDA,
                        "CUDA",
                        "cuda-device-discovery-failed",
                        new IllegalStateException(oneLine(query.diagnostic(), "nvidia-smi query failed"))
                );
            }

            ToolResult overview = runTool(nvidiaSmi.orElseThrow(), List.of());
            String cudaVersion = overview.successful()
                    ? parseCudaVersion(overview.output()).orElse("unknown")
                    : "unknown";
            if (!overview.successful()) {
                diagnostics.add("nvidia-smi CUDA version overview query failed: "
                        + oneLine(overview.diagnostic(), "version query failed"));
            }
            List<GpuRuntimeDeviceProfile> profiles = parseNvidiaSmiCsv(query.output(), richQuery, cudaVersion);
            if (profiles.isEmpty()) {
                return GpuRuntimeDeviceDiscoveryResult.unavailable(
                        GpuBackendTarget.CUDA,
                        "CUDA",
                        "cuda-devices-missing",
                        new IllegalStateException("nvidia-smi did not return any CUDA device rows")
                );
            }

            if ("unknown".equals(cudaVersion)) {
                diagnostics.add("nvidia-smi did not report a CUDA runtime version");
            }
            diagnostics.add("CUDA discovery is inventory-only; CUDA runtime execution backend is not implemented yet");
            GpuRuntimeDeviceSelection selection = resolvedRegistry.select(
                    GpuRuntimeDevicePolicyContext.forBackendDiscovery(resolvedOptions, profiles)
            ).withAdditionalDiagnostics(diagnostics);
            return GpuRuntimeDeviceDiscoveryResult.available(
                    GpuBackendTarget.CUDA,
                    "CUDA",
                    profiles,
                    selection
            );
        } catch (RuntimeException exception) {
            return GpuRuntimeDeviceDiscoveryResult.unavailable(
                    GpuBackendTarget.CUDA,
                    "CUDA",
                    "cuda-device-discovery-failed",
                    exception
            );
        }
    }

    static List<GpuRuntimeDeviceProfile> parseNvidiaSmiCsv(
            String csv,
            boolean includesComputeCapability,
            String cudaVersion
    ) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        ArrayList<GpuRuntimeDeviceProfile> profiles = new ArrayList<>();
        String normalizedCudaVersion = normalize(cudaVersion);
        String apiPrefix = "unknown".equals(normalizedCudaVersion) ? "CUDA unknown" : "CUDA " + normalizedCudaVersion;
        for (String rawLine : csv.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.strip();
            if (line.isBlank()) {
                continue;
            }
            List<String> columns = splitCsvLine(line);
            int requiredColumns = includesComputeCapability ? 6 : 5;
            if (columns.size() < requiredColumns || "index".equalsIgnoreCase(columns.get(0))) {
                continue;
            }
            String index = normalize(columns.get(0));
            String uuid = normalize(columns.get(1));
            String name = normalize(columns.get(2));
            long memoryBytes = parseMemoryBytes(columns.get(3));
            String driverVersion = normalize(columns.get(4));
            String computeCapability = includesComputeCapability ? normalize(columns.get(5)) : "unknown";
            String apiVersion = "unknown".equals(computeCapability)
                    ? apiPrefix
                    : apiPrefix + ", compute capability " + computeCapability;
            String platformVersion = platformVersion(driverVersion, normalizedCudaVersion);
            String deviceId = "unknown".equals(uuid) ? "cuda-" + index : uuid;
            profiles.add(GpuRuntimeDeviceProfile.cuda(
                    deviceId,
                    name,
                    "NVIDIA",
                    driverVersion,
                    apiVersion,
                    GpuDeviceClassTarget.DGPU,
                    memoryBytes,
                    "NVIDIA CUDA",
                    platformVersion
            ));
        }
        return List.copyOf(profiles);
    }

    static Optional<String> parseCudaVersion(String output) {
        if (output == null || output.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = CUDA_VERSION.matcher(output);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static GpuRuntimeCompileOptions normalizeCudaOptions(GpuRuntimeCompileOptions compileOptions) {
        GpuRuntimeCompileOptions source = compileOptions == null
                ? GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA)
                : compileOptions;
        GpuBackendCompileOptions sourceBackendOptions = source.backendOptions();
        GpuBackendCompileOptions cudaOptions = sourceBackendOptions.backendTarget() == GpuBackendTarget.CUDA
                ? sourceBackendOptions
                : new GpuBackendCompileOptions(GpuBackendTarget.CUDA, List.of(), sourceBackendOptions.properties());
        return new GpuRuntimeCompileOptions(
                GpuBackendTarget.CUDA,
                List.of(),
                source.optimizationProfile(),
                cudaOptions,
                source.deviceOverride(),
                source.devicePreference()
        );
    }

    private static Optional<Path> findTool() {
        ArrayList<String> candidates = new ArrayList<>();
        addCandidate(candidates, System.getProperty(NVIDIA_SMI_PROPERTY));
        addCandidate(candidates, System.getenv(NVIDIA_SMI_ENVIRONMENT));
        String executableName = isWindows() ? "nvidia-smi.exe" : "nvidia-smi";
        if (isWindows()) {
            addCandidate(candidates, Paths.get(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"), "System32", executableName).toString());
            addCandidate(candidates, Paths.get("C:\\Windows", "System32", executableName).toString());
            addCandidate(candidates, Paths.get("C:\\Program Files", "NVIDIA Corporation", "NVSMI", executableName).toString());
        } else {
            addCandidate(candidates, "/usr/bin/" + executableName);
            addCandidate(candidates, "/usr/local/bin/" + executableName);
        }
        String path = System.getenv("PATH");
        if (path != null && !path.isBlank()) {
            for (String directory : path.split(Pattern.quote(java.io.File.pathSeparator))) {
                if (!directory.isBlank()) {
                    addCandidate(candidates, Paths.get(directory, executableName).toString());
                }
            }
        }
        for (String candidate : candidates) {
            try {
                Path pathCandidate = Paths.get(candidate).toAbsolutePath().normalize();
                if (Files.isRegularFile(pathCandidate)) {
                    return Optional.of(pathCandidate);
                }
            } catch (RuntimeException ignored) {
                // Ignore malformed optional tool paths and continue discovery.
            }
        }
        return Optional.empty();
    }

    private static void addCandidate(List<String> candidates, String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            candidates.add(candidate.trim());
        }
    }

    private static ToolResult runTool(Path tool, List<String> arguments) {
        Path outputFile = null;
        Process process = null;
        try {
            outputFile = Files.createTempFile("javatogpu-cuda-discovery-", ".log");
            ArrayList<String> command = new ArrayList<>(arguments.size() + 1);
            command.add(tool.toString());
            command.addAll(arguments);
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile())
                    .start();
            boolean completed = process.waitFor(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(2L, TimeUnit.SECONDS);
                return new ToolResult(false, readOutput(outputFile), "nvidia-smi exceeded the 5 second timeout");
            }
            int exitCode = process.exitValue();
            String output = readOutput(outputFile);
            return new ToolResult(
                    exitCode == 0,
                    output,
                    "nvidia-smi exited with code " + exitCode
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ToolResult(false, "", "nvidia-smi wait was interrupted");
        } catch (RuntimeException | IOException exception) {
            return new ToolResult(
                    false,
                    "",
                    "nvidia-smi failed: " + oneLine(exception.getMessage(), exception.getClass().getSimpleName())
            );
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException ignored) {
                    // Diagnostic temporary-file cleanup must not affect runtime discovery.
                }
            }
        }
    }

    private static String readOutput(Path outputFile) throws IOException {
        try (InputStream input = Files.newInputStream(outputFile)) {
            byte[] bytes = input.readNBytes(MAX_OUTPUT_BYTES + 1);
            int length = Math.min(bytes.length, MAX_OUTPUT_BYTES);
            String output = new String(bytes, 0, length, StandardCharsets.UTF_8).strip();
            return bytes.length > MAX_OUTPUT_BYTES ? output + System.lineSeparator() + "[output truncated]" : output;
        }
    }

    private static List<String> splitCsvLine(String line) {
        ArrayList<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                quoted = !quoted;
                continue;
            }
            if (character == ',' && !quoted) {
                columns.add(normalize(current.toString()));
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        columns.add(normalize(current.toString()));
        return columns;
    }

    private static long parseMemoryBytes(String value) {
        String normalized = value == null ? "" : value.replace(",", "");
        Matcher matcher = FIRST_NUMBER.matcher(normalized);
        if (!matcher.find()) {
            return -1L;
        }
        try {
            double mib = Double.parseDouble(matcher.group(1));
            return mib <= 0.0D ? -1L : Math.round(mib * 1024.0D * 1024.0D);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static String platformVersion(String driverVersion, String cudaVersion) {
        String driver = normalize(driverVersion);
        String cuda = normalize(cudaVersion);
        StringBuilder builder = new StringBuilder();
        builder.append("driver ").append(driver);
        if (!"unknown".equals(cuda)) {
            builder.append(", CUDA ").append(cuda);
        }
        return builder.toString();
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || "[not supported]".equalsIgnoreCase(normalized)
                || "n/a".equals(normalized.toLowerCase(Locale.ROOT))) {
            return "unknown";
        }
        return normalized;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String oneLine(String value, String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private record ToolResult(boolean successful, String output, String diagnostic) {

        ToolResult {
            output = output == null ? "" : output.strip();
            diagnostic = oneLine(diagnostic, "none");
        }
    }
}
