package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record OpenClKernelLaunchAdvisoryDrift(
        String status,
        boolean regression,
        Counts current,
        Counts previous,
        Instant previousGeneratedAtUtc,
        String previousDriverVersion,
        String diagnostic
) {

    private static final Pattern COUNT_PATTERN = Pattern.compile(
            "(kernels|aligned|nonPreferred|driverSelected|unavailable|missing|blocking)=(-?\\d+)"
    );

    static OpenClKernelLaunchAdvisoryDrift compare(
            OpenClValidationHistoryEntry currentEntry,
            List<OpenClValidationHistoryEntry> historyEntries
    ) {
        Optional<Counts> currentCounts = Counts.parse(currentEntry.kernelLaunchAdvisoryStatus());
        if (currentCounts.isEmpty()) {
            return unavailable("current launch-advisory counts are unavailable");
        }

        for (OpenClValidationHistoryEntry previousEntry : historyEntries) {
            if (!compatibleEnvironment(currentEntry, previousEntry)
                    || currentEntry.generatedAtUtc().equals(previousEntry.generatedAtUtc())) {
                continue;
            }
            Optional<Counts> previousCounts = Counts.parse(previousEntry.kernelLaunchAdvisoryStatus());
            if (previousCounts.isEmpty()) {
                continue;
            }
            return classify(currentCounts.get(), previousCounts.get(), previousEntry);
        }
        return new OpenClKernelLaunchAdvisoryDrift(
                "no-baseline",
                false,
                currentCounts.get(),
                null,
                null,
                "unknown",
                "no previous compatible launch-advisory history entry was found"
        );
    }

    static OpenClKernelLaunchAdvisoryDrift noBaseline(Counts current) {
        return new OpenClKernelLaunchAdvisoryDrift(
                "no-baseline",
                false,
                current,
                null,
                null,
                "unknown",
                "launch-advisory history is not configured or contains no compatible baseline"
        );
    }

    String toMarkdown() {
        StringBuilder markdown = new StringBuilder();
        markdown.append("## Kernel Launch Advisory Drift\n\n");
        markdown.append("- Status: `").append(status).append("`\n");
        markdown.append("- Regression: `").append(regression).append("`\n");
        if (current != null) {
            markdown.append("- Current counts: `").append(current.toCompactString()).append("`\n");
        }
        if (previous != null) {
            markdown.append("- Previous generated at (UTC): `").append(previousGeneratedAtUtc).append("`\n");
            markdown.append("- Previous driver: `").append(inline(previousDriverVersion)).append("`\n");
            markdown.append("- Previous counts: `").append(previous.toCompactString()).append("`\n");
            markdown.append("- Delta: `").append(deltaText(current, previous)).append("`\n");
        }
        if (!diagnostic.isBlank()) {
            markdown.append("- Diagnostic: `").append(inline(diagnostic)).append("`\n");
        }
        markdown.append('\n');
        return markdown.toString();
    }

    String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("formatVersion=1\n");
        builder.append("status=").append(status).append('\n');
        builder.append("regression=").append(regression).append('\n');
        appendCounts(builder, "current", current);
        builder.append("baseline.present=").append(previous != null).append('\n');
        if (previous != null) {
            builder.append("baseline.generatedAtUtc=").append(previousGeneratedAtUtc).append('\n');
            builder.append("baseline.driverVersion=").append(previousDriverVersion).append('\n');
            appendCounts(builder, "baseline", previous);
            appendDelta(builder, current, previous);
        }
        builder.append("diagnostic=").append(propertyValue(diagnostic)).append('\n');
        return builder.toString();
    }

    private static OpenClKernelLaunchAdvisoryDrift classify(
            Counts current,
            Counts previous,
            OpenClValidationHistoryEntry previousEntry
    ) {
        if (current.equals(previous)) {
            return result("stable", false, current, previous, previousEntry, "launch-advisory counts are unchanged");
        }
        boolean regression = current.blocking() > previous.blocking()
                || current.missing() > previous.missing()
                || current.unavailable() > previous.unavailable()
                || current.nonPreferred() > previous.nonPreferred();
        if (regression) {
            return result(
                    "regressed",
                    true,
                    current,
                    previous,
                    previousEntry,
                    "one or more blocking, missing, unavailable, or non-preferred counts increased"
            );
        }
        boolean improved = current.blocking() < previous.blocking()
                || current.missing() < previous.missing()
                || current.unavailable() < previous.unavailable()
                || current.nonPreferred() < previous.nonPreferred()
                || current.aligned() > previous.aligned();
        return result(
                improved ? "improved" : "changed",
                false,
                current,
                previous,
                previousEntry,
                improved
                        ? "launch-advisory counts improved without a regression signal"
                        : "launch-advisory counts changed without a regression signal"
        );
    }

    private static OpenClKernelLaunchAdvisoryDrift result(
            String status,
            boolean regression,
            Counts current,
            Counts previous,
            OpenClValidationHistoryEntry previousEntry,
            String diagnostic
    ) {
        return new OpenClKernelLaunchAdvisoryDrift(
                status,
                regression,
                current,
                previous,
                previousEntry.generatedAtUtc(),
                previousEntry.driverVersion(),
                diagnostic
        );
    }

    private static OpenClKernelLaunchAdvisoryDrift unavailable(String diagnostic) {
        return new OpenClKernelLaunchAdvisoryDrift(
                "unavailable",
                false,
                null,
                null,
                null,
                "unknown",
                diagnostic
        );
    }

    private static boolean compatibleEnvironment(
            OpenClValidationHistoryEntry current,
            OpenClValidationHistoryEntry previous
    ) {
        return current.backendName().equals(previous.backendName())
                && current.deviceLabel().equals(previous.deviceLabel())
                && current.vendor().equals(previous.vendor())
                && compatibleLane(current.requestedVendorLane(), previous.requestedVendorLane());
    }

    private static boolean compatibleLane(String current, String previous) {
        return "unknown".equals(current)
                || "unknown".equals(previous)
                || current.equalsIgnoreCase(previous);
    }

    private static String deltaText(Counts current, Counts previous) {
        return "kernels=" + signed(current.kernels() - previous.kernels())
                + ", aligned=" + signed(current.aligned() - previous.aligned())
                + ", nonPreferred=" + signed(current.nonPreferred() - previous.nonPreferred())
                + ", driverSelected=" + signed(current.driverSelected() - previous.driverSelected())
                + ", unavailable=" + signed(current.unavailable() - previous.unavailable())
                + ", missing=" + signed(current.missing() - previous.missing())
                + ", blocking=" + signed(current.blocking() - previous.blocking());
    }

    private static void appendCounts(StringBuilder builder, String prefix, Counts counts) {
        builder.append(prefix).append(".present=").append(counts != null).append('\n');
        if (counts == null) {
            return;
        }
        builder.append(prefix).append(".kernels=").append(counts.kernels()).append('\n');
        builder.append(prefix).append(".aligned=").append(counts.aligned()).append('\n');
        builder.append(prefix).append(".nonPreferred=").append(counts.nonPreferred()).append('\n');
        builder.append(prefix).append(".driverSelected=").append(counts.driverSelected()).append('\n');
        builder.append(prefix).append(".unavailable=").append(counts.unavailable()).append('\n');
        builder.append(prefix).append(".missing=").append(counts.missing()).append('\n');
        builder.append(prefix).append(".blocking=").append(counts.blocking()).append('\n');
    }

    private static void appendDelta(StringBuilder builder, Counts current, Counts previous) {
        builder.append("delta.kernels=").append(current.kernels() - previous.kernels()).append('\n');
        builder.append("delta.aligned=").append(current.aligned() - previous.aligned()).append('\n');
        builder.append("delta.nonPreferred=").append(current.nonPreferred() - previous.nonPreferred()).append('\n');
        builder.append("delta.driverSelected=").append(current.driverSelected() - previous.driverSelected()).append('\n');
        builder.append("delta.unavailable=").append(current.unavailable() - previous.unavailable()).append('\n');
        builder.append("delta.missing=").append(current.missing() - previous.missing()).append('\n');
        builder.append("delta.blocking=").append(current.blocking() - previous.blocking()).append('\n');
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    private static String inline(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('`', '\'');
    }

    private static String propertyValue(String value) {
        return value == null ? "" : value.replace('\\', '/').replace('\r', ' ').replace('\n', ' ');
    }

    record Counts(
            int kernels,
            int aligned,
            int nonPreferred,
            int driverSelected,
            int unavailable,
            int missing,
            int blocking
    ) {

        static Optional<Counts> parse(String summary) {
            if (summary == null || !summary.startsWith("recorded (")) {
                return Optional.empty();
            }
            int kernels = Integer.MIN_VALUE;
            int aligned = Integer.MIN_VALUE;
            int nonPreferred = Integer.MIN_VALUE;
            int driverSelected = Integer.MIN_VALUE;
            int unavailable = Integer.MIN_VALUE;
            int missing = Integer.MIN_VALUE;
            int blocking = Integer.MIN_VALUE;
            Matcher matcher = COUNT_PATTERN.matcher(summary);
            while (matcher.find()) {
                int value = Integer.parseInt(matcher.group(2));
                switch (matcher.group(1)) {
                    case "kernels" -> kernels = value;
                    case "aligned" -> aligned = value;
                    case "nonPreferred" -> nonPreferred = value;
                    case "driverSelected" -> driverSelected = value;
                    case "unavailable" -> unavailable = value;
                    case "missing" -> missing = value;
                    case "blocking" -> blocking = value;
                    default -> {
                    }
                }
            }
            if (kernels == Integer.MIN_VALUE
                    || aligned == Integer.MIN_VALUE
                    || nonPreferred == Integer.MIN_VALUE
                    || driverSelected == Integer.MIN_VALUE
                    || unavailable == Integer.MIN_VALUE
                    || missing == Integer.MIN_VALUE
                    || blocking == Integer.MIN_VALUE) {
                return Optional.empty();
            }
            return Optional.of(new Counts(
                    kernels,
                    aligned,
                    nonPreferred,
                    driverSelected,
                    unavailable,
                    missing,
                    blocking
            ));
        }

        String toCompactString() {
            return "kernels=" + kernels
                    + ", aligned=" + aligned
                    + ", nonPreferred=" + nonPreferred
                    + ", driverSelected=" + driverSelected
                    + ", unavailable=" + unavailable
                    + ", missing=" + missing
                    + ", blocking=" + blocking;
        }
    }
}
