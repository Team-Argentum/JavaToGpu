/**
 * Lower-level JavaToGpu runtime compatibility package.
 *
 * <p>Normal application code should prefer {@link net.sixik.ga_utils.javatogpu.api.JavaToGpu} and
 * {@link net.sixik.ga_utils.javatogpu.api.GpuScope}. This package remains supported for generated launchers, advanced
 * runtime configuration, backend/device policy work, diagnostics, and extension modules.</p>
 *
 * <p>The root package is intentionally being stabilized, not expanded. New runtime code should first check
 * {@link net.sixik.ga_utils.javatogpu.runtime.validation.GpuRuntimePackageTaxonomy} and use a domain package such as
 * {@code runtime.selection}, {@code runtime.launch}, {@code runtime.diagnostics}, {@code runtime.observability},
 * {@code runtime.methodtest}, {@code runtime.optimization}, {@code runtime.memory}, {@code runtime.hooks}, or
 * {@code runtime.spi} unless it is a deliberate compatibility entry point.</p>
 */
package net.sixik.ga_utils.javatogpu.runtime;
