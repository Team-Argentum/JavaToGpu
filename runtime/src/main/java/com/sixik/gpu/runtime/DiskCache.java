package com.sixik.gpu.runtime;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

/**
 * Дисковый кэш бинарных образов программ драйвера (REWORK_ARCHITECTURE.md, 9.3, 5.24). Лежит под кэшем в памяти
 * {@link GpuContext}: промах в памяти идёт сюда, и только затем - к компилятору драйвера.
 *
 * <p><b>Кэш - оптимизация, а не источник истины.</b> Любая неудача (нет каталога, нет прав, диск полон, файл
 * повреждён) превращается в промах и пересборку из исходника, но не в ошибку пользователя.</p>
 *
 * <h2>Формат файла</h2>
 * <pre>
 * "GBC1" | длина полезной нагрузки (int, big-endian) | SHA-256 нагрузки (32 байта) | нагрузка
 * </pre>
 * <p>Имя файла - SHA-256 ключа в шестнадцатеричном виде. Ключ строит {@link GpuContext}: версия кодгена, хэш IR без
 * отладочных данных, номер ядра, опции генерации и сборки, устройство и драйвер, хэш самого исходника.</p>
 *
 * <h2>Надёжность</h2>
 * <ul>
 *   <li><b>Атомарная запись</b>: данные пишутся во временный файл рядом и переименуются ({@code ATOMIC_MOVE}), поэтому
 *       читающий (в том числе другой процесс) видит либо старый файл целиком, либо новый целиком;</li>
 *   <li><b>Повреждённый файл - промах</b>: неверный заголовок, длина или контрольная сумма - файл удаляется;</li>
 *   <li><b>LRU по размеру</b>: попадание обновляет время изменения файла, после записи самые давно используемые файлы
 *       удаляются, пока сумма размеров не станет не больше предела.</li>
 * </ul>
 *
 * <p>Класс потокобезопасен; несколько процессов могут делить один каталог.</p>
 */
final class DiskCache {

    /** Предел размера кэша по умолчанию, байт. */
    static final long DEFAULT_MAX_BYTES = 512L << 20;

    private static final byte[] MAGIC = {'G', 'B', 'C', '1'};
    private static final int HEADER = MAGIC.length + 4 + 32;
    private static final String EXT = ".gbc";
    private static final long STALE_TEMP_MILLIS = 3_600_000L;

    /**
     * Счётчики кэша.
     *
     * @param hits      найденных и целых файлов
     * @param misses    запросов без результата (нет файла или файл повреждён)
     * @param writes    записанных файлов
     * @param corrupted найденных повреждённых файлов (они же посчитаны как промахи)
     */
    record Stats(long hits, long misses, long writes, long corrupted) {
    }

    private final Path dir;
    private final long maxBytes;
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder writes = new LongAdder();
    private final LongAdder corrupted = new LongAdder();

    /**
     * @param dir      каталог кэша (создаётся при первой записи)
     * @param maxBytes предел суммарного размера файлов; больше него один образ в кэш не попадает
     */
    DiskCache(Path dir, long maxBytes) {
        if (dir == null || maxBytes <= 0) {
            throw new IllegalArgumentException("a directory and a positive size limit are required");
        }
        this.dir = dir;
        this.maxBytes = maxBytes;
    }

    /** @return каталог кэша */
    Path directory() {
        return dir;
    }

    /** @return счётчики */
    Stats stats() {
        return new Stats(hits.sum(), misses.sum(), writes.sum(), corrupted.sum());
    }

    /**
     * Каталог кэша пользователя по правилам ОС: Windows - {@code %LOCALAPPDATA%\javatogpu}, macOS -
     * {@code ~/Library/Caches/javatogpu}, иначе - {@code $XDG_CACHE_HOME/javatogpu} либо {@code ~/.cache/javatogpu}.
     *
     * @param osName   значение {@code os.name}
     * @param env      переменные окружения
     * @param userHome значение {@code user.home}
     * @return путь (может не существовать)
     */
    static Path defaultDirectory(String osName, Map<String, String> env, String userHome) {
        String os = osName.toLowerCase(Locale.ROOT);
        Path home = Path.of(userHome);
        if (os.contains("win")) {
            String local = env.get("LOCALAPPDATA");
            return (local != null && !local.isBlank() ? Path.of(local) : home.resolve("AppData").resolve("Local"))
                    .resolve("javatogpu");
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return home.resolve("Library").resolve("Caches").resolve("javatogpu");
        }
        String xdg = env.get("XDG_CACHE_HOME");
        return (xdg != null && !xdg.isBlank() ? Path.of(xdg) : home.resolve(".cache")).resolve("javatogpu");
    }

    /** @return каталог кэша для текущей ОС и пользователя */
    static Path defaultDirectory() {
        return defaultDirectory(System.getProperty("os.name", ""), System.getenv(), System.getProperty("user.home", "."));
    }

    private Path fileOf(String key) {
        return dir.resolve(HexFormat.of().formatHex(sha256(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))) + EXT);
    }

    /**
     * Читает образ.
     *
     * @param key ключ
     * @return образ либо {@code null}: файла нет, он повреждён (и удалён) или недоступен
     */
    byte[] get(String key) {
        Path file = fileOf(key);
        byte[] raw;
        try {
            raw = Files.readAllBytes(file);
        } catch (IOException e) {
            misses.increment();
            return null;
        }
        byte[] payload = decode(raw);
        if (payload == null) {
            corrupted.increment();
            misses.increment();
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // недоступный повреждённый файл останется промахом
            }
            return null;
        }
        hits.increment();
        try {
            Files.setLastModifiedTime(file, FileTime.from(Instant.now())); // для LRU
        } catch (IOException ignored) {
            // порядок вытеснения чуть менее точен - не ошибка
        }
        return payload;
    }

    /**
     * Записывает образ (атомарно) и вытесняет давно не использованные.
     *
     * @param key  ключ
     * @param data образ
     * @return {@code true}, если файл записан
     */
    boolean put(String key, byte[] data) {
        if (data == null || data.length == 0 || HEADER + (long) data.length > maxBytes) {
            return false;
        }
        Path file = fileOf(key);
        Path tmp = dir.resolve(file.getFileName() + "." + Long.toHexString(ThreadLocalRandom.current().nextLong()) + ".tmp");
        try {
            Files.createDirectories(dir);
            Files.write(tmp, encode(data));
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // временный файл уберёт чистка устаревших
            }
            return false;
        }
        writes.increment();
        trim();
        return true;
    }

    /**
     * Удаляет запись (например, когда драйвер не принял образ).
     *
     * @param key ключ
     */
    void remove(String key) {
        try {
            Files.deleteIfExists(fileOf(key));
        } catch (IOException ignored) {
            // кэш - оптимизация
        }
    }

    /** @return суммарный размер файлов кэша, байт */
    long sizeBytes() {
        long total = 0;
        for (Path f : files()) {
            total += sizeOf(f);
        }
        return total;
    }

    /** Вытесняет самые давно использованные файлы, пока сумма размеров больше предела; убирает устаревшие временные. */
    private synchronized void trim() {
        List<Path> all = files();
        long total = 0;
        for (Path f : all) {
            total += sizeOf(f);
        }
        if (total > maxBytes) {
            all.sort(Comparator.comparing(DiskCache::modified).thenComparing(Path::getFileName));
            for (Path f : all) {
                if (total <= maxBytes) {
                    break;
                }
                long size = sizeOf(f);
                try {
                    Files.deleteIfExists(f);
                    total -= size;
                } catch (IOException ignored) {
                    // файл занят другим процессом: попробуем в следующий раз
                }
            }
        }
        try (Stream<Path> s = Files.list(dir)) {
            long now = System.currentTimeMillis();
            for (Path t : s.filter(p -> p.getFileName().toString().endsWith(".tmp")).toList()) {
                if (now - modified(t).toMillis() > STALE_TEMP_MILLIS) {
                    Files.deleteIfExists(t);
                }
            }
        } catch (IOException ignored) {
            // чистка необязательна
        }
    }

    private List<Path> files() {
        try (Stream<Path> s = Files.list(dir)) {
            return new ArrayList<>(s.filter(p -> p.getFileName().toString().endsWith(EXT)).toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static long sizeOf(Path f) {
        try {
            return Files.size(f);
        } catch (IOException e) {
            return 0;
        }
    }

    private static FileTime modified(Path f) {
        try {
            return Files.getLastModifiedTime(f);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    // ---- формат ----------------------------------------------------------------------------------

    static byte[] encode(byte[] payload) {
        ByteBuffer b = ByteBuffer.allocate(HEADER + payload.length);
        b.put(MAGIC).putInt(payload.length).put(sha256(payload)).put(payload);
        return b.array();
    }

    /** @return нагрузка либо {@code null}, если заголовок, длина или контрольная сумма не сошлись */
    private static byte[] decode(byte[] raw) {
        if (raw.length < HEADER || !Arrays.equals(Arrays.copyOf(raw, MAGIC.length), MAGIC)) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(raw);
        b.position(MAGIC.length);
        int length = b.getInt();
        if (length < 0 || raw.length - HEADER != length) {
            return null;
        }
        byte[] sum = new byte[32];
        b.get(sum);
        byte[] payload = Arrays.copyOfRange(raw, HEADER, raw.length);
        return MessageDigest.isEqual(sum, sha256(payload)) ? payload : null;
    }

    /**
     * @param data данные
     * @return SHA-256
     */
    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 обязан быть в любой JVM
        }
    }
}
