package net.sixik.ga_utils.javatogpu.frontend.asm;

import java.util.Map;
import java.util.Objects;

/**
 * Stable machine-readable context for ASM frontend failures.
 */
public record AsmFrontendFailureMetadata(
        String family,
        String ownerInternalName,
        String methodName,
        String methodDescriptor,
        int instructionIndex,
        int lineNumber,
        String opcodeName,
        String detail
) {
    public AsmFrontendFailureMetadata {
        if (family == null || family.isBlank()) {
            throw new IllegalArgumentException("family must not be blank");
        }
        if (ownerInternalName == null || ownerInternalName.isBlank()) {
            throw new IllegalArgumentException("ownerInternalName must not be blank");
        }
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (methodDescriptor == null || methodDescriptor.isBlank()) {
            throw new IllegalArgumentException("methodDescriptor must not be blank");
        }
        if (instructionIndex < 0) {
            throw new IllegalArgumentException("instructionIndex must not be negative");
        }
        opcodeName = normalizeNullable(opcodeName);
        detail = normalizeNullable(detail);
    }

    public String methodKey() {
        return ownerInternalName + "." + methodName + methodDescriptor;
    }

    public String summary() {
        StringBuilder builder = new StringBuilder();
        builder.append(family).append(' ').append(methodKey());
        if (instructionIndex > 0) {
            builder.append(" instruction=").append(instructionIndex);
        }
        if (lineNumber >= 0) {
            builder.append(" line=").append(lineNumber);
        }
        if (!opcodeName.isBlank()) {
            builder.append(" opcode=").append(opcodeName);
        }
        return builder.toString();
    }

    public Map<String, String> artifactFields(String prefix) {
        String safePrefix = prefix == null || prefix.isBlank() ? "asmFailure" : prefix;
        java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put(safePrefix + ".summary", summary());
        fields.put(safePrefix + ".family", family);
        fields.put(safePrefix + ".owner", ownerInternalName);
        fields.put(safePrefix + ".method", methodName);
        fields.put(safePrefix + ".descriptor", methodDescriptor);
        fields.put(safePrefix + ".methodKey", methodKey());
        fields.put(safePrefix + ".instructionIndex", Integer.toString(instructionIndex));
        fields.put(safePrefix + ".lineNumber", Integer.toString(lineNumber));
        if (!opcodeName.isBlank()) {
            fields.put(safePrefix + ".opcode", opcodeName);
        }
        if (!detail.isBlank()) {
            fields.put(safePrefix + ".detail", detail);
        }
        return Map.copyOf(fields);
    }

    private static String normalizeNullable(String value) {
        return Objects.requireNonNullElse(value, "");
    }
}
