package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleFormat;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBinaryArtifact;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Optional process-backed CUDA compiler bridge using nvcc to emit PTX, CUBIN, or FATBIN modules.
 */
final class CudaNvccProcessCompilerBridge implements CudaNativeCompilerBridge {

    @Override
    public String bridgeId() {
        return "cuda-native-compiler:nvcc";
    }

    @Override
    public int bridgeOrder() {
        return 100;
    }

    @Override
    public boolean supports(CudaNativeCompilationRequest request) {
        return request != null
                && GpuBackendCompileOptions.CUDA_COMPILER_BRIDGE_NVCC.equals(request.bridgeMode());
    }

    @Override
    public CudaNativeCompilationResult compile(CudaNativeCompilationRequest request) {
        if (request.source().isBlank()) {
            return CudaNativeCompilationResult.unsupported(
                    request.bridgeMode(),
                    List.of("cuda-nvcc-source-missing"),
                    List.of("nvcc bridge requires CUDA source text")
            );
        }
        if (request.nvccOutputFormatBlocker().isPresent()) {
            String blocker = request.nvccOutputFormatBlocker().orElseThrow();
            return CudaNativeCompilationResult.unsupported(
                    request.bridgeMode(),
                    List.of(blocker),
                    List.of("nvcc output format must be one of ptx, cubin, or fatbin")
            );
        }
        NvccOutputSpec outputSpec = NvccOutputSpec.from(request.nvccOutputModuleFormat(), request.kernelName());

        Path workDirectory = null;
        try {
            workDirectory = Files.createTempDirectory("javatogpu-cuda-nvcc-");
            Path sourceFile = workDirectory.resolve(safeKernelName(request.kernelName()) + ".cu");
            Path outputFile = workDirectory.resolve(outputSpec.fileName());
            Files.writeString(sourceFile, request.source(), StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(request.nvccPath().orElse("nvcc"));
            command.add(outputSpec.nvccFlag());
            command.add(sourceFile.toString());
            command.add("-o");
            command.add(outputFile.toString());
            command.addAll(request.compilerFlags());

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readProcessOutput(process));
            Duration timeout = request.timeout();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return CudaNativeCompilationResult.failed(
                        bridgeId(),
                        output.join(),
                        List.of("cuda-nvcc-process-timeout"),
                        List.of("nvcc did not finish within " + timeout.toMillis() + " ms")
                );
            }

            String compileLog = output.join();
            if (process.exitValue() != 0) {
                return CudaNativeCompilationResult.failed(
                        bridgeId(),
                        compileLog,
                        nvccFailureBlockers(process.exitValue(), compileLog),
                        nvccFailureDiagnostics(process.exitValue(), compileLog)
                );
            }
            if (!Files.exists(outputFile)) {
                return CudaNativeCompilationResult.failed(
                        bridgeId(),
                        compileLog,
                        List.of("cuda-nvcc-output-missing:" + outputSpec.moduleFormat().key()),
                        List.of("nvcc succeeded but did not produce a " + outputSpec.moduleFormat().key() + " file")
                );
            }
            GpuBackendModuleArtifact module = outputSpec.moduleArtifact(outputFile, bridgeId());
            List<GpuRuntimeBinaryArtifact> binaryArtifacts = outputSpec.binaryArtifact(outputFile);
            return CudaNativeCompilationResult.succeeded(
                    bridgeId(),
                    module,
                    binaryArtifacts,
                    compileLog,
                    List.of("nvcc emitted " + outputSpec.moduleFormat().key() + " for " + request.kernelName())
            );
        } catch (IOException exception) {
            return CudaNativeCompilationResult.failed(
                    bridgeId(),
                    "",
                    List.of("cuda-nvcc-not-available"),
                    List.of(exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage())
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CudaNativeCompilationResult.failed(
                    bridgeId(),
                    "",
                    List.of("cuda-nvcc-process-interrupted"),
                    List.of("nvcc process was interrupted")
            );
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static String readProcessOutput(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static List<String> nvccFailureBlockers(int exitCode, String compileLog) {
        ArrayList<String> blockers = new ArrayList<>();
        String normalized = normalizeCompileLog(compileLog);
        if (looksLikeMissingWindowsHostCompiler(normalized)) {
            blockers.add("cuda-nvcc-host-compiler-missing:cl.exe");
        } else if (looksLikeUnsupportedHostCompiler(normalized)) {
            blockers.add("cuda-nvcc-host-compiler-unsupported");
        } else if (looksLikeVirtualArchitectureNotAllowed(normalized)) {
            blockers.add("cuda-nvcc-virtual-architecture-not-allowed");
        }
        blockers.add("cuda-nvcc-process-failed:" + exitCode);
        return List.copyOf(blockers);
    }

    static List<String> nvccFailureDiagnostics(int exitCode, String compileLog) {
        ArrayList<String> diagnostics = new ArrayList<>();
        String normalized = normalizeCompileLog(compileLog);
        if (looksLikeMissingWindowsHostCompiler(normalized)) {
            diagnostics.add("nvcc could not find cl.exe; run from a Visual Studio Developer Command Prompt or add the Visual C++ host compiler to PATH");
        } else if (looksLikeUnsupportedHostCompiler(normalized)) {
            diagnostics.add("nvcc rejected the configured host compiler; use a CUDA-supported Visual Studio/MSVC toolset or pass an appropriate nvcc host compiler option");
        } else if (looksLikeVirtualArchitectureNotAllowed(normalized)) {
            diagnostics.add("nvcc rejected a virtual compute architecture for a binary module; use an sm_XX architecture for CUBIN/FATBIN output");
        }
        diagnostics.add("nvcc exited with code " + exitCode);
        firstNonBlankLine(compileLog).ifPresent(line -> diagnostics.add("nvcc output: " + line));
        return List.copyOf(diagnostics);
    }

    private static boolean looksLikeMissingWindowsHostCompiler(String normalizedCompileLog) {
        return normalizedCompileLog.contains("cannot find compiler")
                && normalizedCompileLog.contains("cl.exe");
    }

    private static boolean looksLikeUnsupportedHostCompiler(String normalizedCompileLog) {
        return normalizedCompileLog.contains("unsupported host compiler")
                || normalizedCompileLog.contains("unsupported microsoft visual studio version")
                || normalizedCompileLog.contains("host compiler targets unsupported os");
    }

    private static boolean looksLikeVirtualArchitectureNotAllowed(String normalizedCompileLog) {
        return normalizedCompileLog.contains("not allowed when compiling for a virtual compute architecture");
    }

    private static java.util.Optional<String> firstNonBlankLine(String compileLog) {
        if (compileLog == null || compileLog.isBlank()) {
            return java.util.Optional.empty();
        }
        return compileLog.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst();
    }

    private static String normalizeCompileLog(String compileLog) {
        return compileLog == null ? "" : compileLog.toLowerCase(java.util.Locale.ROOT);
    }

    private static String safeKernelName(String kernelName) {
        String normalized = kernelName == null ? "kernel" : kernelName.replaceAll("[^A-Za-z0-9._-]", "_");
        return normalized.isBlank() ? "kernel" : normalized;
    }

    private record NvccOutputSpec(GpuBackendModuleFormat moduleFormat, String nvccFlag, String fileName) {
        private static NvccOutputSpec from(GpuBackendModuleFormat moduleFormat, String kernelName) {
            GpuBackendModuleFormat normalized = moduleFormat == null ? GpuBackendModuleFormat.PTX : moduleFormat;
            String fileStem = safeKernelName(kernelName);
            return switch (normalized) {
                case CUBIN -> new NvccOutputSpec(normalized, "--cubin", fileStem + ".cubin");
                case FATBIN -> new NvccOutputSpec(normalized, "--fatbin", fileStem + ".fatbin");
                default -> new NvccOutputSpec(GpuBackendModuleFormat.PTX, "--ptx", fileStem + ".ptx");
            };
        }

        private GpuBackendModuleArtifact moduleArtifact(Path outputFile, String bridgeId) throws IOException {
            String resource = "nvcc://" + outputFile.getFileName();
            return switch (moduleFormat) {
                case CUBIN -> GpuBackendModuleArtifact.cubin(resource, bridgeId, true);
                case FATBIN -> GpuBackendModuleArtifact.fatbin(resource, bridgeId, true);
                default -> GpuBackendModuleArtifact.ptx(
                        Files.readString(outputFile, StandardCharsets.UTF_8),
                        resource,
                        bridgeId
                );
            };
        }

        private List<GpuRuntimeBinaryArtifact> binaryArtifact(Path outputFile) throws IOException {
            if (moduleFormat != GpuBackendModuleFormat.CUBIN && moduleFormat != GpuBackendModuleFormat.FATBIN) {
                return List.of();
            }
            return List.of(new GpuRuntimeBinaryArtifact(
                    outputFile.getFileName().toString(),
                    moduleFormat == GpuBackendModuleFormat.CUBIN
                            ? "application/x-cuda-cubin"
                            : "application/x-cuda-fatbin",
                    Files.readAllBytes(outputFile)
            ));
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup only; compile result is already determined.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }
}
