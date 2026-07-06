package net.sixik.ga_utils.javatogpu.frontend.asm;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Combined read-only ASM artifact snapshot for CI and parser-migration planning.
 *
 * <p>The snapshot intentionally keeps failure validation, readiness classification, and
 * bytecode-shape inventory as separate nested reports. Consumers get one stable artifact
 * surface without losing the meaning of each underlying diagnostic layer.</p>
 */
public record AsmFrontendArtifactReport(
        AsmFrontendFailureReport failureReport,
        AsmBytecodeShapeInventoryReport shapeInventoryReport
) {
    public AsmFrontendArtifactReport {
        failureReport = Objects.requireNonNull(failureReport, "failureReport");
        shapeInventoryReport = Objects.requireNonNull(shapeInventoryReport, "shapeInventoryReport");
    }

    public AsmFrontendReadinessReport readinessReport() {
        return failureReport.readinessReport();
    }

    public boolean successful() {
        return failureReport.successful();
    }

    public String summaryLine() {
        return "asmArtifactReport successful="
                + successful()
                + " readiness="
                + readinessReport().verdict().artifactValue()
                + " failureCount="
                + failureReport.failureCount()
                + " riskyShapeCount="
                + shapeInventoryReport.riskyShapeCount();
    }

    public AsmFrontendArtifactReport requireSuccessful() {
        failureReport.requireSuccessful();
        return this;
    }

    public Map<String, String> artifactFields(String prefix) {
        String safePrefix = prefix == null || prefix.isBlank() ? "asmArtifactReport" : prefix;
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(safePrefix + ".successful", Boolean.toString(successful()));
        fields.put(safePrefix + ".summary", summaryLine());
        fields.put(safePrefix + ".failureCount", Integer.toString(failureReport.failureCount()));
        fields.put(safePrefix + ".riskyShapeCount", Integer.toString(shapeInventoryReport.riskyShapeCount()));
        fields.putAll(readinessReport().artifactFields(safePrefix + ".readiness"));
        fields.putAll(failureReport.artifactFields(safePrefix + ".failureReport"));
        fields.putAll(shapeInventoryReport.artifactFields(safePrefix + ".shapeInventory"));
        return Map.copyOf(fields);
    }
}
