package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.runtime.launch.GpuLauncherNamingSupport;

/**
 * Compatibility facade for generated GPU launcher naming.
 *
 * <p>New launch/descriptor code should prefer
 * {@link net.sixik.ga_utils.javatogpu.runtime.launch.GpuLauncherNamingSupport}. This class keeps the original root
 * runtime API stable for generated code, tests, and existing users.</p>
 */
public final class GpuLauncherNaming {

    private GpuLauncherNaming() {
    }

    /**
     * Returns the generated launcher class name for an owner method.
     */
    public static String launcherClassName(Class<?> ownerClass, String methodName) {
        return GpuLauncherNamingSupport.launcherClassName(ownerClass, methodName);
    }

    /**
     * Returns the generated launcher JVM internal name for an owner method.
     */
    public static String launcherInternalName(String ownerInternalName, String methodName) {
        return GpuLauncherNamingSupport.launcherInternalName(ownerInternalName, methodName);
    }
}
