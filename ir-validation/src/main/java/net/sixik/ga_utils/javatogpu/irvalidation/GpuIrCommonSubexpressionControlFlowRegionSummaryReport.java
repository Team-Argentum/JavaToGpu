package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only summary for CSE candidates blocked by control-flow boundaries.
 *
 * <p>This report does not make any rewrite decision. It gives CI and real-kernel dogfooding a
 * compact way to see whether skipped CSE work is blocked by branch/loop/switch scoped locations
 * and what proof has to be added before the rewrite policy can become less conservative.</p>
 */
public record GpuIrCommonSubexpressionControlFlowRegionSummaryReport(
        List<GpuIrCommonSubexpressionSkippedDiagnostic> controlFlowDiagnostics
) {
    public GpuIrCommonSubexpressionControlFlowRegionSummaryReport {
        controlFlowDiagnostics = List.copyOf(Objects.requireNonNull(
                controlFlowDiagnostics,
                "controlFlowDiagnostics"
        ));
    }

    public static GpuIrCommonSubexpressionControlFlowRegionSummaryReport from(
            GpuIrCommonSubexpressionRewritePreview preview
    ) {
        Objects.requireNonNull(preview, "preview");
        return new GpuIrCommonSubexpressionControlFlowRegionSummaryReport(
                preview.skippedDiagnostics().stream()
                        .filter(GpuIrCommonSubexpressionControlFlowRegionSummaryReport::isControlFlowBlocked)
                        .toList()
        );
    }

    public boolean blocked() {
        return !controlFlowDiagnostics.isEmpty();
    }

    public String readiness() {
        return blocked() ? "blocked" : "clear";
    }

    public int blockedCandidateCount() {
        return controlFlowDiagnostics.size();
    }

    public Optional<GpuIrCommonSubexpressionSkippedDiagnostic> firstBlockedDiagnostic() {
        return controlFlowDiagnostics.stream().findFirst();
    }

    public String firstBlockedFingerprint() {
        return firstBlockedDiagnostic()
                .map(GpuIrCommonSubexpressionSkippedDiagnostic::fingerprint)
                .orElse("none");
    }

    public String firstBlockedDominanceStatus() {
        return firstBlockedDiagnostic()
                .map(diagnostic -> diagnostic.dominanceStatus().artifactValue())
                .orElse("none");
    }

    public String firstBlockedLocation() {
        return firstBlockedDiagnostic()
                .flatMap(diagnostic -> diagnostic.locations().stream().findFirst())
                .orElse("none");
    }

    public String firstRemainingWork() {
        return blocked() ? "splitControlFlowRegionsBeforeCse" : "none";
    }

    public Map<String, Long> dominanceStatusCounts() {
        return controlFlowDiagnostics.stream()
                .collect(Collectors.groupingBy(
                        diagnostic -> diagnostic.dominanceStatus().artifactValue(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, Long> locationScopeCounts() {
        return controlFlowDiagnostics.stream()
                .flatMap(diagnostic -> diagnostic.locations().stream())
                .map(GpuIrCommonSubexpressionControlFlowRegionSummaryReport::locationScope)
                .collect(Collectors.groupingBy(
                        scope -> scope,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseControlFlowRegion");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Readiness", readiness());
        values.put(prefix + "Blocked", Boolean.toString(blocked()));
        values.put(prefix + "BlockedCandidates", Integer.toString(blockedCandidateCount()));
        values.put(prefix + "FirstBlockedFingerprint", firstBlockedFingerprint());
        values.put(prefix + "FirstBlockedDominanceStatus", firstBlockedDominanceStatus());
        values.put(prefix + "FirstBlockedLocation", firstBlockedLocation());
        values.put(prefix + "FirstRemainingWork", firstRemainingWork());
        values.put(prefix + "DominanceStatusCounts", mapSummary(dominanceStatusCounts()));
        values.put(prefix + "LocationScopeCounts", mapSummary(locationScopeCounts()));
        dominanceStatusCounts().forEach((status, count) ->
                values.put(prefix + "DominanceStatus." + status, Long.toString(count)));
        locationScopeCounts().forEach((scope, count) ->
                values.put(prefix + "LocationScope." + scope, Long.toString(count)));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "cse control-flow region readiness=" + readiness()
                + " blockedCandidates=" + blockedCandidateCount()
                + " firstDominanceStatus=" + firstBlockedDominanceStatus()
                + " firstRemainingWork=" + firstRemainingWork();
    }

    public String summary() {
        return ciSummaryLine()
                + " dominanceStatusCounts=" + mapSummary(dominanceStatusCounts())
                + " locationScopeCounts=" + mapSummary(locationScopeCounts());
    }

    private static boolean isControlFlowBlocked(GpuIrCommonSubexpressionSkippedDiagnostic diagnostic) {
        return diagnostic.reason() == GpuIrCommonSubexpressionSkipReason.CONTROL_FLOW_BOUNDARY
                || diagnostic.scope() == GpuIrCommonSubexpressionScope.CONTROL_FLOW_BOUNDARY
                || diagnostic.locations().stream()
                .map(GpuIrCommonSubexpressionLocation::parse)
                .anyMatch(GpuIrCommonSubexpressionLocation::controlFlowScoped);
    }

    private static String locationScope(String location) {
        GpuIrCommonSubexpressionLocation parsed = GpuIrCommonSubexpressionLocation.parse(location);
        if (!parsed.controlFlowScoped()) {
            return "straightLine";
        }
        if (location.contains(".then")) {
            return "thenBranch";
        }
        if (location.contains(".else")) {
            return "elseBranch";
        }
        if (location.contains(".case[")) {
            return "switchCase";
        }
        if (location.contains(".condition")) {
            return "condition";
        }
        if (location.contains(".update")) {
            return "loopUpdate";
        }
        if (location.contains(".initializer") && location.contains(".stmt[")) {
            return "loopInitializer";
        }
        if (location.contains(".body")) {
            return "body";
        }
        return "controlFlow";
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }
}
