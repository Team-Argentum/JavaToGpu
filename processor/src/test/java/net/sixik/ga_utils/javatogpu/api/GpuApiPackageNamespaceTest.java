package net.sixik.ga_utils.javatogpu.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GpuApiPackageNamespaceTest {

    private static final Pattern LEGACY_ROOT_WRAPPER_REFERENCE = Pattern.compile(
            "net\\.sixik\\.ga_utils\\.javatogpu\\.api\\.(?:"
                    + String.join("|", legacyRootWrapperNames())
                    + ")\\b"
    );

    @Test
    void sourceAndDocsDoNotUseLegacyRootWrapperPackages() throws IOException {
        List<Path> files = filesToScan();
        List<String> violations = new ArrayList<>();
        for (Path file : files) {
            List<String> lines = Files.readAllLines(file);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (LEGACY_ROOT_WRAPPER_REFERENCE.matcher(line).find()) {
                    violations.add(file + ":" + (index + 1) + ": " + line.trim());
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                "Use grouped API packages: api.types.*, api.pointers.*, or api.images.*\n"
                        + String.join(System.lineSeparator(), violations)
        );
    }

    @Test
    void rootApiPackageOnlyContainsFacadeAndTargetSources() throws IOException {
        Path apiRoot = projectRoot().resolve("processor/src/main/java/net/sixik/ga_utils/javatogpu/api");
        Set<String> actual;
        try (Stream<Path> stream = Files.list(apiRoot)) {
            actual = stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        }

        assertEquals(
                allowedRootApiSourceNames(),
                actual,
                "Keep concrete wrappers out of root api. Use api.types.*, api.pointers.*, api.images, or api.annotations."
        );
    }

    @Test
    void annotationPackageOverviewMentionsEveryAnnotationType() throws IOException {
        Path annotationsRoot = projectRoot().resolve("processor/src/main/java/net/sixik/ga_utils/javatogpu/api/annotations");
        String packageInfo = Files.readString(annotationsRoot.resolve("package-info.java"));
        List<String> missing = new ArrayList<>();

        try (Stream<Path> stream = Files.list(annotationsRoot)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(fileName -> fileName.endsWith(".java"))
                    .filter(fileName -> !fileName.equals("package-info.java"))
                    .map(fileName -> fileName.substring(0, fileName.length() - ".java".length()))
                    .sorted()
                    .filter(simpleName -> !packageInfo.contains(simpleName))
                    .forEach(missing::add);
        }

        assertTrue(
                missing.isEmpty(),
                "Mention new public annotations in api.annotations package-info.java: " + missing
        );
    }

    private static List<Path> filesToScan() throws IOException {
        Path root = projectRoot();
        List<Path> roots = List.of(
                root.resolve("README.md"),
                root.resolve("ROADMAP.md"),
                root.resolve("docs"),
                root.resolve("examples-app/src/main/java"),
                root.resolve("test-app/src/main/java"),
                root.resolve("processor/src/main/java"),
                root.resolve("processor/src/test/java")
        );
        ArrayList<Path> files = new ArrayList<>();
        for (Path scanRoot : roots) {
            if (!Files.exists(scanRoot)) {
                continue;
            }
            if (Files.isRegularFile(scanRoot)) {
                files.add(scanRoot);
                continue;
            }
            try (Stream<Path> stream = Files.walk(scanRoot)) {
                stream.filter(Files::isRegularFile)
                        .filter(GpuApiPackageNamespaceTest::isScannedFile)
                        .forEach(files::add);
            }
        }
        return List.copyOf(files);
    }

    private static boolean isScannedFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".java")
                || fileName.endsWith(".md")
                || fileName.endsWith(".txt")
                || fileName.endsWith(".properties");
    }

    private static Path projectRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate project root from " + Path.of("").toAbsolutePath().normalize());
    }

    private static Set<String> legacyRootWrapperNames() {
        return Set.of(
                "Byte2", "Byte3", "Byte4", "UByte", "UByte2", "UByte3", "UByte4", "UByte8", "UByte16",
                "Short2", "Short3", "Short4", "UShort", "UShort2", "UShort3", "UShort4", "UShort8", "UShort16",
                "Int2", "Int3", "Int4", "Int8", "Int16", "UInt", "UInt2", "UInt3", "UInt4", "UInt8", "UInt16",
                "Long2", "Long3", "Long4", "ULong", "ULong2", "ULong3", "ULong4", "ULong8", "ULong16",
                "Float2", "Float3", "Float4", "Double2", "Double3", "Double4",
                "BytePtr", "CharPtr", "ShortPtr", "IntPtr", "LongPtr", "FloatPtr", "DoublePtr",
                "GlobalBytePtr", "GlobalCharPtr", "GlobalShortPtr", "GlobalIntPtr", "GlobalLongPtr", "GlobalFloatPtr", "GlobalDoublePtr",
                "ConstantBytePtr", "ConstantCharPtr", "ConstantShortPtr", "ConstantIntPtr", "ConstantLongPtr", "ConstantFloatPtr", "ConstantDoublePtr",
                "LocalBytePtr", "LocalCharPtr", "LocalShortPtr", "LocalIntPtr", "LocalLongPtr", "LocalFloatPtr", "LocalDoublePtr",
                "Image1DReadOnly", "Image1DWriteOnly", "Image1DArrayReadOnly", "Image1DArrayWriteOnly",
                "Image1DBufferReadOnly", "Image1DBufferWriteOnly", "Image2DReadOnly", "Image2DWriteOnly",
                "Image2DArrayReadOnly", "Image2DArrayWriteOnly", "Image2DMipmappedReadOnly", "Image2DMipmappedWriteOnly",
                "Image2DMsaaReadOnly", "Image2DMsaaWriteOnly", "Image3DReadOnly", "Image3DWriteOnly", "Sampler"
        );
    }

    private static Set<String> allowedRootApiSourceNames() {
        return new java.util.TreeSet<>(Set.of(
                "GPU.java",
                "GpuAnnotationSupport.java",
                "GpuBackendTarget.java",
                "GpuDeviceClassTarget.java",
                "GpuPreparedLauncher.java",
                "GpuScope.java",
                "GpuVendorTarget.java",
                "JavaToGpu.java",
                "package-info.java"
        ));
    }
}
