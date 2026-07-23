package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Deterministic registry for optional CUDA readback bridges.
 */
public final class CudaKernelReadbackBridgeRegistry {

    private final List<CudaKernelReadbackBridge> readbacks;

    private CudaKernelReadbackBridgeRegistry(List<CudaKernelReadbackBridge> readbacks) {
        ArrayList<CudaKernelReadbackBridge> sorted = new ArrayList<>(readbacks == null ? List.of() : readbacks);
        sorted.sort(Comparator
                .comparingInt(CudaKernelReadbackBridge::readbackOrder)
                .thenComparing(CudaKernelReadbackBridge::readbackId)
                .thenComparing(CudaKernelReadbackBridge::readbackVersion));
        this.readbacks = List.copyOf(sorted);
    }

    public static CudaKernelReadbackBridgeRegistry of(List<CudaKernelReadbackBridge> readbacks) {
        return new CudaKernelReadbackBridgeRegistry(readbacks);
    }

    public static CudaKernelReadbackBridgeRegistry loadWithBuiltIns() {
        ArrayList<CudaKernelReadbackBridge> loaded = new ArrayList<>();
        ServiceLoader.load(
                CudaKernelReadbackBridge.class,
                CudaKernelReadbackBridge.class.getClassLoader()
        ).forEach(loaded::add);
        loaded.add(new CudaDriverReadbackBridge());
        return of(loaded);
    }

    public CudaKernelReadbackResult readBack(CudaKernelReadbackRequest request) {
        Objects.requireNonNull(request, "request");
        if (!request.readbackRequested()) {
            return CudaKernelReadbackResult.disabled(
                    request.readbackMode(),
                    request.readbackRequiredCount(),
                    request.readbackAlreadyCompletedCount()
            );
        }
        if (!request.launchResult().succeeded()) {
            return CudaKernelReadbackResult.unsupported(
                    request.readbackMode(),
                    request.readbackRequiredCount(),
                    request.readbackAlreadyCompletedCount(),
                    List.of("cuda-native-launch-required-before-readback"),
                    List.of("CUDA readback requires a successful kernel launch")
            );
        }
        if (request.readbackRequiredCount() <= request.readbackAlreadyCompletedCount()) {
            return CudaKernelReadbackResult.succeeded(
                    "cuda-readback:already-complete",
                    request.readbackRequiredCount(),
                    request.readbackAlreadyCompletedCount(),
                    List.of("CUDA readback already complete after launch")
            );
        }
        for (CudaKernelReadbackBridge readback : readbacks) {
            if (!readback.supports(request)) {
                continue;
            }
            try {
                return Objects.requireNonNull(readback.readBack(request), "CUDA readback result");
            } catch (RuntimeException exception) {
                return CudaKernelReadbackResult.failed(
                        readback.readbackId(),
                        request.readbackRequiredCount(),
                        request.readbackAlreadyCompletedCount(),
                        List.of("cuda-native-readback-failed:" + exception.getClass().getSimpleName()),
                        List.of(exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage())
                );
            }
        }
        return CudaKernelReadbackResult.unsupported(
                request.readbackMode(),
                request.readbackRequiredCount(),
                request.readbackAlreadyCompletedCount(),
                List.of("cuda-native-readback-unavailable:" + request.readbackMode()),
                List.of("No CUDA readback bridge supports mode " + request.readbackMode())
        );
    }

    public List<CudaKernelReadbackBridge> readbacks() {
        return readbacks;
    }
}
