/**
 * Image and sampler wrapper types for OpenCL-style image kernels.
 *
 * <p>Use this package only when kernel code needs image memory semantics such as sampler reads, image metadata, or
 * image writes. For normal numeric buffers, prefer annotated primitive arrays such as {@code @GPUGlobal float[]}.
 */
package net.sixik.ga_utils.javatogpu.api.images;
