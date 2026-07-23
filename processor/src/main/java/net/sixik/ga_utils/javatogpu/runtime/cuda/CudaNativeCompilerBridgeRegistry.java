package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Deterministic registry for optional CUDA native compiler bridges.
 */
public final class CudaNativeCompilerBridgeRegistry {

    private final List<CudaNativeCompilerBridge> bridges;

    private CudaNativeCompilerBridgeRegistry(List<CudaNativeCompilerBridge> bridges) {
        ArrayList<CudaNativeCompilerBridge> sorted = new ArrayList<>(bridges == null ? List.of() : bridges);
        sorted.sort(Comparator
                .comparingInt(CudaNativeCompilerBridge::bridgeOrder)
                .thenComparing(CudaNativeCompilerBridge::bridgeId)
                .thenComparing(CudaNativeCompilerBridge::bridgeVersion));
        this.bridges = List.copyOf(sorted);
    }

    public static CudaNativeCompilerBridgeRegistry of(List<CudaNativeCompilerBridge> bridges) {
        return new CudaNativeCompilerBridgeRegistry(bridges);
    }

    public static CudaNativeCompilerBridgeRegistry loadWithBuiltIns() {
        ArrayList<CudaNativeCompilerBridge> loaded = new ArrayList<>();
        ServiceLoader.load(
                CudaNativeCompilerBridge.class,
                CudaNativeCompilerBridge.class.getClassLoader()
        ).forEach(loaded::add);
        loaded.add(new CudaNvccProcessCompilerBridge());
        return of(loaded);
    }

    public CudaNativeCompilationResult compile(CudaNativeCompilationRequest request) {
        Objects.requireNonNull(request, "request");
        if (!request.nativeCompilerRequested()) {
            return CudaNativeCompilationResult.disabled(request.bridgeMode());
        }
        for (CudaNativeCompilerBridge bridge : bridges) {
            if (!bridge.supports(request)) {
                continue;
            }
            try {
                return Objects.requireNonNull(bridge.compile(request), "CUDA native compiler bridge result");
            } catch (RuntimeException exception) {
                return CudaNativeCompilationResult.failed(
                        bridge.bridgeId(),
                        "",
                        List.of("cuda-native-compiler-bridge-failed:" + exception.getClass().getSimpleName()),
                        List.of(exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage())
                );
            }
        }
        return CudaNativeCompilationResult.unsupported(
                request.bridgeMode(),
                List.of("cuda-native-compiler-bridge-unavailable:" + request.bridgeMode()),
                List.of("No CUDA native compiler bridge supports mode " + request.bridgeMode()
                        + "; use " + GpuBackendCompileOptions.CUDA_COMPILER_BRIDGE_PREVIEW
                        + " or register a CudaNativeCompilerBridge")
        );
    }

    public List<CudaNativeCompilerBridge> bridges() {
        return bridges;
    }
}
