package net.sixik.ga_utils.javatogpu.runtime.launch;

import net.sixik.ga_utils.javatogpu.runtime.GpuLauncherNaming;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GpuLauncherNamingSupportTest {

    @Test
    void rootLauncherNamingFacadeDelegatesToLaunchSupport() {
        assertEquals(
                GpuLauncherNamingSupport.launcherClassName(NestedOwner.class, "kernel"),
                GpuLauncherNaming.launcherClassName(NestedOwner.class, "kernel")
        );
        assertEquals(
                "net.sixik.ga_utils.javatogpu.runtime.launch.generated.GpuLauncherNamingSupportTest_NestedOwner_kernel_GpuLauncher",
                GpuLauncherNamingSupport.launcherClassName(NestedOwner.class, "kernel")
        );
    }

    @Test
    void rootLauncherInternalNamingFacadeDelegatesToLaunchSupport() {
        assertEquals(
                GpuLauncherNamingSupport.launcherInternalName("sample/Outer$Inner", "kernel"),
                GpuLauncherNaming.launcherInternalName("sample/Outer$Inner", "kernel")
        );
        assertEquals(
                "sample/generated/Outer_Inner_kernel_GpuLauncher",
                GpuLauncherNamingSupport.launcherInternalName("sample/Outer$Inner", "kernel")
        );
    }

    private static final class NestedOwner {
    }
}
