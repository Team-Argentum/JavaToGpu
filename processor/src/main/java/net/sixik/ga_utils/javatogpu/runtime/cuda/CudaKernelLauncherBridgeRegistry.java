package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Deterministic registry for optional CUDA kernel launchers.
 */
public final class CudaKernelLauncherBridgeRegistry {

    private final List<CudaKernelLauncherBridge> launchers;

    private CudaKernelLauncherBridgeRegistry(List<CudaKernelLauncherBridge> launchers) {
        ArrayList<CudaKernelLauncherBridge> sorted = new ArrayList<>(launchers == null ? List.of() : launchers);
        sorted.sort(Comparator
                .comparingInt(CudaKernelLauncherBridge::launcherOrder)
                .thenComparing(CudaKernelLauncherBridge::launcherId)
                .thenComparing(CudaKernelLauncherBridge::launcherVersion));
        this.launchers = List.copyOf(sorted);
    }

    public static CudaKernelLauncherBridgeRegistry of(List<CudaKernelLauncherBridge> launchers) {
        return new CudaKernelLauncherBridgeRegistry(launchers);
    }

    public static CudaKernelLauncherBridgeRegistry loadWithBuiltIns() {
        ArrayList<CudaKernelLauncherBridge> loaded = new ArrayList<>();
        ServiceLoader.load(
                CudaKernelLauncherBridge.class,
                CudaKernelLauncherBridge.class.getClassLoader()
        ).forEach(loaded::add);
        loaded.add(new CudaDriverKernelLauncherBridge());
        return of(loaded);
    }

    public CudaKernelLaunchResult launch(CudaKernelLaunchRequest request) {
        Objects.requireNonNull(request, "request");
        if (!request.kernelLauncherRequested()) {
            return CudaKernelLaunchResult.disabled(request.launcherMode());
        }
        if (!request.argumentBindingSucceeded()) {
            return CudaKernelLaunchResult.unsupported(
                    request.launcherMode(),
                    List.of("cuda-native-argument-binding-required"),
                    List.of("CUDA kernel launch requires successful native argument binding")
            );
        }
        if (request.executionConfig() == null) {
            return CudaKernelLaunchResult.unsupported(
                    request.launcherMode(),
                    List.of("cuda-native-kernel-launch-config-missing"),
                    List.of("CUDA kernel launch requires an explicit execution config")
            );
        }
        for (CudaKernelLauncherBridge launcher : launchers) {
            if (!launcher.supports(request)) {
                continue;
            }
            try {
                return Objects.requireNonNull(launcher.launch(request), "CUDA kernel launch result");
            } catch (RuntimeException exception) {
                return CudaKernelLaunchResult.failed(
                        launcher.launcherId(),
                        List.of("cuda-native-kernel-launcher-failed:" + exception.getClass().getSimpleName()),
                        List.of(exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage())
                );
            }
        }
        return CudaKernelLaunchResult.unsupported(
                request.launcherMode(),
                List.of("cuda-native-kernel-launcher-unavailable:" + request.launcherMode()),
                List.of("No CUDA kernel launcher bridge supports mode " + request.launcherMode())
        );
    }

    public List<CudaKernelLauncherBridge> launchers() {
        return launchers;
    }
}
