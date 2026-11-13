package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.frontend.common.KernelResources;
import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.optimizer.OptimizeOptions;
import com.sixik.gpu.ir.optimizer.OptimizeResult;
import com.sixik.gpu.ir.optimizer.Optimizer;
import com.sixik.gpu.tools.IrDump;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Корпус 17.14-1, часть {@code examples/}: исходники примеров ({@code tour}, {@code standalone}) компилируются javac в
 * памяти с процессором {@code frontend-java} (IR_java), и те же class-файлы обрабатывает {@code frontend-asm}.
 */
class ExamplesTest {

    /**
     * Пункт (д) 17.14-3: константы, свёрнутые javac ({@code double s = 0} - уже {@code 0.0}, {@code TILE / 2} - уже
     * {@code 128}); IR совпадает после оптимизатора.
     */
    private static final Set<String> JAVAC_FOLDED = Set.of("BlockSum.groupSums", "Reduce.above", "Reduce.sum");

    private static List<String> sources() throws IOException {
        Path root = Path.of(System.getProperty("gpu.examples.dir"));
        List<String> out = new ArrayList<>();
        for (String dir : List.of("tour/src/main/java", "standalone/src/main/java")) {
            try (Stream<Path> files = Files.walk(root.resolve(dir))) {
                for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                    out.add(Files.readString(f, StandardCharsets.UTF_8));
                }
            }
        }
        return out;
    }

    @Test
    void examplesGiveTheSameIr() throws IOException {
        JavacHarness.Outcome java = JavacHarness.compile(true, null, sources().toArray(String[]::new));
        assertTrue(java.success(), java.log());
        ClassLoader loader = getClass().getClassLoader();
        ClassSource fromLoader = ClassSource.of(loader);
        ClassSource source = name -> {
            byte[] b = java.classes().get(name);
            return b != null ? b : fromLoader.bytes(name);
        };
        List<KernelResources.IndexEntry> entries = KernelResources.parseIndex(
                new String(java.resources().get(KernelResources.INDEX), StandardCharsets.UTF_8));
        assertFalse(entries.isEmpty());
        Set<String> folded = new TreeSet<>();
        int same = 0;
        for (KernelResources.IndexEntry e : entries) {
            String label = e.className().substring(e.className().lastIndexOf('.') + 1) + "." + e.method();
            AsmFrontend.Result r = AsmFrontend.compile(source, List.of(e.className()), AsmFrontend.Options.DEFAULT);
            AsmFrontend.Kernel mine = r.kernels().stream()
                    .filter(k -> k.entry().method().equals(e.method()) && k.entry().descriptor().equals(e.descriptor()))
                    .findFirst().orElse(null);
            assertTrue(mine != null, label + ": " + r.diagnostics());
            IrModule expected = java.module(e.className(), e.method());
            String a = IrDump.dump(expected, false);
            String b = IrDump.dump(mine.module(), false);
            if (JAVAC_FOLDED.contains(label)) {
                assertNotEquals(a, b, label + ": the IR is the same, the kernel must leave JAVAC_FOLDED");
                assertEquals(IrDump.dump(optimized(expected), false), IrDump.dump(optimized(mine.module()), false),
                        label + ": after constant folding");
                folded.add(label);
                continue;
            }
            assertEquals(a, b, label);
            assertEquals(IrBinary.kernelId(expected), mine.entry().kernelId(), label + ": kernelId");
            same++;
        }
        assertEquals(JAVAC_FOLDED, folded);
        assertTrue(same > 0);
    }

    private static IrModule optimized(IrModule m) {
        OptimizeResult r = Optimizer.optimize(m, OptimizeOptions.DEFAULT);
        assertTrue(r.ok(), r.failures().toString());
        return Optimizer.canonical(r.module());
    }
}
