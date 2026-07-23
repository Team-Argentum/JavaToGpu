package net.sixik.ga_utils.javatogpu.runtime.cuda;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaImageSamplerObjectCreationContractReportTest {

    @Test
    void objectCreationContractIsReadyButRuntimeObjectsRemainDisabled() {
        CudaImageSamplerObjectCreationContractReport report = CudaImageSamplerObjectCreationContractReport.inspectBuiltIns();
        Map<String, String> fields = report.artifactFields("test.cuda.imageSamplerObjectCreation");
        String rendered = CudaImageSamplerObjectCreationContractCli.render(report);

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(17, report.entries().size());
        assertEquals(17, report.entryReadyCount());
        assertEquals(0, report.entryBlockedCount());
        assertEquals(8, report.textureEntryCount());
        assertEquals(8, report.surfaceEntryCount());
        assertEquals(1, report.samplerEntryCount());
        assertFalse(report.objectCreationEnabled());
        assertEquals(0, report.activeObjectCount());
        assertEquals(13, report.driverSymbols().size());
        assertEquals(13, report.resolvedDriverSymbolCount());
        assertEquals(0, report.missingDriverSymbolCount());
        assertEquals(4, report.objectDriverSymbolCount());
        assertEquals(4, report.resolvedObjectDriverSymbolCount());
        assertEquals(9, report.resourceDriverSymbolCount());
        assertEquals(9, report.resolvedResourceDriverSymbolCount());
        assertEquals("prepared", report.objectOwnershipBoundaryStatus());
        assertTrue(report.abiPlan().ready());

        assertEquals("ready", fields.get("runtime.cuda.imageSamplerObjectCreation.status"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerObjectCreation.objectCreation.enabled"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerObjectCreation.objectCreation.activeObject.count"));
        assertEquals("prepared", fields.get("runtime.cuda.imageSamplerObjectCreation.objectOwnership.status"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerObjectCreation.objectOwnership.activeObject.count"));
        assertEquals("17", fields.get("runtime.cuda.imageSamplerObjectCreation.entry.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerObjectCreation.entry.texture.count"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerObjectCreation.entry.surface.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerObjectCreation.entry.sampler.count"));
        assertEquals("13", fields.get("runtime.cuda.imageSamplerObjectCreation.driverSymbol.required.count"));
        assertEquals("13", fields.get("runtime.cuda.imageSamplerObjectCreation.driverSymbol.resolved.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerObjectCreation.driverSymbol.missing.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerObjectCreation.driverSymbol.object.required.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerObjectCreation.driverSymbol.object.resolved.count"));
        assertEquals("ready", fields.get("runtime.cuda.imageSamplerObjectCreation.abiPlan.status"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerObjectCreation.abiPlan.ready"));
        assertEquals("none", fields.get("runtime.cuda.imageSamplerObjectCreation.firstBlocker"));

        assertTrue(report.toMarkdown().contains("CUDA image/sampler object creation contract: ready"));
        assertTrue(report.toMarkdown().contains("Driver symbols: 13/13 resolved; object symbols 4/4 resolved"));
        assertTrue(rendered.contains("objectCreationEnabled=false"));
        assertTrue(rendered.contains("objectOwnershipBoundary=prepared"));
        assertTrue(rendered.contains("activeObjectCount=0"));
        assertTrue(rendered.contains("resolvedDriverSymbols=13"));
        assertTrue(rendered.contains("rule=object creation is metadata/preflight only"));
    }

    @Test
    void missingTextureObjectCreateSymbolBlocksContract() {
        CudaImageSamplerObjectCreationContractReport report = inspectWithout("cuTexObjectCreate");

        assertEquals("blocked", report.status());
        assertFalse(report.ready());
        assertEquals(12, report.resolvedDriverSymbolCount());
        assertEquals(1, report.missingDriverSymbolCount());
        assertEquals(
                "cuda-image-sampler-object-creation-symbol-missing:cuTexObjectCreate",
                report.firstBlocker()
        );
    }

    @Test
    void missingSurfaceObjectCreateSymbolBlocksContract() {
        CudaImageSamplerObjectCreationContractReport report = inspectWithout("cuSurfObjectCreate");

        assertEquals("blocked", report.status());
        assertFalse(report.ready());
        assertEquals(12, report.resolvedDriverSymbolCount());
        assertEquals(1, report.missingDriverSymbolCount());
        assertEquals(
                "cuda-image-sampler-object-creation-symbol-missing:cuSurfObjectCreate",
                report.firstBlocker()
        );
    }

    private static CudaImageSamplerObjectCreationContractReport inspectWithout(String missingSymbol) {
        LinkedHashSet<String> symbols = new LinkedHashSet<>(CudaDriverLibrary.REQUIRED_IMAGE_SAMPLER_SYMBOLS);
        symbols.remove(missingSymbol);
        CudaDriverLoadedModule loadedModule = loadedModule(symbols);
        try {
            return CudaImageSamplerObjectCreationContractReport.inspect(
                    loadedModule,
                    CudaImageSamplerAbiPlanReport.inspectBuiltIns()
            );
        } finally {
            loadedModule.close();
        }
    }

    private static CudaDriverLoadedModule loadedModule(Set<String> resolvedSymbols) {
        return new CudaDriverLoadedModule(
                "cuda-module-loader:image-sampler-object-creation-contract-test",
                "synthetic-nvcuda",
                "inline://cuda/image-sampler-object-creation-contract-test",
                "jtg_cuda_image_sampler_object_creation_contract_test",
                0xC0DA_4001L,
                0xC0DA_4002L,
                0xC0DA_4003L,
                new SyntheticLibraryHandle(resolvedSymbols),
                new SyntheticDriverApiInvoker(),
                java.util.List.of("synthetic CUDA Driver API handles for image/sampler object creation contract test")
        );
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
            return "inline://cuda/image-sampler-object-creation-contract-test";
        }

        @Override
        public long findSymbol(String symbolName) {
            return resolvedSymbols.contains(symbolName) ? 0x4000L : 0L;
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
