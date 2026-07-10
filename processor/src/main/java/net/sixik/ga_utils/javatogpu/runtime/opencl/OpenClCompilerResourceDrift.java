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

/**
 * Compares per-kernel compiler resource metrics against a compatible validation baseline.
 */
record OpenClCompilerResourceDrift(
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

    static final int REGISTER_ABSOLUTE_TOLERANCE = 2;
    static final int REGISTER_RELATIVE_TOLERANCE_PERCENT = 10;
    private static final Pattern COUNT_PATTERN = Pattern.compile(
            "(kernels|available|unavailable|missing|spilling|stackUsing|maxRegisters|totalSpillBytes|maxStackBytes)=(-?\\d+)"
    );

    OpenClCompilerResourceDrift {
        kernelChanges = kernelChanges == null ? List.of() : List.copyOf(kernelChanges);
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    static OpenClCompilerResourceDrift compare(
            OpenClValidationHistoryEntry currentEntry,
            List<OpenClValidationHistoryEntry> historyEntries
    ) {
        Optional<Counts> currentCounts = Counts.parse(currentEntry.compilerResourceStatus());
        if (currentCounts.isEmpty()) {
            return unavailable("current compiler-resource counts are unavailable");
        }
        for (OpenClValidationHistoryEntry previousEntry : historyEntries) {
            if (!compatibleEnvironment(currentEntry, previousEntry)
                    || currentEntry.generatedAtUtc().equals(previousEntry.generatedAtUtc())) {
                continue;
            }
            Optional<Counts> previousCounts = Counts.parse(previousEntry.compilerResourceStatus());
            if (previousCounts.isEmpty()) {
                continue;
            }
            KernelComparison comparison = KernelComparison.compare(
                    OpenClCompilerResourceSummary.parseHistoryEntries(currentEntry.compilerResourceStatus()),
                    OpenClCompilerResourceSummary.parseHistoryEntries(previousEntry.compilerResourceStatus())
            );
            return classify(currentCounts.get(), previousCounts.get(), previousEntry, comparison);
        }
        return noBaseline(currentCounts.get());
    }

    static OpenClCompilerResourceDrift noBaseline(Counts current) {
        return new OpenClCompilerResourceDrift(
                "no-baseline",
                false,
                current,
                null,
                null,
                "unknown",
                false,
                List.of(),
                "no previous compatible compiler-resource history entry was found"
        );
    }

    String toMarkdown() {
        StringBuilder markdown = new StringBuilder();
        markdown.append("## Compiler Resource Drift\n\n");
        markdown.append("- Status: `").append(status).append("`\n");
        markdown.append("- Regression: `").append(regression).append("`\n");
        markdown.append("- Register tolerance: `max(")
                .append(REGISTER_ABSOLUTE_TOLERANCE)
                .append(", ")
                .append(REGISTER_RELATIVE_TOLERANCE_PERCENT)
                .append("% of baseline)`\n");
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
            markdown.append("| Kernel resource | Classification | Registers | Spill bytes | Stack frame | Diagnostic |\n");
            markdown.append("| --- | --- | ---: | ---: | ---: | --- |\n");
            for (KernelChange change : kernelChanges) {
                markdown.append("| `").append(table(change.resource())).append("` | `")
                        .append(table(change.classification())).append("` | `")
                        .append(table(transition(change.previous(), change.current(), EntryField.REGISTERS))).append("` | `")
                        .append(table(transition(change.previous(), change.current(), EntryField.SPILLS))).append("` | `")
                        .append(table(transition(change.previous(), change.current(), EntryField.STACK))).append("` | `")
                        .append(table(change.diagnostic())).append("` |\n");
            }
        }
        markdown.append('\n');
        return markdown.toString();
    }

    String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("formatVersion=1\n");
        builder.append("status=").append(status).append('\n');
        builder.append("regression=").append(regression).append('\n');
        builder.append("registerTolerance.absolute=").append(REGISTER_ABSOLUTE_TOLERANCE).append('\n');
        builder.append("registerTolerance.relativePercent=").append(REGISTER_RELATIVE_TOLERANCE_PERCENT).append('\n');
        appendCounts(builder, "current", current);
        builder.append("baseline.present=").append(previous != null).append('\n');
        if (previous != null) {
            builder.append("baseline.generatedAtUtc=").append(previousGeneratedAtUtc).append('\n');
            builder.append("baseline.driverVersion=").append(propertyValue(previousDriverVersion)).append('\n');
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

    private static OpenClCompilerResourceDrift classify(
            Counts current,
            Counts previous,
            OpenClValidationHistoryEntry previousEntry,
            KernelComparison comparison
    ) {
        boolean aggregateRegression = current.unavailable() > previous.unavailable()
                || current.missing() > previous.missing()
                || current.spilling() > previous.spilling()
                || current.stackUsing() > previous.stackUsing()
                || strictMetricRegression(previous.totalSpillBytes(), current.totalSpillBytes())
                || strictMetricRegression(previous.maxStackBytes(), current.maxStackBytes())
                || registerRegression(previous.maxRegisters(), current.maxRegisters());
        if (comparison.regressionCount() > 0) {
            return result(
                    "regressed",
                    true,
                    current,
                    previous,
                    previousEntry,
                    comparison,
                    "per-kernel compiler resources regressed for " + comparison.firstRegressionResource()
            );
        }
        if (aggregateRegression) {
            return result(
                    "regressed",
                    true,
                    current,
                    previous,
                    previousEntry,
                    comparison,
                    "one or more unavailable, missing, spill, stack, or register aggregate metrics regressed"
            );
        }
        if (current.equals(previous) && comparison.changes().isEmpty()) {
            return result(
                    "stable",
                    false,
                    current,
                    previous,
                    previousEntry,
                    comparison,
                    comparison.available()
                            ? "compiler-resource counts and per-kernel snapshots are unchanged"
                            : "compiler-resource counts are unchanged; per-kernel baseline is unavailable"
            );
        }
        boolean aggregateImproved = current.unavailable() < previous.unavailable()
                || current.missing() < previous.missing()
                || current.spilling() < previous.spilling()
                || current.stackUsing() < previous.stackUsing()
                || strictMetricImprovement(previous.totalSpillBytes(), current.totalSpillBytes())
                || strictMetricImprovement(previous.maxStackBytes(), current.maxStackBytes())
                || registerImprovement(previous.maxRegisters(), current.maxRegisters());
        boolean onlyImprovements = comparison.available()
                && !comparison.changes().isEmpty()
                && comparison.improvementCount() == comparison.changes().size();
        boolean improved = aggregateImproved || onlyImprovements;
        return result(
                improved ? "improved" : "changed",
                false,
                current,
                previous,
                previousEntry,
                comparison,
                improved
                        ? "compiler-resource evidence improved without a regression signal"
                        : "compiler-resource evidence changed within the configured regression thresholds"
        );
    }

    private static OpenClCompilerResourceDrift result(
            String status,
            boolean regression,
            Counts current,
            Counts previous,
            OpenClValidationHistoryEntry previousEntry,
            KernelComparison comparison,
            String diagnostic
    ) {
        return new OpenClCompilerResourceDrift(
                status,
                regression,
                current,
                previous,
                previousEntry.generatedAtUtc(),
                previousEntry.driverVersion(),
                comparison.available(),
                comparison.changes(),
                diagnostic
        );
    }

    private static OpenClCompilerResourceDrift unavailable(String diagnostic) {
        return new OpenClCompilerResourceDrift(
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

    private static int registerTolerance(int baseline) {
        if (baseline < 0) {
            return REGISTER_ABSOLUTE_TOLERANCE;
        }
        int relative = (int) Math.ceil((double) baseline * REGISTER_RELATIVE_TOLERANCE_PERCENT / 100.0);
        return Math.max(REGISTER_ABSOLUTE_TOLERANCE, relative);
    }

    private static boolean registerRegression(int previous, int current) {
        return previous >= 0 && (current < 0 || current > previous + registerTolerance(previous));
    }

    private static boolean registerImprovement(int previous, int current) {
        return current >= 0 && (previous < 0 || current < previous - registerTolerance(previous));
    }

    private static boolean strictMetricRegression(int previous, int current) {
        return previous >= 0 && (current < 0 || current > previous);
    }

    private static boolean strictMetricImprovement(int previous, int current) {
        return current >= 0 && (previous < 0 || current < previous);
    }

    private static String deltaText(Counts current, Counts previous) {
        return "kernels=" + signed(current.kernels() - previous.kernels())
                + ", available=" + signed(current.available() - previous.available())
                + ", unavailable=" + signed(current.unavailable() - previous.unavailable())
                + ", missing=" + signed(current.missing() - previous.missing())
                + ", spilling=" + signed(current.spilling() - previous.spilling())
                + ", stackUsing=" + signed(current.stackUsing() - previous.stackUsing())
                + ", maxRegisters=" + signed(current.maxRegisters() - previous.maxRegisters())
                + ", totalSpillBytes=" + signed(current.totalSpillBytes() - previous.totalSpillBytes())
                + ", maxStackBytes=" + signed(current.maxStackBytes() - previous.maxStackBytes());
    }

    private static void appendCounts(StringBuilder builder, String prefix, Counts counts) {
        builder.append(prefix).append(".present=").append(counts != null).append('\n');
        if (counts == null) {
            return;
        }
        builder.append(prefix).append(".kernels=").append(counts.kernels()).append('\n');
        builder.append(prefix).append(".available=").append(counts.available()).append('\n');
        builder.append(prefix).append(".unavailable=").append(counts.unavailable()).append('\n');
        builder.append(prefix).append(".missing=").append(counts.missing()).append('\n');
        builder.append(prefix).append(".spilling=").append(counts.spilling()).append('\n');
        builder.append(prefix).append(".stackUsing=").append(counts.stackUsing()).append('\n');
        builder.append(prefix).append(".maxRegisters=").append(counts.maxRegisters()).append('\n');
        builder.append(prefix).append(".totalSpillBytes=").append(counts.totalSpillBytes()).append('\n');
        builder.append(prefix).append(".maxStackBytes=").append(counts.maxStackBytes()).append('\n');
    }

    private static void appendDelta(StringBuilder builder, Counts current, Counts previous) {
        builder.append("delta.kernels=").append(current.kernels() - previous.kernels()).append('\n');
        builder.append("delta.available=").append(current.available() - previous.available()).append('\n');
        builder.append("delta.unavailable=").append(current.unavailable() - previous.unavailable()).append('\n');
        builder.append("delta.missing=").append(current.missing() - previous.missing()).append('\n');
        builder.append("delta.spilling=").append(current.spilling() - previous.spilling()).append('\n');
        builder.append("delta.stackUsing=").append(current.stackUsing() - previous.stackUsing()).append('\n');
        builder.append("delta.maxRegisters=").append(current.maxRegisters() - previous.maxRegisters()).append('\n');
        builder.append("delta.totalSpillBytes=").append(current.totalSpillBytes() - previous.totalSpillBytes()).append('\n');
        builder.append("delta.maxStackBytes=").append(current.maxStackBytes() - previous.maxStackBytes()).append('\n');
    }

    private static void appendKernelChange(StringBuilder builder, String prefix, KernelChange change) {
        builder.append(prefix).append(".resource=").append(propertyValue(change.resource())).append('\n');
        builder.append(prefix).append(".classification=").append(change.classification()).append('\n');
        appendEntry(builder, prefix + ".previous", change.previous());
        appendEntry(builder, prefix + ".current", change.current());
        builder.append(prefix).append(".diagnostic=").append(propertyValue(change.diagnostic())).append('\n');
    }

    private static void appendEntry(
            StringBuilder builder,
            String prefix,
            OpenClCompilerResourceSummary.Entry entry
    ) {
        builder.append(prefix).append(".present=").append(entry != null).append('\n');
        if (entry == null) {
            return;
        }
        builder.append(prefix).append(".status=").append(entry.status()).append('\n');
        builder.append(prefix).append(".provider=").append(propertyValue(entry.provider())).append('\n');
        builder.append(prefix).append(".tool=").append(propertyValue(entry.tool())).append('\n');
        builder.append(prefix).append(".registers=").append(entry.registerCount()).append('\n');
        builder.append(prefix).append(".spillStoreBytes=").append(entry.spillStoreBytes()).append('\n');
        builder.append(prefix).append(".spillLoadBytes=").append(entry.spillLoadBytes()).append('\n');
        builder.append(prefix).append(".stackFrameBytes=").append(entry.stackFrameBytes()).append('\n');
    }

    private static String transition(
            OpenClCompilerResourceSummary.Entry previous,
            OpenClCompilerResourceSummary.Entry current,
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
            int available,
            int unavailable,
            int missing,
            int spilling,
            int stackUsing,
            int maxRegisters,
            int totalSpillBytes,
            int maxStackBytes
    ) {

        static Optional<Counts> parse(String summary) {
            if (summary == null || !summary.startsWith("recorded (")) {
                return Optional.empty();
            }
            LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
            Matcher matcher = COUNT_PATTERN.matcher(summary);
            while (matcher.find()) {
                values.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
            }
            if (values.size() != 9) {
                return Optional.empty();
            }
            return Optional.of(new Counts(
                    values.get("kernels"),
                    values.get("available"),
                    values.get("unavailable"),
                    values.get("missing"),
                    values.get("spilling"),
                    values.get("stackUsing"),
                    values.get("maxRegisters"),
                    values.get("totalSpillBytes"),
                    values.get("maxStackBytes")
            ));
        }

        String toCompactString() {
            return "kernels=" + kernels
                    + ", available=" + available
                    + ", unavailable=" + unavailable
                    + ", missing=" + missing
                    + ", spilling=" + spilling
                    + ", stackUsing=" + stackUsing
                    + ", maxRegisters=" + maxRegisters
                    + ", totalSpillBytes=" + totalSpillBytes
                    + ", maxStackBytes=" + maxStackBytes;
        }
    }

    record KernelChange(
            String resource,
            String classification,
            OpenClCompilerResourceSummary.Entry previous,
            OpenClCompilerResourceSummary.Entry current,
            String diagnostic
    ) {
    }

    private record MetricChange(boolean regression, boolean improvement, String diagnostic) {
    }

    private record KernelComparison(boolean available, List<KernelChange> changes) {

        private KernelComparison {
            changes = changes == null ? List.of() : List.copyOf(changes);
        }

        static KernelComparison compare(
                Optional<List<OpenClCompilerResourceSummary.Entry>> currentEntries,
                Optional<List<OpenClCompilerResourceSummary.Entry>> previousEntries
        ) {
            if (currentEntries.isEmpty() || previousEntries.isEmpty()) {
                return new KernelComparison(false, List.of());
            }
            Map<String, OpenClCompilerResourceSummary.Entry> currentByResource = indexByResource(currentEntries.get());
            Map<String, OpenClCompilerResourceSummary.Entry> previousByResource = indexByResource(previousEntries.get());
            Set<String> resources = new LinkedHashSet<>(previousByResource.keySet());
            resources.addAll(currentByResource.keySet());
            ArrayList<KernelChange> changes = new ArrayList<>();
            for (String resource : resources) {
                OpenClCompilerResourceSummary.Entry previous = previousByResource.get(resource);
                OpenClCompilerResourceSummary.Entry current = currentByResource.get(resource);
                if (previous == null) {
                    changes.add(new KernelChange(
                            resource,
                            "added",
                            null,
                            current,
                            "kernel resource was added to the compiler-resource snapshot"
                    ));
                } else if (current == null) {
                    changes.add(new KernelChange(
                            resource,
                            "regressed",
                            previous,
                            null,
                            "kernel resource is missing from the current compiler-resource snapshot"
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

        private static Map<String, OpenClCompilerResourceSummary.Entry> indexByResource(
                List<OpenClCompilerResourceSummary.Entry> entries
        ) {
            LinkedHashMap<String, OpenClCompilerResourceSummary.Entry> indexed = new LinkedHashMap<>();
            for (OpenClCompilerResourceSummary.Entry entry : entries) {
                indexed.put(entry.kernelResource(), entry);
            }
            return indexed;
        }

        private static KernelChange classifyKernelChange(
                String resource,
                OpenClCompilerResourceSummary.Entry previous,
                OpenClCompilerResourceSummary.Entry current
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
            MetricChange registerChange = registerChange(previous.registerCount(), current.registerCount());
            MetricChange storeChange = strictMetricChange(
                    "spill stores",
                    previous.spillStoreBytes(),
                    current.spillStoreBytes()
            );
            MetricChange loadChange = strictMetricChange(
                    "spill loads",
                    previous.spillLoadBytes(),
                    current.spillLoadBytes()
            );
            MetricChange stackChange = strictMetricChange(
                    "stack frame",
                    previous.stackFrameBytes(),
                    current.stackFrameBytes()
            );
            for (MetricChange change : List.of(registerChange, storeChange, loadChange, stackChange)) {
                regression |= change.regression();
                improvement |= change.improvement();
                if (!change.diagnostic().isBlank()) {
                    diagnostics.add(change.diagnostic());
                }
            }
            if (!previous.provider().equals(current.provider())) {
                diagnostics.add("provider " + previous.provider() + " -> " + current.provider());
            }
            if (!previous.tool().equals(current.tool())) {
                diagnostics.add("tool " + previous.tool() + " -> " + current.tool());
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

        private static MetricChange registerChange(int previous, int current) {
            if (previous == current) {
                return new MetricChange(false, false, "");
            }
            String diagnostic = "registers " + metric(previous) + " -> " + metric(current);
            return new MetricChange(
                    registerRegression(previous, current),
                    registerImprovement(previous, current),
                    diagnostic
            );
        }

        private static MetricChange strictMetricChange(String label, int previous, int current) {
            if (previous == current) {
                return new MetricChange(false, false, "");
            }
            return new MetricChange(
                    strictMetricRegression(previous, current),
                    strictMetricImprovement(previous, current),
                    label + " " + metric(previous) + " -> " + metric(current)
            );
        }

        private static int statusRank(String status) {
            return switch (status) {
                case "recorded" -> 0;
                case "unavailable" -> 1;
                case "missing" -> 2;
                default -> 1;
            };
        }

        private static String metric(int value) {
            return value < 0 ? "unknown" : Integer.toString(value);
        }
    }

    private enum EntryField {
        REGISTERS {
            @Override
            String value(OpenClCompilerResourceSummary.Entry entry) {
                return metric(entry.registerCount());
            }
        },
        SPILLS {
            @Override
            String value(OpenClCompilerResourceSummary.Entry entry) {
                return metric(entry.knownSpillBytes());
            }
        },
        STACK {
            @Override
            String value(OpenClCompilerResourceSummary.Entry entry) {
                return metric(entry.stackFrameBytes());
            }
        };

        abstract String value(OpenClCompilerResourceSummary.Entry entry);

        static String metric(int value) {
            return value < 0 ? "unknown" : Integer.toString(value);
        }
    }
}
