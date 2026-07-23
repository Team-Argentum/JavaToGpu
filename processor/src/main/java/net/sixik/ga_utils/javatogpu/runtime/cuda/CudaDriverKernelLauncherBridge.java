package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;

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
        if (request == null) {
            return CudaKernelLaunchResult.unsupported(
                    GpuBackendCompileOptions.CUDA_KERNEL_LAUNCHER_DRIVER,
                    List.of("cuda-driver-launch-request-missing"),
                    List.of("CUDA driver kernel launch requires a launch request")
            );
        }
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

        DriverLaunchShape launchShape;
        int sharedMemoryByteSize;
        try {
            GpuRuntimeDeviceProfile deviceProfile = request.preparedKernel().compiledKernel().deviceProfile();
            launchShape = DriverLaunchShape.from(request.executionConfig(), deviceProfile);
            sharedMemoryByteSize = checkedSharedMemoryByteSize(request, deviceProfile);
        } catch (UnsupportedLaunchContractException exception) {
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
                sharedMemoryByteSize,
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
                sharedMemoryByteSize,
                request.readbackRequiredCount(),
                0,
                List.of("CUDA driver kernel launch submitted with "
                        + launchShape.summary()
                        + ", sharedMemoryBytes="
                        + sharedMemoryByteSize),
                launchShape.toResultLaunchShape()
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

    private record DriverLaunchShape(
            int gridDimX,
            int gridDimY,
            int gridDimZ,
            int blockDimX,
            int blockDimY,
            int blockDimZ
    ) {
        private static DriverLaunchShape from(GpuExecutionConfig config, GpuRuntimeDeviceProfile deviceProfile) {
            if (config == null) {
                throw new UnsupportedLaunchContractException(
                        "cuda-driver-launch-config-missing",
                        "CUDA driver kernel launch requires an explicit execution config"
                );
            }
            if (!config.hasExplicitLocalSize()) {
                throw new UnsupportedLaunchContractException(
                        "cuda-driver-launch-local-size-required",
                        "CUDA driver kernel launch requires an explicit local work-group size"
                );
            }
            long blockX = config.localX();
            long blockY = config.dimensions() >= 2 ? config.localY() : 1L;
            long blockZ = config.dimensions() == 3 ? config.localZ() : 1L;
            ensureDivisible("x", config.globalX(), blockX);
            if (config.dimensions() >= 2) {
                ensureDivisible("y", config.globalY(), blockY);
            }
            if (config.dimensions() == 3) {
                ensureDivisible("z", config.globalZ(), blockZ);
            }
            long blockItemCount = checkedProduct("blockItemCount", blockX, blockY, blockZ);
            if (blockItemCount > Integer.MAX_VALUE) {
                throw new UnsupportedLaunchContractException(
                        "cuda-driver-launch-block-item-count-too-large",
                        "CUDA launch block item count exceeds the supported Java int range: " + blockItemCount
                );
            }
            long maxWorkGroupSize = deviceProfile == null ? -1L : deviceProfile.maxWorkGroupSize();
            if (maxWorkGroupSize > 0L && blockItemCount > maxWorkGroupSize) {
                throw new UnsupportedLaunchContractException(
                        "cuda-driver-launch-block-item-count-exceeds-device",
                        "CUDA launch block item count " + blockItemCount
                                + " exceeds device max work-group size " + maxWorkGroupSize
                );
            }
            long gridX = config.globalX() / blockX;
            long gridY = config.dimensions() >= 2 ? config.globalY() / blockY : 1L;
            long gridZ = config.dimensions() == 3 ? config.globalZ() / blockZ : 1L;
            return new DriverLaunchShape(
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

        private CudaKernelLaunchResult.LaunchShape toResultLaunchShape() {
            return new CudaKernelLaunchResult.LaunchShape(
                    gridDimX,
                    gridDimY,
                    gridDimZ,
                    blockDimX,
                    blockDimY,
                    blockDimZ
            );
        }
    }

    private static int checkedSharedMemoryByteSize(
            CudaKernelLaunchRequest request,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        int byteSize;
        try {
            byteSize = request.sharedMemoryByteSize();
        } catch (IllegalStateException exception) {
            throw new UnsupportedLaunchContractException(
                    "cuda-driver-launch-shared-memory-too-large",
                    exception.getMessage()
            );
        }
        long localMemoryBytes = deviceProfile == null ? -1L : deviceProfile.localMemoryBytes();
        if (localMemoryBytes > 0L && byteSize > localMemoryBytes) {
            throw new UnsupportedLaunchContractException(
                    "cuda-driver-launch-shared-memory-exceeds-device-local-memory",
                    "CUDA launch shared-memory byte size " + byteSize
                            + " exceeds device local memory " + localMemoryBytes
            );
        }
        return byteSize;
    }

    private static void ensureDivisible(String axis, long global, long local) {
        if (global % local != 0L) {
            throw new UnsupportedLaunchContractException(
                    "cuda-driver-launch-global-local-mismatch:" + axis,
                    "CUDA launch global " + axis + " dimension " + global
                            + " must be divisible by local " + axis + " dimension " + local
            );
        }
    }

    private static long checkedProduct(String dimensionName, long x, long y, long z) {
        long xy = checkedMultiply(dimensionName, x, y);
        return checkedMultiply(dimensionName, xy, z);
    }

    private static long checkedMultiply(String dimensionName, long left, long right) {
        if (left > Long.MAX_VALUE / right) {
            throw new UnsupportedLaunchContractException(
                    "cuda-driver-launch-dimension-too-large:" + dimensionName,
                    "CUDA launch dimension " + dimensionName + " exceeds the supported Java long range"
            );
        }
        return left * right;
    }

    private static int checkedUnsignedInt(String dimensionName, long value) {
        if (value <= 0L || value > Integer.MAX_VALUE) {
            throw new UnsupportedLaunchContractException(
                    "cuda-driver-launch-dimension-too-large:" + dimensionName,
                    "CUDA launch dimension " + dimensionName + " is outside the supported Java int range: " + value
            );
        }
        return (int) value;
    }

    private static final class UnsupportedLaunchContractException extends RuntimeException {
        private final String blocker;

        private UnsupportedLaunchContractException(String blocker, String message) {
            super(message);
            this.blocker = blocker;
        }

        private String blocker() {
            return blocker;
        }
    }
}
