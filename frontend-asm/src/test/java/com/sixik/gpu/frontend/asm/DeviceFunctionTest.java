package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.tools.IrDump;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаг A4 (17.15, 17.11): вызовы {@code @DeviceFunction} - замыкание, функции других классов и из jar, варианты
 * {@code fallback}, порядок функций модуля и диагностики. Эталон - IR и коды {@code frontend-java} на тех же
 * исходниках (один проход javac с процессором).
 */
class DeviceFunctionTest {

    private static final String LIB = """
            package lib;

            import com.sixik.gpu.api.annotations.DeviceFunction;

            public class MathLib {
                @DeviceFunction
                public static int square(int x) {
                    return x * x;
                }

                @DeviceFunction
                public static int clamp(int x, int lo, int hi) {
                    return x < lo ? lo : x > hi ? hi : x;
                }
            }
            """;

    private static final String CALLS = """
            package k;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.DeviceFunction;
            import com.sixik.gpu.api.annotations.Global;
            import com.sixik.gpu.api.annotations.Kernel;
            import lib.MathLib;

            public class Calls {
                @DeviceFunction
                static int inc(int x) {
                    return x + 1;
                }

                @DeviceFunction
                static int twice(int x) {
                    return inc(x) + inc(x) - 2 + x;
                }

                @DeviceFunction
                static int mix(int a, int b) {
                    return a * 31 + b;
                }

                @DeviceFunction
                static int bump(@Global int[] a, int i) {
                    a[i] += 1;
                    return a[i];
                }

                @DeviceFunction
                static void put(@Global int[] a, int i, int v) {
                    a[i] = v;
                }

                @Kernel
                public static void nested(@Global int[] in, @Global int[] out) {
                    int g = Gpu.getGlobalId(0);
                    // порядок исходника: mix, square, twice (в байткоде square и twice раньше mix)
                    int acc = mix(MathLib.square(in[g]), twice(g));
                    // вызов в шаге for: в исходнике раньше тела, в байткоде - после
                    for (int i = 0; i < 3; i = MathLib.clamp(i + 1, 0, 9)) {
                        acc += inc(i);
                    }
                    // значение эффектной функции - временная локаль
                    int s = bump(out, g) + inc(acc);
                    put(out, g, s + acc);
                    twice(s);
                }

                @Kernel(fallback = "plain")
                public static void fancy(@Global int[] in, @Global int[] out) {
                    int g = Gpu.getGlobalId(0);
                    out[g] = mix(in[g], twice(g));
                }

                @Kernel
                public static void plain(@Global int[] in, @Global int[] out) {
                    int g = Gpu.getGlobalId(0);
                    out[g] = inc(in[g]);
                }
            }
            """;

    private static final List<String> KERNELS = List.of("nested", "fancy", "plain");

    private static void assertSameIr(JavacHarness.Outcome java, AsmFrontend.Result asm, String cls, List<String> kernels) {
        assertTrue(asm.ok(), asm.diagnostics().toString());
        for (String k : kernels) {
            IrModule expected = java.module(cls, k);
            AsmFrontend.Kernel mine = asm.kernels().stream().filter(x -> x.entry().method().equals(k)).findFirst()
                    .orElseThrow(() -> new AssertionError(k + " is not built: " + asm.diagnostics()));
            assertEquals(IrDump.dump(expected, false), IrDump.dump(mine.module(), false), k);
            assertEquals(IrBinary.kernelId(expected), mine.entry().kernelId(), k + ": kernelId");
        }
    }

    @Test
    void callsGiveTheSameIrAndFunctionOrder() {
        JavacHarness.Outcome java = JavacHarness.compile(true, null, LIB, CALLS);
        assertTrue(java.success(), java.log());
        AsmFrontend.Result asm = AsmFrontend.compile(ClassSource.of(java.classes()), List.of("k.Calls"),
                AsmFrontend.Options.DEFAULT);
        assertSameIr(java, asm, "k.Calls", KERNELS);
        // fallback - функция вида KERNEL перед ядром; ядро последнее
        IrModule fancy = asm.kernels().stream().filter(x -> x.entry().method().equals("fancy")).findFirst()
                .orElseThrow().module();
        assertEquals(IrModule.KERNEL, fancy.fKind(fancy.functionCount() - 2));
    }

    /** Device-функция из jar без исходников: у {@code frontend-java} это {@code GPU0114}, у фронтенда байткода - тот же IR. */
    @Test
    void deviceFunctionFromJarWithoutSources(@TempDir Path dir) throws IOException {
        JavacHarness.Outcome lib = JavacHarness.compile(false, null, LIB);
        assertTrue(lib.success(), lib.log());
        Path jar = dir.resolve("mathlib.jar");
        try (OutputStream os = Files.newOutputStream(jar); JarOutputStream jos = new JarOutputStream(os)) {
            for (Map.Entry<String, byte[]> e : lib.classes().entrySet()) {
                jos.putNextEntry(new JarEntry(e.getKey() + ".class"));
                jos.write(e.getValue());
                jos.closeEntry();
            }
        }
        JavacHarness.Outcome onlyKernels = JavacHarness.compile(true, jar, CALLS);
        assertTrue(onlyKernels.gpuErrors().contains("GPU0114"), onlyKernels.log());
        JavacHarness.Outcome kernels = JavacHarness.compile(false, jar, CALLS);
        assertTrue(kernels.success(), kernels.log());
        assertFalse(kernels.classes().containsKey("lib/MathLib"));

        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            ClassSource fromJar = ClassSource.of(loader);
            ClassSource source = name -> {
                byte[] b = kernels.classes().get(name);
                return b != null ? b : fromJar.bytes(name);
            };
            AsmFrontend.Result asm = AsmFrontend.compile(source, List.of("k.Calls"), AsmFrontend.Options.DEFAULT);
            JavacHarness.Outcome together = JavacHarness.compile(true, null, LIB, CALLS);
            assertSameIr(together, asm, "k.Calls", KERNELS);
        }
    }

    // ---- диагностики: те же коды, что у frontend-java ---------------------------------------------

    private static TreeSet<String> javaCodes(String src) {
        JavacHarness.Outcome j = JavacHarness.compile(true, null, src);
        return new TreeSet<>(j.gpuErrors());
    }

    private static TreeSet<String> asmCodes(String src, String cls) {
        JavacHarness.Outcome j = JavacHarness.compile(false, null, src);
        assertTrue(j.success(), j.log());
        AsmFrontend.Result r = AsmFrontend.compile(ClassSource.of(j.classes()), List.of(cls), AsmFrontend.Options.DEFAULT);
        TreeSet<String> codes = new TreeSet<>();
        r.diagnostics().stream().filter(AsmDiagnostic::error).forEach(d -> codes.add(d.code()));
        return codes;
    }

    private static void sameCodes(String expected, String src) {
        TreeSet<String> java = javaCodes(src);
        assertEquals(new TreeSet<>(List.of(expected)), java, "frontend-java");
        assertEquals(java, asmCodes(src, "d.D"), "frontend-asm");
    }

    private static String kernelClass(String body) {
        return """
                package d;

                import com.sixik.gpu.api.Gpu;
                import com.sixik.gpu.api.annotations.DeviceFunction;
                import com.sixik.gpu.api.annotations.Global;
                import com.sixik.gpu.api.annotations.Kernel;
                import com.sixik.gpu.api.annotations.Local;
                import com.sixik.gpu.api.annotations.Private;

                public class D {
                """ + body + "\n}\n";
    }

    @Test
    void recursionIsReported() {
        sameCodes("GPU0105", kernelClass("""
                @DeviceFunction
                static int a(int x) {
                    return x > 0 ? b(x - 1) : 0;
                }

                @DeviceFunction
                static int b(int x) {
                    return a(x) + 1;
                }

                @Kernel
                public static void k(@Global int[] out) {
                    out[0] = a(3);
                }
                """));
    }

    @Test
    void callOfMethodWithoutDeviceFunctionIsReported() {
        sameCodes("GPU0113", kernelClass("""
                static int helper(int x) {
                    return x + 1;
                }

                @Kernel
                public static void k(@Global int[] out) {
                    out[0] = helper(3);
                }
                """));
    }

    @Test
    void addressSpaceOfArrayArgumentIsChecked() {
        sameCodes("GPU0121", kernelClass("""
                @DeviceFunction
                static int first(@Global int[] a) {
                    return a[0];
                }

                @Kernel
                public static void k(@Local int[] tmp, @Global int[] out) {
                    out[0] = first(tmp);
                }
                """));
    }

    /** {@code @Private}: массив из тела ядра передаётся в device-функцию по ссылке; IR обоих фронтендов совпадает. */
    @Test
    void privateArrayParameterGivesTheSameIr() {
        String src = kernelClass("""
                @DeviceFunction
                static int push(@Private int[] stack, int top, int v) {
                    stack[top] = v;
                    return top + 1;
                }

                @DeviceFunction
                static int sum(@Private int[] stack, int top) {
                    int s = 0;
                    for (int i = 0; i < top; i++) {
                        s += stack[i] * stack.length;
                    }
                    return s;
                }

                @Kernel
                public static void k(@Global int[] out, @Global int[] in) {
                    int gid = Gpu.getGlobalId(0);
                    int[] stack = new int[8];
                    int top = 0;
                    top = push(stack, top, in[gid]);
                    top = push(stack, top, in[gid] * 2);
                    out[gid] = sum(stack, top) + stack[1];
                }
                """);
        JavacHarness.Outcome java = JavacHarness.compile(true, null, src);
        assertTrue(java.success(), java.log());
        AsmFrontend.Result asm = AsmFrontend.compile(ClassSource.of(java.classes()), List.of("d.D"), AsmFrontend.Options.DEFAULT);
        assertSameIr(java, asm, "d.D", List.of("k"));
    }

    @Test
    void privateMisuseIsReported() {
        sameCodes("GPU0123", kernelClass("""
                @Kernel
                public static void k(@Private int[] tmp, @Global int[] out) {
                    out[0] = 1;
                }
                """));
        sameCodes("GPU0123", kernelClass("""
                @DeviceFunction
                static int f(@Private int x) {
                    return x;
                }

                @Kernel
                public static void k(@Global int[] out) {
                    out[0] = f(1);
                }
                """));
        sameCodes("GPU0123", kernelClass("""
                @DeviceFunction
                static int f(@Private @Local int[] a) {
                    return a[0];
                }

                @Kernel
                public static void k(@Global int[] out) {
                    int[] t = new int[2];
                    out[0] = f(t);
                }
                """));
        // без @Private параметр - global: private-массив туда не передать
        sameCodes("GPU0121", kernelClass("""
                @DeviceFunction
                static int f(int[] a) {
                    return a[0];
                }

                @Kernel
                public static void k(@Global int[] out) {
                    int[] t = new int[2];
                    out[0] = f(t);
                }
                """));
    }

    @Test
    void fallbackErrorsAreReported() {
        sameCodes("GPU0010", kernelClass("""
                @Kernel(fallback = "missing")
                public static void k(@Global int[] out) {
                    out[0] = 1;
                }
                """));
        sameCodes("GPU0011", kernelClass("""
                @Kernel(fallback = "other")
                public static void k(@Global int[] out) {
                    out[0] = 1;
                }

                @Kernel
                public static void other(@Global float[] out) {
                    out[0] = 1;
                }
                """));
        sameCodes("GPU0014", kernelClass("""
                @Kernel(fallback = "b")
                public static void a(@Global int[] out) {
                    out[0] = 1;
                }

                @Kernel(fallback = "a")
                public static void b(@Global int[] out) {
                    out[0] = 2;
                }
                """));
    }
}
