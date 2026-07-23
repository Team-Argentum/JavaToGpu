package net.sixik.ga_utils.javatogpu.runtime.validation;

import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPass;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendArtifactHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilationHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilerFeedbackProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendDiscoveryContributor;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendInvocationHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLoweringHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendPolicyContributor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendScoreContributor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicy;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationPass;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizer;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrPeepholeRule;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventListener;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleService;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLogService;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodVariantProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeNativeMemoryService;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Catalog of supported ServiceLoader extension points.
 *
 * <p>This is a documentation and validation helper. Runtime registries still own actual discovery, ordering, and
 * authorization. Keeping the service names here gives docs, examples, and tests one stable place to check before adding
 * another ad-hoc extension registration path.</p>
 */
public final class GpuRuntimeExtensionPointCatalog {

    private static final List<ExtensionPoint> EXTENSION_POINTS = buildExtensionPoints();
    private static final Map<String, ExtensionPoint> BY_SERVICE_TYPE = byServiceType(EXTENSION_POINTS);

    private GpuRuntimeExtensionPointCatalog() {
    }

    /**
     * Returns all known ServiceLoader extension points, including legacy and internal compiler-pass entries.
     */
    public static List<ExtensionPoint> all() {
        return EXTENSION_POINTS;
    }

    /**
     * Returns extension points that are intended for third-party modules or advanced application integrations.
     */
    public static List<ExtensionPoint> publicExtensionPoints() {
        return EXTENSION_POINTS.stream()
                .filter(point -> point.audience() != GpuRuntimePackageTaxonomy.Audience.IMPLEMENTATION_DETAIL)
                .toList();
    }

    /**
     * Finds a catalog entry by fully qualified service type name.
     */
    public static Optional<ExtensionPoint> findByServiceType(String serviceTypeName) {
        if (serviceTypeName == null || serviceTypeName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_SERVICE_TYPE.get(serviceTypeName.trim()));
    }

    /**
     * Renders a compact Markdown table for docs and diagnostic output.
     */
    public static String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("| Service type | Audience | Domain | Permission | Harness | Summary |")
                .append(System.lineSeparator());
        builder.append("| --- | --- | --- | --- | --- | --- |").append(System.lineSeparator());
        for (ExtensionPoint point : publicExtensionPoints()) {
            builder.append("| `")
                    .append(point.serviceTypeName())
                    .append("` | `")
                    .append(point.audience().name())
                    .append("` | `")
                    .append(point.domainPackage())
                    .append("` | ")
                    .append(point.permissionModel())
                    .append(" | ")
                    .append(point.harness().isBlank() ? "-" : "`")
                    .append(point.harness())
                    .append(point.harness().isBlank() ? "" : "`")
                    .append(" | ")
                    .append(point.summary())
                    .append(" |")
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static List<ExtensionPoint> buildExtensionPoints() {
        return List.of(
                point(
                        GpuRuntimeLogService.class,
                        "runtime.observability",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "read-only",
                        "GpuRuntimeObservabilityServiceHarness",
                        "Route framework-neutral runtime log records to Log4J, SLF4J, files, or metrics."
                ),
                point(
                        GpuRuntimeLifecycleService.class,
                        "runtime.observability",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "read-only",
                        "GpuRuntimeObservabilityServiceHarness",
                        "Observe lifecycle stages for journals, traces, metrics, and diagnostics."
                ),
                point(
                        GpuRuntimeLifecycleEventListener.class,
                        "runtime.observability",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "read-only",
                        "GpuRuntimeObservabilityServiceHarness",
                        "Low-level lifecycle listener contract; prefer GpuRuntimeLifecycleService for new integrations."
                ),
                point(
                        GpuIrValidationProvider.class,
                        "frontend.ir.validation",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "read-only",
                        "GpuIrValidationProviderHarness",
                        "Add read-only IR validation diagnostics without rewriting IR."
                ),
                point(
                        GpuRuntimeIrOptimizationPass.class,
                        "runtime.optimization",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "mutation proposal",
                        "optimizer report/artifact gates",
                        "Add staged IR optimizer passes that still go through proof and production gates."
                ),
                point(
                        GpuRuntimeIrPeepholeRule.class,
                        "runtime.optimization",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "mutation proposal",
                        "peephole report/artifact gates",
                        "Add one typed-IR peephole analysis or rewrite proposal rule."
                ),
                point(
                        GpuRuntimeIrOptimizer.class,
                        "runtime.optimization",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "legacy mutation proposal",
                        "optimizer report/artifact gates",
                        "Legacy optimizer compatibility service; prefer GpuRuntimeIrOptimizationPass."
                ),
                point(
                        GpuBackendCompilerFeedbackProvider.class,
                        "runtime.diagnostics",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "read-only",
                        "GpuBackendCompilerFeedbackHarness",
                        "Parse backend compiler logs into advisory resource metrics."
                ),
                point(
                        GpuRuntimeBackendProvider.class,
                        "runtime.spi",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "stage-specific",
                        "GpuRuntimeBackendProviderCatalog",
                        "Register a backend family and adapter without opening native sessions during catalog inspection."
                ),
                point(
                        GpuRuntimeNativeMemoryService.class,
                        "runtime.memory",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "production-affecting",
                        "GpuRuntimeNativeMemoryServiceRegistry",
                        "Provide native host-memory allocation, including future Panama-backed providers."
                ),
                point(
                        GpuRuntimeDevicePolicy.class,
                        "runtime.selection",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "read-only with hard rejections",
                        "GpuRuntimeDevicePolicyHarness",
                        "Contribute device-ranking evidence, capability facts, quirks, and compile-option diagnostics."
                ),
                point(
                        GpuBackendHook.class,
                        "runtime.hooks",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "read-only by default",
                        "GpuBackendHookTestHarness",
                        "Generic backend hook registration point for shared metadata and authorization."
                ),
                point(
                        GpuBackendPolicyContributor.class,
                        "runtime.hooks",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "read-only",
                        "GpuBackendHookTestHarness",
                        "Contribute backend-selection policy facts or requirements."
                ),
                point(
                        GpuRuntimeBackendScoreContributor.class,
                        "runtime.hooks",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "read-only",
                        "GpuBackendHookTestHarness",
                        "Contribute explainable backend candidate score adjustments."
                ),
                point(
                        GpuBackendDiscoveryContributor.class,
                        "runtime.hooks",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "read-only",
                        "GpuBackendHookTestHarness",
                        "Observe discovery results and attach discovery facts."
                ),
                point(
                        GpuBackendLoweringHook.class,
                        "runtime.hooks",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "read-only observer unless explicitly authorized",
                        "GpuBackendHookTestHarness",
                        "Observe backend lowering receipts."
                ),
                point(
                        GpuBackendCompilationHook.class,
                        "runtime.hooks",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "read-only observer unless explicitly authorized",
                        "GpuBackendHookTestHarness",
                        "Observe backend compilation receipts."
                ),
                point(
                        GpuBackendInvocationHook.class,
                        "runtime.hooks",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "read-only observer unless explicitly authorized",
                        "GpuBackendHookTestHarness",
                        "Observe backend invocation receipts."
                ),
                point(
                        GpuBackendArtifactHook.class,
                        "runtime.hooks",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "read-only",
                        "GpuBackendHookTestHarness",
                        "Attach backend artifact fields for diagnostics and CI."
                ),
                point(
                        GpuRuntimeMethodVariantProvider.class,
                        "runtime.variants",
                        GpuRuntimePackageTaxonomy.Audience.ADVANCED_RUNTIME,
                        "generated or advanced provider",
                        "GpuRuntimeMethodVariantRegistry",
                        "Expose generated or third-party fallback variants across module boundaries."
                ),
                point(
                        GpuIrPass.class,
                        "frontend.ir.passes",
                        GpuRuntimePackageTaxonomy.Audience.IMPLEMENTATION_DETAIL,
                        "compiler-internal",
                        "-",
                        "Compiler IR pass hook loaded by the frontend; not a normal application extension point."
                )
        ).stream()
                .sorted(Comparator.comparing(ExtensionPoint::serviceTypeName))
                .toList();
    }

    private static ExtensionPoint point(
            Class<?> serviceType,
            String domainPackage,
            GpuRuntimePackageTaxonomy.Audience audience,
            String permissionModel,
            String harness,
            String summary
    ) {
        String serviceTypeName = serviceType.getName();
        return new ExtensionPoint(
                serviceTypeName,
                domainPackage,
                audience,
                permissionModel,
                "META-INF/services/" + serviceTypeName,
                harness,
                summary
        );
    }

    private static Map<String, ExtensionPoint> byServiceType(List<ExtensionPoint> points) {
        LinkedHashMap<String, ExtensionPoint> byType = new LinkedHashMap<>();
        for (ExtensionPoint point : points) {
            ExtensionPoint previous = byType.putIfAbsent(point.serviceTypeName(), point);
            if (previous != null) {
                throw new IllegalStateException("Duplicate extension point service type: " + point.serviceTypeName());
            }
        }
        return Map.copyOf(byType);
    }

    /**
     * One ServiceLoader extension point entry.
     */
    public record ExtensionPoint(
            String serviceTypeName,
            String domainPackage,
            GpuRuntimePackageTaxonomy.Audience audience,
            String permissionModel,
            String registrationFile,
            String harness,
            String summary
    ) {
        public ExtensionPoint {
            serviceTypeName = requireText(serviceTypeName, "serviceTypeName");
            domainPackage = requireText(domainPackage, "domainPackage");
            audience = Objects.requireNonNull(audience, "audience");
            permissionModel = requireText(permissionModel, "permissionModel");
            registrationFile = requireText(registrationFile, "registrationFile");
            harness = harness == null ? "" : harness.trim();
            summary = requireText(summary, "summary");
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value.trim();
        }
    }
}
