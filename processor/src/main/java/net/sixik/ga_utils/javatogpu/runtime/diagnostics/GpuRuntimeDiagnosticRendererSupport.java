package net.sixik.ga_utils.javatogpu.runtime.diagnostics;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCallSite;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDiagnosticContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeFailurePhase;

import java.util.List;

/**
 * Domain implementation support for compact Rust-like runtime failure diagnostics.
 */
public final class GpuRuntimeDiagnosticRendererSupport {

    private GpuRuntimeDiagnosticRendererSupport() {
    }

    /**
     * Renders a stable runtime failure diagnostic with source, backend, device, and compile context.
     */
    public static String render(
            String code,
            GpuRuntimeFailurePhase phase,
            String message,
            GpuRuntimeDiagnosticContext context,
            List<String> helpMessages
    ) {
        GpuRuntimeDiagnosticContext value = context == null ? GpuRuntimeDiagnosticContext.unknown() : context;
        IrGpuSourceLocation location = value.sourceLocation();
        GpuRuntimeCallSite callSite = value.callSite();
        boolean useCallSite = callSite.knownRange();
        int line = useCallSite ? callSite.line() : location.knownRange() ? location.beginLine() : 1;
        int column = useCallSite ? callSite.column() : location.knownRange() ? location.beginColumn() : 1;
        int endColumn = useCallSite
                ? callSite.endLine() == line
                ? Math.max(column, callSite.endColumn())
                : column
                : location.knownRange() && location.endLine() == location.beginLine()
                ? Math.max(column, location.endColumn())
                : column;
        StringBuilder builder = new StringBuilder();
        builder.append("error[").append(code).append("]: ").append(message).append('\n');
        builder.append("  --> ").append(value.sourceName()).append(':').append(line).append(':').append(column).append('\n');
        builder.append("   |\n");
        builder.append(line).append(" | ")
                .append(useCallSite ? callSite.expression() : "<source unavailable at runtime>")
                .append('\n');
        builder.append("   | ").append(" ".repeat(Math.max(0, column - 1)))
                .append("^".repeat(Math.max(1, endColumn - column + 1)))
                .append(' ')
                .append(phase.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '))
                .append(" failed\n");
        builder.append("   |\n");
        builder.append("   = kernel: ").append(value.kernelName()).append(" [").append(value.kernelResource()).append("]\n");
        builder.append("   = backend: ").append(value.backendTarget()).append('\n');
        builder.append("   = device: ").append(value.deviceLabel())
                .append(" (vendor=").append(value.deviceVendor())
                .append(", id=").append(value.deviceId()).append(")\n");
        builder.append("   = compile args: ")
                .append(value.compileArgs().isEmpty() ? "<none>" : String.join(" ", value.compileArgs()))
                .append('\n');
        builder.append("   = optimization profile: ").append(value.optimizationProfile()).append('\n');
        if (useCallSite) {
            builder.append("   = GPU method: ")
                    .append(location.ownerQualifiedName().isBlank() ? value.kernelName() : location.ownerQualifiedName())
                    .append(location.methodName().isBlank() ? "" : "#" + location.methodName())
                    .append('\n');
        }
        if (helpMessages != null) {
            helpMessages.stream()
                    .filter(help -> help != null && !help.isBlank())
                    .forEach(help -> builder.append("   = help: ").append(help).append('\n'));
        }
        return builder.toString();
    }
}
