package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden-тесты: текст OpenCL C для каждого образца и каждого ручного модуля, отдельно release и debug.
 * Golden просмотрены вручную построчно (см. ROADMAP.md, M5); изменение текста генератора обязано менять
 * golden осознанно.
 */
class GoldenTest {

    /** Все проверяемые модули по именам golden-файлов. */
    static Map<String, IrModule> modules() {
        Map<String, IrModule> m = new LinkedHashMap<>();
        m.put("computeSine", Fixtures.computeSine());
        for (SampleKernels.Kernel k : SampleKernels.all()) {
            m.put(k.label(), k.module());
        }
        m.putAll(HandModules.all());
        return m;
    }

    static Stream<String> names() {
        return modules().keySet().stream();
    }

    @ParameterizedTest(name = "release {0}")
    @MethodSource("names")
    void release(String name) {
        check(name, EmitOptions.RELEASE, "release");
    }

    @ParameterizedTest(name = "debug {0}")
    @MethodSource("names")
    void debug(String name) {
        check(name, EmitOptions.DEBUG, "debug");
    }

    private static void check(String name, EmitOptions options, String mode) {
        IrModule m = modules().get(name);
        Result<Artifact> r = OpenClEmitter.emit(m, m.functionCount() - 1, Target.GENERIC, options);
        assertTrue(r.isOk(), name + ": " + r.diagnostics());
        Golden.check(Target.GENERIC, name + "." + mode + ".cl", r.value().source());
    }
}
