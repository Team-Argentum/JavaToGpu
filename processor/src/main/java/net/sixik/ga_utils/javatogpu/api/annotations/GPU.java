package net.sixik.ga_utils.javatogpu.api.annotations;

/**
 * Marks a Java method as a GPU kernel entry point.
 *
 * <p>The processor parses the method body, validates it against the supported Java subset and emits an OpenCL kernel.
 * A generated launcher is then used when the annotated method is invoked at runtime.
 *
 * <p>Typical usage:
 *
 * <pre>{@code
 * @GPU
 * static void blur(@GPUGlobal float[] input, @GPUGlobal float[] output) {
 *     int id = GPU.get_global_id(0);
 *     output[id] = GPU.sin(input[id]);
 * }
 * }</pre>
 *
 * <p>Current pipeline expectation:
 *
 * <ul>
 *     <li>kernel methods should be {@code void};</li>
 *     <li>input/output arrays are usually marked with {@link GPUGlobal};</li>
 *     <li>helper logic can be extracted into {@link CCode}-annotated methods.</li>
 * </ul>
 */
public @interface GPU {
}
