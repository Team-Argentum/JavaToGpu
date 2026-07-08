package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only scanner-shape classification plus first concrete examples for each bucket.
 */
public record GpuIrAutoVectorizationNoCandidateShapeReport(
        String methodName,
        List<String> buckets,
        List<GpuIrAutoVectorizationNoCandidateExample> examples
) {
    public GpuIrAutoVectorizationNoCandidateShapeReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        buckets = List.copyOf(Objects.requireNonNull(buckets, "buckets"));
        examples = List.copyOf(Objects.requireNonNull(examples, "examples"));
        if (buckets.stream().anyMatch(bucket -> bucket == null || bucket.isBlank())) {
            throw new IllegalArgumentException("buckets must not contain blank entries");
        }
        if (examples.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("examples must not contain null entries");
        }
    }

    public Optional<GpuIrAutoVectorizationNoCandidateExample> firstExample() {
        return examples.stream().findFirst();
    }

    public Map<String, GpuIrAutoVectorizationNoCandidateExample> firstExampleByBucket() {
        LinkedHashMap<String, GpuIrAutoVectorizationNoCandidateExample> values = new LinkedHashMap<>();
        for (GpuIrAutoVectorizationNoCandidateExample example : examples) {
            values.putIfAbsent(example.bucket(), example);
        }
        return java.util.Collections.unmodifiableMap(values);
    }
}
