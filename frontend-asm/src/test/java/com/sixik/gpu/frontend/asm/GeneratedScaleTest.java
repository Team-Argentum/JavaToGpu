package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.ir.optimizer.OptimizeOptions;
import com.sixik.gpu.ir.optimizer.Optimizer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

/**
 * Замер B7 на байткоде генератора ({@link GeneratedShapes}): время фронтенда и оптимизатора и глубина, на которой
 * исчерпывается стек. Печатает таблицу при {@code -Dprobe.gen=true}; размеры - {@code -Dprobe.sizes}, формы -
 * {@code -Dprobe.shapes} ({@code select,if,while,router}). Кроме фронтенда меряются оптимизатор, генератор и интерпретатор.
 */
class GeneratedScaleTest {

    private record Shape(String name, IntFunction<ClassNode> gen) {
    }

    @Test
    void probe() {
        if (!Boolean.getBoolean("probe.gen")) {
            return;
        }
        String custom = System.getProperty("probe.sizes", "256,1024,4096");
        int[] sizes = java.util.Arrays.stream(custom.split(",")).mapToInt(v -> Integer.parseInt(v.trim())).toArray();
        List<Shape> shapes = List.of(new Shape("select", GeneratedShapes::nestedSelect),
                new Shape("if", GeneratedShapes::nestedIf), new Shape("while", GeneratedShapes::nestedLoops),
                new Shape("router", GeneratedShapes::router));
        StringBuilder t = new StringBuilder();
        String only = System.getProperty("probe.shapes");
        for (Shape s : shapes) {
            if (only != null && !java.util.Arrays.asList(only.split(",")).contains(s.name())) {
                continue;
            }
            for (int n : sizes) {
                long t0 = System.nanoTime();
                String verdict;
                long tOpt = -1;
                try {
                    AsmJit.Result r = AsmJit.compile(GeneratedShapes.unit(s.gen().apply(n)), GeneratedShapes.SYNTH);
                    verdict = r.ok() ? "ok" : r.diagnostics().toString();
                    if (r.ok()) {
                        long t1 = System.nanoTime();
                        try {
                            var o = Optimizer.optimize(r.module(), OptimizeOptions.DEFAULT.withTimeBudget(null));
                            tOpt = System.nanoTime() - t1;
                            verdict = "ok opt=" + o.level() + " " + o.failures();
                            var art = com.sixik.gpu.codegen.opencl.OpenClEmitter.emit(o.module(),
                                    com.sixik.gpu.codegen.opencl.Target.GENERIC);
                            verdict += art.isOk() ? " emit ok" : " emit " + art.diagnostics();
                            var run = com.sixik.gpu.ir.interpreter.Interpreter.run(o.module(),
                                    com.sixik.gpu.ir.interpreter.NdRange.of1D(4), new float[4], new float[4]);
                            verdict += run.isOk() ? " run ok" : " run " + run.diagnostics();
                        } catch (RuntimeException | StackOverflowError e) {
                            java.util.Set<String> cycle = new java.util.LinkedHashSet<>();
                            for (StackTraceElement el : e.getStackTrace()) {
                                cycle.add(el.getClassName().substring(el.getClassName().lastIndexOf('.') + 1) + "."
                                        + el.getMethodName());
                            }
                            verdict = "after frontend: " + e + " " + cycle;
                        }
                    }
                } catch (OutOfMemoryError e) {
                    StringBuilder sb = new StringBuilder("out of memory");
                    StackTraceElement[] st = e.getStackTrace();
                    for (int i = 0; i < Math.min(40, st.length); i++) {
                        sb.append(" < ").append(st[i]);
                    }
                    verdict = sb.toString();
                } catch (StackOverflowError e) {
                    java.util.Set<String> cycle = new java.util.LinkedHashSet<>();
                    for (StackTraceElement el : e.getStackTrace()) {
                        cycle.add(el.getClassName().substring(el.getClassName().lastIndexOf('.') + 1) + "."
                                + el.getMethodName());
                    }
                    verdict = "stack overflow " + cycle;
                }
                long tAll = System.nanoTime() - t0;
                t.append(String.format(Locale.ROOT, "%-8s %6d %10.1f %10.1f  %s%n", s.name(), n,
                        (tAll - Math.max(tOpt, 0)) / 1e6, tOpt / 1e6,
                        verdict.length() > 6000 ? verdict.substring(0, 6000) : verdict));
            }
        }
        System.out.print(t);
    }
}
