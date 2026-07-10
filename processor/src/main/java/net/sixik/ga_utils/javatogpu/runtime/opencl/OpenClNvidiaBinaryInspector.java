package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optionally inspects NVIDIA OpenCL binaries with CUDA command-line tools.
 */
final class OpenClNvidiaBinaryInspector {

    static final String CUOBJDUMP_PROPERTY = "javatogpu.opencl.nvidiaCuobjdumpPath";
    static final String NVDISASM_PROPERTY = "javatogpu.opencl.nvidiaNvdisasmPath";
    static final String PTXAS_PROPERTY = "javatogpu.opencl.nvidiaPtxasPath";
    static final String CUOBJDUMP_ENVIRONMENT = "JTG_OPENCL_CUOBJDUMP";
    static final String NVDISASM_ENVIRONMENT = "JTG_OPENCL_NVDISASM";
    static final String PTXAS_ENVIRONMENT = "JTG_OPENCL_PTXAS";

    private static final long TOOL_TIMEOUT_SECONDS = 10L;
    private static final int MAX_OUTPUT_BYTES = 1024 * 1024;
    private static final Pattern REGISTERS = Pattern.compile("\\bREG(?:ISTERS?)?\\s*[:=]\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern USED_REGISTERS = Pattern.compile("\\bUsed\\s+(\\d+)\\s+registers?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern STACK = Pattern.compile("\\bSTACK\\s*[:=]\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern STACK_FRAME = Pattern.compile("\\bstack frame(?: size)?\\s*[:=]?\\s*(\\d+)\\s*bytes?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPILL_STORES = Pattern.compile("(\\d+)\\s+bytes?\\s+spill stores?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPILL_LOADS = Pattern.compile("(\\d+)\\s+bytes?\\s+spill loads?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PTX_TARGET = Pattern.compile("^\\s*\\.target\\s+([^,\\s]+)", Pattern.MULTILINE);

    private OpenClNvidiaBinaryInspector() {
    }

    static Result inspect(OpenClProgramBinaryReader.Result binary, GpuRuntimeDeviceProfile deviceProfile) {
        if (binary == null || !binary.captured()) {
            return Result.unavailable("skipped-binary-unavailable", "no captured OpenCL program binary is available");
        }
        String vendor = deviceProfile == null ? "" : deviceProfile.vendor().toLowerCase(Locale.ROOT);
        if (!vendor.contains("nvidia")) {
            return Result.unavailable("skipped-non-nvidia", "external NVIDIA binary inspection is not applicable to this vendor");
        }

        Optional<Path> cuobjdump = findTool(CUOBJDUMP_PROPERTY, CUOBJDUMP_ENVIRONMENT, "cuobjdump");
        Optional<Path> nvdisasm = findTool(NVDISASM_PROPERTY, NVDISASM_ENVIRONMENT, "nvdisasm");
        Optional<Path> ptxas = findTool(PTXAS_PROPERTY, PTXAS_ENVIRONMENT, "ptxas");
        boolean ptx = "ptx".equals(binary.format());
        if (ptx && ptxas.isEmpty()) {
            return Result.unavailable(
                    "tools-unavailable",
                    "ptxas was not found; cuobjdump and nvdisasm cannot inspect the captured PTX directly"
            );
        }
        if (!ptx && cuobjdump.isEmpty() && nvdisasm.isEmpty()) {
            return Result.unavailable("tools-unavailable", "cuobjdump and nvdisasm were not found in configuration, CUDA_PATH, or PATH");
        }

        Path binaryFile = null;
        Path assembledBinaryFile = null;
        try {
            binaryFile = Files.createTempFile("javatogpu-opencl-program-", ptx ? ".ptx" : ".bin");
            byte[] inspectionBinary = ptx ? normalizePtx(binary.binary()) : binary.binary();
            Files.write(binaryFile, inspectionBinary);
            if (ptx) {
                assembledBinaryFile = Files.createTempFile("javatogpu-opencl-program-", ".cubin");
                ArrayList<String> arguments = new ArrayList<>();
                arguments.add("--verbose");
                ptxTarget(inspectionBinary).ifPresent(target -> {
                    arguments.add("--gpu-name");
                    arguments.add(target);
                });
                arguments.add(binaryFile.toString());
                arguments.add("-o");
                arguments.add(assembledBinaryFile.toString());
                ToolResult ptxasResult = runTool(
                        ptxas.orElseThrow(),
                        arguments
                );
                if (!ptxasResult.successful()) {
                    return toolResult("ptxas", ptxas.orElseThrow(), ptxasResult);
                }
                if (!ptxasResult.output().isBlank()) {
                    return parse("ptxas", ptxas.orElseThrow(), ptxasResult.output(), ptxasResult.diagnostic());
                }
                Result assembledInspection = inspectNativeBinary(assembledBinaryFile, cuobjdump, nvdisasm);
                if (!"tools-unavailable".equals(assembledInspection.status())) {
                    return assembledInspection;
                }
                return toolResult("ptxas", ptxas.orElseThrow(), ptxasResult);
            }
            return inspectNativeBinary(binaryFile, cuobjdump, nvdisasm);
        } catch (RuntimeException | IOException exception) {
            return Result.unavailable(
                    "inspection-failed",
                    "NVIDIA binary inspection failed: " + oneLine(exception.getMessage(), exception.getClass().getSimpleName())
            );
        } finally {
            deleteTemporaryFile(assembledBinaryFile);
            deleteTemporaryFile(binaryFile);
        }
    }

    private static Result inspectNativeBinary(
            Path binaryFile,
            Optional<Path> cuobjdump,
            Optional<Path> nvdisasm
    ) {
        if (cuobjdump.isEmpty() && nvdisasm.isEmpty()) {
            return Result.unavailable("tools-unavailable", "cuobjdump and nvdisasm were not found");
        }
        try {
            if (cuobjdump.isPresent()) {
                ToolResult toolResult = runTool(cuobjdump.get(), List.of("--dump-resource-usage", binaryFile.toString()));
                if (toolResult.successful() && !toolResult.output().isBlank()) {
                    return parse("cuobjdump", cuobjdump.get(), toolResult.output(), toolResult.diagnostic());
                }
            }
            if (nvdisasm.isPresent()) {
                ToolResult toolResult = runTool(nvdisasm.get(), List.of("--print-code", binaryFile.toString()));
                if (toolResult.successful() && !toolResult.output().isBlank()) {
                    return parse("nvdisasm", nvdisasm.get(), toolResult.output(), toolResult.diagnostic());
                }
                return toolResult("nvdisasm", nvdisasm.get(), toolResult);
            }
            return Result.unavailable("inspection-failed", "cuobjdump did not return usable resource diagnostics");
        } catch (RuntimeException exception) {
            return Result.unavailable(
                    "inspection-failed",
                    "NVIDIA binary inspection failed: " + oneLine(exception.getMessage(), exception.getClass().getSimpleName())
            );
        }
    }

    private static Result toolResult(String tool, Path toolPath, ToolResult toolResult) {
        return new Result(
                toolResult.status(),
                tool,
                toolPath.toString(),
                -1,
                -1,
                -1,
                "",
                toolResult.output(),
                toolResult.diagnostic()
        );
    }

    private static void deleteTemporaryFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Diagnostic temporary-file cleanup must not affect runtime execution.
        }
    }

    static Result parse(String tool, Path toolPath, String output, String diagnostic) {
        String normalizedOutput = output == null ? "" : output.strip();
        int registers = firstInt(normalizedOutput, REGISTERS, USED_REGISTERS);
        int stackBytes = firstInt(normalizedOutput, STACK, STACK_FRAME);
        int spillStoreBytes = firstInt(normalizedOutput, SPILL_STORES);
        int spillLoadBytes = firstInt(normalizedOutput, SPILL_LOADS);
        StringBuilder normalizedFeedback = new StringBuilder();
        appendMetric(normalizedFeedback, registers, "Used %d registers");
        appendMetric(normalizedFeedback, spillStoreBytes, "%d bytes spill stores");
        appendMetric(normalizedFeedback, spillLoadBytes, "%d bytes spill loads");
        appendMetric(normalizedFeedback, stackBytes, "%d bytes stack frame");
        return new Result(
                normalizedOutput.isBlank() ? "completed-empty" : "recorded",
                tool,
                toolPath == null ? "none" : toolPath.toString(),
                registers,
                spillStoreBytes,
                spillLoadBytes,
                normalizedFeedback.toString().strip(),
                normalizedOutput,
                oneLine(diagnostic, normalizedOutput.isBlank()
                        ? "inspection tool completed without output"
                        : "inspection tool returned diagnostic output")
        );
    }

    static byte[] normalizePtx(byte[] binary) {
        if (binary == null || binary.length == 0) {
            return new byte[0];
        }
        int length = binary.length;
        while (length > 0 && binary[length - 1] == 0) {
            length--;
        }
        return java.util.Arrays.copyOf(binary, length);
    }

    static Optional<String> ptxTarget(byte[] ptx) {
        if (ptx == null || ptx.length == 0) {
            return Optional.empty();
        }
        Matcher matcher = PTX_TARGET.matcher(new String(ptx, StandardCharsets.US_ASCII));
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static Optional<Path> findTool(String property, String environment, String baseName) {
        ArrayList<String> candidates = new ArrayList<>();
        addCandidate(candidates, System.getProperty(property));
        addCandidate(candidates, System.getenv(environment));
        String executableName = isWindows() ? baseName + ".exe" : baseName;
        addCudaCandidate(candidates, System.getenv("CUDA_PATH"), executableName);
        addCudaCandidate(candidates, System.getenv("CUDA_HOME"), executableName);
        if (isWindows()) {
            addDefaultCudaCandidates(candidates, System.getenv("ProgramW6432"), executableName);
            addDefaultCudaCandidates(candidates, System.getenv("ProgramFiles"), executableName);
            addDefaultCudaCandidates(candidates, "C:\\Program Files", executableName);
        }
        String path = System.getenv("PATH");
        if (path != null && !path.isBlank()) {
            for (String directory : path.split(Pattern.quote(java.io.File.pathSeparator))) {
                if (!directory.isBlank()) {
                    candidates.add(Paths.get(directory, executableName).toString());
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

    private static void addCudaCandidate(List<String> candidates, String cudaRoot, String executableName) {
        if (cudaRoot != null && !cudaRoot.isBlank()) {
            candidates.add(Paths.get(cudaRoot, "bin", executableName).toString());
        }
    }

    private static void addDefaultCudaCandidates(
            List<String> candidates,
            String programFiles,
            String executableName
    ) {
        for (Path candidate : defaultCudaInstallationCandidates(programFiles, executableName)) {
            candidates.add(candidate.toString());
        }
    }

    static List<Path> defaultCudaInstallationCandidates(String programFiles, String executableName) {
        if (programFiles == null || programFiles.isBlank() || executableName == null || executableName.isBlank()) {
            return List.of();
        }
        Path cudaRoot;
        try {
            cudaRoot = Paths.get(programFiles, "NVIDIA GPU Computing Toolkit", "CUDA");
        } catch (RuntimeException ignored) {
            return List.of();
        }
        if (!Files.isDirectory(cudaRoot)) {
            return List.of();
        }
        try (java.util.stream.Stream<Path> versions = Files.list(cudaRoot)) {
            return versions
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(
                            path -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER.reversed()
                    ))
                    .map(path -> path.resolve("bin").resolve(executableName))
                    .filter(Files::isRegularFile)
                    .toList();
        } catch (IOException | RuntimeException ignored) {
            return List.of();
        }
    }

    private static ToolResult runTool(Path tool, List<String> arguments) {
        Path outputFile = null;
        Process process = null;
        try {
            outputFile = Files.createTempFile("javatogpu-opencl-inspection-", ".log");
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
                return new ToolResult("timed-out", -1, readOutput(outputFile), "inspection tool exceeded the 10 second timeout");
            }
            int exitCode = process.exitValue();
            String output = readOutput(outputFile);
            return new ToolResult(
                    exitCode == 0 ? (output.isBlank() ? "completed-empty" : "recorded") : "failed",
                    exitCode,
                    output,
                    "inspection tool exited with code " + exitCode
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ToolResult("interrupted", -1, "", "inspection tool wait was interrupted");
        } catch (RuntimeException | IOException exception) {
            return new ToolResult(
                    "failed",
                    -1,
                    "",
                    "inspection tool failed: " + oneLine(exception.getMessage(), exception.getClass().getSimpleName())
            );
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException ignored) {
                    // Diagnostic temporary-file cleanup must not affect runtime execution.
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

    private static int firstInt(String text, Pattern... patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private static void appendMetric(StringBuilder builder, int value, String format) {
        if (value < 0) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(format.formatted(value));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String oneLine(String value, String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private record ToolResult(String status, int exitCode, String output, String diagnostic) {

        private boolean successful() {
            return exitCode == 0;
        }
    }

    record Result(
            String status,
            String tool,
            String toolPath,
            int registers,
            int spillStoreBytes,
            int spillLoadBytes,
            String normalizedFeedback,
            String output,
            String diagnostic
    ) {

        Result {
            status = status == null || status.isBlank() ? "unknown" : status;
            tool = tool == null || tool.isBlank() ? "none" : tool;
            toolPath = toolPath == null || toolPath.isBlank() ? "none" : toolPath;
            registers = Math.max(-1, registers);
            spillStoreBytes = Math.max(-1, spillStoreBytes);
            spillLoadBytes = Math.max(-1, spillLoadBytes);
            normalizedFeedback = normalizedFeedback == null ? "" : normalizedFeedback.strip();
            output = output == null ? "" : output.strip();
            diagnostic = oneLine(diagnostic, "none");
        }

        static Result unavailable(String status, String diagnostic) {
            return new Result(status, "none", "none", -1, -1, -1, "", "", diagnostic);
        }
    }
}
