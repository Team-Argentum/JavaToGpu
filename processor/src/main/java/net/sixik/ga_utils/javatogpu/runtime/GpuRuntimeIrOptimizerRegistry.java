package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

public final class GpuRuntimeIrOptimizerRegistry {

    private static final GpuRuntimeIrOptimizerRegistry NO_OP = new GpuRuntimeIrOptimizerRegistry(List.of());

    private final List<GpuRuntimeIrOptimizer> optimizers;
    private final String optimizerPipelineVersion;

    private GpuRuntimeIrOptimizerRegistry(List<GpuRuntimeIrOptimizer> optimizers) {
        this.optimizers = List.copyOf(optimizers);
        this.optimizerPipelineVersion = buildOptimizerPipelineVersion(this.optimizers);
    }

    public static GpuRuntimeIrOptimizerRegistry noOp() {
        return NO_OP;
    }

    public static GpuRuntimeIrOptimizerRegistry of(List<GpuRuntimeIrOptimizer> optimizers) {
        if (optimizers == null || optimizers.isEmpty()) {
            return noOp();
        }
        return new GpuRuntimeIrOptimizerRegistry(optimizers);
    }

    public static GpuRuntimeIrOptimizerRegistry loadFromServiceLoader() {
        java.util.ArrayList<GpuRuntimeIrOptimizer> loadedOptimizers = new java.util.ArrayList<>();
        ServiceLoader.load(GpuRuntimeIrOptimizer.class, GpuRuntimeIrOptimizer.class.getClassLoader())
                .forEach(loadedOptimizers::add);
        return of(loadedOptimizers);
    }

    public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
        return optimizeWithReport(request).artifact();
    }

    public GpuRuntimeIrOptimizationReport optimizeWithReport(GpuRuntimeIrOptimizationRequest request) {
        Optional<IrGpuArtifact> currentArtifact = request.artifact();
        java.util.ArrayList<GpuRuntimeIrOptimizationPassReport> passReports = new java.util.ArrayList<>();
        for (GpuRuntimeIrOptimizationPass pass : optimizerPasses()) {
            GpuRuntimeIrOptimizationRequest passRequest = new GpuRuntimeIrOptimizationRequest(
                    request.compileRequest(),
                    currentArtifact
            );
            try {
                GpuRuntimeIrOptimizationReport passReport = pass.run(passRequest);
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
                passReports.add(GpuRuntimeIrOptimizationPassReport.failed(
                        pass.passVersion(),
                        identityOf(currentArtifact),
                        exception
                ).withStage(pass.stage()));
                break;
            }
        }
        return new GpuRuntimeIrOptimizationReport(currentArtifact, passReports, request.strategyDecision());
    }

    public List<GpuRuntimeIrOptimizationPass> optimizerPasses() {
        if (optimizers.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<GpuRuntimeIrOptimizationPass> passes = new java.util.ArrayList<>();
        for (GpuRuntimeIrOptimizer optimizer : optimizers) {
            passes.add(new LegacyOptimizerPassAdapter(optimizer));
        }
        return List.copyOf(passes);
    }

    public int optimizerCount() {
        return optimizers.size();
    }

    public String optimizerPipelineVersion() {
        return optimizerPipelineVersion;
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

    private static String buildOptimizerPipelineVersion(List<GpuRuntimeIrOptimizer> optimizers) {
        if (optimizers == null || optimizers.isEmpty()) {
            return GpuRuntimeIrOptimizer.NO_OP_VERSION;
        }
        StringBuilder builder = new StringBuilder("optimizer-pipeline:v1");
        for (int index = 0; index < optimizers.size(); index++) {
            GpuRuntimeIrOptimizer optimizer = optimizers.get(index);
            builder.append('|')
                    .append(index)
                    .append(':')
                    .append(optimizer.optimizerVersion());
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
    }
}
