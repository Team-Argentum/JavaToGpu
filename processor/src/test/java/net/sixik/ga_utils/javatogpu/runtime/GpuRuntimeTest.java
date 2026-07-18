package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        assertTrue(result.failureSummary().contains("Runtime backend adapter is not implemented for CUDA"));
        assertTrue(result.explanation().toMarkdown().contains("CUDA: Runtime backend adapter is not implemented for CUDA"));
    }

    @Test
    void backendAdaptersExposeCatalogDiscoveryAndLowererContract() {
        List<GpuRuntimeBackendAdapter> adapters = GpuRuntimeBackendAdapters.standardWithPlannedBackends();
        GpuRuntimeBackendAdapter openClAdapter = GpuRuntimeBackendAdapters.requireTarget(GpuBackendTarget.OPENCL);
        GpuRuntimeBackendAdapter cudaAdapter = GpuRuntimeBackendAdapters.requireTarget(GpuBackendTarget.CUDA);

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
