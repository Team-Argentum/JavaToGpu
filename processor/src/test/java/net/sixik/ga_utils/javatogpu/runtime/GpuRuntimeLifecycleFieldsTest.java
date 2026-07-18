package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpuRuntimeLifecycleFieldsTest {

    @Test
    void compileRequestFieldsUseBackendNeutralRuntimeVocabulary() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
                List.of()
        );
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions
                .defaults(GpuBackendTarget.OPENCL)
                .preferDeviceVendor("NVIDIA")
                .withStandardBackendDevicePreflight();
        GpuRuntimeDeviceProfile deviceProfile = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-0",
                "Lifecycle GPU",
                "NVIDIA",
                "driver-1",
                "OpenCL 3.0 Lifecycle",
                "NVIDIA CUDA",
                "OpenCL 3.0 CUDA",
                GpuDeviceClassTarget.DGPU,
                48L,
                8L * 1024L * 1024L * 1024L,
                64L * 1024L,
                1024L,
                1L,
                false,
                true,
                true,
                false
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                options,
                deviceProfile
        );

        Map<String, String> fields = GpuRuntimeLifecycleFields.compileRequestFields(compileRequest);

        assertEquals("kernel", fields.get("runtime.kernel.name"));
        assertEquals("javatogpu/sample/Demo/kernel.cl", fields.get("runtime.kernel.resource"));
        assertEquals("javatogpu/sample/Demo/kernel.irgpu.properties", fields.get("runtime.kernel.irgpuResource"));
        assertEquals("OPENCL", fields.get("runtime.backend.target"));
        assertEquals("OpenCL", fields.get("runtime.backend.name"));
        assertEquals("opencl-0", fields.get("runtime.device.id"));
        assertEquals("Lifecycle GPU", fields.get("runtime.device.label"));
        assertEquals("NVIDIA", fields.get("runtime.device.vendor"));
        assertEquals("DGPU", fields.get("runtime.device.class"));
        assertEquals("standard", options.backendOptions().backendDevicePreflightMode());
        assertEquals("1", fields.get("runtime.compile.backendOption.property.count"));
        assertEquals("false", fields.get("runtime.irgpu.present"));
    }

    @Test
    void descriptorCompileOptionFieldsWorkWithoutDeviceProfile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
                List.of()
        );
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions
                .defaults(GpuBackendTarget.OPENCL)
                .excludeCpuDevices();

        Map<String, String> fields = GpuRuntimeLifecycleFields.descriptorCompileOptionsFields(descriptor, options);

        assertEquals("kernel", fields.get("runtime.kernel.name"));
        assertEquals("javatogpu/sample/Demo/kernel.cl", fields.get("runtime.kernel.resource"));
        assertEquals("OPENCL", fields.get("runtime.backend.target"));
        assertEquals("off", fields.get("runtime.compile.optimizationProfile"));
        assertEquals("automatic", fields.get("runtime.compile.deviceOverride"));
        assertEquals(
                "preferDeviceIds=any, preferVendors=any, preferDeviceLabels=any, "
                        + "preferDeviceClasses=any, excludeDeviceIds=none, excludeVendors=none, "
                        + "excludeDeviceLabels=none, excludeDeviceClasses=[cpu]",
                fields.get("runtime.compile.devicePreference")
        );
    }

    @Test
    void executionConfigFieldsExposeStableWorkShape() {
        Map<String, String> fields = GpuRuntimeLifecycleFields.executionConfigFields(
                GpuExecutionConfig.threeDimensional(8L, 4L, 2L, 2L, 2L, 1L)
        );

        assertEquals("true", fields.get("runtime.work.present"));
        assertEquals("3", fields.get("runtime.work.dimensions"));
        assertEquals("8x4x2", fields.get("runtime.work.globalShape"));
        assertEquals("2x2x1", fields.get("runtime.work.localShape"));
        assertEquals("64", fields.get("runtime.work.globalItemCount"));
        assertEquals("4", fields.get("runtime.work.localItemCount"));
        assertEquals("true", fields.get("runtime.work.explicitLocal"));
    }

    @Test
    void backendRuntimeStateFieldsExposeStableCacheAndCounterVocabulary() {
        Map<String, String> fields = GpuRuntimeLifecycleFields.backendRuntimeStateFields(
                "SHARED",
                3L,
                2L,
                11L,
                5L,
                4L,
                1L,
                9L
        );

        assertEquals("SHARED", fields.get("runtime.backend.cache.mode"));
        assertEquals("3", fields.get("runtime.backend.cache.compiledKernel.count"));
        assertEquals("4", fields.get("runtime.backend.cache.compileHit.count"));
        assertEquals("5", fields.get("runtime.backend.compile.count"));
        assertEquals("11", fields.get("runtime.backend.invocation.count"));
        assertEquals("1", fields.get("runtime.backend.session.creation.count"));
        assertEquals("2", fields.get("runtime.backend.buffer.native.count"));
        assertEquals("9", fields.get("runtime.backend.buffer.device.creation.count"));
    }

    @Test
    void backendSourceSelectionFieldsExposeStableSourceSwitchingVocabulary() {
        GpuBackendModuleArtifact moduleArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel() {}",
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer",
                "descriptor-opencl-source",
                "opencl-descriptor-source-compile"
        );
        GpuBackendSourceSwitchingDecision decision = new GpuBackendSourceSwitchingDecision(
                "descriptor-default",
                "compile-descriptor-source",
                GpuBackendTarget.OPENCL,
                "opencl-c",
                "javatogpu/sample/Demo/kernel.cl",
                "descriptor-opencl-source",
                "opencl-descriptor-source-compile",
                "off",
                false,
                "descriptor",
                false,
                false,
                false,
                true,
                false,
                false,
                "blocked",
                false,
                "backend source must be reconstructed from IrGpu before promotion review",
                "disabled",
                false,
                "diagnostic-only",
                false,
                "descriptor source remains selected"
        );

        Map<String, String> fields = GpuRuntimeLifecycleFields.backendSourceSelectionFields(moduleArtifact, decision);

        assertEquals("descriptor-default", fields.get("runtime.status"));
        assertEquals("true", fields.get("runtime.backend.source.selection.present"));
        assertEquals("descriptor-default", fields.get("runtime.backend.source.status"));
        assertEquals("compile-descriptor-source", fields.get("runtime.backend.source.decision"));
        assertEquals("descriptor", fields.get("runtime.backend.source.selection"));
        assertEquals("false", fields.get("runtime.backend.source.irgpuRequested"));
        assertEquals("true", fields.get("runtime.backend.source.available"));
        assertEquals("blocked", fields.get("runtime.backend.source.promotionStatus"));
        assertEquals(
                "backend source must be reconstructed from IrGpu before promotion review",
                fields.get("runtime.backend.source.promotionFirstBlocker")
        );
        assertEquals("disabled", fields.get("runtime.backend.source.productionSwitching"));
        assertEquals("diagnostic-only", fields.get("runtime.backend.source.productionPromotionDecisionMode"));
        assertEquals("opencl-descriptor-source-compile", fields.get("runtime.backend.source.runtimeLoadMode"));
        assertEquals("descriptor source remains selected", fields.get("runtime.backend.source.diagnostic"));
        assertEquals("opencl-c", fields.get("runtime.module.format"));
        assertEquals("test-lowerer", fields.get("runtime.module.lowererVersion"));
    }

    @Test
    void runtimeIrSelectionAndFallbackFieldsExposeStableTraceVocabulary() {
        GpuRuntimeIrSelection selection = new GpuRuntimeIrSelection(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "original",
                "original-id",
                "optimized-id",
                "original-id",
                true,
                true,
                "optimizer-rollback",
                new GpuProductionIrAcceptanceGate.Result(
                        false,
                        "blocked",
                        "diagnostic-only",
                        "production gate blocked optimized IR"
                ),
                "optimized IR rolled back to original"
        );
        GpuRuntimeFallbackEvidence fallbackEvidence = GpuRuntimeFallbackEvidence.optimizerRollback(
                List.of("replacement proof failed")
        );

        Map<String, String> selectionFields = GpuRuntimeLifecycleFields.runtimeIrSelectionFields(selection);
        Map<String, String> fallbackFields = GpuRuntimeLifecycleFields.fallbackEvidenceFields(fallbackEvidence);

        assertEquals("true", selectionFields.get("runtime.ir.selection.present"));
        assertEquals("original", selectionFields.get("runtime.ir.selectedStage"));
        assertEquals("optimized-id", selectionFields.get("runtime.ir.optimizedIdentity"));
        assertEquals("true", selectionFields.get("runtime.ir.transformed"));
        assertEquals("true", selectionFields.get("runtime.ir.optimizedRejected"));
        assertEquals("optimizer-rollback", selectionFields.get("runtime.ir.fallbackDecision"));
        assertEquals("blocked", selectionFields.get("runtime.ir.productionGate.status"));
        assertEquals("diagnostic-only", selectionFields.get("runtime.ir.productionGate.decisionMode"));
        assertEquals("optimizer-rollback", fallbackFields.get("runtime.fallback.decision"));
        assertEquals("true", fallbackFields.get("runtime.fallback.originalIrSelected"));
        assertEquals("true", fallbackFields.get("runtime.fallback.optimizedIrRejected"));
        assertEquals("1", fallbackFields.get("runtime.fallback.diagnostic.count"));
        assertEquals("replacement proof failed", fallbackFields.get("runtime.fallback.diagnostic.0"));
    }

    @Test
    void backendDeviceSelectionFieldsExposeStableTraceVocabulary() {
        GpuRuntimeBackendSelectionExplanation backendSelection = new GpuRuntimeBackendSelectionExplanation(
                true,
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "Backend GPU",
                List.of(),
                List.of()
        );
        GpuRuntimeDeviceProfile device = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-lifecycle-0",
                "Lifecycle Device GPU",
                "NVIDIA",
                "lifecycle-driver",
                "OpenCL 3.0 Lifecycle",
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
        GpuRuntimeBackendDeviceSelectionExplanation selection = new GpuRuntimeBackendDeviceSelectionExplanation(
                backendSelection,
                catalog,
                null,
                null
        );

        Map<String, String> fields = GpuRuntimeLifecycleFields.backendDeviceSelectionFields(selection);

        assertEquals("backend-and-device-selected", fields.get("runtime.status"));
        assertEquals("backend-and-device-selected", fields.get("runtime.selection.status"));
        assertEquals("true", fields.get("runtime.backend.selection.matched"));
        assertEquals("OPENCL", fields.get("runtime.backend.target"));
        assertEquals("OpenCL", fields.get("runtime.backend.name"));
        assertEquals("true", fields.get("runtime.device.discovery.present"));
        assertEquals("true", fields.get("runtime.device.discovery.available"));
        assertEquals("1", fields.get("runtime.device.discovery.device.count"));
        assertEquals("OPENCL:opencl-lifecycle-0", fields.get("runtime.device.discovery.selectedDeviceKey"));
        assertEquals("true", fields.get("runtime.device.selected"));
        assertEquals("opencl-lifecycle-0", fields.get("runtime.device.id"));
        assertEquals("Lifecycle Device GPU", fields.get("runtime.device.label"));
        assertEquals("NVIDIA", fields.get("runtime.device.vendor"));
        assertEquals("DGPU", fields.get("runtime.device.class"));
    }

    @Test
    void backendAdapterFieldsExposeStableTraceVocabulary() {
        GpuRuntimeBackendAdapter adapter = new CudaRuntimeBackendAdapter();

        Map<String, String> fields = GpuRuntimeLifecycleFields.backendAdapterFields(adapter);

        assertEquals("non-production-adapter", fields.get("runtime.status"));
        assertEquals("true", fields.get("runtime.backend.adapter.present"));
        assertEquals("CUDA", fields.get("runtime.backend.target"));
        assertEquals("CUDA", fields.get("runtime.backend.name"));
        assertEquals("false", fields.get("runtime.backend.adapter.productionAdapter"));
        assertEquals("OWNED", fields.get("runtime.backend.adapter.ownership"));
        assertEquals(
                "Runtime backend adapter is not implemented for CUDA; keep using OPENCL or provide a custom runtime backend",
                fields.get("runtime.backend.adapter.diagnostic")
        );
        assertEquals("backend-lowerer:cuda", fields.get("runtime.backend.lowerer.id"));
        assertEquals("CUDA", fields.get("runtime.backend.lowerer.target"));
    }
}
