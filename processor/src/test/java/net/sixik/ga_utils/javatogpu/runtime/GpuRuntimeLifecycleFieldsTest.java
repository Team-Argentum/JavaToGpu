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
                new GpuRuntimeBackendStateSummary(
                        "SHARED",
                        3L,
                        2L,
                        11L,
                        5L,
                        4L,
                        1L,
                        9L
                )
        );
        Map<String, String> legacyFields = GpuRuntimeLifecycleFields.backendRuntimeStateFields(
                "SHARED",
                3L,
                2L,
                11L,
                5L,
                4L,
                1L,
                9L
        );

        assertEquals("true", fields.get("runtime.backend.state.present"));
        assertEquals("SHARED", fields.get("runtime.backend.cache.mode"));
        assertEquals("3", fields.get("runtime.backend.cache.compiledKernel.count"));
        assertEquals("4", fields.get("runtime.backend.cache.compileHit.count"));
        assertEquals("5", fields.get("runtime.backend.compile.count"));
        assertEquals("11", fields.get("runtime.backend.invocation.count"));
        assertEquals("1", fields.get("runtime.backend.session.creation.count"));
        assertEquals("2", fields.get("runtime.backend.buffer.native.count"));
        assertEquals("9", fields.get("runtime.backend.buffer.device.creation.count"));
        assertEquals(fields, legacyFields);
    }

    @Test
    void backendCompilationInvocationAndArtifactDumpFieldsComposePortableLifecycleFacts() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
                List.of()
        );
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL);
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
        GpuBackendModuleArtifact moduleArtifact = GpuBackendModuleArtifact.openClSource(
                descriptor.kernelSource(),
                descriptor.kernelResource(),
                "test-lowerer"
        );
        GpuRuntimeCompileArtifactSnapshot artifactSnapshot = GpuRuntimeCompileArtifactSnapshot
                .from(compileRequest, compileRequest, moduleArtifact)
                .withCompileLog("build ok");
        Map<String, String> backendState = GpuRuntimeLifecycleFields.backendRuntimeStateFields(
                "LOCAL",
                7L,
                3L,
                11L,
                5L,
                2L,
                1L,
                4L
        );

        Map<String, String> compileFields = GpuRuntimeLifecycleFields.backendCompilationFields(
                compileRequest,
                moduleArtifact,
                artifactSnapshot,
                backendState,
                GpuRuntimeBackendCompilationSummary.from(moduleArtifact, artifactSnapshot, "cache-key-1"),
                "succeeded",
                "cache-key-1",
                null
        );
        Map<String, String> invocationFields = GpuRuntimeLifecycleFields.invocationFields(
                artifactSnapshot,
                backendState,
                GpuExecutionConfig.oneDimensional(16L, 4L),
                new GpuRuntimeInvocationBindingSummary(2, 1, 3, 6),
                "started",
                "cache-key-1",
                null
        );
        Map<String, String> dumpFields = GpuRuntimeLifecycleFields.artifactDumpFields(
                artifactSnapshot,
                new GpuRuntimeArtifactDumpSummary(12, 2, 1, 3),
                "succeeded",
                null
        );

        assertEquals("succeeded", compileFields.get("runtime.status"));
        assertEquals("cache-key-1", compileFields.get("runtime.cache.key"));
        assertEquals("kernel", compileFields.get("runtime.kernel.name"));
        assertEquals("opencl-c", compileFields.get("runtime.module.format"));
        assertEquals("true", compileFields.get("runtime.compile.log.present"));
        assertEquals("true", compileFields.get("runtime.compilation.present"));
        assertEquals("true", compileFields.get("runtime.compilation.cacheKey.present"));
        assertEquals("true", compileFields.get("runtime.compilation.module.present"));
        assertEquals("opencl-c", compileFields.get("runtime.compilation.module.format"));
        assertEquals("true", compileFields.get("runtime.compilation.compileLog.present"));
        assertEquals("0", compileFields.get("runtime.compilation.binaryArtifact.count"));
        assertEquals("0", compileFields.get("runtime.compilation.validationEvidence.count"));
        assertEquals("LOCAL", compileFields.get("runtime.backend.cache.mode"));
        assertEquals("5", compileFields.get("runtime.backend.compile.count"));
        assertEquals("started", invocationFields.get("runtime.status"));
        assertEquals("16", invocationFields.get("runtime.work.globalShape"));
        assertEquals("4", invocationFields.get("runtime.work.localShape"));
        assertEquals("true", invocationFields.get("runtime.invocation.binding.present"));
        assertEquals("2", invocationFields.get("runtime.invocation.binding.buffer.count"));
        assertEquals("1", invocationFields.get("runtime.invocation.binding.local.count"));
        assertEquals("3", invocationFields.get("runtime.invocation.binding.scalar.count"));
        assertEquals("6", invocationFields.get("runtime.invocation.binding.argument.count"));
        assertEquals("11", invocationFields.get("runtime.backend.invocation.count"));
        assertEquals("succeeded", dumpFields.get("runtime.status"));
        assertEquals("opencl-c", dumpFields.get("runtime.module.format"));
        assertEquals("true", dumpFields.get("runtime.compile.log.present"));
        assertEquals("true", dumpFields.get("runtime.artifactDump.present"));
        assertEquals("12", dumpFields.get("runtime.artifactDump.artifact.count"));
        assertEquals("2", dumpFields.get("runtime.artifactDump.binaryArtifact.count"));
        assertEquals("1", dumpFields.get("runtime.artifactDump.sourceLocation.count"));
        assertEquals("3", dumpFields.get("runtime.artifactDump.directory.count"));
    }

    @Test
    void failureFieldsExposeStructuredRuntimeExceptionFacts() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
                List.of()
        );
        GpuRuntimeDiagnosticContext context = GpuRuntimeDiagnosticContext.from(
                descriptor,
                Optional.empty(),
                Optional.empty(),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
        );
        IllegalStateException cause = new IllegalStateException("driver rejected source");
        GpuRuntimeKernelCompilationException failure = new GpuRuntimeKernelCompilationException(
                "OpenCL build failed",
                context,
                cause
        );
        failure.addSuppressed(new IllegalArgumentException("secondary diagnostic"));

        Map<String, String> fields = GpuRuntimeLifecycleFields.failureFields(failure);

        assertEquals("true", fields.get("runtime.failure.present"));
        assertEquals(GpuRuntimeKernelCompilationException.class.getName(), fields.get("runtime.failure.type"));
        assertEquals("GpuRuntimeKernelCompilationException", fields.get("runtime.failure.simpleType"));
        assertEquals("JTG-RUNTIME-COMPILE-001", fields.get("runtime.failure.code"));
        assertEquals("KERNEL_COMPILATION", fields.get("runtime.failure.phase"));
        assertEquals("kernel-compilation", fields.get("runtime.failure.category"));
        assertEquals("OpenCL build failed", fields.get("runtime.failure.summary"));
        assertEquals("true", fields.get("runtime.failure.catchable"));
        assertEquals("true", fields.get("runtime.failure.diagnostic.present"));
        assertEquals("3", fields.get("runtime.failure.help.count"));
        assertEquals("1", fields.get("runtime.failure.suppressed.count"));
        assertEquals(IllegalStateException.class.getName(), fields.get("runtime.failure.cause.type"));
        assertEquals("driver rejected source", fields.get("runtime.failure.cause.message"));
        assertEquals("OPENCL", fields.get("runtime.failure.context.backendTarget"));
        assertEquals("kernel", fields.get("runtime.failure.context.kernelName"));
        assertEquals("javatogpu/sample/Demo/kernel.cl", fields.get("runtime.failure.context.kernelResource"));
    }

    @Test
    void failureFieldsClassifyGenericRuntimeExceptionsWithoutLosingLegacyKeys() {
        IllegalArgumentException failure = new IllegalArgumentException("bad compile flag");

        Map<String, String> fields = GpuRuntimeLifecycleFields.failureFields(failure);

        assertEquals(IllegalArgumentException.class.getName(), fields.get("runtime.failure.type"));
        assertEquals("bad compile flag", fields.get("runtime.failure.message"));
        assertEquals("JTG-RUNTIME-UNCLASSIFIED", fields.get("runtime.failure.code"));
        assertEquals("COMPILE_OPTIONS", fields.get("runtime.failure.phase"));
        assertEquals("compile-options", fields.get("runtime.failure.category"));
        assertEquals("false", fields.get("runtime.failure.catchable"));
        assertEquals("false", fields.get("runtime.failure.diagnostic.present"));
        assertEquals("0", fields.get("runtime.failure.help.count"));
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
        assertEquals("disabled", selectionFields.get("runtime.ir.productionMutation.status"));
        assertEquals("false", selectionFields.get("runtime.ir.productionMutation.enabled"));
        assertEquals("blocked", selectionFields.get("runtime.ir.productionMutation.productionGateStatus"));
        assertEquals("original", selectionFields.get("runtime.ir.productionMutation.selectedStage"));
        assertEquals("false", selectionFields.get("runtime.ir.productionMutation.optimizedSelected"));
        assertEquals("true", selectionFields.get("runtime.ir.productionMutation.optimizedDiffersFromOriginal"));
        assertEquals("true", selectionFields.get("runtime.ir.productionMutation.optimizedIrRejected"));
        assertEquals("optimizer-rollback", selectionFields.get("runtime.ir.productionMutation.fallbackDecision"));
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
