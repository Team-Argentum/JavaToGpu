package net.sixik.ga_utils.javatogpu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Backend-neutral runtime selection orchestrator.
 *
 * <p>The first concrete production adapter is OpenCL, but this selector deliberately works over generic backend
 * factories, requirements, ownership, and capability reports so future CUDA, Vulkan/SPIR-V, and Metal adapters can use
 * the same explanation surface.</p>
 */
public final class GpuRuntimeBackendSelectionOrchestrator {

    private GpuRuntimeBackendSelectionOrchestrator() {
    }

    /**
     * Selects a backend using an immutable policy and returns candidate-level audit evidence.
     */
    public static GpuRuntimeSelectionResult select(GpuRuntimeBackendPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        return select(policy.requirements(), policy.candidateFactories(), policy.candidateOwnerships());
    }

    /**
     * Selects a borrowed backend from already-created backend candidates.
     */
    public static GpuRuntimeSelectionResult selectBorrowed(
            List<GpuRuntimeRequirement> requirements,
            GpuRuntimeBackend... candidates
    ) {
        Objects.requireNonNull(candidates, "candidates");
        ArrayList<GpuRuntimeBackendFactory> factories = new ArrayList<>(candidates.length);
        ArrayList<GpuRuntimeBackendOwnership> ownerships = new ArrayList<>(candidates.length);
        for (int index = 0; index < candidates.length; index++) {
            GpuRuntimeBackend candidate = Objects.requireNonNull(candidates[index], "candidates[" + index + "]");
            factories.add(() -> candidate);
            ownerships.add(GpuRuntimeBackendOwnership.BORROWED);
        }
        return select(requirements, factories, ownerships);
    }

    /**
     * Selects a backend from factories and records every creation, rejection, closure, and selected candidate.
     */
    public static GpuRuntimeSelectionResult select(
            List<GpuRuntimeRequirement> requirements,
            List<GpuRuntimeBackendFactory> candidateFactories,
            List<GpuRuntimeBackendOwnership> candidateOwnerships
    ) {
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(candidateFactories, "candidateFactories");
        Objects.requireNonNull(candidateOwnerships, "candidateOwnerships");
        if (candidateFactories.size() != candidateOwnerships.size()) {
            throw new IllegalArgumentException("candidate factory and ownership counts must match");
        }
        if (candidateFactories.isEmpty()) {
            return new GpuRuntimeSelectionResult(
                    null,
                    List.of("no backend candidates were provided"),
                    List.of()
            );
        }

        ArrayList<String> failures = new ArrayList<>();
        ArrayList<GpuRuntimeBackendCandidateDecision> decisions = new ArrayList<>();
        for (int index = 0; index < candidateFactories.size(); index++) {
            GpuRuntimeBackendFactory factory = Objects.requireNonNull(
                    candidateFactories.get(index),
                    "candidateFactories[" + index + "]"
            );
            GpuRuntimeBackendOwnership ownership = candidateOwnerships.get(index) == null
                    ? GpuRuntimeBackendOwnership.BORROWED
                    : candidateOwnerships.get(index);
            GpuRuntimeBackend candidate;
            try {
                candidate = factory.create();
            } catch (RuntimeException exception) {
                GpuRuntimeBackendCandidateDecision decision = GpuRuntimeBackendCandidateDecision.creationFailed(
                        index,
                        ownership,
                        exception
                );
                decisions.add(decision);
                failures.add(String.join("; ", decision.diagnostics()));
                continue;
            }

            GpuRuntimeBackendReport report = GpuRuntime.describeBackend(candidate);
            List<String> reasons = GpuRuntimeRequirements.failureReasons(report, requirements);
            if (reasons.isEmpty()) {
                decisions.add(GpuRuntimeBackendCandidateDecision.selected(index, report, ownership));
                return new GpuRuntimeSelectionResult(
                        new GpuRuntimeBackendSelection(candidate, report, ownership),
                        failures,
                        decisions
                );
            }

            boolean closed = false;
            if (ownership == GpuRuntimeBackendOwnership.OWNED) {
                closed = closeCandidateQuietly(candidate);
            }
            GpuRuntimeBackendCandidateDecision decision = GpuRuntimeBackendCandidateDecision.rejected(
                    index,
                    report,
                    ownership,
                    reasons,
                    closed
            );
            decisions.add(decision);
            failures.add(report.backendName() + ": " + String.join("; ", reasons));
        }

        return new GpuRuntimeSelectionResult(null, failures, decisions);
    }

    private static boolean closeCandidateQuietly(GpuRuntimeBackend candidate) {
        if (candidate instanceof AutoCloseable closeable) {
            try {
                closeable.close();
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }
}
