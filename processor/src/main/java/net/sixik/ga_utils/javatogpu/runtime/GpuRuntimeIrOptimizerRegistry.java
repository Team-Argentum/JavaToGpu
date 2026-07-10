package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionFailurePolicy;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionRegistry;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

public final class GpuRuntimeIrOptimizerRegistry {

    private static final GpuRuntimeIrOptimizerRegistry NO_OP = new GpuRuntimeIrOptimizerRegistry(List.of(), Map.of());

    private final List<GpuRuntimeIrOptimizationPass> passes;
    private final GpuExtensionRegistry extensionRegistry;
    private final Map<String, GpuProductionExtensionAuthorizationDecision> productionAuthorizations;
    private final String optimizerPipelineVersion;

    private GpuRuntimeIrOptimizerRegistry(List<GpuRuntimeIrOptimizationPass> passes) {
        this(passes, Map.of());
    }

    private GpuRuntimeIrOptimizerRegistry(
            List<GpuRuntimeIrOptimizationPass> passes,
            Map<String, GpuProductionExtensionAuthorizationDecision> productionAuthorizations
    ) {
        this.passes = List.copyOf(passes);
        this.extensionRegistry = GpuExtensionRegistry.of(this.passes);
        this.extensionRegistry.requirePipelineContract(
                "runtime IR optimizer pipeline",
                GpuExtensionPhase.RUNTIME_IR_OPTIMIZATION,
                GpuExtensionPermission.PRODUCTION_AFFECTING,
                GpuExtensionCapability.IR_OPTIMIZATION_PROPOSAL
        );
        this.productionAuthorizations = validateProductionAuthorizations(productionAuthorizations);
        this.optimizerPipelineVersion = buildOptimizerPipelineVersion(this.passes);
    }

    public static GpuRuntimeIrOptimizerRegistry noOp() {
        return NO_OP;
    }

    public static GpuRuntimeIrOptimizerRegistry of(List<GpuRuntimeIrOptimizer> optimizers) {
        if (optimizers == null || optimizers.isEmpty()) {
            return noOp();
        }
        return new GpuRuntimeIrOptimizerRegistry(optimizers.stream()
                .map(LegacyOptimizerPassAdapter::new)
                .map(GpuRuntimeIrOptimizationPass.class::cast)
                .toList());
    }

    public static GpuRuntimeIrOptimizerRegistry ofPasses(List<GpuRuntimeIrOptimizationPass> passes) {
        if (passes == null || passes.isEmpty()) {
            return noOp();
        }
        return new GpuRuntimeIrOptimizerRegistry(passes);
    }

    public static GpuRuntimeIrOptimizerRegistry loadFromServiceLoader() {
        java.util.ArrayList<GpuRuntimeIrOptimizationPass> loadedPasses = new java.util.ArrayList<>();
        loadedPasses.add(new GpuRuntimeRegisterPressureAnalysisPass());
        ServiceLoader.load(GpuRuntimeIrOptimizationPass.class, GpuRuntimeIrOptimizationPass.class.getClassLoader())
                .forEach(loadedPasses::add);
        ServiceLoader.load(GpuRuntimeIrOptimizer.class, GpuRuntimeIrOptimizer.class.getClassLoader())
                .forEach(optimizer -> loadedPasses.add(new LegacyOptimizerPassAdapter(optimizer)));
        loadedPasses.sort(Comparator
                .comparing(GpuRuntimeIrOptimizationPass::stage)
                .thenComparingInt(GpuExtension::extensionOrder)
                .thenComparing(GpuExtension::extensionId)
                .thenComparing(GpuExtension::extensionVersion));
        return ofPasses(loadedPasses);
    }

    public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
        return optimizeWithReport(request).artifact();
    }

    public GpuRuntimeIrOptimizationReport optimizeWithReport(GpuRuntimeIrOptimizationRequest request) {
        Optional<IrGpuArtifact> currentArtifact = request.artifact();
        java.util.ArrayList<GpuRuntimeIrOptimizationPassReport> passReports = new java.util.ArrayList<>();
        java.util.ArrayList<GpuExtensionExecutionReport> executionReports = new java.util.ArrayList<>();
        for (GpuRuntimeIrOptimizationPass pass : optimizerPasses()) {
            GpuRuntimeIrOptimizationRequest passRequest = new GpuRuntimeIrOptimizationRequest(
                    request.compileRequest(),
                    currentArtifact
            );
            if (requiresProductionAuthorization(pass, passRequest)) {
                GpuProductionExtensionAuthorizationDecision authorization = productionAuthorizations.get(pass.extensionId());
                net.sixik.ga_utils.javatogpu.extension.GpuExtensionDescriptor descriptor =
                        extensionRegistry.requireDescriptor(pass.extensionId());
                if (authorization == null || !authorization.matches(descriptor, passRequest.compileRequest(), currentArtifact)) {
                    String reason = authorization == null
                            ? "production-affecting extension has no authorization for this runtime compile context"
                            : "production-affecting extension authorization does not match this runtime compile context";
                    passReports.add(GpuRuntimeIrOptimizationPassReport.skipped(
                            pass.passVersion(),
                            identityOf(currentArtifact),
                            reason
                    ).withStage(pass.stage()));
                    executionReports.add(GpuExtensionExecutionReport.skipped(
                            pass,
                            "runtime IR optimization",
                            reason
                    ));
                    continue;
                }
            }
            if (pass.requiresFastMath() && !passRequest.fastMathEnabled()) {
                passReports.add(GpuRuntimeIrOptimizationPassReport.skipped(
                        pass.passVersion(),
                        identityOf(currentArtifact),
                        "fast-math policy disabled; pass requires @GPUOptimize(fastMath = true), policySource="
                                + passRequest.optimizerPolicy().source()
                ).withStage(pass.stage()));
                executionReports.add(GpuExtensionExecutionReport.skipped(
                        pass,
                        "runtime IR optimization",
                        "fast-math policy disabled"
                ));
                continue;
            }
            try {
                GpuRuntimeIrOptimizationReport passReport = pass.run(passRequest);
                executionReports.add(GpuExtensionExecutionReport.succeeded(pass, "runtime IR optimization"));
                passReports.addAll(tagStage(pass.stage(), passReport.passReports()));
                if (passReport.requiresRollback()) {
                    passReports.add(GpuRuntimeIrOptimizationPassReport.rolledBack(
                            pass.passVersion(),
                            identityOf(currentArtifact),
                            identityOf(passReport.artifact()),
                            "registry-rollback-safety",
                            "pass reported rollback/failure; keeping previous IR artifact",
                            List.of("runtime optimizer registry discarded pass artifact")
                    ).withStage(pass.stage()));
                    break;
                }
                currentArtifact = passReport.artifact();
            } catch (RuntimeException exception) {
                GpuExtensionFailurePolicy failurePolicy = failurePolicy(pass, passRequest);
                executionReports.add(GpuExtensionExecutionReport.failed(
                        pass,
                        "runtime IR optimization",
                        failurePolicy,
                        exception
                ));
                if (failurePolicy == GpuExtensionFailurePolicy.CONTINUE) {
                    passReports.add(GpuRuntimeIrOptimizationPassReport.isolatedFailure(
                            pass.passVersion(),
                            identityOf(currentArtifact),
                            exception
                    ).withStage(pass.stage()));
                    continue;
                }
                passReports.add(GpuRuntimeIrOptimizationPassReport.failed(
                        pass.passVersion(),
                        identityOf(currentArtifact),
                        exception
                ).withStage(pass.stage()));
                break;
            }
        }
        return new GpuRuntimeIrOptimizationReport(
                currentArtifact,
                passReports,
                request.strategyDecision(),
                executionReports
        );
    }

    public List<GpuRuntimeIrOptimizationPass> optimizerPasses() {
        return passes;
    }

    public int optimizerCount() {
        return passes.size();
    }

    public String optimizerPipelineVersion() {
        return optimizerPipelineVersion;
    }

    public GpuExtensionRegistry extensionRegistry() {
        return extensionRegistry;
    }

    public Map<String, String> extensionArtifactFields() {
        return extensionRegistry.artifactFields("runtimeOptimizerExtension");
    }

    public GpuRuntimeIrOptimizerRegistry withProductionAuthorizations(
            List<GpuProductionExtensionAuthorizationDecision> authorizations
    ) {
        LinkedHashMap<String, GpuProductionExtensionAuthorizationDecision> indexed = new LinkedHashMap<>();
        if (authorizations != null) {
            for (GpuProductionExtensionAuthorizationDecision authorization : authorizations) {
                GpuProductionExtensionAuthorizationDecision value = java.util.Objects.requireNonNull(
                        authorization,
                        "authorization"
                );
                if (indexed.putIfAbsent(value.extensionId(), value) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate production extension authorization for '" + value.extensionId() + "'"
                    );
                }
            }
        }
        return new GpuRuntimeIrOptimizerRegistry(passes, indexed);
    }

    public GpuProductionExtensionAuthorizationDecision authorizeProductionExtension(
            String extensionId,
            GpuRuntimeCompileRequest request,
            GpuRuntimeIrOptimizationReport optimizationReport,
            GpuRuntimeEquivalenceEvidence runtimeEquivalenceEvidence,
            GpuRuntimeFallbackEvidence fallbackEvidence,
            GpuProductionPromotionDecision promotionDecision,
            boolean rollbackSupported
    ) {
        return GpuProductionExtensionAuthorizationDecision.evaluate(
                extensionRegistry.requireDescriptor(extensionId),
                request,
                optimizationReport,
                runtimeEquivalenceEvidence,
                fallbackEvidence,
                promotionDecision,
                rollbackSupported
        );
    }

    public Map<String, GpuProductionExtensionAuthorizationDecision> productionAuthorizations() {
        return productionAuthorizations;
    }

    static String identityOf(Optional<IrGpuArtifact> artifact) {
        return artifact.map(IrGpuArtifactIdentity::stableIdentity).orElse("irgpu:missing");
    }

    private static List<GpuRuntimeIrOptimizationPassReport> tagStage(
            GpuRuntimeIrOptimizationStage stage,
            List<GpuRuntimeIrOptimizationPassReport> reports
    ) {
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<GpuRuntimeIrOptimizationPassReport> taggedReports = new java.util.ArrayList<>();
        for (GpuRuntimeIrOptimizationPassReport report : reports) {
            taggedReports.add(report.withStage(stage));
        }
        return List.copyOf(taggedReports);
    }

    private static String buildOptimizerPipelineVersion(List<GpuRuntimeIrOptimizationPass> passes) {
        if (passes == null || passes.isEmpty()) {
            return GpuRuntimeIrOptimizer.NO_OP_VERSION;
        }
        StringBuilder builder = new StringBuilder("optimizer-pipeline:v1");
        for (int index = 0; index < passes.size(); index++) {
            GpuRuntimeIrOptimizationPass pass = passes.get(index);
            builder.append('|')
                    .append(index)
                    .append(':')
                    .append(pass.stage())
                    .append(':')
                    .append(pass.passVersion());
        }
        return builder.toString();
    }

    private record LegacyOptimizerPassAdapter(GpuRuntimeIrOptimizer optimizer) implements GpuRuntimeIrOptimizationPass {

        private LegacyOptimizerPassAdapter {
            optimizer = java.util.Objects.requireNonNull(optimizer, "optimizer");
        }

        @Override
        public GpuRuntimeIrOptimizationReport run(GpuRuntimeIrOptimizationRequest request) {
            return optimizer.optimizeWithReport(request);
        }

        @Override
        public String passName() {
            return optimizer.optimizerVersion();
        }

        @Override
        public String passVersion() {
            return optimizer.optimizerVersion();
        }

        @Override
        public boolean requiresFastMath() {
            return optimizer.requiresFastMath();
        }
    }

    private Map<String, GpuProductionExtensionAuthorizationDecision> validateProductionAuthorizations(
            Map<String, GpuProductionExtensionAuthorizationDecision> authorizations
    ) {
        if (authorizations == null || authorizations.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, GpuProductionExtensionAuthorizationDecision> validated = new LinkedHashMap<>();
        for (Map.Entry<String, GpuProductionExtensionAuthorizationDecision> entry : authorizations.entrySet()) {
            GpuProductionExtensionAuthorizationDecision authorization = java.util.Objects.requireNonNull(
                    entry.getValue(),
                    "authorization"
            );
            net.sixik.ga_utils.javatogpu.extension.GpuExtensionDescriptor descriptor =
                    extensionRegistry.requireDescriptor(entry.getKey());
            if (!descriptor.id().equals(authorization.extensionId())
                    || !descriptor.version().equals(authorization.extensionVersion())) {
                throw new IllegalArgumentException(
                        "Production extension authorization does not match registered extension " + descriptor.id()
                );
            }
            validated.put(entry.getKey(), authorization);
        }
        return Map.copyOf(validated);
    }

    private static boolean requiresProductionAuthorization(
            GpuRuntimeIrOptimizationPass pass,
            GpuRuntimeIrOptimizationRequest request
    ) {
        return pass.extensionPermission() == GpuExtensionPermission.PRODUCTION_AFFECTING
                && GpuRuntimeProductionProfiles.isProductionProfile(
                request.compileRequest().options().optimizationProfile()
        );
    }

    private static GpuExtensionFailurePolicy failurePolicy(
            GpuRuntimeIrOptimizationPass pass,
            GpuRuntimeIrOptimizationRequest request
    ) {
        if (pass.extensionPermission() == GpuExtensionPermission.PRODUCTION_AFFECTING
                || GpuRuntimeProductionProfiles.isProductionProfile(
                request.compileRequest().options().optimizationProfile()
        )) {
            return GpuExtensionFailurePolicy.STOP_PIPELINE;
        }
        return GpuExtensionFailurePolicy.CONTINUE;
    }
}
