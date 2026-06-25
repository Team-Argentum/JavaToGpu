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
 *     <li>{@link net.sixik.ga_utils.javatogpu.api.GPU} - OpenCL-like built-in functions available from Java.</li>
 *     <li>Pointer wrappers such as {@link net.sixik.ga_utils.javatogpu.api.FloatPtr} - scalar-by-reference helpers for {@code @CCode} methods.</li>
 *     <li>Vector wrappers such as {@link net.sixik.ga_utils.javatogpu.api.Float2} - Java-side representation of OpenCL vector types.</li>
 *     <li>Annotations from {@code net.sixik.ga_utils.javatogpu.api.annotations} - mark kernels, helpers, structs and address spaces.</li>
 * </ul>
 *
 * <p>The classes in this package are intentionally small: most of them exist to make user code compile as regular Java while
 * the processor converts it to OpenCL at build time.
 */
package net.sixik.ga_utils.javatogpu.api;
