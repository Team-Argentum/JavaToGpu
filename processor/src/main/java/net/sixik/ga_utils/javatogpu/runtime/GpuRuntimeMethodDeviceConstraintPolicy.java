package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodDeviceConstraint;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Core policy that enforces entry-method compatibility metadata loaded from IrGpu.
 */
public final class GpuRuntimeMethodDeviceConstraintPolicy implements GpuRuntimeDevicePolicy {

    public static final String POLICY_ID = "javatogpu.device.method-constraint";

    @Override
    public GpuRuntimeDevicePolicyDecision evaluate(GpuRuntimeDevicePolicyContext context) {
        IrGpuMethodDeviceConstraint constraint = context.irGpuArtifact()
                .flatMap(artifact -> artifact.entryDeviceConstraint())
                .filter(IrGpuMethodDeviceConstraint::active)
                .orElse(null);
        if (constraint == null) {
            return GpuRuntimeDevicePolicyDecision.noChange(this);
        }

        LinkedHashSet<String> rejected = new LinkedHashSet<>();
        LinkedHashMap<String, String> facts = new LinkedHashMap<>();
        int matched = 0;
        for (GpuRuntimeDeviceProfile candidate : context.candidates()) {
            String key = GpuRuntimeDevicePolicyContext.deviceKey(candidate);
            String incompatibility = constraint.incompatibility(candidate);
            boolean compatible = incompatibility.isBlank();
            facts.put(key + ".methodConstraintCompatible", Boolean.toString(compatible));
            facts.put(key + ".methodConstraintReason", compatible ? "none" : incompatibility);
            if (compatible) {
                matched++;
            } else {
                rejected.add(key);
            }
        }
        facts.put("methodConstraint.methodName", constraint.methodName());
        facts.put("methodConstraint.emittedName", constraint.emittedName());
        facts.put("methodConstraint.source", constraint.source());
        facts.put("methodConstraint.matchCount", Integer.toString(matched));

        return new GpuRuntimeDevicePolicyDecision(
                policyId(),
                policyVersion(),
                Map.of(),
                rejected,
                facts,
                List.of(),
                true,
                List.of(),
                matched == 0
                        ? List.of("entry method device constraint rejected every discovered candidate")
                        : List.of("entry method device constraint accepted " + matched + " candidate(s)")
        );
    }

    @Override
    public String policyId() {
        return POLICY_ID;
    }
}
