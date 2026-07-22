package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuScope;
import net.sixik.ga_utils.javatogpu.api.JavaToGpu;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendDeviceSelection;

/**
 * Small example for the user-facing JavaToGpu runtime facade.
 */
public final class RuntimeFacadeExample {

    private RuntimeFacadeExample() {
    }

    public static void main(String[] args) {
        System.out.println(renderRuntimeFacadeExample());
    }

    static String renderRuntimeFacadeExample() {
        GpuExecutionConfig launch = JavaToGpu.launch1D(1024L, 64L);
        GpuRuntimeBackendDeviceSelection selection = JavaToGpu.explainStandardBackendAndDevice();

        return "Runtime facade example" + System.lineSeparator()
                + "- scope type: " + GpuScope.class.getSimpleName() + System.lineSeparator()
                + "- use one-off OpenCL scope: JavaToGpu.useOpenCl()" + System.lineSeparator()
                + "- use shared OpenCL cache: JavaToGpu.useOpenClSharedCache()" + System.lineSeparator()
                + "- release shared cache: JavaToGpu.shutdownOpenClSharedCache()" + System.lineSeparator()
                + "- launch helper: " + launch.summary() + System.lineSeparator()
                + "- backend/device explanation:" + System.lineSeparator()
                + selection.toMarkdown() + System.lineSeparator();
    }
}
