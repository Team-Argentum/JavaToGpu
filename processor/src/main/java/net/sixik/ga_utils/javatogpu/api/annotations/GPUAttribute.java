package net.sixik.ga_utils.javatogpu.api.annotations;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.api.GpuVendorTarget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches a backend-specific raw attribute or qualifier to GPU source metadata.
 *
 * <p>This annotation is an escape hatch for backend features that do not yet have a portable JavaToGpu annotation.
 * Prefer backend-neutral annotations whenever they exist, because raw attributes are only meaningful for the selected
 * backend and may be rejected by other backend lowerers.
 *
 * <pre>{@code
 * @GPUAttribute(backend = GpuBackendTarget.OPENCL, value = "reqd_work_group_size(8, 8, 1)")
 * @GPUAttribute(
 *         backend = GpuBackendTarget.OPENCL,
 *         vendor = GpuVendorTarget.NVIDIA,
 *         deviceClass = GpuDeviceClassTarget.DGPU,
 *         value = "some_vendor_hint"
 * )
 * @GPU
 * static void kernel(@GPUGlobal float[] output) {
 *     output[GPU.get_global_id(0)] = 1.0f;
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface GPUAttribute {

    /**
     * Backend that understands the raw attribute text.
     */
    GpuBackendTarget backend();

    /**
     * Optional vendor selector for backend-specific metadata.
     */
    GpuVendorTarget vendor() default GpuVendorTarget.ANY;

    /**
     * Optional device-class selector for backend-specific metadata.
     */
    GpuDeviceClassTarget deviceClass() default GpuDeviceClassTarget.ANY;

    /**
     * Raw backend attribute or qualifier text.
     */
    String value();
}
