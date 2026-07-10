package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record OpenClKernelLaunchAdvisoryDrift(
        String status,
        boolean regression,
        Counts current,
        Counts previous,
        Instant previousGeneratedAtUtc,
        String previousDriverVersion,
        boolean kernelComparisonAvailable,
        List<KernelChange> kernelChanges,
        String diagnostic
) {

    private static final Pattern COUNT_PATTERN = Pattern.compile(
            "(kernels|aligned|nonPreferred|driverSelected|unavailable|missing|blocking)=(-?\\d+)"
    );

    OpenClKernelLaunchAdvisoryDrift {
        kernelChanges = kernelChanges == null ? List.of() : List.copyOf(kernelChanges);
    }

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
            KernelComparison kernelComparison = KernelComparison.compare(
                    OpenClKernelLaunchAdvisorySummary.parseHistoryEntries(
                            currentEntry.kernelLaunchAdvisoryStatus()
                    ),
                    OpenClKernelLaunchAdvisorySummary.parseHistoryEntries(
                            previousEntry.kernelLaunchAdvisoryStatus()
                    )
            );
            return classify(currentCounts.get(), previousCounts.get(), previousEntry, kernelComparison);
        }
        return new OpenClKernelLaunchAdvisoryDrift(
                "no-baseline",
                false,
                currentCounts.get(),
                null,
                null,
                "unknown",
                false,
                List.of(),
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
                false,
                List.of(),
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
        markdown.append("- Per-kernel comparison: `")
                .append(kernelComparisonAvailable ? "available" : "unavailable")
                .append("`\n");
        if (kernelComparisonAvailable) {
            markdown.append("- Per-kernel changes: `").append(kernelChanges.size()).append("`\n");
            markdown.append("- Per-kernel regressions: `").append(countChanges("regressed")).append("`\n");
            markdown.append("- Per-kernel improvements: `").append(countChanges("improved")).append("`\n");
        }
        if (!diagnostic.isBlank()) {
            markdown.append("- Diagnostic: `").append(inline(diagnostic)).append("`\n");
        }
        if (!kernelChanges.isEmpty()) {
            markdown.append('\n');
            markdown.append("| Kernel resource | Classification | Status | Kernel max | Preferred | Diagnostic |\n");
            markdown.append("| --- | --- | --- | ---: | ---: | --- |\n");
            for (KernelChange change : kernelChanges) {
                markdown.append("| `").append(table(change.resource())).append("` | `")
                        .append(table(change.classification())).append("` | `")
                        .append(table(transition(change.previous(), change.current(), EntryField.STATUS))).append("` | `")
                        .append(table(transition(change.previous(), change.current(), EntryField.KERNEL_MAX))).append("` | `")
                        .append(table(transition(change.previous(), change.current(), EntryField.PREFERRED))).append("` | `")
                        .append(table(change.diagnostic())).append("` |\n");
            }
        }
        markdown.append('\n');
        return markdown.toString();
    }

    String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("formatVersion=2\n");
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
        builder.append("kernelComparison.available=").append(kernelComparisonAvailable).append('\n');
        builder.append("kernelChange.count=").append(kernelChanges.size()).append('\n');
        builder.append("kernelChange.regressed.count=").append(countChanges("regressed")).append('\n');
        builder.append("kernelChange.improved.count=").append(countChanges("improved")).append('\n');
        builder.append("kernelChange.changed.count=").append(countChanges("changed")).append('\n');
        builder.append("kernelChange.added.count=").append(countChanges("added")).append('\n');
        for (int index = 0; index < kernelChanges.size(); index++) {
            appendKernelChange(builder, "kernelChange." + index, kernelChanges.get(index));
        }
        builder.append("diagnostic=").append(propertyValue(diagnostic)).append('\n');
        return builder.toString();
    }

    private static OpenClKernelLaunchAdvisoryDrift classify(
            Counts current,
            Counts previous,
            OpenClValidationHistoryEntry previousEntry,
            KernelComparison kernelComparison
    ) {
        boolean aggregateRegression = current.blocking() > previous.blocking()
                || current.missing() > previous.missing()
                || current.unavailable() > previous.unavailable()
                || current.nonPreferred() > previous.nonPreferred();
        if (kernelComparison.regressionCount() > 0) {
            return result(
                    "regressed",
                    true,
                    current,
                    previous,
                    previousEntry,
                    kernelComparison,
                    "per-kernel launch advisory regressed for " + kernelComparison.firstRegressionResource()
            );
        }
        if (aggregateRegression) {
            return result(
                    "regressed",
                    true,
                    current,
                    previous,
                    previousEntry,
                    kernelComparison,
                    "one or more blocking, missing, unavailable, or non-preferred counts increased"
            );
        }
        if (current.equals(previous) && kernelComparison.changes().isEmpty()) {
            return result(
                    "stable",
                    false,
                    current,
                    previous,
                    previousEntry,
                    kernelComparison,
                    kernelComparison.available()
                            ? "launch-advisory counts and per-kernel snapshots are unchanged"
                            : "launch-advisory counts are unchanged; per-kernel baseline is unavailable"
            );
        }
        boolean aggregateImproved = current.blocking() < previous.blocking()
                || current.missing() < previous.missing()
                || current.unavailable() < previous.unavailable()
                || current.nonPreferred() < previous.nonPreferred()
                || current.aligned() > previous.aligned();
        boolean onlyImprovements = kernelComparison.available()
                && !kernelComparison.changes().isEmpty()
                && kernelComparison.improvementCount() == kernelComparison.changes().size();
        boolean improved = aggregateImproved || onlyImprovements;
        return result(
                improved ? "improved" : "changed",
                false,
                current,
                previous,
                previousEntry,
                kernelComparison,
                improved
                        ? "launch-advisory evidence improved without a regression signal"
                        : "launch-advisory evidence changed without a regression signal"
        );
    }

    private static OpenClKernelLaunchAdvisoryDrift result(
            String status,
            boolean regression,
            Counts current,
            Counts previous,
            OpenClValidationHistoryEntry previousEntry,
            KernelComparison kernelComparison,
            String diagnostic
    ) {
        return new OpenClKernelLaunchAdvisoryDrift(
                status,
                regression,
                current,
                previous,
                previousEntry.generatedAtUtc(),
                previousEntry.driverVersion(),
                kernelComparison.available(),
                kernelComparison.changes(),
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
                false,
                List.of(),
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

    private int countChanges(String classification) {
        int count = 0;
        for (KernelChange change : kernelChanges) {
            if (classification.equals(change.classification())) {
                count++;
            }
        }
        return count;
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

    private static void appendKernelChange(StringBuilder builder, String prefix, KernelChange change) {
        builder.append(prefix).append(".resource=").append(propertyValue(change.resource())).append('\n');
        builder.append(prefix).append(".classification=").append(change.classification()).append('\n');
        appendKernelEntry(builder, prefix + ".previous", change.previous());
        appendKernelEntry(builder, prefix + ".current", change.current());
        builder.append(prefix).append(".diagnostic=").append(propertyValue(change.diagnostic())).append('\n');
    }

    private static void appendKernelEntry(
            StringBuilder builder,
            String prefix,
            OpenClKernelLaunchAdvisorySummary.Entry entry
    ) {
        builder.append(prefix).append(".present=").append(entry != null).append('\n');
        if (entry == null) {
            return;
        }
        builder.append(prefix).append(".status=").append(propertyValue(entry.status())).append('\n');
        builder.append(prefix).append(".localShape=").append(propertyValue(entry.localShape())).append('\n');
        builder.append(prefix).append(".requestedSize=").append(propertyValue(entry.requestedSize())).append('\n');
        builder.append(prefix).append(".kernelMax=").append(propertyValue(entry.kernelMax())).append('\n');
        builder.append(prefix).append(".preferredMultiple=").append(propertyValue(entry.preferredMultiple())).append('\n');
        builder.append(prefix).append(".matched=").append(propertyValue(entry.matched())).append('\n');
        builder.append(prefix).append(".blocking=").append(propertyValue(entry.blocking())).append('\n');
    }

    private static String transition(
            OpenClKernelLaunchAdvisorySummary.Entry previous,
            OpenClKernelLaunchAdvisorySummary.Entry current,
            EntryField field
    ) {
        String previousValue = previous == null ? "missing" : field.value(previous);
        String currentValue = current == null ? "missing" : field.value(current);
        return previousValue.equals(currentValue) ? currentValue : previousValue + " -> " + currentValue;
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    private static String inline(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('`', '\'');
    }

    private static String table(String value) {
        return inline(value).replace("|", "\\|");
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

    record KernelChange(
            String resource,
            String classification,
            OpenClKernelLaunchAdvisorySummary.Entry previous,
            OpenClKernelLaunchAdvisorySummary.Entry current,
            String diagnostic
    ) {
    }

    private record KernelComparison(boolean available, List<KernelChange> changes) {

        private KernelComparison {
            changes = changes == null ? List.of() : List.copyOf(changes);
        }

        static KernelComparison compare(
                Optional<List<OpenClKernelLaunchAdvisorySummary.Entry>> currentEntries,
                Optional<List<OpenClKernelLaunchAdvisorySummary.Entry>> previousEntries
        ) {
            if (currentEntries.isEmpty() || previousEntries.isEmpty()) {
                return new KernelComparison(false, List.of());
            }
            Map<String, OpenClKernelLaunchAdvisorySummary.Entry> currentByResource = indexByResource(
                    currentEntries.get()
            );
            Map<String, OpenClKernelLaunchAdvisorySummary.Entry> previousByResource = indexByResource(
                    previousEntries.get()
            );
            Set<String> resources = new LinkedHashSet<>(previousByResource.keySet());
            resources.addAll(currentByResource.keySet());
            ArrayList<KernelChange> changes = new ArrayList<>();
            for (String resource : resources) {
                OpenClKernelLaunchAdvisorySummary.Entry previous = previousByResource.get(resource);
                OpenClKernelLaunchAdvisorySummary.Entry current = currentByResource.get(resource);
                if (previous == null) {
                    changes.add(new KernelChange(
                            resource,
                            "added",
                            null,
                            current,
                            "kernel resource was added to the advisory snapshot"
                    ));
                } else if (current == null) {
                    changes.add(new KernelChange(
                            resource,
                            "regressed",
                            previous,
                            null,
                            "kernel resource is missing from the current advisory snapshot"
                    ));
                } else if (!previous.equals(current)) {
                    changes.add(classifyKernelChange(resource, previous, current));
                }
            }
            return new KernelComparison(true, changes);
        }

        int regressionCount() {
            return count("regressed");
        }

        int improvementCount() {
            return count("improved");
        }

        String firstRegressionResource() {
            for (KernelChange change : changes) {
                if ("regressed".equals(change.classification())) {
                    return change.resource();
                }
            }
            return "unknown";
        }

        private int count(String classification) {
            int count = 0;
            for (KernelChange change : changes) {
                if (classification.equals(change.classification())) {
                    count++;
                }
            }
            return count;
        }

        private static Map<String, OpenClKernelLaunchAdvisorySummary.Entry> indexByResource(
                List<OpenClKernelLaunchAdvisorySummary.Entry> entries
        ) {
            LinkedHashMap<String, OpenClKernelLaunchAdvisorySummary.Entry> indexed = new LinkedHashMap<>();
            for (OpenClKernelLaunchAdvisorySummary.Entry entry : entries) {
                indexed.put(entry.kernelResource(), entry);
            }
            return indexed;
        }

        private static KernelChange classifyKernelChange(
                String resource,
                OpenClKernelLaunchAdvisorySummary.Entry previous,
                OpenClKernelLaunchAdvisorySummary.Entry current
        ) {
            ArrayList<String> diagnostics = new ArrayList<>();
            boolean regression = false;
            boolean improvement = false;

            if (!previous.status().equals(current.status())) {
                diagnostics.add("status " + previous.status() + " -> " + current.status());
                int previousRank = statusRank(previous.status());
                int currentRank = statusRank(current.status());
                regression |= currentRank > previousRank;
                improvement |= currentRank < previousRank;
            }
            if (!previous.blocking().equals(current.blocking())) {
                diagnostics.add("blocking " + previous.blocking() + " -> " + current.blocking());
                regression |= !isTrue(previous.blocking()) && isTrue(current.blocking());
                improvement |= isTrue(previous.blocking()) && !isTrue(current.blocking());
            }
            if (!previous.matched().equals(current.matched())) {
                diagnostics.add("preferred match " + previous.matched() + " -> " + current.matched());
                regression |= isTrue(previous.matched()) && !isTrue(current.matched());
                improvement |= !isTrue(previous.matched()) && isTrue(current.matched());
            }
            if (!previous.kernelMax().equals(current.kernelMax())) {
                diagnostics.add("kernel max " + previous.kernelMax() + " -> " + current.kernelMax());
                Optional<Integer> previousMax = parsePositiveInt(previous.kernelMax());
                Optional<Integer> currentMax = parsePositiveInt(current.kernelMax());
                if (previousMax.isPresent() && currentMax.isPresent()) {
                    regression |= currentMax.get() < previousMax.get();
                    improvement |= currentMax.get() > previousMax.get();
                }
            }
            if (!previous.preferredMultiple().equals(current.preferredMultiple())) {
                diagnostics.add("preferred multiple " + previous.preferredMultiple()
                        + " -> " + current.preferredMultiple());
            }
            if (!previous.localShape().equals(current.localShape())) {
                diagnostics.add("local shape " + previous.localShape() + " -> " + current.localShape());
            }
            if (!previous.requestedSize().equals(current.requestedSize())) {
                diagnostics.add("requested size " + previous.requestedSize() + " -> " + current.requestedSize());
            }

            String classification = regression ? "regressed" : (improvement ? "improved" : "changed");
            return new KernelChange(
                    resource,
                    classification,
                    previous,
                    current,
                    String.join("; ", diagnostics)
            );
        }

        private static int statusRank(String status) {
            return switch (status) {
                case "aligned" -> 0;
                case "driver-selected" -> 1;
                case "non-preferred-multiple" -> 2;
                case "unavailable" -> 3;
                case "missing" -> 4;
                default -> 3;
            };
        }

        private static Optional<Integer> parsePositiveInt(String value) {
            try {
                int parsed = Integer.parseInt(value);
                return parsed >= 0 ? Optional.of(parsed) : Optional.empty();
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }

        private static boolean isTrue(String value) {
            return Boolean.parseBoolean(value);
        }
    }

    private enum EntryField {
        STATUS {
            @Override
            String value(OpenClKernelLaunchAdvisorySummary.Entry entry) {
                return entry.status();
            }
        },
        KERNEL_MAX {
            @Override
            String value(OpenClKernelLaunchAdvisorySummary.Entry entry) {
                return entry.kernelMax();
            }
        },
        PREFERRED {
            @Override
            String value(OpenClKernelLaunchAdvisorySummary.Entry entry) {
                return entry.preferredMultiple();
            }
        };

        abstract String value(OpenClKernelLaunchAdvisorySummary.Entry entry);
    }
}
