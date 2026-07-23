package net.sixik.ga_utils.javatogpu.runtime.validation;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardware-free validation result for backend hook authorization gates.
 */
public record GpuBackendHookAuthorizationValidationResult(
        GpuBackendHookAuthorizationCatalog catalog,
        boolean allowFutureAuthorizedButDisabled
) {

    public GpuBackendHookAuthorizationValidationResult {
        catalog = catalog == null
                ? new GpuBackendHookAuthorizationCatalog(
                GpuBackendTarget.UNKNOWN,
                GpuBackendHookAuthorizationPolicy.readOnlyOnly(),
                List.of()
        )
                : catalog;
    }

    public boolean passed() {
        if (catalog.blockedCount() > 0) {
            return false;
        }
        return allowFutureAuthorizedButDisabled || catalog.futureAuthorizedButDisabledCount() == 0;
    }

    public String status() {
        if (passed()) {
            return "passed";
        }
        if (catalog.blockedCount() > 0) {
            return "blocked";
        }
        return "future-authorized-execution-disabled";
    }

    public String diagnostic() {
        if (passed()) {
            if (catalog.futureAuthorizedButDisabledCount() > 0) {
                return "authorization preview has no blockers, but future-authorized hooks are still disabled by the current runner";
            }
            return "backend hook classpath is read-only ready";
        }
        if (catalog.blockedCount() > 0) {
            return "backend hook classpath is blocked by " + catalog.firstBlocker();
        }
        return "backend hook classpath contains future-authorized hooks that are not executable by the current runner";
    }

    public int recommendedExitCode() {
        return passed() ? 0 : 1;
    }

    public void throwIfFailed() {
        if (!passed()) {
            throw new IllegalStateException(diagnostic() + System.lineSeparator() + catalog.toMarkdown());
        }
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.backend.hookAuthorization.validation"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".passed", Boolean.toString(passed()));
        fields.put(normalizedPrefix + ".diagnostic", diagnostic());
        fields.put(normalizedPrefix + ".recommendedExitCode", Integer.toString(recommendedExitCode()));
        fields.put(normalizedPrefix + ".allowFutureAuthorizedButDisabled",
                Boolean.toString(allowFutureAuthorizedButDisabled));
        fields.putAll(catalog.artifactFields(normalizedPrefix + ".catalog"));
        fields.put("runtime.backend.hookAuthorization.validation.present", "true");
        fields.put("runtime.backend.hookAuthorization.validation.status", status());
        fields.put("runtime.backend.hookAuthorization.validation.passed", Boolean.toString(passed()));
        fields.put("runtime.backend.hookAuthorization.validation.recommendedExitCode",
                Integer.toString(recommendedExitCode()));
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Backend hook authorization validation: ").append(status()).append('\n');
        builder.append("Diagnostic: ").append(diagnostic()).append('\n');
        builder.append("Recommended exit code: ").append(recommendedExitCode()).append('\n');
        builder.append('\n').append(catalog.toMarkdown());
        return builder.toString();
    }
}
