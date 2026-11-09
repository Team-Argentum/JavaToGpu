package com.sixik.gpu.frontend.java;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Validator;
import com.sixik.gpu.tools.IrDump;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Сравнение дампа IR образца с golden-файлом из {@code src/test/resources/golden}.
 *
 * <p>При расхождении (или отсутствии эталона) фактический вывод записывается в {@code build/golden-actual/<имя>}: после
 * осознанной проверки его копируют на место эталона. Эталоны обновляются только вручную (REWORK_ARCHITECTURE.md,
 * разд. 13).</p>
 */
final class Golden {

    private Golden() {
    }

    /**
     * Компилирует образец, проверяет отсутствие диагностик и валидность каждого модуля и сравнивает дамп всех ядер
     * (по именам, с отладочной информацией) с golden-файлом.
     *
     * @param golden имя golden-файла
     * @param source исходник образца
     */
    static void assertLowers(String golden, String source) {
        FrontendHarness.Outcome r = FrontendHarness.compile(source);
        assertTrue(r.success(), r.describe());
        assertTrue(r.gpu().isEmpty(), "expected no GPU diagnostics:\n" + r.describe());
        assertMatches(golden, dumpAll(r));
    }

    /**
     * @param r итог компиляции
     * @return дампы всех ядер по возрастанию имени ядра
     */
    static String dumpAll(FrontendHarness.Outcome r) {
        Map<String, IrModule> byName = new TreeMap<>();
        for (IrModule m : r.modules()) {
            assertFalse(Validator.validate(m).hasErrors(), "module failed the validator: " + Validator.validate(m).diagnostics());
            byName.put(m.debug().functionName(m.functionCount() - 1), m);
        }
        StringBuilder sb = new StringBuilder();
        for (var e : byName.entrySet()) {
            sb.append("=== ").append(e.getKey()).append(" ===\n").append(IrDump.dump(e.getValue(), true)).append('\n');
        }
        return sb.toString();
    }

    static void assertMatches(String name, String actual) {
        String expected;
        try (InputStream in = Golden.class.getResourceAsStream("/golden/" + name)) {
            if (in == null) {
                writeActual(name, actual);
                fail("no golden file " + name + "; actual output written to build/golden-actual/" + name);
                return;
            }
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        if (!expected.equals(actual)) {
            writeActual(name, actual);
            assertEquals(expected, actual, "golden " + name + " did not match; actual output is in build/golden-actual/" + name);
        }
    }

    private static void writeActual(String name, String actual) {
        try {
            Path dir = Path.of("build", "golden-actual");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name), actual, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
