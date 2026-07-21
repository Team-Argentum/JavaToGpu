package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Built-in CUDA Driver API readback bridge for READ_WRITE primitive/vector/struct array device allocations.
 */
final class CudaDriverReadbackBridge implements CudaKernelReadbackBridge {

    static final String ID = "cuda-readback:driver";

    @Override
    public String readbackId() {
        return ID;
    }

    @Override
    public int readbackOrder() {
        return 100;
    }

    @Override
    public boolean supports(CudaKernelReadbackRequest request) {
        return request != null
                && GpuBackendCompileOptions.CUDA_READBACK_DRIVER.equals(request.readbackMode());
    }

    @Override
    public CudaKernelReadbackResult readBack(CudaKernelReadbackRequest request) {
        CudaDriverLoadedModule loadedModule = request.preparedKernel().moduleLoadResult().loadedModule();
        if (loadedModule == null) {
            return CudaKernelReadbackResult.unsupported(
                    request.readbackMode(),
                    request.readbackRequiredCount(),
                    request.readbackAlreadyCompletedCount(),
                    List.of("cuda-driver-module-handle-missing"),
                    List.of("CUDA driver readback requires a real CUDA Driver API module handle")
            );
        }
        CudaArgumentBindingResult bindingResult = request.preparedKernel().argumentBindingResult();
        CudaKernelArgumentFrame argumentFrame = bindingResult == null ? null : bindingResult.argumentFrame();
        if (argumentFrame == null) {
            return CudaKernelReadbackResult.unsupported(
                    request.readbackMode(),
                    request.readbackRequiredCount(),
                    request.readbackAlreadyCompletedCount(),
                    List.of("cuda-driver-argument-frame-missing"),
                    List.of("CUDA driver readback requires a prepared native argument frame")
            );
        }

        ReadbackSymbols symbols = resolveReadbackSymbols(loadedModule);
        if (!symbols.available()) {
            return CudaKernelReadbackResult.unsupported(
                    request.readbackMode(),
                    request.readbackRequiredCount(),
                    request.readbackAlreadyCompletedCount(),
                    symbols.blockers(),
                    List.of("CUDA Driver API readback symbols are required for native readback")
            );
        }

        int completedCount = request.readbackAlreadyCompletedCount();
        for (CudaDriverDeviceAllocation allocation : argumentFrame.deviceAllocations()) {
            if (!allocation.readbackRequired()) {
                continue;
            }
            Object values = allocation.hostArray();
            if (!isSupportedReadbackArray(values)) {
                return CudaKernelReadbackResult.unsupported(
                        request.readbackMode(),
                        request.readbackRequiredCount(),
                        completedCount,
                        List.of("cuda-driver-readback-array-type-unsupported:" + allocation.parameterIndex()),
                        List.of("CUDA driver readback currently supports only READ_WRITE primitive/vector/struct array allocations")
                );
            }
            if (allocation.byteSize() > Integer.MAX_VALUE) {
                return CudaKernelReadbackResult.unsupported(
                        request.readbackMode(),
                        request.readbackRequiredCount(),
                        completedCount,
                        List.of("cuda-driver-readback-too-large:" + allocation.parameterIndex()),
                        List.of("CUDA driver readback buffer is too large for the current Java host copy path")
                );
            }
            int status = copyArrayBack(loadedModule, symbols, allocation, values);
            allocation.recordReadbackStatus(status);
            if (status != CudaDriverLibrary.CUDA_SUCCESS) {
                return CudaKernelReadbackResult.failed(
                        readbackId(),
                        request.readbackRequiredCount(),
                        completedCount,
                        List.of("cuda-driver-cuMemcpyDtoH-failed:" + status),
                        List.of("CUDA cuMemcpyDtoH failed for parameter "
                                + allocation.parameterName()
                                + " with code "
                                + status)
                );
            }
            completedCount++;
        }
        return CudaKernelReadbackResult.succeeded(
                readbackId(),
                request.readbackRequiredCount(),
                completedCount,
                List.of("CUDA driver primitive/vector/struct array readback completed for "
                        + (completedCount - request.readbackAlreadyCompletedCount())
                        + " allocation(s)")
        );
    }

    private static boolean isSupportedReadbackArray(Object values) {
        return values instanceof byte[]
                || values instanceof short[]
                || values instanceof char[]
                || values instanceof int[]
                || values instanceof long[]
                || values instanceof float[]
                || values instanceof double[]
                || CudaValuePacker.isVectorArrayInstance(values)
                || CudaValuePacker.isStructArrayInstance(values);
    }

    private static int copyArrayBack(
            CudaDriverLoadedModule loadedModule,
            ReadbackSymbols symbols,
            CudaDriverDeviceAllocation allocation,
            Object values
    ) {
        ByteBuffer hostBuffer = MemoryUtil.memAlloc((int) allocation.byteSize());
        try {
            int status = loadedModule.driverApiInvoker().cuMemcpyDtoH(
                    MemoryUtil.memAddress(hostBuffer),
                    allocation.devicePointer(),
                    allocation.byteSize(),
                    symbols.cuMemcpyDtoH()
            );
            if (status == CudaDriverLibrary.CUDA_SUCCESS) {
                readArrayFromBuffer(hostBuffer.order(ByteOrder.nativeOrder()), allocation, values);
            }
            return status;
        } finally {
            MemoryUtil.memFree(hostBuffer);
        }
    }

    private static void readArrayFromBuffer(
            ByteBuffer hostBuffer,
            CudaDriverDeviceAllocation allocation,
            Object values
    ) {
        if (CudaValuePacker.isVectorArrayInstance(values)) {
            CudaValuePacker.unpackVectorArray(
                    hostBuffer,
                    allocation.javaType(),
                    values,
                    allocation.hostElementOffset(),
                    allocation.elementCount()
            );
            return;
        }
        if (CudaValuePacker.isStructArrayInstance(values)) {
            CudaValuePacker.unpackStructArray(
                    hostBuffer,
                    values,
                    allocation.hostElementOffset(),
                    allocation.elementCount()
            );
            return;
        }
        readPrimitiveArrayFromBuffer(hostBuffer, values, allocation.hostElementOffset(), allocation.elementCount());
    }

    private static void readPrimitiveArrayFromBuffer(ByteBuffer hostBuffer, Object values, int offset, int elementCount) {
        if (values instanceof byte[] array) {
            hostBuffer.get(array, offset, elementCount);
        } else if (values instanceof short[] array) {
            hostBuffer.asShortBuffer().get(array, offset, elementCount);
        } else if (values instanceof char[] array) {
            hostBuffer.asCharBuffer().get(array, offset, elementCount);
        } else if (values instanceof int[] array) {
            hostBuffer.asIntBuffer().get(array, offset, elementCount);
        } else if (values instanceof long[] array) {
            hostBuffer.asLongBuffer().get(array, offset, elementCount);
        } else if (values instanceof float[] array) {
            hostBuffer.asFloatBuffer().get(array, offset, elementCount);
        } else if (values instanceof double[] array) {
            hostBuffer.asDoubleBuffer().get(array, offset, elementCount);
        }
    }

    private static ReadbackSymbols resolveReadbackSymbols(CudaDriverLoadedModule loadedModule) {
        ArrayList<String> missing = new ArrayList<>();
        long cuMemcpyDtoH = requiredSymbol(loadedModule, "cuMemcpyDtoH_v2", missing);
        return new ReadbackSymbols(cuMemcpyDtoH, missing);
    }

    private static long requiredSymbol(CudaDriverLoadedModule loadedModule, String name, List<String> missing) {
        long address = loadedModule.findSymbol(name);
        if (address == 0L) {
            missing.add(name);
        }
        return address;
    }

    private record ReadbackSymbols(long cuMemcpyDtoH, List<String> missingSymbols) {
        private ReadbackSymbols {
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
}
