package net.sixik.ga_utils.javatogpu.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Noise-aware summary of bounded timing samples using a trimmed median.
 */
public record GpuRuntimeDeviceSelfTestSampleSummary(
        long medianNanos,
        long minNanos,
        long maxNanos,
        int sampleCount,
        int acceptedSampleCount,
        int discardedSampleCount,
        int noisePermille,
        boolean stable
) {

    public GpuRuntimeDeviceSelfTestSampleSummary {
        medianNanos = Math.max(0L, medianNanos);
        minNanos = Math.max(0L, minNanos);
        maxNanos = Math.max(0L, maxNanos);
        sampleCount = Math.max(0, sampleCount);
        acceptedSampleCount = Math.max(0, acceptedSampleCount);
        discardedSampleCount = Math.max(0, discardedSampleCount);
        noisePermille = Math.max(0, noisePermille);
    }

    public static GpuRuntimeDeviceSelfTestSampleSummary summarize(
            List<Long> samples,
            int maxNoisePermille
    ) {
        ArrayList<Long> sorted = new ArrayList<>();
        if (samples != null) {
            samples.stream()
                    .filter(value -> value != null && value > 0L)
                    .sorted(Comparator.naturalOrder())
                    .forEach(sorted::add);
        }
        if (sorted.isEmpty()) {
            return unavailable();
        }

        int trim = sorted.size() >= 5 ? 1 : 0;
        List<Long> accepted = sorted.subList(trim, sorted.size() - trim);
        long median = accepted.get(accepted.size() / 2);
        long min = accepted.get(0);
        long max = accepted.get(accepted.size() - 1);
        long spread = Math.max(0L, max - min);
        int noise = median <= 0L
                ? Integer.MAX_VALUE
                : (int) Math.min(Integer.MAX_VALUE, spread * 1_000L / median);
        boolean stable = accepted.size() >= 3 && noise <= Math.max(0, maxNoisePermille);
        return new GpuRuntimeDeviceSelfTestSampleSummary(
                median,
                min,
                max,
                sorted.size(),
                accepted.size(),
                sorted.size() - accepted.size(),
                noise,
                stable
        );
    }

    public static GpuRuntimeDeviceSelfTestSampleSummary unavailable() {
        return new GpuRuntimeDeviceSelfTestSampleSummary(0L, 0L, 0L, 0, 0, 0, 0, false);
    }

    public boolean available() {
        return medianNanos > 0L && acceptedSampleCount > 0;
    }
}
