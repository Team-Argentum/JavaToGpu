package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodTestVectorMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeTest {

    @Test
    void compileRequestFactoryBuildsSharedFrontendRequestWithRuntimeOptionsAndIrGpu() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
                List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
        GpuRuntimeCompileOptions compileOptions = new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                List.of("-cl-fast-relaxed-math"),
                "vendor-tuned"
        );
        GpuRuntimeDeviceProfile deviceProfile = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "Mock GPU",
                "Mock Vendor",
                "Mock Driver",
                "OpenCL 3.0 Mock",
                48L,
                65_536L,
                512L,
                1L,
                true,
                true,
                false
        );
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "jtg_kernel",
                                "body\n  return output[0]\n",
                                List.of()
                        ))
                ),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
        GpuKernelInvocation invocation = new GpuKernelInvocation(
                descriptor,
                new Object[]{new int[]{0}},
                compileOptions
        );

        GpuRuntimeCompileRequest request = GpuRuntimeCompileRequestFactory.fromInvocation(
                invocation,
                deviceProfile,
                Optional.of(artifact)
        );

        assertSame(descriptor, request.descriptor());
        assertSame(compileOptions, request.options());
        assertSame(deviceProfile, request.deviceProfile());
        assertSame(artifact, request.irGpuArtifact().orElseThrow());
        assertEquals(GpuBackendTarget.OPENCL, request.options().backendTarget());
        assertEquals(List.of("-cl-fast-relaxed-math"), request.options().compileArgs());
        assertEquals(GpuBackendTarget.OPENCL, request.options().backendOptions().backendTarget());
        assertEquals(List.of("-cl-fast-relaxed-math"), request.options().backendOptions().flags());
        assertTrue(request.options().backendOptions().properties().isEmpty());
        assertEquals("vendor-tuned", request.options().optimizationProfile());
    }

    @Test
    void compileOptionsExposeOptInOpenClIrGpuSourceSelection() {
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.openClIrGpuSource(
                List.of("-cl-fast-relaxed-math"),
                GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE
        );

        assertEquals(GpuBackendTarget.OPENCL, options.backendTarget());
        assertEquals(List.of("-cl-fast-relaxed-math"), options.compileArgs());
        assertEquals(GpuBackendTarget.OPENCL, options.backendOptions().backendTarget());
        assertEquals(List.of("-cl-fast-relaxed-math"), options.backendOptions().flags());
        assertEquals(
                GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_IRGPU,
                options.backendOptions().properties().get(GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_PROPERTY)
        );
        assertTrue(options.backendOptions().requestsOpenClIrGpuSource());
        assertEquals(GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE, options.optimizationProfile());
    }

    @Test
    void compileOptionsExposeNamedOpenClIrGpuSourceReviewPreset() {
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.openClIrGpuSourceReview(
                List.of("-cl-fast-relaxed-math")
        );

        assertEquals(GpuBackendTarget.OPENCL, options.backendTarget());
        assertEquals(List.of("-cl-fast-relaxed-math"), options.compileArgs());
        assertEquals(GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE, options.optimizationProfile());
        assertEquals(
                GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_IRGPU,
                options.backendOptions().properties().get(GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_PROPERTY)
        );
        assertTrue(options.backendOptions().requestsOpenClIrGpuSource());
        assertTrue(!options.backendOptions().enablesOpenClProductionSourceSwitching());
    }

    @Test
    void compileOptionsExposeExplicitOpenClProductionSourceSwitching() {
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.openClProductionIrGpuSource(
                List.of("-cl-fast-relaxed-math"),
                "vendor-tuned"
        );

        assertEquals(GpuBackendTarget.OPENCL, options.backendTarget());
        assertEquals(List.of("-cl-fast-relaxed-math"), options.compileArgs());
        assertEquals(GpuBackendTarget.OPENCL, options.backendOptions().backendTarget());
        assertEquals(List.of("-cl-fast-relaxed-math"), options.backendOptions().flags());
        assertEquals(
                GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_IRGPU,
                options.backendOptions().properties().get(GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_PROPERTY)
        );
        assertEquals(
                GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_ENABLED,
                options.backendOptions().properties().get(
                        GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_PROPERTY
                )
        );
        assertTrue(options.backendOptions().requestsOpenClIrGpuSource());
        assertTrue(options.backendOptions().enablesOpenClProductionSourceSwitching());
        assertEquals("vendor-tuned", options.optimizationProfile());
    }

    @Test
    void compileOptionsCarryProductionPromotionDecisionWithoutEnablingSourceSwitching() {
        GpuProductionPromotionDecision decision = new GpuProductionPromotionDecision(
                GpuProductionPromotionDecision.REVIEW_READY,
                "blocked",
                true,
                false,
                false,
                "production-source-switching-disabled",
                "none",
                "review-ready evidence is visible to runtime diagnostics"
        );

        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions
                .openClIrGpuSource(List.of("-cl-fast-relaxed-math"), "vendor-tuned")
                .withProductionPromotionDecision(decision);

        assertEquals(
                GpuProductionPromotionDecision.REVIEW_READY,
                options.backendOptions().productionPromotionDecisionMode()
        );
        assertEquals(
                GpuProductionPromotionDecision.REVIEW_READY,
                options.backendOptions().properties().get(
                        GpuBackendCompileOptions.PRODUCTION_PROMOTION_DECISION_MODE_PROPERTY
                )
        );
        assertTrue(options.backendOptions().requestsOpenClIrGpuSource());
        assertTrue(!options.backendOptions().enablesOpenClProductionSourceSwitching());
    }

    @Test
    void productionProfileClassifierIsSharedAcrossRuntimeGates() {
        assertTrue(GpuRuntimeProductionProfiles.isProductionProfile("production"));
        assertTrue(GpuRuntimeProductionProfiles.isProductionProfile("prod"));
        assertTrue(GpuRuntimeProductionProfiles.isProductionProfile("vendor-tuned"));
        assertTrue(GpuRuntimeProductionProfiles.isProductionProfile("runtime-tuned"));
        assertTrue(GpuRuntimeProductionProfiles.isProductionProfile("VENDOR-TUNED"));
        assertTrue(!GpuRuntimeProductionProfiles.isProductionProfile(GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE));
        assertTrue(!GpuRuntimeProductionProfiles.isProductionProfile("fast"));
        assertTrue(!GpuRuntimeProductionProfiles.isProductionProfile(null));
    }

    @Test
    void compileOptionsExposeBackendSpecificBucketsForFutureBackends() {
        GpuRuntimeCompileOptions cudaOptions = GpuRuntimeCompileOptions.cuda(
                List.of("--use_fast_math", "--gpu-architecture=compute_89"),
                Map.of("linkMode", "ptx"),
                "nvidia-fast"
        );
        GpuRuntimeCompileOptions vulkanOptions = GpuRuntimeCompileOptions.vulkan(
                List.of("--target-env=vulkan1.3"),
                Map.of("entryModel", "GLCompute"),
                "spirv-safe"
        );
        GpuRuntimeCompileOptions metalOptions = GpuRuntimeCompileOptions.metal(
                List.of("-ffast-math"),
                Map.of("languageVersion", "3.1"),
                "apple-fast"
        );

        assertEquals(GpuBackendTarget.CUDA, cudaOptions.backendTarget());
        assertTrue(cudaOptions.compileArgs().isEmpty());
        assertEquals(List.of("--use_fast_math", "--gpu-architecture=compute_89"), cudaOptions.backendOptions().flags());
        assertEquals("ptx", cudaOptions.backendOptions().properties().get("linkMode"));
        assertEquals("nvidia-fast", cudaOptions.optimizationProfile());

        assertEquals(GpuBackendTarget.VULKAN, vulkanOptions.backendOptions().backendTarget());
        assertEquals("GLCompute", vulkanOptions.backendOptions().properties().get("entryModel"));

        assertEquals(GpuBackendTarget.METAL, metalOptions.backendOptions().backendTarget());
        assertEquals("3.1", metalOptions.backendOptions().properties().get("languageVersion"));
    }

    @Test
    void compileOptionsExposeOptInStandardBackendDevicePreflight() {
        GpuRuntimeCompileOptions defaults = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL);

        assertEquals(
                GpuBackendCompileOptions.RUNTIME_BACKEND_DEVICE_PREFLIGHT_DISABLED,
                defaults.backendOptions().backendDevicePreflightMode()
        );
        assertFalse(defaults.backendOptions().requestsStandardBackendDevicePreflight());

        GpuRuntimeCompileOptions preflight = defaults.withStandardBackendDevicePreflight();

        assertEquals(GpuBackendTarget.OPENCL, preflight.backendTarget());
        assertEquals(
                GpuBackendCompileOptions.RUNTIME_BACKEND_DEVICE_PREFLIGHT_STANDARD,
                preflight.backendOptions().backendDevicePreflightMode()
        );
        assertEquals(
                GpuBackendCompileOptions.RUNTIME_BACKEND_DEVICE_PREFLIGHT_STANDARD,
                preflight.backendOptions().properties().get(
                        GpuBackendCompileOptions.RUNTIME_BACKEND_DEVICE_PREFLIGHT_PROPERTY
                )
        );
        assertTrue(preflight.backendOptions().requestsStandardBackendDevicePreflight());
        assertFalse(preflight.withoutBackendDevicePreflight()
                .backendOptions()
                .requestsStandardBackendDevicePreflight());
    }

    @Test
    void backendDevicePreflightPropertyRejectsUnknownMode() {
        GpuBackendCompileOptions backendOptions = GpuBackendCompileOptions.openCl(
                List.of(),
                Map.of(GpuBackendCompileOptions.RUNTIME_BACKEND_DEVICE_PREFLIGHT_PROPERTY, "standrad")
        );

        assertEquals(
                Optional.of("runtime-backend-device-preflight-mode-invalid"),
                backendOptions.backendDevicePreflightModeBlocker()
        );
        assertEquals(
                GpuBackendCompileOptions.RUNTIME_BACKEND_DEVICE_PREFLIGHT_DISABLED,
                backendOptions.backendDevicePreflightMode()
        );
        assertFalse(backendOptions.requestsStandardBackendDevicePreflight());
    }

    @Test
    void invokeWithCompileOptionsCanAutoInstallStandardBackendDeviceWhenExplicitlyRequested() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of()
        );
        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions
                .defaults(GpuBackendTarget.OPENCL)
                .withStandardBackendDevicePreflight();
        java.util.concurrent.atomic.AtomicReference<GpuKernelInvocation> capturedInvocation =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger scopeCalls = new java.util.concurrent.atomic.AtomicInteger();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.resetBackend();
        GpuRuntime.setAutomaticBackendDevicePreflightScopeFactoryForTesting(options -> {
            scopeCalls.incrementAndGet();
            assertSame(compileOptions, options);
            return GpuRuntime.useBackend(capturedInvocation::set);
        });

        try {
            GpuExecutionConfig executionConfig = GpuExecutionConfig.oneDimensional(13L);

            GpuRuntime.invokeWithCompileOptions(executionConfig, compileOptions, descriptor, new Object[0]);

            GpuKernelInvocation invocation = capturedInvocation.get();
            assertEquals(1, scopeCalls.get());
            assertSame(compileOptions, invocation.compileOptions());
            assertSame(executionConfig, invocation.executionConfig());
            assertSame(GpuRuntime.defaultBackend(), GpuRuntime.backend());
        } finally {
            GpuRuntime.resetAutomaticBackendDevicePreflightScopeFactoryForTesting();
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void invokeWithCompileOptionsPublishesAutomaticBackendDevicePreflightLifecycleEvents() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of()
        );
        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions
                .defaults(GpuBackendTarget.OPENCL)
                .withStandardBackendDevicePreflight();
        ArrayList<GpuRuntimeLifecycleEvent> events = new ArrayList<>();
        GpuRuntimeLifecycleEventBus eventBus = GpuRuntimeLifecycleEventBus.of(List.of(events::add));
        java.util.concurrent.atomic.AtomicReference<GpuKernelInvocation> capturedInvocation =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger scopeCalls = new java.util.concurrent.atomic.AtomicInteger();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.resetBackend();
        GpuRuntime.setAutomaticBackendDevicePreflightLifecycleEventBusFactoryForTesting(() -> eventBus);
        GpuRuntime.setAutomaticBackendDevicePreflightScopeFactoryForTesting((options, lifecycleEventBus) -> {
            scopeCalls.incrementAndGet();
            assertSame(compileOptions, options);
            assertSame(eventBus, lifecycleEventBus);
            return GpuRuntime.useBackend(capturedInvocation::set);
        });

        try {
            GpuRuntime.invokeWithCompileOptions(
                    GpuExecutionConfig.oneDimensional(5L),
                    compileOptions,
                    descriptor,
                    new Object[0]
            );

            assertEquals(1, scopeCalls.get());
            assertSame(compileOptions, capturedInvocation.get().compileOptions());
            assertEquals(List.of(
                    GpuRuntimeLifecycleEventKind.BACKEND_DEVICE_PREFLIGHT_STARTED,
                    GpuRuntimeLifecycleEventKind.BACKEND_DEVICE_PREFLIGHT_COMPLETED
            ), events.stream().map(GpuRuntimeLifecycleEvent::kind).toList());
            assertEquals(GpuBackendTarget.OPENCL, events.get(0).backendTarget());
            assertEquals("javatogpu/sample/Demo/kernel.cl", events.get(0).kernelResource());
            assertEquals("runtime-backend-device-preflight", events.get(0).fields().get("pipeline"));
            assertEquals("compile-options", events.get(0).fields().get("trigger"));
            assertEquals("started", events.get(0).fields().get("status"));
            assertEquals("success", events.get(1).fields().get("status"));
            assertEquals("standard", events.get(0).fields().get("backendDevicePreflight.mode"));
            assertEquals("true", events.get(0).fields().get("backendDevicePreflight.requested"));
            assertEquals("kernel", events.get(0).fields().get("kernel.name"));
            assertEquals("1", events.get(0).fields().get("execution.dimensions"));
            assertEquals("5", events.get(0).fields().get("execution.globalShape"));
            assertEquals("started", events.get(0).fields().get("runtime.status"));
            assertEquals("success", events.get(1).fields().get("runtime.status"));
            assertEquals("kernel", events.get(0).fields().get("runtime.kernel.name"));
            assertEquals("javatogpu/sample/Demo/kernel.cl", events.get(0).fields().get("runtime.kernel.resource"));
            assertEquals("OPENCL", events.get(0).fields().get("runtime.backend.target"));
            assertEquals("off", events.get(0).fields().get("runtime.compile.optimizationProfile"));
            assertEquals("standard", events.get(0).fields().get("runtime.backendDevicePreflight.mode"));
            assertEquals("true", events.get(0).fields().get("runtime.backendDevicePreflight.requested"));
            assertEquals("1", events.get(0).fields().get("runtime.work.dimensions"));
            assertEquals("5", events.get(0).fields().get("runtime.work.globalShape"));
            assertSame(GpuRuntime.defaultBackend(), GpuRuntime.backend());
        } finally {
            GpuRuntime.resetAutomaticBackendDevicePreflightScopeFactoryForTesting();
            GpuRuntime.resetAutomaticBackendDevicePreflightLifecycleEventBusFactoryForTesting();
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void invokeWithCompileOptionsPublishesFailedAutomaticBackendDevicePreflightLifecycleEvent() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of()
        );
        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions
                .defaults(GpuBackendTarget.OPENCL)
                .withStandardBackendDevicePreflight();
        RuntimeException failure = new IllegalStateException("preflight backend failed");
        ArrayList<GpuRuntimeLifecycleEvent> events = new ArrayList<>();
        GpuRuntimeLifecycleEventBus eventBus = GpuRuntimeLifecycleEventBus.of(List.of(events::add));
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.resetBackend();
        GpuRuntime.setAutomaticBackendDevicePreflightLifecycleEventBusFactoryForTesting(() -> eventBus);
        GpuRuntime.setAutomaticBackendDevicePreflightScopeFactoryForTesting((options, lifecycleEventBus) -> {
            assertSame(compileOptions, options);
            assertSame(eventBus, lifecycleEventBus);
            return GpuRuntime.useBackend(runtimeInvocation -> {
                throw failure;
            });
        });

        try {
            RuntimeException thrown = assertThrows(IllegalStateException.class, () -> GpuRuntime.invokeWithCompileOptions(
                    GpuExecutionConfig.oneDimensional(9L),
                    compileOptions,
                    descriptor,
                    new Object[0]
            ));

            assertSame(failure, thrown);
            assertEquals(List.of(
                    GpuRuntimeLifecycleEventKind.BACKEND_DEVICE_PREFLIGHT_STARTED,
                    GpuRuntimeLifecycleEventKind.BACKEND_DEVICE_PREFLIGHT_COMPLETED
            ), events.stream().map(GpuRuntimeLifecycleEvent::kind).toList());
            Map<String, String> failedFields = events.get(1).fields();
            assertEquals("failed", failedFields.get("status"));
            assertEquals("failed", failedFields.get("runtime.status"));
            assertEquals(IllegalStateException.class.getName(), failedFields.get("error.type"));
            assertEquals("preflight backend failed", failedFields.get("error.message"));
            assertEquals(IllegalStateException.class.getName(), failedFields.get("runtime.failure.type"));
            assertEquals("preflight backend failed", failedFields.get("runtime.failure.message"));
            assertEquals("kernel", failedFields.get("runtime.kernel.name"));
            assertEquals("OPENCL", failedFields.get("runtime.backend.target"));
            assertEquals("standard", failedFields.get("runtime.backendDevicePreflight.mode"));
            assertEquals("9", failedFields.get("runtime.work.globalShape"));
            assertSame(GpuRuntime.defaultBackend(), GpuRuntime.backend());
        } finally {
            GpuRuntime.resetAutomaticBackendDevicePreflightScopeFactoryForTesting();
            GpuRuntime.resetAutomaticBackendDevicePreflightLifecycleEventBusFactoryForTesting();
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void invokeWithCompileOptionsDoesNotOverrideConfiguredBackendForPreflightProfile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of()
        );
        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions
                .defaults(GpuBackendTarget.OPENCL)
                .withStandardBackendDevicePreflight();
        java.util.concurrent.atomic.AtomicReference<GpuKernelInvocation> capturedInvocation =
                new java.util.concurrent.atomic.AtomicReference<>();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntimeBackend configuredBackend = capturedInvocation::set;
        GpuRuntime.setBackend(configuredBackend);
        GpuRuntime.setAutomaticBackendDevicePreflightScopeFactoryForTesting(options -> {
            throw new AssertionError("preflight scope factory should not run when a backend is already configured");
        });

        try {
            GpuRuntime.invokeWithCompileOptions(compileOptions, descriptor, new Object[0]);

            assertSame(compileOptions, capturedInvocation.get().compileOptions());
            assertSame(configuredBackend, GpuRuntime.backend());
        } finally {
            GpuRuntime.resetAutomaticBackendDevicePreflightScopeFactoryForTesting();
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void invokeVariantsFromGeneratedLauncherUsesAutoBackendDevicePreflightProfile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of()
        );
        GpuKernelDescriptor fallback = new GpuKernelDescriptor(
                "kernel_fallback",
                "javatogpu/sample/Demo/kernel_fallback.cl",
                "__kernel void kernel_fallback(__global int* output) { output[0] = 2; }",
                java.util.List.of()
        );
        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions
                .defaults(GpuBackendTarget.OPENCL)
                .withStandardBackendDevicePreflight();
        java.util.concurrent.atomic.AtomicReference<GpuKernelInvocation> capturedInvocation =
                new java.util.concurrent.atomic.AtomicReference<>();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.resetBackend();
        GpuRuntime.setAutomaticBackendDevicePreflightScopeFactoryForTesting(options -> GpuRuntime.useBackend(capturedInvocation::set));

        try {
            GpuExecutionConfig executionConfig = GpuExecutionConfig.oneDimensional(7L);

            GpuRuntime.invokeVariantsFromGeneratedLauncher(
                    FixtureOwner.class,
                    executionConfig,
                    compileOptions,
                    descriptor,
                    List.of(fallback),
                    new Object[0]
            );

            GpuKernelInvocation invocation = capturedInvocation.get();
            assertSame(compileOptions, invocation.compileOptions());
            assertSame(executionConfig, invocation.executionConfig());
            assertSame(FixtureOwner.class.getClassLoader(), invocation.artifactClassLoader());
            assertEquals(List.of(fallback), invocation.fallbackDescriptors());
        } finally {
            GpuRuntime.resetAutomaticBackendDevicePreflightScopeFactoryForTesting();
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void invokeWithExecutionConfigPassesConfigIntoKernelInvocation() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of()
        );
        java.util.concurrent.atomic.AtomicReference<GpuKernelInvocation> capturedInvocation = new java.util.concurrent.atomic.AtomicReference<>();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.setBackend(capturedInvocation::set);

        try {
            GpuRuntime.invoke(GpuExecutionConfig.oneDimensional(11L), descriptor, new Object[0]);
            GpuKernelInvocation invocation = capturedInvocation.get();
            assertEquals(11L, invocation.globalWorkSize());
            assertEquals(11L, invocation.executionConfig().globalWorkSize());
            assertEquals(1, invocation.executionConfig().dimensions());
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void invokeWithCompileOptionsPassesOptionsIntoKernelInvocation() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of()
        );
        GpuRuntimeCompileOptions compileOptions = new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                java.util.List.of("-cl-fast-relaxed-math"),
                "vendor-tuned"
        );
        java.util.concurrent.atomic.AtomicReference<GpuKernelInvocation> capturedInvocation = new java.util.concurrent.atomic.AtomicReference<>();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.setBackend(capturedInvocation::set);

        try {
            GpuRuntime.invokeWithCompileOptions(GpuExecutionConfig.oneDimensional(13L), compileOptions, descriptor, new Object[0]);
            GpuKernelInvocation invocation = capturedInvocation.get();
            assertSame(compileOptions, invocation.compileOptions());
            assertEquals(13L, invocation.globalWorkSize());
            assertEquals(java.util.List.of("-cl-fast-relaxed-math"), invocation.compileOptions().compileArgs());
            assertEquals("vendor-tuned", invocation.compileOptions().optimizationProfile());
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void generatedLauncherInvokerWithConfigUsesGeneratedDescriptor() {
        java.util.concurrent.atomic.AtomicReference<GpuKernelInvocation> capturedInvocation = new java.util.concurrent.atomic.AtomicReference<>();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.setBackend(capturedInvocation::set);

        try {
            int[] output = new int[4];
            GpuExecutionConfig config = GpuExecutionConfig.oneDimensional(3L);

            GpuGeneratedLauncherInvoker.invokeWithConfig(FixtureOwner.class, "kernel", config, output);

            GpuKernelInvocation invocation = capturedInvocation.get();
            assertEquals("fixture_kernel", invocation.descriptor().kernelName());
            assertEquals("javatogpu/runtime/FixtureOwner/kernel.cl", invocation.descriptor().kernelResource());
            assertSame(config, invocation.executionConfig());
            assertSame(output, invocation.arguments()[0]);
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void generatedLauncherInvokerWith3DWorkSizeUsesGeneratedDescriptor() {
        java.util.concurrent.atomic.AtomicReference<GpuKernelInvocation> capturedInvocation = new java.util.concurrent.atomic.AtomicReference<>();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.setBackend(capturedInvocation::set);

        try {
            int[] output = new int[4];

            GpuGeneratedLauncherInvoker.invokeWith3DWorkSize(FixtureOwner.class, "kernel", 16L, 8L, 4L, output);

            GpuKernelInvocation invocation = capturedInvocation.get();
            assertEquals("fixture_kernel", invocation.descriptor().kernelName());
            assertEquals("javatogpu/runtime/FixtureOwner/kernel.cl", invocation.descriptor().kernelResource());
            assertEquals(3, invocation.executionConfig().dimensions());
            assertEquals(16L, invocation.executionConfig().globalX());
            assertEquals(8L, invocation.executionConfig().globalY());
            assertEquals(4L, invocation.executionConfig().globalZ());
            assertSame(output, invocation.arguments()[0]);
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void generatedLauncherInvokerWithCompileOptionsUsesGeneratedDescriptor() {
        java.util.concurrent.atomic.AtomicReference<GpuKernelInvocation> capturedInvocation = new java.util.concurrent.atomic.AtomicReference<>();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.setBackend(capturedInvocation::set);

        try {
            int[] output = new int[4];
            GpuRuntimeCompileOptions compileOptions = new GpuRuntimeCompileOptions(
                    GpuBackendTarget.OPENCL,
                    java.util.List.of("-cl-mad-enable"),
                    "nvidia-fast"
            );

            GpuGeneratedLauncherInvoker.invokeWithCompileOptions(FixtureOwner.class, "kernel", compileOptions, output);

            GpuKernelInvocation invocation = capturedInvocation.get();
            assertEquals("fixture_kernel", invocation.descriptor().kernelName());
            assertSame(compileOptions, invocation.compileOptions());
            assertSame(output, invocation.arguments()[0]);
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void generatedLauncherInvokerWithConfigAndCompileOptionsUsesGeneratedDescriptor() {
        java.util.concurrent.atomic.AtomicReference<GpuKernelInvocation> capturedInvocation = new java.util.concurrent.atomic.AtomicReference<>();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.setBackend(capturedInvocation::set);

        try {
            int[] output = new int[4];
            GpuExecutionConfig config = GpuExecutionConfig.threeDimensional(8L, 4L, 2L);
            GpuRuntimeCompileOptions compileOptions = new GpuRuntimeCompileOptions(
                    GpuBackendTarget.OPENCL,
                    java.util.List.of("-cl-no-signed-zeros"),
                    "vendor-profile"
            );

            GpuGeneratedLauncherInvoker.invokeWithConfigAndCompileOptions(
                    FixtureOwner.class,
                    "kernel",
                    config,
                    compileOptions,
                    output
            );

            GpuKernelInvocation invocation = capturedInvocation.get();
            assertEquals("fixture_kernel", invocation.descriptor().kernelName());
            assertSame(config, invocation.executionConfig());
            assertSame(compileOptions, invocation.compileOptions());
            assertSame(output, invocation.arguments()[0]);
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void generatedLauncherInvokerWithStandardBackendAndDeviceUsesScopedSelection() {
        java.util.concurrent.atomic.AtomicReference<GpuKernelInvocation> capturedInvocation =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger scopeCalls = new java.util.concurrent.atomic.AtomicInteger();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions
                .defaults(GpuBackendTarget.OPENCL)
                .preferDeviceVendor("NVIDIA");
        GpuRuntime.setStandardBackendDeviceScopeFactoryForTesting(options -> {
            scopeCalls.incrementAndGet();
            assertSame(compileOptions, options);
            return GpuRuntime.useBackend(capturedInvocation::set);
        });
        GpuRuntime.resetBackend();

        try {
            int[] output = new int[4];
            GpuExecutionConfig config = GpuExecutionConfig.twoDimensional(4L, 2L);

            GpuGeneratedLauncherInvoker.invokeWithConfigAndStandardBackendAndDevice(
                    FixtureOwner.class,
                    "kernel",
                    config,
                    compileOptions,
                    output
            );

            GpuKernelInvocation invocation = capturedInvocation.get();
            assertEquals(1, scopeCalls.get());
            assertEquals("fixture_kernel", invocation.descriptor().kernelName());
            assertSame(config, invocation.executionConfig());
            assertSame(compileOptions, invocation.compileOptions());
            assertSame(output, invocation.arguments()[0]);
            assertSame(GpuRuntime.defaultBackend(), GpuRuntime.backend());
        } finally {
            GpuRuntime.resetStandardBackendDeviceScopeFactoryForTesting();
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void generatedLauncherHandleWithStandardBackendAndDeviceUsesScopedSelection() {
        java.util.concurrent.atomic.AtomicReference<GpuKernelInvocation> capturedInvocation =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger scopeCalls = new java.util.concurrent.atomic.AtomicInteger();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions
                .defaults(GpuBackendTarget.OPENCL)
                .excludeCpuDevices();
        GpuRuntime.setStandardBackendDeviceScopeFactoryForTesting(options -> {
            scopeCalls.incrementAndGet();
            assertSame(compileOptions, options);
            return GpuRuntime.useBackend(capturedInvocation::set);
        });
        GpuRuntime.resetBackend();

        try {
            int[] output = new int[4];
            GpuGeneratedLauncherInvoker.GeneratedLauncher launcher = GpuGeneratedLauncherInvoker.launcher(
                    FixtureOwner.class,
                    "kernel"
            );

            launcher.invokeWithGlobalWorkSizeAndStandardBackendAndDevice(17L, compileOptions, output);

            GpuKernelInvocation invocation = capturedInvocation.get();
            assertEquals(1, scopeCalls.get());
            assertEquals("fixture_kernel", invocation.descriptor().kernelName());
            assertEquals(17L, invocation.globalWorkSize());
            assertSame(compileOptions, invocation.compileOptions());
            assertSame(output, invocation.arguments()[0]);
            assertSame(GpuRuntime.defaultBackend(), GpuRuntime.backend());
        } finally {
            GpuRuntime.resetStandardBackendDeviceScopeFactoryForTesting();
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void generatedLauncherInvokerReturningFirstWithStandardBackendAndDeviceUsesScopedSelection() {
        java.util.concurrent.atomic.AtomicReference<GpuKernelInvocation> capturedInvocation =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger scopeCalls = new java.util.concurrent.atomic.AtomicInteger();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions
                .defaults(GpuBackendTarget.OPENCL)
                .preferDeviceClass(GpuDeviceClassTarget.DGPU);
        GpuRuntime.setStandardBackendDeviceScopeFactoryForTesting(options -> {
            scopeCalls.incrementAndGet();
            assertSame(compileOptions, options);
            return GpuRuntime.useBackend(invocation -> {
                capturedInvocation.set(invocation);
                ((int[]) invocation.arguments()[0])[0] = invocation.globalWorkSize().intValue();
            });
        });
        GpuRuntime.resetBackend();

        try {
            Integer value = GpuGeneratedLauncherInvoker.invokeReturningFirstWithGlobalWorkSizeAndStandardBackendAndDeviceAs(
                    Integer.class,
                    FixtureOwner.class,
                    "kernel",
                    19L,
                    compileOptions
            );

            GpuKernelInvocation invocation = capturedInvocation.get();
            assertEquals(1, scopeCalls.get());
            assertEquals(19, value.intValue());
            assertEquals(19L, invocation.globalWorkSize());
            assertSame(compileOptions, invocation.compileOptions());
            assertEquals(1, invocation.arguments().length);
            assertEquals(19, ((int[]) invocation.arguments()[0])[0]);
            assertSame(GpuRuntime.defaultBackend(), GpuRuntime.backend());
        } finally {
            GpuRuntime.resetStandardBackendDeviceScopeFactoryForTesting();
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void generatedLauncherHandleReturningFirstWithStandardBackendAndDeviceUsesScopedSelection() {
        java.util.concurrent.atomic.AtomicReference<GpuKernelInvocation> capturedInvocation =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger scopeCalls = new java.util.concurrent.atomic.AtomicInteger();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions
                .defaults(GpuBackendTarget.OPENCL)
                .excludeIntegratedAndCpuDevices();
        GpuRuntime.setStandardBackendDeviceScopeFactoryForTesting(options -> {
            scopeCalls.incrementAndGet();
            assertSame(compileOptions, options);
            return GpuRuntime.useBackend(invocation -> {
                capturedInvocation.set(invocation);
                ((int[]) invocation.arguments()[0])[0] = invocation.globalWorkSize().intValue();
            });
        });
        GpuRuntime.resetBackend();

        try {
            GpuGeneratedLauncherInvoker.GeneratedLauncher launcher = GpuGeneratedLauncherInvoker.launcher(
                    FixtureOwner.class,
                    "kernel"
            );

            Integer value = launcher.invokeReturningFirstWithConfigAndStandardBackendAndDeviceAs(
                    Integer.class,
                    GpuExecutionConfig.oneDimensional(23L),
                    compileOptions
            );

            GpuKernelInvocation invocation = capturedInvocation.get();
            assertEquals(1, scopeCalls.get());
            assertEquals(23, value.intValue());
            assertEquals(23L, invocation.globalWorkSize());
            assertSame(compileOptions, invocation.compileOptions());
            assertEquals(1, invocation.arguments().length);
            assertEquals(23, ((int[]) invocation.arguments()[0])[0]);
            assertSame(GpuRuntime.defaultBackend(), GpuRuntime.backend());
        } finally {
            GpuRuntime.resetStandardBackendDeviceScopeFactoryForTesting();
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void executionConfigSupportsTwoDimensionalLaunches() {
        GpuExecutionConfig config = GpuExecutionConfig.twoDimensional(16L, 8L, 4L, 2L);

        assertEquals(2, config.dimensions());
        assertEquals(16L, config.globalX());
        assertEquals(8L, config.globalY());
        assertEquals(4L, config.localX());
        assertEquals(2L, config.localY());
        assertEquals("16x8", config.globalShape());
        assertEquals("4x2", config.localShape());
        assertTrue(config.hasExplicitLocalSize());
        assertEquals(128L, config.globalItemCount());
        assertEquals(8L, config.localItemCount());
        assertEquals("2D global=16x8, local=4x2", config.summary());
    }

    @Test
    void executionConfigSupportsThreeDimensionalLaunches() {
        GpuExecutionConfig config = GpuExecutionConfig.threeDimensional(16L, 8L, 4L, 4L, 2L, 1L);

        assertEquals(3, config.dimensions());
        assertEquals(16L, config.globalX());
        assertEquals(8L, config.globalY());
        assertEquals(4L, config.globalZ());
        assertEquals(4L, config.localX());
        assertEquals(2L, config.localY());
        assertEquals(1L, config.localZ());
        assertEquals(16L, config.globalWorkSize());
        assertEquals("16x8x4", config.globalShape());
        assertEquals("4x2x1", config.localShape());
        assertTrue(config.hasExplicitLocalSize());
        assertEquals(512L, config.globalItemCount());
        assertEquals(8L, config.localItemCount());
        assertEquals("3D global=16x8x4, local=4x2x1", config.summary());
    }

    @Test
    void executionConfigSummarizesAutomaticLocalSizing() {
        GpuExecutionConfig config = GpuExecutionConfig.threeDimensional(8L, 4L, 2L);

        assertEquals("8x4x2", config.globalShape());
        assertEquals("auto", config.localShape());
        assertFalse(config.hasExplicitLocalSize());
        assertEquals(64L, config.globalItemCount());
        assertEquals(0L, config.localItemCount());
        assertEquals("3D global=8x4x2, local=auto", config.summary());
    }

    @Test
    void executionConfigRejectsNonPositiveGlobalWorkSize() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GpuExecutionConfig.oneDimensional(0L)
        );

        assertEquals("globalX must be positive: 0", exception.getMessage());
    }

    @Test
    void executionConfigRejectsInvalidTwoDimensionalLocalShape() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GpuExecutionConfig.twoDimensional(16L, 8L, 4L, 0L)
        );

        assertEquals("localX/localY must both be zero or both be > 0 for 2D execution", exception.getMessage());
    }

    @Test
    void executionConfigRejectsNonPositiveThreeDimensionalGlobalZ() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GpuExecutionConfig.threeDimensional(16L, 8L, 0L)
        );

        assertEquals("globalZ must be positive for 3D execution: 0", exception.getMessage());
    }

    @Test
    void executionConfigRejectsInvalidThreeDimensionalLocalShape() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GpuExecutionConfig.threeDimensional(16L, 8L, 4L, 4L, 2L, 0L)
        );

        assertEquals("localX/localY/localZ must all be zero or all be > 0 for 3D execution", exception.getMessage());
    }

    @Test
    void defaultBackendThrowsHelpfulError() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of()
        );

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> GpuRuntime.defaultBackend().invoke(new GpuKernelInvocation(descriptor, new Object[0]))
        );

        assertEquals("GPU runtime backend is not configured for kernel kernel", exception.getMessage());
    }

    @Test
    void setBackendRejectsNullAndResetBackendRestoresDefault() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> GpuRuntime.setBackend(null)
        );

        assertEquals("newBackend", exception.getMessage());

        GpuRuntimeBackend backend = invocation -> {
        };
        GpuRuntime.setBackend(backend);
        assertSame(backend, GpuRuntime.backend());

        GpuRuntime.resetBackend();
        assertSame(GpuRuntime.defaultBackend(), GpuRuntime.backend());
    }

    @Test
    void scopedBackendRestoresPreviousBackendOnClose() {
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntimeBackend scopedBackend = invocation -> {
        };

        try (GpuRuntimeScope ignored = GpuRuntime.useBackend(scopedBackend)) {
            assertSame(previousBackend, ignored.previousBackend());
            assertSame(scopedBackend, ignored.installedBackend());
            assertTrue(!ignored.ownsInstalledBackend());
            assertTrue(!ignored.closed());
            assertSame(scopedBackend, GpuRuntime.backend());
        }

        assertSame(previousBackend, GpuRuntime.backend());
    }

    @Test
    void ownedScopedBackendClosesInstalledBackendOnClose() {
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();

        final class CloseableBackend implements GpuRuntimeBackend, AutoCloseable {
            private int closeCalls;

            @Override
            public void invoke(GpuKernelInvocation invocation) {
            }

            @Override
            public void close() {
                closeCalls++;
            }
        }

        CloseableBackend backend = new CloseableBackend();

        try (GpuRuntimeScope ignored = GpuRuntime.useOwnedBackend(backend)) {
            assertTrue(ignored.ownsInstalledBackend());
            assertSame(backend, GpuRuntime.backend());
        }

        assertSame(previousBackend, GpuRuntime.backend());
        assertEquals(1, backend.closeCalls);
    }

    @Test
    void nestedScopesMustCloseInLifoOrder() {
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntimeBackend outerBackend = invocation -> {
        };
        GpuRuntimeBackend innerBackend = invocation -> {
        };

        GpuRuntimeScope outer = GpuRuntime.useBackend(outerBackend);
        GpuRuntimeScope inner = GpuRuntime.useBackend(innerBackend);

        IllegalStateException exception = assertThrows(IllegalStateException.class, outer::close);
        assertTrue(exception.getMessage().contains("cannot close out of order"));
        assertSame(innerBackend, GpuRuntime.backend());
        assertTrue(!outer.closed());

        inner.close();
        assertSame(outerBackend, GpuRuntime.backend());
        assertTrue(inner.closed());

        outer.close();
        assertSame(previousBackend, GpuRuntime.backend());
        assertTrue(outer.closed());
    }

    @Test
    void openClSharedScopeRestoresPreviousBackend() {
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();

        try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    GpuRuntime.backend() instanceof net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend
            );
        } finally {
            GpuRuntime.shutdownOpenClSharedCache();
        }

        assertSame(previousBackend, GpuRuntime.backend());
    }

    @Test
    void selectFirstMatchingChoosesFirstBackendThatSatisfiesRequirements() {
        GpuRuntimeBackend cudaBackend = new ReportingBackend(
                GpuRuntimeBackendReport.unavailable(GpuBackendTarget.CUDA, "CUDA", "CUDA runtime is unavailable")
        );
        GpuRuntimeBackend openClBackend = new ReportingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Fake GPU",
                        new GpuRuntimeApiVersion(3, 0),
                        "OpenCL 3.0 Fake GPU",
                        java.util.EnumSet.of(GpuRuntimeFeature.DOUBLE_PRECISION, GpuRuntimeFeature.IMAGES),
                        32_768L,
                        256L,
                        null
                )
        );

        GpuRuntimeBackendSelection selection = GpuRuntime.selectFirstMatching(
                java.util.List.of(
                        GpuRuntimeRequirements.minimumApiVersion(GpuBackendTarget.OPENCL, 3, 0),
                        GpuRuntimeRequirements.requireFeature(GpuBackendTarget.OPENCL, GpuRuntimeFeature.IMAGES)
                ),
                cudaBackend,
                openClBackend
        );

        assertSame(openClBackend, selection.backend());
        assertEquals(GpuBackendTarget.OPENCL, selection.report().backendTarget());
        assertEquals("Fake GPU", selection.report().deviceLabel());
        assertEquals(GpuRuntimeBackendOwnership.BORROWED, selection.ownership());
        assertTrue(!selection.ownsBackend());
    }

    @Test
    void useFirstMatchingClosesRejectedCandidatesAndInstallsFallback() {
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();

        final class CloseableReportingBackend implements GpuRuntimeBackend, AutoCloseable {
            private final GpuRuntimeBackendReport report;
            private int closeCalls;

            private CloseableReportingBackend(GpuRuntimeBackendReport report) {
                this.report = report;
            }

            @Override
            public net.sixik.ga_utils.javatogpu.api.GpuBackendTarget backendTarget() {
                return report.backendTarget();
            }

            @Override
            public GpuRuntimeBackendReport describeCapabilities() {
                return report;
            }

            @Override
            public void invoke(GpuKernelInvocation invocation) {
            }

            @Override
            public void close() {
                closeCalls++;
            }
        }

        CloseableReportingBackend unavailableCuda = new CloseableReportingBackend(
                GpuRuntimeBackendReport.unavailable(GpuBackendTarget.CUDA, "CUDA", "CUDA driver is unavailable")
        );
        CloseableReportingBackend openCl = new CloseableReportingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Fake GPU",
                        new GpuRuntimeApiVersion(3, 0),
                        "OpenCL 3.0 Fake GPU",
                        java.util.EnumSet.of(GpuRuntimeFeature.IMAGES),
                        16_384L,
                        128L,
                        null
                )
        );

        try (GpuRuntimeScope ignored = GpuRuntime.useFirstMatching(
                java.util.List.of(GpuRuntimeRequirements.minimumApiVersion(GpuBackendTarget.OPENCL, 3, 0)),
                () -> unavailableCuda,
                () -> openCl
        )) {
            assertSame(openCl, GpuRuntime.backend());
        }

        assertSame(previousBackend, GpuRuntime.backend());
        assertEquals(1, unavailableCuda.closeCalls);
        assertEquals(1, openCl.closeCalls);
    }

    @Test
    void selectFirstMatchingExplainsRejectedCandidates() {
        GpuRuntimeBackend cudaBackend = new ReportingBackend(
                GpuRuntimeBackendReport.unavailable(GpuBackendTarget.CUDA, "CUDA", "CUDA runtime is unavailable")
        );
        GpuRuntimeBackend openClBackend = new ReportingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Legacy GPU",
                        new GpuRuntimeApiVersion(2, 0),
                        "OpenCL 2.0 Legacy GPU",
                        java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                        4_096L,
                        64L,
                        null
                )
        );

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> GpuRuntime.selectFirstMatching(
                        java.util.List.of(
                                GpuRuntimeRequirements.minimumApiVersion(GpuBackendTarget.OPENCL, 3, 0),
                                GpuRuntimeRequirements.requireFeature(GpuBackendTarget.OPENCL, GpuRuntimeFeature.IMAGES)
                        ),
                        cudaBackend,
                        openClBackend
                )
        );

        assertTrue(exception.getMessage().contains("CUDA: CUDA runtime is unavailable"));
        assertTrue(exception.getMessage().contains("OpenCL: requires API version at least 3.0 but found 2.0; missing feature IMAGES"));
    }

    @Test
    void trySelectFirstMatchingReturnsFailureSummaryInsteadOfThrowing() {
        GpuRuntimeBackend cudaBackend = new ReportingBackend(
                GpuRuntimeBackendReport.unavailable(GpuBackendTarget.CUDA, "CUDA", "CUDA runtime is unavailable")
        );
        GpuRuntimeBackend openClBackend = new ReportingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Legacy GPU",
                        new GpuRuntimeApiVersion(2, 0),
                        "OpenCL 2.0 Legacy GPU",
                        java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                        4_096L,
                        64L,
                        null
                )
        );

        GpuRuntimeSelectionResult result = GpuRuntime.trySelectFirstMatching(
                java.util.List.of(
                        GpuRuntimeRequirements.minimumApiVersion(GpuBackendTarget.OPENCL, 3, 0),
                        GpuRuntimeRequirements.requireFeature(GpuBackendTarget.OPENCL, GpuRuntimeFeature.IMAGES)
                ),
                cudaBackend,
                openClBackend
        );

        assertTrue(!result.matched());
        assertTrue(result.failureSummary().contains("CUDA: CUDA runtime is unavailable"));
        assertTrue(result.failureSummary().contains("OpenCL: requires API version at least 3.0 but found 2.0; missing feature IMAGES"));
        assertEquals(2, result.candidateDecisions().size());
        assertEquals(GpuBackendTarget.CUDA, result.candidateDecisions().get(0).backendTarget());
        assertEquals("CUDA runtime is unavailable", result.candidateDecisions().get(0).firstBlocker());
        assertEquals(GpuBackendTarget.OPENCL, result.candidateDecisions().get(1).backendTarget());
        assertTrue(result.explanationSummary().contains("CUDA: CUDA runtime is unavailable"));
    }

    @Test
    void trySelectFirstMatchingWithoutCandidatesExplainsTheMiss() {
        GpuRuntimeSelectionResult result = GpuRuntime.trySelectFirstMatching(java.util.List.of());

        assertTrue(!result.matched());
        assertEquals("no backend candidates were provided", result.failureSummary());

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                result::requireSelection
        );
        assertTrue(exception.getMessage().contains("no backend candidates were provided"));
    }

    @Test
    void useFirstMatchingWithoutCandidatesFailsWithHelpfulMessage() {
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> GpuRuntime.useFirstMatching(java.util.List.of())
        );

        assertEquals(
                "No GPU runtime backend satisfies the requested requirements: no backend candidates were provided",
                exception.getMessage()
        );
    }

    @Test
    void useFirstMatchingExplainsFactoryCreationFailures() {
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> GpuRuntime.useFirstMatching(
                        java.util.List.of(GpuRuntimeRequirements.requireFeature(GpuBackendTarget.OPENCL, GpuRuntimeFeature.DOUBLE_PRECISION)),
                        () -> {
                            throw new IllegalStateException("driver init failed");
                        },
                        () -> new ReportingBackend(
                                GpuRuntimeBackendReport.unavailable(
                                        GpuBackendTarget.OPENCL,
                                        "OpenCL",
                                        "OpenCL ICD loader is unavailable"
                                )
                        )
                )
        );

        assertTrue(exception.getMessage().contains("Failed to create backend candidate: driver init failed"));
        assertTrue(exception.getMessage().contains("OpenCL: OpenCL ICD loader is unavailable; missing feature DOUBLE_PRECISION"));
    }

    @Test
    void backendPolicyRequiresAtLeastOneCandidate() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> GpuRuntimeBackendPolicy.builder().build()
        );

        assertEquals("GPU runtime backend policy requires at least one candidate backend", exception.getMessage());
    }

    @Test
    void backendPolicySelectsFirstMatchingBackendInPreferenceOrder() {
        GpuRuntimeBackend cudaBackend = new ReportingBackend(
                GpuRuntimeBackendReport.unavailable(GpuBackendTarget.CUDA, "CUDA", "CUDA runtime is unavailable")
        );
        GpuRuntimeBackend openClBackend = new ReportingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Policy GPU",
                        new GpuRuntimeApiVersion(3, 0),
                        "OpenCL 3.0 Policy GPU",
                        java.util.EnumSet.of(GpuRuntimeFeature.IMAGES),
                        16_384L,
                        128L,
                        null
                )
        );

        GpuRuntimeBackendSelection selection = GpuRuntimeBackendPolicy.builder()
                .minimumApiVersion(GpuBackendTarget.OPENCL, 3, 0)
                .requireFeature(GpuBackendTarget.OPENCL, GpuRuntimeFeature.IMAGES)
                .preferOwnedBackend(cudaBackend)
                .preferOwnedBackend(openClBackend)
                .build()
                .select();

        assertSame(openClBackend, selection.backend());
        assertEquals(GpuBackendTarget.OPENCL, selection.report().backendTarget());
        assertEquals("Policy GPU", selection.report().deviceLabel());
        assertEquals(GpuRuntimeBackendOwnership.OWNED, selection.ownership());
        assertTrue(selection.ownsBackend());
    }

    @Test
    void backendSelectionInstallHonorsBorrowedOwnership() {
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();

        final class CloseableBackend implements GpuRuntimeBackend, AutoCloseable {
            private int closeCalls;

            @Override
            public void invoke(GpuKernelInvocation invocation) {
            }

            @Override
            public void close() {
                closeCalls++;
            }
        }

        CloseableBackend backend = new CloseableBackend();
        GpuRuntimeBackendSelection selection = new GpuRuntimeBackendSelection(
                backend,
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Borrowed GPU",
                        new GpuRuntimeApiVersion(3, 0),
                        "OpenCL 3.0 Borrowed GPU",
                        java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                        1L,
                        1L,
                        null
                ),
                GpuRuntimeBackendOwnership.BORROWED
        );

        try (GpuRuntimeScope ignored = selection.install()) {
            assertSame(backend, GpuRuntime.backend());
            assertTrue(!ignored.ownsInstalledBackend());
        }

        assertSame(previousBackend, GpuRuntime.backend());
        assertEquals(0, backend.closeCalls);
    }

    @Test
    void backendPolicyUseInstallsSelectedBackendAndClosesItOnClose() {
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();

        final class CloseableReportingBackend implements GpuRuntimeBackend, AutoCloseable {
            private final GpuRuntimeBackendReport report;
            private int closeCalls;

            private CloseableReportingBackend(GpuRuntimeBackendReport report) {
                this.report = report;
            }

            @Override
            public GpuBackendTarget backendTarget() {
                return report.backendTarget();
            }

            @Override
            public GpuRuntimeBackendReport describeCapabilities() {
                return report;
            }

            @Override
            public void invoke(GpuKernelInvocation invocation) {
            }

            @Override
            public void close() {
                closeCalls++;
            }
        }

        CloseableReportingBackend unavailableCuda = new CloseableReportingBackend(
                GpuRuntimeBackendReport.unavailable(GpuBackendTarget.CUDA, "CUDA", "CUDA driver is unavailable")
        );
        CloseableReportingBackend openCl = new CloseableReportingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Policy GPU",
                        new GpuRuntimeApiVersion(3, 0),
                        "OpenCL 3.0 Policy GPU",
                        java.util.EnumSet.of(GpuRuntimeFeature.DOUBLE_PRECISION),
                        16_384L,
                        128L,
                        null
                )
        );

        GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
                .minimumApiVersion(GpuBackendTarget.OPENCL, 3, 0)
                .preferOwnedBackend(unavailableCuda)
                .preferOwnedBackend(openCl)
                .build();

        try (GpuRuntimeScope ignored = GpuRuntime.use(policy)) {
            assertSame(openCl, GpuRuntime.backend());
        }

        assertSame(previousBackend, GpuRuntime.backend());
        assertEquals(1, unavailableCuda.closeCalls);
        assertEquals(1, openCl.closeCalls);
    }

    @Test
    void backendPolicyTrySelectSupportsPrecheckAndSkipFlow() {
        GpuRuntimeBackend cudaBackend = new ReportingBackend(
                GpuRuntimeBackendReport.unavailable(GpuBackendTarget.CUDA, "CUDA", "CUDA runtime is unavailable")
        );
        GpuRuntimeBackend openClBackend = new ReportingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Policy GPU",
                        new GpuRuntimeApiVersion(3, 0),
                        "OpenCL 3.0 Policy GPU",
                        java.util.EnumSet.of(GpuRuntimeFeature.IMAGES),
                        16_384L,
                        128L,
                        null
                )
        );

        GpuRuntimeSelectionResult result = GpuRuntimeBackendPolicy.builder()
                .minimumApiVersion(GpuBackendTarget.OPENCL, 3, 0)
                .requireFeature(GpuBackendTarget.OPENCL, GpuRuntimeFeature.IMAGES)
                .preferOwnedBackend(cudaBackend)
                .preferOwnedBackend(openClBackend)
                .build()
                .trySelect();

        assertTrue(result.matched());
        assertSame(openClBackend, result.requireSelection().backend());
        assertEquals(GpuBackendTarget.OPENCL, result.requireSelection().report().backendTarget());
        assertEquals(2, result.candidateDecisions().size());
        assertTrue(!result.candidateDecisions().get(0).selected());
        assertTrue(result.candidateDecisions().get(1).selected());
        assertEquals("OpenCL selected", result.candidateDecisions().get(1).summary());
    }

    @Test
    void backendSelectionExplanationExposesSummaryMarkdownAndArtifactFields() {
        GpuRuntimeBackend cudaBackend = new ReportingBackend(
                GpuRuntimeBackendReport.unavailable(GpuBackendTarget.CUDA, "CUDA", "CUDA runtime is unavailable")
        );
        GpuRuntimeBackend openClBackend = new ReportingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Explanation GPU",
                        new GpuRuntimeApiVersion(3, 0),
                        "OpenCL 3.0 Explanation GPU",
                        java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                        16_384L,
                        128L,
                        null
                )
        );

        GpuRuntimeSelectionResult result = GpuRuntimeBackendPolicy.builder()
                .preferOwnedBackend(cudaBackend)
                .preferOwnedBackend(openClBackend)
                .build()
                .trySelect();
        GpuRuntimeBackendSelectionExplanation explanation = result.explanation();
        Map<String, String> fields = result.artifactFields("backendSelection");

        assertTrue(explanation.matched());
        assertEquals(GpuBackendTarget.OPENCL, explanation.selectedBackendTarget());
        assertEquals("OpenCL", explanation.selectedBackendName());
        assertEquals("Explanation GPU", explanation.selectedDeviceLabel());
        assertEquals(result.explanationSummary(), explanation.summary());
        assertTrue(explanation.toMarkdown().contains("Selected: OpenCL (`OPENCL`) on Explanation GPU"));
        assertEquals("backend-selected", fields.get("runtime.status"));
        assertEquals("true", fields.get("runtime.backend.selection.present"));
        assertEquals("true", fields.get("runtime.backend.selection.matched"));
        assertEquals("OPENCL", fields.get("runtime.backend.target"));
        assertEquals("OpenCL", fields.get("runtime.backend.name"));
        assertEquals("true", fields.get("backendSelection.matched"));
        assertEquals("OPENCL", fields.get("backendSelection.selected.backendTarget"));
        assertEquals("2", fields.get("backendSelection.candidate.count"));
        assertEquals("CUDA runtime is unavailable", fields.get("backendSelection.candidate.0.firstBlocker"));
    }

    @Test
    void backendDeviceSelectionExplanationLinksBackendAndDeviceEvidence() {
        GpuRuntimeBackend openClBackend = new ReportingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "NVIDIA RTX",
                        new GpuRuntimeApiVersion(3, 0),
                        "OpenCL 3.0 NVIDIA RTX",
                        java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                        16_384L,
                        128L,
                        null
                )
        );
        GpuRuntimeSelectionResult backendResult = GpuRuntimeBackendPolicy.builder()
                .preferBorrowedBackend(openClBackend)
                .build()
                .trySelect();
        GpuRuntimeDeviceProfile device = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-0",
                "NVIDIA RTX",
                "NVIDIA",
                "test-driver",
                "OpenCL 3.0 Test",
                "NVIDIA CUDA",
                "OpenCL 3.0 CUDA",
                GpuDeviceClassTarget.DGPU,
                48,
                8L * 1024L * 1024L * 1024L,
                64L * 1024L,
                1024L,
                1L,
                false,
                true,
                true,
                false
        );
        GpuRuntimeDeviceSelection deviceSelection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                GpuRuntimeDevicePolicyContext.forBackendDiscovery(
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                                .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED),
                        List.of(device)
                )
        );
        GpuRuntimeDeviceDiscoveryResult discovery = GpuRuntimeDeviceDiscoveryResult.available(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                List.of(device),
                deviceSelection
        );
        GpuRuntimeDeviceDiscoveryCatalog catalog = GpuRuntimeDeviceDiscoveryCatalog.of(List.of(
                discovery,
                GpuRuntimeDeviceDiscovery.plannedUnavailable(GpuBackendTarget.CUDA)
        ));

        GpuRuntimeBackendDeviceSelectionExplanation explanation = backendResult.explainWithDeviceDiscovery(catalog);
        Map<String, String> fields = explanation.artifactFields("runtimeSelection");

        assertEquals("backend-and-device-selected", explanation.status());
        assertTrue(explanation.summary().contains("selected OpenCL on NVIDIA RTX"));
        assertTrue(explanation.toMarkdown().contains("Runtime selection: backend-and-device-selected"));
        assertTrue(explanation.toMarkdown().contains("Device discoveries:"));
        assertTrue(explanation.toMarkdown().contains("Backend device discovery: CUDA (`CUDA`)"));
        assertEquals("backend-and-device-selected", fields.get("runtime.status"));
        assertEquals("backend-and-device-selected", fields.get("runtime.selection.status"));
        assertEquals("OPENCL", fields.get("runtime.backend.target"));
        assertEquals("OpenCL", fields.get("runtime.backend.name"));
        assertEquals("NVIDIA RTX", fields.get("runtime.device.label"));
        assertEquals("NVIDIA", fields.get("runtime.device.vendor"));
        assertEquals("DGPU", fields.get("runtime.device.class"));
        assertEquals("OPENCL:opencl-0", fields.get("runtime.device.discovery.selectedDeviceKey"));
        assertEquals("backend-and-device-selected", fields.get("runtimeSelection.status"));
        assertEquals("OPENCL", fields.get("runtimeSelection.backend.selected.backendTarget"));
        assertEquals("OPENCL:opencl-0", fields.get("runtimeSelection.device.selected.deviceKey"));
        assertEquals("NVIDIA CUDA", fields.get("runtimeSelection.device.selected.platformName"));
        assertEquals("true", fields.get("runtimeSelection.deviceDiscovery.present"));
        assertEquals("2", fields.get("runtimeSelection.deviceDiscoveryCatalog.backend.count"));
        assertEquals("CUDA", fields.get("runtimeSelection.deviceDiscoveryCatalog.backend.1.backendTarget"));
    }

    @Test
    void backendDeviceSelectionResultExposesConcreteBackendAndDevice() {
        GpuRuntimeBackend openClBackend = new ReportingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Selected Backend GPU",
                        new GpuRuntimeApiVersion(3, 0),
                        "OpenCL 3.0 Selected Backend GPU",
                        java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                        16_384L,
                        128L,
                        null
                )
        );
        GpuRuntimeSelectionResult backendResult = GpuRuntimeBackendPolicy.builder()
                .preferBorrowedBackend(openClBackend)
                .build()
                .trySelect();
        GpuRuntimeDeviceProfile device = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-selected-0",
                "Selected Device GPU",
                "NVIDIA",
                "selected-driver",
                "OpenCL 3.0 Selected Device GPU",
                "NVIDIA CUDA",
                "OpenCL 3.0 CUDA",
                GpuDeviceClassTarget.DGPU,
                48,
                8L * 1024L * 1024L * 1024L,
                64L * 1024L,
                1024L,
                1L,
                false,
                true,
                true,
                false
        );
        GpuRuntimeDeviceSelection deviceSelection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                GpuRuntimeDevicePolicyContext.forBackendDiscovery(
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                                .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED),
                        List.of(device)
                )
        );
        GpuRuntimeDeviceDiscoveryCatalog catalog = GpuRuntimeDeviceDiscoveryCatalog.of(List.of(
                GpuRuntimeDeviceDiscoveryResult.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        List.of(device),
                        deviceSelection
                ),
                GpuRuntimeDeviceDiscovery.plannedUnavailable(GpuBackendTarget.CUDA)
        ));

        GpuRuntimeBackendDeviceSelection selection = backendResult.withDeviceDiscovery(catalog);
        Map<String, String> fields = selection.artifactFields("runtimeSelection");

        assertTrue(selection.backendMatched());
        assertTrue(selection.deviceMatched());
        assertTrue(selection.matched());
        assertSame(openClBackend, selection.selectedBackend().orElseThrow().backend());
        assertEquals("Selected Device GPU", selection.selectedDevice().orElseThrow().deviceLabel());
        assertEquals("backend-and-device-selected", selection.status());
        assertTrue(selection.summary().contains("selected OpenCL on Selected Device GPU"));
        assertTrue(selection.toMarkdown().contains("Runtime selection: backend-and-device-selected"));
        assertEquals("backend-and-device-selected", fields.get("runtime.status"));
        assertEquals("backend-and-device-selected", fields.get("runtime.selection.status"));
        assertEquals("Selected Device GPU", fields.get("runtime.device.label"));
        assertEquals("OPENCL:opencl-selected-0", fields.get("runtime.device.discovery.selectedDeviceKey"));
        assertEquals("backend-and-device-selected", fields.get("runtimeSelection.status"));
        assertEquals("OPENCL:opencl-selected-0", fields.get("runtimeSelection.device.selected.deviceKey"));
    }

    @Test
    void backendDeviceSelectionPublishesLifecycleEvents() {
        GpuRuntimeBackend openClBackend = new ReportingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Lifecycle Backend GPU",
                        new GpuRuntimeApiVersion(3, 0),
                        "OpenCL 3.0 Lifecycle Backend GPU",
                        java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                        16_384L,
                        128L,
                        null
                )
        );
        GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
                .preferBorrowedBackend(openClBackend)
                .build();
        GpuRuntimeDeviceProfile device = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-lifecycle-0",
                "Lifecycle Device GPU",
                "NVIDIA",
                "lifecycle-driver",
                "OpenCL 3.0 Lifecycle Device GPU",
                "NVIDIA CUDA",
                "OpenCL 3.0 CUDA",
                GpuDeviceClassTarget.DGPU,
                48,
                8L * 1024L * 1024L * 1024L,
                64L * 1024L,
                1024L,
                1L,
                false,
                true,
                true,
                false
        );
        GpuRuntimeDeviceSelection deviceSelection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                GpuRuntimeDevicePolicyContext.forBackendDiscovery(
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                                .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED),
                        List.of(device)
                )
        );
        GpuRuntimeDeviceDiscoveryCatalog catalog = GpuRuntimeDeviceDiscoveryCatalog.of(List.of(
                GpuRuntimeDeviceDiscoveryResult.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        List.of(device),
                        deviceSelection
                )
        ));
        ArrayList<GpuRuntimeLifecycleEvent> events = new ArrayList<>();
        GpuRuntimeLifecycleEventBus eventBus = GpuRuntimeLifecycleEventBus.of(List.of(
                new GpuRuntimeLifecycleEventListener() {
                    @Override
                    public void onRuntimeLifecycleEvent(GpuRuntimeLifecycleEvent event) {
                        events.add(event);
                    }

                    @Override
                    public String extensionId() {
                        return "test.backend-device-selection.lifecycle";
                    }
                }
        ));

        GpuRuntimeBackendDeviceSelection selection = GpuRuntime.trySelectWithDeviceDiscovery(
                policy,
                catalog,
                eventBus
        );

        assertTrue(selection.matched());
        assertEquals(List.of(
                GpuRuntimeLifecycleEventKind.BACKEND_SELECTION_STARTED,
                GpuRuntimeLifecycleEventKind.BACKEND_SELECTION_COMPLETED,
                GpuRuntimeLifecycleEventKind.DEVICE_DISCOVERY_COMPLETED
        ), events.stream().map(GpuRuntimeLifecycleEvent::kind).toList());
        assertEquals("backend-device-selection", events.get(0).fields().get("pipeline"));
        assertEquals("started", events.get(0).fields().get("runtime.status"));
        assertEquals("UNKNOWN", events.get(0).fields().get("runtime.backend.target"));
        assertEquals("true", events.get(1).fields().get("backendSelection.matched"));
        assertEquals("backend-selected", events.get(1).fields().get("runtime.status"));
        assertEquals("OPENCL", events.get(1).fields().get("runtime.backend.target"));
        assertEquals("OpenCL", events.get(1).fields().get("runtime.backend.name"));
        assertEquals("true", events.get(2).fields().get("deviceDiscovery.precomputed"));
        assertEquals("backend-and-device-selected", events.get(2).fields().get("runtimeSelection.status"));
        assertEquals("backend-and-device-selected", events.get(2).fields().get("runtime.status"));
        assertEquals("OPENCL", events.get(2).fields().get("runtime.backend.target"));
        assertEquals("OpenCL", events.get(2).fields().get("runtime.backend.name"));
        assertEquals("Lifecycle Device GPU", events.get(2).fields().get("runtime.device.label"));
        assertEquals("NVIDIA", events.get(2).fields().get("runtime.device.vendor"));
        assertEquals("DGPU", events.get(2).fields().get("runtime.device.class"));
        assertEquals("OPENCL:opencl-lifecycle-0", events.get(2).fields().get("runtime.device.discovery.selectedDeviceKey"));
    }

    @Test
    void backendPolicyCanForceOrExcludeBackendTargets() {
        GpuRuntimeBackend cudaBackend = new ReportingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.CUDA,
                        "CUDA",
                        "CUDA GPU",
                        new GpuRuntimeApiVersion(12, 0),
                        "CUDA 12.0",
                        java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                        65_536L,
                        256L,
                        null
                )
        );
        GpuRuntimeBackend openClBackend = new ReportingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "OpenCL GPU",
                        new GpuRuntimeApiVersion(3, 0),
                        "OpenCL 3.0",
                        java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                        16_384L,
                        128L,
                        null
                )
        );

        GpuRuntimeSelectionResult forced = GpuRuntimeBackendPolicy.builder()
                .forceBackendTarget(GpuBackendTarget.OPENCL)
                .preferOwnedBackend(cudaBackend)
                .preferOwnedBackend(openClBackend)
                .build()
                .trySelect();
        GpuRuntimeSelectionResult excluded = GpuRuntimeBackendPolicy.builder()
                .excludeBackendTarget(GpuBackendTarget.CUDA)
                .preferOwnedBackend(cudaBackend)
                .preferOwnedBackend(openClBackend)
                .build()
                .trySelect();

        assertTrue(forced.matched());
        assertSame(openClBackend, forced.requireSelection().backend());
        assertEquals(
                "requires backend target OPENCL but found CUDA",
                forced.candidateDecisions().get(0).firstBlocker()
        );
        assertTrue(forced.explanationSummary().contains("CUDA: requires backend target OPENCL but found CUDA"));
        assertTrue(excluded.matched());
        assertSame(openClBackend, excluded.requireSelection().backend());
        assertEquals("backend target CUDA is excluded", excluded.candidateDecisions().get(0).firstBlocker());
    }

    @Test
    void backendCatalogStandardEntriesAreLazyAndInspectable() {
        List<GpuRuntimeBackendCatalogEntry> entries = GpuRuntimeBackendCatalog.standard();

        assertEquals(1, entries.size());
        GpuRuntimeBackendCatalogEntry entry = entries.get(0);
        assertEquals(GpuBackendTarget.OPENCL, entry.backendTarget());
        assertEquals("OpenCL (shared cache)", entry.backendName());
        assertEquals(GpuRuntimeBackendOwnership.OWNED, entry.ownership());
        assertTrue(entry.productionAdapter());
        assertEquals("production runtime adapter", entry.diagnostic());
        assertTrue(entry.executionSupport().isPresent());
        assertEquals("opencl-c", entry.executionSupport().orElseThrow().moduleFormatKeys());
        assertTrue(entry.executionSupport().orElseThrow().declaresCapability(GpuRuntimeCapability.LOCAL_MEMORY));
    }

    @Test
    void backendCatalogPlannedUnsupportedEntriesProduceExplicitDiagnostics() {
        GpuRuntimeBackendCatalogEntry cudaEntry = GpuRuntimeBackendCatalog.plannedUnsupported(GpuBackendTarget.CUDA);

        GpuRuntimeSelectionResult result = GpuRuntimeBackendPolicy.builder()
                .preferCatalogEntry(cudaEntry)
                .build()
                .trySelect();

        assertTrue(!result.matched());
        assertEquals(1, result.candidateDecisions().size());
        assertEquals(GpuBackendTarget.CUDA, result.candidateDecisions().get(0).backendTarget());
        assertTrue(result.candidateDecisions().get(0).metadata().executionSupportPresent());
        assertEquals("cubin,cuda-c,fatbin,ptx", result.candidateDecisions().get(0).metadata().moduleFormatKeys());
        assertTrue(result.candidateDecisions().get(0).artifactFields("candidate")
                .get("candidate.executionSupport.capabilities")
                .contains("compute-capability"));
        assertTrue(result.failureSummary().contains("Runtime backend adapter is not implemented for CUDA"));
        assertTrue(result.explanation().toMarkdown().contains("CUDA: Runtime backend adapter is not implemented for CUDA"));
        assertTrue(result.explanation().toMarkdown().contains("moduleFormats: cubin,cuda-c,fatbin,ptx"));
        assertTrue(result.explanation().toMarkdown().contains("executionPipeline: available=true"));
    }

    @Test
    void backendAdaptersExposeCatalogDiscoveryAndLowererContract() {
        List<GpuRuntimeBackendProvider> providers = GpuRuntimeBackendProviders.standardWithPlannedBackends();
        List<GpuRuntimeBackendAdapter> adapters = GpuRuntimeBackendAdapters.standardWithPlannedBackends();
        GpuRuntimeBackendProviderCatalog providerCatalog = GpuRuntimeBackendProviderCatalog.of(providers);
        GpuRuntimeBackendProvider openClProvider = providers.get(0);
        GpuRuntimeBackendProvider cudaProvider = providers.get(1);
        GpuRuntimeBackendAdapter openClAdapter = GpuRuntimeBackendAdapters.requireTarget(GpuBackendTarget.OPENCL);
        GpuRuntimeBackendAdapter cudaAdapter = GpuRuntimeBackendAdapters.requireTarget(GpuBackendTarget.CUDA);

        assertEquals(4, providers.size());
        assertEquals(4, providerCatalog.providers().size());
        assertTrue(providerCatalog.forTarget(GpuBackendTarget.OPENCL).orElseThrow().executionAvailability()
                .sharedPipelineRunnerAvailable());
        assertEquals(cudaProvider, providerCatalog.forProviderId("backend-provider:cuda").orElseThrow());
        assertEquals(2, providerCatalog.sharedPipelineRunnerAvailableCount());
        assertEquals(2, providerCatalog.executionUnavailableCount());
        assertTrue(providerCatalog.anySharedPipelineRunnerAvailable());
        assertTrue(providerCatalog.toMarkdown().contains("OPENCL: status=execution-pipeline-available"));
        assertTrue(providerCatalog.toMarkdown().contains("CUDA: status=execution-pipeline-available"));
        Map<String, String> providerCatalogFields = providerCatalog.artifactFields("providerCatalog");
        assertEquals("true", providerCatalogFields.get("runtime.backend.providerCatalog.present"));
        assertEquals("4", providerCatalogFields.get("providerCatalog.provider.count"));
        assertEquals("2", providerCatalogFields.get("providerCatalog.sharedRunner.available.count"));
        assertEquals("2", providerCatalogFields.get("providerCatalog.executionUnavailable.count"));
        assertEquals("backend-provider:opencl", providerCatalogFields.get("providerCatalog.provider.0.providerId"));
        assertEquals(
                "execution-pipeline-available",
                providerCatalogFields.get("providerCatalog.provider.0.executionAvailability.status")
        );
        assertEquals("backend-provider:opencl", openClProvider.providerId());
        assertEquals("backend-provider:cuda", cudaProvider.providerId());
        assertEquals(GpuBackendTarget.OPENCL, openClProvider.createAdapter().backendTarget());
        assertTrue(openClProvider.executionSupport().productionExecution());
        assertTrue(openClProvider.executionSupport().executionPipelineAvailable());
        assertEquals("opencl-c", openClProvider.executionSupport().moduleFormatKeys());
        assertTrue(openClProvider.executionSupport().declaresModuleFormat(GpuBackendModuleFormat.OPENCL_C));
        assertTrue(openClProvider.executionSupport().declaresCapability(GpuRuntimeCapability.LOCAL_MEMORY));
        assertTrue(openClProvider.executionSupport().declaresCapability(GpuRuntimeCapability.MAX_WORK_GROUP_SIZE));
        assertTrue(openClProvider.executionPipelineFactory().isPresent());
        assertEquals("execution-pipeline-available", openClProvider.executionAvailability().status());
        assertTrue(openClProvider.executionAvailability().sharedPipelineRunnerAvailable());
        assertFalse(cudaProvider.executionSupport().productionExecution());
        assertTrue(cudaProvider.executionSupport().executionPipelineAvailable());
        assertEquals("cubin,cuda-c,fatbin,ptx", cudaProvider.executionSupport().moduleFormatKeys());
        assertTrue(cudaProvider.executionSupport().declaresModuleFormat(GpuBackendModuleFormat.CUDA_C));
        assertTrue(cudaProvider.executionSupport().declaresModuleFormat(GpuBackendModuleFormat.PTX));
        assertTrue(cudaProvider.executionSupport().declaresModuleFormat(GpuBackendModuleFormat.CUBIN));
        assertTrue(cudaProvider.executionSupport().declaresModuleFormat(GpuBackendModuleFormat.FATBIN));
        assertTrue(cudaProvider.executionSupport().declaresCapability(GpuRuntimeCapability.COMPUTE_CAPABILITY));
        assertTrue(cudaProvider.executionSupport().declaresCapability(GpuRuntimeCapability.GLOBAL_MEMORY));
        assertTrue(cudaProvider.executionPipelineFactory().isPresent());
        assertEquals("execution-pipeline-available", cudaProvider.executionAvailability().status());
        assertTrue(cudaProvider.executionAvailability().blockers().isEmpty());
        assertTrue(cudaProvider.executionAvailability().toMarkdown().contains("CUDA execution is available through provider"));
        Map<String, String> openClProviderFields = openClProvider.artifactFields("provider");
        assertEquals("true", openClProviderFields.get("runtime.backend.provider.present"));
        assertEquals("backend-provider:opencl", openClProviderFields.get("provider.providerId"));
        assertEquals("true", openClProviderFields.get("runtime.backend.executionPipeline.available"));
        assertEquals("true", openClProviderFields.get("runtime.backend.executionPipeline.factory.present"));
        assertEquals("opencl-c", openClProviderFields.get("runtime.backend.executionSupport.moduleFormats"));
        assertTrue(openClProviderFields.get("runtime.backend.executionSupport.capabilities").contains("local-memory"));
        assertEquals(
                "execution-pipeline-available",
                openClProviderFields.get("runtime.backend.executionAvailability.status")
        );
        assertEquals("compile", openClProviderFields.get("provider.executionSupport.supportedStages").split(",")[3]);
        Map<String, String> cudaProviderFields = cudaProvider.artifactFields("provider");
        assertEquals("execution-pipeline-available", cudaProviderFields.get("runtime.backend.executionAvailability.status"));
        assertEquals("true", cudaProviderFields.get("runtime.backend.executionAvailability.sharedRunner.available"));
        assertEquals("0", cudaProviderFields.get("runtime.backend.executionAvailability.blocker.count"));
        assertEquals("cubin,cuda-c,fatbin,ptx", cudaProviderFields.get("runtime.backend.executionSupport.moduleFormats"));
        assertTrue(cudaProviderFields.get("runtime.backend.executionSupport.capabilities").contains("compute-capability"));
        GpuBackendExecutionPipelineFactory<?, ?, ?> cudaPipelineFactory = cudaProvider
                .executionPipelineFactory()
                .orElseThrow();
        assertEquals("backend-execution-pipeline:cuda-preview", cudaPipelineFactory.factoryId());
        assertFalse(cudaProvider.executionSupport().productionExecution());
        GpuBackendExecutionPipelineFactory<?, ?, ?> openClPipelineFactory = openClProvider
                .executionPipelineFactory()
                .orElseThrow();
        assertEquals("backend-execution-pipeline:opencl", openClPipelineFactory.factoryId());
        try (net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend backend =
                     new net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend()) {
            assertTrue(openClPipelineFactory.supportsBackend(backend));
            assertEquals(GpuBackendTarget.OPENCL, openClPipelineFactory.createPipeline(backend).backendTarget());
        }
        assertEquals(4, adapters.size());
        assertEquals(GpuBackendTarget.OPENCL, openClAdapter.backendTarget());
        assertEquals("OpenCL", openClAdapter.backendName());
        assertTrue(openClAdapter.catalogEntry().productionAdapter());
        assertEquals(GpuBackendTarget.OPENCL, openClAdapter.lowerer().backendTarget());
        assertEquals(GpuBackendTarget.CUDA, cudaAdapter.backendTarget());
        assertFalse(cudaAdapter.catalogEntry().productionAdapter());
        assertEquals(GpuBackendTarget.CUDA, cudaAdapter.lowerer().backendTarget());

        List<GpuRuntimeBackendCatalogEntry> entries = GpuRuntimeBackendAdapters.catalogEntries(adapters);
        GpuRuntimeDeviceDiscoveryCatalog discoveryCatalog = GpuRuntimeBackendAdapters.discoverDevices(
                List.of(cudaAdapter),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
        );
        Map<String, String> fields = cudaAdapter.artifactFields("adapter");

        assertEquals(4, entries.size());
        assertEquals(GpuBackendTarget.CUDA, entries.get(1).backendTarget());
        assertTrue(entries.get(0).executionSupport().orElseThrow().executionPipelineAvailable());
        assertEquals("opencl-c", entries.get(0).executionSupport().orElseThrow().moduleFormatKeys());
        assertEquals("cubin,cuda-c,fatbin,ptx", entries.get(1).executionSupport().orElseThrow().moduleFormatKeys());
        assertEquals(GpuBackendTarget.CUDA, discoveryCatalog.forBackend(GpuBackendTarget.CUDA).orElseThrow().backendTarget());
        assertEquals("CUDA", discoveryCatalog.forBackend(GpuBackendTarget.CUDA).orElseThrow().backendName());
        assertEquals("CUDA", fields.get("adapter.backendTarget"));
        assertEquals("false", fields.get("adapter.productionAdapter"));
        assertEquals("backend-lowerer:cuda", fields.get("adapter.lowerer.id"));
        assertEquals("non-production-adapter", fields.get("runtime.status"));
        assertEquals("true", fields.get("runtime.backend.adapter.present"));
        assertEquals("CUDA", fields.get("runtime.backend.target"));
        assertEquals("CUDA", fields.get("runtime.backend.name"));
        assertEquals("false", fields.get("runtime.backend.adapter.productionAdapter"));
        assertEquals("backend-lowerer:cuda", fields.get("runtime.backend.lowerer.id"));
        assertEquals("CUDA", fields.get("runtime.backend.lowerer.target"));

        GpuBackendLoweringResult cudaLoweringResult = GpuBackendLoweringResult.succeeded(
                new GpuBackendModuleArtifact(
                        GpuBackendTarget.CUDA,
                        "source",
                        "cuda-c",
                        "extern \"C\" __global__ void kernel(const float* input, float* output) { }",
                        "javatogpu/sample/Demo/kernel.cu",
                        "cuda:source:cuda-c:v1",
                        "test-cuda-lowerer"
                ),
                GpuBackendSourceSelectionPlan.descriptorSource(
                        GpuBackendTarget.CUDA,
                        "cuda-c",
                        "CUDA descriptor source preview"
                ),
                List.of("CUDA lowering preview only")
        );
        GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> unsupportedExecution =
                cudaProvider.unsupportedExecutionResult(cudaLoweringResult);

        assertTrue(!unsupportedExecution.succeeded());
        assertEquals(GpuBackendStageStatus.SUCCEEDED, unsupportedExecution.compilationResult().stageResult().status());
        assertEquals(GpuBackendStageStatus.UNSUPPORTED, unsupportedExecution.preparationResult().stageResult().status());
        assertEquals(GpuBackendStageStatus.SKIPPED, unsupportedExecution.invocationResult().stageResult().status());
        assertEquals(
                "cuda-native-argument-binding-missing",
                unsupportedExecution.preparationResult().stageResult().blockers().get(0)
        );
    }

    @Test
    void backendModuleFormatVocabularyNormalizesCommonBackendFormats() {
        GpuBackendModuleArtifact openCl = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel() {}",
                "generated/kernel.cl",
                "test-opencl-lowerer"
        );
        GpuBackendModuleArtifact cuda = GpuBackendModuleArtifact.cudaSource(
                "extern \"C\" __global__ void kernel() {}",
                "generated/kernel.cu",
                "test-cuda-lowerer"
        );
        GpuBackendModuleArtifact spirV = new GpuBackendModuleArtifact(
                GpuBackendTarget.VULKAN,
                "binary",
                "spirv",
                "",
                "generated/kernel.spv",
                "vulkan:binary:spir-v:v1",
                "test-vulkan-lowerer",
                "derived-spir-v",
                false,
                true,
                "",
                "",
                "binary-load"
        );
        GpuBackendModuleArtifact cubin = GpuBackendModuleArtifact.cubin(
                "generated/kernel.cubin",
                "test-cuda-cubin-lowerer",
                true
        );
        GpuBackendModuleArtifact fatbin = GpuBackendModuleArtifact.fatbin(
                "generated/kernel.fatbin",
                "test-cuda-fatbin-lowerer",
                true
        );

        assertEquals("opencl-c", openCl.format());
        assertEquals(GpuBackendModuleFormat.OPENCL_C, openCl.moduleFormat());
        assertTrue(openCl.sourceLikeFormat());
        assertFalse(openCl.binaryLikeFormat());
        assertTrue(openCl.formatMatchesBackendTarget());
        assertEquals("cuda-c", cuda.format());
        assertEquals(GpuBackendModuleFormat.CUDA_C, cuda.moduleFormat());
        assertTrue(cuda.sourceLikeFormat());
        assertTrue(cuda.formatMatchesBackendTarget());
        assertEquals("spir-v", spirV.format());
        assertEquals(GpuBackendModuleFormat.SPIR_V, spirV.moduleFormat());
        assertFalse(spirV.sourceLikeFormat());
        assertTrue(spirV.binaryLikeFormat());
        assertTrue(spirV.formatMatchesBackendTarget());
        assertEquals("cubin", cubin.format());
        assertEquals(GpuBackendModuleFormat.CUBIN, cubin.moduleFormat());
        assertFalse(cubin.sourceLikeFormat());
        assertTrue(cubin.binaryLikeFormat());
        assertTrue(cubin.formatMatchesBackendTarget());
        assertEquals("fatbin", fatbin.format());
        assertEquals(GpuBackendModuleFormat.FATBIN, fatbin.moduleFormat());
        assertTrue(fatbin.binaryLikeFormat());
        assertEquals(GpuBackendModuleFormat.FATBIN, GpuBackendModuleFormat.fromKey("nvidia-fatbin"));
    }

    @Test
    void runtimeDeviceProfilesExposeBackendNeutralCapabilityFacts() {
        GpuRuntimeDeviceProfile profile = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-0",
                "Capability GPU",
                "NVIDIA Corporation",
                "595.97",
                "OpenCL 3.0 CUDA 13.2.73",
                "OpenCL C 3.0 CUDA",
                "NVIDIA CUDA",
                "OpenCL 3.0 CUDA 13.2.73",
                GpuDeviceClassTarget.DGPU,
                48L,
                12_000_000_000L,
                65_536L,
                1_024L,
                1L,
                false,
                true,
                true,
                true,
                true,
                true
        );
        Map<String, String> facts = profile.capabilityFacts();

        assertTrue(profile.supportsCapability(GpuRuntimeCapability.FP64));
        assertTrue(profile.supportsCapability(GpuRuntimeCapability.IMAGES));
        assertTrue(profile.supportsCapability(GpuRuntimeCapability.IMAGE_3D_WRITES));
        assertTrue(profile.supportsCapability(GpuRuntimeCapability.ATOMICS));
        assertTrue(profile.supportsCapability(GpuRuntimeCapability.SUBGROUPS));
        assertTrue(profile.supportsCapability(GpuRuntimeCapability.LOCAL_MEMORY));
        assertTrue(profile.supportsCapability(GpuRuntimeCapability.MAX_WORK_GROUP_SIZE));
        assertTrue(profile.supportsCapability(GpuRuntimeCapability.COMPILER_VERSION));
        assertEquals("true", facts.get("capability.fp64"));
        assertEquals("true", facts.get("capability.images"));
        assertEquals("true", facts.get("capability.image-3d-writes"));
        assertEquals("true", facts.get("capability.atomics"));
        assertEquals("true", facts.get("capability.compiler-version"));
        assertEquals("OpenCL C 3.0 CUDA", facts.get("compilerVersion"));
        assertEquals("true", facts.get("supportsImage3dWrites"));
        assertEquals("true", facts.get("supportsAtomics"));
        assertEquals("65536", facts.get("localMemoryBytes"));
        assertEquals("1024", facts.get("maxWorkGroupSize"));
    }

    @Test
    void backendReportsCanBeCheckedWithRuntimeCapabilityVocabulary() {
        GpuRuntimeBackendReport report = GpuRuntimeBackendReport.available(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "Capability GPU",
                new GpuRuntimeApiVersion(3, 0),
                "OpenCL 3.0 Capability GPU",
                Set.of(GpuRuntimeFeature.DOUBLE_PRECISION, GpuRuntimeFeature.IMAGES),
                65_536L,
                1_024L,
                null
        );

        assertTrue(report.supports(GpuRuntimeCapability.FP64));
        assertTrue(report.supports(GpuRuntimeCapability.IMAGES));
        assertTrue(report.supports(GpuRuntimeCapability.LOCAL_MEMORY));
        assertTrue(GpuRuntimeRequirements.isSatisfied(
                report,
                List.of(
                        GpuRuntimeRequirements.requireCapability(GpuRuntimeCapability.FP64),
                        GpuRuntimeRequirements.requireCapability(GpuRuntimeCapability.MAX_WORK_GROUP_SIZE)
                )
        ));
        assertEquals(List.of("missing capability subgroups"), GpuRuntimeRequirements.failureReasons(
                report,
                List.of(GpuRuntimeRequirements.requireCapability(GpuRuntimeCapability.SUBGROUPS))
        ));
    }

    @Test
    void backendProviderRegistryRejectsDuplicateProviderIds() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> GpuRuntimeBackendProviders.adapters(List.of(
                        new CudaRuntimeBackendProvider(),
                        new CudaRuntimeBackendProvider()
                ))
        );

        assertTrue(exception.getMessage().contains("Duplicate GPU runtime backend provider id 'backend-provider:cuda'"));
    }

    @Test
    void backendProviderRegistryRejectsMismatchedExecutionSupportTarget() {
        GpuRuntimeBackendProvider provider = new GpuRuntimeBackendProvider() {
            @Override
            public GpuBackendTarget backendTarget() {
                return GpuBackendTarget.CUDA;
            }

            @Override
            public String providerId() {
                return "test.mismatched-execution-support";
            }

            @Override
            public String providerVersion() {
                return "1";
            }

            @Override
            public int providerOrder() {
                return 1_000;
            }

            @Override
            public GpuRuntimeBackendAdapter createAdapter() {
                return new CudaRuntimeBackendAdapter();
            }

            @Override
            public GpuRuntimeBackendExecutionSupport executionSupport() {
                return GpuRuntimeBackendExecutionSupport.productionPipeline(
                        GpuBackendTarget.OPENCL,
                        providerId(),
                        "intentionally mismatched for registry validation"
                );
            }
        };

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> GpuRuntimeBackendProviders.adapters(List.of(provider))
        );

        assertTrue(exception.getMessage().contains("execution support reports OPENCL"));
    }

    @Test
    void backendProviderRegistryLoadsExternalProvidersFromServiceLoader(@TempDir Path tempDir) throws Exception {
        Path serviceFile = tempDir.resolve("META-INF/services/" + GpuRuntimeBackendProvider.class.getName());
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(
                serviceFile,
                TestServiceLoadedRuntimeBackendProvider.class.getName() + System.lineSeparator(),
                StandardCharsets.UTF_8
        );

        try (URLClassLoader classLoader = new URLClassLoader(
                new java.net.URL[]{tempDir.toUri().toURL()},
                GpuRuntimeTest.class.getClassLoader()
        )) {
            GpuRuntimeBackendProviderCatalog providerCatalog = GpuRuntimeBackendProviderCatalog
                    .standardWithPlannedBackends(classLoader);
            List<GpuRuntimeBackendProvider> providers = providerCatalog.providers();
            GpuRuntimeBackendProvider serviceLoadedProvider = providers.stream()
                    .filter(provider -> provider.providerId().equals("test.backend-provider:service-loaded"))
                    .findFirst()
                    .orElseThrow();

            assertEquals(GpuBackendTarget.UNKNOWN, serviceLoadedProvider.backendTarget());
            assertEquals("test-1", serviceLoadedProvider.providerVersion());
            assertEquals("execution-unavailable", serviceLoadedProvider.executionAvailability().status());
            assertTrue(serviceLoadedProvider.executionAvailability().diagnostics()
                    .contains("test provider loaded from a temporary ServiceLoader descriptor"));
            assertSame(serviceLoadedProvider, providers.get(providers.size() - 1));
            assertSame(serviceLoadedProvider, providerCatalog.forTarget(GpuBackendTarget.UNKNOWN).orElseThrow());
            assertTrue(providerCatalog.toMarkdown().contains("UNKNOWN: status=execution-unavailable"));

            List<GpuRuntimeBackendAdapter> adapters = GpuRuntimeBackendProviders.adapters(providers);

            assertTrue(adapters.stream().anyMatch(adapter -> adapter.backendTarget() == GpuBackendTarget.UNKNOWN));
        }
    }

    @Test
    void backendPolicyCanSelectFromCustomCatalogEntries() {
        CloseCountingBackend unavailableCuda = new CloseCountingBackend(
                GpuRuntimeBackendReport.unavailable(GpuBackendTarget.CUDA, "CUDA", "CUDA fixture unavailable")
        );
        CloseCountingBackend openCl = new CloseCountingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Catalog GPU",
                        new GpuRuntimeApiVersion(3, 0),
                        "OpenCL 3.0 Catalog GPU",
                        java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                        16_384L,
                        128L,
                        null
                )
        );
        List<GpuRuntimeBackendCatalogEntry> catalog = List.of(
                GpuRuntimeBackendCatalogEntry.owned(
                        GpuBackendTarget.CUDA,
                        "CUDA fixture",
                        () -> unavailableCuda,
                        false,
                        "test fixture"
                ),
                GpuRuntimeBackendCatalogEntry.owned(
                        GpuBackendTarget.OPENCL,
                        "OpenCL fixture",
                        () -> openCl,
                        true,
                        "test fixture"
                )
        );

        GpuRuntimeSelectionResult result = GpuRuntimeBackendPolicy.builder()
                .preferCatalog(catalog)
                .build()
                .trySelect();

        assertTrue(result.matched());
        assertSame(openCl, result.requireSelection().backend());
        assertEquals(1, unavailableCuda.closeCalls);
        assertEquals(0, openCl.closeCalls);
        assertEquals(2, result.candidateDecisions().size());
        assertEquals("CUDA fixture unavailable", result.candidateDecisions().get(0).firstBlocker());
        assertTrue(result.candidateDecisions().get(1).selected());
    }

    @Test
    void backendPolicyCanUseCatalogMetadataRequirements() {
        CloseCountingBackend openClForPtxCheck = new CloseCountingBackend(availableBackendReport(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                new GpuRuntimeApiVersion(3, 0)
        ));
        CloseCountingBackend cudaForPtxCheck = new CloseCountingBackend(availableBackendReport(
                GpuBackendTarget.CUDA,
                "CUDA",
                new GpuRuntimeApiVersion(12, 0)
        ));
        GpuRuntimeBackendCatalogEntry openClEntry = metadataBackedCatalogEntry(
                GpuBackendTarget.OPENCL,
                "OpenCL metadata fixture",
                openClForPtxCheck,
                GpuRuntimeBackendExecutionSupport.productionPipeline(
                        GpuBackendTarget.OPENCL,
                        "test.opencl.execution-support",
                        Set.of(GpuBackendModuleFormat.OPENCL_C),
                        Set.of(GpuRuntimeCapability.LOCAL_MEMORY),
                        "test OpenCL production fixture"
                )
        );
        GpuRuntimeBackendCatalogEntry cudaDiscoveryEntry = metadataBackedCatalogEntry(
                GpuBackendTarget.CUDA,
                "CUDA discovery fixture",
                cudaForPtxCheck,
                GpuRuntimeBackendExecutionSupport.discoveryOnly(
                        GpuBackendTarget.CUDA,
                        "test.cuda.execution-support",
                        Set.of(GpuBackendModuleFormat.CUDA_C, GpuBackendModuleFormat.PTX),
                        Set.of(GpuRuntimeCapability.COMPUTE_CAPABILITY),
                        "test CUDA discovery fixture"
                )
        );

        GpuRuntimeSelectionResult ptxSelection = GpuRuntimeBackendPolicy.builder()
                .requireDeclaredModuleFormat(GpuBackendModuleFormat.PTX)
                .preferCatalog(List.of(openClEntry, cudaDiscoveryEntry))
                .build()
                .trySelect();

        assertTrue(ptxSelection.matched());
        assertSame(cudaForPtxCheck, ptxSelection.requireSelection().backend());
        assertEquals("missing declared module format ptx (declared: opencl-c)",
                ptxSelection.candidateDecisions().get(0).firstBlocker());
        assertEquals("cuda-c,ptx", ptxSelection.candidateDecisions().get(1).metadata().moduleFormatKeys());
        assertEquals(1_000_000, ptxSelection.candidateDecisions().get(0).score().preferenceScore());
        assertTrue(ptxSelection.candidateDecisions().get(0).score().rejected());
        assertEquals(999_000, ptxSelection.candidateDecisions().get(1).score().preferenceScore());
        assertTrue(!ptxSelection.candidateDecisions().get(1).score().rejected());
        assertTrue(ptxSelection.candidateDecisions().get(1).score().diagnostics()
                .contains("discovery-only support +250"));
        assertTrue(ptxSelection.candidateDecisions().get(1).score().runtimeScoreAdjustment() > 0);
        assertTrue(ptxSelection.candidateDecisions().get(1).score().diagnostics()
                .contains("runtime local memory +64"));
        assertTrue(ptxSelection.explanation().toMarkdown().contains("moduleFormats: cuda-c,ptx"));
        assertTrue(ptxSelection.explanation().toMarkdown().contains("score: preference=999000"));
        assertTrue(ptxSelection.explanation().toMarkdown().contains("runtimeAdjustment="));

        CloseCountingBackend openClForExecutionCheck = new CloseCountingBackend(availableBackendReport(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                new GpuRuntimeApiVersion(3, 0)
        ));
        CloseCountingBackend cudaForExecutionCheck = new CloseCountingBackend(availableBackendReport(
                GpuBackendTarget.CUDA,
                "CUDA",
                new GpuRuntimeApiVersion(12, 0)
        ));

        GpuRuntimeSelectionResult executionSelection = GpuRuntimeBackendPolicy.builder()
                .requireExecutionPipelineAvailable()
                .preferCatalog(List.of(
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.CUDA,
                                "CUDA discovery fixture",
                                cudaForExecutionCheck,
                                cudaDiscoveryEntry.executionSupport().orElseThrow()
                        ),
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.OPENCL,
                                "OpenCL metadata fixture",
                                openClForExecutionCheck,
                                openClEntry.executionSupport().orElseThrow()
                        )
                ))
                .build()
                .trySelect();

        assertTrue(executionSelection.matched());
        assertSame(openClForExecutionCheck, executionSelection.requireSelection().backend());
        assertEquals("backend execution pipeline is not available (declared stages: discover)",
                executionSelection.candidateDecisions().get(0).firstBlocker());
        assertTrue(executionSelection.candidateDecisions().get(0).closed());
        assertEquals("true", executionSelection.candidateDecisions().get(0).artifactFields("candidate")
                .get("candidate.score.rejected"));
        assertTrue(executionSelection.candidateDecisions().get(1).score().diagnostics()
                .contains("compile/prepare/invoke pipeline available +5000"));
        assertEquals(1, cudaForExecutionCheck.closeCalls);
        assertTrue(executionSelection.candidateDecisions().get(1).selected());
        assertTrue(executionSelection.explanation().toMarkdown().contains("executionPipeline: available=true"));
    }

    @Test
    void backendPolicyCanOptIntoScoreBasedCandidateRanking() {
        CloseCountingBackend defaultCuda = new CloseCountingBackend(availableBackendReport(
                GpuBackendTarget.CUDA,
                "CUDA",
                new GpuRuntimeApiVersion(12, 0)
        ));
        CloseCountingBackend defaultOpenCl = new CloseCountingBackend(availableBackendReport(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                new GpuRuntimeApiVersion(3, 0)
        ));
        GpuRuntimeBackendExecutionSupport cudaDiscoverySupport = GpuRuntimeBackendExecutionSupport.discoveryOnly(
                GpuBackendTarget.CUDA,
                "test.cuda.discovery-ranking",
                Set.of(GpuBackendModuleFormat.CUDA_C, GpuBackendModuleFormat.PTX),
                Set.of(GpuRuntimeCapability.COMPUTE_CAPABILITY),
                "test CUDA discovery fixture"
        );
        GpuRuntimeBackendExecutionSupport openClExecutionSupport = GpuRuntimeBackendExecutionSupport.productionPipeline(
                GpuBackendTarget.OPENCL,
                "test.opencl.production-ranking",
                Set.of(GpuBackendModuleFormat.OPENCL_C),
                Set.of(GpuRuntimeCapability.LOCAL_MEMORY),
                "test OpenCL production fixture"
        );

        GpuRuntimeSelectionResult fallbackSelection = GpuRuntimeBackendPolicy.builder()
                .preferCatalog(List.of(
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.CUDA,
                                "CUDA discovery fixture",
                                defaultCuda,
                                cudaDiscoverySupport
                        ),
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.OPENCL,
                                "OpenCL production fixture",
                                defaultOpenCl,
                                openClExecutionSupport
                        )
                ))
                .build()
                .trySelect();

        assertTrue(fallbackSelection.matched());
        assertSame(defaultCuda, fallbackSelection.requireSelection().backend());
        assertEquals(GpuRuntimeBackendCandidateOrdering.FALLBACK_ORDER,
                GpuRuntimeBackendPolicy.builder()
                        .preferCatalogEntry(metadataBackedCatalogEntry(
                                GpuBackendTarget.CUDA,
                                "CUDA discovery fixture",
                                defaultCuda,
                                cudaDiscoverySupport
                        ))
                        .build()
                        .candidateOrdering());
        assertEquals(1, fallbackSelection.candidateDecisions().size());
        assertEquals(0, defaultOpenCl.closeCalls);

        CloseCountingBackend rankedCuda = new CloseCountingBackend(availableBackendReport(
                GpuBackendTarget.CUDA,
                "CUDA",
                new GpuRuntimeApiVersion(12, 0)
        ));
        CloseCountingBackend rankedOpenCl = new CloseCountingBackend(availableBackendReport(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                new GpuRuntimeApiVersion(3, 0)
        ));

        GpuRuntimeSelectionResult rankedSelection = GpuRuntimeBackendPolicy.builder()
                .rankCandidatesByScore()
                .preferCatalog(List.of(
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.CUDA,
                                "CUDA discovery fixture",
                                rankedCuda,
                                cudaDiscoverySupport
                        ),
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.OPENCL,
                                "OpenCL production fixture",
                                rankedOpenCl,
                                openClExecutionSupport
                        )
                ))
                .build()
                .trySelect();

        assertTrue(rankedSelection.matched());
        assertSame(rankedOpenCl, rankedSelection.requireSelection().backend());
        assertEquals(2, rankedSelection.candidateDecisions().size());
        assertTrue(!rankedSelection.candidateDecisions().get(0).selected());
        assertTrue(rankedSelection.candidateDecisions().get(0).closed());
        assertTrue(rankedSelection.candidateDecisions().get(0).diagnostics().get(0)
                .contains("not selected: score below selected candidate OpenCL"));
        assertTrue(rankedSelection.candidateDecisions().get(1).selected());
        assertTrue(rankedSelection.candidateDecisions().get(1).score().totalScore()
                > rankedSelection.candidateDecisions().get(0).score().totalScore());
        assertTrue(rankedSelection.candidateDecisions().get(1).score().diagnostics()
                .contains("runtime max work-group size +1024"));
        assertEquals(1, rankedCuda.closeCalls);
        assertEquals(0, rankedOpenCl.closeCalls);
    }

    @Test
    void backendPolicyCanApplyExplicitScoreContributorsWithoutChangingFallbackDefault() {
        GpuRuntimeBackendExecutionSupport openClExecutionSupport = GpuRuntimeBackendExecutionSupport.productionPipeline(
                GpuBackendTarget.OPENCL,
                "test.opencl.score-contributor",
                Set.of(GpuBackendModuleFormat.OPENCL_C),
                Set.of(GpuRuntimeCapability.LOCAL_MEMORY),
                "test OpenCL production fixture"
        );
        GpuRuntimeBackendExecutionSupport cudaExecutionSupport = GpuRuntimeBackendExecutionSupport.productionPipeline(
                GpuBackendTarget.CUDA,
                "test.cuda.score-contributor",
                Set.of(GpuBackendModuleFormat.CUDA_C, GpuBackendModuleFormat.PTX),
                Set.of(GpuRuntimeCapability.COMPUTE_CAPABILITY),
                "test CUDA production fixture"
        );
        GpuRuntimeBackendScoreContributor cudaWorkloadScore = new GpuRuntimeBackendScoreContributor() {
            @Override
            public String extensionId() {
                return "test.backend-score:cuda-workload";
            }

            @Override
            public GpuRuntimeBackendScoreContribution scoreCandidate(GpuRuntimeBackendScoreContext context) {
                if (context.report().backendTarget() != GpuBackendTarget.CUDA) {
                    return GpuRuntimeBackendScoreContribution.none();
                }
                if (!"cuda-score-test".equals(context.compileOptions().optimizationProfile())) {
                    return GpuRuntimeBackendScoreContribution.none();
                }
                return GpuRuntimeBackendScoreContribution.of(50_000, "cached workload evidence +50000");
            }
        };

        CloseCountingBackend fallbackOpenCl = new CloseCountingBackend(availableBackendReport(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                new GpuRuntimeApiVersion(3, 0)
        ));
        CloseCountingBackend fallbackCuda = new CloseCountingBackend(availableBackendReport(
                GpuBackendTarget.CUDA,
                "CUDA",
                new GpuRuntimeApiVersion(12, 0)
        ));

        GpuRuntimeSelectionResult fallbackSelection = GpuRuntimeBackendPolicy.builder()
                .scoreCandidatesWith(cudaWorkloadScore)
                .scoreCandidatesForCompileOptions(GpuRuntimeCompileOptions.cuda(List.of(), Map.of(), "cuda-score-test"))
                .preferCatalog(List.of(
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.OPENCL,
                                "OpenCL score fixture",
                                fallbackOpenCl,
                                openClExecutionSupport
                        ),
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.CUDA,
                                "CUDA score fixture",
                                fallbackCuda,
                                cudaExecutionSupport
                        )
                ))
                .build()
                .trySelect();

        assertTrue(fallbackSelection.matched());
        assertSame(fallbackOpenCl, fallbackSelection.requireSelection().backend());
        assertEquals(1, fallbackSelection.candidateDecisions().size());
        assertEquals(0, fallbackCuda.closeCalls);

        CloseCountingBackend rankedOpenCl = new CloseCountingBackend(availableBackendReport(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                new GpuRuntimeApiVersion(3, 0)
        ));
        CloseCountingBackend rankedCuda = new CloseCountingBackend(availableBackendReport(
                GpuBackendTarget.CUDA,
                "CUDA",
                new GpuRuntimeApiVersion(12, 0)
        ));

        GpuRuntimeSelectionResult rankedSelection = GpuRuntimeBackendPolicy.builder()
                .rankCandidatesByScore()
                .scoreCandidatesWith(cudaWorkloadScore)
                .scoreCandidatesForCompileOptions(GpuRuntimeCompileOptions.cuda(List.of(), Map.of(), "cuda-score-test"))
                .preferCatalog(List.of(
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.OPENCL,
                                "OpenCL score fixture",
                                rankedOpenCl,
                                openClExecutionSupport
                        ),
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.CUDA,
                                "CUDA score fixture",
                                rankedCuda,
                                cudaExecutionSupport
                        )
                ))
                .build()
                .trySelect();

        assertTrue(rankedSelection.matched());
        assertSame(rankedCuda, rankedSelection.requireSelection().backend());
        assertTrue(!rankedSelection.candidateDecisions().get(0).selected());
        assertTrue(rankedSelection.candidateDecisions().get(0).closed());
        assertTrue(rankedSelection.candidateDecisions().get(1).selected());
        assertEquals(50_000, rankedSelection.candidateDecisions().get(1).score().policyScoreAdjustment());
        assertTrue(rankedSelection.candidateDecisions().get(1).score().diagnostics()
                .contains("policy score contributor test.backend-score:cuda-workload: cached workload evidence +50000"));
        assertTrue(rankedSelection.explanation().toMarkdown().contains("policyAdjustment=50000"));
        assertEquals(1, rankedOpenCl.closeCalls);
        assertEquals(0, rankedCuda.closeCalls);
    }

    @Test
    void backendPolicyCanRankWithWorkloadHints() {
        GpuRuntimeBackendExecutionSupport openClExecutionSupport = GpuRuntimeBackendExecutionSupport.productionPipeline(
                GpuBackendTarget.OPENCL,
                "test.opencl.workload-hints-score",
                Set.of(GpuBackendModuleFormat.OPENCL_C),
                Set.of(GpuRuntimeCapability.LOCAL_MEMORY),
                "test OpenCL production fixture"
        );
        GpuRuntimeBackendExecutionSupport cudaExecutionSupport = GpuRuntimeBackendExecutionSupport.productionPipeline(
                GpuBackendTarget.CUDA,
                "test.cuda.workload-hints-score",
                Set.of(GpuBackendModuleFormat.CUDA_C, GpuBackendModuleFormat.PTX),
                Set.of(
                        GpuRuntimeCapability.COMPUTE_CAPABILITY,
                        GpuRuntimeCapability.LOCAL_MEMORY,
                        GpuRuntimeCapability.MAX_WORK_GROUP_SIZE
                ),
                "test CUDA production fixture"
        );
        GpuRuntimeWorkloadHints hints = GpuRuntimeWorkloadHints.builder()
                .expectedItemCount(1_000_000L)
                .preferredWorkGroupSize(256)
                .memoryIntensity(GpuRuntimeWorkloadIntensity.HIGH)
                .arithmeticIntensity(GpuRuntimeWorkloadIntensity.HIGH)
                .requireCapability(GpuRuntimeCapability.COMPUTE_CAPABILITY)
                .preferModuleFormat(GpuBackendModuleFormat.PTX)
                .build();

        CloseCountingBackend fallbackOpenCl = new CloseCountingBackend(GpuRuntimeBackendReport.available(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "OpenCL workload GPU",
                new GpuRuntimeApiVersion(3, 0),
                "OpenCL 3.0",
                java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                16_384L,
                64L,
                "synthetic OpenCL workload fixture"
        ));
        CloseCountingBackend fallbackCuda = new CloseCountingBackend(GpuRuntimeBackendReport.available(
                GpuBackendTarget.CUDA,
                "CUDA",
                "CUDA workload GPU",
                new GpuRuntimeApiVersion(12, 0),
                "CUDA 12.0",
                java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                98_304L,
                512L,
                "synthetic CUDA workload fixture"
        ));

        GpuRuntimeSelectionResult fallbackSelection = GpuRuntimeBackendPolicy.builder()
                .scoreCandidatesWithWorkloadHints(hints)
                .preferCatalog(List.of(
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.OPENCL,
                                "OpenCL score fixture",
                                fallbackOpenCl,
                                openClExecutionSupport
                        ),
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.CUDA,
                                "CUDA score fixture",
                                fallbackCuda,
                                cudaExecutionSupport
                        )
                ))
                .build()
                .trySelect();

        assertTrue(fallbackSelection.matched());
        assertSame(fallbackOpenCl, fallbackSelection.requireSelection().backend());
        assertEquals(1, fallbackSelection.candidateDecisions().size());
        assertEquals(0, fallbackCuda.closeCalls);

        CloseCountingBackend rankedOpenCl = new CloseCountingBackend(fallbackOpenCl.report);
        CloseCountingBackend rankedCuda = new CloseCountingBackend(fallbackCuda.report);
        GpuRuntimeSelectionResult rankedSelection = GpuRuntimeBackendPolicy.builder()
                .rankCandidatesByScore()
                .scoreCandidatesWithWorkloadHints(hints)
                .preferCatalog(List.of(
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.OPENCL,
                                "OpenCL score fixture",
                                rankedOpenCl,
                                openClExecutionSupport
                        ),
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.CUDA,
                                "CUDA score fixture",
                                rankedCuda,
                                cudaExecutionSupport
                        )
                ))
                .build()
                .trySelect();

        assertTrue(rankedSelection.matched());
        assertSame(rankedCuda, rankedSelection.requireSelection().backend());
        assertTrue(rankedSelection.candidateDecisions().get(0).closed());
        assertTrue(rankedSelection.candidateDecisions().get(1).selected());
        assertEquals(465_000, rankedSelection.candidateDecisions().get(1).score().policyScoreAdjustment());
        assertTrue(rankedSelection.candidateDecisions().get(1).score().diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.contains("workload capability compute-capability supported")));
        assertTrue(rankedSelection.explanation().toMarkdown().contains("policyAdjustment=465000"));
        assertEquals(1, rankedOpenCl.closeCalls);
        assertEquals(0, rankedCuda.closeCalls);
    }

    @Test
    void workloadHintInferenceCapturesAddressSpaceRequirements() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "jtg_address_space_kernel",
                "javatogpu/runtime/address-space-inference.cl",
                "__kernel void jtg_address_space_kernel(__global float* input, "
                        + "__constant float* lookup, __local float* scratch, __global float* output) { "
                        + "scratch[0] = input[0] + lookup[0]; output[0] = scratch[0]; }",
                "javatogpu/runtime/address-space-inference.irgpu.properties",
                List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("lookup", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scratch", "float[]", GpuKernelParameterAccess.LOCAL),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "addressSpaceInference",
                        "jtg_address_space_kernel",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "addressSpaceInference",
                                "jtg_address_space_kernel",
                                "body\n  output[0] = input[0] + lookup[0]\n",
                                List.of()
                        ))
                ),
                List.of(
                        new IrGpuEntryParameter("input", "float[]", "GLOBAL", true, List.of("const")),
                        new IrGpuEntryParameter("lookup", "float[]", "CONSTANT", true, List.of("const")),
                        new IrGpuEntryParameter("scratch", "float[]", "LOCAL", false, List.of()),
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of())
                ),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/runtime/address-space-inference.cl")),
                "opencl",
                "off"
        );

        GpuRuntimeInferredWorkloadHints inferred = GpuRuntimeWorkloadHintInference.infer(descriptor, artifact);
        GpuRuntimeWorkloadHints hints = inferred.hints();

        assertTrue(hints.requiresCapability(GpuRuntimeCapability.ADDRESS_SPACE_GLOBAL));
        assertTrue(hints.requiresCapability(GpuRuntimeCapability.ADDRESS_SPACE_LOCAL));
        assertTrue(hints.requiresCapability(GpuRuntimeCapability.ADDRESS_SPACE_CONSTANT));
        assertTrue(hints.requiresCapability(GpuRuntimeCapability.LOCAL_MEMORY));
        assertTrue(inferred.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.contains("address-space-global")));
    }

    @Test
    void backendPolicyCanRankWithInferredWorkloadHints() {
        GpuRuntimeBackendExecutionSupport cudaExecutionSupport = GpuRuntimeBackendExecutionSupport.productionPipeline(
                GpuBackendTarget.CUDA,
                "test.cuda.inferred-workload-hints-score",
                Set.of(GpuBackendModuleFormat.CUDA_C, GpuBackendModuleFormat.PTX),
                Set.of(GpuRuntimeCapability.COMPUTE_CAPABILITY),
                "test CUDA production fixture"
        );
        GpuRuntimeBackendExecutionSupport openClExecutionSupport = GpuRuntimeBackendExecutionSupport.productionPipeline(
                GpuBackendTarget.OPENCL,
                "test.opencl.inferred-workload-hints-score",
                Set.of(GpuBackendModuleFormat.OPENCL_C),
                Set.of(
                        GpuRuntimeCapability.FP64,
                        GpuRuntimeCapability.IMAGES,
                        GpuRuntimeCapability.IMAGE_ABI,
                        GpuRuntimeCapability.IMAGE_3D_WRITES,
                        GpuRuntimeCapability.LOCAL_MEMORY,
                        GpuRuntimeCapability.STRUCT_ABI,
                        GpuRuntimeCapability.VECTOR_TYPES,
                        GpuRuntimeCapability.ADDRESS_SPACE_GLOBAL,
                        GpuRuntimeCapability.ADDRESS_SPACE_LOCAL,
                        GpuRuntimeCapability.ADDRESS_SPACE_CONSTANT
                ),
                "test OpenCL production fixture"
        );
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "jtg_inferred_rank_kernel",
                "javatogpu/runtime/inferred-rank.cl",
                "__kernel void jtg_inferred_rank_kernel(__global const double* input, "
                        + "__global float* output, write_only image3d_t volume) { "
                        + "float value = mad((float)input[0], 2.0f, sqrt(output[0])); "
                        + "output[0] = sin(value) + cos(value); }",
                "javatogpu/runtime/inferred-rank.irgpu.properties",
                List.of(
                        new GpuKernelParameterDescriptor("input", "double[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE),
                        new GpuKernelParameterDescriptor(
                                "volume",
                                "net.sixik.ga_utils.javatogpu.api.Image3DWriteOnly",
                                GpuKernelParameterAccess.READ_WRITE
                        ),
                        new GpuKernelParameterDescriptor("points", "example.Point[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scratch", "float[]", GpuKernelParameterAccess.LOCAL)
                )
        );
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "inferredRankKernel",
                        "jtg_inferred_rank_kernel",
                        List.of(),
                        List.of("typedef struct { float x; float y; } Point;"),
                        List.of(IrGpuMethodBody.entry(
                                "inferredRankKernel",
                                "jtg_inferred_rank_kernel",
                                "body\n  value = mad(value, sqrt(value), sin(value)) + cos(value)\n",
                                List.of()
                        ))
                ),
                List.of(
                        new IrGpuEntryParameter("input", "double[]", "GLOBAL", true, List.of("const")),
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of()),
                        new IrGpuEntryParameter("scratch", "float[]", "LOCAL", false, List.of())
                ),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/runtime/inferred-rank.cl")),
                "opencl",
                "off"
        );

        CloseCountingBackend fallbackCuda = new CloseCountingBackend(GpuRuntimeBackendReport.available(
                GpuBackendTarget.CUDA,
                "CUDA",
                "CUDA inferred workload GPU",
                new GpuRuntimeApiVersion(12, 0),
                "CUDA 12.0",
                java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                16_384L,
                64L,
                "synthetic CUDA inferred workload fixture"
        ));
        CloseCountingBackend fallbackOpenCl = new CloseCountingBackend(GpuRuntimeBackendReport.available(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "OpenCL inferred workload GPU",
                new GpuRuntimeApiVersion(3, 0),
                "OpenCL 3.0",
                java.util.EnumSet.of(
                        GpuRuntimeFeature.DOUBLE_PRECISION,
                        GpuRuntimeFeature.IMAGES,
                        GpuRuntimeFeature.IMAGE3D_WRITES
                ),
                98_304L,
                512L,
                "synthetic OpenCL inferred workload fixture"
        ));

        GpuRuntimeSelectionResult fallbackSelection = GpuRuntimeBackendPolicy.builder()
                .scoreCandidatesWithInferredWorkloadHints(descriptor, artifact)
                .preferCatalog(List.of(
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.CUDA,
                                "CUDA score fixture",
                                fallbackCuda,
                                cudaExecutionSupport
                        ),
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.OPENCL,
                                "OpenCL score fixture",
                                fallbackOpenCl,
                                openClExecutionSupport
                        )
                ))
                .build()
                .trySelect();

        assertTrue(fallbackSelection.matched());
        assertSame(fallbackCuda, fallbackSelection.requireSelection().backend());
        assertEquals(1, fallbackSelection.candidateDecisions().size());
        assertEquals(0, fallbackOpenCl.closeCalls);

        CloseCountingBackend rankedCuda = new CloseCountingBackend(fallbackCuda.report);
        CloseCountingBackend rankedOpenCl = new CloseCountingBackend(fallbackOpenCl.report);
        GpuRuntimeSelectionResult rankedSelection = GpuRuntimeBackendPolicy.builder()
                .rankCandidatesByScore()
                .scoreCandidatesWithInferredWorkloadHints(descriptor, artifact)
                .preferCatalog(List.of(
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.CUDA,
                                "CUDA score fixture",
                                rankedCuda,
                                cudaExecutionSupport
                        ),
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.OPENCL,
                                "OpenCL score fixture",
                                rankedOpenCl,
                                openClExecutionSupport
                        )
                ))
                .build()
                .trySelect();

        assertTrue(rankedSelection.matched());
        assertSame(rankedOpenCl, rankedSelection.requireSelection().backend());
        assertTrue(rankedSelection.candidateDecisions().get(0).closed());
        assertTrue(rankedSelection.candidateDecisions().get(1).selected());
        assertTrue(rankedSelection.candidateDecisions().get(1).score().policyScoreAdjustment() > 0);
        assertTrue(rankedSelection.candidateDecisions().get(1).score().diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.contains("inferred workload capability fp64 supported")));
        assertTrue(rankedSelection.candidateDecisions().get(1).score().diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.contains("inferred workload memory intensity HIGH")));
        assertTrue(rankedSelection.candidateDecisions().get(1).score().diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.contains("inferred workload capability address-space-global supported")));
        assertTrue(rankedSelection.candidateDecisions().get(1).score().diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.contains("javatogpu.backend.inferred-workload-hints-score")));
        assertEquals(1, rankedCuda.closeCalls);
        assertEquals(0, rankedOpenCl.closeCalls);
    }

    @Test
    void backendPolicyCanRankWithPrecomputedCompilerFeedback() {
        GpuRuntimeBackendExecutionSupport openClExecutionSupport = GpuRuntimeBackendExecutionSupport.productionPipeline(
                GpuBackendTarget.OPENCL,
                "test.opencl.compiler-feedback-score",
                Set.of(GpuBackendModuleFormat.OPENCL_C),
                Set.of(GpuRuntimeCapability.LOCAL_MEMORY),
                "test OpenCL production fixture"
        );
        GpuRuntimeBackendExecutionSupport cudaExecutionSupport = GpuRuntimeBackendExecutionSupport.productionPipeline(
                GpuBackendTarget.CUDA,
                "test.cuda.compiler-feedback-score",
                Set.of(GpuBackendModuleFormat.CUDA_C, GpuBackendModuleFormat.PTX),
                Set.of(GpuRuntimeCapability.COMPUTE_CAPABILITY),
                "test CUDA production fixture"
        );
        GpuBackendCompilerFeedbackReport cudaFeedbackReport = new GpuBackendCompilerFeedbackReport(
                new GpuBackendCompilerFeedbackRequest(
                        GpuBackendTarget.CUDA,
                        "ptx",
                        "javatogpu/runtime/backend-score.ptx",
                        "Used 24 registers; 0 bytes stack frame; 0 bytes spill stores; 0 bytes spill loads"
                ),
                List.of(new GpuBackendCompilerFeedback(
                        "compiler-feedback:test",
                        "1",
                        "jtg_backend_score_kernel",
                        24,
                        GpuBackendCompilerFeedback.UNKNOWN,
                        GpuBackendCompilerFeedback.UNKNOWN,
                        0,
                        0,
                        0,
                        512,
                        750,
                        Map.of("source", "unit-test"),
                        List.of()
                )),
                List.of()
        );

        CloseCountingBackend fallbackOpenCl = new CloseCountingBackend(availableBackendReport(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                new GpuRuntimeApiVersion(3, 0)
        ));
        CloseCountingBackend fallbackCuda = new CloseCountingBackend(availableBackendReport(
                GpuBackendTarget.CUDA,
                "CUDA",
                new GpuRuntimeApiVersion(12, 0)
        ));

        GpuRuntimeSelectionResult fallbackSelection = GpuRuntimeBackendPolicy.builder()
                .scoreCandidatesWithCompilerFeedback(cudaFeedbackReport)
                .preferCatalog(List.of(
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.OPENCL,
                                "OpenCL score fixture",
                                fallbackOpenCl,
                                openClExecutionSupport
                        ),
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.CUDA,
                                "CUDA score fixture",
                                fallbackCuda,
                                cudaExecutionSupport
                        )
                ))
                .build()
                .trySelect();

        assertTrue(fallbackSelection.matched());
        assertSame(fallbackOpenCl, fallbackSelection.requireSelection().backend());
        assertEquals(1, fallbackSelection.candidateDecisions().size());
        assertEquals(0, fallbackCuda.closeCalls);

        CloseCountingBackend rankedOpenCl = new CloseCountingBackend(availableBackendReport(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                new GpuRuntimeApiVersion(3, 0)
        ));
        CloseCountingBackend rankedCuda = new CloseCountingBackend(availableBackendReport(
                GpuBackendTarget.CUDA,
                "CUDA",
                new GpuRuntimeApiVersion(12, 0)
        ));

        GpuRuntimeSelectionResult rankedSelection = GpuRuntimeBackendPolicy.builder()
                .rankCandidatesByScore()
                .scoreCandidatesWithCompilerFeedback(cudaFeedbackReport)
                .preferCatalog(List.of(
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.OPENCL,
                                "OpenCL score fixture",
                                rankedOpenCl,
                                openClExecutionSupport
                        ),
                        metadataBackedCatalogEntry(
                                GpuBackendTarget.CUDA,
                                "CUDA score fixture",
                                rankedCuda,
                                cudaExecutionSupport
                        )
                ))
                .build()
                .trySelect();

        assertTrue(rankedSelection.matched());
        assertSame(rankedCuda, rankedSelection.requireSelection().backend());
        assertTrue(rankedSelection.candidateDecisions().get(0).closed());
        assertTrue(rankedSelection.candidateDecisions().get(1).selected());
        assertEquals(223_000, rankedSelection.candidateDecisions().get(1).score().policyScoreAdjustment());
        assertTrue(rankedSelection.candidateDecisions().get(1).score().diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.contains("compiler feedback target=CUDA")));
        assertTrue(rankedSelection.explanation().toMarkdown().contains("policyAdjustment=223000"));
        assertEquals(1, rankedOpenCl.closeCalls);
        assertEquals(0, rankedCuda.closeCalls);
    }

    @Test
    void backendPolicyCanRankWithCachedMethodTestProbeEvidence(@TempDir Path tempDir) throws Exception {
        Path fixtureRoot = tempDir.resolve("fixture-root");
        Files.createDirectories(fixtureRoot.resolve("fixtures"));
        Files.writeString(fixtureRoot.resolve("fixtures/backend-score.inputs.json"), "{\"input\":[1.0,2.0]}");
        Files.writeString(fixtureRoot.resolve("fixtures/backend-score.outputs.json"), "{\"output\":[2.0,4.0]}");
        Path cacheDirectory = tempDir.resolve("method-test-cache");
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "jtg_backend_score_kernel",
                "javatogpu/runtime/backend-score.cl",
                "__kernel void jtg_backend_score_kernel(__global const float* input, __global float* output) { }",
                "javatogpu/runtime/backend-score.irgpu.properties",
                List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule("backendScoreKernel", "jtg_backend_score_kernel", List.of(), List.of(), List.of()),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/runtime/backend-score.cl")),
                "cuda",
                "probe-ranked"
        ).withMethodTestVectors(List.of(new IrGpuMethodTestVectorMetadata(
                "backendScoreKernel",
                "jtg_backend_score_kernel",
                "backend-score-smoke",
                List.of("fixtures/backend-score.inputs.json"),
                List.of("fixtures/backend-score.outputs.json"),
                "abs=1e-5",
                List.of("selection", "backend-score"),
                true,
                "GPUTest"
        )));
        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions
                .cuda(List.of(), Map.of(), "probe-ranked")
                .withPersistentMethodTestProbeEvidenceRanking(cacheDirectory, Duration.ofDays(10));
        GpuRuntimeDeviceProfile cudaProfile = GpuRuntimeDeviceProfile.cuda(
                "cuda-score-device-0",
                "CUDA score GPU",
                "NVIDIA",
                "test-driver",
                "CUDA 12.0 compute capability 9.0",
                GpuDeviceClassTarget.DGPU,
                16L * 1024L * 1024L * 1024L,
                "NVIDIA CUDA",
                "driver 999, CUDA 12.0"
        );
        GpuRuntimeBackendReport cudaReport = GpuRuntimeBackendReport.available(
                GpuBackendTarget.CUDA,
                "CUDA",
                cudaProfile.deviceLabel(),
                new GpuRuntimeApiVersion(12, 0),
                cudaProfile.apiVersionText(),
                java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                null,
                null,
                "synthetic CUDA backend score fixture"
        );

        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(
                new java.net.URL[]{fixtureRoot.toUri().toURL()},
                previousClassLoader
        )) {
            Thread.currentThread().setContextClassLoader(classLoader);
            GpuRuntimeMethodTestProbePlan probePlan = GpuRuntimeMethodTestProbes.plan(descriptor, artifact);
            GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                    descriptor,
                    probePlan,
                    classLoader
            );
            GpuRuntimeMethodTestInvocationMaterializationPlan materialization =
                    GpuRuntimeMethodTestProbes.fixtureInvocationMaterialization(descriptor, bindings);
            GpuRuntimeMethodTestInvocationMaterialization invocation = materialization.invocations().get(0);
            GpuExecutionConfig executionConfig = GpuExecutionConfig.oneDimensional(2L);
            GpuRuntimeMethodTestGpuProbeEvidenceKey evidenceKey = GpuRuntimeMethodTestGpuProbeEvidenceKey.from(
                    descriptor,
                    invocation,
                    executionConfig,
                    compileOptions,
                    cudaReport,
                    cudaProfile
            );
            GpuRuntimeMethodTestGpuProbeCache.persistent(cacheDirectory).record(new GpuRuntimeMethodTestGpuProbeExecution(
                    "backend-score-smoke",
                    evidenceKey,
                    false,
                    true,
                    true,
                    executionConfig,
                    List.of(new GpuRuntimeMethodTestReferenceComparison(
                            "backend-score-smoke",
                            1,
                            "output",
                            "float[]",
                            GpuKernelParameterAccess.READ_WRITE,
                            true,
                            true,
                            "java-float-array",
                            2,
                            List.of("2.0", "4.0"),
                            "java-float-array",
                            2,
                            List.of("2.0", "4.0"),
                            1.0e-5d,
                            0.0d,
                            "none",
                            "none",
                            "cached backend score fixture"
                    )),
                    List.of(),
                    List.of("cached backend score fixture")
            ));

            CloseCountingBackend rankedOpenCl = new CloseCountingBackend(availableBackendReport(
                    GpuBackendTarget.OPENCL,
                    "OpenCL",
                    new GpuRuntimeApiVersion(3, 0)
            ));
            CloseCountingBackend rankedCuda = new CloseCountingBackend(cudaReport);
            GpuRuntimeSelectionResult rankedSelection = GpuRuntimeBackendPolicy.builder()
                    .rankCandidatesByScore()
                    .scoreCandidatesForCompileRequest(new GpuRuntimeCompileRequest(
                            descriptor,
                            compileOptions,
                            cudaProfile,
                            Optional.of(artifact)
                    ))
                    .scoreCandidatesWithCachedMethodTestProbeEvidence()
                    .preferCatalog(List.of(
                            metadataBackedCatalogEntry(
                                    GpuBackendTarget.OPENCL,
                                    "OpenCL score fixture",
                                    rankedOpenCl,
                                    GpuRuntimeBackendExecutionSupport.productionPipeline(
                                            GpuBackendTarget.OPENCL,
                                            "test.opencl.method-test-score",
                                            Set.of(GpuBackendModuleFormat.OPENCL_C),
                                            Set.of(GpuRuntimeCapability.LOCAL_MEMORY),
                                            "test OpenCL production fixture"
                                    )
                            ),
                            metadataBackedCatalogEntry(
                                    GpuBackendTarget.CUDA,
                                    "CUDA score fixture",
                                    rankedCuda,
                                    GpuRuntimeBackendExecutionSupport.productionPipeline(
                                            GpuBackendTarget.CUDA,
                                            "test.cuda.method-test-score",
                                            Set.of(GpuBackendModuleFormat.CUDA_C, GpuBackendModuleFormat.PTX),
                                            Set.of(GpuRuntimeCapability.COMPUTE_CAPABILITY),
                                            "test CUDA production fixture"
                                    )
                            )
                    ))
                    .build()
                    .trySelect();

            assertTrue(rankedSelection.matched());
            assertSame(rankedCuda, rankedSelection.requireSelection().backend());
            assertTrue(rankedSelection.candidateDecisions().get(0).closed());
            assertTrue(rankedSelection.candidateDecisions().get(1).selected());
            assertEquals(1_600_000_000, rankedSelection.candidateDecisions().get(1).score().policyScoreAdjustment());
            assertTrue(rankedSelection.candidateDecisions().get(1).score().diagnostics().stream()
                    .anyMatch(diagnostic -> diagnostic.contains("passed cached method-test backend evidence")));
            assertTrue(rankedSelection.explanation().toMarkdown().contains("policyAdjustment=1600000000"));
            assertEquals(1, rankedOpenCl.closeCalls);
            assertEquals(0, rankedCuda.closeCalls);

            Path cacheEntry = cacheDirectory.resolve(evidenceKey.stableHash() + ".properties");
            String staleCacheText = Files.readString(cacheEntry, StandardCharsets.UTF_8)
                    .replaceAll(
                            "createdEpochMillis=\\d+",
                            "createdEpochMillis=" + (System.currentTimeMillis() - Duration.ofDays(9).toMillis())
                    );
            Files.writeString(cacheEntry, staleCacheText, StandardCharsets.UTF_8);

            CloseCountingBackend staleOpenCl = new CloseCountingBackend(availableBackendReport(
                    GpuBackendTarget.OPENCL,
                    "OpenCL",
                    new GpuRuntimeApiVersion(3, 0)
            ));
            CloseCountingBackend staleCuda = new CloseCountingBackend(cudaReport);
            GpuRuntimeSelectionResult staleSelection = GpuRuntimeBackendPolicy.builder()
                    .rankCandidatesByScore()
                    .scoreCandidatesForCompileRequest(new GpuRuntimeCompileRequest(
                            descriptor,
                            compileOptions,
                            cudaProfile,
                            Optional.of(artifact)
                    ))
                    .scoreCandidatesWithCachedMethodTestProbeEvidence()
                    .preferCatalog(List.of(
                            metadataBackedCatalogEntry(
                                    GpuBackendTarget.OPENCL,
                                    "OpenCL score fixture",
                                    staleOpenCl,
                                    GpuRuntimeBackendExecutionSupport.productionPipeline(
                                            GpuBackendTarget.OPENCL,
                                            "test.opencl.method-test-score-stale",
                                            Set.of(GpuBackendModuleFormat.OPENCL_C),
                                            Set.of(GpuRuntimeCapability.LOCAL_MEMORY),
                                            "test OpenCL production fixture"
                                    )
                            ),
                            metadataBackedCatalogEntry(
                                    GpuBackendTarget.CUDA,
                                    "CUDA score fixture",
                                    staleCuda,
                                    GpuRuntimeBackendExecutionSupport.productionPipeline(
                                            GpuBackendTarget.CUDA,
                                            "test.cuda.method-test-score-stale",
                                            Set.of(GpuBackendModuleFormat.CUDA_C, GpuBackendModuleFormat.PTX),
                                            Set.of(GpuRuntimeCapability.COMPUTE_CAPABILITY),
                                            "test CUDA production fixture"
                                    )
                            )
                    ))
                    .build()
                    .trySelect();

            int staleAdjustment = staleSelection.candidateDecisions().get(1).score().policyScoreAdjustment();
            assertTrue(staleSelection.matched());
            assertSame(staleCuda, staleSelection.requireSelection().backend());
            assertTrue(staleSelection.candidateDecisions().get(0).closed());
            assertTrue(staleSelection.candidateDecisions().get(1).selected());
            assertTrue(staleAdjustment > 0 && staleAdjustment < 1_600_000_000);
            assertTrue(staleSelection.candidateDecisions().get(1).score().diagnostics().stream()
                    .anyMatch(diagnostic -> diagnostic.contains("ageLimited=1")));
            assertTrue(staleSelection.candidateDecisions().get(1).score().diagnostics().stream()
                    .anyMatch(diagnostic -> diagnostic.contains("freshnessPermille=")));
            assertEquals(1, staleOpenCl.closeCalls);
            assertEquals(0, staleCuda.closeCalls);
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void backendSelectionOrchestratorRecordsFactoryFailuresAndClosesRejectedOwnedBackends() {
        CloseCountingBackend unavailableOpenCl = new CloseCountingBackend(
                GpuRuntimeBackendReport.unavailable(GpuBackendTarget.OPENCL, "OpenCL", "OpenCL ICD is unavailable")
        );
        CloseCountingBackend selectedCuda = new CloseCountingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.CUDA,
                        "CUDA",
                        "Future CUDA GPU",
                        new GpuRuntimeApiVersion(12, 0),
                        "CUDA 12.0",
                        java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                        65_536L,
                        256L,
                        null
                )
        );

        GpuRuntimeSelectionResult result = GpuRuntimeBackendPolicy.builder()
                .preferFactory(() -> {
                    throw new IllegalStateException("backend adapter missing");
                })
                .preferOwnedBackend(unavailableOpenCl)
                .preferOwnedBackend(selectedCuda)
                .build()
                .trySelect();

        assertTrue(result.matched());
        assertSame(selectedCuda, result.requireSelection().backend());
        assertEquals(3, result.candidateDecisions().size());
        assertEquals("creation-failed", result.candidateDecisions().get(0).firstBlocker());
        assertEquals("OpenCL ICD is unavailable", result.candidateDecisions().get(1).firstBlocker());
        assertTrue(result.candidateDecisions().get(1).closed());
        assertTrue(result.candidateDecisions().get(2).selected());
        assertEquals(1, unavailableOpenCl.closeCalls);
        assertEquals(0, selectedCuda.closeCalls);
        assertTrue(result.explanationSummary().contains("candidate.0: Failed to create backend candidate: backend adapter missing"));
        assertTrue(result.explanationSummary().contains("CUDA selected"));
    }

    @Test
    void backendPolicySupportsBorrowedBackendInstancesWithoutAutoClose() {
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();

        final class CloseableReportingBackend implements GpuRuntimeBackend, AutoCloseable {
            private final GpuRuntimeBackendReport report;
            private int closeCalls;

            private CloseableReportingBackend(GpuRuntimeBackendReport report) {
                this.report = report;
            }

            @Override
            public GpuBackendTarget backendTarget() {
                return report.backendTarget();
            }

            @Override
            public GpuRuntimeBackendReport describeCapabilities() {
                return report;
            }

            @Override
            public void invoke(GpuKernelInvocation invocation) {
            }

            @Override
            public void close() {
                closeCalls++;
            }
        }

        CloseableReportingBackend sharedBackend = new CloseableReportingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Borrowed Policy GPU",
                        new GpuRuntimeApiVersion(3, 0),
                        "OpenCL 3.0 Borrowed Policy GPU",
                        java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                        16_384L,
                        128L,
                        null
                )
        );

        GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
                .preferBorrowedBackend(sharedBackend)
                .build();

        try (GpuRuntimeScope ignored = GpuRuntime.use(policy)) {
            assertSame(sharedBackend, GpuRuntime.backend());
            assertTrue(!ignored.ownsInstalledBackend());
        }

        assertSame(previousBackend, GpuRuntime.backend());
        assertEquals(0, sharedBackend.closeCalls);

        GpuRuntimeBackendSelection selection = policy.select();
        assertEquals(GpuRuntimeBackendOwnership.BORROWED, selection.ownership());
        assertTrue(!selection.ownsBackend());
        assertSame(sharedBackend, selection.backend());
        assertEquals(0, sharedBackend.closeCalls);
    }

    @Test
    void selectionResultInstallUsesSelectionOwnershipSemantics() {
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();

        final class CloseableReportingBackend implements GpuRuntimeBackend, AutoCloseable {
            private final GpuRuntimeBackendReport report;
            private int closeCalls;

            private CloseableReportingBackend(GpuRuntimeBackendReport report) {
                this.report = report;
            }

            @Override
            public GpuBackendTarget backendTarget() {
                return report.backendTarget();
            }

            @Override
            public GpuRuntimeBackendReport describeCapabilities() {
                return report;
            }

            @Override
            public void invoke(GpuKernelInvocation invocation) {
            }

            @Override
            public void close() {
                closeCalls++;
            }
        }

        CloseableReportingBackend backend = new CloseableReportingBackend(
                GpuRuntimeBackendReport.available(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Result Install GPU",
                        new GpuRuntimeApiVersion(3, 0),
                        "OpenCL 3.0 Result Install GPU",
                        java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                        16_384L,
                        128L,
                        null
                )
        );

        GpuRuntimeSelectionResult result = GpuRuntimeBackendPolicy.builder()
                .preferBorrowedBackend(backend)
                .build()
                .trySelect();

        try (GpuRuntimeScope ignored = result.install()) {
            assertSame(backend, GpuRuntime.backend());
            assertTrue(!ignored.ownsInstalledBackend());
        }

        assertSame(previousBackend, GpuRuntime.backend());
        assertEquals(0, backend.closeCalls);
    }

    @Test
    void backendDeviceSelectionInstallPreselectsDeviceAwareBackend() {
        PreselectingBackend backend = new PreselectingBackend(GpuRuntimeBackendReport.available(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "Preselected Backend GPU",
                new GpuRuntimeApiVersion(3, 0),
                "OpenCL 3.0 Preselected Backend GPU",
                java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                16_384L,
                128L,
                null
        ));
        GpuRuntimeSelectionResult backendResult = GpuRuntimeBackendPolicy.builder()
                .preferBorrowedBackend(backend)
                .build()
                .trySelect();
        GpuRuntimeDeviceProfile device = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-preselected-0",
                "Preselected Device GPU",
                "NVIDIA",
                "preselected-driver",
                "OpenCL 3.0 Preselected Device GPU",
                "NVIDIA CUDA",
                "OpenCL 3.0 CUDA",
                GpuDeviceClassTarget.DGPU,
                48,
                8L * 1024L * 1024L * 1024L,
                64L * 1024L,
                1024L,
                1L,
                false,
                true,
                true,
                false
        );
        GpuRuntimeDeviceSelection deviceSelection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                GpuRuntimeDevicePolicyContext.forBackendDiscovery(
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                                .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED),
                        List.of(device)
                )
        );
        GpuRuntimeDeviceDiscoveryResult discovery = GpuRuntimeDeviceDiscoveryResult.available(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                List.of(device),
                deviceSelection
        );
        GpuRuntimeBackendDeviceSelection selection = backendResult.withDeviceDiscovery(
                GpuRuntimeDeviceDiscoveryCatalog.of(List.of(discovery))
        );
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();

        try (GpuRuntimeScope ignored = GpuRuntime.use(selection)) {
            assertSame(backend, GpuRuntime.backend());
            assertSame(discovery, backend.preselectedDiscovery);
        }

        assertSame(previousBackend, GpuRuntime.backend());
    }

    @Test
    void openClReportExposesApiVersionAndFeatures() {
        net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend backend =
                new net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend(
                        net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend.CacheMode.SHARED
                ) {
                    @Override
                    public GpuRuntimeBackendReport describeCapabilities() {
                        return GpuRuntimeBackendReport.available(
                                GpuBackendTarget.OPENCL,
                                "OpenCL (shared cache)",
                                "Fake GPU",
                                new GpuRuntimeApiVersion(3, 0),
                                "OpenCL 3.0 Fake GPU",
                                java.util.EnumSet.of(GpuRuntimeFeature.DOUBLE_PRECISION, GpuRuntimeFeature.IMAGES, GpuRuntimeFeature.SHARED_CACHE),
                                32_768L,
                                256L,
                                null
                        );
                    }
                };

        GpuRuntimeBackendReport report = backend.describeCapabilities();

        assertTrue(report.available());
        assertEquals(GpuBackendTarget.OPENCL, report.backendTarget());
        assertEquals(new GpuRuntimeApiVersion(3, 0), report.apiVersion());
        assertTrue(report.supports(GpuRuntimeFeature.DOUBLE_PRECISION));
        assertTrue(report.supports(GpuRuntimeFeature.IMAGES));
        assertTrue(report.supports(GpuRuntimeFeature.SHARED_CACHE));
    }

    @Test
    void runtimeBackendDefaultsPromotionArtifactSupportToFailClosed() {
        GpuRuntimeBackend backend = invocation -> {
        };

        GpuPromotionArtifactSupport support = backend.promotionArtifactSupport();

        assertEquals(GpuBackendTarget.UNKNOWN, support.backendTarget());
        assertTrue(support.supportedArtifacts().isEmpty());
        assertEquals(GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS, support.missingArtifacts());
        assertTrue(!support.complete());
    }

    @Test
    void openClBackendAdvertisesCompletePromotionArtifactSupport() {
        net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend backend =
                new net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend();

        GpuPromotionArtifactSupport support = backend.promotionArtifactSupport();

        assertEquals(GpuBackendTarget.OPENCL, support.backendTarget());
        assertEquals(GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS, support.supportedArtifacts());
        assertTrue(support.missingArtifacts().isEmpty());
        assertTrue(support.complete());
    }

    private record ReportingBackend(GpuRuntimeBackendReport report) implements GpuRuntimeBackend {

        @Override
        public GpuBackendTarget backendTarget() {
            return report.backendTarget();
        }

        @Override
        public GpuRuntimeBackendReport describeCapabilities() {
            return report;
        }

        @Override
        public void invoke(GpuKernelInvocation invocation) {
        }
    }

    private static final class CloseCountingBackend implements GpuRuntimeBackend, AutoCloseable {
        private final GpuRuntimeBackendReport report;
        private int closeCalls;

        private CloseCountingBackend(GpuRuntimeBackendReport report) {
            this.report = report;
        }

        @Override
        public GpuBackendTarget backendTarget() {
            return report.backendTarget();
        }

        @Override
        public GpuRuntimeBackendReport describeCapabilities() {
            return report;
        }

        @Override
        public void invoke(GpuKernelInvocation invocation) {
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }

    private static GpuRuntimeBackendReport availableBackendReport(
            GpuBackendTarget backendTarget,
            String backendName,
            GpuRuntimeApiVersion apiVersion
    ) {
        return GpuRuntimeBackendReport.available(
                backendTarget,
                backendName,
                backendName + " metadata GPU",
                apiVersion,
                backendName + ' ' + apiVersion,
                java.util.EnumSet.noneOf(GpuRuntimeFeature.class),
                65_536L,
                1_024L,
                null
        );
    }

    private static GpuRuntimeBackendCatalogEntry metadataBackedCatalogEntry(
            GpuBackendTarget backendTarget,
            String backendName,
            CloseCountingBackend backend,
            GpuRuntimeBackendExecutionSupport executionSupport
    ) {
        return GpuRuntimeBackendCatalogEntry.owned(
                backendTarget,
                backendName,
                () -> backend,
                executionSupport.productionExecution(),
                executionSupport,
                "test metadata fixture"
        );
    }

    private static final class PreselectingBackend implements GpuRuntimeBackend, GpuRuntimeBackendDevicePreselector {
        private final GpuRuntimeBackendReport report;
        private GpuRuntimeDeviceDiscoveryResult preselectedDiscovery;

        private PreselectingBackend(GpuRuntimeBackendReport report) {
            this.report = report;
        }

        @Override
        public GpuBackendTarget backendTarget() {
            return report.backendTarget();
        }

        @Override
        public GpuRuntimeBackendReport describeCapabilities() {
            return report;
        }

        @Override
        public void preselectDevice(GpuRuntimeDeviceDiscoveryResult discoveryResult) {
            this.preselectedDiscovery = discoveryResult;
        }

        @Override
        public void invoke(GpuKernelInvocation invocation) {
        }
    }

    static final class FixtureOwner {
        private FixtureOwner() {
        }

        static void kernel(int[] output) {
        }
    }
}
