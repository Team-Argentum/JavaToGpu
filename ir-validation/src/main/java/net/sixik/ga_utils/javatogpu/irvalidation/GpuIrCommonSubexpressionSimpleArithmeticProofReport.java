package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only proof summary for the narrow simple-associative arithmetic CSE slice.
 *
 * <p>The current proof only blesses canonical `binary_assoc_simple(...)` fingerprints emitted by
 * {@link GpuIrCanonicalExpressionFingerprint}. That fingerprint is intentionally produced only for
 * nested reference-only arithmetic trees, so literals and casts stay outside this proof until their
 * numeric semantics are modeled explicitly.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticProofReport(
        int provenCandidateCount,
        int provenInsertionCount,
        int provenReplacementCount,
        List<GpuIrCommonSubexpressionRewriteInsertion> provenInsertions,
        List<GpuIrCommonSubexpressionRewriteEdit> provenReplacements
) {
    private static final String SIMPLE_ARITHMETIC_PREFIX = "binary_assoc_simple(";

    public GpuIrCommonSubexpressionSimpleArithmeticProofReport {
        if (provenCandidateCount < 0) {
            throw new IllegalArgumentException("provenCandidateCount must be non-negative");
        }
        if (provenInsertionCount < 0) {
            throw new IllegalArgumentException("provenInsertionCount must be non-negative");
        }
        if (provenReplacementCount < 0) {
            throw new IllegalArgumentException("provenReplacementCount must be non-negative");
        }
        provenInsertions = List.copyOf(Objects.requireNonNull(provenInsertions, "provenInsertions"));
        provenReplacements = List.copyOf(Objects.requireNonNull(provenReplacements, "provenReplacements"));
        if (provenCandidateCount != provenInsertionCount || provenInsertionCount != provenInsertions.size()) {
            throw new IllegalArgumentException("candidate/insertion counts must match proven insertions");
        }
        if (provenReplacementCount != provenReplacements.size()) {
            throw new IllegalArgumentException("replacement count must match proven replacements");
        }
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticProofReport from(GpuIrCommonSubexpressionRewritePreview preview) {
        Objects.requireNonNull(preview, "preview");
        List<GpuIrCommonSubexpressionRewriteInsertion> insertions = preview.insertions().stream()
                .filter(insertion -> isSimpleArithmeticFingerprint(insertion.fingerprint()))
                .toList();
        List<GpuIrCommonSubexpressionRewriteEdit> replacements = preview.replacementEdits().stream()
                .filter(replacement -> isSimpleArithmeticFingerprint(replacement.fingerprint()))
                .toList();
        return new GpuIrCommonSubexpressionSimpleArithmeticProofReport(
                insertions.size(),
                insertions.size(),
                replacements.size(),
                insertions,
                replacements
        );
    }

    public boolean hasProofs() {
        return provenCandidateCount > 0;
    }

    public Optional<GpuIrCommonSubexpressionRewriteInsertion> firstProvenInsertion() {
        return provenInsertions.stream().findFirst();
    }

    public String proofBoundary() {
        return "referenceOnlyNestedArithmetic";
    }

    public String blockedBoundary() {
        return "literalsAndCastsRequireTypedNumericProof";
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "ProvenCandidates", Integer.toString(provenCandidateCount));
        values.put(prefix + "ProvenInsertions", Integer.toString(provenInsertionCount));
        values.put(prefix + "ProvenReplacements", Integer.toString(provenReplacementCount));
        values.put(prefix + "HasProofs", Boolean.toString(hasProofs()));
        values.put(prefix + "ProofBoundary", proofBoundary());
        values.put(prefix + "BlockedBoundary", blockedBoundary());
        firstProvenInsertion().ifPresent(insertion -> {
            values.put(prefix + "FirstProvenFingerprint", insertion.fingerprint());
            values.put(prefix + "FirstProvenAnchor", insertion.anchorLocation());
        });
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticProof");
    }

    public String summary() {
        return "CSE simple arithmetic proof provenCandidates=" + provenCandidateCount
                + " provenInsertions=" + provenInsertionCount
                + " provenReplacements=" + provenReplacementCount
                + " hasProofs=" + hasProofs()
                + " proofBoundary=" + proofBoundary()
                + " blockedBoundary=" + blockedBoundary()
                + firstProvenInsertion()
                .map(insertion -> " firstProven=" + insertion.fingerprint() + " @ " + insertion.anchorLocation())
                .orElse("");
    }

    private static boolean isSimpleArithmeticFingerprint(String fingerprint) {
        return fingerprint != null && fingerprint.startsWith(SIMPLE_ARITHMETIC_PREFIX);
    }
}
