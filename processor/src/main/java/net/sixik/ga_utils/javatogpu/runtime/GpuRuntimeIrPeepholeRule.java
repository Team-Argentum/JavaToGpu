package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Set;

/**
 * One structural typed-IR peephole analysis or rewrite proposal rule.
 */
@FunctionalInterface
public interface GpuRuntimeIrPeepholeRule extends GpuExtension {

    GpuRuntimeIrPeepholeRuleReport analyze(GpuRuntimeIrPeepholeRuleContext context);

    default String ruleId() {
        return getClass().getName();
    }

    default String ruleVersion() {
        return "1";
    }

    @Override
    default String extensionId() {
        return ruleId();
    }

    @Override
    default String extensionVersion() {
        return ruleVersion();
    }

    @Override
    default Set<GpuExtensionCapability> extensionCapabilities() {
        return Set.of(GpuExtensionCapability.IR_OPTIMIZATION_PROPOSAL);
    }

    @Override
    default GpuExtensionPhase extensionPhase() {
        return GpuExtensionPhase.RUNTIME_IR_OPTIMIZATION;
    }

    @Override
    default GpuExtensionPermission extensionPermission() {
        return GpuExtensionPermission.MUTATION_PROPOSAL;
    }
}
