package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Objects;

/**
 * Typed read-only guard emitted before any auto-vectorization rewrite mutates IR.
 */
public record GpuIrAutoVectorizationRewriteGuardDiagnostic(
        GpuIrAutoVectorizationRewriteGuardFamily family,
        String location,
        String message
) {
    public GpuIrAutoVectorizationRewriteGuardDiagnostic {
        family = Objects.requireNonNull(family, "family");
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    public String summary() {
        return "guard " + location + ": " + message;
    }

    public static GpuIrAutoVectorizationRewriteGuardDiagnostic fromLegacySummary(String diagnostic) {
        if (diagnostic == null || diagnostic.isBlank()) {
            throw new IllegalArgumentException("diagnostic must not be blank");
        }
        String prefix = "guard ";
        int separator = diagnostic.indexOf(": ");
        String location = diagnostic.startsWith(prefix) && separator > prefix.length()
                ? diagnostic.substring(prefix.length(), separator)
                : "unknown";
        String message = diagnostic.startsWith(prefix) && separator > prefix.length()
                ? diagnostic.substring(separator + 2)
                : diagnostic;
        return new GpuIrAutoVectorizationRewriteGuardDiagnostic(familyFromLegacySummary(diagnostic), location, message);
    }

    public static GpuIrAutoVectorizationRewriteGuardFamily familyFromLegacySummary(String diagnostic) {
        if (diagnostic == null || diagnostic.isBlank()) {
            throw new IllegalArgumentException("diagnostic must not be blank");
        }
        if (diagnostic.contains("unknown vector type")) {
            return GpuIrAutoVectorizationRewriteGuardFamily.UNKNOWN_VECTOR_TYPE;
        }
        if (diagnostic.contains("backend vector width")) {
            return GpuIrAutoVectorizationRewriteGuardFamily.BACKEND_VECTOR_WIDTH;
        }
        if (diagnostic.contains("backend double vector type")) {
            return GpuIrAutoVectorizationRewriteGuardFamily.BACKEND_DOUBLE_VECTOR;
        }
        if (diagnostic.contains("memory address space")) {
            return GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE;
        }
        if (diagnostic.contains("is also read by the candidate")) {
            return GpuIrAutoVectorizationRewriteGuardFamily.TARGET_SOURCE_ALIAS;
        }
        if (diagnostic.contains("writes source array")) {
            return GpuIrAutoVectorizationRewriteGuardFamily.NEIGHBOR_SOURCE_WRITE;
        }
        if (diagnostic.contains("writes target array")) {
            return GpuIrAutoVectorizationRewriteGuardFamily.NEIGHBOR_TARGET_WRITE;
        }
        if (diagnostic.contains("control-flow boundary")) {
            return GpuIrAutoVectorizationRewriteGuardFamily.CONTROL_FLOW_BOUNDARY;
        }
        if (diagnostic.contains("early-exit boundary")) {
            return GpuIrAutoVectorizationRewriteGuardFamily.EARLY_EXIT_BOUNDARY;
        }
        return GpuIrAutoVectorizationRewriteGuardFamily.OTHER;
    }
}
