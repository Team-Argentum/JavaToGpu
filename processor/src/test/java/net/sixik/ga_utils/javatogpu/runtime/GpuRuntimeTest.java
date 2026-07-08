package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void executionConfigSupportsTwoDimensionalLaunches() {
        GpuExecutionConfig config = GpuExecutionConfig.twoDimensional(16L, 8L, 4L, 2L);

        assertEquals(2, config.dimensions());
        assertEquals(16L, config.globalX());
        assertEquals(8L, config.globalY());
        assertEquals(4L, config.localX());
        assertEquals(2L, config.localY());
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

    static final class FixtureOwner {
        private FixtureOwner() {
        }

        static void kernel(int[] output) {
        }
    }
}
