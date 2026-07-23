package net.sixik.ga_utils.javatogpu.runtime.hooks;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionDescriptor;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionRegistry;
import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Deterministic catalog and read-only executor for backend SPI hooks.
 *
 * <p>The registry discovers and validates backend hooks, renders their catalog metadata, and can execute read-only
 * discovery, lowering, compilation, invocation, and artifact hooks as fail-soft observers. Stage observers never replace
 * production results: if a read-only hook returns a different result object, the replacement is ignored and reported in
 * lifecycle fields.</p>
 */
public final class GpuBackendHookRegistry {

    private static final GpuBackendHookRegistry EMPTY = new GpuBackendHookRegistry(List.of());

    private final List<GpuBackendHook> hooks;
    private final GpuExtensionRegistry extensionRegistry;

    private GpuBackendHookRegistry(List<GpuBackendHook> hooks) {
        this.hooks = List.copyOf(hooks);
        this.extensionRegistry = GpuExtensionRegistry.of(this.hooks);
    }

    public static GpuBackendHookRegistry empty() {
        return EMPTY;
    }

    public static GpuBackendHookRegistry of(Collection<? extends GpuBackendHook> hooks) {
        if (hooks == null || hooks.isEmpty()) {
            return empty();
        }
        ArrayList<GpuBackendHook> sorted = new ArrayList<>();
        for (GpuBackendHook hook : hooks) {
            sorted.add(Objects.requireNonNull(hook, "hook"));
        }
        sorted.sort(Comparator
                .comparingInt(GpuBackendHook::extensionOrder)
                .thenComparing(GpuBackendHook::extensionId)
                .thenComparing(GpuBackendHook::extensionVersion)
                .thenComparing(hook -> hook.getClass().getName()));
        validateBackendHookContracts(sorted);
        return new GpuBackendHookRegistry(sorted);
    }

    public static GpuBackendHookRegistry loadWithServiceLoader() {
        return loadWithServiceLoader(contextClassLoader());
    }

    public static GpuBackendHookRegistry loadWithServiceLoader(ClassLoader classLoader) {
        ClassLoader loader = classLoader == null ? contextClassLoader() : classLoader;
        LinkedHashMap<String, GpuBackendHook> loaded = new LinkedHashMap<>();
        loadService(loader, GpuBackendHook.class, loaded);
        loadService(loader, GpuBackendPolicyContributor.class, loaded);
        loadService(loader, GpuRuntimeBackendScoreContributor.class, loaded);
        loadService(loader, GpuBackendDiscoveryContributor.class, loaded);
        loadService(loader, GpuBackendLoweringHook.class, loaded);
        loadService(loader, GpuBackendCompilationHook.class, loaded);
        loadService(loader, GpuBackendInvocationHook.class, loaded);
        loadService(loader, GpuBackendArtifactHook.class, loaded);
        return of(loaded.values());
    }

    public List<GpuBackendHook> hooks() {
        return hooks;
    }

    public GpuExtensionRegistry extensionRegistry() {
        return extensionRegistry;
    }

    public int size() {
        return hooks.size();
    }

    public boolean isEmpty() {
        return hooks.isEmpty();
    }

    public List<GpuBackendHook> forBackendTarget(GpuBackendTarget backendTarget) {
        return hooks.stream()
                .filter(hook -> hook.appliesTo(backendTarget))
                .toList();
    }

    public List<GpuBackendHook> forCapability(GpuExtensionCapability capability) {
        if (capability == null) {
            return List.of();
        }
        return hooks.stream()
                .filter(hook -> hook.extensionCapabilities().contains(capability))
                .toList();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.backend.hookRegistry" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".hook.count", Integer.toString(hooks.size()));
        for (int index = 0; index < hooks.size(); index++) {
            GpuBackendHook hook = hooks.get(index);
            String hookPrefix = normalizedPrefix + ".hook." + index;
            fields.put(hookPrefix + ".implementationClass", hook.getClass().getName());
            hook.artifactFields(hookPrefix).forEach((key, value) -> {
                if (key.startsWith(hookPrefix + ".")) {
                    fields.put(key, value);
                }
            });
        }
        fields.put("runtime.backend.hookRegistry.present", "true");
        fields.put("runtime.backend.hookRegistry.hook.count", Integer.toString(hooks.size()));
        fields.putAll(contractDiagnosticFields(normalizedPrefix + ".contract"));
        return Collections.unmodifiableMap(fields);
    }

    public Map<String, String> contractDiagnosticFields(String prefix) {
        String normalizedPrefix = normalizePrefix(prefix, "runtime.backend.hookRegistry.contract");
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        int warningCount = 0;
        int nonReadOnlyCount = 0;
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".hook.count", Integer.toString(hooks.size()));
        fields.put(normalizedPrefix + ".error.count", "0");
        for (int index = 0; index < hooks.size(); index++) {
            GpuBackendHook hook = hooks.get(index);
            GpuExtensionDescriptor descriptor = GpuExtensionDescriptor.from(hook);
            BackendHookContract contract = BackendHookContract.expectedFor(hook);
            boolean readOnly = descriptor.permission() == GpuExtensionPermission.READ_ONLY;
            if (!readOnly) {
                warningCount++;
                nonReadOnlyCount++;
            }

            String hookPrefix = normalizedPrefix + ".hook." + index;
            fields.put(hookPrefix + ".id", descriptor.id());
            fields.put(hookPrefix + ".implementationClass", descriptor.implementationClass());
            fields.put(hookPrefix + ".phase", descriptor.phase().name());
            fields.put(hookPrefix + ".permission", descriptor.permission().name());
            fields.put(hookPrefix + ".family.count", Integer.toString(contract.familyNames().size()));
            for (int familyIndex = 0; familyIndex < contract.familyNames().size(); familyIndex++) {
                fields.put(hookPrefix + ".family." + familyIndex, contract.familyNames().get(familyIndex));
            }
            fields.put(hookPrefix + ".expectedCapability.count", Integer.toString(contract.expectedCapabilities().size()));
            for (int capabilityIndex = 0; capabilityIndex < contract.expectedCapabilities().size(); capabilityIndex++) {
                fields.put(
                        hookPrefix + ".expectedCapability." + capabilityIndex,
                        contract.expectedCapabilities().get(capabilityIndex).name()
                );
            }
            fields.put(hookPrefix + ".expectedPhase.count", Integer.toString(contract.expectedPhases().size()));
            int phaseIndex = 0;
            for (GpuExtensionPhase phase : contract.expectedPhases()) {
                fields.put(hookPrefix + ".expectedPhase." + phaseIndex, phase.name());
                phaseIndex++;
            }
            fields.put(hookPrefix + ".status", readOnly ? "ok" : "authorization-required");
            fields.put(hookPrefix + ".diagnostic", readOnly
                    ? "backend hook contract is valid for read-only execution"
                    : "hook declares " + descriptor.permission()
                    + "; current backend hook registry will skip execution until explicit production authorization exists");
        }
        fields.put(normalizedPrefix + ".warning.count", Integer.toString(warningCount));
        fields.put(normalizedPrefix + ".nonReadOnly.count", Integer.toString(nonReadOnlyCount));
        fields.put("runtime.backend.hookRegistry.contract.present", "true");
        fields.put("runtime.backend.hookRegistry.contract.hook.count", Integer.toString(hooks.size()));
        fields.put("runtime.backend.hookRegistry.contract.warning.count", Integer.toString(warningCount));
        fields.put("runtime.backend.hookRegistry.contract.nonReadOnly.count", Integer.toString(nonReadOnlyCount));
        return Collections.unmodifiableMap(fields);
    }

    public GpuBackendHookAuthorizationReport authorizationReport(
            GpuBackendTarget backendTarget,
            GpuExtensionPhase phase
    ) {
        return authorizationReport(backendTarget, phase, GpuBackendHookAuthorizationPolicy.readOnlyOnly());
    }

    public GpuBackendHookAuthorizationReport authorizationReport(
            GpuBackendTarget backendTarget,
            GpuExtensionPhase phase,
            GpuBackendHookAuthorizationPolicy policy
    ) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        GpuExtensionPhase requestedPhase = phase == null ? GpuExtensionPhase.BACKEND_INVOCATION : phase;
        GpuBackendHookAuthorizationPolicy authorizationPolicy = policy == null
                ? GpuBackendHookAuthorizationPolicy.readOnlyOnly()
                : policy;
        ArrayList<GpuBackendHookAuthorizationDecision> decisions = new ArrayList<>();
        for (GpuBackendHook hook : hooks) {
            decisions.add(authorizationPolicy.evaluate(hook, target, requestedPhase));
        }
        return new GpuBackendHookAuthorizationReport(target, requestedPhase, authorizationPolicy, decisions);
    }

    public GpuBackendHookAuthorizationCatalog authorizationCatalog(GpuBackendTarget backendTarget) {
        return authorizationCatalog(backendTarget, GpuBackendHookAuthorizationPolicy.readOnlyOnly());
    }

    public GpuBackendHookAuthorizationCatalog authorizationCatalog(
            GpuBackendTarget backendTarget,
            GpuBackendHookAuthorizationPolicy policy
    ) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        GpuBackendHookAuthorizationPolicy authorizationPolicy = policy == null
                ? GpuBackendHookAuthorizationPolicy.readOnlyOnly()
                : policy;
        return new GpuBackendHookAuthorizationCatalog(
                target,
                authorizationPolicy,
                List.of(
                        authorizationReport(target, GpuExtensionPhase.BACKEND_DISCOVERY, authorizationPolicy),
                        authorizationReport(target, GpuExtensionPhase.BACKEND_LOWERING, authorizationPolicy),
                        authorizationReport(target, GpuExtensionPhase.BACKEND_COMPILATION, authorizationPolicy),
                        authorizationReport(target, GpuExtensionPhase.BACKEND_INVOCATION, authorizationPolicy),
                        authorizationReport(target, GpuExtensionPhase.ARTIFACT_EMISSION, authorizationPolicy)
                )
        );
    }

    public Map<String, String> observeDiscovery(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDeviceDiscoveryResult discoveryResult,
            String prefix
    ) {
        GpuBackendTarget backendTarget = discoveryResult == null
                ? GpuBackendTarget.UNKNOWN
                : discoveryResult.backendTarget();
        return observeDiscovery(backendTarget, compileOptions, discoveryResult, prefix);
    }

    public Map<String, String> observeDiscovery(
            GpuBackendTarget backendTarget,
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDeviceDiscoveryResult discoveryResult,
            String prefix
    ) {
        String normalizedPrefix = normalizePrefix(prefix, "runtime.backend.hookExecution.discovery");
        HookExecutionFields fields = new HookExecutionFields(normalizedPrefix, GpuExtensionPhase.BACKEND_DISCOVERY);
        for (GpuBackendHook hook : hooks) {
            if (hook instanceof GpuBackendDiscoveryContributor discoveryContributor) {
                observeReadOnlyHook(fields, hook, backendTarget, () -> {
                    GpuRuntimeDeviceDiscoveryResult observed = discoveryContributor.afterDiscovery(
                            compileOptions,
                            discoveryResult
                    );
                    return new HookObservation(
                            observed == discoveryResult,
                            discoveryContributor.discoveryFacts(discoveryResult)
                    );
                });
            }
        }
        return fields.toFields();
    }

    public Map<String, String> observeLowering(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendLoweringResult loweringResult,
            String prefix
    ) {
        GpuBackendTarget backendTarget = loweringResult == null
                ? GpuBackendTarget.UNKNOWN
                : loweringResult.moduleArtifact().backendTarget();
        return observeLowering(backendTarget, compileRequest, loweringResult, prefix);
    }

    public Map<String, String> observeLowering(
            GpuBackendTarget backendTarget,
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendLoweringResult loweringResult,
            String prefix
    ) {
        String normalizedPrefix = normalizePrefix(prefix, "runtime.backend.hookExecution.lowering");
        HookExecutionFields fields = new HookExecutionFields(normalizedPrefix, GpuExtensionPhase.BACKEND_LOWERING);
        for (GpuBackendHook hook : hooks) {
            if (hook instanceof GpuBackendLoweringHook loweringHook) {
                observeReadOnlyHook(fields, hook, backendTarget, () -> {
                    GpuBackendLoweringResult observed = loweringHook.afterLowering(compileRequest, loweringResult);
                    return observed == loweringResult;
                });
            }
        }
        return fields.toFields();
    }

    public Map<String, String> observeCompilation(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendCompilationResult compilationResult,
            String prefix
    ) {
        GpuBackendTarget backendTarget = compilationResult == null
                ? GpuBackendTarget.UNKNOWN
                : compilationResult.moduleArtifact().backendTarget();
        return observeCompilation(backendTarget, compileRequest, compilationResult, prefix);
    }

    public Map<String, String> observeCompilation(
            GpuBackendTarget backendTarget,
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendCompilationResult compilationResult,
            String prefix
    ) {
        String normalizedPrefix = normalizePrefix(prefix, "runtime.backend.hookExecution.compilation");
        HookExecutionFields fields = new HookExecutionFields(normalizedPrefix, GpuExtensionPhase.BACKEND_COMPILATION);
        for (GpuBackendHook hook : hooks) {
            if (hook instanceof GpuBackendCompilationHook compilationHook) {
                observeReadOnlyHook(fields, hook, backendTarget, () -> {
                    GpuBackendCompilationResult observed = compilationHook.afterCompilation(compileRequest, compilationResult);
                    return observed == compilationResult;
                });
            }
        }
        return fields.toFields();
    }

    public Map<String, String> observeInvocation(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendInvocationResult invocationResult,
            String prefix
    ) {
        GpuBackendTarget backendTarget = invocationResult == null
                ? GpuBackendTarget.UNKNOWN
                : invocationResult.preparationResult().compilationResult().moduleArtifact().backendTarget();
        return observeInvocation(backendTarget, compileRequest, invocationResult, prefix);
    }

    public Map<String, String> observeInvocation(
            GpuBackendTarget backendTarget,
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendInvocationResult invocationResult,
            String prefix
    ) {
        String normalizedPrefix = normalizePrefix(prefix, "runtime.backend.hookExecution.invocation");
        HookExecutionFields fields = new HookExecutionFields(normalizedPrefix, GpuExtensionPhase.BACKEND_INVOCATION);
        for (GpuBackendHook hook : hooks) {
            if (hook instanceof GpuBackendInvocationHook invocationHook) {
                observeReadOnlyHook(fields, hook, backendTarget, () -> {
                    GpuBackendInvocationResult observed = invocationHook.afterInvocation(compileRequest, invocationResult);
                    return observed == invocationResult;
                });
            }
        }
        return fields.toFields();
    }

    public Map<String, String> contributeArtifactFields(
            GpuBackendTarget backendTarget,
            GpuRuntimeCompileRequest compileRequest,
            Map<String, String> currentFields,
            String prefix
    ) {
        String normalizedPrefix = normalizePrefix(prefix, "runtime.backend.hookExecution.artifact");
        HookExecutionFields fields = new HookExecutionFields(normalizedPrefix, GpuExtensionPhase.ARTIFACT_EMISSION);
        Map<String, String> snapshot = currentFields == null ? Map.of() : Map.copyOf(currentFields);
        for (GpuBackendHook hook : hooks) {
            if (hook instanceof GpuBackendArtifactHook artifactHook) {
                observeReadOnlyArtifactHook(fields, hook, backendTarget, () ->
                        artifactHook.contributeArtifactFields(compileRequest, snapshot)
                );
            }
        }
        return fields.toFields();
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Backend hooks: ").append(hooks.size()).append(System.lineSeparator());
        for (GpuBackendHook hook : hooks) {
            builder.append("- ")
                    .append(hook.extensionId())
                    .append(": phase=")
                    .append(hook.extensionPhase())
                    .append(", permission=")
                    .append(hook.extensionPermission())
                    .append(", failurePolicy=")
                    .append(hook.failurePolicy())
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static <T extends GpuBackendHook> void loadService(
            ClassLoader classLoader,
            Class<T> serviceType,
            LinkedHashMap<String, GpuBackendHook> loaded
    ) {
        ServiceLoader.load(serviceType, classLoader)
                .forEach(hook -> loaded.putIfAbsent(hook.getClass().getName(), hook));
    }

    private static void validateBackendHookContracts(List<GpuBackendHook> hooks) {
        LinkedHashMap<String, GpuExtensionDescriptor> descriptorsById = new LinkedHashMap<>();
        for (GpuBackendHook hook : hooks) {
            GpuExtensionDescriptor descriptor = GpuExtensionDescriptor.from(hook);
            GpuExtensionDescriptor previous = descriptorsById.putIfAbsent(descriptor.id(), descriptor);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate backend hook extension id '" + descriptor.id() + "': "
                                + previous.implementationClass() + " and " + descriptor.implementationClass()
                                + ". Give each ServiceLoader backend hook a unique extensionId()."
                );
            }
            validateBackendTargets(hook, descriptor);
            validateConcreteHookFamilyContract(descriptor, BackendHookContract.expectedFor(hook));
        }
    }

    private static void validateBackendTargets(GpuBackendHook hook, GpuExtensionDescriptor descriptor) {
        Set<GpuBackendTarget> targets = hook.backendTargets();
        if (targets != null && targets.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Backend hook '" + descriptor.id() + "' backendTargets() must not contain null. "
                            + "Use an empty set for all backend targets or explicit GpuBackendTarget values."
            );
        }
    }

    private static void validateConcreteHookFamilyContract(
            GpuExtensionDescriptor descriptor,
            BackendHookContract contract
    ) {
        if (contract.familyNames().isEmpty()) {
            return;
        }
        for (GpuExtensionCapability expectedCapability : contract.expectedCapabilities()) {
            if (!descriptor.capabilities().contains(expectedCapability)) {
                throw new IllegalArgumentException(
                        "Backend hook '" + descriptor.id() + "' implements " + String.join(", ", contract.familyNames())
                                + " but does not declare expected capability " + expectedCapability
                                + ". Declared capabilities: " + joinCapabilities(descriptor.capabilities())
                );
            }
        }
        if (!contract.expectedPhases().isEmpty() && !contract.expectedPhases().contains(descriptor.phase())) {
            throw new IllegalArgumentException(
                    "Backend hook '" + descriptor.id() + "' implements " + String.join(", ", contract.familyNames())
                            + " but declares phase " + descriptor.phase()
                            + ". Expected phase: " + joinPhases(contract.expectedPhases())
            );
        }
    }

    private static String joinCapabilities(Collection<GpuExtensionCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return "none";
        }
        ArrayList<String> names = new ArrayList<>();
        for (GpuExtensionCapability capability : capabilities) {
            names.add(capability.name());
        }
        return String.join(", ", names);
    }

    private static String joinPhases(Collection<GpuExtensionPhase> phases) {
        if (phases == null || phases.isEmpty()) {
            return "none";
        }
        ArrayList<String> names = new ArrayList<>();
        for (GpuExtensionPhase phase : phases) {
            names.add(phase.name());
        }
        return String.join(", ", names);
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader == null ? GpuBackendHookRegistry.class.getClassLoader() : classLoader;
    }

    private static void observeReadOnlyHook(
            HookExecutionFields fields,
            GpuBackendHook hook,
            GpuBackendTarget backendTarget,
            StageObserver observer
    ) {
        if (!hook.appliesTo(backendTarget)) {
            fields.recordSkipped(hook, "backend-target-filtered");
            return;
        }
        if (hook.extensionPermission() != GpuExtensionPermission.READ_ONLY) {
            fields.recordSkipped(hook, "non-read-only-permission");
            return;
        }
        try {
            boolean keptOriginalResult = observer.observe();
            fields.recordObserved(hook, keptOriginalResult, Map.of());
        } catch (RuntimeException exception) {
            fields.recordFailed(hook, exception);
        }
    }

    private static void observeReadOnlyHook(
            HookExecutionFields fields,
            GpuBackendHook hook,
            GpuBackendTarget backendTarget,
            ContributingStageObserver observer
    ) {
        if (!hook.appliesTo(backendTarget)) {
            fields.recordSkipped(hook, "backend-target-filtered");
            return;
        }
        if (hook.extensionPermission() != GpuExtensionPermission.READ_ONLY) {
            fields.recordSkipped(hook, "non-read-only-permission");
            return;
        }
        try {
            HookObservation observation = observer.observe();
            fields.recordObserved(
                    hook,
                    observation == null || observation.keptOriginalResult(),
                    observation == null ? Map.of() : observation.contributedFields()
            );
        } catch (RuntimeException exception) {
            fields.recordFailed(hook, exception);
        }
    }

    private static void observeReadOnlyArtifactHook(
            HookExecutionFields fields,
            GpuBackendHook hook,
            GpuBackendTarget backendTarget,
            ArtifactFieldContributor contributor
    ) {
        if (!hook.appliesTo(backendTarget)) {
            fields.recordSkipped(hook, "backend-target-filtered");
            return;
        }
        if (hook.extensionPermission() != GpuExtensionPermission.READ_ONLY) {
            fields.recordSkipped(hook, "non-read-only-permission");
            return;
        }
        try {
            fields.recordArtifactContribution(hook, contributor.contribute());
        } catch (RuntimeException exception) {
            fields.recordFailed(hook, exception);
        }
    }

    private static String normalizePrefix(String prefix, String fallback) {
        return prefix == null || prefix.isBlank() ? fallback : prefix.trim();
    }

    @FunctionalInterface
    private interface StageObserver {
        boolean observe();
    }

    @FunctionalInterface
    private interface ContributingStageObserver {
        HookObservation observe();
    }

    @FunctionalInterface
    private interface ArtifactFieldContributor {
        Map<String, String> contribute();
    }

    private record HookObservation(boolean keptOriginalResult, Map<String, String> contributedFields) {
        private HookObservation {
            contributedFields = contributedFields == null ? Map.of() : Map.copyOf(contributedFields);
        }
    }

    private static final class HookExecutionFields {
        private final String prefix;
        private final GpuExtensionPhase phase;
        private final LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        private int hookCount;
        private int appliedCount;
        private int skippedCount;
        private int failedCount;
        private int mutationIgnoredCount;
        private int contributionFieldCount;

        private HookExecutionFields(String prefix, GpuExtensionPhase phase) {
            this.prefix = prefix;
            this.phase = phase;
        }

        private void recordObserved(
                GpuBackendHook hook,
                boolean keptOriginalResult,
                Map<String, String> contributedFields
        ) {
            int index = beginHook(hook, keptOriginalResult ? "applied" : "mutation-ignored");
            appliedCount++;
            if (keptOriginalResult) {
                fields.put(hookPrefix(index) + ".diagnostic", "read-only hook observed stage result");
            } else {
                mutationIgnoredCount++;
                fields.put(
                        hookPrefix(index) + ".diagnostic",
                        "read-only hook returned a replacement result; replacement was ignored"
                );
            }
            recordContributions(index, contributedFields);
        }

        private void recordSkipped(GpuBackendHook hook, String reason) {
            int index = beginHook(hook, "skipped");
            skippedCount++;
            fields.put(hookPrefix(index) + ".skipReason", reason);
            fields.put(hookPrefix(index) + ".diagnostic", skippedDiagnostic(hook, reason));
        }

        private void recordFailed(GpuBackendHook hook, RuntimeException exception) {
            int index = beginHook(hook, "failed");
            failedCount++;
            fields.put(hookPrefix(index) + ".failure.type", exception.getClass().getName());
            fields.put(hookPrefix(index) + ".failure.message", normalize(exception.getMessage()));
        }

        private void recordArtifactContribution(GpuBackendHook hook, Map<String, String> contributedFields) {
            int index = beginHook(hook, "applied");
            appliedCount++;
            recordContributions(index, contributedFields);
        }

        private void recordContributions(int index, Map<String, String> contributedFields) {
            Map<String, String> contributions = contributedFields == null ? Map.of() : contributedFields;
            fields.put(hookPrefix(index) + ".contribution.count", Integer.toString(contributions.size()));
            int contributionIndex = 0;
            for (Map.Entry<String, String> entry : contributions.entrySet()) {
                String contributionPrefix = hookPrefix(index) + ".contribution." + contributionIndex;
                fields.put(contributionPrefix + ".key", normalize(entry.getKey()));
                fields.put(contributionPrefix + ".value", normalize(entry.getValue()));
                contributionIndex++;
            }
            contributionFieldCount += contributions.size();
        }

        private int beginHook(GpuBackendHook hook, String status) {
            int index = hookCount++;
            String hookPrefix = hookPrefix(index);
            fields.put(hookPrefix + ".id", hook.extensionId());
            fields.put(hookPrefix + ".version", hook.extensionVersion());
            fields.put(hookPrefix + ".implementationClass", hook.getClass().getName());
            fields.put(hookPrefix + ".phase", hook.extensionPhase().name());
            fields.put(hookPrefix + ".permission", hook.extensionPermission().name());
            fields.put(hookPrefix + ".failurePolicy", hook.failurePolicy().name());
            fields.put(hookPrefix + ".status", status);
            return index;
        }

        private String hookPrefix(int index) {
            return prefix + ".hook." + index;
        }

        private Map<String, String> toFields() {
            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            result.put(prefix + ".present", "true");
            result.put(prefix + ".phase", phase.name());
            result.put(prefix + ".hook.count", Integer.toString(hookCount));
            result.put(prefix + ".applied.count", Integer.toString(appliedCount));
            result.put(prefix + ".skipped.count", Integer.toString(skippedCount));
            result.put(prefix + ".failed.count", Integer.toString(failedCount));
            result.put(prefix + ".mutationIgnored.count", Integer.toString(mutationIgnoredCount));
            result.put(prefix + ".contributionField.count", Integer.toString(contributionFieldCount));
            result.putAll(fields);
            result.put("runtime.backend.hookExecution.present", "true");
            result.put("runtime.backend.hookExecution." + phase.name().toLowerCase(java.util.Locale.ROOT) + ".hook.count", Integer.toString(hookCount));
            result.put("runtime.backend.hookExecution." + phase.name().toLowerCase(java.util.Locale.ROOT) + ".failed.count", Integer.toString(failedCount));
            return Collections.unmodifiableMap(result);
        }

        private static String normalize(String value) {
            return value == null ? "" : value;
        }

        private static String skippedDiagnostic(GpuBackendHook hook, String reason) {
            if ("backend-target-filtered".equals(reason)) {
                return "hook target filter does not include this backend target";
            }
            if ("non-read-only-permission".equals(reason)) {
                return "hook declares " + hook.extensionPermission()
                        + "; current backend hook execution only runs READ_ONLY hooks"
                        + " and leaves production-affecting authorization for a future gate";
            }
            return reason;
        }
    }

    private record BackendHookContract(
            List<String> familyNames,
            List<GpuExtensionCapability> expectedCapabilities,
            List<GpuExtensionPhase> expectedPhases
    ) {

        private BackendHookContract {
            familyNames = List.copyOf(familyNames);
            expectedCapabilities = List.copyOf(expectedCapabilities);
            expectedPhases = expectedPhases.stream()
                    .distinct()
                    .toList();
        }

        private static BackendHookContract expectedFor(GpuBackendHook hook) {
            ArrayList<String> families = new ArrayList<>();
            ArrayList<GpuExtensionCapability> capabilities = new ArrayList<>();
            ArrayList<GpuExtensionPhase> phases = new ArrayList<>();
            if (hook instanceof GpuBackendPolicyContributor) {
                families.add(GpuBackendPolicyContributor.class.getSimpleName());
                capabilities.add(GpuExtensionCapability.BACKEND_POLICY_CONTRIBUTION);
                phases.add(GpuExtensionPhase.BACKEND_POLICY);
            }
            if (hook instanceof GpuRuntimeBackendScoreContributor) {
                families.add(GpuRuntimeBackendScoreContributor.class.getSimpleName());
                capabilities.add(GpuExtensionCapability.BACKEND_SCORE_CONTRIBUTION);
                phases.add(GpuExtensionPhase.BACKEND_POLICY);
            }
            if (hook instanceof GpuBackendDiscoveryContributor) {
                families.add(GpuBackendDiscoveryContributor.class.getSimpleName());
                capabilities.add(GpuExtensionCapability.BACKEND_DISCOVERY_CONTRIBUTION);
                phases.add(GpuExtensionPhase.BACKEND_DISCOVERY);
            }
            if (hook instanceof GpuBackendLoweringHook) {
                families.add(GpuBackendLoweringHook.class.getSimpleName());
                capabilities.add(GpuExtensionCapability.BACKEND_LOWERING_HOOK);
                phases.add(GpuExtensionPhase.BACKEND_LOWERING);
            }
            if (hook instanceof GpuBackendCompilationHook) {
                families.add(GpuBackendCompilationHook.class.getSimpleName());
                capabilities.add(GpuExtensionCapability.BACKEND_COMPILATION_HOOK);
                phases.add(GpuExtensionPhase.BACKEND_COMPILATION);
            }
            if (hook instanceof GpuBackendInvocationHook) {
                families.add(GpuBackendInvocationHook.class.getSimpleName());
                capabilities.add(GpuExtensionCapability.BACKEND_INVOCATION_HOOK);
                phases.add(GpuExtensionPhase.BACKEND_INVOCATION);
            }
            if (hook instanceof GpuBackendArtifactHook) {
                families.add(GpuBackendArtifactHook.class.getSimpleName());
                capabilities.add(GpuExtensionCapability.BACKEND_ARTIFACT_HOOK);
                phases.add(GpuExtensionPhase.ARTIFACT_EMISSION);
            }
            return new BackendHookContract(families, capabilities, phases);
        }
    }
}
