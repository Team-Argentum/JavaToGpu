package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.time.Instant;
import java.util.Objects;

public record OpenClWorkloadValidationSummary(
        Instant completedAtUtc,
        String status,
        String perlinStatus,
        String packedBlobStatus,
        String packedNumericStatus,
        String c2me3dPackedRootBlobStatus,
        String imageStatus
) {

    public OpenClWorkloadValidationSummary {
        completedAtUtc = Objects.requireNonNull(completedAtUtc, "completedAtUtc");
        status = normalize(status);
        perlinStatus = normalize(perlinStatus);
        packedBlobStatus = normalize(packedBlobStatus);
        packedNumericStatus = normalize(packedNumericStatus);
        c2me3dPackedRootBlobStatus = normalize(c2me3dPackedRootBlobStatus);
        imageStatus = normalize(imageStatus);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
