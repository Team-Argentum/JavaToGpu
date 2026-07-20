package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendHookAuthorizationReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendHookTestHarness;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendHookTestHarnessReport;

import java.util.Map;

/**
 * Runnable example for ServiceLoader backend hooks.
 */
public final class BackendHookServiceLoaderExample {

    private BackendHookServiceLoaderExample() {
    }

    public static void main(String[] args) {
        System.out.println(renderServiceLoaderHookPreview());
    }

    static String renderServiceLoaderHookPreview() {
        GpuBackendHookTestHarness harness = GpuBackendHookTestHarness.loadWithServiceLoader();
        GpuBackendHookTestHarnessReport report = harness.runSyntheticOpenCl();
        GpuBackendHookAuthorizationReport authorizationReport = harness.registry().authorizationReport(
                GpuBackendTarget.OPENCL,
                GpuExtensionPhase.BACKEND_INVOCATION
        );

        StringBuilder builder = new StringBuilder();
        builder.append("Backend hook ServiceLoader example:").append(System.lineSeparator());
        builder.append("- loadedExampleHooks=").append(exampleHookCount(harness)).append(System.lineSeparator());
        builder.append("- discoveryHookCount=")
                .append(report.discoveryFields().get("runtime.backend.hookExecution.discovery.hook.count"))
                .append(System.lineSeparator());
        builder.append("- discoveryContribution=")
                .append("examples.backendHook.discovery.backendTarget")
                .append('=')
                .append(contributionValue(
                        report.discoveryFields(),
                        "runtime.backend.hookExecution.discovery.hook.0",
                        "examples.backendHook.discovery.backendTarget"
                ))
                .append(System.lineSeparator());
        builder.append("- loweringHookStatus=")
                .append(report.loweringFields().get("runtime.backend.hookExecution.lowering.hook.0.status"))
                .append(System.lineSeparator());
        builder.append("- compilationHookStatus=")
                .append(report.compilationFields().get("runtime.backend.hookExecution.compilation.hook.0.status"))
                .append(System.lineSeparator());
        builder.append("- invocationHookStatus=")
                .append(report.invocationFields().get("runtime.backend.hookExecution.invocation.hook.0.status"))
                .append(System.lineSeparator());
        builder.append("- artifactContribution=")
                .append("examples.backendHook.artifact.status")
                .append('=')
                .append(contributionValue(
                        report.artifactFields(),
                        "runtime.backend.hookExecution.artifact.hook.0",
                        "examples.backendHook.artifact.status"
                ))
                .append(System.lineSeparator());
        builder.append("- authorizationStatus=")
                .append(authorizationReport.status())
                .append(System.lineSeparator());
        builder.append("- authorizationExecutableHooks=")
                .append(authorizationReport.currentRegistryExecutableCount())
                .append(System.lineSeparator());
        builder.append("- rule=read-only hooks observe receipts; returned replacements are ignored by the registry")
                .append(System.lineSeparator());
        return builder.toString();
    }

    private static long exampleHookCount(GpuBackendHookTestHarness harness) {
        return harness.registry().hooks().stream()
                .map(GpuBackendHook::extensionId)
                .filter(id -> id.startsWith("examples.backend-hook."))
                .count();
    }

    private static String contributionValue(Map<String, String> fields, String hookPrefix, String contributionKey) {
        int contributionCount = parseInt(fields.get(hookPrefix + ".contribution.count"));
        for (int index = 0; index < contributionCount; index++) {
            String contributionPrefix = hookPrefix + ".contribution." + index;
            if (contributionKey.equals(fields.get(contributionPrefix + ".key"))) {
                return fields.getOrDefault(contributionPrefix + ".value", "missing");
            }
        }
        return "missing";
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
