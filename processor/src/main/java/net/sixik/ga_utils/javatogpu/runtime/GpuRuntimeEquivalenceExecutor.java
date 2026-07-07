package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Opt-in executor for comparing original and optimized runtime IR before promotion.
 *
 * <p>The default runtime path intentionally does not execute kernels twice. Backends or tests can plug in a concrete
 * executor once deterministic inputs and safe pre/post execution are available for a selected optimization family.</p>
 */
@FunctionalInterface
public interface GpuRuntimeEquivalenceExecutor {

    GpuRuntimeEquivalenceEvidence execute(GpuRuntimeEquivalenceRequest request);

    static GpuRuntimeEquivalenceExecutor notRun(String reason) {
        return request -> GpuRuntimeEquivalenceEvidence.notRun(
                request == null ? null : request.optimizedCompileRequest(),
                reason
        );
    }
}
