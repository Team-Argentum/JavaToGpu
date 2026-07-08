package net.sixik.ga_utils.javatogpu.frontend.diagnostics;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structured JavaToGpu diagnostic that can be rendered with source snippets.
 */
public record GpuSourceDiagnostic(
        String code,
        String message,
        GpuSourceSpan primarySpan,
        List<GpuDiagnosticLabel> labels,
        List<String> helpMessages,
        Map<String, String> artifactFields
) {
    public GpuSourceDiagnostic {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        primarySpan = Objects.requireNonNull(primarySpan, "primarySpan");
        labels = List.copyOf(Objects.requireNonNull(labels, "labels"));
        helpMessages = List.copyOf(Objects.requireNonNull(helpMessages, "helpMessages"));
        artifactFields = Map.copyOf(Objects.requireNonNull(artifactFields, "artifactFields"));
        if (labels.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("labels must not contain null entries");
        }
        if (helpMessages.stream().anyMatch(help -> help == null || help.isBlank())) {
            throw new IllegalArgumentException("helpMessages must not contain blank entries");
        }
        if (artifactFields.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getKey().isBlank()
                || entry.getValue() == null)) {
            throw new IllegalArgumentException("artifactFields must not contain blank keys or null values");
        }
    }

    public GpuSourceDiagnostic(
            String code,
            String message,
            GpuSourceSpan primarySpan,
            List<GpuDiagnosticLabel> labels,
            List<String> helpMessages
    ) {
        this(code, message, primarySpan, labels, helpMessages, Map.of());
    }

    public static GpuSourceDiagnostic error(
            String code,
            String message,
            GpuSourceSpan primarySpan,
            String primaryLabel,
            List<String> helpMessages
    ) {
        return new GpuSourceDiagnostic(
                code,
                message,
                primarySpan,
                List.of(GpuDiagnosticLabel.primary(primarySpan, primaryLabel)),
                helpMessages,
                Map.of()
        );
    }

    public static GpuSourceDiagnostic error(
            String code,
            String message,
            GpuSourceSpan primarySpan,
            String primaryLabel,
            List<String> helpMessages,
            Map<String, String> artifactFields
    ) {
        return new GpuSourceDiagnostic(
                code,
                message,
                primarySpan,
                List.of(GpuDiagnosticLabel.primary(primarySpan, primaryLabel)),
                helpMessages,
                artifactFields
        );
    }
}
