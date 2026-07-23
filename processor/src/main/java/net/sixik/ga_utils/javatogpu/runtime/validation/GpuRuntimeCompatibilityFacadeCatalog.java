package net.sixik.ga_utils.javatogpu.runtime.validation;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilerFeedbackScoreContributor;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilerFeedbackRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourcePromotionBlockerClassifier;
import net.sixik.ga_utils.javatogpu.runtime.GpuLauncherNaming;
import net.sixik.ga_utils.javatogpu.runtime.GpuProductionPromotionExplainabilityFormatter;
import net.sixik.ga_utils.javatogpu.runtime.GpuPromotionArtifactRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendSelectionOrchestrator;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeClampPeepholeRule;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCommonSubexpressionReviewPass;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequestFactory;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscovery;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDotPeepholeRule;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInferredWorkloadHintBackendScoreContributor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrPeepholePass;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrPeepholeRuleRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrPeepholeTypedRewriteVisitor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrTypedNodeGraph;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventBus;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleFields;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleFileJournalListener;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleLoggingService;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLogBus;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMadFmaPeepholeRule;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodVariantRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodVariantSelector;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMixPeepholeRule;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeNativeMemoryServiceRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeRegisterPressureAnalysisPass;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeRegisterPressureAnalyzer;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeStepPeepholeRule;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeSystemStreamLogService;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeWorkloadHintBackendScoreContributor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeWorkloadHintInference;
import net.sixik.ga_utils.javatogpu.runtime.launch.GpuLauncherNamingSupport;
import net.sixik.ga_utils.javatogpu.runtime.launch.GpuRuntimeCompileRequestSupport;
import net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeBackendSelectionSupport;
import net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeDeviceDiscoverySupport;
import net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeSelection;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Catalog of root-runtime compatibility facades that already have a domain-package home.
 *
 * <p>The entries here are migration/navigation metadata only. They do not change runtime behavior. The goal is to make
 * future refactors explicit: root classes can stay source-compatible while new code imports the domain package listed in
 * the catalog.</p>
 */
@SuppressWarnings("deprecation")
public final class GpuRuntimeCompatibilityFacadeCatalog {

    private static final List<CompatibilityFacade> FACADES = buildFacades();
    private static final Map<String, CompatibilityFacade> BY_ROOT_CLASS = byRootClass(FACADES);

    private GpuRuntimeCompatibilityFacadeCatalog() {
    }

    /**
     * Returns all tracked root-runtime compatibility facades.
     */
    public static List<CompatibilityFacade> all() {
        return FACADES;
    }

    /**
     * Finds a tracked root-runtime compatibility facade by fully qualified root class name.
     */
    public static Optional<CompatibilityFacade> findByRootClass(String rootClassName) {
        if (rootClassName == null || rootClassName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ROOT_CLASS.get(rootClassName.trim()));
    }

    /**
     * Renders a compact Markdown table for docs and diagnostics.
     */
    public static String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("| Root facade | Prefer for new code | Domain | Audience | Purpose |")
                .append(System.lineSeparator());
        builder.append("| --- | --- | --- | --- | --- |").append(System.lineSeparator());
        for (CompatibilityFacade facade : FACADES) {
            builder.append("| `")
                    .append(facade.rootClassName())
                    .append("` | `")
                    .append(facade.preferredClassName())
                    .append("` | `")
                    .append(facade.domainPackage())
                    .append("` | `")
                    .append(facade.audience().name())
                    .append("` | ")
                    .append(facade.purpose())
                    .append(" |")
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static List<CompatibilityFacade> buildFacades() {
        return List.of(
                facade(
                        GpuRuntimeDeviceDiscovery.class,
                        GpuRuntimeSelection.class,
                        GpuRuntimeDeviceDiscoverySupport.class,
                        "runtime.selection",
                        GpuRuntimePackageTaxonomy.Audience.ADVANCED_RUNTIME,
                        "Backend/device discovery snapshots."
                ),
                facade(
                        GpuRuntimeBackendSelectionOrchestrator.class,
                        GpuRuntimeSelection.class,
                        GpuRuntimeBackendSelectionSupport.class,
                        "runtime.selection",
                        GpuRuntimePackageTaxonomy.Audience.ADVANCED_RUNTIME,
                        "Backend selection and backend/device preflight."
                ),
                facade(
                        GpuRuntimeWorkloadHintInference.class,
                        net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeWorkloadHintInference.class,
                        net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeWorkloadHintInference.class,
                        "runtime.selection",
                        GpuRuntimePackageTaxonomy.Audience.ADVANCED_RUNTIME,
                        "Read-only workload-hint inference."
                ),
                facade(
                        GpuRuntimeWorkloadHintBackendScoreContributor.class,
                        net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeWorkloadHintBackendScoreContributor.class,
                        net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeWorkloadHintBackendScoreContributor.class,
                        "runtime.selection",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Caller-provided workload-hint backend scoring."
                ),
                facade(
                        GpuRuntimeInferredWorkloadHintBackendScoreContributor.class,
                        net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeInferredWorkloadHintBackendScoreContributor.class,
                        net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeInferredWorkloadHintBackendScoreContributor.class,
                        "runtime.selection",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Inferred workload-hint backend scoring."
                ),
                facade(
                        GpuBackendCompilerFeedbackScoreContributor.class,
                        net.sixik.ga_utils.javatogpu.runtime.selection.GpuBackendCompilerFeedbackScoreContributor.class,
                        net.sixik.ga_utils.javatogpu.runtime.selection.GpuBackendCompilerFeedbackScoreContributor.class,
                        "runtime.selection",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Compiler-feedback backend scoring."
                ),
                facade(
                        GpuLauncherNaming.class,
                        GpuLauncherNamingSupport.class,
                        GpuLauncherNamingSupport.class,
                        "runtime.launch",
                        GpuRuntimePackageTaxonomy.Audience.ADVANCED_RUNTIME,
                        "Generated-launcher naming rules."
                ),
                facade(
                        GpuRuntimeCompileRequestFactory.class,
                        GpuRuntimeCompileRequestSupport.class,
                        GpuRuntimeCompileRequestSupport.class,
                        "runtime.launch",
                        GpuRuntimePackageTaxonomy.Audience.ADVANCED_RUNTIME,
                        "Runtime compile-request construction."
                ),
                facade(
                        GpuBackendCompilerFeedbackRegistry.class,
                        net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuBackendCompilerFeedbackRegistry.class,
                        net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuBackendCompilerFeedbackRegistry.class,
                        "runtime.diagnostics",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Backend compiler-feedback inspection."
                ),
                facade(
                        GpuBackendSourcePromotionBlockerClassifier.class,
                        net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuBackendSourcePromotionBlockerClassifier.class,
                        net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuBackendSourcePromotionBlockerClassifier.class,
                        "runtime.diagnostics",
                        GpuRuntimePackageTaxonomy.Audience.ADVANCED_RUNTIME,
                        "Backend-source promotion blocker classification."
                ),
                facade(
                        GpuProductionPromotionExplainabilityFormatter.class,
                        net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuProductionPromotionExplainabilityFormatter.class,
                        net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuProductionPromotionExplainabilityFormatter.class,
                        "runtime.diagnostics",
                        GpuRuntimePackageTaxonomy.Audience.ADVANCED_RUNTIME,
                        "Production-promotion explainability formatting."
                ),
                facade(
                        GpuPromotionArtifactRegistry.class,
                        net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry.class,
                        net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry.class,
                        "runtime.diagnostics",
                        GpuRuntimePackageTaxonomy.Audience.ADVANCED_RUNTIME,
                        "Promotion artifact filename registry."
                ),
                facade(
                        GpuRuntimeCompileArtifactDumper.class,
                        net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCompileArtifactDumper.class,
                        net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCompileArtifactDumper.class,
                        "runtime.diagnostics",
                        GpuRuntimePackageTaxonomy.Audience.ADVANCED_RUNTIME,
                        "Runtime compile artifact dumping."
                ),
                facade(
                        GpuRuntimeIrPeepholePass.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholePass.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholePass.class,
                        "runtime.optimization",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Built-in typed-IR peephole optimization pass."
                ),
                facade(
                        GpuRuntimeIrPeepholeRuleRegistry.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholeRuleRegistry.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholeRuleRegistry.class,
                        "runtime.optimization",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Typed-IR peephole rule discovery and analysis."
                ),
                facade(
                        GpuRuntimeIrPeepholeTypedRewriteVisitor.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholeTypedRewriteVisitor.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholeTypedRewriteVisitor.class,
                        "runtime.optimization",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Typed peephole rewrite visitor preflight."
                ),
                facade(
                        GpuRuntimeIrTypedNodeGraph.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrTypedNodeGraphSupport.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrTypedNodeGraphSupport.class,
                        "runtime.optimization",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Typed-node graph helper used by peephole optimization."
                ),
                facade(
                        GpuRuntimeCommonSubexpressionReviewPass.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeCommonSubexpressionReviewPass.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeCommonSubexpressionReviewPass.class,
                        "runtime.optimization",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Review-only common-subexpression optimizer-family lane."
                ),
                facade(
                        GpuRuntimeRegisterPressureAnalysisPass.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeRegisterPressureAnalysisPass.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeRegisterPressureAnalysisPass.class,
                        "runtime.optimization",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Built-in register-pressure analysis pass."
                ),
                facade(
                        GpuRuntimeRegisterPressureAnalyzer.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeRegisterPressureAnalyzer.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeRegisterPressureAnalyzer.class,
                        "runtime.optimization",
                        GpuRuntimePackageTaxonomy.Audience.ADVANCED_RUNTIME,
                        "Advisory typed-IR register-pressure analysis."
                ),
                facade(
                        GpuRuntimeClampPeepholeRule.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeClampPeepholeRule.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeClampPeepholeRule.class,
                        "runtime.optimization",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Built-in clamp peephole rule."
                ),
                facade(
                        GpuRuntimeDotPeepholeRule.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeDotPeepholeRule.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeDotPeepholeRule.class,
                        "runtime.optimization",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Built-in dot peephole rule."
                ),
                facade(
                        GpuRuntimeMadFmaPeepholeRule.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeMadFmaPeepholeRule.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeMadFmaPeepholeRule.class,
                        "runtime.optimization",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Built-in mad/fma peephole rule."
                ),
                facade(
                        GpuRuntimeMixPeepholeRule.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeMixPeepholeRule.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeMixPeepholeRule.class,
                        "runtime.optimization",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Built-in mix peephole rule."
                ),
                facade(
                        GpuRuntimeStepPeepholeRule.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeStepPeepholeRule.class,
                        net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeStepPeepholeRule.class,
                        "runtime.optimization",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Built-in step peephole rule."
                ),
                facade(
                        GpuRuntimeNativeMemoryServiceRegistry.class,
                        net.sixik.ga_utils.javatogpu.runtime.memory.GpuRuntimeNativeMemoryServiceRegistry.class,
                        net.sixik.ga_utils.javatogpu.runtime.memory.GpuRuntimeNativeMemoryServiceRegistry.class,
                        "runtime.memory",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Native host-memory service discovery."
                ),
                facade(
                        GpuRuntimeLifecycleEventBus.class,
                        net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleEventBus.class,
                        net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleEventBus.class,
                        "runtime.observability",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Runtime lifecycle event dispatch."
                ),
                facade(
                        GpuRuntimeLifecycleFields.class,
                        net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleFields.class,
                        net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleFields.class,
                        "runtime.observability",
                        GpuRuntimePackageTaxonomy.Audience.ADVANCED_RUNTIME,
                        "Runtime lifecycle field vocabulary helpers."
                ),
                facade(
                        GpuRuntimeLifecycleFileJournalListener.class,
                        net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleFileJournalListener.class,
                        net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleFileJournalListener.class,
                        "runtime.observability",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Built-in file-backed lifecycle journal service."
                ),
                facade(
                        GpuRuntimeLifecycleLoggingService.class,
                        net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleLoggingService.class,
                        net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLifecycleLoggingService.class,
                        "runtime.observability",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Built-in lifecycle-to-log bridge."
                ),
                facade(
                        GpuRuntimeLogBus.class,
                        net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLogBus.class,
                        net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeLogBus.class,
                        "runtime.observability",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Runtime log dispatch."
                ),
                facade(
                        GpuRuntimeSystemStreamLogService.class,
                        net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeSystemStreamLogService.class,
                        net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeSystemStreamLogService.class,
                        "runtime.observability",
                        GpuRuntimePackageTaxonomy.Audience.EXTENSION_SPI,
                        "Built-in system stream log sink."
                ),
                facade(
                        GpuRuntimeMethodVariantRegistry.class,
                        net.sixik.ga_utils.javatogpu.runtime.variants.GpuRuntimeMethodVariantRegistry.class,
                        net.sixik.ga_utils.javatogpu.runtime.variants.GpuRuntimeMethodVariantRegistry.class,
                        "runtime.variants",
                        GpuRuntimePackageTaxonomy.Audience.ADVANCED_RUNTIME,
                        "Runtime method-variant discovery."
                ),
                facade(
                        GpuRuntimeMethodVariantSelector.class,
                        net.sixik.ga_utils.javatogpu.runtime.variants.GpuRuntimeMethodVariantSelector.class,
                        net.sixik.ga_utils.javatogpu.runtime.variants.GpuRuntimeMethodVariantSelector.class,
                        "runtime.variants",
                        GpuRuntimePackageTaxonomy.Audience.ADVANCED_RUNTIME,
                        "Runtime method-variant selection."
                )
        ).stream()
                .sorted(Comparator.comparing(CompatibilityFacade::rootClassName))
                .toList();
    }

    private static CompatibilityFacade facade(
            Class<?> rootClass,
            Class<?> preferredClass,
            Class<?> implementationClass,
            String domainPackage,
            GpuRuntimePackageTaxonomy.Audience audience,
            String purpose
    ) {
        return new CompatibilityFacade(
                rootClass.getName(),
                preferredClass.getName(),
                implementationClass.getName(),
                domainPackage,
                audience,
                purpose
        );
    }

    private static Map<String, CompatibilityFacade> byRootClass(List<CompatibilityFacade> facades) {
        LinkedHashMap<String, CompatibilityFacade> byClass = new LinkedHashMap<>();
        for (CompatibilityFacade facade : facades) {
            CompatibilityFacade previous = byClass.putIfAbsent(facade.rootClassName(), facade);
            if (previous != null) {
                throw new IllegalStateException("Duplicate compatibility facade: " + facade.rootClassName());
            }
        }
        return Map.copyOf(byClass);
    }

    /**
     * One root-runtime compatibility facade and its preferred domain-package target.
     */
    public record CompatibilityFacade(
            String rootClassName,
            String preferredClassName,
            String implementationClassName,
            String domainPackage,
            GpuRuntimePackageTaxonomy.Audience audience,
            String purpose
    ) {
        public CompatibilityFacade {
            rootClassName = requireText(rootClassName, "rootClassName");
            preferredClassName = requireText(preferredClassName, "preferredClassName");
            implementationClassName = requireText(implementationClassName, "implementationClassName");
            domainPackage = requireText(domainPackage, "domainPackage");
            audience = Objects.requireNonNull(audience, "audience");
            purpose = requireText(purpose, "purpose");
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value.trim();
        }
    }
}
