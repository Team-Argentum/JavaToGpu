package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * First concrete method/source-location example for an auto-vectorization no-candidate bucket.
 */
public record GpuIrAutoVectorizationNoCandidateExample(
        String bucket,
        String methodName,
        String location,
        String summary
) {
    public GpuIrAutoVectorizationNoCandidateExample {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket must not be blank");
        }
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
    }

    public String artifactValue() {
        return methodName + "@" + location + ": " + summary;
    }
}
