package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.Image1DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DBufferReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DBufferWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DMipmappedReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DMipmappedWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Float2;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuStructFieldMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuStructMetadata;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuOptimizationStrategyDecision;
import net.sixik.ga_utils.javatogpu.runtime.GpuOptimizationVendorBaseline;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuProductionPromotionDecision;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceOverride;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelectionException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactSnapshot;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendUnavailableException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCapabilityException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCallSiteResolver;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptionsException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeEquivalenceEvidence;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeEquivalenceRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationPassReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizerRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeKernelCompilationException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeKernelExecutionException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpenClGpuRuntimeBackendTest {

    private static final String SIMPLE_IRGPU_SOURCE_RESOURCE =
            "javatogpu/runtime/opencl/integration/simple-irgpu-source-kernel.irgpu.properties";

    @Test
    void finalCompileSnapshotCarriesAvailableRuntimeDeviceSelection() {
        GpuRuntimeDeviceProfile profile = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-0",
                "Mock GPU",
                "Mock Vendor",
                "Mock Driver",
                "OpenCL 3.0 Mock",
                GpuDeviceClassTarget.DGPU,
                48L,
                8L * 1024L * 1024L * 1024L,
                65_536L,
                512L,
                1L,
                false,
                true,
                true,
                false
        );
        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                GpuRuntimeDevicePolicyContext.forBackendDiscovery(
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                        List.of(profile)
                )
        );
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
            @Override
            protected Optional<GpuRuntimeDeviceSelection> runtimeDeviceSelection() {
                return Optional.of(selection);
            }
        };

        backend.invoke(new GpuKernelInvocation(intOutputDescriptor(), new Object[]{new int[]{0}}));

        assertSame(selection, capturedSnapshot.get().deviceSelection().orElseThrow());
    }

    @Test
    void activeSessionRejectsRequestThatRequiresAnotherDevice() {
        GpuRuntimeDeviceProfile nvidia = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-0",
                "NVIDIA RTX",
                "NVIDIA",
                "driver",
                "OpenCL 3.0",
                GpuDeviceClassTarget.DGPU,
                48L,
                8L * 1024L * 1024L * 1024L,
                65_536L,
                512L,
                1L,
                false,
                true,
                true,
                false
        );
        GpuRuntimeDeviceProfile amd = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-1",
                "AMD Radeon",
                "AMD",
                "driver",
                "OpenCL 3.0",
                GpuDeviceClassTarget.DGPU,
                32L,
                8L * 1024L * 1024L * 1024L,
                65_536L,
                512L,
                1L,
                false,
                true,
                true,
                false
        );
        GpuRuntimeDeviceSelection activeSelection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                GpuRuntimeDevicePolicyContext.forBackendDiscovery(
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                        List.of(nvidia, amd)
                )
        );
        OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(new AtomicReference<>()) {
            @Override
            protected Optional<GpuRuntimeDeviceSelection> runtimeDeviceSelection() {
                return Optional.of(activeSelection);
            }
        };
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                .withDeviceOverride(GpuRuntimeDeviceOverride.byVendor("AMD"));

        GpuRuntimeDeviceSelectionException exception = assertThrows(
                GpuRuntimeDeviceSelectionException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        intOutputDescriptor(),
                        new Object[]{new int[]{0}},
                        options
                ))
        );

        assertTrue(exception.getMessage().contains("create a new runtime scope/backend instance"));
        assertEquals("AMD Radeon", exception.selection().selectedDevice().orElseThrow().deviceLabel());
    }

    @Test
    void irGpuArtifactIdentityIsStableAndChangesWithPayload() {
        IrGpuArtifact firstArtifact = testIrGpuArtifact("body\n  return output[0] + 1\n");
        IrGpuArtifact sameArtifact = testIrGpuArtifact("body\n  return output[0] + 1\n");
        IrGpuArtifact changedArtifact = testIrGpuArtifact("body\n  return output[0] + 2\n");

        assertEquals(IrGpuArtifactIdentity.stableHash(firstArtifact), IrGpuArtifactIdentity.stableHash(sameArtifact));
        assertEquals(IrGpuArtifactIdentity.stableIdentity(firstArtifact), IrGpuArtifactIdentity.stableIdentity(sameArtifact));
        assertNotEquals(IrGpuArtifactIdentity.stableHash(firstArtifact), IrGpuArtifactIdentity.stableHash(changedArtifact));
        assertNotEquals(IrGpuArtifactIdentity.stableIdentity(firstArtifact), IrGpuArtifactIdentity.stableIdentity(changedArtifact));
        assertTrue(IrGpuArtifactIdentity.stableIdentity(firstArtifact).startsWith("irgpu:sha256:"));
    }

    @Test
    void cachesCompiledKernelAcrossInvocations() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicInteger compileCalls = new AtomicInteger();
        AtomicInteger executeCalls = new AtomicInteger();
        AtomicReference<OpenClCompiledKernel> firstCompiledKernel = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                OpenClCompiledKernel compiledKernel = new OpenClCompiledKernel(
                        kernelDescriptor,
                        "compiled:" + compileCalls.incrementAndGet()
                );
                firstCompiledKernel.compareAndSet(null, compiledKernel);
                return compiledKernel;
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                executeCalls.incrementAndGet();
                assertSame(firstCompiledKernel.get(), execution.compiledKernel());
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        assertEquals(1, compileCalls.get());
        assertEquals(2, executeCalls.get());
        assertEquals(1, backend.cacheSize());
    }

    @Test
    void compileRequestCarriesDefaultOptionsAndDeviceProfile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicReference<GpuRuntimeCompileRequest> capturedRequest = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected GpuRuntimeDeviceProfile compileDeviceProfile() {
                return new GpuRuntimeDeviceProfile(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Mock GPU",
                        "Mock Vendor",
                        "Mock Driver",
                        "OpenCL 3.0 Mock"
                );
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                capturedRequest.set(compileRequest);
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        GpuRuntimeCompileRequest request = capturedRequest.get();
        assertSame(descriptor, request.descriptor());
        assertEquals(GpuBackendTarget.OPENCL, request.options().backendTarget());
        assertTrue(request.options().compileArgs().isEmpty());
        assertEquals("off", request.options().optimizationProfile());
        assertEquals("Mock GPU", request.deviceProfile().deviceLabel());
        assertEquals("Mock Vendor", request.deviceProfile().vendor());
        assertEquals("OpenCL 3.0 Mock", request.deviceProfile().apiVersionText());
    }

    @Test
    void compileCacheSeparatesVariantsByDeviceProfile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicReference<String> currentDeviceLabel = new AtomicReference<>("Mock GPU A");
        AtomicInteger compileCalls = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected GpuRuntimeDeviceProfile compileDeviceProfile() {
                return new GpuRuntimeDeviceProfile(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        currentDeviceLabel.get(),
                        "Mock Vendor",
                        "Mock Driver",
                        "OpenCL 3.0 Mock"
                );
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:" + compileCalls.incrementAndGet());
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        currentDeviceLabel.set("Mock GPU B");
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        assertEquals(2, compileCalls.get());
        assertEquals(2, backend.cacheSize());
    }

    @Test
    void compileCacheSeparatesVariantsByCompileOptions() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicInteger compileCalls = new AtomicInteger();
        AtomicReference<GpuRuntimeCompileRequest> lastCompileRequest = new AtomicReference<>();
        GpuRuntimeCompileOptions fastOptions = new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                java.util.List.of("-cl-fast-relaxed-math"),
                "fast"
        );
        GpuRuntimeCompileOptions preciseOptions = new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                java.util.List.of("-cl-opt-disable"),
                "precise"
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                lastCompileRequest.set(compileRequest);
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:" + compileCalls.incrementAndGet());
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, fastOptions));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, fastOptions));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, preciseOptions));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, preciseOptions));

        assertEquals(2, compileCalls.get());
        assertEquals(2, backend.cacheSize());
        assertEquals("precise", lastCompileRequest.get().options().optimizationProfile());
        assertEquals(java.util.List.of("-cl-opt-disable"), lastCompileRequest.get().options().compileArgs());
    }

    @Test
    void defaultCompilePathUsesBackendLowererBoundary() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicInteger lowerCalls = new AtomicInteger();
        AtomicInteger moduleCompileCalls = new AtomicInteger();
        AtomicInteger legacyCompileCalls = new AtomicInteger();
        GpuBackendModuleArtifact loweredArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* output) { output[0] = 2; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                lowerCalls.incrementAndGet();
                return loweredArtifact;
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuBackendModuleArtifact moduleArtifact,
                    GpuKernelDescriptor kernelDescriptor
            ) {
                moduleCompileCalls.incrementAndGet();
                assertSame(loweredArtifact, moduleArtifact);
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:lowered");
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                legacyCompileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:legacy");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                assertEquals("compiled:lowered", execution.compiledKernel().cacheKey());
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        assertEquals(1, lowerCalls.get());
        assertEquals(1, moduleCompileCalls.get());
        assertEquals(0, legacyCompileCalls.get());
    }

    @Test
    void compileCacheSeparatesVariantsByLoweredBackendModuleArtifact() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicInteger lowerVariant = new AtomicInteger(1);
        AtomicInteger compileCalls = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                int variant = lowerVariant.get();
                return GpuBackendModuleArtifact.openClSource(
                        "__kernel void kernel(__global int* output) { output[0] = " + variant + "; }",
                        "runtime/lowered/kernel-v" + variant + ".cl",
                        "test-lowerer-v" + variant
                );
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                return new OpenClCompiledKernel(
                        compileRequest.descriptor(),
                        moduleArtifact.resource() + ":" + compileCalls.incrementAndGet()
                );
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        lowerVariant.set(2);
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        assertEquals(2, compileCalls.get());
        assertEquals(2, backend.cacheSize());
    }

    @Test
    void compileCacheSeparatesVariantsByOptimizedIrGpuArtifactIdentity() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicInteger irVariant = new AtomicInteger(1);
        AtomicInteger compileCalls = new AtomicInteger();
        GpuBackendModuleArtifact loweredArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected GpuRuntimeCompileRequest optimizeRuntimeIr(GpuRuntimeCompileRequest compileRequest) {
                return compileRequest.withIrGpuArtifact(java.util.Optional.of(testIrGpuArtifact(
                        "body\n  return variant " + irVariant.get() + "\n"
                )));
            }

            @Override
            protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                return loweredArtifact;
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                return new OpenClCompiledKernel(
                        compileRequest.descriptor(),
                        moduleArtifact.resource() + ":" + compileCalls.incrementAndGet()
                );
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        irVariant.set(2);
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        assertEquals(2, compileCalls.get());
        assertEquals(2, backend.cacheSize());
    }

    @Test
    void compileCacheSeparatesVariantsByBackendArtifactVersion() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicInteger artifactVersion = new AtomicInteger(1);
        AtomicInteger compileCalls = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                int version = artifactVersion.get();
                return new GpuBackendModuleArtifact(
                        GpuBackendTarget.OPENCL,
                        "source",
                        "opencl-c",
                        "__kernel void kernel(__global int* output) { output[0] = 1; }",
                        "runtime/lowered/kernel.cl",
                        "opencl:source:opencl-c:v" + version,
                        "test-lowerer"
                );
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                return new OpenClCompiledKernel(
                        compileRequest.descriptor(),
                        moduleArtifact.artifactVersion() + ":" + compileCalls.incrementAndGet()
                );
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        artifactVersion.set(2);
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        assertEquals(2, compileCalls.get());
        assertEquals(2, backend.cacheSize());
    }

    @Test
    void compileCacheSeparatesVariantsByOptimizerPipelineVersion() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicReference<String> optimizerVersion = new AtomicReference<>("optimizer:test-v1");
        AtomicInteger compileCalls = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected String optimizerPipelineVersion() {
                return optimizerVersion.get();
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:" + compileCalls.incrementAndGet());
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        optimizerVersion.set("optimizer:test-v2");
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        assertEquals(2, compileCalls.get());
        assertEquals(2, backend.cacheSize());
    }

    @Test
    void rejectsUnsupportedCompileOptionBeforeRuntimeCapabilityLookup() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        GpuRuntimeCompileOptions compileOptions = new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                java.util.List.of("--cuda-fast-math"),
                "diagnostic"
        );
        AtomicInteger capabilityLookups = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                capabilityLookups.incrementAndGet();
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        GpuRuntimeCompileOptionsException exception = assertThrows(
                GpuRuntimeCompileOptionsException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, compileOptions))
        );

        assertEquals(0, capabilityLookups.get());
        assertTrue(exception.getMessage().contains("Unsupported OpenCL compile option"));
        assertTrue(exception.getMessage().contains("--cuda-fast-math"));
    }

    @Test
    void rejectsUnsupportedOpenClSourceSelectionBeforeRuntimeCapabilityLookup() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        GpuRuntimeCompileOptions compileOptions = new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                java.util.List.of(),
                "diagnostic",
                GpuBackendCompileOptions.openCl(
                        java.util.List.of(),
                        java.util.Map.of(GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_PROPERTY, "compiled-binary")
                )
        );
        AtomicInteger capabilityLookups = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                capabilityLookups.incrementAndGet();
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        GpuRuntimeCompileOptionsException exception = assertThrows(
                GpuRuntimeCompileOptionsException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, compileOptions))
        );

        assertEquals(0, capabilityLookups.get());
        assertTrue(exception.getMessage().contains("Unsupported OpenCL source selection compile option"));
        assertTrue(exception.getMessage().contains("compiled-binary"));
        assertTrue(exception.getMessage().contains(GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_DESCRIPTOR));
        assertTrue(exception.getMessage().contains(GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_IRGPU));
    }

    @Test
    void rejectsUnsupportedOpenClProductionSourceSwitchingBeforeRuntimeCapabilityLookup() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        GpuRuntimeCompileOptions compileOptions = new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                java.util.List.of(),
                "diagnostic",
                GpuBackendCompileOptions.openCl(
                        java.util.List.of(),
                        java.util.Map.of(
                                GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_PROPERTY,
                                "auto"
                        )
                )
        );
        AtomicInteger capabilityLookups = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                capabilityLookups.incrementAndGet();
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        GpuRuntimeCompileOptionsException exception = assertThrows(
                GpuRuntimeCompileOptionsException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, compileOptions))
        );

        assertEquals(0, capabilityLookups.get());
        assertTrue(exception.getMessage().contains("Unsupported OpenCL production source switching compile option"));
        assertTrue(exception.getMessage().contains("auto"));
        assertTrue(exception.getMessage().contains(GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_DISABLED));
        assertTrue(exception.getMessage().contains(GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_ENABLED));
    }

    @Test
    void rejectsCompileOptionsForOtherBackendBeforeRuntimeCapabilityLookup() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        GpuRuntimeCompileOptions compileOptions = new GpuRuntimeCompileOptions(
                GpuBackendTarget.CUDA,
                java.util.List.of(),
                "diagnostic"
        );
        AtomicInteger capabilityLookups = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                capabilityLookups.incrementAndGet();
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        GpuRuntimeCompileOptionsException exception = assertThrows(
                GpuRuntimeCompileOptionsException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, compileOptions))
        );

        assertEquals(0, capabilityLookups.get());
        assertTrue(exception.getMessage().contains("OpenCL backend cannot use compile options for backend CUDA"));
    }

    @Test
    void runtimeIrOptimizerRunsBeforeLegacyKernelCompileHook() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicInteger optimizerCalls = new AtomicInteger();
        AtomicInteger legacyCompileCalls = new AtomicInteger();
        AtomicReference<GpuRuntimeCompileRequest> optimizerRequest = new AtomicReference<>();

        GpuRuntimeIrOptimizerRegistry optimizerRegistry = GpuRuntimeIrOptimizerRegistry.of(java.util.List.of(request -> {
            optimizerCalls.incrementAndGet();
            optimizerRequest.set(request.compileRequest());
            return request.artifact();
        }));

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend(
                OpenClGpuRuntimeBackend.CacheMode.INSTANCE,
                optimizerRegistry
        ) {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                legacyCompileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        assertEquals(2, optimizerCalls.get());
        assertEquals(1, legacyCompileCalls.get());
        assertSame(descriptor, optimizerRequest.get().descriptor());
        assertEquals(GpuBackendTarget.OPENCL, optimizerRequest.get().options().backendTarget());
        assertEquals("off", optimizerRequest.get().options().optimizationProfile());
    }

    @Test
    void runtimeIrOptimizerReceivesClasspathIrGpuArtifact() throws Exception {
        Path classpathRoot = Files.createTempDirectory("javatogpu-irgpu-runtime-resource");
        Path artifactPath = classpathRoot.resolve("javatogpu/sample/Demo/kernel.irgpu.properties");
        Files.createDirectories(artifactPath.getParent());
        Files.writeString(artifactPath, """
                # JavaToGpu backend-neutral IR artifact manifest
                backendOutput.0.backend=opencl
                backendOutput.0.format=opencl-c
                backendOutput.0.kind=source
                backendOutput.0.resource=javatogpu/sample/Demo/kernel.cl
                backendOutput.count=1
                compilerArtifact=JavaToGpu
                derived.opencl.resource=javatogpu/sample/Demo/kernel.cl
                entryEmittedName=jtg_kernel
                entryMethod=kernel
                format=javatogpu.irgpu.v1
                helper.count=0
                methodBody.0.body=method jtg_kernel source\\=kernel\\nhelpers -\\nbody\\n  return\\n
                methodBody.0.emittedName=jtg_kernel
                methodBody.0.format=ir-text-v1
                methodBody.0.helperDependency.count=0
                methodBody.0.name=kernel
                methodBody.0.role=entry
                methodBody.0.source.beginColumn=17
                methodBody.0.source.beginLine=4
                methodBody.0.source.endColumn=5
                methodBody.0.source.endLine=7
                methodBody.0.source.kind=java-source
                methodBody.0.source.methodName=kernel
                methodBody.0.source.ownerQualifiedName=sample.Demo
                methodBody.count=1
                runtime.defaultBackend=opencl
                runtime.optimizationProfile=off
                schemaVersion=1
                sourceFrontend=java-source
                struct.count=0
                """);

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicReference<IrGpuArtifact> capturedArtifact = new AtomicReference<>();
        AtomicReference<IrGpuArtifact> lowererArtifact = new AtomicReference<>();

        GpuRuntimeIrOptimizerRegistry optimizerRegistry = GpuRuntimeIrOptimizerRegistry.of(java.util.List.of(request -> {
            request.artifact().ifPresent(capturedArtifact::set);
            return request.artifact();
        }));

        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classpathRoot.toUri().toURL()}, previousClassLoader)) {
            Thread.currentThread().setContextClassLoader(classLoader);
            OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend(
                    OpenClGpuRuntimeBackend.CacheMode.INSTANCE,
                    optimizerRegistry
            ) {
                @Override
                protected OpenClRuntimeCapabilities runtimeCapabilities() {
                    return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
                }

                @Override
                protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                    return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
                }

                @Override
                protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                    compileRequest.irGpuArtifact().ifPresent(lowererArtifact::set);
                    return super.lowerBackendModule(compileRequest);
                }

                @Override
                protected void executeKernel(OpenClPreparedExecution execution) {
                    // no-op
                }
            };

            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }

        IrGpuArtifact artifact = capturedArtifact.get();
        assertEquals("javatogpu.irgpu.v1", artifact.header().format());
        assertEquals("kernel", artifact.module().entryMethod());
        assertEquals("jtg_kernel", artifact.module().entryEmittedName());
        assertEquals(1, artifact.module().methodBodies().size());
        assertEquals("entry", artifact.module().methodBodies().get(0).role());
        assertEquals("ir-text-v1", artifact.module().methodBodies().get(0).format());
        assertTrue(artifact.module().methodBodies().get(0).body().contains("method jtg_kernel source=kernel"));
        assertSame(artifact, lowererArtifact.get());
        assertEquals(1, artifact.backendOutputs().size());
        assertEquals("opencl", artifact.backendOutputs().get(0).backend());
        assertEquals("javatogpu/sample/Demo/kernel.cl", artifact.derivedOpenClResource());
    }

    @Test
    void compiledKernelCarriesRuntimeArtifactSnapshot() throws Exception {
        Path classpathRoot = Files.createTempDirectory("javatogpu-irgpu-snapshot-resource");
        Path artifactPath = classpathRoot.resolve("javatogpu/sample/Demo/kernel.irgpu.properties");
        Files.createDirectories(artifactPath.getParent());
        Files.writeString(artifactPath, """
                # JavaToGpu backend-neutral IR artifact manifest
                backendOutput.0.backend=opencl
                backendOutput.0.format=opencl-c
                backendOutput.0.kind=source
                backendOutput.0.resource=javatogpu/sample/Demo/kernel.cl
                backendOutput.count=1
                compilerArtifact=JavaToGpu
                derived.opencl.resource=javatogpu/sample/Demo/kernel.cl
                entryEmittedName=jtg_kernel
                entryMethod=kernel
                format=javatogpu.irgpu.v1
                helper.count=0
                methodBody.0.body=method jtg_kernel source\\=kernel\\nhelpers -\\nbody\\n  return original\\n
                methodBody.0.emittedName=jtg_kernel
                methodBody.0.format=ir-text-v1
                methodBody.0.helperDependency.count=0
                methodBody.0.name=kernel
                methodBody.0.role=entry
                methodBody.0.source.beginColumn=17
                methodBody.0.source.beginLine=4
                methodBody.0.source.endColumn=5
                methodBody.0.source.endLine=7
                methodBody.0.source.kind=java-source
                methodBody.0.source.methodName=kernel
                methodBody.0.source.ownerQualifiedName=sample.Demo
                methodBody.count=1
                runtime.defaultBackend=opencl
                runtime.optimizationProfile=off
                schemaVersion=1
                sourceFrontend=java-source
                struct.count=0
                """);

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        IrGpuArtifact optimizedArtifact = testIrGpuArtifact("body\n  return optimized\n");
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        GpuBackendModuleArtifact loweredArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* output) { output[0] = 2; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );

        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classpathRoot.toUri().toURL()}, previousClassLoader)) {
            Thread.currentThread().setContextClassLoader(classLoader);
            OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
                @Override
                protected OpenClRuntimeCapabilities runtimeCapabilities() {
                    return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
                }

                @Override
                protected GpuRuntimeCompileRequest optimizeRuntimeIr(GpuRuntimeCompileRequest compileRequest) {
                    assertTrue(compileRequest.irGpuArtifact().orElseThrow()
                            .module()
                            .methodBodies()
                            .get(0)
                            .body()
                            .contains("return original"));
                    return compileRequest.withIrGpuArtifact(java.util.Optional.of(optimizedArtifact));
                }

                @Override
                protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                    assertSame(optimizedArtifact, compileRequest.irGpuArtifact().orElseThrow());
                    return loweredArtifact;
                }

                @Override
                protected OpenClCompiledKernel compileKernel(
                        GpuRuntimeCompileRequest compileRequest,
                        GpuBackendModuleArtifact moduleArtifact
                ) {
                    return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:test");
                }

                @Override
                protected void executeKernel(OpenClPreparedExecution execution) {
                    capturedSnapshot.set(execution.compiledKernel().artifactSnapshot());
                }
            };

            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertTrue(snapshot.originalIrGpuArtifact().orElseThrow()
                .module()
                .methodBodies()
                .get(0)
                .body()
                .contains("return original"));
        assertSame(optimizedArtifact, snapshot.optimizedIrGpuArtifact().orElseThrow());
        assertSame(loweredArtifact, snapshot.backendModuleArtifact());
        assertEquals("javatogpu.irgpu.v1", snapshot.invalidationStamp().irFormat());
        assertEquals(1, snapshot.invalidationStamp().irSchemaVersion());
        assertEquals("JavaToGpu", snapshot.invalidationStamp().compilerArtifact());
        assertEquals("java-source", snapshot.invalidationStamp().sourceFrontend());
        assertEquals(loweredArtifact.artifactVersion(), snapshot.invalidationStamp().backendArtifactVersion());
        assertEquals(loweredArtifact.lowererVersion(), snapshot.invalidationStamp().backendLowererVersion());
        assertEquals(GpuBackendTarget.OPENCL, snapshot.compileProvenance().backendTarget());
        assertEquals("off", snapshot.compileProvenance().optimizationProfile());
        assertTrue(snapshot.compileProvenance().compileArgs().isEmpty());
        assertEquals("none", snapshot.compileProvenance().fallbackDecision());
        assertTrue(snapshot.backendSourcePromotionGate().isPresent());
        assertEquals("blocked", snapshot.backendSourcePromotionGate().orElseThrow().status());
        assertTrue(snapshot.backendSourceSwitchingDecision().isPresent());
        assertEquals("descriptor-default", snapshot.backendSourceSwitchingDecision().orElseThrow().status());
        assertEquals("compile-descriptor-source", snapshot.backendSourceSwitchingDecision().orElseThrow().decision());
        assertFalse(snapshot.backendSourceSwitchingDecision().orElseThrow().irGpuSourceRequested());
        assertEquals(1, snapshot.sourceLocations().size());
        assertEquals("java-source", snapshot.sourceLocations().get(0).sourceKind());
        assertEquals("kernel", snapshot.sourceLocations().get(0).methodName());
        assertTrue(snapshot.compileLog().isBlank());
        assertTrue(snapshot.runtimeValidationEvidence().isEmpty());
    }

    @Test
    void runtimeEquivalenceDefaultsToNotRunWhenBackendDoesNotEnablePrePostExecution() {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot);

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals("not-run", snapshot.runtimeEquivalenceEvidence().status());
        assertFalse(snapshot.runtimeEquivalenceEvidence().executed());
        assertEquals("none", snapshot.fallbackEvidence().decision());
        assertEquals("not-requested", snapshot.productionOptimizerGate().status());
    }

    @Test
    void runtimeEquivalenceHookCanPersistPassedEvidenceForOptimizedIr() {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        AtomicReference<GpuRuntimeEquivalenceRequest> capturedRequest = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
            @Override
            protected GpuRuntimeEquivalenceEvidence executeRuntimeEquivalence(GpuRuntimeEquivalenceRequest request) {
                capturedRequest.set(request);
                return GpuRuntimeEquivalenceEvidence.passed(
                        request.optimizedCompileRequest(),
                        3,
                        1,
                        List.of("deterministic pre/post outputs matched")
                );
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertSame(descriptor, capturedRequest.get().originalCompileRequest().descriptor());
        assertSame(descriptor, capturedRequest.get().optimizedCompileRequest().descriptor());
        assertTrue(capturedRequest.get().hasInvocationContext());
        assertNotSame(capturedRequest.get().invocationArguments(), capturedRequest.get().invocationArguments());
        assertEquals(1, capturedRequest.get().invocationArguments().length);
        assertFalse(capturedRequest.get().hasOptimizedTransform());
        assertEquals("passed", snapshot.runtimeEquivalenceEvidence().status(), snapshot.runtimeEquivalenceEvidence().diagnostics().toString());
        assertTrue(snapshot.runtimeEquivalenceEvidence().executed());
        assertTrue(snapshot.runtimeEquivalenceEvidence().equivalent());
        assertEquals(3, snapshot.runtimeEquivalenceEvidence().inputCaseCount());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().comparedOutputCount());
        assertEquals("none", snapshot.fallbackEvidence().decision());
    }

    @Test
    void runtimeEquivalencePreflightRunsIsolatedPrimitiveArrayComparison() {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        IrGpuArtifact artifact = parityMatchedIrGpuArtifact();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        ArrayList<String> compiledResources = new ArrayList<>();
        ArrayList<String> compiledSources = new ArrayList<>();
        int[] productionOutput = new int[]{0};

        OpenClGpuRuntimeBackend backend = new EquivalenceSimulatingBackend(capturedSnapshot, 7, 7) {
            @Override
            protected GpuRuntimeCompileRequest optimizeRuntimeIr(GpuRuntimeCompileRequest compileRequest) {
                return compileRequest.withIrGpuArtifact(java.util.Optional.of(artifact));
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                compiledResources.add(moduleArtifact.resource());
                compiledSources.add(moduleArtifact.source());
                return new OpenClCompiledKernel(compileRequest.descriptor(), moduleArtifact.resource());
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{productionOutput}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals("passed", snapshot.runtimeEquivalenceEvidence().status(), snapshot.runtimeEquivalenceEvidence().diagnostics().toString());
        assertTrue(snapshot.runtimeEquivalenceEvidence().executed());
        assertTrue(snapshot.runtimeEquivalenceEvidence().equivalent());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().inputCaseCount());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().comparedOutputCount());
        assertTrue(snapshot.runtimeEquivalenceEvidence().diagnostics().contains(
                "descriptor and reconstructed OpenCL outputs matched for isolated array runtime-equivalence"
        ));
        assertEquals(3, compiledResources.size());
        assertEquals("javatogpu/sample/Demo/kernel.cl", compiledResources.get(0));
        assertTrue(compiledResources.get(1).contains("#irgpu-reconstructed-equivalence-preflight"));
        assertEquals("javatogpu/sample/Demo/kernel.cl", compiledResources.get(2));
        assertEquals(descriptor.kernelSource(), compiledSources.get(0));
        assertEquals(canonicalOpenClSource(descriptor.kernelSource()), canonicalOpenClSource(compiledSources.get(1)));
        assertEquals(descriptor.kernelSource(), compiledSources.get(2));
        assertArrayEquals(new int[]{1}, productionOutput);
    }

    @Test
    void runtimeEquivalenceFailsWhenIsolatedPrimitiveArrayOutputsDiffer() {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        IrGpuArtifact artifact = parityMatchedIrGpuArtifact();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        ArrayList<String> compiledResources = new ArrayList<>();

        OpenClGpuRuntimeBackend backend = new EquivalenceSimulatingBackend(capturedSnapshot, 7, 9) {
            @Override
            protected GpuRuntimeCompileRequest optimizeRuntimeIr(GpuRuntimeCompileRequest compileRequest) {
                return compileRequest.withIrGpuArtifact(java.util.Optional.of(artifact));
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                compiledResources.add(moduleArtifact.resource());
                return new OpenClCompiledKernel(compileRequest.descriptor(), moduleArtifact.resource());
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals("failed", snapshot.runtimeEquivalenceEvidence().status());
        assertTrue(snapshot.runtimeEquivalenceEvidence().executed());
        assertFalse(snapshot.runtimeEquivalenceEvidence().equivalent());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().inputCaseCount());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().comparedOutputCount());
        assertTrue(snapshot.runtimeEquivalenceEvidence().diagnostics().contains(
                "runtime-equivalence output mismatch for parameter 'output' at argument 0"
        ));
        assertEquals("runtime-equivalence-failed", snapshot.fallbackEvidence().decision());
        assertTrue(compiledResources.get(1).contains("#irgpu-reconstructed-equivalence-preflight"));
    }

    @Test
    void runtimeEquivalenceSkipsUnsupportedPrimitiveArrayComparisonWithoutProductionMutation() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* input) { return; }",
                java.util.List.of(new GpuKernelParameterDescriptor("input", "int[]", GpuKernelParameterAccess.READ_ONLY))
        );
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "kernel",
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "kernel",
                                "body\n  return\n",
                                java.util.List.of()
                        ))
                ),
                java.util.List.of(new net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter(
                        "input",
                        "int[]",
                        "GLOBAL",
                        false,
                        java.util.List.of()
                )),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.backendNeutralReady(),
                java.util.List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        int[] input = new int[]{3};

        OpenClGpuRuntimeBackend backend = new EquivalenceSimulatingBackend(capturedSnapshot, 7, 7) {
            @Override
            protected GpuRuntimeCompileRequest optimizeRuntimeIr(GpuRuntimeCompileRequest compileRequest) {
                return compileRequest.withIrGpuArtifact(java.util.Optional.of(artifact));
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals("not-run", snapshot.runtimeEquivalenceEvidence().status());
        assertFalse(snapshot.runtimeEquivalenceEvidence().executed());
        assertTrue(snapshot.runtimeEquivalenceEvidence().diagnostics().contains(
                "runtime-equivalence output comparison requires at least one READ_WRITE array output"
        ));
        assertArrayEquals(new int[]{3}, input);
    }

    @Test
    void runtimeEquivalenceComparesVectorArrayOutputsThroughPackedAbiBytes() {
        GpuKernelDescriptor descriptor = float2OutputDescriptor();
        IrGpuArtifact artifact = parityMatchedFloat2IrGpuArtifact();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        Float2[] output = new Float2[]{new Float2()};

        OpenClGpuRuntimeBackend backend = new EquivalenceSimulatingBackend(capturedSnapshot, 7, 7) {
            @Override
            protected GpuRuntimeCompileRequest optimizeRuntimeIr(GpuRuntimeCompileRequest compileRequest) {
                return compileRequest.withIrGpuArtifact(java.util.Optional.of(artifact));
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{output}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals(
                "passed",
                snapshot.runtimeEquivalenceEvidence().status(),
                snapshot.runtimeEquivalenceEvidence().diagnostics().toString()
        );
        assertTrue(snapshot.runtimeEquivalenceEvidence().executed());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().comparedOutputCount());
        assertEquals(1.0f, output[0].x);
        assertEquals(2.0f, output[0].y);
    }

    @Test
    void runtimeEquivalenceComparesStructArrayOutputsThroughPackedAbiBytes() {
        GpuKernelDescriptor descriptor = structOutputDescriptor();
        IrGpuArtifact artifact = parityMatchedStructIrGpuArtifact();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        EquivalenceStructSample[] output = new EquivalenceStructSample[]{new EquivalenceStructSample()};

        OpenClGpuRuntimeBackend backend = new EquivalenceSimulatingBackend(capturedSnapshot, 7, 7) {
            @Override
            protected GpuRuntimeCompileRequest optimizeRuntimeIr(GpuRuntimeCompileRequest compileRequest) {
                return compileRequest.withIrGpuArtifact(java.util.Optional.of(artifact));
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{output}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals(
                "passed",
                snapshot.runtimeEquivalenceEvidence().status(),
                snapshot.runtimeEquivalenceEvidence().diagnostics().toString()
        );
        assertTrue(snapshot.runtimeEquivalenceEvidence().executed());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().comparedOutputCount());
        assertEquals(11, output[0].x);
        assertEquals(12.5f, output[0].y);
    }

    @Test
    void writesWorkloadSourcePromotionGateFromRuntimeSnapshotWhenConfigured() throws Exception {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        Path gateFile = Files.createTempFile("javatogpu-opencl-workload-source-promotion", ".properties");
        Files.deleteIfExists(gateFile);
        String previousGateFile = System.getProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
        try {
            System.setProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", gateFile.toString());

            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot);

            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

            String gateProperties = Files.readString(gateFile);
            assertEquals("javatogpu/sample/Demo/kernel.cl", capturedSnapshot.get().backendModuleArtifact().resource());
            assertTrue(gateProperties.contains("status=blocked"));
            assertTrue(gateProperties.contains("reviewReady=false"));
            assertTrue(gateProperties.contains("runtimeEquivalencePassed=false"));
            assertTrue(gateProperties.contains("scope=real-workload"));
            assertTrue(gateProperties.contains("productionSourceSwitching=false"));
            assertTrue(gateProperties.contains("realWorkloadEvidence=runtime-snapshot"));
            assertTrue(gateProperties.contains("kernel.count=1"));
            assertTrue(gateProperties.contains("kernel.0.sourceKernelResource=javatogpu/sample/Demo/kernel.cl"));
            assertTrue(gateProperties.contains("kernel.0.status=blocked"));
            assertTrue(gateProperties.contains("kernel.0.runtimeOptimizerDrift.status=recorded"));
            assertTrue(gateProperties.contains("kernel.0.runtimeOptimizerDrift.pass.count=0"));
            assertTrue(gateProperties.contains("kernel.0.runtimeOptimizerDrift.proofArtifact.count=0"));
            assertTrue(gateProperties.contains("kernel.0.diagnostic.count=5"));
            assertTrue(gateProperties.contains("kernel.0.diagnostic.0=backend source must be reconstructed from IrGpu before promotion review"));
            assertTrue(gateProperties.contains("blockerFamily.0.name=reconstruction"));
            assertTrue(gateProperties.contains("blockerFamily.0.count=2"));
            assertTrue(gateProperties.contains("blockerFamily.1.name=source-parity"));
            assertTrue(gateProperties.contains("blockerFamily.2.name=runtime-equivalence"));
            assertTrue(gateProperties.contains("kernel.0.blockerFamily.0.name=reconstruction"));
        } finally {
            if (previousGateFile == null) {
                System.clearProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
            } else {
                System.setProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", previousGateFile);
            }
        }
    }

    @Test
    void aggregatesWorkloadSourcePromotionGateAcrossKernelResources() throws Exception {
        GpuKernelDescriptor firstDescriptor = intOutputDescriptor();
        GpuKernelDescriptor secondDescriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/other-kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 2; }",
                java.util.List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        Path gateFile = Files.createTempFile("javatogpu-opencl-workload-source-promotion-aggregate", ".properties");
        Files.deleteIfExists(gateFile);
        String previousGateFile = System.getProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
        try {
            System.setProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", gateFile.toString());

            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot);

            backend.invoke(new GpuKernelInvocation(firstDescriptor, new Object[]{new int[]{0}}));
            backend.invoke(new GpuKernelInvocation(secondDescriptor, new Object[]{new int[]{0}}));

            String gateProperties = Files.readString(gateFile);
            assertTrue(gateProperties.contains("kernel.count=2"));
            assertTrue(gateProperties.contains("kernel.0.sourceKernelResource=javatogpu/sample/Demo/kernel.cl"));
            assertTrue(gateProperties.contains("kernel.1.sourceKernelResource=javatogpu/sample/Demo/other-kernel.cl"));
            assertTrue(gateProperties.contains("kernel.0.realWorkloadEvidence=runtime-snapshot"));
            assertTrue(gateProperties.contains("kernel.1.realWorkloadEvidence=runtime-snapshot"));
            assertTrue(gateProperties.contains("kernel.0.runtimeOptimizerDrift.status=recorded"));
            assertTrue(gateProperties.contains("kernel.1.runtimeOptimizerDrift.status=recorded"));
            assertTrue(gateProperties.contains("kernel.0.diagnostic.0=backend source must be reconstructed from IrGpu before promotion review"));
            assertTrue(gateProperties.contains("kernel.1.diagnostic.0=backend source must be reconstructed from IrGpu before promotion review"));
            assertTrue(gateProperties.contains("blockerFamily.0.name=reconstruction"));
            assertTrue(gateProperties.contains("blockerFamily.0.count=4"));
            assertTrue(gateProperties.contains("kernel.1.blockerFamily.2.name=runtime-equivalence"));
            assertTrue(gateProperties.contains("productionSourceSwitching=false"));
        } finally {
            if (previousGateFile == null) {
                System.clearProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
            } else {
                System.setProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", previousGateFile);
            }
        }
    }

    @Test
    void runtimeEquivalenceFailureRejectsOptimizedIrInSnapshotArtifacts() {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
            @Override
            protected GpuRuntimeEquivalenceEvidence executeRuntimeEquivalence(GpuRuntimeEquivalenceRequest request) {
                return GpuRuntimeEquivalenceEvidence.failed(
                        request.optimizedCompileRequest(),
                        2,
                        1,
                        List.of("case 1 output differs")
                );
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals("failed", snapshot.runtimeEquivalenceEvidence().status());
        assertTrue(snapshot.runtimeEquivalenceEvidence().executed());
        assertFalse(snapshot.runtimeEquivalenceEvidence().equivalent());
        assertEquals("runtime-equivalence-failed", snapshot.fallbackEvidence().decision());
        assertTrue(snapshot.fallbackEvidence().originalIrSelected());
        assertTrue(snapshot.fallbackEvidence().optimizedIrRejected());
        assertEquals("runtime-equivalence-failed", snapshot.compileProvenance().fallbackDecision());
        assertEquals("missing", snapshot.runtimeIrSelection().selectedStage());
        assertTrue(snapshot.runtimeIrSelection().optimizedRejected());
        assertEquals("runtime-equivalence-failed", snapshot.runtimeIrSelection().fallbackDecision());
    }

    @Test
    void runtimeEquivalenceFailureCompilesOriginalIrAfterSelectionFallback() throws Exception {
        Path classpathRoot = Files.createTempDirectory("javatogpu-irgpu-fallback-resource");
        Path artifactPath = classpathRoot.resolve("javatogpu/sample/Demo/kernel.irgpu.properties");
        Files.createDirectories(artifactPath.getParent());
        Files.writeString(artifactPath, """
                # JavaToGpu backend-neutral IR artifact manifest
                backendOutput.0.backend=opencl
                backendOutput.0.format=opencl-c
                backendOutput.0.kind=source
                backendOutput.0.resource=javatogpu/sample/Demo/kernel.cl
                backendOutput.count=1
                compilerArtifact=JavaToGpu
                derived.opencl.resource=javatogpu/sample/Demo/kernel.cl
                entryEmittedName=jtg_kernel
                entryMethod=kernel
                format=javatogpu.irgpu.v1
                helper.count=0
                methodBody.0.body=method jtg_kernel source\\=kernel\\nhelpers -\\nbody\\n  return original\\n
                methodBody.0.emittedName=jtg_kernel
                methodBody.0.format=ir-text-v1
                methodBody.0.helperDependency.count=0
                methodBody.0.name=kernel
                methodBody.0.role=entry
                methodBody.count=1
                runtime.defaultBackend=opencl
                runtime.optimizationProfile=off
                schemaVersion=1
                sourceFrontend=java-source
                struct.count=0
                """);
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
                java.util.List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        AtomicReference<GpuRuntimeCompileRequest> finalCompileRequest = new AtomicReference<>();

        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classpathRoot.toUri().toURL()}, previousClassLoader)) {
            Thread.currentThread().setContextClassLoader(classLoader);
            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
                @Override
                protected GpuRuntimeIrOptimizationResult optimizeRuntimeIrWithReport(GpuRuntimeCompileRequest compileRequest) {
                    IrGpuArtifact optimizedArtifact = testIrGpuArtifact("optimized runtime body\n  return output[0] + 42\n");
                    GpuRuntimeCompileRequest optimizedRequest = compileRequest.withIrGpuArtifact(java.util.Optional.of(optimizedArtifact));
                    String originalIdentity = IrGpuArtifactIdentity.stableIdentity(compileRequest.irGpuArtifact());
                    String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(optimizedRequest.irGpuArtifact());
                    return new GpuRuntimeIrOptimizationResult(
                            optimizedRequest,
                            new net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationReport(
                                    java.util.Optional.of(optimizedArtifact),
                                    List.of(net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationPassReport.applied(
                                            "test-runtime-optimizer",
                                            originalIdentity,
                                            optimizedIdentity,
                                            "test-proof",
                                            List.of("test optimizer supplied transformed IR")
                                    ))
                            )
                    );
                }

                @Override
                protected GpuRuntimeEquivalenceEvidence executeRuntimeEquivalence(GpuRuntimeEquivalenceRequest request) {
                    return GpuRuntimeEquivalenceEvidence.failed(
                            request.optimizedCompileRequest(),
                            2,
                            1,
                            List.of("case 1 output differs")
                    );
                }

                @Override
                protected OpenClCompiledKernel compileKernel(
                        GpuRuntimeCompileRequest compileRequest,
                        GpuBackendModuleArtifact moduleArtifact
                ) {
                    finalCompileRequest.set(compileRequest);
                    return super.compileKernel(compileRequest, moduleArtifact);
                }
            };

            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        String originalIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.originalIrGpuArtifact());
        String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.optimizedIrGpuArtifact());
        String finalCompileIdentity = IrGpuArtifactIdentity.stableIdentity(finalCompileRequest.get().irGpuArtifact());
        assertNotEquals(originalIdentity, optimizedIdentity);
        assertEquals(originalIdentity, finalCompileIdentity);
        assertEquals("runtime-equivalence-failed", snapshot.compileProvenance().fallbackDecision());
        assertTrue(snapshot.fallbackEvidence().originalIrSelected());
        assertEquals("original", snapshot.runtimeIrSelection().selectedStage());
        assertEquals(originalIdentity, snapshot.runtimeIrSelection().selectedIdentity());
        assertTrue(snapshot.runtimeIrSelection().optimizedRejected());
    }

    @Test
    void blockedProductionGateCompilesOriginalIrAfterSelectionFallback() throws Exception {
        Path classpathRoot = Files.createTempDirectory("javatogpu-production-gate-blocked-resource");
        Path artifactPath = classpathRoot.resolve("javatogpu/sample/Demo/kernel.irgpu.properties");
        Files.createDirectories(artifactPath.getParent());
        Files.writeString(artifactPath, irGpuArtifactProperties("return original"));
        GpuKernelDescriptor descriptor = descriptorWithIrGpuResource();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        AtomicReference<GpuRuntimeCompileRequest> finalCompileRequest = new AtomicReference<>();

        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classpathRoot.toUri().toURL()}, previousClassLoader)) {
            Thread.currentThread().setContextClassLoader(classLoader);
            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
                @Override
                protected GpuRuntimeIrOptimizationResult optimizeRuntimeIrWithReport(GpuRuntimeCompileRequest compileRequest) {
                    return optimizedRuntimeResult(compileRequest, "production blocked optimized body\n  return output[0] + 42\n", GpuOptimizationStrategyDecision.advisory(
                            "strategy:blocked-production-fixture",
                            "test-vendor",
                            "vendor-tuned",
                            "blocked production fixture remains advisory",
                            GpuOptimizationVendorBaseline.missing("test-vendor"),
                            List.of("production fixture is intentionally blocked")
                    ));
                }

                @Override
                protected GpuRuntimeEquivalenceEvidence executeRuntimeEquivalence(GpuRuntimeEquivalenceRequest request) {
                    return GpuRuntimeEquivalenceEvidence.passed(
                            request.optimizedCompileRequest(),
                            2,
                            2,
                            List.of("blocked production fixture remains equivalent")
                    );
                }

                @Override
                protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                    return GpuBackendModuleArtifact.openClSource(
                            "__kernel void kernel(__global int* output) { output[0] = 2; }",
                            "runtime/lowered/" + IrGpuArtifactIdentity.stableHash(compileRequest.irGpuArtifact().orElseThrow()) + ".cl",
                            "test-lowerer-v1"
                    );
                }

                @Override
                protected OpenClCompiledKernel compileKernel(
                        GpuRuntimeCompileRequest compileRequest,
                        GpuBackendModuleArtifact moduleArtifact
                ) {
                    finalCompileRequest.set(compileRequest);
                    return super.compileKernel(compileRequest, moduleArtifact);
                }
            };

            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{new int[]{0}},
                    GpuRuntimeCompileOptions.openCl(List.of(), "vendor-tuned")
            ));
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        String originalIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.originalIrGpuArtifact());
        String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.optimizedIrGpuArtifact());
        String finalCompileIdentity = IrGpuArtifactIdentity.stableIdentity(finalCompileRequest.get().irGpuArtifact());
        assertNotEquals(originalIdentity, optimizedIdentity);
        assertEquals(originalIdentity, finalCompileIdentity);
        assertEquals("blocked", snapshot.productionOptimizerGate().status());
        assertEquals("blocked", snapshot.runtimeIrSelection().productionIrGate().status());
        assertEquals("production-ir-gate-blocked", snapshot.runtimeIrSelection().fallbackDecision());
        assertEquals("original", snapshot.runtimeIrSelection().selectedStage());
        assertTrue(snapshot.runtimeIrSelection().optimizedRejected());
        assertTrue(snapshot.backendSourcePromotionGate().isPresent());
        assertEquals("blocked", snapshot.backendSourcePromotionGate().orElseThrow().status());
        assertTrue(snapshot.backendSourceSwitchingDecision().isPresent());
        assertEquals("descriptor-default", snapshot.backendSourceSwitchingDecision().orElseThrow().status());
        assertEquals("compile-descriptor-source", snapshot.backendSourceSwitchingDecision().orElseThrow().decision());
    }

    @Test
    void productionEnabledGateCompilesOptimizedIrAfterSelection() throws Exception {
        Path classpathRoot = Files.createTempDirectory("javatogpu-production-enabled-resource");
        Path artifactPath = classpathRoot.resolve("javatogpu/sample/Demo/kernel.irgpu.properties");
        Files.createDirectories(artifactPath.getParent());
        Files.writeString(artifactPath, irGpuArtifactProperties("return original"));
        GpuKernelDescriptor descriptor = descriptorWithIrGpuResource();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        AtomicReference<GpuRuntimeCompileRequest> finalCompileRequest = new AtomicReference<>();

        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classpathRoot.toUri().toURL()}, previousClassLoader)) {
            Thread.currentThread().setContextClassLoader(classLoader);
            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
                @Override
                protected GpuRuntimeIrOptimizationResult optimizeRuntimeIrWithReport(GpuRuntimeCompileRequest compileRequest) {
                    return optimizedRuntimeResultWithAcceptedProof(
                            compileRequest,
                            "production enabled optimized body\n  return output[0] + 42\n",
                            productionBackedStrategyDecision()
                    );
                }

                @Override
                protected GpuRuntimeEquivalenceEvidence executeRuntimeEquivalence(GpuRuntimeEquivalenceRequest request) {
                    return GpuRuntimeEquivalenceEvidence.passed(
                            request.optimizedCompileRequest(),
                            2,
                            2,
                            List.of("production enabled fixture remains equivalent")
                    );
                }

                @Override
                protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                    return GpuBackendModuleArtifact.openClSource(
                            "__kernel void kernel(__global int* output) { output[0] = 2; }",
                            "runtime/lowered/" + IrGpuArtifactIdentity.stableHash(compileRequest.irGpuArtifact().orElseThrow()) + ".cl",
                            "test-lowerer-v1"
                    );
                }

                @Override
                protected OpenClCompiledKernel compileKernel(
                        GpuRuntimeCompileRequest compileRequest,
                        GpuBackendModuleArtifact moduleArtifact
                ) {
                    finalCompileRequest.set(compileRequest);
                    return super.compileKernel(compileRequest, moduleArtifact);
                }
            };

            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{new int[]{0}},
                    GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned")
                            .withProductionPromotionDecision(productionEnabledDecision())
                            .withProductionPromotionOperatorAccepted(true)
            ));
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        String originalIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.originalIrGpuArtifact());
        String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.optimizedIrGpuArtifact());
        String finalCompileIdentity = IrGpuArtifactIdentity.stableIdentity(finalCompileRequest.get().irGpuArtifact());
        assertNotEquals(originalIdentity, optimizedIdentity);
        assertEquals(optimizedIdentity, finalCompileIdentity);
        assertEquals("accepted", snapshot.productionOptimizerGate().status());
        assertEquals("production-enabled", snapshot.runtimeIrSelection().productionIrGate().status());
        assertEquals("optimized", snapshot.runtimeIrSelection().selectedStage());
        assertFalse(snapshot.runtimeIrSelection().optimizedRejected());
        assertEquals("none", snapshot.runtimeIrSelection().fallbackDecision());
        assertTrue(snapshot.backendSourcePromotionGate().isPresent());
        assertEquals("blocked", snapshot.backendSourcePromotionGate().orElseThrow().status());
        assertTrue(snapshot.backendSourceSwitchingDecision().isPresent());
        assertEquals("blocked", snapshot.backendSourceSwitchingDecision().orElseThrow().status());
        assertEquals("reject-irgpu-source-unavailable", snapshot.backendSourceSwitchingDecision().orElseThrow().decision());
        assertTrue(snapshot.backendSourceSwitchingDecision().orElseThrow().irGpuSourceRequested());
        assertFalse(snapshot.backendSourceSwitchingDecision().orElseThrow().sourceReady());
        assertTrue(snapshot.backendSourceSwitchingDecision().orElseThrow().productionProfileRequested());
        assertTrue(snapshot.backendSourceSwitchingDecision().orElseThrow().productionSourceSwitchingEnabled());
    }

    @Test
    void productionReadyExplainabilityWithoutAcceptedProofCompilesOriginalIr() throws Exception {
        Path classpathRoot = Files.createTempDirectory("javatogpu-production-proof-required-resource");
        Path artifactPath = classpathRoot.resolve("javatogpu/sample/Demo/kernel.irgpu.properties");
        Files.createDirectories(artifactPath.getParent());
        Files.writeString(artifactPath, irGpuArtifactProperties("return original"));
        GpuKernelDescriptor descriptor = descriptorWithIrGpuResource();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        AtomicReference<GpuRuntimeCompileRequest> finalCompileRequest = new AtomicReference<>();

        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classpathRoot.toUri().toURL()}, previousClassLoader)) {
            Thread.currentThread().setContextClassLoader(classLoader);
            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
                @Override
                protected GpuRuntimeIrOptimizationResult optimizeRuntimeIrWithReport(GpuRuntimeCompileRequest compileRequest) {
                    return optimizedRuntimeResult(
                            compileRequest,
                            "production proof missing optimized body\n  return output[0] + 42\n",
                            productionBackedStrategyDecision()
                    );
                }

                @Override
                protected GpuRuntimeEquivalenceEvidence executeRuntimeEquivalence(GpuRuntimeEquivalenceRequest request) {
                    return GpuRuntimeEquivalenceEvidence.passed(
                            request.optimizedCompileRequest(),
                            2,
                            2,
                            List.of("production proof-required fixture remains equivalent")
                    );
                }

                @Override
                protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                    return GpuBackendModuleArtifact.openClSource(
                            "__kernel void kernel(__global int* output) { output[0] = 2; }",
                            "runtime/lowered/" + IrGpuArtifactIdentity.stableHash(compileRequest.irGpuArtifact().orElseThrow()) + ".cl",
                            "test-lowerer-v1"
                    );
                }

                @Override
                protected OpenClCompiledKernel compileKernel(
                        GpuRuntimeCompileRequest compileRequest,
                        GpuBackendModuleArtifact moduleArtifact
                ) {
                    finalCompileRequest.set(compileRequest);
                    return super.compileKernel(compileRequest, moduleArtifact);
                }
            };

            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{new int[]{0}},
                    GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned")
                            .withProductionPromotionDecision(productionEnabledDecision())
                            .withProductionPromotionOperatorAccepted(true)
            ));
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        String originalIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.originalIrGpuArtifact());
        String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.optimizedIrGpuArtifact());
        String finalCompileIdentity = IrGpuArtifactIdentity.stableIdentity(finalCompileRequest.get().irGpuArtifact());
        assertNotEquals(originalIdentity, optimizedIdentity);
        assertEquals(originalIdentity, finalCompileIdentity);
        assertEquals("blocked", snapshot.productionOptimizerGate().status());
        assertEquals("blocked", snapshot.runtimeIrSelection().productionIrGate().status());
        assertEquals("production-ir-gate-blocked", snapshot.runtimeIrSelection().fallbackDecision());
        assertEquals("original", snapshot.runtimeIrSelection().selectedStage());
        assertTrue(snapshot.runtimeIrSelection().optimizedRejected());
        assertTrue(snapshot.productionOptimizerGate().diagnostics().contains(
                "accepted optimizer proof artifact is required before production promotion"
        ));
    }

    @Test
    void productionPromotionExplainabilityFileFeedsRuntimeCompileOptions() throws Exception {
        Path explainabilityFile = Files.createTempFile("javatogpu-production-promotion-explainability", ".properties");
        writeProductionReadyExplainability(explainabilityFile);
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        AtomicReference<GpuRuntimeCompileRequest> capturedCompileRequest = new AtomicReference<>();
        String previousExplainabilityFile = System.getProperty("javatogpu.opencl.productionPromotionExplainabilityFile");
        try {
            System.setProperty("javatogpu.opencl.productionPromotionExplainabilityFile", explainabilityFile.toString());

            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
                @Override
                protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                    capturedCompileRequest.set(compileRequest);
                    return GpuBackendModuleArtifact.openClSource(
                            "__kernel void kernel(__global int* output) { output[0] = 1; }",
                            "javatogpu/sample/Demo/kernel.cl",
                            "test-lowerer-v1"
                    );
                }
            };

            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{new int[]{0}},
                    GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned")
            ));
        } finally {
            if (previousExplainabilityFile == null) {
                System.clearProperty("javatogpu.opencl.productionPromotionExplainabilityFile");
            } else {
                System.setProperty("javatogpu.opencl.productionPromotionExplainabilityFile", previousExplainabilityFile);
            }
        }

        assertEquals(
                GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                capturedCompileRequest.get().options().backendOptions().productionPromotionDecisionMode()
        );
        assertTrue(capturedSnapshot.get().backendSourceSwitchingDecision().isPresent());
        assertEquals(
                GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                capturedSnapshot.get().backendSourceSwitchingDecision().orElseThrow().productionPromotionDecisionMode()
        );
    }

    @Test
    void productionReadyExplainabilityAndPackagedIrGpuCanReachProductionSourceSwitching() throws Exception {
        Path explainabilityFile = Files.createTempFile("javatogpu-production-source-switching-explainability", ".properties");
        writeProductionReadyExplainability(explainabilityFile);
        GpuKernelDescriptor descriptor = simpleIrGpuSourceDescriptor();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        String previousExplainabilityFile = System.getProperty("javatogpu.opencl.productionPromotionExplainabilityFile");
        try {
            System.setProperty("javatogpu.opencl.productionPromotionExplainabilityFile", explainabilityFile.toString());

            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
                @Override
                protected GpuRuntimeEquivalenceEvidence executeRuntimeEquivalence(GpuRuntimeEquivalenceRequest request) {
                    return GpuRuntimeEquivalenceEvidence.passed(
                            request.optimizedCompileRequest(),
                            1,
                            1,
                            List.of("packaged IrGpu source remained equivalent before production source switching")
                    );
                }
            };

            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{new float[]{1.0f}, 2.0f, new float[]{0.0f}},
                    GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned")
                            .withProductionPromotionOperatorAccepted(true)
            ));
        } finally {
            if (previousExplainabilityFile == null) {
                System.clearProperty("javatogpu.opencl.productionPromotionExplainabilityFile");
            } else {
                System.setProperty("javatogpu.opencl.productionPromotionExplainabilityFile", previousExplainabilityFile);
            }
        }

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertTrue(snapshot.backendSourceSwitchingDecision().isPresent());
        assertEquals(
                "production-switch-enabled",
                snapshot.backendSourceSwitchingDecision().orElseThrow().status(),
                snapshot.backendSourceSwitchingDecision().orElseThrow().toPropertiesText()
        );
        assertEquals("compile-irgpu-source-production", snapshot.backendSourceSwitchingDecision().orElseThrow().decision());
        assertEquals(
                descriptor.kernelResource() + "#irgpu-reconstructed",
                snapshot.backendSourceSwitchingDecision().orElseThrow().backendResource()
        );
        assertTrue(snapshot.backendSourceSwitchingDecision().orElseThrow().sourceReady());
        assertTrue(snapshot.backendSourceSwitchingDecision().orElseThrow().sourceAvailable());
        assertTrue(snapshot.backendSourceSwitchingDecision().orElseThrow().sourceParityMatched());
        assertTrue(snapshot.backendSourceSwitchingDecision().orElseThrow().productionSourceSwitchingEnabled());
        assertTrue(snapshot.backendSourceSwitchingDecision().orElseThrow().productionPromotionOperatorAccepted());
        assertEquals(
                GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                snapshot.backendSourceSwitchingDecision().orElseThrow().productionPromotionDecisionMode()
        );
    }

    @Test
    void missingProductionPromotionExplainabilityFileKeepsRuntimeDiagnosticOnly() throws Exception {
        Path missingExplainabilityFile = Files.createTempDirectory("javatogpu-missing-production-promotion")
                .resolve("missing-production-promotion-explainability.properties");

        assertProductionPromotionExplainabilityFileKeepsRuntimeDiagnosticOnly(missingExplainabilityFile);
    }

    @Test
    void invalidProductionPromotionExplainabilityFileKeepsRuntimeDiagnosticOnly() throws Exception {
        Path invalidExplainabilityFile = Files.createTempFile("javatogpu-invalid-production-promotion", ".properties");
        Files.writeString(invalidExplainabilityFile, "not a properties file with a valid production promotion contract\n");

        assertProductionPromotionExplainabilityFileKeepsRuntimeDiagnosticOnly(invalidExplainabilityFile);
    }

    @Test
    void requiresBufferArgumentToDeriveGlobalWorkSize() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of()
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        GpuRuntimeInvocationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                GpuRuntimeInvocationException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[0]))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL execution requires at least one buffer argument to derive global work size for kernel kernel"
        ));
        assertTrue(exception.getMessage().contains("launch with an explicit GpuExecutionConfig"));
        assertEquals(0, backend.cacheSize());
    }

    @Test
    void explicitGlobalWorkSizeAllowsBufferlessKernelLaunch() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of()
        );

        AtomicReference<net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig> executedConfig = new AtomicReference<>();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
                executedConfig.set(executionConfig);
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[0], 8L));

        assertEquals(8L, executedConfig.get().globalWorkSize());
    }

    @Test
    void explicitGlobalWorkSizeBypassesMismatchedBufferLengthRestriction() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("blob", "byte[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicReference<net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig> executedConfig = new AtomicReference<>();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                return new Object();
            }

            @Override
            protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
            }

            @Override
            protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
            }

            @Override
            protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
                executedConfig.set(executionConfig);
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new byte[64], new int[8]}, 8L));

        assertEquals(8L, executedConfig.get().globalWorkSize());
    }

    @Test
    void rejectsNonPositiveExplicitGlobalWorkSize() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of()
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[0], 0L))
        );

        assertTrue(exception.getMessage().contains("globalX must be positive: 0"));
    }

    @Test
    void explicitTwoDimensionalExecutionConfigReachesKernelEnqueuePath() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of()
        );

        AtomicReference<net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig> executedConfig = new AtomicReference<>();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
                executedConfig.set(executionConfig);
            }
        };

        backend.invoke(new GpuKernelInvocation(
                descriptor,
                new Object[0],
                net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.twoDimensional(16L, 8L, 4L, 2L)
        ));

        assertEquals(2, executedConfig.get().dimensions());
        assertEquals(16L, executedConfig.get().globalX());
        assertEquals(8L, executedConfig.get().globalY());
        assertEquals(4L, executedConfig.get().localX());
        assertEquals(2L, executedConfig.get().localY());
    }

    @Test
    void explicitThreeDimensionalExecutionConfigReachesKernelEnqueuePath() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of()
        );

        AtomicReference<net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig> executedConfig = new AtomicReference<>();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
                executedConfig.set(executionConfig);
            }
        };

        backend.invoke(new GpuKernelInvocation(
                descriptor,
                new Object[0],
                net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.threeDimensional(16L, 8L, 4L, 4L, 2L, 1L)
        ));

        assertEquals(3, executedConfig.get().dimensions());
        assertEquals(16L, executedConfig.get().globalX());
        assertEquals(8L, executedConfig.get().globalY());
        assertEquals(4L, executedConfig.get().globalZ());
        assertEquals(4L, executedConfig.get().localX());
        assertEquals(2L, executedConfig.get().localY());
        assertEquals(1L, executedConfig.get().localZ());
    }

    @Test
    void executesPreparedArgumentsInKernelOrderAndReadsBackOutputs() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        float[] input = new float[]{1.0f, 2.0f, 3.0f};
        float[] output = new float[]{0.0f, 0.0f, 0.0f};
        List<String> events = new ArrayList<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            private final HashMap<Object, float[]> nativeArrays = new HashMap<>();
            private final HashMap<Integer, Object> boundBuffers = new HashMap<>();
            private final HashMap<Integer, Object> boundScalars = new HashMap<>();

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                float[] deviceArray = new float[binding.length()];
                nativeArrays.put(deviceArray, deviceArray);
                events.add("alloc:" + binding.length());
                return deviceArray;
            }

            @Override
            protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                System.arraycopy((float[]) binding.sourceArray(), 0, (float[]) nativeBuffer, 0, binding.length());
                events.add("upload:" + binding.length());
            }

            @Override
            protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
                boundBuffers.put(parameterIndex, nativeBuffer);
                events.add("bind-buffer:" + parameterIndex);
            }

            @Override
            protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
                boundScalars.put(parameterIndex, binding.value());
                events.add("bind-scalar:" + parameterIndex);
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
                float[] in = (float[]) boundBuffers.get(0);
                float scale = (Float) boundScalars.get(1);
                float[] out = (float[]) boundBuffers.get(2);
                for (int i = 0; i < executionConfig.globalWorkSize(); i++) {
                    out[i] = in[i] + scale;
                }
                events.add("enqueue:" + executionConfig.globalWorkSize());
            }

            @Override
            protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                System.arraycopy((float[]) nativeBuffer, 0, (float[]) binding.sourceArray(), 0, binding.length());
                events.add("readback:" + binding.length());
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, 2.5f, output}));

        assertEquals(
                Arrays.asList(
                        "alloc:3",
                        "upload:3",
                        "alloc:3",
                        "upload:3",
                        "bind-buffer:0",
                        "bind-scalar:1",
                        "bind-buffer:2",
                        "enqueue:3",
                        "readback:3"
                ),
                events
        );
        assertEquals(3.5f, output[0]);
        assertEquals(4.5f, output[1]);
        assertEquals(5.5f, output[2]);
    }

    @Test
    void rejectsMismatchedBufferLengthsBeforeKernelLaunch() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger executeCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                return new Object();
            }

            @Override
            protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                // no-op for length validation path
            }

            @Override
            protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
                // no-op for length validation path
            }

            @Override
            protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
                // no-op for length validation path
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
                executeCalls.incrementAndGet();
            }

            @Override
            protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                // no-op for length validation path
            }
        };

        GpuRuntimeInvocationException exception = assertThrows(
                GpuRuntimeInvocationException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{new float[]{1.0f, 2.0f}, new float[]{0.0f}}
                ))
        );

        assertTrue(exception.getMessage().contains(
                "Mismatched GPU array lengths for kernel kernel: expected 2 but found 1"
        ));
        assertTrue(exception.getMessage().contains("must share the same logical length"));
        assertEquals(0, executeCalls.get());
        assertEquals(0, backend.cacheSize());
    }

    @Test
    void unsupportedUploadTypesIncludeQuickFixHints() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "java.lang.Object[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                return new Object();
            }
        };

        GpuRuntimeInvocationException exception = assertThrows(
                GpuRuntimeInvocationException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new Object[]{new Object()}}))
        );

        assertTrue(exception.getMessage().contains("Failed to marshall parameter 'input':"));
        assertTrue(exception.getMessage().contains("Unsupported OpenCL argument type:"));
        assertTrue(exception.getMessage().contains("java.lang.Object"));
    }

    @Test
    void rejectsDoubleKernelWhenDeviceLacksFp64BeforeCompile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Double/kernel.cl",
                "__kernel void kernel(__global double* input, __global double* output) { output[0] = input[0]; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "double[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "double[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", false, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        GpuRuntimeCapabilityException exception = assertThrows(
                GpuRuntimeCapabilityException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{new double[]{1.0d}, new double[]{0.0d}}
                ))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL capability precheck failed for kernel kernel: device Fake GPU does not advertise fp64 support, but the kernel uses double precision"
        ));
        assertTrue(exception.getMessage().contains("float/fallback path"));
        assertEquals(0, compileCalls.get());
        assertEquals(0, backend.cacheSize());
    }

    @Test
    void rejectsImageKernelWhenDeviceLacksImageSupportBeforeCompile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Image/kernel.cl",
                "__kernel void kernel(read_only image2d_t inputImage, sampler_t sampler, __global int* output) { }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, false, false, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        GpuRuntimeCapabilityException exception = assertThrows(
                GpuRuntimeCapabilityException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{Image2DReadOnly.borrowed(1L, 1, 1), Sampler.borrowed(2L), new int[]{0}}
                ))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL capability precheck failed for kernel kernel: device Fake GPU does not support OpenCL images, but the kernel requires image/sampler parameters"
        ));
        assertTrue(exception.getMessage().contains("buffer-backed kernels"));
        assertEquals(0, compileCalls.get());
    }

    @Test
    void rejectsImage3dWriteKernelWhenDeviceLacks3dWriteSupportBeforeCompile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Image3d/kernel.cl",
                "__kernel void kernel(read_only image3d_t inputImage, write_only image3d_t outputImage, sampler_t sampler, __global int* output) { }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image3DReadOnly", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("outputImage", "Image3DWriteOnly", GpuKernelParameterAccess.READ_WRITE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, false, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        GpuRuntimeCapabilityException exception = assertThrows(
                GpuRuntimeCapabilityException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{Image3DReadOnly.borrowed(1L, 1, 1, 1), Image3DWriteOnly.borrowed(2L, 1, 1, 1), Sampler.borrowed(3L), new int[]{0}}
                ))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL capability precheck failed for kernel kernel: device Fake GPU does not support 3D image writes required by the kernel"
        ));
        assertTrue(exception.getMessage().contains("2D/buffer workflows"));
        assertEquals(0, compileCalls.get());
    }

    @Test
    void rejectsLocalMemoryRequestThatExceedsDeviceBudgetBeforeCompile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Local/kernel.cl",
                "__kernel void kernel(__local float* scratch, __global float* output) { output[0] = scratch[0]; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("scratch", "float[]", GpuKernelParameterAccess.LOCAL),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 8L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        GpuRuntimeCapabilityException exception = assertThrows(
                GpuRuntimeCapabilityException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{new float[]{1.0f, 2.0f, 3.0f}, new float[]{0.0f}}
                ))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL capability precheck failed for kernel kernel: requested 12 bytes of local memory, but device Fake GPU exposes only 8 bytes"
        ));
        assertTrue(exception.getMessage().contains("reduce the local scratch size"));
        assertEquals(0, compileCalls.get());
    }

    @Test
    void reportsUnavailableOpenClRuntimeClearly() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY)
                )
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeSession createSession() {
                throw new UnsatisfiedLinkError("LWJGL OpenCL bindings are missing");
            }
        };

        GpuRuntimeBackendUnavailableException exception = org.junit.jupiter.api.Assertions.assertThrows(
                GpuRuntimeBackendUnavailableException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new float[]{1.0f}}))
        );

        assertTrue(exception.getMessage().contains("OpenCL runtime is unavailable: LWJGL OpenCL bindings are missing"));
        assertTrue(exception.getMessage().contains("GpuRuntime.trySelect(...)"));
    }

    @Test
    void formatsKernelBuildFailuresWithKernelAndDeviceContext() throws java.io.IOException {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global float* output) { output[0] = 1.0f; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                throw new RuntimeException("driver build log: unknown type name 'half16'");
            }
        };

        String expression = "GpuShowcase.basicMath(input, output)";
        GpuRuntimeKernelCompilationException exception;
        try (URLClassLoader callSiteClassLoader = callSiteClassLoader(
                "formatsKernelBuildFailuresWithKernelAndDeviceContext",
                expression
        )) {
            exception = assertThrows(
                    GpuRuntimeKernelCompilationException.class,
                    () -> backend.invoke(new GpuKernelInvocation(
                            descriptor,
                            new Object[]{new float[]{0.0f}}
                    ).withArtifactClassLoader(callSiteClassLoader))
            );
        }

        assertTrue(exception.getMessage().contains(
                "OpenCL kernel build failed for kernel kernel on device Fake GPU [javatogpu/sample/Demo/kernel.cl]: driver build log: unknown type name 'half16'"
        ));
        assertTrue(exception.getMessage().contains("enable ABI debug"));
        assertTrue(exception.getMessage().contains("Device-Quirks.md"));
        assertEquals("JTG-RUNTIME-COMPILE-001", exception.code());
        assertTrue(exception.diagnosticText().startsWith("error[JTG-RUNTIME-COMPILE-001]:"));
        assertTrue(exception.diagnosticText().contains("--> OpenClGpuRuntimeBackendTest.java:1:1"));
        assertEquals("kernel", exception.context().kernelName());
        assertEquals("Fake GPU", exception.context().deviceLabel());
        assertEquals("compiler-index", exception.context().callSite().source());
        assertEquals(expression, exception.context().callSite().expression());
        assertTrue(exception.diagnosticText().contains(expression));
    }

    @Test
    void compilerLogFromCompiledKernelSurvivesFinalSnapshotAttachment() {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        String compileLog = "Used 40 registers, 8 bytes spill stores, 4 bytes spill loads";

        OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                return new OpenClCompiledKernel(
                        compileRequest.descriptor(),
                        "compiled:with-log",
                        GpuRuntimeCompileArtifactSnapshot.legacy(compileRequest.descriptor())
                                .withCompileLog(compileLog),
                        null,
                        null
                );
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals(compileLog, snapshot.compileLog());
        net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDump dump =
                net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper.dump(snapshot);
        String feedback = dump.artifact(
                net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper.BACKEND_COMPILER_FEEDBACK_ARTIFACT
        );
        assertTrue(feedback.contains("status=recorded"));
        assertTrue(feedback.contains("selected.register.general=40"));
        assertTrue(feedback.contains("selected.spill.knownBytes=12"));
    }

    @Test
    void formatsKernelExecutionFailuresWithKernelAndDeviceContext() throws java.io.IOException {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global float* output) { output[0] = 1.0f; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                throw new RuntimeException("clEnqueueNDRangeKernel failed: CL_OUT_OF_RESOURCES");
            }
        };

        String expression = "GpuShowcase.basicMath(input, output)";
        GpuRuntimeKernelExecutionException exception;
        try (URLClassLoader callSiteClassLoader = callSiteClassLoader(
                "formatsKernelExecutionFailuresWithKernelAndDeviceContext",
                expression
        )) {
            exception = assertThrows(
                    GpuRuntimeKernelExecutionException.class,
                    () -> backend.invoke(new GpuKernelInvocation(
                            descriptor,
                            new Object[]{new float[]{0.0f}}
                    ).withArtifactClassLoader(callSiteClassLoader))
            );
        }

        assertTrue(exception.getMessage().contains(
                "OpenCL kernel execution failed for kernel kernel on device Fake GPU: clEnqueueNDRangeKernel failed: CL_OUT_OF_RESOURCES"
        ));
        assertTrue(exception.getMessage().contains("fallback backend"));
        assertEquals("JTG-RUNTIME-EXECUTE-001", exception.code());
        assertTrue(exception.diagnosticText().startsWith("error[JTG-RUNTIME-EXECUTE-001]:"));
        assertEquals("compiler-index", exception.context().callSite().source());
        assertEquals(expression, exception.context().callSite().expression());
        assertTrue(exception.diagnosticText().contains(expression));
    }

    @Test
    void closeClearsBufferRegistryClosesTrackedBuffersAndAllowsReuse() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        AtomicInteger trackedBufferCloseCalls = new AtomicInteger();
        AtomicInteger deviceBufferAllocations = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:" + compileCalls.get());
            }

            @Override
            protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                deviceBufferAllocations.incrementAndGet();
                return new AutoCloseable() {
                    private boolean closed;

                    @Override
                    public void close() {
                        if (!closed) {
                            closed = true;
                            trackedBufferCloseCalls.incrementAndGet();
                        }
                    }
                };
            }

            @Override
            protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                // no-op
            }

            @Override
            protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
                // no-op
            }

            @Override
            protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
                // no-op
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
                // no-op
            }

            @Override
            protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                // no-op
            }
        };

        int[] output = new int[]{0};
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{output}));

        assertEquals(1, compileCalls.get());
        assertEquals(1, deviceBufferAllocations.get());
        assertEquals(1, backend.cacheSize());
        assertEquals(1, backend.bufferCacheSize());

        backend.close();

        assertEquals(1, trackedBufferCloseCalls.get());
        assertEquals(0, backend.cacheSize());
        assertEquals(0, backend.bufferCacheSize());

        backend.close();

        assertEquals(1, trackedBufferCloseCalls.get());

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{output}));

        assertEquals(2, compileCalls.get());
        assertEquals(2, deviceBufferAllocations.get());
        assertEquals(1, backend.cacheSize());
        assertEquals(1, backend.bufferCacheSize());
    }

    @Test
    void repeatedCreateInvokeCloseCyclesRemainStable() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        AtomicInteger deviceBufferAllocations = new AtomicInteger();
        AtomicInteger trackedBufferCloseCalls = new AtomicInteger();

        for (int iteration = 0; iteration < 25; iteration++) {
            OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
                @Override
                protected OpenClRuntimeCapabilities runtimeCapabilities() {
                    return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
                }

                @Override
                protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                    compileCalls.incrementAndGet();
                    return new OpenClCompiledKernel(kernelDescriptor, "compiled:" + compileCalls.get());
                }

                @Override
                protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                    deviceBufferAllocations.incrementAndGet();
                    return new AutoCloseable() {
                        private boolean closed;

                        @Override
                        public void close() {
                            if (!closed) {
                                closed = true;
                                trackedBufferCloseCalls.incrementAndGet();
                            }
                        }
                    };
                }

                @Override
                protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                    // no-op
                }

                @Override
                protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
                    // no-op
                }

                @Override
                protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
                    // no-op
                }

                @Override
                protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
                    // no-op
                }

                @Override
                protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                    // no-op
                }
            };

            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{iteration}}));

            assertEquals(1, backend.cacheSize());
            assertEquals(1, backend.bufferCacheSize());

            backend.close();

            assertEquals(0, backend.cacheSize());
            assertEquals(0, backend.bufferCacheSize());
        }

        assertEquals(25, compileCalls.get());
        assertEquals(25, deviceBufferAllocations.get());
        assertEquals(25, trackedBufferCloseCalls.get());
    }

    @Test
    void sharedCacheRetainsCompiledKernelAcrossBackendInstancesUntilExplicitShutdown() {
        OpenClGpuRuntimeBackend.shutdownSharedCache();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();

        OpenClGpuRuntimeBackend firstBackend = new OpenClGpuRuntimeBackend(OpenClGpuRuntimeBackend.CacheMode.SHARED) {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:" + compileCalls.incrementAndGet());
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        firstBackend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        assertEquals(1, compileCalls.get());
        assertEquals(1, firstBackend.cacheSize());
        firstBackend.close();
        assertEquals(1, firstBackend.cacheSize());

        OpenClGpuRuntimeBackend secondBackend = new OpenClGpuRuntimeBackend(OpenClGpuRuntimeBackend.CacheMode.SHARED) {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:" + compileCalls.incrementAndGet());
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        secondBackend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        assertEquals(1, compileCalls.get());
        assertEquals(1, secondBackend.cacheSize());
        secondBackend.close();

        OpenClGpuRuntimeBackend.shutdownSharedCache();

        OpenClGpuRuntimeBackend thirdBackend = new OpenClGpuRuntimeBackend(OpenClGpuRuntimeBackend.CacheMode.SHARED) {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:" + compileCalls.incrementAndGet());
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        thirdBackend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        assertEquals(2, compileCalls.get());
        thirdBackend.close();
        OpenClGpuRuntimeBackend.shutdownSharedCache();
    }

    @Test
    void createsHighLevelFloatImageThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgbaFloatImageInternal(int width, int height, float[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(rgba);
                return Image2DReadOnly.borrowed(777L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgbaFloatImage(2, 1, new float[]{1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f});

        assertEquals(777L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRFloatImageThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRFloatImageInternal(int width, int height, float[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(778L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRFloatImage(2, 1, new float[]{1.0f, 2.0f});

        assertEquals(778L, image.handle());
        assertEquals(2, captured.get().length);
    }

    @Test
    void createsHighLevelRgFloatImageThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgFloatImageInternal(int width, int height, float[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(779L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgFloatImage(2, 1, new float[]{1.0f, 2.0f, 3.0f, 4.0f});

        assertEquals(779L, image.handle());
        assertEquals(4, captured.get().length);
    }

    @Test
    void createsHighLevelDepthImageThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyDepthImageInternal(int width, int height, float[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(7791L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyDepthImage(2, 1, new float[]{0.25f, 0.75f});

        assertEquals(7791L, image.handle());
        assertEquals(2, captured.get().length);
    }

    @Test
    void createsHighLevelRIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRIntImageInternal(int width, int height, int[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(780L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRIntImage(2, 1, new int[]{1, 2});

        assertEquals(780L, image.handle());
        assertEquals(2, captured.get().length);
    }

    @Test
    void createsHighLevelRgIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgIntImageInternal(int width, int height, int[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(781L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgIntImage(2, 1, new int[]{1, 2, 3, 4});

        assertEquals(781L, image.handle());
        assertEquals(4, captured.get().length);
    }

    @Test
    void createsHighLevelSamplerThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Sampler createSamplerInternal(boolean normalizedCoordinates, int addressingMode, int filterMode) {
                assertEquals(true, normalizedCoordinates);
                assertEquals(5, addressingMode);
                assertEquals(6, filterMode);
                return Sampler.borrowed(888L);
            }
        };

        Sampler sampler = backend.createSampler(true, 5, 6);

        assertEquals(888L, sampler.handle());
    }

    @Test
    void createsHighLevelRgba8ImageThroughProtectedHook() {
        AtomicReference<byte[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgba8ImageInternal(int width, int height, byte[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(rgba);
                return Image2DReadOnly.borrowed(999L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgba8Image(
                2,
                1,
                new byte[]{1, 2, 3, 4, 5, 6, 7, 8}
        );

        assertEquals(999L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaUIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgbaUIntImageInternal(int width, int height, int[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(rgba);
                return Image2DReadOnly.borrowed(1001L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgbaUIntImage(
                2,
                1,
                new int[]{1, 2, 3, 4, 5, 6, 7, 8}
        );

        assertEquals(1001L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRUIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRUIntImageInternal(int width, int height, int[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(1002L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRUIntImage(2, 1, new int[]{1, 2});

        assertEquals(1002L, image.handle());
        assertEquals(2, captured.get().length);
    }

    @Test
    void createsHighLevelRgUIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgUIntImageInternal(int width, int height, int[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(1003L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgUIntImage(2, 1, new int[]{1, 2, 3, 4});

        assertEquals(1003L, image.handle());
        assertEquals(4, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaFloatImage3dThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image3DReadOnly createReadOnlyRgbaFloatImage3DInternal(int width, int height, int depth, float[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                assertEquals(2, depth);
                captured.set(rgba);
                return Image3DReadOnly.borrowed(1004L, width, height, depth);
            }
        };

        Image3DReadOnly image = backend.createReadOnlyRgbaFloatImage3D(
                2,
                1,
                2,
                new float[]{
                        1.0f, 0.0f, 0.0f, 1.0f,
                        0.0f, 1.0f, 0.0f, 1.0f,
                        0.0f, 0.0f, 1.0f, 1.0f,
                        1.0f, 1.0f, 1.0f, 1.0f
                }
        );

        assertEquals(1004L, image.handle());
        assertEquals(16, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaIntImage3dThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image3DReadOnly createReadOnlyRgbaIntImage3DInternal(int width, int height, int depth, int[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                assertEquals(2, depth);
                captured.set(rgba);
                return Image3DReadOnly.borrowed(1005L, width, height, depth);
            }
        };

        Image3DReadOnly image = backend.createReadOnlyRgbaIntImage3D(
                2,
                1,
                2,
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
        );

        assertEquals(1005L, image.handle());
        assertEquals(16, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaUIntImage3dThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image3DReadOnly createReadOnlyRgbaUIntImage3DInternal(int width, int height, int depth, int[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                assertEquals(2, depth);
                captured.set(rgba);
                return Image3DReadOnly.borrowed(1006L, width, height, depth);
            }
        };

        Image3DReadOnly image = backend.createReadOnlyRgbaUIntImage3D(
                2,
                1,
                2,
                new int[]{101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116}
        );

        assertEquals(1006L, image.handle());
        assertEquals(16, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaFloatImage1dThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image1DReadOnly createReadOnlyRgbaFloatImage1DInternal(int width, float[] rgba) {
                assertEquals(2, width);
                captured.set(rgba);
                return Image1DReadOnly.borrowed(1007L, width);
            }
        };

        Image1DReadOnly image = backend.createReadOnlyRgbaFloatImage1D(2, new float[]{1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f});

        assertEquals(1007L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaUIntImage1dThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image1DReadOnly createReadOnlyRgbaUIntImage1DInternal(int width, int[] rgba) {
                assertEquals(2, width);
                captured.set(rgba);
                return Image1DReadOnly.borrowed(1008L, width);
            }
        };

        Image1DReadOnly image = backend.createReadOnlyRgbaUIntImage1D(2, new int[]{1, 2, 3, 4, 5, 6, 7, 8});

        assertEquals(1008L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaUIntImage1dArrayThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image1DArrayReadOnly createReadOnlyRgbaUIntImage1DArrayInternal(int width, int layers, int[] rgba) {
                assertEquals(2, width);
                assertEquals(2, layers);
                captured.set(rgba);
                return Image1DArrayReadOnly.borrowed(1010L, width, layers);
            }
        };

        Image1DArrayReadOnly image = backend.createReadOnlyRgbaUIntImage1DArray(2, 2, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});

        assertEquals(1010L, image.handle());
        assertEquals(16, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaUIntImage1dBufferThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image1DBufferReadOnly createReadOnlyRgbaUIntImage1DBufferInternal(int width, int[] rgba) {
                assertEquals(2, width);
                captured.set(rgba);
                return Image1DBufferReadOnly.borrowed(1011L, width);
            }
        };

        Image1DBufferReadOnly image = backend.createReadOnlyRgbaUIntImage1DBuffer(2, new int[]{1, 2, 3, 4, 5, 6, 7, 8});

        assertEquals(1011L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaFloatImage2dArrayThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DArrayReadOnly createReadOnlyRgbaFloatImage2DArrayInternal(int width, int height, int layers, float[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                assertEquals(2, layers);
                captured.set(rgba);
                return Image2DArrayReadOnly.borrowed(1012L, width, height, layers);
            }
        };

        Image2DArrayReadOnly image = backend.createReadOnlyRgbaFloatImage2DArray(2, 1, 2, new float[]{1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1});

        assertEquals(1012L, image.handle());
        assertEquals(16, captured.get().length);
    }

    @Test
    void createsHighLevelMipmappedFloatImageThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DMipmappedReadOnly createReadOnlyRgbaFloatImageMipmappedInternal(int width, int height, int mipLevels, float[] rgba) {
                assertEquals(4, width);
                assertEquals(2, height);
                assertEquals(2, mipLevels);
                captured.set(rgba);
                return Image2DMipmappedReadOnly.borrowed(10121L, width, height, mipLevels);
            }
        };

        Image2DMipmappedReadOnly image = backend.createReadOnlyRgbaFloatImageMipmapped(4, 2, 2, new float[]{
                1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1,
                1, 0, 1, 1, 0, 1, 1, 1, 1, 1, 0, 1, 0, 0, 0, 1,
                0.5f, 0.5f, 0.5f, 1.0f, 0.25f, 0.25f, 0.25f, 1.0f
        });

        assertEquals(10121L, image.handle());
        assertEquals(40, captured.get().length);
    }

    @Test
    void createsHighLevelMipmappedRgba8ImageThroughProtectedHook() {
        AtomicReference<byte[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DMipmappedReadOnly createReadOnlyRgba8ImageMipmappedInternal(int width, int height, int mipLevels, byte[] rgba) {
                assertEquals(4, width);
                assertEquals(2, height);
                assertEquals(2, mipLevels);
                captured.set(rgba);
                return Image2DMipmappedReadOnly.borrowed(10127L, width, height, mipLevels);
            }
        };

        Image2DMipmappedReadOnly image = backend.createReadOnlyRgba8ImageMipmapped(4, 2, 2, new byte[]{
                1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24,
                25, 26, 27, 28, 29, 30, 31, 32,
                33, 34, 35, 36, 37, 38, 39, 40
        });

        assertEquals(10127L, image.handle());
        assertEquals(40, captured.get().length);
    }

    @Test
    void createsHighLevelMipmappedIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DMipmappedReadOnly createReadOnlyRgbaIntImageMipmappedInternal(int width, int height, int mipLevels, int[] rgba) {
                assertEquals(4, width);
                assertEquals(2, height);
                assertEquals(2, mipLevels);
                captured.set(rgba);
                return Image2DMipmappedReadOnly.borrowed(10125L, width, height, mipLevels);
            }
        };

        Image2DMipmappedReadOnly image = backend.createReadOnlyRgbaIntImageMipmapped(4, 2, 2, new int[]{
                1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24,
                25, 26, 27, 28, 29, 30, 31, 32,
                -1, -2, -3, -4, -5, -6, -7, -8
        });

        assertEquals(10125L, image.handle());
        assertEquals(40, captured.get().length);
    }

    @Test
    void createsHighLevelMipmappedUIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DMipmappedReadOnly createReadOnlyRgbaUIntImageMipmappedInternal(int width, int height, int mipLevels, int[] rgba) {
                assertEquals(4, width);
                assertEquals(2, height);
                assertEquals(2, mipLevels);
                captured.set(rgba);
                return Image2DMipmappedReadOnly.borrowed(10122L, width, height, mipLevels);
            }
        };

        Image2DMipmappedReadOnly image = backend.createReadOnlyRgbaUIntImageMipmapped(4, 2, 2, new int[]{
                1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24,
                25, 26, 27, 28, 29, 30, 31, 32,
                33, 34, 35, 36, 37, 38, 39, 40
        });

        assertEquals(10122L, image.handle());
        assertEquals(40, captured.get().length);
    }

    @Test
    void readsHighLevelFloatImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgbaFloatImageInternal(Image2DWriteOnly image) {
                assertEquals(123L, image.handle());
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f};
            }
        };

        float[] rgba = backend.readRgbaFloatImage(Image2DWriteOnly.borrowed(123L, 1, 1));

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f}, rgba);
    }

    @Test
    void readsHighLevelRFloatImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRFloatImageInternal(Image2DReadOnly image) {
                assertEquals(124L, image.handle());
                return new float[]{1.0f, 2.0f};
            }
        };

        float[] values = backend.readRFloatImage(Image2DReadOnly.borrowed(124L, 2, 1));

        assertArrayEquals(new float[]{1.0f, 2.0f}, values);
    }

    @Test
    void readsHighLevelRgFloatImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgFloatImageInternal(Image2DWriteOnly image) {
                assertEquals(125L, image.handle());
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f};
            }
        };

        float[] values = backend.readRgFloatImage(Image2DWriteOnly.borrowed(125L, 2, 1));

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f}, values);
    }

    @Test
    void readsHighLevelDepthImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readDepthImageInternal(Image2DWriteOnly image) {
                assertEquals(1251L, image.handle());
                return new float[]{0.25f, 0.75f};
            }
        };

        float[] values = backend.readDepthImage(Image2DWriteOnly.borrowed(1251L, 2, 1));

        assertArrayEquals(new float[]{0.25f, 0.75f}, values);
    }

    @Test
    void readsHighLevelRIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRIntImageInternal(Image2DReadOnly image) {
                assertEquals(126L, image.handle());
                return new int[]{7, 8};
            }
        };

        int[] values = backend.readRIntImage(Image2DReadOnly.borrowed(126L, 2, 1));

        assertArrayEquals(new int[]{7, 8}, values);
    }

    @Test
    void readsHighLevelRgIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgIntImageInternal(Image2DWriteOnly image) {
                assertEquals(127L, image.handle());
                return new int[]{7, 8, 9, 10};
            }
        };

        int[] values = backend.readRgIntImage(Image2DWriteOnly.borrowed(127L, 2, 1));

        assertArrayEquals(new int[]{7, 8, 9, 10}, values);
    }

    @Test
    void readsHighLevelIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaIntImageInternal(Image2DReadOnly image) {
                assertEquals(321L, image.handle());
                return new int[]{5, 6, 7, 8};
            }
        };

        int[] rgba = backend.readRgbaIntImage(Image2DReadOnly.borrowed(321L, 1, 1));

        assertArrayEquals(new int[]{5, 6, 7, 8}, rgba);
    }

    @Test
    void readsHighLevelRgbaUIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaUIntImageInternal(Image2DReadOnly image) {
                assertEquals(741L, image.handle());
                return new int[]{11, 12, 13, 14};
            }
        };

        int[] rgba = backend.readRgbaUIntImage(Image2DReadOnly.borrowed(741L, 1, 1));

        assertArrayEquals(new int[]{11, 12, 13, 14}, rgba);
    }

    @Test
    void readsHighLevelRUIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRUIntImageInternal(Image2DReadOnly image) {
                assertEquals(742L, image.handle());
                return new int[]{15, 16};
            }
        };

        int[] values = backend.readRUIntImage(Image2DReadOnly.borrowed(742L, 2, 1));

        assertArrayEquals(new int[]{15, 16}, values);
    }

    @Test
    void readsHighLevelRgUIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgUIntImageInternal(Image2DWriteOnly image) {
                assertEquals(743L, image.handle());
                return new int[]{15, 16, 17, 18};
            }
        };

        int[] values = backend.readRgUIntImage(Image2DWriteOnly.borrowed(743L, 2, 1));

        assertArrayEquals(new int[]{15, 16, 17, 18}, values);
    }

    @Test
    void readsHighLevelRgba8ImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected byte[] readRgba8ImageInternal(Image2DWriteOnly image) {
                assertEquals(654L, image.handle());
                return new byte[]{9, 10, 11, 12};
            }
        };

        byte[] rgba = backend.readRgba8Image(Image2DWriteOnly.borrowed(654L, 1, 1));

        assertArrayEquals(new byte[]{9, 10, 11, 12}, rgba);
    }

    @Test
    void readsHighLevelRgbaFloatImage3dThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgbaFloatImage3DInternal(Image3DWriteOnly image) {
                assertEquals(905L, image.handle());
                assertEquals(2, image.depth());
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f};
            }
        };

        float[] rgba = backend.readRgbaFloatImage3D(Image3DWriteOnly.borrowed(905L, 1, 1, 2));

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f}, rgba);
    }

    @Test
    void readsHighLevelRgbaIntImage3dThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaIntImage3DInternal(Image3DWriteOnly image) {
                assertEquals(906L, image.handle());
                assertEquals(2, image.depth());
                return new int[]{1, 2, 3, 4, 5, 6, 7, 8};
            }
        };

        int[] rgba = backend.readRgbaIntImage3D(Image3DWriteOnly.borrowed(906L, 1, 1, 2));

        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7, 8}, rgba);
    }

    @Test
    void readsHighLevelRgbaUIntImage3dThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaUIntImage3DInternal(Image3DWriteOnly image) {
                assertEquals(907L, image.handle());
                assertEquals(2, image.depth());
                return new int[]{11, 12, 13, 14, 15, 16, 17, 18};
            }
        };

        int[] rgba = backend.readRgbaUIntImage3D(Image3DWriteOnly.borrowed(907L, 1, 1, 2));

        assertArrayEquals(new int[]{11, 12, 13, 14, 15, 16, 17, 18}, rgba);
    }

    @Test
    void readsHighLevelRgbaFloatImage1dThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgbaFloatImage1DInternal(Image1DWriteOnly image) {
                assertEquals(908L, image.handle());
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f};
            }
        };

        float[] rgba = backend.readRgbaFloatImage1D(Image1DWriteOnly.borrowed(908L, 1));

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f}, rgba);
    }

    @Test
    void readsHighLevelRgbaUIntImage1dThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaUIntImage1DInternal(Image1DWriteOnly image) {
                assertEquals(909L, image.handle());
                return new int[]{9, 10, 11, 12};
            }
        };

        int[] rgba = backend.readRgbaUIntImage1D(Image1DWriteOnly.borrowed(909L, 1));

        assertArrayEquals(new int[]{9, 10, 11, 12}, rgba);
    }

    @Test
    void readsHighLevelRgbaUIntImage1dArrayThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaUIntImage1DArrayInternal(Image1DArrayWriteOnly image) {
                assertEquals(910L, image.handle());
                assertEquals(2, image.layers());
                return new int[]{9, 10, 11, 12, 13, 14, 15, 16};
            }
        };

        int[] rgba = backend.readRgbaUIntImage1DArray(Image1DArrayWriteOnly.borrowed(910L, 1, 2));

        assertArrayEquals(new int[]{9, 10, 11, 12, 13, 14, 15, 16}, rgba);
    }

    @Test
    void readsHighLevelRgbaIntImage1dBufferThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaIntImage1DBufferInternal(Image1DBufferWriteOnly image) {
                assertEquals(911L, image.handle());
                return new int[]{9, 10, 11, 12};
            }
        };

        int[] rgba = backend.readRgbaIntImage1DBuffer(Image1DBufferWriteOnly.borrowed(911L, 1));

        assertArrayEquals(new int[]{9, 10, 11, 12}, rgba);
    }

    @Test
    void readsHighLevelRgbaFloatImage2dArrayThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgbaFloatImage2DArrayInternal(Image2DArrayWriteOnly image) {
                assertEquals(912L, image.handle());
                assertEquals(2, image.layers());
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f};
            }
        };

        float[] rgba = backend.readRgbaFloatImage2DArray(Image2DArrayWriteOnly.borrowed(912L, 1, 1, 2));

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f}, rgba);
    }

    @Test
    void readsHighLevelMipmappedFloatImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgbaFloatImageMipmappedInternal(Image2DMipmappedWriteOnly image, int mipLevel) {
                assertEquals(10123L, image.handle());
                assertEquals(2, image.mipLevels());
                assertEquals(1, mipLevel);
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f};
            }
        };

        float[] rgba = backend.readRgbaFloatImageMipmapped(Image2DMipmappedWriteOnly.borrowed(10123L, 4, 2, 2), 1);

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f}, rgba);
    }

    @Test
    void readsHighLevelMipmappedRgba8ImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected byte[] readRgba8ImageMipmappedInternal(Image2DMipmappedWriteOnly image, int mipLevel) {
                assertEquals(10128L, image.handle());
                assertEquals(2, image.mipLevels());
                assertEquals(1, mipLevel);
                return new byte[]{9, 10, 11, 12};
            }
        };

        byte[] rgba = backend.readRgba8ImageMipmapped(Image2DMipmappedWriteOnly.borrowed(10128L, 4, 2, 2), 1);

        assertArrayEquals(new byte[]{9, 10, 11, 12}, rgba);
    }

    @Test
    void readsHighLevelMipmappedIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaIntImageMipmappedInternal(Image2DMipmappedWriteOnly image, int mipLevel) {
                assertEquals(10126L, image.handle());
                assertEquals(2, image.mipLevels());
                assertEquals(1, mipLevel);
                return new int[]{-9, -10, -11, -12};
            }
        };

        int[] rgba = backend.readRgbaIntImageMipmapped(Image2DMipmappedWriteOnly.borrowed(10126L, 4, 2, 2), 1);

        assertArrayEquals(new int[]{-9, -10, -11, -12}, rgba);
    }

    @Test
    void readsHighLevelMipmappedUIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaUIntImageMipmappedInternal(Image2DMipmappedWriteOnly image, int mipLevel) {
                assertEquals(10124L, image.handle());
                assertEquals(2, image.mipLevels());
                assertEquals(1, mipLevel);
                return new int[]{9, 10, 11, 12};
            }
        };

        int[] rgba = backend.readRgbaUIntImageMipmapped(Image2DMipmappedWriteOnly.borrowed(10124L, 4, 2, 2), 1);

        assertArrayEquals(new int[]{9, 10, 11, 12}, rgba);
    }
    private static IrGpuArtifact testIrGpuArtifact(String body) {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(IrGpuMethodBody.entry("kernel", "jtg_kernel", body, java.util.List.of()))
                ),
                java.util.List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static String irGpuArtifactProperties(String returnExpression) {
        return """
                # JavaToGpu backend-neutral IR artifact manifest
                backendOutput.0.backend=opencl
                backendOutput.0.format=opencl-c
                backendOutput.0.kind=source
                backendOutput.0.resource=javatogpu/sample/Demo/kernel.cl
                backendOutput.count=1
                compilerArtifact=JavaToGpu
                derived.opencl.resource=javatogpu/sample/Demo/kernel.cl
                entryEmittedName=jtg_kernel
                entryMethod=kernel
                format=javatogpu.irgpu.v1
                helper.count=0
                methodBody.0.body=method jtg_kernel source\\=kernel\\nhelpers -\\nbody\\n  %s\\n
                methodBody.0.emittedName=jtg_kernel
                methodBody.0.format=ir-text-v1
                methodBody.0.helperDependency.count=0
                methodBody.0.name=kernel
                methodBody.0.role=entry
                methodBody.count=1
                runtime.defaultBackend=opencl
                runtime.optimizationProfile=off
                schemaVersion=1
                sourceFrontend=java-source
                struct.count=0
                """.formatted(returnExpression);
    }

    private static GpuKernelDescriptor descriptorWithIrGpuResource() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
                java.util.List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
    }

    private static GpuKernelDescriptor simpleIrGpuSourceDescriptor() {
        return new GpuKernelDescriptor(
                "gpu_irgpu_entry",
                "inline://integration/simple-irgpu-source-kernel.cl",
                """
                        __kernel void gpu_irgpu_entry(__global const float* input, float scale, __global float* output) {
                            int id = get_global_id(0);
                            output[id] = input[id] + scale;
                        }
                        """,
                SIMPLE_IRGPU_SOURCE_RESOURCE,
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private static OpenClGpuRuntimeBackend.GpuRuntimeIrOptimizationResult optimizedRuntimeResult(
            GpuRuntimeCompileRequest compileRequest,
            String optimizedBody,
            GpuOptimizationStrategyDecision strategyDecision
    ) {
        return optimizedRuntimeResult(compileRequest, optimizedBody, strategyDecision, false);
    }

    private static OpenClGpuRuntimeBackend.GpuRuntimeIrOptimizationResult optimizedRuntimeResultWithAcceptedProof(
            GpuRuntimeCompileRequest compileRequest,
            String optimizedBody,
            GpuOptimizationStrategyDecision strategyDecision
    ) {
        return optimizedRuntimeResult(compileRequest, optimizedBody, strategyDecision, true);
    }

    private static OpenClGpuRuntimeBackend.GpuRuntimeIrOptimizationResult optimizedRuntimeResult(
            GpuRuntimeCompileRequest compileRequest,
            String optimizedBody,
            GpuOptimizationStrategyDecision strategyDecision,
            boolean acceptedProof
    ) {
        IrGpuArtifact optimizedArtifact = testIrGpuArtifact(optimizedBody);
        GpuRuntimeCompileRequest optimizedRequest = compileRequest.withIrGpuArtifact(java.util.Optional.of(optimizedArtifact));
        String originalIdentity = IrGpuArtifactIdentity.stableIdentity(compileRequest.irGpuArtifact());
        String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(optimizedRequest.irGpuArtifact());
        GpuRuntimeIrOptimizationPassReport passReport = GpuRuntimeIrOptimizationPassReport.applied(
                "test-runtime-optimizer",
                originalIdentity,
                optimizedIdentity,
                "test-proof",
                List.of("test optimizer supplied transformed IR")
        );
        if (acceptedProof) {
            passReport = passReport.withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                    "test.productionProof",
                    "accepted",
                    java.util.Map.of("runtimeProductionProof", "accepted")
            ));
        }
        return new OpenClGpuRuntimeBackend.GpuRuntimeIrOptimizationResult(
                optimizedRequest,
                new GpuRuntimeIrOptimizationReport(
                        java.util.Optional.of(optimizedArtifact),
                        List.of(passReport),
                        strategyDecision
                )
        );
    }

    private static GpuOptimizationStrategyDecision productionBackedStrategyDecision() {
        return new GpuOptimizationStrategyDecision(
                "strategy:production-fixture",
                "test-vendor",
                "vendor-tuned",
                false,
                true,
                "test fixture carries production evidence",
                new GpuOptimizationVendorBaseline(
                        "test-vendor",
                        "production-fixture",
                        true,
                        true,
                        "test-fixture",
                        List.of("test fixture is promotion-eligible")
                ),
                List.of("production fixture is evidence-backed")
        );
    }

    private static GpuProductionPromotionDecision productionEnabledDecision() {
        return new GpuProductionPromotionDecision(
                GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                "production-ready",
                true,
                true,
                true,
                "none",
                "none",
                "production fixture enables runtime IR mutation"
        );
    }

    private static void writeProductionReadyExplainability(Path path) throws java.io.IOException {
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("status", "production-ready");
        properties.setProperty("kernel.count", "1");
        properties.setProperty("i3ReviewReady.count", "1");
        properties.setProperty("i3Blocked.count", "0");
        properties.setProperty("i3SourceReady.count", "1");
        properties.setProperty("productionSourceSwitchingAllowed", "true");
        properties.setProperty("productionSourceSwitchingEnabled", "true");
        properties.setProperty("productionSourceSwitchingEnabled.count", "1");
        properties.setProperty("productionSourceSwitchingEnabled.all", "true");
        properties.setProperty("productionPromotionDecisionMode.productionEnabled.count", "1");
        properties.setProperty("productionPromotionDecisionMode.productionEnabled.all", "true");
        properties.setProperty("sourceSwitching.productionDecision.count", "1");
        properties.setProperty("sourceSwitching.productionDecision.all", "true");
        properties.setProperty("productionMutationAllowed", "true");
        properties.setProperty("productionMutationEnabled", "true");
        properties.setProperty("backendPromotionArtifactSupport.complete", "true");
        properties.setProperty("backendPromotionArtifactSupport.missing.count", "0");
        properties.setProperty("blocker.count", "0");
        try (java.io.Writer writer = Files.newBufferedWriter(path)) {
            properties.store(writer, "test production promotion explainability");
        }
    }

    private static void assertProductionPromotionExplainabilityFileKeepsRuntimeDiagnosticOnly(
            Path explainabilityFile
    ) throws java.io.IOException {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        AtomicReference<GpuRuntimeCompileRequest> capturedCompileRequest = new AtomicReference<>();
        String previousExplainabilityFile = System.getProperty("javatogpu.opencl.productionPromotionExplainabilityFile");
        try {
            System.setProperty("javatogpu.opencl.productionPromotionExplainabilityFile", explainabilityFile.toString());

            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
                @Override
                protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                    capturedCompileRequest.set(compileRequest);
                    return GpuBackendModuleArtifact.openClSource(
                            "__kernel void kernel(__global int* output) { output[0] = 1; }",
                            "javatogpu/sample/Demo/kernel.cl",
                            "test-lowerer-v1"
                    );
                }
            };

            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{new int[]{0}},
                    GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned")
            ));
        } finally {
            if (previousExplainabilityFile == null) {
                System.clearProperty("javatogpu.opencl.productionPromotionExplainabilityFile");
            } else {
                System.setProperty("javatogpu.opencl.productionPromotionExplainabilityFile", previousExplainabilityFile);
            }
        }

        assertEquals(
                GpuProductionPromotionDecision.DIAGNOSTIC_ONLY,
                capturedCompileRequest.get().options().backendOptions().productionPromotionDecisionMode()
        );
        assertTrue(capturedSnapshot.get().backendSourceSwitchingDecision().isPresent());
        assertEquals(
                GpuProductionPromotionDecision.DIAGNOSTIC_ONLY,
                capturedSnapshot.get().backendSourceSwitchingDecision().orElseThrow().productionPromotionDecisionMode()
        );
        assertTrue(capturedSnapshot.get().backendSourceSwitchingDecision().orElseThrow().productionSourceSwitchingEnabled());
    }

    private static IrGpuArtifact parityMatchedIrGpuArtifact() {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "kernel",
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "kernel",
                                "body\n  set output[0] = 1\n",
                                java.util.List.of()
                        ))
                ),
                java.util.List.of(new net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter(
                        "output",
                        "int[]",
                        "GLOBAL",
                        false,
                        java.util.List.of()
                )),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.backendNeutralReady(),
                java.util.List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact parityMatchedFloat2IrGpuArtifact() {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "kernel",
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "kernel",
                                "body\n  set output[0] = (float2)(1.0f, 2.0f)\n",
                                java.util.List.of()
                        ))
                ),
                java.util.List.of(new net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter(
                        "output",
                        "net.sixik.ga_utils.javatogpu.api.Float2[]",
                        "GLOBAL",
                        false,
                        java.util.List.of()
                )),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.backendNeutralReady(),
                java.util.List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact parityMatchedStructIrGpuArtifact() {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "kernel",
                        java.util.List.of(),
                        java.util.List.of("typedef struct { int x; float y; } EquivalenceStructSample;"),
                        java.util.List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "kernel",
                                "body\n  set output[0].x = 11\n  set output[0].y = 12.5f\n",
                                java.util.List.of()
                        ))
                ),
                java.util.List.of(new net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter(
                        "output",
                        "EquivalenceStructSample[]",
                        "GLOBAL",
                        false,
                        java.util.List.of()
                )),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.backendNeutralReady(),
                java.util.List.of(new IrGpuStructMetadata(
                        EquivalenceStructSample.class.getName(),
                        "EquivalenceStructSample",
                        java.util.List.of(
                                new IrGpuStructFieldMetadata("x", "int", java.util.List.of()),
                                new IrGpuStructFieldMetadata("y", "float", java.util.List.of())
                        ),
                        java.util.List.of()
                )),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static String canonicalOpenClSource(String source) {
        return source.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static GpuKernelDescriptor intOutputDescriptor() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
    }

    private static GpuKernelDescriptor float2OutputDescriptor() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global float2* output) { output[0] = (float2)(1.0f, 2.0f); }",
                java.util.List.of(new GpuKernelParameterDescriptor(
                        "output",
                        "net.sixik.ga_utils.javatogpu.api.Float2[]",
                        GpuKernelParameterAccess.READ_WRITE
                ))
        );
    }

    private static GpuKernelDescriptor structOutputDescriptor() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "typedef struct{\n"
                        + "    int x;\n"
                        + "    float y;\n"
                        + "} EquivalenceStructSample;\n\n"
                        + "__kernel void kernel(__global EquivalenceStructSample* output) {\n"
                        + "    output[0].x = 11;\n"
                        + "    output[0].y = 12.5f;\n"
                        + "}",
                java.util.List.of(new GpuKernelParameterDescriptor(
                        "output",
                        "EquivalenceStructSample[]",
                        GpuKernelParameterAccess.READ_WRITE
                ))
        );
    }

    private static URLClassLoader callSiteClassLoader(
            String callerMethodName,
            String expression
    ) throws java.io.IOException {
        Path root = Files.createTempDirectory("javatogpu-runtime-call-site");
        Path resource = root.resolve(GpuRuntimeCallSiteResolver.resourcePath(
                OpenClGpuRuntimeBackendTest.class.getName()
        ));
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, String.join("\n",
                "format=javatogpu.call-sites.v1",
                "callSite.count=1",
                "callSite.0.callerClassName=" + OpenClGpuRuntimeBackendTest.class.getName(),
                "callSite.0.callerMethodName=" + callerMethodName,
                "callSite.0.sourceName=OpenClGpuRuntimeBackendTest.java",
                "callSite.0.line=1",
                "callSite.0.column=1",
                "callSite.0.endLine=1",
                "callSite.0.endColumn=" + expression.length(),
                "callSite.0.expression=" + expression,
                "callSite.0.targetOwnerName=unknown",
                "callSite.0.targetMethodName=unknown",
                ""
        ));
        return new URLClassLoader(
                new URL[]{root.toUri().toURL()},
                OpenClGpuRuntimeBackendTest.class.getClassLoader()
        );
    }

    private static class SnapshotCapturingBackend extends OpenClGpuRuntimeBackend {
        protected final AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot;

        private SnapshotCapturingBackend(AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot) {
            this.capturedSnapshot = capturedSnapshot;
        }

        @Override
        protected OpenClRuntimeCapabilities runtimeCapabilities() {
            return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
        }

        @Override
        protected OpenClCompiledKernel compileKernel(
                GpuRuntimeCompileRequest compileRequest,
                GpuBackendModuleArtifact moduleArtifact
        ) {
            return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:test");
        }

        @Override
        protected void executeKernel(OpenClPreparedExecution execution) {
            capturedSnapshot.set(execution.compiledKernel().artifactSnapshot());
        }
    }

    private static class EquivalenceSimulatingBackend extends SnapshotCapturingBackend {
        private final int descriptorEquivalenceOutput;
        private final int reconstructedEquivalenceOutput;

        private EquivalenceSimulatingBackend(
                AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot,
                int descriptorEquivalenceOutput,
                int reconstructedEquivalenceOutput
        ) {
            super(capturedSnapshot);
            this.descriptorEquivalenceOutput = descriptorEquivalenceOutput;
            this.reconstructedEquivalenceOutput = reconstructedEquivalenceOutput;
        }

        @Override
        protected void executeKernel(OpenClPreparedExecution execution) {
            for (OpenClPreparedBufferBinding binding : execution.bufferBindings()) {
                if (binding.access() != GpuKernelParameterAccess.READ_WRITE) {
                    continue;
                }
                mutateEquivalenceOutput(execution, binding.binding().sourceArray());
            }
            capturedSnapshot.set(execution.compiledKernel().artifactSnapshot());
        }

        private void mutateEquivalenceOutput(OpenClPreparedExecution execution, Object sourceArray) {
            if (sourceArray instanceof int[] values && values.length > 0) {
                if (execution.compiledKernel().cacheKey().contains("#irgpu-reconstructed-equivalence-preflight")) {
                    values[0] = reconstructedEquivalenceOutput;
                } else if (execution.compiledKernel().artifactSnapshot().runtimeEquivalenceEvidence().executed()) {
                    values[0] = 1;
                } else {
                    values[0] = descriptorEquivalenceOutput;
                }
                return;
            }
            if (sourceArray instanceof Float2[] values && values.length > 0) {
                values[0] = new Float2(1.0f, 2.0f);
                return;
            }
            if (sourceArray instanceof EquivalenceStructSample[] values && values.length > 0) {
                values[0] = new EquivalenceStructSample(11, 12.5f);
            }
        }
    }

    @GPUStruct
    static final class EquivalenceStructSample {
        int x;
        float y;

        EquivalenceStructSample() {
        }

        EquivalenceStructSample(int x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
