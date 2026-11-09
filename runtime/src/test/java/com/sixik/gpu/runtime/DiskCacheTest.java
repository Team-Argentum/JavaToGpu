package com.sixik.gpu.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link DiskCache}: формат, надёжность записи и чтения, LRU, каталоги по ОС. */
class DiskCacheTest {

    private static byte[] bytes(int n, int seed) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) (i * 31 + seed);
        }
        return b;
    }

    private static List<Path> cacheFiles(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".gbc")).toList();
        }
    }

    @Test
    void roundTripAndMiss(@TempDir Path dir) {
        DiskCache cache = new DiskCache(dir.resolve("sub"), 1 << 20);
        assertNull(cache.get("k"), "empty cache - a miss");
        byte[] image = bytes(1000, 1);
        assertTrue(cache.put("k", image));
        assertArrayEquals(image, cache.get("k"));
        assertNull(cache.get("other"));
        DiskCache.Stats s = cache.stats();
        assertEquals(1, s.hits());
        assertEquals(2, s.misses());
        assertEquals(1, s.writes());
        assertEquals(0, s.corrupted());
    }

    @Test
    void dataSurvivesANewInstanceOverTheSameDirectory(@TempDir Path dir) {
        new DiskCache(dir, 1 << 20).put("k", bytes(64, 2));
        assertArrayEquals(bytes(64, 2), new DiskCache(dir, 1 << 20).get("k"), "another process will see what was written");
    }

    @Test
    void rewritingAKeyReplacesTheImage(@TempDir Path dir) {
        DiskCache cache = new DiskCache(dir, 1 << 20);
        cache.put("k", bytes(10, 1));
        cache.put("k", bytes(20, 2));
        assertArrayEquals(bytes(20, 2), cache.get("k"));
    }

    @Test
    void flippedByteMakesAMissAndTheFileIsRemoved(@TempDir Path dir) throws IOException {
        DiskCache cache = new DiskCache(dir, 1 << 20);
        cache.put("k", bytes(500, 3));
        Path file = cacheFiles(dir).get(0);
        byte[] raw = Files.readAllBytes(file);
        raw[raw.length - 1] ^= 0x55;
        Files.write(file, raw);
        assertNull(cache.get("k"), "checksum mismatch - a miss");
        assertEquals(0, cacheFiles(dir).size(), "the corrupted file was deleted");
        assertEquals(1, cache.stats().corrupted());
        assertTrue(cache.put("k", bytes(500, 3)), "after a miss, writing is possible again");
        assertArrayEquals(bytes(500, 3), cache.get("k"));
    }

    @Test
    void truncatedWrongMagicAndGarbageAreMisses(@TempDir Path dir) throws IOException {
        DiskCache cache = new DiskCache(dir, 1 << 20);
        for (int variant = 0; variant < 4; variant++) {
            cache.put("k", bytes(200, 4));
            Path file = cacheFiles(dir).get(0);
            byte[] raw = Files.readAllBytes(file);
            byte[] broken = switch (variant) {
                case 0 -> Arrays.copyOf(raw, raw.length - 7);          // усечён
                case 1 -> Arrays.copyOf(raw, 10);                      // короче заголовка
                case 2 -> {                                           // чужой заголовок
                    byte[] c = raw.clone();
                    c[0] = 'X';
                    yield c;
                }
                default -> new byte[0];                                // пустой
            };
            Files.write(file, broken);
            assertNull(cache.get("k"), "variant " + variant);
            assertEquals(0, cacheFiles(dir).size(), "variant " + variant);
        }
    }

    @Test
    void writeIsAtomicNoTemporaryFilesRemain(@TempDir Path dir) throws IOException {
        DiskCache cache = new DiskCache(dir, 1 << 20);
        for (int i = 0; i < 20; i++) {
            cache.put("k" + i, bytes(100, i));
        }
        try (Stream<Path> s = Files.list(dir)) {
            assertTrue(s.noneMatch(p -> p.toString().endsWith(".tmp")), "temporary files were renamed");
        }
        assertEquals(20, cacheFiles(dir).size());
    }

    @Test
    void leastRecentlyUsedFilesAreEvictedFirst(@TempDir Path dir) throws IOException {
        // файл: 4 + 4 + 32 + 100 = 140 байт; предел на три файла
        DiskCache cache = new DiskCache(dir, 3 * 140 + 10);
        cache.put("a", bytes(100, 1));
        cache.put("b", bytes(100, 2));
        cache.put("c", bytes(100, 3));
        // искусственно расставляем возраст: a самый старый, затем b, затем c
        long now = System.currentTimeMillis();
        touch(dir, cache, "a", now - 3000);
        touch(dir, cache, "b", now - 2000);
        touch(dir, cache, "c", now - 1000);
        assertArrayEquals(bytes(100, 1), cache.get("a"), "a read refreshes a: now b is the oldest");
        cache.put("d", bytes(100, 4));
        assertNull(cache.get("b"), "the least recently used was evicted");
        assertArrayEquals(bytes(100, 1), cache.get("a"));
        assertArrayEquals(bytes(100, 3), cache.get("c"));
        assertArrayEquals(bytes(100, 4), cache.get("d"));
        assertTrue(cache.sizeBytes() <= 3 * 140 + 10);
    }

    /** Выставляет время изменения файла записи: имя файла выводится тем же способом, что и в кэше. */
    private static void touch(Path dir, DiskCache cache, String key, long millis) throws IOException {
        byte[] image = cache.get(key);
        cache.put(key, image); // тот же файл, новое содержимое
        for (Path f : cacheFiles(dir)) {
            // имя файла - SHA-256 ключа
            if (f.getFileName().toString().startsWith(java.util.HexFormat.of().formatHex(
                    DiskCache.sha256(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))))) {
                Files.setLastModifiedTime(f, FileTime.fromMillis(millis));
            }
        }
    }

    @Test
    void imageLargerThanTheLimitIsNotStored(@TempDir Path dir) throws IOException {
        DiskCache cache = new DiskCache(dir, 100);
        assertFalse(cache.put("big", bytes(500, 1)));
        assertNull(cache.get("big"));
        assertFalse(cache.put("empty", new byte[0]));
        assertFalse(cache.put("null", null));
        assertEquals(0, cacheFiles(dir).size());
    }

    @Test
    void unusableDirectoryIsASilentMiss(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("file"), "x");
        DiskCache cache = new DiskCache(file.resolve("below-a-file"), 1 << 20);
        assertFalse(cache.put("k", bytes(10, 1)), "the cache is an optimization: the write failed, but there is no exception");
        assertNull(cache.get("k"));
    }

    @Test
    void concurrentWritersOfTheSameKeyLeaveAValidFile(@TempDir Path dir) throws Exception {
        DiskCache cache = new DiskCache(dir, 1 << 20);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> all = new java.util.ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int seed = t;
                all.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 30; i++) {
                        cache.put("same", bytes(4096, seed));
                        byte[] got = cache.get("same");
                        if (got != null && got.length != 4096) {
                            throw new AssertionError("an incomplete image was read");
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : all) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        byte[] last = cache.get("same");
        assertEquals(4096, last.length);
        assertEquals(0, cache.stats().corrupted(), "a reader never saw a half-written file");
    }

    @Test
    void invalidArgumentsAreRejected(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class, () -> new DiskCache(null, 10));
        assertThrows(IllegalArgumentException.class, () -> new DiskCache(dir, 0));
    }

    @Test
    void defaultDirectoryFollowsTheConventionsOfEachOs() {
        assertEquals(Path.of("C:\\Users\\u\\AppData\\Local\\javatogpu"),
                DiskCache.defaultDirectory("Windows 10", Map.of("LOCALAPPDATA", "C:\\Users\\u\\AppData\\Local"), "C:\\Users\\u"));
        assertEquals(Path.of("C:\\Users\\u").resolve("AppData").resolve("Local").resolve("javatogpu"),
                DiskCache.defaultDirectory("Windows 11", Map.of(), "C:\\Users\\u"));
        assertEquals(Path.of("/Users/u").resolve("Library").resolve("Caches").resolve("javatogpu"),
                DiskCache.defaultDirectory("Mac OS X", Map.of(), "/Users/u"));
        assertEquals(Path.of("/home/u").resolve(".cache").resolve("javatogpu"),
                DiskCache.defaultDirectory("Linux", Map.of(), "/home/u"));
        assertEquals(Path.of("/xdg").resolve("javatogpu"),
                DiskCache.defaultDirectory("Linux", Map.of("XDG_CACHE_HOME", "/xdg"), "/home/u"));
    }
}
