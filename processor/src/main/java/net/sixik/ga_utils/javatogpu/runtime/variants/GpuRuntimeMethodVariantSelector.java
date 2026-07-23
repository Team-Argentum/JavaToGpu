package net.sixik.ga_utils.javatogpu.runtime.variants;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodFallbackVariant;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceCandidateRanking;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDiagnosticContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrArtifactLoader;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodVariantEvaluation;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodVariantRegistration;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodVariantSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodVariantSelectionException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Selects one ABI-compatible method variant and one device through the existing device policy pipeline.
 */
public final class GpuRuntimeMethodVariantSelector {

    private GpuRuntimeMethodVariantSelector() {
    }

    public static GpuRuntimeMethodVariantSelection select(
            GpuKernelDescriptor primaryDescriptor,
            List<GpuKernelDescriptor> fallbackDescriptors,
            ClassLoader artifactClassLoader,
            GpuRuntimeCompileOptions compileOptions,
            List<GpuRuntimeDeviceProfile> deviceProfiles,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry
    ) {
        Objects.requireNonNull(primaryDescriptor, "primaryDescriptor");
        Objects.requireNonNull(devicePolicyRegistry, "devicePolicyRegistry");
        VariantCandidate primaryCandidate = candidate(primaryDescriptor, artifactClassLoader);
        IrGpuMethodFallbackVariant primaryMetadata = primaryCandidate.metadata().orElseThrow(() ->
                new IllegalArgumentException("Primary GPU method does not declare @GPUFallbackVariant metadata")
        );
        String groupId = primaryMetadata.groupId();
        List<GpuRuntimeMethodVariantRegistration> externalRegistrations = GpuRuntimeMethodVariantRegistry
                .load(artifactClassLoader)
                .variants(groupId);
        List<GpuKernelDescriptor> descriptors = orderedDistinct(
                primaryDescriptor,
                fallbackDescriptors,
                externalRegistrations.stream()
                        .map(GpuRuntimeMethodVariantRegistration::descriptor)
                        .toList()
        );
        List<VariantCandidate> candidates = descriptors.stream()
                .map(descriptor -> sameDescriptor(primaryDescriptor, descriptor)
                        ? primaryCandidate
                        : candidate(descriptor, artifactClassLoader))
                .toList();
        validateUniqueVariantIds(groupId, candidates);

        ArrayList<VariantResult> accepted = new ArrayList<>();
        ArrayList<GpuRuntimeMethodVariantEvaluation> evaluations = new ArrayList<>();
        for (VariantCandidate candidate : candidates) {
            ArrayList<String> diagnostics = new ArrayList<>();
            IrGpuMethodFallbackVariant metadata = candidate.metadata().orElse(null);
            if (metadata == null) {
                diagnostics.add("variant IrGpu metadata is missing @GPUFallbackVariant");
                evaluations.add(rejected(groupId, candidate, 0, diagnostics));
                continue;
            }
            if (!groupId.equals(metadata.groupId())) {
                diagnostics.add("variant group " + metadata.groupId() + " does not match primary group " + groupId);
                evaluations.add(rejected(groupId, candidate, metadata.priority(), diagnostics));
                continue;
            }
            String abiMismatch = abiMismatch(primaryDescriptor, candidate.descriptor());
            if (!abiMismatch.isBlank()) {
                diagnostics.add(abiMismatch);
                evaluations.add(rejected(groupId, candidate, metadata.priority(), diagnostics));
                continue;
            }

            GpuRuntimeDeviceSelection selection = devicePolicyRegistry.select(new GpuRuntimeDevicePolicyContext(
                    candidate.descriptor(),
                    compileOptions,
                    deviceProfiles,
                    candidate.artifact()
            ));
            Optional<GpuRuntimeDeviceProfile> selectedDevice = selection.selectedDevice();
            if (selectedDevice.isEmpty()) {
                diagnostics.add("device selection rejected variant: " + selection.firstBlocker());
                diagnostics.addAll(selection.diagnostics());
                evaluations.add(rejected(groupId, candidate, metadata.priority(), diagnostics));
                continue;
            }
            String selectedDeviceKey = GpuRuntimeDevicePolicyContext.deviceKey(selectedDevice.orElseThrow());
            int selectedDeviceScore = selection.rankedCandidates().stream()
                    .filter(ranking -> ranking.deviceKey().equals(selectedDeviceKey))
                    .mapToInt(GpuRuntimeDeviceCandidateRanking::totalScore)
                    .findFirst()
                    .orElse(Integer.MIN_VALUE);
            diagnostics.add("variant accepted on " + selectedDeviceKey);
            GpuRuntimeMethodVariantEvaluation evaluation = new GpuRuntimeMethodVariantEvaluation(
                    groupId,
                    metadata.variantId(),
                    metadata.priority(),
                    candidate.descriptor(),
                    true,
                    selectedDeviceScore,
                    selectedDeviceKey,
                    diagnostics
            );
            evaluations.add(evaluation);
            accepted.add(new VariantResult(candidate, metadata, selection, evaluation));
        }

        if (accepted.isEmpty()) {
            throw new GpuRuntimeMethodVariantSelectionException(
                    "No compatible GPU method variant found for fallback group " + groupId + ": "
                            + evaluations.stream()
                            .map(evaluation -> evaluation.variantId() + "=" + String.join("; ", evaluation.diagnostics()))
                            .reduce((left, right) -> left + " | " + right)
                            .orElse("no variants were provided"),
                    evaluations,
                    GpuRuntimeDiagnosticContext.from(
                            primaryDescriptor,
                            primaryCandidate.artifact(),
                            Optional.empty(),
                            compileOptions
                    )
            );
        }

        accepted.sort(Comparator
                .comparingInt((VariantResult result) -> result.evaluation().selectedDeviceScore()).reversed()
                .thenComparing(Comparator.comparingInt((VariantResult result) -> result.metadata().priority()).reversed())
                .thenComparing(result -> result.metadata().variantId())
                .thenComparing(result -> result.candidate().descriptor().kernelResource())
                .thenComparing(result -> result.candidate().descriptor().kernelName()));
        VariantResult selected = accepted.get(0);
        ArrayList<String> diagnostics = new ArrayList<>();
        diagnostics.add("selected fallback variant " + selected.metadata().variantId()
                + " from group " + groupId
                + " on " + selected.evaluation().selectedDeviceKey());
        for (GpuRuntimeMethodVariantEvaluation evaluation : evaluations) {
            if (!evaluation.accepted()) {
                diagnostics.add("skipped variant " + evaluation.variantId() + ": "
                        + String.join("; ", evaluation.diagnostics()));
            }
        }
        GpuRuntimeDeviceSelection enrichedDeviceSelection = selected.selection().withAdditionalDiagnostics(diagnostics);
        return new GpuRuntimeMethodVariantSelection(
                groupId,
                selected.metadata().variantId(),
                selected.candidate().descriptor(),
                selected.candidate().artifact(),
                enrichedDeviceSelection,
                evaluations,
                diagnostics
        );
    }

    private static List<GpuKernelDescriptor> orderedDistinct(
            GpuKernelDescriptor primaryDescriptor,
            List<GpuKernelDescriptor> fallbackDescriptors,
            List<GpuKernelDescriptor> externalDescriptors
    ) {
        ArrayList<GpuKernelDescriptor> ordered = new ArrayList<>();
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        addDistinct(ordered, identities, primaryDescriptor);
        if (fallbackDescriptors != null) {
            for (GpuKernelDescriptor descriptor : fallbackDescriptors) {
                if (descriptor != null) {
                    addDistinct(ordered, identities, descriptor);
                }
            }
        }
        if (externalDescriptors != null) {
            for (GpuKernelDescriptor descriptor : externalDescriptors) {
                if (descriptor != null) {
                    addDistinct(ordered, identities, descriptor);
                }
            }
        }
        return List.copyOf(ordered);
    }

    private static void validateUniqueVariantIds(String groupId, List<VariantCandidate> candidates) {
        java.util.LinkedHashMap<String, GpuKernelDescriptor> byVariantId = new java.util.LinkedHashMap<>();
        for (VariantCandidate candidate : candidates) {
            IrGpuMethodFallbackVariant metadata = candidate.metadata().orElse(null);
            if (metadata == null || !groupId.equals(metadata.groupId())) {
                continue;
            }
            GpuKernelDescriptor previous = byVariantId.putIfAbsent(metadata.variantId(), candidate.descriptor());
            if (previous != null && !sameDescriptor(previous, candidate.descriptor())) {
                throw new IllegalArgumentException(
                        "Duplicate runtime method variant '" + metadata.variantId()
                                + "' in fallback group '" + groupId + "'"
                );
            }
        }
    }

    private static void addDistinct(
            List<GpuKernelDescriptor> ordered,
            LinkedHashSet<String> identities,
            GpuKernelDescriptor descriptor
    ) {
        String identity = descriptor.kernelName() + '|' + descriptor.kernelResource() + '|' + descriptor.irGpuResource();
        if (identities.add(identity)) {
            ordered.add(descriptor);
        }
    }

    private static boolean sameDescriptor(GpuKernelDescriptor left, GpuKernelDescriptor right) {
        return left.kernelName().equals(right.kernelName())
                && left.kernelResource().equals(right.kernelResource())
                && left.irGpuResource().equals(right.irGpuResource())
                && left.parameterDescriptors().equals(right.parameterDescriptors());
    }

    private static VariantCandidate candidate(GpuKernelDescriptor descriptor, ClassLoader artifactClassLoader) {
        Optional<IrGpuArtifact> artifact = GpuRuntimeIrArtifactLoader.load(descriptor, artifactClassLoader);
        GpuKernelDescriptor hydratedDescriptor = descriptor.kernelSource() == null || descriptor.kernelSource().isBlank()
                ? withKernelSource(descriptor, loadKernelSource(descriptor.kernelResource(), artifactClassLoader))
                : descriptor;
        return new VariantCandidate(
                hydratedDescriptor,
                artifact,
                artifact.flatMap(IrGpuArtifact::entryFallbackVariant)
        );
    }

    private static GpuKernelDescriptor withKernelSource(GpuKernelDescriptor descriptor, String source) {
        return new GpuKernelDescriptor(
                descriptor.kernelName(),
                descriptor.kernelResource(),
                source,
                descriptor.irGpuResource(),
                descriptor.parameterDescriptors()
        );
    }

    private static String loadKernelSource(String resourcePath, ClassLoader preferredClassLoader) {
        return tryLoadKernelSource(resourcePath, preferredClassLoader)
                .or(() -> tryLoadKernelSource(resourcePath, Thread.currentThread().getContextClassLoader()))
                .or(() -> tryLoadKernelSource(resourcePath, GpuRuntimeMethodVariantSelector.class.getClassLoader()))
                .orElseThrow(() -> new IllegalStateException("Failed to load fallback kernel source resource: " + resourcePath));
    }

    private static Optional<String> tryLoadKernelSource(String resourcePath, ClassLoader classLoader) {
        if (resourcePath == null || resourcePath.isBlank() || classLoader == null) {
            return Optional.empty();
        }
        try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return Optional.empty();
            }
            return Optional.of(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load fallback kernel source resource: " + resourcePath, exception);
        }
    }

    private static String abiMismatch(GpuKernelDescriptor primary, GpuKernelDescriptor candidate) {
        if (primary.parameterDescriptors().size() != candidate.parameterDescriptors().size()) {
            return "variant parameter count does not match primary ABI";
        }
        for (int index = 0; index < primary.parameterDescriptors().size(); index++) {
            GpuKernelParameterDescriptor expected = primary.parameterDescriptors().get(index);
            GpuKernelParameterDescriptor actual = candidate.parameterDescriptors().get(index);
            if (!expected.javaType().equals(actual.javaType()) || expected.access() != actual.access()) {
                return "variant parameter " + index + " does not match primary ABI: expected "
                        + expected.javaType() + '/' + expected.access() + " but got "
                        + actual.javaType() + '/' + actual.access();
            }
        }
        return "";
    }

    private static GpuRuntimeMethodVariantEvaluation rejected(
            String groupId,
            VariantCandidate candidate,
            int priority,
            List<String> diagnostics
    ) {
        return new GpuRuntimeMethodVariantEvaluation(
                groupId,
                candidate.metadata().map(IrGpuMethodFallbackVariant::variantId).orElse(candidate.descriptor().kernelName()),
                priority,
                candidate.descriptor(),
                false,
                Integer.MIN_VALUE,
                "none",
                diagnostics
        );
    }

    private record VariantCandidate(
            GpuKernelDescriptor descriptor,
            Optional<IrGpuArtifact> artifact,
            Optional<IrGpuMethodFallbackVariant> metadata
    ) {
    }

    private record VariantResult(
            VariantCandidate candidate,
            IrGpuMethodFallbackVariant metadata,
            GpuRuntimeDeviceSelection selection,
            GpuRuntimeMethodVariantEvaluation evaluation
    ) {
    }
}
