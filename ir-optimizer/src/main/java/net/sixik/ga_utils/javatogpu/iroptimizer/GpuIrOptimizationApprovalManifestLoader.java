package net.sixik.ga_utils.javatogpu.iroptimizer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Loads review-only optimizer approval manifests from the classpath and validates them against a proposal.
 */
public final class GpuIrOptimizationApprovalManifestLoader {

    private GpuIrOptimizationApprovalManifestLoader() {
    }

    public static Result loadAndValidate(
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request
    ) {
        return loadAndValidate(proposal, request, Thread.currentThread().getContextClassLoader());
    }

    public static Result loadAndValidate(
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request,
            ClassLoader preferredClassLoader
    ) {
        String resourcePath;
        try {
            resourcePath = GpuIrOptimizationApprovalManifest.resourcePath(proposal, request);
        } catch (RuntimeException exception) {
            return Result.blocked("missing", "approval-template-not-applicable", 0);
        }

        List<URL> resources;
        try {
            resources = resources(resourcePath, preferredClassLoader);
        } catch (IOException exception) {
            return Result.blocked(resourcePath, "approval-manifest-load-failed", 0);
        }
        if (resources.isEmpty()) {
            return Result.missing(resourcePath);
        }
        if (resources.size() > 1) {
            return Result.blocked(resourcePath, "approval-manifest-resource-ambiguous", resources.size());
        }

        Properties properties = new Properties();
        try (InputStream inputStream = resources.get(0).openStream()) {
            properties.load(inputStream);
        } catch (IOException exception) {
            return Result.blocked(resourcePath, "approval-manifest-load-failed", resources.size());
        }

        GpuIrOptimizationApprovalManifest.Validation validation =
                GpuIrOptimizationApprovalManifest.validate(proposal, request, properties);
        return Result.fromValidation(resourcePath, resources.size(), validation);
    }

    private static List<URL> resources(String resourcePath, ClassLoader preferredClassLoader) throws IOException {
        LinkedHashSet<ClassLoader> classLoaders = new LinkedHashSet<>();
        if (preferredClassLoader != null) {
            classLoaders.add(preferredClassLoader);
        }
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            classLoaders.add(contextClassLoader);
        }
        ClassLoader ownClassLoader = GpuIrOptimizationApprovalManifestLoader.class.getClassLoader();
        if (ownClassLoader != null) {
            classLoaders.add(ownClassLoader);
        }

        LinkedHashSet<URL> resources = new LinkedHashSet<>();
        for (ClassLoader classLoader : classLoaders) {
            Enumeration<URL> found = classLoader.getResources(resourcePath);
            while (found.hasMoreElements()) {
                resources.add(found.nextElement());
            }
        }
        return List.copyOf(resources);
    }

    public record Result(
            String status,
            boolean required,
            boolean present,
            boolean accepted,
            String resourcePath,
            int resourceCount,
            String firstBlocker,
            Optional<GpuIrOptimizationApprovalManifest.Validation> validation
    ) {
        public Result {
            status = normalize(status, accepted ? "accepted" : "pending-manifest-validation");
            resourcePath = normalize(resourcePath, "missing");
            resourceCount = Math.max(0, resourceCount);
            firstBlocker = normalize(firstBlocker, accepted ? "none" : "approval-manifest-not-loaded");
            validation = validation == null ? Optional.empty() : validation;
        }

        private static Result missing(String resourcePath) {
            return new Result(
                    "pending-manifest-validation",
                    true,
                    false,
                    false,
                    resourcePath,
                    0,
                    "approval-manifest-not-loaded",
                    Optional.empty()
            );
        }

        private static Result blocked(String resourcePath, String firstBlocker, int resourceCount) {
            return new Result(
                    "blocked",
                    true,
                    resourceCount > 0,
                    false,
                    resourcePath,
                    resourceCount,
                    firstBlocker,
                    Optional.empty()
            );
        }

        private static Result fromValidation(
                String resourcePath,
                int resourceCount,
                GpuIrOptimizationApprovalManifest.Validation validation
        ) {
            boolean accepted = validation != null && validation.valid();
            return new Result(
                    accepted ? "accepted" : "blocked",
                    true,
                    true,
                    accepted,
                    resourcePath,
                    resourceCount,
                    validation == null ? "approval-manifest-validation-missing" : validation.firstBlocker(),
                    Optional.ofNullable(validation)
            );
        }

        public Map<String, String> fields() {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("status", status);
            fields.put("required", Boolean.toString(required));
            fields.put("present", Boolean.toString(present));
            fields.put("accepted", Boolean.toString(accepted));
            fields.put("resourcePath", resourcePath);
            fields.put("resource.count", Integer.toString(resourceCount));
            fields.put("firstBlocker", firstBlocker);
            fields.put("manualReviewOnly", "true");
            fields.put("productionMutation", "disabled");
            fields.put("selectedIrReplacement", "disabled");
            validation.ifPresent(value -> {
                fields.put("validation.valid", Boolean.toString(value.valid()));
                fields.put("validation.status", value.status());
                fields.put("validation.blocker.count", Integer.toString(value.blockers().size()));
                for (int index = 0; index < value.blockers().size(); index++) {
                    fields.put("validation.blocker." + index, value.blockers().get(index));
                }
            });
            return Map.copyOf(fields);
        }

        private static String normalize(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
