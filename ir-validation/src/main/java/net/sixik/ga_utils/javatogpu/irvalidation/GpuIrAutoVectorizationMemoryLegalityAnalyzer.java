package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Reusable read-only memory legality checks shared by auto-vectorization planning code.
 */
public final class GpuIrAutoVectorizationMemoryLegalityAnalyzer {
    public GpuIrAutoVectorizationMemoryLegalityReport analyze(
            String location,
            Set<String> targetArrays,
            Set<String> sourceArrays,
            Function<String, Optional<ParsedGpuParameter>> parameterLookup
    ) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
        Objects.requireNonNull(targetArrays, "targetArrays");
        Objects.requireNonNull(sourceArrays, "sourceArrays");
        Objects.requireNonNull(parameterLookup, "parameterLookup");
        return new GpuIrAutoVectorizationMemoryLegalityReport(
                location,
                List.copyOf(targetArrays),
                List.copyOf(sourceArrays),
                aliasWarnings(targetArrays, sourceArrays),
                addressSpaceGuards(location, targetArrays, sourceArrays, parameterLookup)
        );
    }

    public List<String> aliasWarnings(Set<String> targetArrays, Set<String> sourceArrays) {
        Objects.requireNonNull(targetArrays, "targetArrays");
        Objects.requireNonNull(sourceArrays, "sourceArrays");
        List<String> warnings = new ArrayList<>();
        for (String targetArray : targetArrays) {
            if (sourceArrays.contains(targetArray)) {
                warnings.add("target array `" + targetArray + "` is also read in the loop body");
            }
        }
        return List.copyOf(warnings);
    }

    public List<GpuIrAutoVectorizationRewriteGuardDiagnostic> addressSpaceGuards(
            String location,
            Set<String> targetArrays,
            Set<String> sourceArrays,
            Function<String, Optional<ParsedGpuParameter>> parameterLookup
    ) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
        Objects.requireNonNull(targetArrays, "targetArrays");
        Objects.requireNonNull(sourceArrays, "sourceArrays");
        Objects.requireNonNull(parameterLookup, "parameterLookup");
        List<GpuIrAutoVectorizationRewriteGuardDiagnostic> diagnostics = new ArrayList<>();
        for (String targetArray : targetArrays) {
            parameterLookup.apply(targetArray).ifPresent(parameter -> addTargetGuard(diagnostics, location, targetArray, parameter));
        }
        for (String sourceArray : sourceArrays) {
            parameterLookup.apply(sourceArray).ifPresent(parameter -> addSourceGuard(diagnostics, location, sourceArray, parameter));
        }
        return List.copyOf(diagnostics);
    }

    private void addTargetGuard(
            List<GpuIrAutoVectorizationRewriteGuardDiagnostic> diagnostics,
            String location,
            String targetArray,
            ParsedGpuParameter parameter
    ) {
        if (parameter.addressSpace() == GpuAddressSpace.CONSTANT || parameter.constant()) {
            diagnostics.add(rewriteGuard(
                    location,
                    "target array `" + targetArray + "` uses read-only memory address space before vector rewrite safety is proven"
            ));
        } else if (parameter.addressSpace() == GpuAddressSpace.LOCAL) {
            diagnostics.add(rewriteGuard(
                    location,
                    "target array `" + targetArray + "` uses local memory address space before vector rewrite safety is proven"
            ));
        }
    }

    private void addSourceGuard(
            List<GpuIrAutoVectorizationRewriteGuardDiagnostic> diagnostics,
            String location,
            String sourceArray,
            ParsedGpuParameter parameter
    ) {
        if (parameter.addressSpace() == GpuAddressSpace.CONSTANT || parameter.constant()) {
            diagnostics.add(rewriteGuard(
                    location,
                    "source array `" + sourceArray + "` uses constant memory address space before vector rewrite policy is proven"
            ));
        } else if (parameter.addressSpace() == GpuAddressSpace.LOCAL) {
            diagnostics.add(rewriteGuard(
                    location,
                    "source array `" + sourceArray + "` uses local memory address space before vector rewrite policy is proven"
            ));
        }
    }

    private GpuIrAutoVectorizationRewriteGuardDiagnostic rewriteGuard(String location, String message) {
        return new GpuIrAutoVectorizationRewriteGuardDiagnostic(
                GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE,
                location,
                message
        );
    }
}
