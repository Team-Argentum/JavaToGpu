package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Deterministic registry for optional CUDA module/function loaders.
 */
public final class CudaModuleLoaderBridgeRegistry {

    private final List<CudaModuleLoaderBridge> loaders;

    private CudaModuleLoaderBridgeRegistry(List<CudaModuleLoaderBridge> loaders) {
        ArrayList<CudaModuleLoaderBridge> sorted = new ArrayList<>(loaders == null ? List.of() : loaders);
        sorted.sort(Comparator
                .comparingInt(CudaModuleLoaderBridge::loaderOrder)
                .thenComparing(CudaModuleLoaderBridge::loaderId)
                .thenComparing(CudaModuleLoaderBridge::loaderVersion));
        this.loaders = List.copyOf(sorted);
    }

    public static CudaModuleLoaderBridgeRegistry of(List<CudaModuleLoaderBridge> loaders) {
        return new CudaModuleLoaderBridgeRegistry(loaders);
    }

    public static CudaModuleLoaderBridgeRegistry loadWithBuiltIns() {
        ArrayList<CudaModuleLoaderBridge> loaded = new ArrayList<>();
        ServiceLoader.load(
                CudaModuleLoaderBridge.class,
                CudaModuleLoaderBridge.class.getClassLoader()
        ).forEach(loaded::add);
        loaded.add(new CudaDriverModuleLoaderBridge());
        return of(loaded);
    }

    public CudaModuleLoadResult load(CudaModuleLoadRequest request) {
        Objects.requireNonNull(request, "request");
        if (!request.moduleLoaderRequested()) {
            return CudaModuleLoadResult.disabled(request.loaderMode());
        }
        for (CudaModuleLoaderBridge loader : loaders) {
            if (!loader.supports(request)) {
                continue;
            }
            try {
                return Objects.requireNonNull(loader.load(request), "CUDA module loader result");
            } catch (RuntimeException exception) {
                return CudaModuleLoadResult.failed(
                        loader.loaderId(),
                        List.of("cuda-native-module-loader-failed:" + exception.getClass().getSimpleName()),
                        List.of(exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage())
                );
            }
        }
        return CudaModuleLoadResult.unsupported(
                request.loaderMode(),
                List.of("cuda-native-module-loader-unavailable:" + request.loaderMode()),
                List.of("No CUDA module loader bridge supports mode " + request.loaderMode())
        );
    }

    public List<CudaModuleLoaderBridge> loaders() {
        return loaders;
    }
}
