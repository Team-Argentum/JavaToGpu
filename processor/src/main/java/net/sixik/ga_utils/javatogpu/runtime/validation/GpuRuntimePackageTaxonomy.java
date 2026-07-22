package net.sixik.ga_utils.javatogpu.runtime.validation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Classifies the currently flat runtime package into the domains it should be split into over time.
 *
 * <p>This is an API/QOL guardrail, not a runtime feature. New code should prefer the recommended domain packages instead
 * of adding another public class directly under {@code net.sixik.ga_utils.javatogpu.runtime}. Compatibility classes that
 * already exist in the root package can move gradually only when a stable shim or migration path exists.</p>
 */
public final class GpuRuntimePackageTaxonomy {

    private static final Map<Domain, String> RECOMMENDED_PACKAGES = recommendedPackages();

    private GpuRuntimePackageTaxonomy() {
    }

    /**
     * Major domains currently mixed together in the root runtime package.
     */
    public enum Domain {
        /** User-facing runtime entry points and compatibility types that must stay easy to find. */
        USER_API,
        /** Kernel descriptors, invocation frames, generated-launcher helpers, and launch configuration. */
        LAUNCH_AND_DESCRIPTOR,
        /** Backend provider, lowerer, compiler, and execution-pipeline SPI contracts. */
        BACKEND_SPI,
        /** Backend hook discovery, catalog, authorization preview, and read-only stage observation. */
        BACKEND_HOOKS,
        /** Backend/device selection, scoring, workload hints, and device policy support. */
        SELECTION_AND_DEVICE_POLICY,
        /** Runtime artifact maps, diagnostics, production gates, promotion evidence, and failure reporting. */
        ARTIFACTS_AND_DIAGNOSTICS,
        /** Lifecycle events, logging services, journals, and observability harnesses. */
        OBSERVABILITY,
        /** Fixture-backed method-test and selection-probe support. */
        METHOD_TESTS,
        /** Runtime method fallback variant discovery and selection. */
        METHOD_VARIANTS,
        /** Runtime IR analysis, optimizer, peephole, and register-pressure support. */
        IR_OPTIMIZATION,
        /** Native-memory allocation services and provider bridges. */
        NATIVE_MEMORY,
        /** CLI entry points, report validators, test harnesses, and contract check helpers. */
        VALIDATION_AND_HARNESSES,
        /** Built-in backend provider placeholders and internal adapter glue that should not become user API. */
        INTERNAL_BACKEND_IMPLEMENTATION
    }

    /**
     * Returns the recommended future package for each domain.
     */
    public static Map<Domain, String> recommendedPackages() {
        EnumMap<Domain, String> packages = new EnumMap<>(Domain.class);
        packages.put(Domain.USER_API, "net.sixik.ga_utils.javatogpu.api and runtime compatibility shims");
        packages.put(Domain.LAUNCH_AND_DESCRIPTOR, "net.sixik.ga_utils.javatogpu.runtime.launch");
        packages.put(Domain.BACKEND_SPI, "net.sixik.ga_utils.javatogpu.runtime.spi");
        packages.put(Domain.BACKEND_HOOKS, "net.sixik.ga_utils.javatogpu.runtime.hooks");
        packages.put(Domain.SELECTION_AND_DEVICE_POLICY, "net.sixik.ga_utils.javatogpu.runtime.selection");
        packages.put(Domain.ARTIFACTS_AND_DIAGNOSTICS, "net.sixik.ga_utils.javatogpu.runtime.diagnostics");
        packages.put(Domain.OBSERVABILITY, "net.sixik.ga_utils.javatogpu.runtime.observability");
        packages.put(Domain.METHOD_TESTS, "net.sixik.ga_utils.javatogpu.runtime.methodtest");
        packages.put(Domain.METHOD_VARIANTS, "net.sixik.ga_utils.javatogpu.runtime.variants");
        packages.put(Domain.IR_OPTIMIZATION, "net.sixik.ga_utils.javatogpu.runtime.optimization");
        packages.put(Domain.NATIVE_MEMORY, "net.sixik.ga_utils.javatogpu.runtime.memory");
        packages.put(Domain.VALIDATION_AND_HARNESSES, "net.sixik.ga_utils.javatogpu.runtime.validation");
        packages.put(Domain.INTERNAL_BACKEND_IMPLEMENTATION, "backend-specific implementation packages");
        return Collections.unmodifiableMap(packages);
    }

    /**
     * Returns the recommended future package for the given domain.
     */
    public static String recommendedPackage(Domain domain) {
        return RECOMMENDED_PACKAGES.get(domain);
    }

    /**
     * Classifies a root-runtime Java class by simple class name.
     */
    public static Optional<Domain> classifyRootRuntimeClass(String simpleName) {
        if (simpleName == null || simpleName.isBlank() || "package-info".equals(simpleName)) {
            return Optional.empty();
        }

        if (isValidationOrHarness(simpleName)) {
            return Optional.of(Domain.VALIDATION_AND_HARNESSES);
        }
        if (startsWith(simpleName, "GpuRuntimeMethodTest")) {
            return Optional.of(Domain.METHOD_TESTS);
        }
        if (startsWith(simpleName, "GpuRuntimeMethodVariant")) {
            return Optional.of(Domain.METHOD_VARIANTS);
        }
        if (isObservability(simpleName)) {
            return Optional.of(Domain.OBSERVABILITY);
        }
        if (isNativeMemory(simpleName)) {
            return Optional.of(Domain.NATIVE_MEMORY);
        }
        if (isRuntimeIrOptimization(simpleName)) {
            return Optional.of(Domain.IR_OPTIMIZATION);
        }
        if (isSelectionOrDevicePolicy(simpleName)) {
            return Optional.of(Domain.SELECTION_AND_DEVICE_POLICY);
        }
        if (isBackendHook(simpleName)) {
            return Optional.of(Domain.BACKEND_HOOKS);
        }
        if (isBackendSpi(simpleName)) {
            return Optional.of(Domain.BACKEND_SPI);
        }
        if (isInternalBackendImplementation(simpleName)) {
            return Optional.of(Domain.INTERNAL_BACKEND_IMPLEMENTATION);
        }
        if (isLaunchOrDescriptor(simpleName)) {
            return Optional.of(Domain.LAUNCH_AND_DESCRIPTOR);
        }
        if (isArtifactOrDiagnostic(simpleName)) {
            return Optional.of(Domain.ARTIFACTS_AND_DIAGNOSTICS);
        }
        if (isUserApi(simpleName)) {
            return Optional.of(Domain.USER_API);
        }
        return Optional.empty();
    }

    private static boolean isValidationOrHarness(String simpleName) {
        return endsWithAny(simpleName, "Cli", "Validator")
                || containsAny(simpleName, "Harness", "ContractReport", "Validation")
                || "GpuRuntimePackageTaxonomy".equals(simpleName);
    }

    private static boolean isObservability(String simpleName) {
        return containsAny(simpleName, "Lifecycle", "Log", "Observability");
    }

    private static boolean isNativeMemory(String simpleName) {
        return containsAny(simpleName, "NativeMemory") || startsWith(simpleName, "LwjglGpuRuntimeNativeMemory");
    }

    private static boolean isRuntimeIrOptimization(String simpleName) {
        return startsWithAny(simpleName,
                "GpuRuntimeIr",
                "GpuRuntimeEquivalence",
                "GpuRuntimeClamp",
                "GpuRuntimeDot",
                "GpuRuntimeMadFma",
                "GpuRuntimeMix",
                "GpuRuntimeStep",
                "GpuRuntimeCommonSubexpression",
                "GpuRuntimeRegisterPressure",
                "GpuOptimization")
                || containsAny(simpleName, "Optimizer", "Optimization");
    }

    private static boolean isSelectionOrDevicePolicy(String simpleName) {
        return startsWithAny(simpleName,
                "GpuRuntimeDevice",
                "GpuRuntimeBackendCandidate",
                "GpuRuntimeBackendSelection",
                "GpuRuntimeBackendDevice",
                "GpuRuntimeBackendPolicy",
                "GpuRuntimeBackendRequirement",
                "GpuRuntimeBackendScore",
                "GpuRuntimeWorkload",
                "GpuRuntimeInferredWorkload",
                "GpuRuntimeExplicitDeviceOverride",
                "GpuRuntimeMethodDeviceConstraint")
                || "GpuRuntimeSelectionResult".equals(simpleName);
    }

    private static boolean isBackendHook(String simpleName) {
        return startsWith(simpleName, "GpuBackendHook")
                || endsWithAny(simpleName, "Hook")
                || containsAny(simpleName, "HookAuthorization");
    }

    private static boolean isBackendSpi(String simpleName) {
        return startsWith(simpleName, "GpuBackend")
                || startsWithAny(simpleName,
                "GpuRuntimeBackend",
                "GpuPreparedKernel",
                "GpuGenericCompilerFeedbackProvider")
                || endsWithAny(simpleName, "BackendProvider", "BackendAdapter", "BackendFactory", "BackendLowerer");
    }

    private static boolean isInternalBackendImplementation(String simpleName) {
        return startsWithAny(simpleName,
                "CudaRuntimeBackend",
                "OpenClRuntimeBackend",
                "PlannedGpuRuntimeBackend",
                "UnsupportedGpuBackend",
                "UnsupportedGpuRuntimeBackend");
    }

    private static boolean isLaunchOrDescriptor(String simpleName) {
        return startsWithAny(simpleName,
                "GpuKernel",
                "GpuGeneratedLauncher",
                "GpuLauncher",
                "GpuMethodBodyRewriter")
                || "GpuExecutionConfig".equals(simpleName)
                || "GpuMemorySlice".equals(simpleName)
                || "GpuRuntimeInvocationBindingSummary".equals(simpleName);
    }

    private static boolean isArtifactOrDiagnostic(String simpleName) {
        return startsWithAny(simpleName,
                "GpuRuntimeArtifact",
                "GpuRuntimeBinaryArtifact",
                "GpuRuntimeCallSite",
                "GpuRuntimeCompileArtifact",
                "GpuRuntimeCompileCache",
                "GpuRuntimeCompileInvalidation",
                "GpuRuntimeCompileProvenance",
                "GpuRuntimeDiagnostic",
                "GpuRuntimeExtensionParticipationArtifact",
                "GpuRuntimeFallback",
                "GpuRuntimeFailure",
                "GpuRuntimeProductionProfiles",
                "GpuProduction",
                "GpuPromotion")
                || endsWithAny(simpleName, "Exception", "Report", "Summary")
                || containsAny(simpleName, "Explainability", "Acceptance", "Activation", "Gate", "Manifest");
    }

    private static boolean isUserApi(String simpleName) {
        return startsWithAny(simpleName,
                "GpuRuntimeCompileOptions",
                "GpuRuntimeCompileRequest")
                || equalsAny(simpleName,
                "GpuRuntime",
                "GpuRuntimeApiVersion",
                "GpuRuntimeBackendReport",
                "GpuRuntimeCapability",
                "GpuRuntimeFeature",
                "GpuRuntimeRequirement",
                "GpuRuntimeRequirements",
                "GpuRuntimeScope");
    }

    private static boolean startsWith(String value, String prefix) {
        return value.startsWith(prefix);
    }

    private static boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithAny(String value, String... suffixes) {
        for (String suffix : suffixes) {
            if (value.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean equalsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.equals(candidate)) {
                return true;
            }
        }
        return false;
    }
}
