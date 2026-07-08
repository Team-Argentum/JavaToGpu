package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.time.Instant;
import java.util.Objects;

record OpenClValidationHistoryEntry(
        Instant generatedAtUtc,
        String requestedVendorLane,
        String backendName,
        String deviceLabel,
        String vendor,
        String driverVersion,
        String deviceVersion,
        String bucketSummary,
        String longRunningStatus,
        String workloadStatus,
        String irGpuSourceReviewStatus,
        String backendSourcePromotionContractStatus,
        String backendSourcePromotionWorkloadStatus,
        String productionPromotionExplainabilityStatus
) {

    OpenClValidationHistoryEntry {
        generatedAtUtc = Objects.requireNonNull(generatedAtUtc, "generatedAtUtc");
        requestedVendorLane = normalize(requestedVendorLane);
        backendName = normalize(backendName);
        deviceLabel = normalize(deviceLabel);
        vendor = normalize(vendor);
        driverVersion = normalize(driverVersion);
        deviceVersion = normalize(deviceVersion);
        bucketSummary = normalize(bucketSummary);
        longRunningStatus = normalize(longRunningStatus);
        workloadStatus = normalize(workloadStatus);
        irGpuSourceReviewStatus = normalize(irGpuSourceReviewStatus);
        backendSourcePromotionContractStatus = normalize(backendSourcePromotionContractStatus);
        backendSourcePromotionWorkloadStatus = normalize(backendSourcePromotionWorkloadStatus);
        productionPromotionExplainabilityStatus = normalize(productionPromotionExplainabilityStatus);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
