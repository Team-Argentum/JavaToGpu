package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionException;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Hardware-free harness for {@link GpuIrValidationProvider} ServiceLoader extensions.
 */
public final class GpuIrValidationProviderHarness {
    private final List<GpuIrValidationProvider> providers;
    private final GpuIrValidationMode mode;
    private final GpuIrValidationDiagnosticPolicy diagnosticPolicy;

    public GpuIrValidationProviderHarness(List<GpuIrValidationProvider> providers) {
        this(providers, GpuIrValidationMode.DIAGNOSTIC, GpuIrValidationDiagnosticPolicy.SUMMARY);
    }

    public GpuIrValidationProviderHarness(
            List<GpuIrValidationProvider> providers,
            GpuIrValidationMode mode,
            GpuIrValidationDiagnosticPolicy diagnosticPolicy
    ) {
        this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
        this.mode = mode == null ? GpuIrValidationMode.DIAGNOSTIC : mode;
        this.diagnosticPolicy = diagnosticPolicy == null ? GpuIrValidationDiagnosticPolicy.SUMMARY : diagnosticPolicy;
    }

    public static GpuIrValidationProviderHarness loadFromServiceLoader() {
        return loadFromServiceLoader(GpuIrValidationMode.DIAGNOSTIC);
    }

    public static GpuIrValidationProviderHarness loadFromServiceLoader(GpuIrValidationMode mode) {
        return loadFromServiceLoader(mode, GpuIrValidationDiagnosticPolicy.SUMMARY, contextClassLoader());
    }

    public static GpuIrValidationProviderHarness loadFromServiceLoader(
            GpuIrValidationMode mode,
            GpuIrValidationDiagnosticPolicy diagnosticPolicy,
            ClassLoader classLoader
    ) {
        ArrayList<GpuIrValidationProvider> loadedProviders = new ArrayList<>();
        ServiceLoader.load(
                GpuIrValidationProvider.class,
                classLoader == null ? contextClassLoader() : classLoader
        ).forEach(loadedProviders::add);
        loadedProviders.sort(Comparator
                .comparingInt(GpuExtension::extensionOrder)
                .thenComparing(GpuExtension::extensionId)
                .thenComparing(GpuExtension::extensionVersion));
        return new GpuIrValidationProviderHarness(loadedProviders, mode, diagnosticPolicy);
    }

    public List<GpuIrValidationProvider> providers() {
        return providers;
    }

    public GpuIrValidationProviderHarnessReport runSynthetic() {
        ArrayList<String> diagnostics = new ArrayList<>();
        ArrayList<GpuIrValidationReportEntry> entries = new ArrayList<>();
        ArrayList<GpuExtensionExecutionReport> executionReports = new ArrayList<>();
        Map<String, String> extensionFields = Map.of("irValidationExtension.count", Integer.toString(providers.size()));
        try {
            GpuIrValidationRunner runner = new GpuIrValidationRunner(
                    providers,
                    mode,
                    diagnosticPolicy,
                    diagnostics::add,
                    entries::add
            );
            extensionFields = runner.extensionArtifactFields();
            executionReports.addAll(runner.runWithReport(
                    syntheticMethod("kernel", true),
                    List.of(syntheticMethod("helper", false)),
                    List.of()
            ));
        } catch (GpuExtensionExecutionException exception) {
            executionReports.add(exception.report());
            diagnostics.add(exception.report().toLine());
        } catch (RuntimeException exception) {
            diagnostics.add(exception.getClass().getName() + ": " + message(exception));
            extensionFields = Map.of(
                    "irValidationExtension.count", Integer.toString(providers.size()),
                    "irValidationExtension.contractError", message(exception),
                    "irValidationExtension.contractFailureType", exception.getClass().getName()
            );
        }
        return new GpuIrValidationProviderHarnessReport(
                mode,
                extensionFields,
                executionReports,
                entries,
                diagnostics
        );
    }

    private static GpuIrCompiledMethod syntheticMethod(String name, boolean entryPoint) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "SyntheticIrValidationHarness",
                "net.sixik.ga_utils.javatogpu.synthetic.SyntheticIrValidationHarness",
                name,
                entryPoint ? "float" : "void",
                List.of(),
                List.of(),
                List.of(),
                null,
                !entryPoint,
                List.of(),
                null,
                "",
                null,
                false
        );
        return new GpuIrCompiledMethod(
                parsedMethod,
                new GpuIrMethod(name, List.of()),
                "jtg_" + name,
                List.of()
        );
    }

    private static String message(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader == null ? GpuIrValidationProviderHarness.class.getClassLoader() : classLoader;
    }
}
