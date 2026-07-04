package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Loads and executes optional read-only IR validation providers.
 */
public final class GpuIrValidationRunner {
    private final List<GpuIrValidationProvider> providers;
    private final GpuIrValidationMode mode;

    public GpuIrValidationRunner(List<GpuIrValidationProvider> providers, GpuIrValidationMode mode) {
        this.providers = List.copyOf(providers);
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public static GpuIrValidationRunner disabled() {
        return new GpuIrValidationRunner(List.of(), GpuIrValidationMode.OFF);
    }

    public static GpuIrValidationRunner loadFromServiceLoader(GpuIrValidationMode mode) {
        if (mode == GpuIrValidationMode.OFF) {
            return disabled();
        }
        List<GpuIrValidationProvider> loadedProviders = new ArrayList<>();
        ServiceLoader.load(GpuIrValidationProvider.class, GpuIrValidationProvider.class.getClassLoader())
                .forEach(loadedProviders::add);
        return new GpuIrValidationRunner(loadedProviders, mode);
    }

    public void run(
            GpuIrCompiledMethod compiledKernel,
            List<GpuIrCompiledMethod> compiledHelpers,
            List<ParsedGpuStruct> structs
    ) {
        if (mode == GpuIrValidationMode.OFF || providers.isEmpty()) {
            return;
        }

        for (GpuIrCompiledMethod helper : compiledHelpers) {
            runForMethod(helper, compiledHelpers, structs, false);
        }
        runForMethod(compiledKernel, compiledHelpers, structs, true);
    }

    private void runForMethod(
            GpuIrCompiledMethod method,
            List<GpuIrCompiledMethod> helperMethods,
            List<ParsedGpuStruct> structs,
            boolean entryPoint
    ) {
        GpuIrValidationRequest request = new GpuIrValidationRequest(method, helperMethods, structs, entryPoint, mode);
        for (GpuIrValidationProvider provider : providers) {
            provider.validate(request);
        }
    }
}
