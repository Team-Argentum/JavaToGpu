package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Set;

/**
 * Compatibility facade for the built-in dot peephole rule.
 *
 * @deprecated use {@link net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeDotPeepholeRule}.
 */
@Deprecated
public final class GpuRuntimeDotPeepholeRule implements GpuRuntimeIrPeepholeRule {

    public static final String RULE_ID =
            net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeDotPeepholeRule.RULE_ID;
    public static final String VERSION =
            net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeDotPeepholeRule.VERSION;

    private final net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeDotPeepholeRule delegate =
            new net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeDotPeepholeRule();

    @Override
    public GpuRuntimeIrPeepholeRuleReport analyze(GpuRuntimeIrPeepholeRuleContext context) {
        return delegate.analyze(context);
    }

    @Override
    public String ruleId() {
        return delegate.ruleId();
    }

    @Override
    public String ruleVersion() {
        return delegate.ruleVersion();
    }

    @Override
    public String extensionId() {
        return delegate.extensionId();
    }

    @Override
    public String extensionVersion() {
        return delegate.extensionVersion();
    }

    @Override
    public Set<GpuExtensionCapability> extensionCapabilities() {
        return delegate.extensionCapabilities();
    }

    @Override
    public GpuExtensionPhase extensionPhase() {
        return delegate.extensionPhase();
    }

    @Override
    public GpuExtensionPermission extensionPermission() {
        return delegate.extensionPermission();
    }

    @Override
    public int extensionOrder() {
        return delegate.extensionOrder();
    }
}
