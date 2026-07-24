package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.GpuPreparedLauncher;
import net.sixik.ga_utils.javatogpu.api.GpuScope;
import net.sixik.ga_utils.javatogpu.api.JavaToGpu;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

import java.util.Arrays;

/**
 * Shows the low-overhead prepared launcher path for hot loops.
 */
public final class PreparedLauncherExample {

    private PreparedLauncherExample() {
    }

    public static void main(String[] args) {
        System.out.println(renderPreparedLauncherExample());
    }

    static String renderPreparedLauncherExample() {
        float[] input = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        float[] output = new float[input.length];

        try (GpuScope ignored = JavaToGpu.useOpenClSharedCache()) {
            GpuPreparedLauncher launcher = JavaToGpu.prepare(
                    PreparedLauncherExample.class,
                    "scaleKernel",
                    input,
                    output
            );

            launcher.invoke(input, output);
            launcher.invoke(input, output);

            return "Prepared launcher output = " + Arrays.toString(output);
        } catch (RuntimeException exception) {
            return "Prepared launcher example requires a working OpenCL runtime: " + exception.getMessage();
        } finally {
            JavaToGpu.shutdownOpenClSharedCache();
        }
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void scaleKernel(
            @GPUGlobal(constant = true) float[] input,
            @GPUGlobal float[] output
    ) {
        int id = GPU.get_global_id(0);
        output[id] = input[id] * 3.0f;
    }
}
