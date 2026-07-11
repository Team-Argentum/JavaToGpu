package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class OpenClKernelLaunchAdvisoryNegativeFixtureCli {

    private OpenClKernelLaunchAdvisoryNegativeFixtureCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || args[0] == null || args[0].isBlank()) {
            throw new IllegalArgumentException("Expected validation-history baseline file path");
        }
        Path baselineFile = Path.of(args[0]);
        if (!Files.isRegularFile(baselineFile)) {
            throw new IllegalStateException("Validation-history baseline is missing: " + baselineFile);
        }

        List<OpenClValidationHistoryEntry> history = new ArrayList<>(
                OpenClValidationHistoryIO.readAll(baselineFile)
        );
        for (int historyIndex = 0; historyIndex < history.size(); historyIndex++) {
            OpenClValidationHistoryEntry entry = history.get(historyIndex);
            Optional<List<OpenClKernelLaunchAdvisorySummary.Entry>> parsed =
                    OpenClKernelLaunchAdvisorySummary.parseHistoryEntries(
                            entry.kernelLaunchAdvisoryStatus()
                    );
            if (parsed.isEmpty()) {
                continue;
            }
            List<OpenClKernelLaunchAdvisorySummary.Entry> advisories = new ArrayList<>(parsed.get());
            for (int advisoryIndex = 0; advisoryIndex < advisories.size(); advisoryIndex++) {
                OpenClKernelLaunchAdvisorySummary.Entry advisory = advisories.get(advisoryIndex);
                Optional<Integer> kernelMax = parseNonNegativeInt(advisory.kernelMax());
                if (kernelMax.isEmpty()) {
                    continue;
                }
                int fixtureKernelMax = increasedKernelMax(kernelMax.get());
                advisories.set(advisoryIndex, new OpenClKernelLaunchAdvisorySummary.Entry(
                        advisory.kernelResource(),
                        advisory.status(),
                        advisory.localShape(),
                        advisory.requestedSize(),
                        Integer.toString(fixtureKernelMax),
                        advisory.preferredMultiple(),
                        advisory.matched(),
                        advisory.blocking()
                ));
                String fixtureSummary = new OpenClKernelLaunchAdvisorySummary(
                        "recorded",
                        advisories,
                        ""
                ).toHistorySummary();
                history.set(historyIndex, withAdvisoryStatus(entry, fixtureSummary));
                OpenClValidationHistoryIO.writeAll(baselineFile, history);
                System.out.println(
                        "Prepared launch-advisory regression fixture for "
                                + advisory.kernelResource()
                                + ": baseline kernel max "
                                + kernelMax.get()
                                + " -> "
                                + fixtureKernelMax
                );
                return;
            }
        }
        throw new IllegalStateException(
                "Validation-history baseline has no per-kernel snapshot with a numeric kernel maximum: "
                        + baselineFile
        );
    }

    private static OpenClValidationHistoryEntry withAdvisoryStatus(
            OpenClValidationHistoryEntry entry,
            String advisoryStatus
    ) {
        return new OpenClValidationHistoryEntry(
                entry.generatedAtUtc(),
                entry.requestedVendorLane(),
                entry.backendName(),
                entry.deviceLabel(),
                entry.vendor(),
                entry.driverVersion(),
                entry.deviceVersion(),
                entry.bucketSummary(),
                entry.longRunningStatus(),
                entry.workloadStatus(),
                entry.irGpuSourceReviewStatus(),
                entry.productionSourceSwitchingValidationStatus(),
                entry.backendSourcePromotionContractStatus(),
                entry.backendSourcePromotionWorkloadStatus(),
                entry.productionPromotionExplainabilityStatus(),
                advisoryStatus
        );
    }

    private static Optional<Integer> parseNonNegativeInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static int increasedKernelMax(int kernelMax) {
        if (kernelMax == Integer.MAX_VALUE) {
            throw new IllegalStateException("Kernel maximum cannot be increased for fixture generation");
        }
        long doubled = Math.max((long) kernelMax + 1L, (long) kernelMax * 2L);
        return doubled > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) doubled;
    }
}
