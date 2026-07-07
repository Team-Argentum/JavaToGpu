package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.util.List;

/**
 * Compares an IrGpu-derived OpenCL source payload with the generated descriptor source.
 *
 * <p>This is diagnostic-only for now. The production lowerer still compiles the generated descriptor source until the
 * reconstructed source path has enough parity and runtime-equivalence evidence to be promoted safely.
 */
public record OpenClIrGpuSourceParityComparison(
        boolean checked,
        boolean matched,
        List<String> diagnostics
) {

    public OpenClIrGpuSourceParityComparison {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static OpenClIrGpuSourceParityComparison compare(String reconstructedSource, String descriptorSource) {
        if (reconstructedSource == null || reconstructedSource.isBlank()) {
            return new OpenClIrGpuSourceParityComparison(
                    false,
                    false,
                    List.of("sourceParity.checked=false", "sourceParity.reason=reconstructed-source-missing")
            );
        }
        if (descriptorSource == null || descriptorSource.isBlank()) {
            return new OpenClIrGpuSourceParityComparison(
                    false,
                    false,
                    List.of("sourceParity.checked=false", "sourceParity.reason=descriptor-source-missing")
            );
        }

        String reconstructedCanonical = canonicalize(reconstructedSource);
        String descriptorCanonical = canonicalize(descriptorSource);
        boolean matched = reconstructedCanonical.equals(descriptorCanonical);
        return new OpenClIrGpuSourceParityComparison(
                true,
                matched,
                List.of(
                        "sourceParity.checked=true",
                        "sourceParity.matched=" + matched,
                        "sourceParity.reconstructedLength=" + reconstructedSource.length(),
                        "sourceParity.descriptorLength=" + descriptorSource.length(),
                        "sourceParity.normalizedReconstructedLength=" + reconstructedCanonical.length(),
                        "sourceParity.normalizedDescriptorLength=" + descriptorCanonical.length()
                )
        );
    }

    private static String canonicalize(String source) {
        return source.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
