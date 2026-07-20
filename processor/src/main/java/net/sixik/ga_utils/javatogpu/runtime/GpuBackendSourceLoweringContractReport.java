package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Metadata-only contract report for backend source selection and lowering receipts.
 */
public record GpuBackendSourceLoweringContractReport(List<Entry> entries) {

    public record Entry(
            GpuBackendTarget backendTarget,
            String lowererId,
            String lowererVersion,
            boolean productionLoweringExpected,
            boolean previewLoweringExpected,
            GpuBackendSourceSelectionPlan sourceSelectionPlan,
            GpuBackendLoweringResult loweringResult
    ) {
        public Entry(
                GpuBackendTarget backendTarget,
                String lowererId,
                String lowererVersion,
                boolean productionLoweringExpected,
                GpuBackendSourceSelectionPlan sourceSelectionPlan,
                GpuBackendLoweringResult loweringResult
        ) {
            this(
                    backendTarget,
                    lowererId,
                    lowererVersion,
                    productionLoweringExpected,
                    false,
                    sourceSelectionPlan,
                    loweringResult
            );
        }

        public Entry {
            backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
            lowererId = lowererId == null || lowererId.isBlank() ? "unknown-lowerer" : lowererId.trim();
            lowererVersion = lowererVersion == null || lowererVersion.isBlank() ? "unknown" : lowererVersion.trim();
            sourceSelectionPlan = sourceSelectionPlan == null
                    ? GpuBackendSourceSelectionPlan.descriptorSource(backendTarget, "unknown", "source selection missing")
                    : sourceSelectionPlan;
            loweringResult = loweringResult == null
                    ? new GpuBackendLoweringResult(null, sourceSelectionPlan, null)
                    : loweringResult;
        }

        public boolean ready() {
            return blockers().isEmpty();
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public List<String> blockers() {
            ArrayList<String> blockers = new ArrayList<>();
            if (sourceSelectionPlan.backendTarget() != backendTarget) {
                blockers.add("source-selection-target-mismatch:" + sourceSelectionPlan.backendTarget());
            }
            if (loweringResult.stageResult().backendTarget() != backendTarget) {
                blockers.add("lowering-stage-target-mismatch:" + loweringResult.stageResult().backendTarget());
            }
            if (loweringResult.stageResult().stage() != GpuBackendPipelineStage.LOWER) {
                blockers.add("lowering-stage-unexpected:" + loweringResult.stageResult().stage().key());
            }
            Map<String, String> selectionFields = sourceSelectionPlan.artifactFields("contract.sourceSelection");
            if (!"true".equals(selectionFields.get("runtime.backend.sourceSelection.present"))) {
                blockers.add("portable-source-selection-fields-missing");
            }
            Map<String, String> loweringFields = loweringResult.artifactFields("contract.lowering");
            if (!"true".equals(loweringFields.get("runtime.backend.lowering.present"))) {
                blockers.add("portable-lowering-fields-missing");
            }

            if (productionLoweringExpected) {
                validateExpectedSourceLowering(blockers, "production");
            } else if (previewLoweringExpected) {
                validateExpectedSourceLowering(blockers, "preview");
            } else {
                validateUnsupportedLowering(blockers);
            }
            return List.copyOf(blockers);
        }

        private void validateExpectedSourceLowering(List<String> blockers, String expectationKind) {
            GpuBackendModuleArtifact module = loweringResult.moduleArtifact();
            if (loweringResult.stageResult().status() != GpuBackendStageStatus.SUCCEEDED) {
                blockers.add(expectationKind + "-lowering-stage-not-succeeded:" + loweringResult.stageResult().status());
            }
            if (!loweringResult.lowered()) {
                blockers.add(expectationKind + "-lowering-not-lowered");
            }
            if (module.backendTarget() != backendTarget) {
                blockers.add(expectationKind + "-module-target-mismatch:" + module.backendTarget());
            }
            if (!module.sourceAvailable()) {
                blockers.add(expectationKind + "-module-source-missing");
            }
            if (!module.sourceLikeFormat()) {
                blockers.add(expectationKind + "-module-format-not-source-like:" + module.moduleFormat().key());
            }
            if (!module.formatMatchesBackendTarget()) {
                blockers.add(expectationKind + "-module-format-target-mismatch:" + module.moduleFormat().key());
            }
            if (sourceSelectionPlan.selectedSource().isBlank()) {
                blockers.add(expectationKind + "-source-selection-empty");
            }
            if (sourceSelectionPlan.runtimeLoadMode().isBlank()) {
                blockers.add(expectationKind + "-runtime-load-mode-empty");
            }
        }

        private void validateUnsupportedLowering(List<String> blockers) {
            String backendName = backendTarget.name().toLowerCase(java.util.Locale.ROOT);
            if (loweringResult.stageResult().status() != GpuBackendStageStatus.UNSUPPORTED) {
                blockers.add("planned-lowering-stage-not-unsupported:" + loweringResult.stageResult().status());
            }
            if (loweringResult.lowered()) {
                blockers.add("planned-backend-lowered-before-contract-update");
            }
            if (!sourceSelectionPlan.selectedSource().equals(backendName + "-lowerer-unavailable")) {
                blockers.add("planned-selected-source-unexpected:" + sourceSelectionPlan.selectedSource());
            }
            if (!"irgpu-unlowered".equals(sourceSelectionPlan.payloadFormat())) {
                blockers.add("planned-payload-format-unexpected:" + sourceSelectionPlan.payloadFormat());
            }
            if (!sourceSelectionPlan.runtimeLoadMode().equals(backendName + "-unsupported")) {
                blockers.add("planned-runtime-load-mode-unexpected:" + sourceSelectionPlan.runtimeLoadMode());
            }
            if (!sourceSelectionPlan.blockers().contains(backendName + "-lowerer-not-implemented")) {
                blockers.add("planned-lowerer-blocker-missing:" + backendName + "-lowerer-not-implemented");
            }
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.backend.sourceLoweringContract.entry"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
            fields.put(normalizedPrefix + ".lowerer.id", lowererId);
            fields.put(normalizedPrefix + ".lowerer.version", lowererVersion);
            fields.put(normalizedPrefix + ".productionLoweringExpected", Boolean.toString(productionLoweringExpected));
            fields.put(normalizedPrefix + ".previewLoweringExpected", Boolean.toString(previewLoweringExpected));
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".lowering.stage.status", loweringResult.stageResult().status().name());
            fields.put(normalizedPrefix + ".lowering.lowered", Boolean.toString(loweringResult.lowered()));
            fields.put(normalizedPrefix + ".sourceSelection.selectedSource", sourceSelectionPlan.selectedSource());
            fields.put(normalizedPrefix + ".sourceSelection.payloadFormat", sourceSelectionPlan.payloadFormat());
            fields.put(normalizedPrefix + ".sourceSelection.runtimeLoadMode", sourceSelectionPlan.runtimeLoadMode());
            fields.put(normalizedPrefix + ".module.format", loweringResult.moduleArtifact().format());
            fields.put(normalizedPrefix + ".module.format.canonical", loweringResult.moduleArtifact().moduleFormat().key());
            fields.put(normalizedPrefix + ".module.sourceAvailable", Boolean.toString(loweringResult.moduleArtifact().sourceAvailable()));
            List<String> blockers = blockers();
            fields.put(normalizedPrefix + ".blocker.count", Integer.toString(blockers.size()));
            for (int index = 0; index < blockers.size(); index++) {
                fields.put(normalizedPrefix + ".blocker." + index, blockers.get(index));
            }
            return Collections.unmodifiableMap(fields);
        }
    }

    public GpuBackendSourceLoweringContractReport {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static GpuBackendSourceLoweringContractReport inspectBuiltIns() {
        return new GpuBackendSourceLoweringContractReport(List.of(
                inspectTarget(GpuBackendTarget.OPENCL, true, false),
                inspectTarget(GpuBackendTarget.CUDA, false, true),
                inspectTarget(GpuBackendTarget.VULKAN, false, false),
                inspectTarget(GpuBackendTarget.METAL, false, false)
        ));
    }

    public boolean ready() {
        return blockers().isEmpty();
    }

    public String status() {
        return ready() ? "ready" : "blocked";
    }

    public List<String> blockers() {
        ArrayList<String> blockers = new ArrayList<>();
        for (Entry entry : entries) {
            for (String blocker : entry.blockers()) {
                blockers.add(entry.backendTarget().name() + ":" + blocker);
            }
        }
        return List.copyOf(blockers);
    }

    public String firstBlocker() {
        return blockers().stream().findFirst().orElse("none");
    }

    public long readyEntryCount() {
        return entries.stream().filter(Entry::ready).count();
    }

    public long plannedUnsupportedCount() {
        return entries.stream()
                .filter(entry -> !entry.productionLoweringExpected())
                .filter(entry -> !entry.previewLoweringExpected())
                .filter(entry -> entry.loweringResult().stageResult().status() == GpuBackendStageStatus.UNSUPPORTED)
                .count();
    }

    public long plannedEntryCount() {
        return entries.stream()
                .filter(entry -> !entry.productionLoweringExpected())
                .filter(entry -> !entry.previewLoweringExpected())
                .count();
    }

    public long previewLoweringCount() {
        return entries.stream().filter(Entry::previewLoweringExpected).count();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.backend.sourceLoweringContract"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(normalizedPrefix + ".entry.ready.count", Long.toString(readyEntryCount()));
        fields.put(normalizedPrefix + ".planned.unsupported.count", Long.toString(plannedUnsupportedCount()));
        fields.put(normalizedPrefix + ".planned.count", Long.toString(plannedEntryCount()));
        fields.put(normalizedPrefix + ".preview.lowering.count", Long.toString(previewLoweringCount()));
        List<String> blockers = blockers();
        fields.put(normalizedPrefix + ".blocker.count", Integer.toString(blockers.size()));
        for (int index = 0; index < blockers.size(); index++) {
            fields.put(normalizedPrefix + ".blocker." + index, blockers.get(index));
        }
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(normalizedPrefix + ".entry." + index));
        }
        fields.put("runtime.backend.sourceLoweringContract.present", "true");
        fields.put("runtime.backend.sourceLoweringContract.status", status());
        fields.put("runtime.backend.sourceLoweringContract.entry.count", Integer.toString(entries.size()));
        fields.put("runtime.backend.sourceLoweringContract.entry.ready.count", Long.toString(readyEntryCount()));
        fields.put("runtime.backend.sourceLoweringContract.planned.unsupported.count", Long.toString(plannedUnsupportedCount()));
        fields.put("runtime.backend.sourceLoweringContract.preview.lowering.count", Long.toString(previewLoweringCount()));
        fields.put("runtime.backend.sourceLoweringContract.firstBlocker", firstBlocker());
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Backend source/lowering contract: ").append(status()).append('\n');
        builder.append("Entries: ").append(entries.size()).append('\n');
        builder.append("Ready entries: ").append(readyEntryCount()).append('/').append(entries.size()).append('\n');
        builder.append("Planned unsupported entries: ")
                .append(plannedUnsupportedCount())
                .append('/')
                .append(plannedEntryCount())
                .append('\n');
        builder.append("Preview source lowerings: ").append(previewLoweringCount()).append('\n');
        for (Entry entry : entries) {
            builder.append("- ")
                    .append(entry.backendTarget())
                    .append(": status=")
                    .append(entry.status())
                    .append(", lowerStage=")
                    .append(entry.loweringResult().stageResult().status())
                    .append(", selectedSource=")
                    .append(entry.sourceSelectionPlan().selectedSource())
                    .append(", moduleFormat=")
                    .append(entry.loweringResult().moduleArtifact().moduleFormat().key())
                    .append('\n');
        }
        if (!blockers().isEmpty()) {
            builder.append('\n').append("Blockers:").append('\n');
            for (String blocker : blockers()) {
                builder.append("- ").append(blocker).append('\n');
            }
        }
        return builder.toString();
    }

    private static Entry inspectTarget(
            GpuBackendTarget backendTarget,
            boolean productionLoweringExpected,
            boolean previewLoweringExpected
    ) {
        GpuBackendLowerer lowerer = GpuBackendLowerers.forTarget(backendTarget);
        GpuRuntimeCompileRequest compileRequest = sampleCompileRequest(backendTarget);
        GpuBackendSourceSelectionPlan plan = lowerer.sourceSelectionPlan(compileRequest);
        GpuBackendLoweringResult result;
        if (productionLoweringExpected || previewLoweringExpected) {
            try {
                result = lowerer.lowerWithStageResult(compileRequest);
            } catch (RuntimeException exception) {
                result = new GpuBackendLoweringResult(
                        GpuBackendStageResult.failed(
                                GpuBackendPipelineStage.LOWER,
                                backendTarget,
                                "backend lowering failed during contract inspection",
                                exception,
                                List.of("metadata-only source/lowering contract inspection")
                        ),
                        plan,
                        GpuBackendModuleArtifact.unknown()
                );
            }
        } else {
            result = GpuBackendLoweringResult.unsupported(
                    backendTarget,
                    plan,
                    plan.blockers(),
                    plan.diagnostics()
            );
        }
        return new Entry(
                backendTarget,
                lowerer.extensionId(),
                lowerer.lowererVersion(),
                productionLoweringExpected,
                previewLoweringExpected,
                plan,
                result
        );
    }

    private static GpuRuntimeCompileRequest sampleCompileRequest(GpuBackendTarget backendTarget) {
        if (backendTarget == GpuBackendTarget.CUDA) {
            return new GpuRuntimeCompileRequest(
                    sampleCudaDescriptor(),
                    GpuRuntimeCompileOptions.defaults(backendTarget),
                    GpuRuntimeDeviceProfile.generic(backendTarget, backendTarget.name()),
                    java.util.Optional.of(sampleCudaIrGpuArtifact())
            );
        }
        return new GpuRuntimeCompileRequest(
                sampleDescriptor(),
                GpuRuntimeCompileOptions.defaults(backendTarget),
                GpuRuntimeDeviceProfile.generic(backendTarget, backendTarget.name())
        );
    }

    private static GpuKernelDescriptor sampleCudaDescriptor() {
        return new GpuKernelDescriptor(
                "gpu_irgpu_entry",
                "javatogpu/contract/source-lowering/kernel.cu",
                "",
                List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private static IrGpuArtifact sampleCudaIrGpuArtifact() {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "gpu_irgpu_entry",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "gpu_irgpu_entry",
                                """
                                        body
                                          var int id = intrinsic(get_global_id template="" args=[0])
                                          set output[id] = input[id] + scale
                                        """,
                                List.of()
                        ))
                ),
                List.of(
                        new IrGpuEntryParameter("input", "float[]", "GLOBAL", true, List.of("const")),
                        new IrGpuEntryParameter("scale", "float", "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of())
                ),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(IrGpuBackendOutput.openClSource("inline://contract/source-lowering/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static GpuKernelDescriptor sampleDescriptor() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/contract/source-lowering/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
    }
}
