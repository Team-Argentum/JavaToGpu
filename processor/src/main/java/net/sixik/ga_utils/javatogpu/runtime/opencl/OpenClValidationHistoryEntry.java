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
        String productionSourceSwitchingValidationStatus,
        String backendSourcePromotionContractStatus,
        String backendSourcePromotionWorkloadStatus,
        String productionPromotionExplainabilityStatus,
        String kernelLaunchAdvisoryStatus,
        String compilerResourceStatus,
        String extensionParticipationStatus
) {

    OpenClValidationHistoryEntry(
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
            String productionSourceSwitchingValidationStatus,
            String backendSourcePromotionContractStatus,
            String backendSourcePromotionWorkloadStatus,
            String productionPromotionExplainabilityStatus,
            String kernelLaunchAdvisoryStatus,
            String compilerResourceStatus
    ) {
        this(
                generatedAtUtc,
                requestedVendorLane,
                backendName,
                deviceLabel,
                vendor,
                driverVersion,
                deviceVersion,
                bucketSummary,
                longRunningStatus,
                workloadStatus,
                irGpuSourceReviewStatus,
                productionSourceSwitchingValidationStatus,
                backendSourcePromotionContractStatus,
                backendSourcePromotionWorkloadStatus,
                productionPromotionExplainabilityStatus,
                kernelLaunchAdvisoryStatus,
                compilerResourceStatus,
                "not recorded"
        );
    }

    OpenClValidationHistoryEntry(
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
            String productionSourceSwitchingValidationStatus,
            String backendSourcePromotionContractStatus,
            String backendSourcePromotionWorkloadStatus,
            String productionPromotionExplainabilityStatus,
            String kernelLaunchAdvisoryStatus
    ) {
        this(
                generatedAtUtc,
                requestedVendorLane,
                backendName,
                deviceLabel,
                vendor,
                driverVersion,
                deviceVersion,
                bucketSummary,
                longRunningStatus,
                workloadStatus,
                irGpuSourceReviewStatus,
                productionSourceSwitchingValidationStatus,
                backendSourcePromotionContractStatus,
                backendSourcePromotionWorkloadStatus,
                productionPromotionExplainabilityStatus,
                kernelLaunchAdvisoryStatus,
                "not recorded",
                "not recorded"
        );
    }

    OpenClValidationHistoryEntry(
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
            String productionSourceSwitchingValidationStatus,
            String backendSourcePromotionContractStatus,
            String backendSourcePromotionWorkloadStatus,
            String productionPromotionExplainabilityStatus
    ) {
        this(
                generatedAtUtc,
                requestedVendorLane,
                backendName,
                deviceLabel,
                vendor,
                driverVersion,
                deviceVersion,
                bucketSummary,
                longRunningStatus,
                workloadStatus,
                irGpuSourceReviewStatus,
                productionSourceSwitchingValidationStatus,
                backendSourcePromotionContractStatus,
                backendSourcePromotionWorkloadStatus,
                productionPromotionExplainabilityStatus,
                "not recorded",
                "not recorded",
                "not recorded"
        );
    }

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
        productionSourceSwitchingValidationStatus = normalize(productionSourceSwitchingValidationStatus);
        backendSourcePromotionContractStatus = normalize(backendSourcePromotionContractStatus);
        backendSourcePromotionWorkloadStatus = normalize(backendSourcePromotionWorkloadStatus);
        productionPromotionExplainabilityStatus = normalize(productionPromotionExplainabilityStatus);
        kernelLaunchAdvisoryStatus = normalize(kernelLaunchAdvisoryStatus);
        compilerResourceStatus = normalize(compilerResourceStatus);
        extensionParticipationStatus = normalize(extensionParticipationStatus);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
