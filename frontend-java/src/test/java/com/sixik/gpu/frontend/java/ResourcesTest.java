package com.sixik.gpu.frontend.java;

import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrModule;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ресурсы фронтенда (решение по O5): IR по {@code kernelId} и индекс ядер для weaver'а. */
class ResourcesTest {

    private static final String TWO_KERNELS = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.Global;
            import com.sixik.gpu.api.annotations.Kernel;

            public final class Outer {
                @Kernel
                public static void outerKernel(@Global float[] a) {
                    a[Gpu.getGlobalId(0)] = 1f;
                }

                public static final class Inner {
                    @Kernel
                    public static void innerKernel(@Global float[] a, int n, @Global long[] l, boolean[] flags) {
                        a[Gpu.getGlobalId(0)] = n;
                    }
                }
            }
            """;

    @Test
    void indexListsKernelsWithBinaryNamesAndDescriptors() {
        FrontendHarness.Outcome r = FrontendHarness.compile(TWO_KERNELS);
        assertTrue(r.success(), r.describe());
        String text = new String(r.resources().get(KernelResources.INDEX), StandardCharsets.UTF_8);
        List<KernelResources.IndexEntry> entries = KernelResources.parseIndex(text);
        assertEquals(2, entries.size());
        // отсортировано по классу: demo.Outer раньше demo.Outer$Inner
        assertEquals("demo.Outer", entries.get(0).className());
        assertEquals("outerKernel", entries.get(0).method());
        assertEquals("([F)V", entries.get(0).descriptor());
        assertEquals("demo.Outer$Inner", entries.get(1).className());
        assertEquals("innerKernel", entries.get(1).method());
        assertEquals("([FI[J[Z)V", entries.get(1).descriptor());
        for (KernelResources.IndexEntry e : entries) {
            byte[] ir = r.resources().get(KernelResources.irPath(e.kernelId()));
            assertTrue(ir != null, "for " + e.method() + " has no IR resource");
            assertEquals(e.kernelId(), IrBinary.kernelId(IrBinary.read(ir).value()));
        }
        assertEquals(text, KernelResources.formatIndex(entries), "the index format is canonical");
    }

    @Test
    void irResourcesLiveUnderKernelId() {
        FrontendHarness.Outcome r = FrontendHarness.compile(TWO_KERNELS);
        Set<String> paths = new TreeSet<>(r.resources().keySet());
        assertEquals(3, paths.size(), paths.toString());
        assertTrue(paths.contains(KernelResources.INDEX));
        for (IrModule m : r.modules()) {
            String id = IrBinary.kernelId(m);
            assertEquals(64, id.length());
            assertTrue(paths.contains("META-INF/gpu/ir/" + id + ".gir"), id);
        }
    }

    @Test
    void identicalKernelsShareOneIrFileButBothAreIndexed() {
        FrontendHarness.Outcome r = FrontendHarness.compile("""
                package demo;

                import com.sixik.gpu.api.Gpu;
                import com.sixik.gpu.api.annotations.Global;
                import com.sixik.gpu.api.annotations.Kernel;

                public final class Twins {
                    @Kernel
                    public static void first(@Global float[] a) { a[Gpu.getGlobalId(0)] = 1f; }

                    @Kernel
                    public static void second(@Global float[] a) { a[Gpu.getGlobalId(0)] = 1f; }
                }
                """);
        assertTrue(r.success(), r.describe());
        assertEquals(1, r.modules().size(), "identical IR is written once");
        List<KernelResources.IndexEntry> entries = KernelResources.parseIndex(
                new String(r.resources().get(KernelResources.INDEX), StandardCharsets.UTF_8));
        assertEquals(2, entries.size());
        assertEquals(entries.get(0).kernelId(), entries.get(1).kernelId());
    }

    @Test
    void fullCompilationAlsoWritesClassFilesAndKeepsOriginalMethods() {
        FrontendHarness.Outcome r = FrontendHarness.compile(new GpuProcessor(), List.of(), SmokeTest.COMPUTE_SINE);
        assertTrue(r.success(), r.describe());
        byte[] cls = r.resources().get("demo/Sine.class");
        assertTrue(cls != null && cls.length > 0, "class file written: " + r.resources().keySet());
        assertTrue(new String(cls, StandardCharsets.ISO_8859_1).contains("computeSine"),
                "the original method stays in the class file (the weaver replaces it later)");
        assertEquals(1, r.modules().size());
        assertTrue(r.resources().containsKey(KernelResources.INDEX));
    }

    @Test
    void processorIsDiscoveredThroughServiceLoader() {
        FrontendHarness.Outcome r = FrontendHarness.compileDiscovering(SmokeTest.COMPUTE_SINE);
        assertTrue(r.success(), r.describe());
        assertEquals(1, r.modules().size(), "javac found the processor via META-INF/services: " + r.describe());
        assertTrue(r.resources().containsKey(KernelResources.INDEX));
    }

    @Test
    void sourcesWithoutKernelsProduceNothing() {
        FrontendHarness.Outcome r = FrontendHarness.compile("""
                package demo;

                public final class Plain {
                    static int twice(int x) { return 2 * x; }
                }
                """);
        assertTrue(r.success(), r.describe());
        assertTrue(r.resources().isEmpty());
        assertTrue(r.gpu().isEmpty());
    }

    @Test
    void deviceFunctionsAreCheckedEvenWithoutKernels() {
        FrontendHarness.Outcome r = FrontendHarness.compile("""
                package demo;

                import com.sixik.gpu.api.annotations.DeviceFunction;

                public final class Lib {
                    @DeviceFunction
                    static int f(int x) {
                        try { return x; } finally { }
                    }
                }
                """);
        assertFalse(r.success());
        assertEquals(List.of("GPU0101"), r.errorCodes(), r.describe());
    }

    @Test
    void sharedInvalidHelperIsReportedOnce() {
        FrontendHarness.Outcome r = FrontendHarness.compile("""
                package demo;

                import com.sixik.gpu.api.Gpu;
                import com.sixik.gpu.api.annotations.DeviceFunction;
                import com.sixik.gpu.api.annotations.Global;
                import com.sixik.gpu.api.annotations.Kernel;

                public final class Shared {
                    @DeviceFunction
                    static int bad(int x) { return "s".length() + x; }

                    @Kernel
                    public static void one(@Global int[] a) { a[0] = bad(1); }

                    @Kernel
                    public static void two(@Global int[] a) { a[1] = bad(2); }
                }
                """);
        assertFalse(r.success());
        long count = r.errorCodes().stream().filter(c -> c.equals("GPU0106")).count();
        assertEquals(1, count, "an error in a shared function is reported once: " + r.errorCodes());
        assertTrue(r.resources().isEmpty(), "both kernels depend on the invalid function: no IR");
    }

    @Test
    void indexFormatRoundTripAndErrors() {
        var entries = List.of(
                new KernelResources.IndexEntry("b.B", "m", "()V", "22"),
                new KernelResources.IndexEntry("a.A", "z", "(I)V", "11"),
                new KernelResources.IndexEntry("a.A", "y", "(I)V", "33"));
        String text = KernelResources.formatIndex(entries);
        assertEquals("a.A\ty\t(I)V\t33\na.A\tz\t(I)V\t11\nb.B\tm\t()V\t22\n", text, "sorting: class, method, descriptor");
        assertEquals(KernelResources.parseIndex(text).size(), 3);
        assertThrows(IllegalArgumentException.class, () -> KernelResources.parseIndex("a\tb\tc\n"));
        assertEquals("META-INF/gpu/ir/abc.gir", KernelResources.irPath("abc"));
        assertTrue(KernelResources.parseIndex("").isEmpty());
    }
}
