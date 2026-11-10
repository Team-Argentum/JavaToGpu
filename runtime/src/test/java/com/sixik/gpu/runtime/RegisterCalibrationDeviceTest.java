package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import com.sixik.gpu.ir.optimizer.OptimizeOptions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Калибровка оценки давления на регистры ({@code ir-optimizer}, {@code RegisterPressure}) по данным драйвера NVIDIA.
 *
 * <p>Каждое ядро (образцы, ручные фикстуры, синтетические ядра с {@code k} одновременно живыми значениями) собирается
 * настоящим драйвером дважды - без оптимизатора и с ним; оценка сравнивается с числом регистров, которое напечатал
 * {@code ptxas}. Таблица печатается и пишется в {@code build/reports/register-calibration.csv}. Тест не проверяет
 * точность модели (это исследование), только что драйвер сообщает данные. Запуск: {@code ./gradlew :runtime:deviceTest}.</p>
 */
@Tag("device")
class RegisterCalibrationDeviceTest {

    private static final int SIN = Builtins.idOf("sin");
    private static final int GID = Builtins.idOf("getGlobalId");

    /** Строка отчёта. */
    record Row(String kernel, String mode, int estimate, int driver, int spillBytes, long privateBytes) {
    }

    /**
     * {@code k} значений {@code v_i = sin(in[g*64+i])} живут одновременно, затем складываются в обратном порядке:
     * регистров нужно порядка {@code k}.
     */
    static IrModule liveValues(int k, int type) {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, type);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr);
        b.nameFunction("live" + k + (type == Types.F64 ? "d" : "f"));
        List<Integer> st = new ArrayList<>();
        int g = b.declareLocal(Types.I32);
        st.add(b.localDecl(g, b.intrinsic(GID, b.constI32(0))));
        int[] v = new int[k];
        for (int i = 0; i < k; i++) {
            v[i] = b.declareLocal(type);
            int idx = b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(g), b.constI32(64)), b.constI32(i));
            st.add(b.localDecl(v[i], b.intrinsic(SIN, b.load(b.elemPtr(b.param(0), idx)))));
        }
        int acc = b.localGet(v[k - 1]);
        for (int i = k - 2; i >= 0; i--) {
            // смешиваем умножением, чтобы компилятор не переставил сложения в цепочку по мере загрузки
            acc = b.binary(Op.ADD, b.binary(Op.MUL, acc, b.localGet(v[i])), b.localGet(v[(i * 7) % k]));
        }
        st.add(b.store(b.elemPtr(b.param(1), b.localGet(g)), acc));
        b.endFunction(b.block(st.stream().mapToInt(Integer::intValue).toArray()));
        return b.build();
    }

    private static List<Object[]> kernels() {
        List<Object[]> out = new ArrayList<>();
        for (SampleKernels.Kernel k : SampleKernels.all()) {
            out.add(new Object[]{k.label(), k.module()});
        }
        out.add(new Object[]{"Fixtures.rich", Fixtures.rich()});
        out.add(new Object[]{"Fixtures.computeSine", Fixtures.computeSine()});
        for (int k : new int[]{2, 4, 8, 16, 24, 32, 48, 64, 96, 128}) {
            out.add(new Object[]{"live" + k + "f", liveValues(k, Types.F32)});
        }
        for (int k : new int[]{4, 16, 32, 64}) {
            out.add(new Object[]{"live" + k + "d", liveValues(k, Types.F64)});
        }
        return out;
    }

    @Test
    void estimateVersusDriver() throws IOException {
        List<Row> rows = new ArrayList<>();
        String deviceName;
        for (String mode : new String[]{"plain", "optimized"}) {
            OptimizeOptions o = mode.equals("plain") ? OptimizeOptions.NONE : OptimizeOptions.DEFAULT;
            Result<GpuContext> rc = GpuContext.builder().ignoreEnvironment().noDiskCache().optimize(o).tryBuild();
            Assumptions.assumeTrue(rc.isOk(), "no OpenCL device");
            try (GpuContext ctx = rc.value()) {
                deviceName = ctx.device().describe();
                Assumptions.assumeTrue(deviceName.toUpperCase(Locale.ROOT).contains("NVIDIA"),
                        "register counts are reported only by the NVIDIA driver: " + deviceName);
                for (Object[] k : kernels()) {
                    IrModule m = (IrModule) k[1];
                    Result<CompiledKernel> r = ctx.compile(m);
                    if (!r.isOk()) {
                        continue; // образцы, которые устройство не поддерживает (например, fp64)
                    }
                    CompiledKernel ck = r.value();
                    DriverStats s = ck.driverStats();
                    int spill = s.spillStoreBytes() < 0 ? -1 : s.spillStoreBytes() + s.spillLoadBytes();
                    rows.add(new Row((String) k[0], mode, ck.estimatedRegisters(), s.registers(), spill,
                            s.privateBytes()));
                }
            }
        }
        report(rows);
        assertTrue(rows.stream().anyMatch(r -> r.driver() > 0), "the driver reported no register counts");
    }

    private static void report(List<Row> rows) throws IOException {
        StringBuilder csv = new StringBuilder("kernel,mode,estimate,driver,ratio,spill_bytes,private_bytes\n");
        System.out.printf(Locale.ROOT, "%-34s %-9s %8s %6s %6s %6s %8s%n", "kernel", "mode", "estimate", "driver",
                "ratio", "spill", "private");
        double sumRatio = 0;
        double sumAbs = 0;
        int n = 0;
        for (Row r : rows) {
            double ratio = r.estimate() > 0 && r.driver() > 0 ? (double) r.driver() / r.estimate() : Double.NaN;
            System.out.printf(Locale.ROOT, "%-34s %-9s %8d %6d %6.2f %6d %8d%n", r.kernel(), r.mode(), r.estimate(),
                    r.driver(), ratio, r.spillBytes(), r.privateBytes());
            csv.append(String.format(Locale.ROOT, "%s,%s,%d,%d,%.3f,%d,%d%n", r.kernel(), r.mode(), r.estimate(),
                    r.driver(), ratio, r.spillBytes(), r.privateBytes()));
            if (!Double.isNaN(ratio)) {
                sumRatio += ratio;
                sumAbs += Math.abs(r.driver() - r.estimate());
                n++;
            }
        }
        if (n > 0) {
            System.out.printf(Locale.ROOT, "kernels %d, mean driver/estimate %.2f, mean |driver - estimate| %.1f%n", n,
                    sumRatio / n, sumAbs / n);
        }
        Path out = Path.of("build", "reports", "register-calibration.csv");
        Files.createDirectories(out.getParent());
        Files.writeString(out, csv, StandardCharsets.UTF_8);
    }
}
