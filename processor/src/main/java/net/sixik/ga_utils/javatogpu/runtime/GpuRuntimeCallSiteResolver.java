package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;
import net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCallSiteResolverSupport;

import java.util.List;

/**
 * Resolves a runtime stack frame against compile-time generated GPU call-site metadata.
 */
public final class GpuRuntimeCallSiteResolver {

    public static final String RESOURCE_PREFIX = GpuRuntimeCallSiteResolverSupport.RESOURCE_PREFIX;

    private GpuRuntimeCallSiteResolver() {
    }

    public static GpuRuntimeCallSite resolve(
            ClassLoader preferredClassLoader,
            IrGpuSourceLocation gpuMethodLocation
    ) {
        return GpuRuntimeCallSiteResolverSupport.resolve(preferredClassLoader, gpuMethodLocation);
    }

    public static List<GpuRuntimeCallSite> load(ClassLoader classLoader, String callerClassName) {
        return GpuRuntimeCallSiteResolverSupport.load(classLoader, callerClassName);
    }

    public static String resourcePath(String callerClassName) {
        return GpuRuntimeCallSiteResolverSupport.resourcePath(callerClassName);
    }
}
