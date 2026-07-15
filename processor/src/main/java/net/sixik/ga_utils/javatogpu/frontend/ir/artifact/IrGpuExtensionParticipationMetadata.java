package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionFailurePolicy;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.List;

/**
 * Durable extension execution metadata persisted inside an IrGpu artifact.
 */
public record IrGpuExtensionParticipationMetadata(
        String source,
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

    public IrGpuExtensionParticipationMetadata {
        source = normalize(source, "unknown");
        extensionId = normalize(extensionId, "extension:unknown");
        extensionVersion = normalize(extensionVersion, "unknown");
        phase = phase == null ? GpuExtensionPhase.IR_VALIDATION : phase;
        permission = permission == null ? GpuExtensionPermission.READ_ONLY : permission;
        operation = normalize(operation, "extension invocation");
        outcome = outcome == null ? GpuExtensionExecutionOutcome.SKIPPED : outcome;
        failurePolicy = failurePolicy == null ? GpuExtensionFailurePolicy.CONTINUE : failurePolicy;
        failureType = normalize(failureType, "none");
        message = normalize(message, "extension invocation metadata");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static IrGpuExtensionParticipationMetadata fromExecutionReport(
            String source,
            GpuExtensionExecutionReport report
    ) {
        GpuExtensionExecutionReport value = java.util.Objects.requireNonNull(report, "report");
        return new IrGpuExtensionParticipationMetadata(
                source,
                value.extensionId(),
                value.extensionVersion(),
                value.phase(),
                value.permission(),
                value.operation(),
                value.outcome(),
                value.failurePolicy(),
                value.pipelineContinued(),
                value.failureType(),
                value.message(),
                value.diagnostics()
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
