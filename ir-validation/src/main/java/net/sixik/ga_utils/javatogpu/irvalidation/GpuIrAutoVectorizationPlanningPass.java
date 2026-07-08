package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPass;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassException;

import java.util.Objects;

/**
 * No-op optimizer bridge: scans and ranks vectorization candidates but never mutates IR.
 */
public final class GpuIrAutoVectorizationPlanningPass implements GpuIrPass {
    private final GpuIrAutoVectorizationCandidateScanner scanner;
    private final GpuIrAutoVectorizationPlanningMode mode;

    public GpuIrAutoVectorizationPlanningPass() {
        this(new GpuIrAutoVectorizationCandidateScanner(), GpuIrAutoVectorizationPlanningMode.DIAGNOSTIC_ONLY);
    }

    public GpuIrAutoVectorizationPlanningPass(
            GpuIrAutoVectorizationCandidateScanner scanner,
            GpuIrAutoVectorizationPlanningMode mode
    ) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    @Override
    public void run(GpuIrPassContext context) {
        GpuIrAutoVectorizationReport report = scan(context);
        GpuIrAutoVectorizationPreview preview = report.preview();
        if (mode == GpuIrAutoVectorizationPlanningMode.STRICT_FAIL_ON_ANY_DIAGNOSTIC
                && preview.hasBlockingDiagnostics()) {
            throw new GpuIrPassException("IR auto-vectorization planning failed for "
                    + report.methodName()
                    + ": " + preview.summary()
                    + "; first blocking diagnostic " + preview.firstBlockingDiagnosticSummary().orElseThrow());
        }
        if (mode == GpuIrAutoVectorizationPlanningMode.STRICT_FAIL_ON_WARNED_CANDIDATES
                && preview.hasWarnings()) {
            throw new GpuIrPassException("IR auto-vectorization planning failed for "
                    + report.methodName()
                    + ": " + preview.summary()
                    + "; first warning " + preview.warningDiagnostics().get(0).summary());
        }
    }

    public GpuIrAutoVectorizationReport scan(GpuIrPassContext context) {
        if (context == null || context.method() == null) {
            return scanner.scan((net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod) null);
        }
        return scanner.scan(context.method());
    }

    public GpuIrAutoVectorizationPreview preview(GpuIrPassContext context) {
        return scan(context).preview();
    }
}
