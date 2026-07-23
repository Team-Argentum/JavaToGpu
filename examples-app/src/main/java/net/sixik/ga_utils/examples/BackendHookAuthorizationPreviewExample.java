package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendHookAuthorizationCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendHookAuthorizationPolicy;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendHookRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendInvocationHook;

import java.util.List;

/**
 * Runnable example for backend hook authorization previews.
 */
public final class BackendHookAuthorizationPreviewExample {

    private static final String READ_ONLY_HOOK_ID = "examples.backend-hook.preview-read-only-invocation";
    private static final String PRODUCTION_HOOK_ID = "examples.backend-hook.preview-production-invocation";

    private BackendHookAuthorizationPreviewExample() {
    }

    public static void main(String[] args) {
        System.out.println(renderAuthorizationPreview());
    }

    static String renderAuthorizationPreview() {
        GpuBackendHookRegistry registry = GpuBackendHookRegistry.of(List.of(
                readOnlyInvocationHook(),
                productionInvocationHook()
        ));
        GpuBackendHookAuthorizationCatalog defaultCatalog = registry.authorizationCatalog(GpuBackendTarget.OPENCL);
        GpuBackendHookAuthorizationCatalog previewCatalog = registry.authorizationCatalog(
                GpuBackendTarget.OPENCL,
                GpuBackendHookAuthorizationPolicy.previewExplicitAuthorization(
                        GpuExtensionPermission.PRODUCTION_AFFECTING,
                        List.of(PRODUCTION_HOOK_ID)
                )
        );

        StringBuilder builder = new StringBuilder();
        builder.append("Backend hook authorization preview:").append(System.lineSeparator());
        builder.append("- defaultStatus=").append(defaultCatalog.status()).append(System.lineSeparator());
        builder.append("- defaultFirstBlocker=").append(defaultCatalog.firstBlocker()).append(System.lineSeparator());
        builder.append("- defaultExecutableHooks=")
                .append(defaultCatalog.currentRegistryExecutableCount())
                .append(System.lineSeparator());
        builder.append("- previewStatus=").append(previewCatalog.status()).append(System.lineSeparator());
        builder.append("- previewFirstBlocker=").append(previewCatalog.firstBlocker()).append(System.lineSeparator());
        builder.append("- previewExecutableHooks=")
                .append(previewCatalog.currentRegistryExecutableCount())
                .append(System.lineSeparator());
        builder.append("- previewFutureAuthorizedButDisabled=")
                .append(previewCatalog.futureAuthorizedButDisabledCount())
                .append(System.lineSeparator());
        builder.append("- rule=preview authorization is diagnostic only; production-affecting hooks still do not execute")
                .append(System.lineSeparator());
        return builder.toString();
    }

    private static GpuBackendInvocationHook readOnlyInvocationHook() {
        return new GpuBackendInvocationHook() {
            @Override
            public String extensionId() {
                return READ_ONLY_HOOK_ID;
            }
        };
    }

    private static GpuBackendInvocationHook productionInvocationHook() {
        return new GpuBackendInvocationHook() {
            @Override
            public String extensionId() {
                return PRODUCTION_HOOK_ID;
            }

            @Override
            public GpuExtensionPermission extensionPermission() {
                return GpuExtensionPermission.PRODUCTION_AFFECTING;
            }
        };
    }
}
