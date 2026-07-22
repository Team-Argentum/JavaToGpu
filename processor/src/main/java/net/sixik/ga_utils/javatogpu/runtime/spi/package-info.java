/**
 * Backend and extension service-provider contracts.
 *
 * <p>Classes in this package should describe stable integration points for backend adapters and ServiceLoader-based
 * extensions. Backend-specific implementation details belong in backend packages such as {@code runtime.opencl} or
 * {@code runtime.cuda}. Planned/unsupported placeholder implementations also live here because they are generic SPI
 * fallbacks rather than a concrete backend implementation.</p>
 */
package net.sixik.ga_utils.javatogpu.runtime.spi;
