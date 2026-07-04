package net.sixik.ga_utils.javatogpu.frontend.diagnostics;

/**
 * One-based source range used by Rust-style JavaToGpu diagnostics.
 */
public record GpuSourceSpan(
        String sourceName,
        int line,
        int column,
        int endLine,
        int endColumn
) {
    public GpuSourceSpan {
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("sourceName must not be blank");
        }
        if (line <= 0) {
            throw new IllegalArgumentException("line must be positive");
        }
        if (column <= 0) {
            throw new IllegalArgumentException("column must be positive");
        }
        if (endLine < line) {
            throw new IllegalArgumentException("endLine must not be before line");
        }
        if (endLine == line && endColumn < column) {
            throw new IllegalArgumentException("endColumn must not be before column on the same line");
        }
        if (endColumn <= 0) {
            throw new IllegalArgumentException("endColumn must be positive");
        }
    }

    public static GpuSourceSpan point(String sourceName, int line, int column) {
        return new GpuSourceSpan(sourceName, line, column, line, column);
    }

    public int singleLineLength() {
        if (line != endLine) {
            return 1;
        }
        return Math.max(1, endColumn - column + 1);
    }

    public String location() {
        return sourceName + ":" + line + ":" + column;
    }
}
