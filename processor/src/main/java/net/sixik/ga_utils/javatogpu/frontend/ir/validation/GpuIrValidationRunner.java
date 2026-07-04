package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Consumer;

/**
 * Loads and executes optional read-only IR validation providers.
 */
public final class GpuIrValidationRunner {
    private final List<GpuIrValidationProvider> providers;
    private final GpuIrValidationMode mode;
    private final GpuIrValidationDiagnosticPolicy diagnosticPolicy;
    private final Consumer<String> diagnosticReporter;
    private final Consumer<GpuIrValidationReportEntry> reportSink;

    public GpuIrValidationRunner(List<GpuIrValidationProvider> providers, GpuIrValidationMode mode) {
        this(providers, mode, GpuIrValidationDiagnosticPolicy.SUMMARY, ignored -> { }, ignored -> { });
    }

    public GpuIrValidationRunner(
            List<GpuIrValidationProvider> providers,
            GpuIrValidationMode mode,
            GpuIrValidationDiagnosticPolicy diagnosticPolicy,
            Consumer<String> diagnosticReporter
    ) {
        this(providers, mode, diagnosticPolicy, diagnosticReporter, ignored -> { });
    }

    public GpuIrValidationRunner(
            List<GpuIrValidationProvider> providers,
            GpuIrValidationMode mode,
            GpuIrValidationDiagnosticPolicy diagnosticPolicy,
            Consumer<String> diagnosticReporter,
            Consumer<GpuIrValidationReportEntry> reportSink
    ) {
        this.providers = List.copyOf(providers);
        this.mode = Objects.requireNonNull(mode, "mode");
        this.diagnosticPolicy = Objects.requireNonNull(diagnosticPolicy, "diagnosticPolicy");
        this.diagnosticReporter = diagnosticReporter == null ? ignored -> { } : diagnosticReporter;
        this.reportSink = reportSink == null ? ignored -> { } : reportSink;
    }

    public static GpuIrValidationRunner disabled() {
        return new GpuIrValidationRunner(List.of(), GpuIrValidationMode.OFF);
    }

    public static GpuIrValidationRunner loadFromServiceLoader(GpuIrValidationMode mode) {
        return loadFromServiceLoader(mode, GpuIrValidationDiagnosticPolicy.SUMMARY, ignored -> { });
    }

    public static GpuIrValidationRunner loadFromServiceLoader(
            GpuIrValidationMode mode,
            Consumer<String> diagnosticReporter
    ) {
        return loadFromServiceLoader(mode, GpuIrValidationDiagnosticPolicy.SUMMARY, diagnosticReporter);
    }

    public static GpuIrValidationRunner loadFromServiceLoader(
            GpuIrValidationMode mode,
            GpuIrValidationDiagnosticPolicy diagnosticPolicy,
            Consumer<String> diagnosticReporter
    ) {
        return loadFromServiceLoader(mode, diagnosticPolicy, diagnosticReporter, ignored -> { });
    }

    public static GpuIrValidationRunner loadFromServiceLoader(
            GpuIrValidationMode mode,
            GpuIrValidationDiagnosticPolicy diagnosticPolicy,
            Consumer<String> diagnosticReporter,
            Consumer<GpuIrValidationReportEntry> reportSink
    ) {
        if (mode == GpuIrValidationMode.OFF) {
            return disabled();
        }
        List<GpuIrValidationProvider> loadedProviders = new ArrayList<>();
        ServiceLoader.load(GpuIrValidationProvider.class, GpuIrValidationProvider.class.getClassLoader())
                .forEach(loadedProviders::add);
        return new GpuIrValidationRunner(loadedProviders, mode, diagnosticPolicy, diagnosticReporter, reportSink);
    }

    public void run(
            GpuIrCompiledMethod compiledKernel,
            List<GpuIrCompiledMethod> compiledHelpers,
            List<ParsedGpuStruct> structs
    ) {
        if (mode == GpuIrValidationMode.OFF || providers.isEmpty()) {
            return;
        }

        for (GpuIrCompiledMethod helper : compiledHelpers) {
            runForMethod(helper, compiledHelpers, structs, false);
        }
        runForMethod(compiledKernel, compiledHelpers, structs, true);
    }

    private void runForMethod(
            GpuIrCompiledMethod method,
            List<GpuIrCompiledMethod> helperMethods,
            List<ParsedGpuStruct> structs,
            boolean entryPoint
    ) {
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                method,
                helperMethods,
                structs,
                entryPoint,
                mode,
                diagnosticPolicy,
                diagnosticReporter,
                reportSink
        );
        for (GpuIrValidationProvider provider : providers) {
            provider.validate(request);
        }
    }
}
