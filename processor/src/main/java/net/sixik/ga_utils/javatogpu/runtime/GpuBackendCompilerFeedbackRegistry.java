package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionFailurePolicy;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Executes compiler feedback providers in deterministic order and isolates parser failures.
 */
public final class GpuBackendCompilerFeedbackRegistry {

    private final List<GpuBackendCompilerFeedbackProvider> providers;
    private final GpuExtensionRegistry extensionRegistry;

    private GpuBackendCompilerFeedbackRegistry(List<GpuBackendCompilerFeedbackProvider> providers) {
        ArrayList<GpuBackendCompilerFeedbackProvider> sorted = new ArrayList<>(providers);
        sorted.sort(Comparator
                .comparingInt(GpuBackendCompilerFeedbackProvider::extensionOrder)
                .thenComparing(GpuBackendCompilerFeedbackProvider::extensionId)
                .thenComparing(GpuBackendCompilerFeedbackProvider::extensionVersion));
        this.providers = List.copyOf(sorted);
        this.extensionRegistry = GpuExtensionRegistry.of(this.providers);
        this.extensionRegistry.requirePipelineContract(
                "backend compiler feedback pipeline",
                GpuExtensionPhase.BACKEND_COMPILER_FEEDBACK,
                GpuExtensionPermission.READ_ONLY,
                GpuExtensionCapability.COMPILER_FEEDBACK
        );
    }

    public static GpuBackendCompilerFeedbackRegistry of(List<GpuBackendCompilerFeedbackProvider> providers) {
        return new GpuBackendCompilerFeedbackRegistry(providers == null ? List.of() : providers);
    }

    public static GpuBackendCompilerFeedbackRegistry loadWithBuiltIns() {
        ArrayList<GpuBackendCompilerFeedbackProvider> loaded = new ArrayList<>();
        ServiceLoader.load(
                GpuBackendCompilerFeedbackProvider.class,
                GpuBackendCompilerFeedbackProvider.class.getClassLoader()
        ).forEach(loaded::add);
        loaded.add(new GpuGenericCompilerFeedbackProvider());
        return of(loaded);
    }

    public GpuBackendCompilerFeedbackReport inspect(GpuRuntimeCompileArtifactSnapshot snapshot) {
        return inspect(GpuBackendCompilerFeedbackRequest.from(snapshot));
    }

    public GpuBackendCompilerFeedbackReport inspect(GpuBackendCompilerFeedbackRequest request) {
        Objects.requireNonNull(request, "request");
        ArrayList<GpuBackendCompilerFeedback> feedback = new ArrayList<>();
        ArrayList<GpuExtensionExecutionReport> executions = new ArrayList<>();
        for (GpuBackendCompilerFeedbackProvider provider : providers) {
            if (request.compileLog().isBlank()) {
                executions.add(GpuExtensionExecutionReport.skipped(
                        provider,
                        "backend compiler feedback inspection",
                        "compile log is empty"
                ));
                continue;
            }
            try {
                Optional<GpuBackendCompilerFeedback> result = Objects.requireNonNull(
                        provider.inspect(request),
                        "compiler feedback provider result"
                );
                if (result.isEmpty()) {
                    executions.add(GpuExtensionExecutionReport.skipped(
                            provider,
                            "backend compiler feedback inspection",
                            "provider recognized no compiler resource metrics"
                    ));
                    continue;
                }
                GpuBackendCompilerFeedback value = result.orElseThrow();
                validateFeedbackIdentity(provider, value);
                feedback.add(value);
                executions.add(GpuExtensionExecutionReport.succeeded(
                        provider,
                        "backend compiler feedback inspection"
                ));
            } catch (RuntimeException exception) {
                executions.add(GpuExtensionExecutionReport.failed(
                        provider,
                        "backend compiler feedback inspection",
                        GpuExtensionFailurePolicy.CONTINUE,
                        exception
                ));
            }
        }
        return new GpuBackendCompilerFeedbackReport(request, feedback, executions);
    }

    public List<GpuBackendCompilerFeedbackProvider> providers() {
        return providers;
    }

    public GpuExtensionRegistry extensionRegistry() {
        return extensionRegistry;
    }

    private static void validateFeedbackIdentity(
            GpuBackendCompilerFeedbackProvider provider,
            GpuBackendCompilerFeedback feedback
    ) {
        if (!provider.extensionId().equals(feedback.providerId())
                || !provider.extensionVersion().equals(feedback.providerVersion())) {
            throw new IllegalArgumentException(
                    "Compiler feedback identity does not match registered provider " + provider.extensionId()
            );
        }
    }
}
