package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in CUDA Driver API kernel launcher for already-loaded modules and bound argument frames.
 */
final class CudaDriverKernelLauncherBridge implements CudaKernelLauncherBridge {

    static final String ID = "cuda-kernel-launcher:driver";

    @Override
    public String launcherId() {
        return ID;
    }

    @Override
    public int launcherOrder() {
        return 100;
    }

    @Override
    public boolean supports(CudaKernelLaunchRequest request) {
        return request != null
                && GpuBackendCompileOptions.CUDA_KERNEL_LAUNCHER_DRIVER.equals(request.launcherMode());
    }

    @Override
    public CudaKernelLaunchResult launch(CudaKernelLaunchRequest request) {
        CudaDriverLoadedModule loadedModule = request.preparedKernel().moduleLoadResult().loadedModule();
        if (loadedModule == null || loadedModule.moduleHandle() == 0L) {
            return CudaKernelLaunchResult.unsupported(
                    request.launcherMode(),
                    List.of("cuda-driver-module-handle-missing"),
                    List.of("CUDA driver kernel launch requires a real CUDA Driver API module handle")
            );
        }
        if (loadedModule.functionHandle() == 0L) {
            return CudaKernelLaunchResult.unsupported(
                    request.launcherMode(),
                    List.of("cuda-driver-function-handle-missing"),
                    List.of("CUDA driver kernel launch requires a real CUDA Driver API function handle")
            );
        }

        CudaArgumentBindingResult bindingResult = request.preparedKernel().argumentBindingResult();
        CudaKernelArgumentFrame argumentFrame = bindingResult == null ? null : bindingResult.argumentFrame();
        if (argumentFrame == null) {
            return CudaKernelLaunchResult.unsupported(
                    request.launcherMode(),
                    List.of("cuda-driver-argument-frame-missing"),
                    List.of("CUDA driver kernel launch requires a prepared native argument frame")
            );
        }
        long kernelParameterTableAddress = argumentFrame.kernelParameterTableAddress();
        if (request.kernelParameterTableRequired() && kernelParameterTableAddress == 0L) {
            return CudaKernelLaunchResult.unsupported(
                    request.launcherMode(),
                    List.of("cuda-driver-kernel-parameter-table-missing"),
                    List.of("CUDA driver kernel launch requires a native kernel parameter table for non-LOCAL descriptor arguments")
            );
        }

        LaunchSymbols symbols = resolveLaunchSymbols(loadedModule);
        if (!symbols.available()) {
            return CudaKernelLaunchResult.unsupported(
                    request.launcherMode(),
                    symbols.blockers(),
                    List.of("CUDA Driver API launch symbols are required for native kernel launch")
            );
        }

        LaunchShape launchShape;
        try {
            launchShape = LaunchShape.from(request.executionConfig());
        } catch (UnsupportedLaunchShapeException exception) {
            return CudaKernelLaunchResult.unsupported(
                    request.launcherMode(),
                    List.of(exception.blocker()),
                    List.of(exception.getMessage())
            );
        }

        int launchStatus = loadedModule.driverApiInvoker().cuLaunchKernel(
                loadedModule.functionHandle(),
                launchShape.gridDimX(),
                launchShape.gridDimY(),
                launchShape.gridDimZ(),
                launchShape.blockDimX(),
                launchShape.blockDimY(),
                launchShape.blockDimZ(),
                request.sharedMemoryByteSize(),
                0L,
                kernelParameterTableAddress,
                0L,
                symbols.cuLaunchKernel()
        );
        if (launchStatus != CudaDriverLibrary.CUDA_SUCCESS) {
            return CudaKernelLaunchResult.failed(
                    launcherId(),
                    List.of("cuda-driver-cuLaunchKernel-failed:" + launchStatus),
                    List.of("CUDA cuLaunchKernel failed with code " + launchStatus)
            );
        }
        return CudaKernelLaunchResult.succeeded(
                launcherId(),
                request.executionConfig(),
                request.sharedMemoryByteSize(),
                request.readbackRequiredCount(),
                0,
                List.of("CUDA driver kernel launch submitted with "
                        + launchShape.summary()
                        + ", sharedMemoryBytes="
                        + request.sharedMemoryByteSize())
        );
    }

    private static LaunchSymbols resolveLaunchSymbols(CudaDriverLoadedModule loadedModule) {
        ArrayList<String> missing = new ArrayList<>();
        long cuLaunchKernel = requiredSymbol(loadedModule, "cuLaunchKernel", missing);
        return new LaunchSymbols(cuLaunchKernel, missing);
    }

    private static long requiredSymbol(CudaDriverLoadedModule loadedModule, String name, List<String> missing) {
        long address = loadedModule.findSymbol(name);
        if (address == 0L) {
            missing.add(name);
        }
        return address;
    }

    private record LaunchSymbols(long cuLaunchKernel, List<String> missingSymbols) {
        private LaunchSymbols {
            missingSymbols = missingSymbols == null ? List.of() : List.copyOf(missingSymbols);
        }

        private boolean available() {
            return missingSymbols.isEmpty();
        }

        private List<String> blockers() {
            return java.util.stream.Stream.concat(
                            java.util.stream.Stream.of("cuda-driver-symbols-missing"),
                            missingSymbols.stream().map(symbol -> "cuda-driver-symbol-missing:" + symbol)
                    )
                    .toList();
        }
    }

    private record LaunchShape(
            int gridDimX,
            int gridDimY,
            int gridDimZ,
            int blockDimX,
            int blockDimY,
            int blockDimZ
    ) {
        private static LaunchShape from(GpuExecutionConfig config) {
            long blockX = config.localX() > 0L ? config.localX() : 1L;
            long blockY = config.dimensions() >= 2 && config.localY() > 0L ? config.localY() : 1L;
            long blockZ = config.dimensions() == 3 && config.localZ() > 0L ? config.localZ() : 1L;
            long gridX = ceilDiv(config.globalX(), blockX);
            long gridY = config.dimensions() >= 2 ? ceilDiv(config.globalY(), blockY) : 1L;
            long gridZ = config.dimensions() == 3 ? ceilDiv(config.globalZ(), blockZ) : 1L;
            return new LaunchShape(
                    checkedUnsignedInt("gridDimX", gridX),
                    checkedUnsignedInt("gridDimY", gridY),
                    checkedUnsignedInt("gridDimZ", gridZ),
                    checkedUnsignedInt("blockDimX", blockX),
                    checkedUnsignedInt("blockDimY", blockY),
                    checkedUnsignedInt("blockDimZ", blockZ)
            );
        }

        private String summary() {
            return "grid=" + gridDimX + "x" + gridDimY + "x" + gridDimZ
                    + ", block=" + blockDimX + "x" + blockDimY + "x" + blockDimZ;
        }
    }

    private static long ceilDiv(long value, long divisor) {
        return ((value - 1L) / divisor) + 1L;
    }

    private static int checkedUnsignedInt(String dimensionName, long value) {
        if (value <= 0L || value > Integer.MAX_VALUE) {
            throw new UnsupportedLaunchShapeException(
                    "cuda-driver-launch-dimension-too-large:" + dimensionName,
                    "CUDA launch dimension " + dimensionName + " is outside the supported Java int range: " + value
            );
        }
        return (int) value;
    }

    private static final class UnsupportedLaunchShapeException extends RuntimeException {
        private final String blocker;

        private UnsupportedLaunchShapeException(String blocker, String message) {
            super(message);
            this.blocker = blocker;
        }

        private String blocker() {
            return blocker;
        }
    }
}
