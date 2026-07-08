package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

public record IrGpuArtifactHeader(
        String format,
        int schemaVersion,
        String compilerArtifact,
        String sourceFrontend
) {

    public static IrGpuArtifactHeader javaSourceV1() {
        return sourceFrontendV1("java-source");
    }

    public static IrGpuArtifactHeader sourceFrontendV1(String sourceFrontend) {
        return new IrGpuArtifactHeader(
                "javatogpu.irgpu.v1",
                1,
                "JavaToGpu",
                sourceFrontend == null || sourceFrontend.isBlank() ? "java-source" : sourceFrontend
        );
    }
}
