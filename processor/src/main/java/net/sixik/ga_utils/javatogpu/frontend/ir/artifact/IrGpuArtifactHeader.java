package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

public record IrGpuArtifactHeader(
        String format,
        int schemaVersion,
        String compilerArtifact,
        String sourceFrontend
) {

    public static IrGpuArtifactHeader javaSourceV1() {
        return new IrGpuArtifactHeader(
                "javatogpu.irgpu.v1",
                1,
                "JavaToGpu",
                "java-source"
        );
    }
}
