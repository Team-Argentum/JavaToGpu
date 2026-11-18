package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаг B4 на устройстве: программа из трёх ядер с общей функцией {@code __attribute__((noinline))} собирается драйвером
 * одной программой, и каждое ядро даёт тот же результат, что {@code ir-interpreter}. Без драйвера пропускается.
 */
@Tag("device")
class ProgramDeviceTest {

    private static ClDevice device;

    @BeforeAll
    static void open() {
        device = ClDevice.open();
        Assumptions.assumeTrue(device != null, "no OpenCL driver or device");
        System.out.println("OpenCL device: " + device.name());
    }

    @AfterAll
    static void close() {
        if (device != null) {
            device.close();
        }
    }

    @Test
    void threeKernelsOfOneProgramMatchTheInterpreter() {
        IrModule m = ProgramTest.threeKernels();
        Result<Program> r = OpenClEmitter.emitProgram(m, ProgramTest.NV, EmitOptions.RELEASE);
        assertTrue(r.isOk(), r.diagnostics().toString());
        Program p = r.value();
        try (ClDevice.Program built = device.build(p.source(), p.buildOptions())) {
            for (Program.Kernel k : p.kernels()) {
                int[] gpu = new int[64];
                int[] ref = new int[64];
                for (int i = 0; i < gpu.length; i++) {
                    gpu[i] = ref[i] = i * 7 - 100;
                }
                Artifact a = new Artifact(p.source(), k.entry(), k.args(), p.requiredFeatures(), p.buildOptions(),
                        p.target(), p.options());
                device.run(built, a, new Object[]{gpu}, new long[]{gpu.length}, null);
                Result<Interpreter.Execution> ir = Interpreter.run(m, k.function(), NdRange.of1D(ref.length),
                        Interpreter.Options.DEFAULT, new Object[]{ref});
                assertTrue(ir.isOk(), ir.diagnostics().toString());
                assertArrayEquals(ref, gpu, k.entry());
            }
        }
    }
}
