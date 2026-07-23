package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hardware-free CUDA texture/surface object creation boundary.
 *
 * <p>This report resolves the Driver API symbols that future image/sampler binding needs, but deliberately does not
 * call {@code cuTexObjectCreate} or {@code cuSurfObjectCreate}. Runtime object creation remains fail-closed until real
 * texture/surface allocation, destruction, and device evidence are implemented.</p>
 */
public record CudaImageSamplerObjectCreationContractReport(
        List<Entry> entries,
        List<DriverSymbol> driverSymbols,
        CudaImageSamplerAbiPlanReport abiPlan,
        boolean objectCreationEnabled,
        int activeObjectCount
) {

    public record Entry(
            String key,
            String javaType,
            String cudaAbiRole,
            String cudaResourceKind,
            String parameterCarrier,
            List<String> requiredDriverSymbols,
            String objectCreationStatus,
            boolean productionSupportEnabled
    ) {
        public Entry {
            key = normalize(key, "unknown");
            javaType = normalize(javaType, "unknown");
            cudaAbiRole = normalize(cudaAbiRole, "unknown");
            cudaResourceKind = normalize(cudaResourceKind, "unknown");
            parameterCarrier = normalize(parameterCarrier, "unknown");
            requiredDriverSymbols = requiredDriverSymbols == null ? List.of() : List.copyOf(requiredDriverSymbols);
            objectCreationStatus = normalize(objectCreationStatus, "fail-closed");
        }

        public boolean textureEntry() {
            return "read-texture-object".equals(cudaAbiRole);
        }

        public boolean surfaceEntry() {
            return "write-surface-object".equals(cudaAbiRole);
        }

        public boolean samplerEntry() {
            return "texture-descriptor-state".equals(cudaAbiRole);
        }

        public boolean ready() {
            return !"unknown".equals(javaType)
                    && !"unknown".equals(cudaAbiRole)
                    && !"unknown".equals(cudaResourceKind)
                    && !"unknown".equals(parameterCarrier)
                    && !requiredDriverSymbols.isEmpty()
                    && "fail-closed".equals(objectCreationStatus)
                    && !productionSupportEnabled;
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerObjectCreation.entry"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".javaType", javaType);
            fields.put(normalizedPrefix + ".cudaAbi.role", cudaAbiRole);
            fields.put(normalizedPrefix + ".cudaResource.kind", cudaResourceKind);
            fields.put(normalizedPrefix + ".parameter.carrier", parameterCarrier);
            fields.put(normalizedPrefix + ".driverSymbol.required.count", Integer.toString(requiredDriverSymbols.size()));
            fields.put(normalizedPrefix + ".objectCreation.status", objectCreationStatus);
            fields.put(normalizedPrefix + ".productionSupport.enabled", Boolean.toString(productionSupportEnabled));
            for (int index = 0; index < requiredDriverSymbols.size(); index++) {
                fields.put(normalizedPrefix + ".driverSymbol.required." + index, requiredDriverSymbols.get(index));
            }
            return Collections.unmodifiableMap(fields);
        }
    }

    public record DriverSymbol(
            String name,
            String kind,
            boolean resolved,
            long address
    ) {
        public DriverSymbol {
            name = normalize(name, "unknown");
            kind = normalize(kind, "resource");
            address = Math.max(0L, address);
        }

        public boolean objectSymbol() {
            return "object".equals(kind);
        }

        public String status() {
            return resolved ? "resolved" : "missing";
        }

        public String blocker() {
            return resolved ? "none" : "cuda-image-sampler-object-creation-symbol-missing:" + name;
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerObjectCreation.driverSymbol"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".name", name);
            fields.put(normalizedPrefix + ".kind", kind);
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".resolved", Boolean.toString(resolved));
            fields.put(normalizedPrefix + ".address.present", Boolean.toString(address != 0L));
            fields.put(normalizedPrefix + ".blocker", blocker());
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerObjectCreationContractReport {
        entries = entries == null ? List.of() : List.copyOf(entries);
        driverSymbols = driverSymbols == null ? List.of() : List.copyOf(driverSymbols);
        abiPlan = abiPlan == null ? CudaImageSamplerAbiPlanReport.inspectBuiltIns() : abiPlan;
        activeObjectCount = Math.max(0, activeObjectCount);
    }

    public static CudaImageSamplerObjectCreationContractReport inspectBuiltIns() {
        CudaDriverLoadedModule loadedModule = loadedModule(new LinkedHashSet<>(plannedDriverSymbols()));
        try {
            return inspect(loadedModule);
        } finally {
            loadedModule.close();
        }
    }

    public static CudaImageSamplerObjectCreationContractReport inspect(CudaDriverLoadedModule loadedModule) {
        return inspect(loadedModule, CudaImageSamplerAbiPlanReport.inspectBuiltIns());
    }

    static CudaImageSamplerObjectCreationContractReport inspect(
            CudaDriverLoadedModule loadedModule,
            CudaImageSamplerAbiPlanReport abiPlan
    ) {
        List<String> plannedSymbols = plannedDriverSymbols();
        return new CudaImageSamplerObjectCreationContractReport(
                CudaImageSamplerAbi.descriptors().stream()
                        .map(CudaImageSamplerObjectCreationContractReport::entry)
                        .toList(),
                plannedSymbols.stream()
                        .map(symbol -> resolve(loadedModule, symbol))
                        .toList(),
                abiPlan,
                false,
                0
        );
    }

    public boolean ready() {
        return !entries.isEmpty()
                && entries.stream().allMatch(Entry::ready)
                && abiPlan.ready()
                && !objectCreationEnabled
                && activeObjectCount == 0
                && !driverSymbols.isEmpty()
                && driverSymbols.stream().allMatch(DriverSymbol::resolved);
    }

    public String status() {
        return ready() ? "ready" : "blocked";
    }

    public String objectOwnershipBoundaryStatus() {
        return "prepared";
    }

    public long entryReadyCount() {
        return entries.stream().filter(Entry::ready).count();
    }

    public long entryBlockedCount() {
        return entries.stream().filter(entry -> !entry.ready()).count();
    }

    public long textureEntryCount() {
        return entries.stream().filter(Entry::textureEntry).count();
    }

    public long surfaceEntryCount() {
        return entries.stream().filter(Entry::surfaceEntry).count();
    }

    public long samplerEntryCount() {
        return entries.stream().filter(Entry::samplerEntry).count();
    }

    public long resolvedDriverSymbolCount() {
        return driverSymbols.stream().filter(DriverSymbol::resolved).count();
    }

    public long missingDriverSymbolCount() {
        return driverSymbols.stream().filter(symbol -> !symbol.resolved()).count();
    }

    public long objectDriverSymbolCount() {
        return driverSymbols.stream().filter(DriverSymbol::objectSymbol).count();
    }

    public long resolvedObjectDriverSymbolCount() {
        return driverSymbols.stream().filter(DriverSymbol::objectSymbol).filter(DriverSymbol::resolved).count();
    }

    public long resourceDriverSymbolCount() {
        return driverSymbols.stream().filter(symbol -> !symbol.objectSymbol()).count();
    }

    public long resolvedResourceDriverSymbolCount() {
        return driverSymbols.stream().filter(symbol -> !symbol.objectSymbol()).filter(DriverSymbol::resolved).count();
    }

    public String firstBlocker() {
        if (entries.isEmpty()) {
            return "cuda-image-sampler-object-creation-entries-missing";
        }
        for (Entry entry : entries) {
            if (!entry.ready()) {
                return "cuda-image-sampler-object-creation-entry-not-ready:" + entry.key();
            }
        }
        if (!abiPlan.ready()) {
            return "cuda-image-sampler-abi-plan-not-ready:" + abiPlan.firstBlocker();
        }
        if (objectCreationEnabled) {
            return "cuda-image-sampler-object-creation-enabled-without-runtime-implementation";
        }
        if (activeObjectCount != 0) {
            return "cuda-image-sampler-object-creation-active-objects-unexpected:" + activeObjectCount;
        }
        if (driverSymbols.isEmpty()) {
            return "cuda-image-sampler-object-creation-symbols-missing";
        }
        return driverSymbols.stream()
                .filter(symbol -> !symbol.resolved())
                .map(DriverSymbol::blocker)
                .findFirst()
                .orElse("none");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerObjectCreation"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(normalizedPrefix + ".entry." + index));
        }
        for (int index = 0; index < driverSymbols.size(); index++) {
            fields.putAll(driverSymbols.get(index).artifactFields(normalizedPrefix + ".driverSymbol." + index));
        }
        putFields(fields, "runtime.cuda.imageSamplerObjectCreation");
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler object creation contract: ").append(status()).append('\n');
        builder.append("Entries: ").append(entryReadyCount()).append('/').append(entries.size()).append(" ready").append('\n');
        builder.append("Texture entries: ").append(textureEntryCount()).append(", surface entries: ").append(surfaceEntryCount())
                .append(", sampler entries: ").append(samplerEntryCount()).append('\n');
        builder.append("Driver symbols: ").append(resolvedDriverSymbolCount()).append('/').append(driverSymbols.size())
                .append(" resolved; object symbols ").append(resolvedObjectDriverSymbolCount()).append('/')
                .append(objectDriverSymbolCount()).append(" resolved").append('\n');
        builder.append("Object creation enabled: ").append(objectCreationEnabled).append('\n');
        builder.append("Object ownership boundary: ").append(objectOwnershipBoundaryStatus()).append('\n');
        builder.append("Active CUDA image/sampler objects: ").append(activeObjectCount).append('\n');
        builder.append("ABI plan: ").append(abiPlan.status()).append('\n');
        builder.append("First blocker: ").append(firstBlocker()).append('\n');
        builder.append('\n').append("Symbols:").append('\n');
        for (DriverSymbol symbol : driverSymbols) {
            builder.append("- ")
                    .append(symbol.name())
                    .append(": ")
                    .append(symbol.status())
                    .append(" (`")
                    .append(symbol.kind())
                    .append("`)")
                    .append('\n');
        }
        return builder.toString();
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", "true");
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".objectCreation.enabled", Boolean.toString(objectCreationEnabled));
        fields.put(prefix + ".objectCreation.activeObject.count", Integer.toString(activeObjectCount));
        fields.put(prefix + ".objectOwnership.status", objectOwnershipBoundaryStatus());
        fields.put(prefix + ".objectOwnership.activeObject.count", Integer.toString(activeObjectCount));
        fields.put(prefix + ".productionSupport.enabled", "false");
        fields.put(prefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(prefix + ".entry.ready.count", Long.toString(entryReadyCount()));
        fields.put(prefix + ".entry.blocked.count", Long.toString(entryBlockedCount()));
        fields.put(prefix + ".entry.texture.count", Long.toString(textureEntryCount()));
        fields.put(prefix + ".entry.surface.count", Long.toString(surfaceEntryCount()));
        fields.put(prefix + ".entry.sampler.count", Long.toString(samplerEntryCount()));
        fields.put(prefix + ".driverSymbol.required.count", Integer.toString(driverSymbols.size()));
        fields.put(prefix + ".driverSymbol.resolved.count", Long.toString(resolvedDriverSymbolCount()));
        fields.put(prefix + ".driverSymbol.missing.count", Long.toString(missingDriverSymbolCount()));
        fields.put(prefix + ".driverSymbol.object.required.count", Long.toString(objectDriverSymbolCount()));
        fields.put(prefix + ".driverSymbol.object.resolved.count", Long.toString(resolvedObjectDriverSymbolCount()));
        fields.put(prefix + ".driverSymbol.resource.required.count", Long.toString(resourceDriverSymbolCount()));
        fields.put(prefix + ".driverSymbol.resource.resolved.count", Long.toString(resolvedResourceDriverSymbolCount()));
        fields.put(prefix + ".abiPlan.status", abiPlan.status());
        fields.put(prefix + ".abiPlan.ready", Boolean.toString(abiPlan.ready()));
        fields.put(prefix + ".firstBlocker", firstBlocker());
    }

    private static Entry entry(CudaImageSamplerAbi.Descriptor descriptor) {
        return new Entry(
                descriptor.key(),
                descriptor.javaQualifiedName(),
                descriptor.cudaAbiRole(),
                descriptor.cudaResourceKind(),
                descriptor.parameterCarrier(),
                splitSymbols(descriptor.requiredDriverSymbols()),
                descriptor.runtimeBindingStatus(),
                descriptor.productionSupportEnabled()
        );
    }

    private static DriverSymbol resolve(CudaDriverLoadedModule loadedModule, String symbol) {
        long address = loadedModule == null ? 0L : loadedModule.findSymbol(symbol);
        return new DriverSymbol(symbol, symbolKind(symbol), address != 0L, address);
    }

    private static String symbolKind(String symbol) {
        return CudaDriverLibrary.REQUIRED_IMAGE_SAMPLER_OBJECT_SYMBOLS.contains(symbol) ? "object" : "resource";
    }

    private static List<String> plannedDriverSymbols() {
        LinkedHashSet<String> symbols = new LinkedHashSet<>(CudaDriverLibrary.REQUIRED_IMAGE_SAMPLER_SYMBOLS);
        for (CudaImageSamplerAbi.Descriptor descriptor : CudaImageSamplerAbi.descriptors()) {
            symbols.addAll(splitSymbols(descriptor.requiredDriverSymbols()));
        }
        return List.copyOf(symbols);
    }

    private static List<String> splitSymbols(String symbols) {
        if (symbols == null || symbols.isBlank() || "none".equals(symbols.trim())) {
            return List.of();
        }
        ArrayList<String> result = new ArrayList<>();
        for (String symbol : symbols.split(",")) {
            if (symbol != null && !symbol.isBlank()) {
                result.add(symbol.trim());
            }
        }
        return List.copyOf(result);
    }

    private static CudaDriverLoadedModule loadedModule(Set<String> resolvedSymbols) {
        return new CudaDriverLoadedModule(
                "cuda-module-loader:image-sampler-object-creation-contract",
                "synthetic-nvcuda",
                "inline://cuda/image-sampler-object-creation-contract",
                "jtg_cuda_image_sampler_object_creation_contract",
                0xC0DA_3001L,
                0xC0DA_3002L,
                0xC0DA_3003L,
                new SyntheticLibraryHandle(resolvedSymbols),
                new SyntheticDriverApiInvoker(),
                List.of("synthetic CUDA Driver API handles for image/sampler object creation contract validation")
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record SyntheticLibraryHandle(Set<String> resolvedSymbols) implements CudaDriverLibrary.SharedLibraryHandle {
        private SyntheticLibraryHandle {
            resolvedSymbols = resolvedSymbols == null ? Set.of() : Set.copyOf(resolvedSymbols);
        }

        @Override
        public String loadedName() {
            return "synthetic-nvcuda";
        }

        @Override
        public String path() {
            return "inline://cuda/image-sampler-object-creation-contract";
        }

        @Override
        public long findSymbol(String symbolName) {
            if (!resolvedSymbols.contains(symbolName)) {
                return 0L;
            }
            return 0x3000L + Math.abs(symbolName.hashCode() % 0x0FFFL);
        }

        @Override
        public void close() {
        }
    }

    private static final class SyntheticDriverApiInvoker implements CudaDriverLibrary.DriverApiInvoker {
        @Override
        public int cuInit(int flags, long functionAddress) {
            return CudaDriverLibrary.CUDA_SUCCESS;
        }

        @Override
        public int cuModuleLoadDataEx(
                long moduleOutAddress,
                long imageAddress,
                int optionCount,
                long optionsAddress,
                long optionValuesAddress,
                long functionAddress
        ) {
            return CudaDriverLibrary.CUDA_SUCCESS;
        }

        @Override
        public int cuModuleGetFunction(
                long functionOutAddress,
                long moduleHandle,
                long kernelNameAddress,
                long functionAddress
        ) {
            return CudaDriverLibrary.CUDA_SUCCESS;
        }

        @Override
        public int cuModuleUnload(long moduleHandle, long functionAddress) {
            return CudaDriverLibrary.CUDA_SUCCESS;
        }
    }
}
