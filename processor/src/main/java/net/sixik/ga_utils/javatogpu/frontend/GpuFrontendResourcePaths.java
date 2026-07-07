package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;

/**
 * Shared resource identity helper for generated backend source and IrGpu artifacts.
 */
public final class GpuFrontendResourcePaths {

    private GpuFrontendResourcePaths() {
    }

    public static String openClResource(ParsedGpuMethod method) {
        return basePath(method) + ".cl";
    }

    public static String irGpuResource(ParsedGpuMethod method) {
        return basePath(method) + ".irgpu.properties";
    }

    public static String openClResource(String ownerQualifiedName, String ownerSimpleName, String methodName) {
        return basePath(ownerQualifiedName, ownerSimpleName, methodName) + ".cl";
    }

    public static String irGpuResource(String ownerQualifiedName, String ownerSimpleName, String methodName) {
        return basePath(ownerQualifiedName, ownerSimpleName, methodName) + ".irgpu.properties";
    }

    public static String irGpuResourceForOpenClResource(String openClResource) {
        if (openClResource == null || openClResource.isBlank()) {
            return "";
        }
        if (openClResource.endsWith(".cl")) {
            return openClResource.substring(0, openClResource.length() - ".cl".length()) + ".irgpu.properties";
        }
        return openClResource + ".irgpu.properties";
    }

    private static String basePath(ParsedGpuMethod method) {
        return basePath(method.ownerQualifiedName(), method.ownerSimpleName(), method.name());
    }

    private static String basePath(String ownerQualifiedName, String ownerSimpleName, String methodName) {
        String owner = ownerQualifiedName == null || ownerQualifiedName.isBlank()
                ? ownerSimpleName
                : ownerQualifiedName;
        String normalizedOwner = owner == null || owner.isBlank()
                ? "unknown"
                : owner.replace('.', '/').replace('$', '/');
        String normalizedMethod = methodName == null || methodName.isBlank() ? "kernel" : methodName;
        return "javatogpu/" + normalizedOwner + "/" + normalizedMethod;
    }
}
