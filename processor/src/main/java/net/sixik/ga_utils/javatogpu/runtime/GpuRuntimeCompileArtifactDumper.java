package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Compatibility facade for runtime compile artifact dumping.
 */
public final class GpuRuntimeCompileArtifactDumper {

    public static final String RUNTIME_DEVICE_SELECTION_ARTIFACT =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCompileArtifactDumper.RUNTIME_DEVICE_SELECTION_ARTIFACT;
    public static final String BACKEND_COMPILER_FEEDBACK_ARTIFACT =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCompileArtifactDumper.BACKEND_COMPILER_FEEDBACK_ARTIFACT;
    public static final String RUNTIME_EXTENSION_PARTICIPATION_ARTIFACT =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCompileArtifactDumper.RUNTIME_EXTENSION_PARTICIPATION_ARTIFACT;
    public static final String RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT;
    public static final String RUNTIME_METHOD_TEST_EVIDENCE_ARTIFACT =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCompileArtifactDumper.RUNTIME_METHOD_TEST_EVIDENCE_ARTIFACT;
    public static final String RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD_DIRECTORY =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCompileArtifactDumper.RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD_DIRECTORY;

    private GpuRuntimeCompileArtifactDumper() {
    }

    public static GpuRuntimeCompileArtifactDump dump(GpuRuntimeCompileArtifactSnapshot snapshot) {
        return net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCompileArtifactDumper.dump(snapshot);
    }

    public static GpuRuntimeCompileArtifactDump dump(
            GpuRuntimeCompileArtifactSnapshot snapshot,
            GpuBackendCompilerFeedbackRegistry compilerFeedbackRegistry
    ) {
        return net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCompileArtifactDumper.dump(
                snapshot,
                compilerFeedbackRegistry.unwrap()
        );
    }
}
