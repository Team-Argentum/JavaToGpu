package net.sixik.ga_utils.javatogpu.runtime.launch;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain implementation support for generated GPU launcher naming.
 *
 * <p>The root {@code GpuLauncherNaming} class remains as a compatibility facade. New launch/descriptor code should use
 * this class so naming rules live with the rest of the generated-launcher support instead of the flat runtime package.</p>
 */
public final class GpuLauncherNamingSupport {

    private GpuLauncherNamingSupport() {
    }

    /**
     * Returns the generated launcher class name for an owner method.
     */
    public static String launcherClassName(Class<?> ownerClass, String methodName) {
        String packageName = ownerClass.getPackageName();
        String generatedPackage = packageName.isEmpty() ? "generated" : packageName + ".generated";

        List<String> ownerNames = new ArrayList<>();
        Class<?> current = ownerClass;
        while (current != null) {
            ownerNames.add(0, current.getSimpleName());
            current = current.getEnclosingClass();
        }
        ownerNames.add(methodName);
        ownerNames.add("GpuLauncher");
        return generatedPackage + "." + String.join("_", ownerNames);
    }

    /**
     * Returns the generated launcher JVM internal name for an owner method.
     */
    public static String launcherInternalName(String ownerInternalName, String methodName) {
        int packageSeparator = ownerInternalName.lastIndexOf('/');
        String packageInternalName = packageSeparator >= 0 ? ownerInternalName.substring(0, packageSeparator) : "";
        String ownerSimplePath = packageSeparator >= 0
                ? ownerInternalName.substring(packageSeparator + 1)
                : ownerInternalName;
        String generatedPackage = packageInternalName.isEmpty() ? "generated" : packageInternalName + "/generated";
        String flattenedOwnerName = ownerSimplePath.replace('$', '_');
        return generatedPackage + "/" + flattenedOwnerName + "_" + methodName + "_GpuLauncher";
    }
}
