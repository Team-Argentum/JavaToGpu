/**
 * JavaToGpu source annotations.
 *
 * <p>Use these annotations to mark GPU entry methods, helper methods, address spaces, structs, and backend metadata in
 * ordinary Java source. The public Java facade intentionally uses OpenCL-style naming; portable annotations such as
 * {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUWorkGroupSize},
 * {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUWorkGroupSizeHint},
 * {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUPacked},
 * {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUAligned},
 * {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUAlwaysInline},
 * {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorTypeHint} and
 * {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUOptimize} should be preferred over backend-specific escape
 * hatches whenever possible.
 */
package net.sixik.ga_utils.javatogpu.api.annotations;
