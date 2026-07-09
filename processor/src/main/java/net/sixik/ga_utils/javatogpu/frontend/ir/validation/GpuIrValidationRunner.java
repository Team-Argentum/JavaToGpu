package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionException;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionFailurePolicy;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionRegistry;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassException;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Consumer;

/**
 * Loads and executes optional read-only IR validation providers.
 */
public final class GpuIrValidationRunner {
    private final List<GpuIrValidationProvider> providers;
    private final GpuExtensionRegistry extensionRegistry;
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
        this.extensionRegistry = GpuExtensionRegistry.of(this.providers);
        this.extensionRegistry.requirePipelineContract(
                "IR validation pipeline",
                GpuExtensionPhase.IR_VALIDATION,
                GpuExtensionPermission.READ_ONLY,
                GpuExtensionCapability.IR_VALIDATION
        );
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
        loadedProviders.sort(Comparator
                .comparingInt(GpuExtension::extensionOrder)
                .thenComparing(GpuExtension::extensionId)
                .thenComparing(GpuExtension::extensionVersion));
        return new GpuIrValidationRunner(loadedProviders, mode, diagnosticPolicy, diagnosticReporter, reportSink);
    }

    public List<GpuIrValidationProvider> validationProviders() {
        return providers;
    }

    public GpuExtensionRegistry extensionRegistry() {
        return extensionRegistry;
    }

    public Map<String, String> extensionArtifactFields() {
        return extensionRegistry.artifactFields("irValidationExtension");
    }

    public void run(
            GpuIrCompiledMethod compiledKernel,
            List<GpuIrCompiledMethod> compiledHelpers,
            List<ParsedGpuStruct> structs
    ) {
        runWithReport(compiledKernel, compiledHelpers, structs);
    }

    public List<GpuExtensionExecutionReport> runWithReport(
            GpuIrCompiledMethod compiledKernel,
            List<GpuIrCompiledMethod> compiledHelpers,
            List<ParsedGpuStruct> structs
    ) {
        if (mode == GpuIrValidationMode.OFF || providers.isEmpty()) {
            return List.of();
        }

        ArrayList<GpuExtensionExecutionReport> executionReports = new ArrayList<>();
        for (GpuIrCompiledMethod helper : compiledHelpers) {
            runForMethod(helper, compiledHelpers, structs, false, executionReports);
        }
        runForMethod(compiledKernel, compiledHelpers, structs, true, executionReports);
        return List.copyOf(executionReports);
    }

    private void runForMethod(
            GpuIrCompiledMethod method,
            List<GpuIrCompiledMethod> helperMethods,
            List<ParsedGpuStruct> structs,
            boolean entryPoint,
            List<GpuExtensionExecutionReport> executionReports
    ) {
        for (GpuIrValidationProvider provider : providers) {
            String sourceAnchor = sourceAnchor(method);
            GpuIrValidationRequest request = new GpuIrValidationRequest(
                    method,
                    helperMethods,
                    structs,
                    entryPoint,
                    mode,
                    diagnosticPolicy,
                    diagnosticReporter,
                    entry -> reportSink.accept(entry.withExtensionContext(
                            provider.extensionId(),
                            provider.extensionVersion(),
                            sourceAnchor,
                            method.parsedMethod().name(),
                            entryPoint
                    ))
            );
            try {
                provider.validate(request);
                executionReports.add(GpuExtensionExecutionReport.succeeded(
                        provider,
                        "IR validation " + method.parsedMethod().name()
                ));
            } catch (RuntimeException exception) {
                GpuExtensionFailurePolicy failurePolicy = mode == GpuIrValidationMode.DIAGNOSTIC
                        ? GpuExtensionFailurePolicy.CONTINUE
                        : GpuExtensionFailurePolicy.THROW;
                GpuExtensionExecutionReport executionReport = GpuExtensionExecutionReport.failed(
                        provider,
                        "IR validation " + method.parsedMethod().name(),
                        failurePolicy,
                        exception
                );
                executionReports.add(executionReport);
                request.reportDiagnostic(executionReport.toLine());
                request.reportEntry(new GpuIrValidationReportEntry(
                        provider.extensionId(),
                        method.parsedMethod().name(),
                        entryPoint,
                        executionReport.artifactFields("extensionExecution")
                ));
                if (failurePolicy == GpuExtensionFailurePolicy.THROW) {
                    if (exception instanceof GpuIrPassException passException) {
                        throw passException;
                    }
                    throw new GpuExtensionExecutionException(executionReport, exception);
                }
            }
        }
    }

    private static String sourceAnchor(GpuIrCompiledMethod method) {
        String owner = method.parsedMethod().ownerQualifiedName();
        String methodName = method.parsedMethod().name();
        com.github.javaparser.ast.body.MethodDeclaration declaration = method.parsedMethod().declaration();
        if (declaration == null) {
            return "asm:" + owner + "#" + methodName;
        }
        return declaration.getRange()
                .map(range -> "java:" + owner + "#" + methodName + ":" + range.begin.line + ":" + range.begin.column)
                .orElse("java:" + owner + "#" + methodName);
    }
}
