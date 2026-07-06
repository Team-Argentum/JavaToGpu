package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Objects;

/**
 * Stable CI-facing families for safety validation failures that block optimizer readiness.
 */
enum GpuIrSafetyGateFamily {
    HELPER_MUTABLE_STORAGE_OPAQUE_ARGUMENT(
            "safety.helperMutableStorageOpaqueArgument",
            "mutable helper argument ",
            " must reference declared storage directly"
    ),
    HELPER_MUTABLE_STORAGE_READ_ONLY_ARGUMENT(
            "safety.helperMutableStorageReadOnlyArgument",
            "read-only storage cannot be used as mutable helper argument "
    ),
    HELPER_ARGUMENT_COUNT_MISMATCH(
            "safety.helperArgumentCountMismatch",
            "helper call argument count mismatch for "
    ),
    HELPER_ARGUMENT_TYPE_MISMATCH(
            "safety.helperArgumentTypeMismatch",
            "type mismatch in helper argument "
    ),
    GENERIC_ERROR("safety.error");

    private final String artifactValue;
    private final List<String> requiredFragments;

    GpuIrSafetyGateFamily(String artifactValue, String... requiredFragments) {
        this.artifactValue = requireNonBlank(artifactValue, "artifactValue");
        this.requiredFragments = List.of(requiredFragments);
        if (this.requiredFragments.stream().anyMatch(fragment -> fragment == null || fragment.isBlank())) {
            throw new IllegalArgumentException("requiredFragments must contain non-blank values");
        }
    }

    static GpuIrSafetyGateFamily fromSafetyError(String safetyError) {
        String message = requireNonBlank(Objects.requireNonNull(safetyError, "safetyError"), "safetyError");
        for (GpuIrSafetyGateFamily family : values()) {
            if (family.isTyped() && family.matches(message)) {
                return family;
            }
        }
        return GENERIC_ERROR;
    }

    String artifactValue() {
        return artifactValue;
    }

    boolean isTyped() {
        return this != GENERIC_ERROR;
    }

    private boolean matches(String safetyError) {
        return requiredFragments.stream().allMatch(safetyError::contains);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
