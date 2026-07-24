/**
 * Public JavaToGpu API used by application code.
 *
 * <p>The typical workflow looks like this:
 *
 * <pre>{@code
 * public final class Demo {
 *
 *     @net.sixik.ga_utils.javatogpu.api.annotations.GPU
 *     public static void saxpy(
 *             @net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal float[] input,
 *             @net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal float[] output
 *     ) {
 *         int id = GPU.get_global_id(0);
 *         output[id] = GPU.sin(input[id]) + 2.0f;
 *     }
 * }
 * }</pre>
 *
 * <p>Main building blocks:
 *
 * <ul>
 *     <li>{@link net.sixik.ga_utils.javatogpu.api.JavaToGpu} - the user-facing runtime facade for common OpenCL scopes, shared-cache shutdown, prepared hot-loop launchers, launch shapes, and setup diagnostics.</li>
 *     <li>{@link net.sixik.ga_utils.javatogpu.api.GpuScope} - try-with-resources handle returned by the runtime facade.</li>
 *     <li>{@link net.sixik.ga_utils.javatogpu.api.GpuPreparedLauncher} - reusable handle for tight loops after one cold validation/compile step.</li>
 *     <li>{@link net.sixik.ga_utils.javatogpu.api.GPU} - the single OpenCL-style built-in facade available from Java.</li>
 *     <li>{@link net.sixik.ga_utils.javatogpu.api.GpuBackendTarget}, {@link net.sixik.ga_utils.javatogpu.api.GpuVendorTarget}, and {@link net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget} - stable selectors used by public annotations and backend/device metadata.</li>
 *     <li>Pointer wrappers such as {@link net.sixik.ga_utils.javatogpu.api.pointers.FloatPtr} - scalar-by-reference helpers for {@code @CCode} methods.</li>
 *     <li>Address-space pointer wrappers from {@code net.sixik.ga_utils.javatogpu.api.pointers.global}, {@code .constant}, and {@code .local} - low-level packed-buffer views.</li>
 *     <li>Vector wrappers such as {@link net.sixik.ga_utils.javatogpu.api.types.floats.Float2} - Java-side representation of OpenCL vector types.</li>
 *     <li>Annotations from {@code net.sixik.ga_utils.javatogpu.api.annotations} - mark kernels, helpers, structs and address spaces.</li>
 * </ul>
 *
 * <p>The root package is intentionally a thin user-facing layer. Concrete GPU data wrappers live in grouped
 * subpackages ({@code api.types.*}, {@code api.pointers.*}, and {@code api.images}) so normal imports stay readable and
 * the root package does not become a flat dump of every OpenCL shape.
 *
 * <p>{@link net.sixik.ga_utils.javatogpu.api.GpuAnnotationSupport} is kept in the root package as processor/runtime
 * support and compatibility glue. It is not required for ordinary kernel source code.
 *
 * <p>The source dialect is intentionally OpenCL-style even as additional backends are added. CUDA, Vulkan/SPIR-V, and
 * Metal support should be implemented by backend lowerers and runtime planners behind the same Java API rather than by
 * adding parallel source-level facade styles.
 *
 * <p>The classes in this package are intentionally small: most of them exist to make user code compile as regular Java
 * while the processor converts it to backend code at build time.
 */
package net.sixik.ga_utils.javatogpu.api;
