package net.sixik.ga_utils.javatogpu.extension;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Auditable result of one compiler or runtime extension invocation.
 */
public record GpuExtensionExecutionReport(
        String extensionId,
        String extensionVersion,
        GpuExtensionPhase phase,
        GpuExtensionPermission permission,
        String operation,
        GpuExtensionExecutionOutcome outcome,
        GpuExtensionFailurePolicy failurePolicy,
        boolean pipelineContinued,
        String failureType,
        String message,
        List<String> diagnostics
) {

    public GpuExtensionExecutionReport {
        extensionId = normalize(extensionId, "extension:unknown");
        extensionVersion = normalize(extensionVersion, "unknown");
        phase = Objects.requireNonNull(phase, "phase");
        permission = Objects.requireNonNull(permission, "permission");
        operation = normalize(operation, "extension invocation");
        outcome = Objects.requireNonNull(outcome, "outcome");
        failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
        failureType = normalize(failureType, "none");
        message = normalize(message, defaultMessage(outcome));
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static GpuExtensionExecutionReport succeeded(GpuExtension extension, String operation) {
        return fromExtension(
                extension,
                operation,
                GpuExtensionExecutionOutcome.SUCCEEDED,
                GpuExtensionFailurePolicy.CONTINUE,
                true,
                "none",
                "extension invocation completed",
                List.of()
        );
    }

    public static GpuExtensionExecutionReport skipped(
            GpuExtension extension,
            String operation,
            String reason
    ) {
        return fromExtension(
                extension,
                operation,
                GpuExtensionExecutionOutcome.SKIPPED,
                GpuExtensionFailurePolicy.CONTINUE,
                true,
                "none",
                normalize(reason, "extension invocation was skipped"),
                List.of()
        );
    }

    public static GpuExtensionExecutionReport failed(
            GpuExtension extension,
            String operation,
            GpuExtensionFailurePolicy failurePolicy,
            RuntimeException failure
    ) {
        Objects.requireNonNull(failurePolicy, "failurePolicy");
        RuntimeException resolvedFailure = failure == null
                ? new IllegalStateException("extension invocation failed")
                : failure;
        boolean continued = failurePolicy == GpuExtensionFailurePolicy.CONTINUE;
        return fromExtension(
                extension,
                operation,
                continued
                        ? GpuExtensionExecutionOutcome.FAILED_CONTINUED
                        : GpuExtensionExecutionOutcome.FAILED_CLOSED,
                failurePolicy,
                continued,
                resolvedFailure.getClass().getName(),
                normalize(resolvedFailure.getMessage(), resolvedFailure.getClass().getSimpleName()),
                List.of("extension failure was isolated according to " + failurePolicy)
        );
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "extensionExecution" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".extensionId", extensionId);
        fields.put(normalizedPrefix + ".extensionVersion", extensionVersion);
        fields.put(normalizedPrefix + ".phase", phase.name());
        fields.put(normalizedPrefix + ".permission", permission.name());
        fields.put(normalizedPrefix + ".operation", operation);
        fields.put(normalizedPrefix + ".outcome", outcome.name());
        fields.put(normalizedPrefix + ".failurePolicy", failurePolicy.name());
        fields.put(normalizedPrefix + ".pipelineContinued", Boolean.toString(pipelineContinued));
        fields.put(normalizedPrefix + ".failureType", failureType);
        fields.put(normalizedPrefix + ".message", message);
        fields.put(normalizedPrefix + ".diagnostic.count", Integer.toString(diagnostics.size()));
        for (int index = 0; index < diagnostics.size(); index++) {
            fields.put(normalizedPrefix + ".diagnostic." + index, diagnostics.get(index));
        }
        return Collections.unmodifiableMap(fields);
    }

    public String toLine() {
        return "extension=" + extensionId
                + " version=" + extensionVersion
                + " phase=" + phase
                + " permission=" + permission
                + " operation=" + operation
                + " outcome=" + outcome
                + " failurePolicy=" + failurePolicy
                + " pipelineContinued=" + pipelineContinued
                + " failureType=" + failureType
                + " message=" + message;
    }

    private static GpuExtensionExecutionReport fromExtension(
            GpuExtension extension,
            String operation,
            GpuExtensionExecutionOutcome outcome,
            GpuExtensionFailurePolicy failurePolicy,
            boolean pipelineContinued,
            String failureType,
            String message,
            List<String> diagnostics
    ) {
        GpuExtension value = Objects.requireNonNull(extension, "extension");
        return new GpuExtensionExecutionReport(
                value.extensionId(),
                value.extensionVersion(),
                value.extensionPhase(),
                value.extensionPermission(),
                operation,
                outcome,
                failurePolicy,
                pipelineContinued,
                failureType,
                message,
                diagnostics
        );
    }

    private static String defaultMessage(GpuExtensionExecutionOutcome outcome) {
        return outcome == GpuExtensionExecutionOutcome.SUCCEEDED
                ? "extension invocation completed"
                : "extension invocation did not complete successfully";
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
