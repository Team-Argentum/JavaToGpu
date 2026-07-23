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
 * {@code runtime.spi} unless it is a deliberate compatibility entry point. Maintainer-facing checks and harnesses
 * belong in {@code runtime.validation}; method-variant support belongs in {@code runtime.variants}. Root compatibility
 * facades that already have domain-package homes are tracked by
 * {@link net.sixik.ga_utils.javatogpu.runtime.validation.GpuRuntimeCompatibilityFacadeCatalog}.</p>
 *
 * <p>Audience split:</p>
 *
 * <ul>
 *     <li>Normal users: prefer {@code api.JavaToGpu}, {@code api.GpuScope}, and annotations.</li>
 *     <li>Advanced runtime users: use root compatibility types only when configuring compile options, explicit launch descriptors, or backend/device selection.</li>
 *     <li>Extension authors: use ServiceLoader contracts from root compatibility interfaces and domain packages such as {@code runtime.spi}, {@code runtime.hooks}, {@code runtime.observability}, {@code runtime.optimization}, and {@code runtime.memory}.</li>
 *     <li>Maintainers: use {@code runtime.validation} for package taxonomy checks, hardware-free harnesses, and CI contract validators.</li>
 *     <li>Backend implementors: keep OpenCL/CUDA details in {@code runtime.opencl} and {@code runtime.cuda}, not in the root package.</li>
 * </ul>
 */
package net.sixik.ga_utils.javatogpu.runtime;
