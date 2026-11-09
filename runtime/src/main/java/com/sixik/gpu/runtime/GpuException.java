package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.Diagnostic;

import java.util.List;

/**
 * Ошибка на границе пользовательского API (REWORK_ARCHITECTURE.md, принцип «ошибки - значения»): внутри рантайма
 * ошибки возвращаются как {@link com.sixik.gpu.ir.Result} с диагностиками, а методы, которыми пользуются напрямую
 * ({@link GpuContext#run}, {@link GpuContext#allocate}), превращают их в это исключение.
 */
public final class GpuException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<Diagnostic> diagnostics;

    /**
     * @param diagnostics причины; не пусто
     */
    public GpuException(List<Diagnostic> diagnostics) {
        super(describe(diagnostics));
        this.diagnostics = List.copyOf(diagnostics);
    }

    /** @return причины отказа в порядке обнаружения */
    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    private static String describe(List<Diagnostic> diagnostics) {
        if (diagnostics.isEmpty()) {
            return "runtime error";
        }
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : diagnostics) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(d.code()).append(": ").append(d.message());
        }
        return sb.toString();
    }
}
