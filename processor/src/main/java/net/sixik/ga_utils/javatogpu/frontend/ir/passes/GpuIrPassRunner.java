package net.sixik.ga_utils.javatogpu.frontend.ir.passes;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public final class GpuIrPassRunner {
    private final List<GpuIrPass> passes;

    public GpuIrPassRunner(List<GpuIrPass> passes) {
        this.passes = List.copyOf(passes);
    }

    public static GpuIrPassRunner loadFromServiceLoader() {
        List<GpuIrPass> loadedPasses = new ArrayList<>();
        ServiceLoader.load(GpuIrPass.class, GpuIrPass.class.getClassLoader()).forEach(loadedPasses::add);
        return new GpuIrPassRunner(loadedPasses);
    }

    public void run(
            GpuIrCompiledMethod compiledKernel,
            List<GpuIrCompiledMethod> compiledHelpers,
            List<ParsedGpuStruct> structs
    ) {
        if (passes.isEmpty()) {
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
        GpuIrPassContext context = new GpuIrPassContext(method, helperMethods, structs, entryPoint);
        for (GpuIrPass pass : passes) {
            pass.run(context);
        }
    }
}
