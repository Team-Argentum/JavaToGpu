package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Resolves a runtime stack frame against compile-time generated GPU call-site metadata.
 */
public final class GpuRuntimeCallSiteResolver {

    public static final String RESOURCE_PREFIX = "META-INF/javatogpu/call-sites/";

    private GpuRuntimeCallSiteResolver() {
    }

    public static GpuRuntimeCallSite resolve(
            ClassLoader preferredClassLoader,
            IrGpuSourceLocation gpuMethodLocation
    ) {
        ClassLoader classLoader = preferredClassLoader == null
                ? Thread.currentThread().getContextClassLoader()
                : preferredClassLoader;
        StackTraceElement fallback = null;
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (skipFrame(frame, gpuMethodLocation)) {
                continue;
            }
            Optional<GpuRuntimeCallSite> indexed = load(classLoader, frame.getClassName()).stream()
                    .filter(callSite -> callSite.callerMethodName().equals(frame.getMethodName()))
                    .filter(callSite -> targetMatches(callSite, gpuMethodLocation))
                    .min(Comparator
                            .comparing((GpuRuntimeCallSite callSite) -> callSite.line() != frame.getLineNumber())
                            .thenComparingInt(callSite -> Math.abs(callSite.line() - frame.getLineNumber()))
                            .thenComparingInt(GpuRuntimeCallSite::column));
            if (indexed.isPresent()) {
                return indexed.orElseThrow();
            }
            if (fallback == null && frame.getLineNumber() > 0) {
                fallback = frame;
            }
        }
        if (fallback == null) {
            return GpuRuntimeCallSite.unknown();
        }
        return new GpuRuntimeCallSite(
                fallback.getClassName(),
                fallback.getMethodName(),
                fallback.getFileName() == null ? fallback.getClassName() : fallback.getFileName(),
                fallback.getLineNumber(),
                1,
                fallback.getLineNumber(),
                1,
                fallback.getClassName() + "." + fallback.getMethodName() + "(...)",
                "stack-trace"
        );
    }

    public static List<GpuRuntimeCallSite> load(ClassLoader classLoader, String callerClassName) {
        if (classLoader == null || callerClassName == null || callerClassName.isBlank()) {
            return List.of();
        }
        String resource = resourcePath(callerClassName);
        try (InputStream inputStream = classLoader.getResourceAsStream(resource)) {
            if (inputStream == null) {
                return List.of();
            }
            Properties properties = new Properties();
            properties.load(inputStream);
            int count = Integer.parseInt(properties.getProperty("callSite.count", "0"));
            ArrayList<GpuRuntimeCallSite> callSites = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                String prefix = "callSite." + index + ".";
                callSites.add(new GpuRuntimeCallSite(
                        properties.getProperty(prefix + "callerClassName", callerClassName),
                        properties.getProperty(prefix + "callerMethodName", "unknown"),
                        properties.getProperty(prefix + "sourceName", callerClassName),
                        parseInt(properties, prefix + "line"),
                        parseInt(properties, prefix + "column"),
                        parseInt(properties, prefix + "endLine"),
                        parseInt(properties, prefix + "endColumn"),
                        properties.getProperty(prefix + "expression", "unknown"),
                        properties.getProperty(prefix + "targetOwnerName", "unknown"),
                        properties.getProperty(prefix + "targetMethodName", "unknown"),
                        "compiler-index"
                ));
            }
            return List.copyOf(callSites);
        } catch (IOException | NumberFormatException exception) {
            throw new IllegalStateException("Failed to load GPU call-site metadata: " + resource, exception);
        }
    }

    public static String resourcePath(String callerClassName) {
        return RESOURCE_PREFIX + callerClassName.replace('.', '/') + ".properties";
    }

    private static boolean skipFrame(StackTraceElement frame, IrGpuSourceLocation gpuMethodLocation) {
        String className = frame.getClassName();
        if (className.equals(Thread.class.getName())
                || className.equals(GpuRuntimeCallSiteResolver.class.getName())
                || className.equals("net.sixik.ga_utils.javatogpu.runtime.GpuRuntime")
                || className.equals("net.sixik.ga_utils.javatogpu.runtime.GpuGeneratedLauncherInvoker")
                || className.equals("net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend")
                || className.contains(".generated.")) {
            return true;
        }
        if (gpuMethodLocation == null) {
            return false;
        }
        return className.equals(gpuMethodLocation.ownerQualifiedName())
                && frame.getMethodName().equals(gpuMethodLocation.methodName());
    }

    private static boolean targetMatches(
            GpuRuntimeCallSite callSite,
            IrGpuSourceLocation gpuMethodLocation
    ) {
        if (gpuMethodLocation == null) {
            return true;
        }
        boolean ownerMatches = "unknown".equals(callSite.targetOwnerName())
                || gpuMethodLocation.ownerQualifiedName().isBlank()
                || callSite.targetOwnerName().equals(gpuMethodLocation.ownerQualifiedName());
        boolean methodMatches = "unknown".equals(callSite.targetMethodName())
                || gpuMethodLocation.methodName().isBlank()
                || callSite.targetMethodName().equals(gpuMethodLocation.methodName());
        return ownerMatches && methodMatches;
    }

    private static int parseInt(Properties properties, String key) {
        return Integer.parseInt(properties.getProperty(key, "-1"));
    }
}
