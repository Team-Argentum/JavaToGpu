package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Deterministic registry for optional CUDA argument binders.
 */
public final class CudaArgumentBinderBridgeRegistry {

    private final List<CudaArgumentBinderBridge> binders;

    private CudaArgumentBinderBridgeRegistry(List<CudaArgumentBinderBridge> binders) {
        ArrayList<CudaArgumentBinderBridge> sorted = new ArrayList<>(binders == null ? List.of() : binders);
        sorted.sort(Comparator
                .comparingInt(CudaArgumentBinderBridge::binderOrder)
                .thenComparing(CudaArgumentBinderBridge::binderId)
                .thenComparing(CudaArgumentBinderBridge::binderVersion));
        this.binders = List.copyOf(sorted);
    }

    public static CudaArgumentBinderBridgeRegistry of(List<CudaArgumentBinderBridge> binders) {
        return new CudaArgumentBinderBridgeRegistry(binders);
    }

    public static CudaArgumentBinderBridgeRegistry loadWithBuiltIns() {
        ArrayList<CudaArgumentBinderBridge> loaded = new ArrayList<>();
        ServiceLoader.load(
                CudaArgumentBinderBridge.class,
                CudaArgumentBinderBridge.class.getClassLoader()
        ).forEach(loaded::add);
        loaded.add(new CudaDriverArgumentBinderBridge());
        return of(loaded);
    }

    public CudaArgumentBindingResult bind(CudaArgumentBindingRequest request) {
        Objects.requireNonNull(request, "request");
        if (!request.argumentBinderRequested()) {
            return CudaArgumentBindingResult.disabled(request.binderMode());
        }
        for (CudaArgumentBinderBridge binder : binders) {
            if (!binder.supports(request)) {
                continue;
            }
            try {
                return Objects.requireNonNull(binder.bind(request), "CUDA argument binder result");
            } catch (RuntimeException exception) {
                return CudaArgumentBindingResult.failed(
                        binder.binderId(),
                        List.of("cuda-native-argument-binder-failed:" + exception.getClass().getSimpleName()),
                        List.of(exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage())
                );
            }
        }
        return CudaArgumentBindingResult.unsupported(
                request.binderMode(),
                List.of("cuda-native-argument-binder-unavailable:" + request.binderMode()),
                List.of("No CUDA argument binder bridge supports mode " + request.binderMode())
        );
    }

    public List<CudaArgumentBinderBridge> binders() {
        return binders;
    }
}
