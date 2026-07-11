package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Source anchor for the Java expression that invoked a rewritten GPU method.
 */
public record GpuRuntimeCallSite(
        String callerClassName,
        String callerMethodName,
        String sourceName,
        int line,
        int column,
        int endLine,
        int endColumn,
        String expression,
        String targetOwnerName,
        String targetMethodName,
        String source
) {

    public GpuRuntimeCallSite(
            String callerClassName,
            String callerMethodName,
            String sourceName,
            int line,
            int column,
            int endLine,
            int endColumn,
            String expression,
            String source
    ) {
        this(
                callerClassName,
                callerMethodName,
                sourceName,
                line,
                column,
                endLine,
                endColumn,
                expression,
                "unknown",
                "unknown",
                source
        );
    }

    public GpuRuntimeCallSite {
        callerClassName = normalize(callerClassName, "unknown");
        callerMethodName = normalize(callerMethodName, "unknown");
        sourceName = normalize(sourceName, callerClassName);
        expression = normalize(expression, callerClassName + "." + callerMethodName + "(...)");
        targetOwnerName = normalize(targetOwnerName, "unknown");
        targetMethodName = normalize(targetMethodName, "unknown");
        source = normalize(source, "stack-trace");
    }

    public static GpuRuntimeCallSite unknown() {
        return new GpuRuntimeCallSite(
                "unknown",
                "unknown",
                "unknown",
                -1,
                -1,
                -1,
                -1,
                "unknown",
                "unknown",
                "unknown",
                "unknown"
        );
    }

    public boolean knownRange() {
        return line > 0 && column > 0;
    }

    public String location() {
        return sourceName + ':' + (line > 0 ? line : 1) + ':' + (column > 0 ? column : 1);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
