package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;

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
 * Optional process-backed CUDA compiler bridge using nvcc to emit PTX.
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

        Path workDirectory = null;
        try {
            workDirectory = Files.createTempDirectory("javatogpu-cuda-nvcc-");
            Path sourceFile = workDirectory.resolve(safeKernelName(request.kernelName()) + ".cu");
            Path ptxFile = workDirectory.resolve(safeKernelName(request.kernelName()) + ".ptx");
            Files.writeString(sourceFile, request.source(), StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(request.nvccPath().orElse("nvcc"));
            command.add("--ptx");
            command.add(sourceFile.toString());
            command.add("-o");
            command.add(ptxFile.toString());
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
                        List.of("cuda-nvcc-process-failed:" + process.exitValue()),
                        List.of("nvcc exited with code " + process.exitValue())
                );
            }
            if (!Files.exists(ptxFile)) {
                return CudaNativeCompilationResult.failed(
                        bridgeId(),
                        compileLog,
                        List.of("cuda-nvcc-ptx-missing"),
                        List.of("nvcc succeeded but did not produce a PTX file")
                );
            }
            String ptx = Files.readString(ptxFile, StandardCharsets.UTF_8);
            GpuBackendModuleArtifact module = GpuBackendModuleArtifact.ptx(
                    ptx,
                    "nvcc://" + ptxFile.getFileName(),
                    bridgeId()
            );
            return CudaNativeCompilationResult.succeeded(
                    bridgeId(),
                    module,
                    compileLog,
                    List.of("nvcc emitted PTX for " + request.kernelName())
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

    private static String safeKernelName(String kernelName) {
        String normalized = kernelName == null ? "kernel" : kernelName.replaceAll("[^A-Za-z0-9._-]", "_");
        return normalized.isBlank() ? "kernel" : normalized;
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
