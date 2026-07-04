package net.sixik.ga_utils.javatogpu.frontend.diagnostics;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Renders JavaToGpu diagnostics in a compact Rust-like source-snippet format.
 */
public final class GpuDiagnosticRenderer {

    public String render(GpuSourceDiagnostic diagnostic, List<String> sourceLines) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        Objects.requireNonNull(sourceLines, "sourceLines");

        StringBuilder builder = new StringBuilder();
        builder.append("error[").append(diagnostic.code()).append("]: ").append(diagnostic.message()).append('\n');
        builder.append("  --> ").append(diagnostic.primarySpan().location()).append('\n');
        builder.append("   |\n");

        Map<Integer, List<GpuDiagnosticLabel>> labelsByLine = diagnostic.labels().stream()
                .collect(Collectors.groupingBy(label -> label.span().line(), java.util.LinkedHashMap::new, Collectors.toList()));

        labelsByLine.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> appendLine(builder, entry.getKey(), sourceLines, entry.getValue()));

        diagnostic.helpMessages().forEach(help -> builder.append("   = help: ").append(help).append('\n'));
        return builder.toString();
    }

    private void appendLine(
            StringBuilder builder,
            int lineNumber,
            List<String> sourceLines,
            List<GpuDiagnosticLabel> labels
    ) {
        String sourceLine = sourceLine(sourceLines, lineNumber);
        String linePrefix = Integer.toString(lineNumber);
        builder.append(linePrefix).append(" | ").append(sourceLine).append('\n');

        labels.stream()
                .sorted(Comparator.comparingInt(label -> label.span().column()))
                .forEach(label -> appendLabel(builder, linePrefix.length(), label));
        builder.append("   |\n");
    }

    private void appendLabel(StringBuilder builder, int lineNumberWidth, GpuDiagnosticLabel label) {
        String marker = label.primary() ? "^" : "-";
        builder.append(" ".repeat(lineNumberWidth)).append(" | ");
        builder.append(" ".repeat(Math.max(0, label.span().column() - 1)));
        builder.append(marker.repeat(label.span().singleLineLength()));
        builder.append(' ').append(label.message()).append('\n');
    }

    private String sourceLine(List<String> sourceLines, int lineNumber) {
        int index = lineNumber - 1;
        if (index < 0 || index >= sourceLines.size()) {
            return "<source unavailable>";
        }
        return sourceLines.get(index);
    }
}
