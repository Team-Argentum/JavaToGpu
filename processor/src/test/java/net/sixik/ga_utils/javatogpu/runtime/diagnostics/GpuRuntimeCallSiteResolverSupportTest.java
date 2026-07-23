package net.sixik.ga_utils.javatogpu.runtime.diagnostics;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCallSite;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCallSiteResolver;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GpuRuntimeCallSiteResolverSupportTest {

    @Test
    void rootCallSiteResolverFacadeDelegatesResourcePathToDiagnosticsSupport() {
        assertEquals(
                GpuRuntimeCallSiteResolverSupport.resourcePath("sample.Owner"),
                GpuRuntimeCallSiteResolver.resourcePath("sample.Owner")
        );
        assertEquals(
                "META-INF/javatogpu/call-sites/sample/Owner.properties",
                GpuRuntimeCallSiteResolverSupport.resourcePath("sample.Owner")
        );
    }

    @Test
    void rootCallSiteResolverFacadeDelegatesMetadataLoadToDiagnosticsSupport() {
        ClassLoader classLoader = new SingleResourceClassLoader(
                GpuRuntimeCallSiteResolverSupport.resourcePath("sample.Owner"),
                """
                        callSite.count=1
                        callSite.0.callerClassName=sample.Owner
                        callSite.0.callerMethodName=run
                        callSite.0.sourceName=Owner.java
                        callSite.0.line=12
                        callSite.0.column=3
                        callSite.0.endLine=12
                        callSite.0.endColumn=28
                        callSite.0.expression=kernel(input)
                        callSite.0.targetOwnerName=sample.Kernels
                        callSite.0.targetMethodName=kernel
                        """
        );

        List<GpuRuntimeCallSite> rootCallSites = GpuRuntimeCallSiteResolver.load(classLoader, "sample.Owner");
        List<GpuRuntimeCallSite> supportCallSites = GpuRuntimeCallSiteResolverSupport.load(classLoader, "sample.Owner");

        assertEquals(supportCallSites, rootCallSites);
        assertEquals(1, rootCallSites.size());
        assertEquals("sample.Owner", rootCallSites.get(0).callerClassName());
        assertEquals("kernel(input)", rootCallSites.get(0).expression());
    }

    @Test
    void missingCallSiteMetadataStaysEmpty() {
        assertTrue(GpuRuntimeCallSiteResolverSupport.load(getClass().getClassLoader(), "missing.Owner").isEmpty());
        assertTrue(GpuRuntimeCallSiteResolver.load(getClass().getClassLoader(), "missing.Owner").isEmpty());
    }

    private static final class SingleResourceClassLoader extends ClassLoader {

        private final String resourcePath;
        private final byte[] payload;

        private SingleResourceClassLoader(String resourcePath, String payload) {
            super(GpuRuntimeCallSiteResolverSupportTest.class.getClassLoader());
            this.resourcePath = resourcePath;
            this.payload = payload.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (!resourcePath.equals(name)) {
                return super.getResourceAsStream(name);
            }
            return new ByteArrayInputStream(payload);
        }
    }
}
