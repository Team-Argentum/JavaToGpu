/**
 * Runtime support for {@code @GPUTest} metadata, fixtures, reference comparison, GPU probes, and placement evidence.
 *
 * <p>The package is intentionally split into explicit stages so users can stop at the amount of confidence they need:</p>
 *
 * <ul>
 *     <li>{@link net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestProbes#plan} reads generated
 *     metadata without opening a GPU backend.</li>
 *     <li>Fixture readiness, value binding, and invocation materialization validate resource files and Java argument
 *     shapes before any kernel execution.</li>
 *     <li>Reference comparison runs caller-provided CPU/reference logic and compares expected outputs.</li>
 *     <li>GPU probe execution is opt-in and bounded through
 *     {@link net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestGpuProbeOptions}.</li>
 *     <li>Evidence warm-up writes cacheable results that device selection can consume later without running probes
 *     during startup.</li>
 * </ul>
 *
 * <p>Normal applications do not need to use this package to launch kernels. It is for tests, examples, CI, and
 * stronger backend/device placement evidence.</p>
 */
package net.sixik.ga_utils.javatogpu.runtime.methodtest;
