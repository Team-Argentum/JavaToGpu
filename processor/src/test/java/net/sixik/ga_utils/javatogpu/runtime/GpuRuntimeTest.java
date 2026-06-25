package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeTest {

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
}
