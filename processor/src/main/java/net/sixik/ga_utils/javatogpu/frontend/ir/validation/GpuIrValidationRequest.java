package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Immutable validation input passed from the compiler frontend to optional providers.
 */
public record GpuIrValidationRequest(
        GpuIrCompiledMethod method,
        List<GpuIrCompiledMethod> helperMethods,
        List<ParsedGpuStruct> structs,
        boolean entryPoint,
        GpuIrValidationMode mode,
        GpuIrValidationDiagnosticPolicy diagnosticPolicy,
        Consumer<String> diagnosticReporter,
        Consumer<GpuIrValidationReportEntry> reportSink
) {
    public GpuIrValidationRequest {
        method = Objects.requireNonNull(method, "method");
        helperMethods = List.copyOf(helperMethods);
        structs = List.copyOf(structs);
        mode = Objects.requireNonNull(mode, "mode");
        diagnosticPolicy = Objects.requireNonNull(diagnosticPolicy, "diagnosticPolicy");
        diagnosticReporter = diagnosticReporter == null ? ignored -> { } : diagnosticReporter;
        reportSink = reportSink == null ? ignored -> { } : reportSink;
    }

    public GpuIrValidationRequest(
            GpuIrCompiledMethod method,
            List<GpuIrCompiledMethod> helperMethods,
            List<ParsedGpuStruct> structs,
            boolean entryPoint,
            GpuIrValidationMode mode
    ) {
        this(method, helperMethods, structs, entryPoint, mode, GpuIrValidationDiagnosticPolicy.SUMMARY, ignored -> { }, ignored -> { });
    }

    public GpuIrValidationRequest(
            GpuIrCompiledMethod method,
            List<GpuIrCompiledMethod> helperMethods,
            List<ParsedGpuStruct> structs,
            boolean entryPoint,
            GpuIrValidationMode mode,
            Consumer<String> diagnosticReporter
    ) {
        this(method, helperMethods, structs, entryPoint, mode, GpuIrValidationDiagnosticPolicy.SUMMARY, diagnosticReporter, ignored -> { });
    }

    public GpuIrValidationRequest(
            GpuIrCompiledMethod method,
            List<GpuIrCompiledMethod> helperMethods,
            List<ParsedGpuStruct> structs,
            boolean entryPoint,
            GpuIrValidationMode mode,
            GpuIrValidationDiagnosticPolicy diagnosticPolicy,
            Consumer<String> diagnosticReporter
    ) {
        this(method, helperMethods, structs, entryPoint, mode, diagnosticPolicy, diagnosticReporter, ignored -> { });
    }

    public void reportDiagnostic(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        diagnosticReporter.accept(message);
    }

    public void reportEntry(GpuIrValidationReportEntry entry) {
        reportSink.accept(Objects.requireNonNull(entry, "entry"));
    }
}
