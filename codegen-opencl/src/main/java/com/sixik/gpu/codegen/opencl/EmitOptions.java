package com.sixik.gpu.codegen.opencl;

/**
 * Параметры генерации (REWORK_ARCHITECTURE.md, 8.A: 8.1, 8.3).
 *
 * <ul>
 *   <li>{@code debug} - читаемый режим: имена функций, параметров и локалей берутся из debug-секции IR, а перед
 *       каждым стейтментом с известной позицией пишется {@code #line N}. В release-режиме ничего этого нет
 *       (имена {@code k0}, {@code f1}, {@code p0}, {@code v2}), поэтому текст и ключ кэша бинаря драйвера не
 *       зависят от отладочных данных;</li>
 *   <li>{@code fastMath} - снимает строгость float (8.3): в текст не пишется {@code FP_CONTRACT OFF}, а в
 *       {@link Artifact#buildOptions()} попадает {@code -cl-fast-relaxed-math}. Входит в ключ кэша;</li>
 *   <li>{@code sourceName} - имя исходного файла для {@code #line N "имя"}; {@code null} - писать {@code #line N}
 *       без имени (в debug-секции IR имени файла нет). В release не используется.</li>
 * </ul>
 *
 * @param debug      читаемый режим с именами и {@code #line}
 * @param fastMath   разрешить быстрые, менее точные вычисления
 * @param sourceName имя исходного файла для {@code #line} либо {@code null}
 */
public record EmitOptions(boolean debug, boolean fastMath, String sourceName) {

    /** Release: без имён и {@code #line}, строгий float. */
    public static final EmitOptions RELEASE = new EmitOptions(false, false, null);

    /** Debug: имена и {@code #line}, строгий float. */
    public static final EmitOptions DEBUG = new EmitOptions(true, false, null);

    /**
     * @param fastMath значение {@code fastMath}
     * @return копия с заданным {@code fastMath}
     */
    public EmitOptions withFastMath(boolean fastMath) {
        return new EmitOptions(debug, fastMath, sourceName);
    }

    /**
     * @param sourceName значение {@code sourceName}
     * @return копия с заданным именем исходника
     */
    public EmitOptions withSourceName(String sourceName) {
        return new EmitOptions(debug, fastMath, sourceName);
    }
}
