package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend;

import java.util.List;
import java.util.Objects;

/**
 * Built-in runtime backend catalog.
 *
 * <p>This is the first lightweight discovery layer for backend selection. It exposes lazy catalog entries rather than
 * eagerly probing native APIs, so applications can inspect backend families and then opt into selection when ready.</p>
 */
public final class GpuRuntimeBackendCatalog {

    private GpuRuntimeBackendCatalog() {
    }

    /**
     * Returns the standard production-ready backend catalog entries.
     */
    public static List<GpuRuntimeBackendCatalogEntry> standard() {
        return List.of(openClSharedCache());
    }

    /**
     * Returns standard entries plus explicit planned-backend placeholders for diagnostics and UI discovery.
     */
    public static List<GpuRuntimeBackendCatalogEntry> standardWithPlannedBackends() {
        return List.of(
                openClSharedCache(),
                plannedUnsupported(GpuBackendTarget.CUDA),
                plannedUnsupported(GpuBackendTarget.VULKAN),
                plannedUnsupported(GpuBackendTarget.METAL)
        );
    }

    /**
     * Returns the default OpenCL shared-cache backend entry.
     */
    public static GpuRuntimeBackendCatalogEntry openClSharedCache() {
        return GpuRuntimeBackendCatalogEntry.owned(
                GpuBackendTarget.OPENCL,
                "OpenCL (shared cache)",
                OpenClGpuRuntimeBackend::sharedCache,
                true,
                "production runtime adapter"
        );
    }

    /**
     * Returns an explicit unsupported placeholder entry for a planned backend family.
     */
    public static GpuRuntimeBackendCatalogEntry plannedUnsupported(GpuBackendTarget backendTarget) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        String backendName = target.name();
        String diagnostic = "Runtime backend adapter is not implemented for "
                + target
                + "; keep using OPENCL or provide a custom runtime backend";
        return GpuRuntimeBackendCatalogEntry.owned(
                target,
                backendName,
                () -> new UnsupportedGpuRuntimeBackend(target, backendName, diagnostic),
                false,
                diagnostic
        );
    }

    /**
     * Appends catalog entries to an existing policy builder.
     */
    public static GpuRuntimeBackendPolicy.Builder appendTo(
            GpuRuntimeBackendPolicy.Builder builder,
            List<GpuRuntimeBackendCatalogEntry> entries
    ) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(entries, "entries");
        for (GpuRuntimeBackendCatalogEntry entry : entries) {
            builder.preferCatalogEntry(entry);
        }
        return builder;
    }
}
