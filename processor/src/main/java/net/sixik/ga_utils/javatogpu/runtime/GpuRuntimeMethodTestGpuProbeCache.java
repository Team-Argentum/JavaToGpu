package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Cache for bounded {@code @GPUTest} GPU probe execution evidence.
 */
public final class GpuRuntimeMethodTestGpuProbeCache {

    private static final String PERSISTENT_FORMAT_VERSION = "javatogpu.method-test-gpu-probe-cache-entry.v1";

    private static final GpuRuntimeMethodTestGpuProbeCache SHARED = new GpuRuntimeMethodTestGpuProbeCache();

    private final ConcurrentHashMap<String, GpuRuntimeMethodTestGpuProbeExecution> executions =
            new ConcurrentHashMap<>();
    private final Path persistentDirectory;
    private final Duration maxEntryAge;

    public GpuRuntimeMethodTestGpuProbeCache() {
        this(null, null);
    }

    private GpuRuntimeMethodTestGpuProbeCache(Path persistentDirectory, Duration maxEntryAge) {
        this.persistentDirectory = persistentDirectory == null ? null : persistentDirectory.toAbsolutePath().normalize();
        this.maxEntryAge = maxEntryAge == null || maxEntryAge.isNegative() || maxEntryAge.isZero() ? null : maxEntryAge;
    }

    public static GpuRuntimeMethodTestGpuProbeCache shared() {
        return SHARED;
    }

    public static GpuRuntimeMethodTestGpuProbeCache persistent(Path directory) {
        return persistent(directory, null);
    }

    public static GpuRuntimeMethodTestGpuProbeCache persistent(Path directory, Duration maxEntryAge) {
        return new GpuRuntimeMethodTestGpuProbeCache(Objects.requireNonNull(directory, "directory"), maxEntryAge);
    }

    public GpuRuntimeMethodTestGpuProbeCacheEntry getOrRun(
            GpuRuntimeMethodTestGpuProbeEvidenceKey key,
            Supplier<GpuRuntimeMethodTestGpuProbeExecution> supplier
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(supplier, "supplier");
        GpuRuntimeMethodTestGpuProbeExecution cached = executions.get(key.stableHash());
        if (cached != null) {
            return new GpuRuntimeMethodTestGpuProbeCacheEntry(cached, true);
        }

        GpuRuntimeMethodTestGpuProbeCacheEntry persistentEntry = readPersistentEntry(key);
        if (persistentEntry != null) {
            executions.putIfAbsent(key.stableHash(), persistentEntry.execution().withCacheHit(false));
            return persistentEntry;
        }

        GpuRuntimeMethodTestGpuProbeExecution produced = Objects.requireNonNull(supplier.get(), "probe execution");
        validateKey(key, produced);
        if (!produced.executionReady()) {
            return new GpuRuntimeMethodTestGpuProbeCacheEntry(produced, false);
        }

        GpuRuntimeMethodTestGpuProbeExecution prior = executions.putIfAbsent(
                key.stableHash(),
                produced.withCacheHit(false)
        );
        if (prior == null) {
            writePersistentEntry(key, produced.withCacheHit(false));
        }
        return prior == null
                ? new GpuRuntimeMethodTestGpuProbeCacheEntry(produced, false)
                : new GpuRuntimeMethodTestGpuProbeCacheEntry(prior, true);
    }

    public void record(GpuRuntimeMethodTestGpuProbeExecution execution) {
        GpuRuntimeMethodTestGpuProbeExecution value = Objects.requireNonNull(execution, "execution");
        GpuRuntimeMethodTestGpuProbeEvidenceKey key = Objects.requireNonNull(value.evidenceKey(), "execution.evidenceKey");
        if (!value.executionReady()) {
            throw new IllegalArgumentException("Only executed GPU probe evidence can be cached: " + key.stableHash());
        }
        executions.put(key.stableHash(), value.withCacheHit(false));
        writePersistentEntry(key, value.withCacheHit(false));
    }

    public GpuRuntimeMethodTestGpuProbeCacheEntry get(GpuRuntimeMethodTestGpuProbeEvidenceKey key) {
        Objects.requireNonNull(key, "key");
        GpuRuntimeMethodTestGpuProbeExecution execution = executions.get(key.stableHash());
        if (execution != null) {
            return new GpuRuntimeMethodTestGpuProbeCacheEntry(execution, true);
        }
        GpuRuntimeMethodTestGpuProbeCacheEntry persistentEntry = readPersistentEntry(key);
        if (persistentEntry != null) {
            executions.putIfAbsent(key.stableHash(), persistentEntry.execution().withCacheHit(false));
        }
        return persistentEntry;
    }

    public List<GpuRuntimeMethodTestGpuProbeExecution> executions() {
        return executions.values().stream()
                .map(execution -> execution.withCacheHit(false))
                .toList();
    }

    public int size() {
        return executions.size();
    }

    public void clear() {
        executions.clear();
        clearPersistentEntries();
    }

    public boolean persistent() {
        return persistentDirectory != null;
    }

    public Path persistentDirectory() {
        return persistentDirectory;
    }

    private static void validateKey(
            GpuRuntimeMethodTestGpuProbeEvidenceKey requestedKey,
            GpuRuntimeMethodTestGpuProbeExecution execution
    ) {
        GpuRuntimeMethodTestGpuProbeEvidenceKey actualKey = Objects.requireNonNull(
                execution.evidenceKey(),
                "execution.evidenceKey"
        );
        if (!requestedKey.stableHash().equals(actualKey.stableHash())) {
            throw new IllegalArgumentException("GPU probe cache key mismatch: requested="
                    + requestedKey.stableHash() + ", actual=" + actualKey.stableHash());
        }
    }

    private GpuRuntimeMethodTestGpuProbeCacheEntry readPersistentEntry(GpuRuntimeMethodTestGpuProbeEvidenceKey key) {
        if (persistentDirectory == null) {
            return null;
        }
        Path path = persistentEntryPath(key);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            GpuRuntimeMethodTestGpuProbeExecution execution = readExecution(key, properties);
            if (execution == null) {
                return null;
            }
            return new GpuRuntimeMethodTestGpuProbeCacheEntry(execution, true);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private GpuRuntimeMethodTestGpuProbeExecution readExecution(
            GpuRuntimeMethodTestGpuProbeEvidenceKey requestedKey,
            Properties properties
    ) {
        if (!PERSISTENT_FORMAT_VERSION.equals(properties.getProperty("formatVersion"))) {
            return null;
        }
        if (!requestedKey.stableHash().equals(properties.getProperty("stableHash"))) {
            return null;
        }
        long createdEpochMillis = parseLong(properties, "createdEpochMillis", -1L);
        if (createdEpochMillis <= 0L || expired(createdEpochMillis)) {
            return null;
        }
        GpuRuntimeMethodTestGpuProbeEvidenceKey evidenceKey = readEvidenceKey(properties);
        if (!requestedKey.stableHash().equals(evidenceKey.stableHash())) {
            return null;
        }
        GpuExecutionConfig executionConfig = readExecutionConfig(properties, "execution.executionConfig");
        if (executionConfig == null) {
            return null;
        }
        int comparisonCount = parseInt(properties, "execution.comparison.count", -1);
        if (comparisonCount < 0) {
            return null;
        }
        ArrayList<GpuRuntimeMethodTestReferenceComparison> comparisons = new ArrayList<>();
        for (int index = 0; index < comparisonCount; index++) {
            comparisons.add(readComparison(properties, "execution.comparison." + index));
        }
        GpuRuntimeMethodTestGpuProbeExecution execution = new GpuRuntimeMethodTestGpuProbeExecution(
                properties.getProperty("execution.testId", evidenceKey.testId()),
                evidenceKey,
                false,
                Boolean.parseBoolean(properties.getProperty("execution.executionReady", "false")),
                Boolean.parseBoolean(properties.getProperty("execution.executionPassed", "false")),
                executionConfig,
                comparisons,
                readList(properties, "execution.blocker"),
                readList(properties, "execution.diagnostic")
        );
        return execution.executionReady() ? execution : null;
    }

    private GpuRuntimeMethodTestGpuProbeEvidenceKey readEvidenceKey(Properties properties) {
        return new GpuRuntimeMethodTestGpuProbeEvidenceKey(
                properties.getProperty("evidence.formatVersion"),
                properties.getProperty("evidence.testId"),
                properties.getProperty("evidence.kernelName"),
                properties.getProperty("evidence.kernelResource"),
                properties.getProperty("evidence.irGpuResource"),
                properties.getProperty("evidence.kernelSource.sha256"),
                properties.getProperty("evidence.fixtureInput.fingerprint"),
                properties.getProperty("evidence.expectedOutput.fingerprint"),
                properties.getProperty("evidence.executionConfig.fingerprint"),
                enumValue(GpuBackendTarget.class, properties.getProperty("evidence.backendTarget"), GpuBackendTarget.UNKNOWN),
                properties.getProperty("evidence.backendName"),
                properties.getProperty("evidence.deviceId"),
                properties.getProperty("evidence.deviceLabel"),
                properties.getProperty("evidence.vendor"),
                properties.getProperty("evidence.driverVersion"),
                properties.getProperty("evidence.apiVersionText"),
                properties.getProperty("evidence.optimizationProfile"),
                properties.getProperty("evidence.compileOptions.fingerprint"),
                properties.getProperty("evidence.compilerIdentity")
        );
    }

    private GpuExecutionConfig readExecutionConfig(Properties properties, String prefix) {
        if (!Boolean.parseBoolean(properties.getProperty(prefix + ".present", "false"))) {
            return null;
        }
        return new GpuExecutionConfig(
                parseInt(properties, prefix + ".dimensions", -1),
                parseLong(properties, prefix + ".globalX", -1L),
                parseLong(properties, prefix + ".globalY", -1L),
                parseLong(properties, prefix + ".globalZ", -1L),
                parseLong(properties, prefix + ".localX", -1L),
                parseLong(properties, prefix + ".localY", -1L),
                parseLong(properties, prefix + ".localZ", -1L)
        );
    }

    private GpuRuntimeMethodTestReferenceComparison readComparison(Properties properties, String prefix) {
        return new GpuRuntimeMethodTestReferenceComparison(
                properties.getProperty(prefix + ".testId"),
                parseInt(properties, prefix + ".parameter.index", -1),
                properties.getProperty(prefix + ".parameter.name"),
                properties.getProperty(prefix + ".javaType"),
                enumValue(GpuKernelParameterAccess.class, properties.getProperty(prefix + ".access"), GpuKernelParameterAccess.VALUE),
                Boolean.parseBoolean(properties.getProperty(prefix + ".comparisonReady", "false")),
                Boolean.parseBoolean(properties.getProperty(prefix + ".passed", "false")),
                properties.getProperty(prefix + ".actual.kind"),
                parseInt(properties, prefix + ".actual.item.count", -1),
                readList(properties, prefix + ".actual.numericValue"),
                properties.getProperty(prefix + ".expected.kind"),
                parseInt(properties, prefix + ".expected.item.count", -1),
                readList(properties, prefix + ".expected.numericValue"),
                parseDouble(properties, prefix + ".tolerance.absolute", 0.0d),
                parseDouble(properties, prefix + ".tolerance.relative", 0.0d),
                properties.getProperty(prefix + ".blocker"),
                properties.getProperty(prefix + ".failure"),
                properties.getProperty(prefix + ".diagnostic")
        );
    }

    private void writePersistentEntry(
            GpuRuntimeMethodTestGpuProbeEvidenceKey key,
            GpuRuntimeMethodTestGpuProbeExecution execution
    ) {
        if (persistentDirectory == null || !execution.executionReady()) {
            return;
        }
        try {
            Files.createDirectories(persistentDirectory);
            Properties properties = new Properties();
            writeExecution(properties, key, execution.withCacheHit(false));
            Path target = persistentEntryPath(key);
            Path temp = persistentDirectory.resolve(target.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                properties.store(writer, "JavaToGpu @GPUTest GPU probe cache entry");
            }
            moveReplacing(temp, target);
        } catch (IOException | RuntimeException exception) {
            // Cache persistence is an optimization; execution evidence remains valid in memory.
        }
    }

    private void writeExecution(
            Properties properties,
            GpuRuntimeMethodTestGpuProbeEvidenceKey key,
            GpuRuntimeMethodTestGpuProbeExecution execution
    ) {
        properties.setProperty("formatVersion", PERSISTENT_FORMAT_VERSION);
        properties.setProperty("createdEpochMillis", Long.toString(System.currentTimeMillis()));
        properties.setProperty("stableHash", key.stableHash());
        writeEvidenceKey(properties, execution.evidenceKey());
        properties.setProperty("execution.testId", execution.testId());
        properties.setProperty("execution.executionReady", Boolean.toString(execution.executionReady()));
        properties.setProperty("execution.executionPassed", Boolean.toString(execution.executionPassed()));
        writeExecutionConfig(properties, "execution.executionConfig", execution.executionConfig());
        properties.setProperty("execution.comparison.count", Integer.toString(execution.comparisons().size()));
        for (int index = 0; index < execution.comparisons().size(); index++) {
            writeComparison(properties, "execution.comparison." + index, execution.comparisons().get(index));
        }
        writeList(properties, "execution.blocker", execution.blockers());
        writeList(properties, "execution.diagnostic", execution.diagnostics());
    }

    private void writeEvidenceKey(Properties properties, GpuRuntimeMethodTestGpuProbeEvidenceKey evidenceKey) {
        properties.setProperty("evidence.formatVersion", evidenceKey.formatVersion());
        properties.setProperty("evidence.testId", evidenceKey.testId());
        properties.setProperty("evidence.kernelName", evidenceKey.kernelName());
        properties.setProperty("evidence.kernelResource", evidenceKey.kernelResource());
        properties.setProperty("evidence.irGpuResource", evidenceKey.irGpuResource());
        properties.setProperty("evidence.kernelSource.sha256", evidenceKey.kernelSourceSha256());
        properties.setProperty("evidence.fixtureInput.fingerprint", evidenceKey.fixtureInputFingerprint());
        properties.setProperty("evidence.expectedOutput.fingerprint", evidenceKey.expectedOutputFingerprint());
        properties.setProperty("evidence.executionConfig.fingerprint", evidenceKey.executionConfigFingerprint());
        properties.setProperty("evidence.backendTarget", evidenceKey.backendTarget().name());
        properties.setProperty("evidence.backendName", evidenceKey.backendName());
        properties.setProperty("evidence.deviceId", evidenceKey.deviceId());
        properties.setProperty("evidence.deviceLabel", evidenceKey.deviceLabel());
        properties.setProperty("evidence.vendor", evidenceKey.vendor());
        properties.setProperty("evidence.driverVersion", evidenceKey.driverVersion());
        properties.setProperty("evidence.apiVersionText", evidenceKey.apiVersionText());
        properties.setProperty("evidence.optimizationProfile", evidenceKey.optimizationProfile());
        properties.setProperty("evidence.compileOptions.fingerprint", evidenceKey.compileOptionsFingerprint());
        properties.setProperty("evidence.compilerIdentity", evidenceKey.compilerIdentity());
    }

    private void writeExecutionConfig(Properties properties, String prefix, GpuExecutionConfig config) {
        properties.setProperty(prefix + ".present", Boolean.toString(config != null));
        if (config == null) {
            return;
        }
        properties.setProperty(prefix + ".dimensions", Integer.toString(config.dimensions()));
        properties.setProperty(prefix + ".globalX", Long.toString(config.globalX()));
        properties.setProperty(prefix + ".globalY", Long.toString(config.globalY()));
        properties.setProperty(prefix + ".globalZ", Long.toString(config.globalZ()));
        properties.setProperty(prefix + ".localX", Long.toString(config.localX()));
        properties.setProperty(prefix + ".localY", Long.toString(config.localY()));
        properties.setProperty(prefix + ".localZ", Long.toString(config.localZ()));
    }

    private void writeComparison(
            Properties properties,
            String prefix,
            GpuRuntimeMethodTestReferenceComparison comparison
    ) {
        properties.setProperty(prefix + ".testId", comparison.testId());
        properties.setProperty(prefix + ".parameter.index", Integer.toString(comparison.parameterIndex()));
        properties.setProperty(prefix + ".parameter.name", comparison.parameterName());
        properties.setProperty(prefix + ".javaType", comparison.javaType());
        properties.setProperty(prefix + ".access", comparison.access().name());
        properties.setProperty(prefix + ".comparisonReady", Boolean.toString(comparison.comparisonReady()));
        properties.setProperty(prefix + ".passed", Boolean.toString(comparison.passed()));
        properties.setProperty(prefix + ".actual.kind", comparison.actualKind());
        properties.setProperty(prefix + ".actual.item.count", Integer.toString(comparison.actualItemCount()));
        writeList(properties, prefix + ".actual.numericValue", comparison.actualNumericValues());
        properties.setProperty(prefix + ".expected.kind", comparison.expectedKind());
        properties.setProperty(prefix + ".expected.item.count", Integer.toString(comparison.expectedItemCount()));
        writeList(properties, prefix + ".expected.numericValue", comparison.expectedNumericValues());
        properties.setProperty(prefix + ".tolerance.absolute", Double.toString(comparison.absoluteTolerance()));
        properties.setProperty(prefix + ".tolerance.relative", Double.toString(comparison.relativeTolerance()));
        properties.setProperty(prefix + ".blocker", comparison.blocker());
        properties.setProperty(prefix + ".failure", comparison.failure());
        properties.setProperty(prefix + ".diagnostic", comparison.diagnostic());
    }

    private void clearPersistentEntries() {
        if (persistentDirectory == null || !Files.isDirectory(persistentDirectory)) {
            return;
        }
        try (var entries = Files.list(persistentDirectory)) {
            entries.filter(GpuRuntimeMethodTestGpuProbeCache::persistentEntryFile)
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Best-effort cleanup only.
                        }
                    });
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    private Path persistentEntryPath(GpuRuntimeMethodTestGpuProbeEvidenceKey key) {
        return persistentDirectory.resolve(key.stableHash() + ".properties");
    }

    private boolean expired(long createdEpochMillis) {
        return maxEntryAge != null && createdEpochMillis + maxEntryAge.toMillis() < System.currentTimeMillis();
    }

    private static boolean persistentEntryFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".properties")
                && name.length() == 75
                && name.substring(0, 64).chars().allMatch(character ->
                (character >= '0' && character <= '9') || (character >= 'a' && character <= 'f'));
    }

    private static void moveReplacing(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeList(Properties properties, String prefix, List<String> values) {
        List<String> normalizedValues = values == null ? List.of() : values;
        properties.setProperty(prefix + ".count", Integer.toString(normalizedValues.size()));
        for (int index = 0; index < normalizedValues.size(); index++) {
            properties.setProperty(prefix + "." + index, normalizedValues.get(index));
        }
    }

    private static List<String> readList(Properties properties, String prefix) {
        int count = parseInt(properties, prefix + ".count", 0);
        if (count <= 0) {
            return List.of();
        }
        ArrayList<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(properties.getProperty(prefix + "." + index, ""));
        }
        return List.copyOf(values);
    }

    private static int parseInt(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long parseLong(Properties properties, String key, long fallback) {
        try {
            return Long.parseLong(properties.getProperty(key, Long.toString(fallback)).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double parseDouble(Properties properties, String key, double fallback) {
        try {
            return Double.parseDouble(properties.getProperty(key, Double.toString(fallback)).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> enumType, String value, T fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Enum.valueOf(enumType, value.trim());
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
