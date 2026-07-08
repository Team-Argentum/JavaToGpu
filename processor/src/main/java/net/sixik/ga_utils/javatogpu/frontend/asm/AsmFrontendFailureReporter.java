package net.sixik.ga_utils.javatogpu.frontend.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Runs the existing ASM subset validator in read-only reporting mode across multiple methods.
 */
public final class AsmFrontendFailureReporter {

    private final AsmSubsetValidator validator;
    private final AsmUnsupportedPatternScanner scanner;
    private final AsmBytecodeShapeInventoryScanner inventoryScanner;

    public AsmFrontendFailureReporter() {
        this(new AsmSubsetValidator(), new AsmUnsupportedPatternScanner(), new AsmBytecodeShapeInventoryScanner());
    }

    public AsmFrontendFailureReporter(AsmSubsetValidator validator) {
        this(validator, new AsmUnsupportedPatternScanner(), new AsmBytecodeShapeInventoryScanner());
    }

    public AsmFrontendFailureReporter(AsmSubsetValidator validator, AsmUnsupportedPatternScanner scanner) {
        this(validator, scanner, new AsmBytecodeShapeInventoryScanner());
    }

    public AsmFrontendFailureReporter(
            AsmSubsetValidator validator,
            AsmUnsupportedPatternScanner scanner,
            AsmBytecodeShapeInventoryScanner inventoryScanner
    ) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.inventoryScanner = Objects.requireNonNull(inventoryScanner, "inventoryScanner");
    }

    public AsmFrontendFailureReport report(String ownerInternalName, MethodNode methodNode) {
        return report(ownerInternalName, methodNode, AsmValidationConfig.defaultConfig());
    }

    public AsmFrontendFailureReport report(
            String ownerInternalName,
            MethodNode methodNode,
            AsmValidationConfig config
    ) {
        Objects.requireNonNull(ownerInternalName, "ownerInternalName");
        Objects.requireNonNull(methodNode, "methodNode");
        Objects.requireNonNull(config, "config");

        AsmFrontendFailureReport scannedReport = scanner.scan(ownerInternalName, methodNode, config);
        if (!scannedReport.successful()) {
            return scannedReport;
        }

        try {
            validator.validate(ownerInternalName, methodNode, config);
            return new AsmFrontendFailureReport(List.of());
        } catch (AsmFrontendException exception) {
            return new AsmFrontendFailureReport(exception.metadata().stream().toList());
        }
    }

    public AsmFrontendFailureReport reportAll(List<AsmGpuMethod> methods) {
        return reportAll(methods, AsmValidationConfig.defaultConfig());
    }

    public AsmFrontendFailureReport reportAll(List<AsmGpuMethod> methods, AsmValidationConfig config) {
        Objects.requireNonNull(methods, "methods");
        Objects.requireNonNull(config, "config");

        List<AsmFrontendFailureMetadata> failures = new ArrayList<>();
        for (AsmGpuMethod method : methods) {
            Objects.requireNonNull(method, "method");
            AsmFrontendFailureReport report = report(method.ownerInternalName(), method.methodNode(), config);
            failures.addAll(report.failures());
        }
        return new AsmFrontendFailureReport(failures);
    }

    public AsmFrontendFailureReport reportClass(ClassNode classNode) {
        return reportClass(classNode, AsmValidationConfig.defaultConfig());
    }

    public AsmFrontendFailureReport reportClass(byte[] classBytes) {
        return reportClass(classBytes, AsmValidationConfig.defaultConfig());
    }

    public AsmFrontendFailureReport reportClass(byte[] classBytes, AsmValidationConfig config) {
        Objects.requireNonNull(classBytes, "classBytes");
        Objects.requireNonNull(config, "config");
        ClassNode classNode = new ClassNode();
        new ClassReader(classBytes).accept(classNode, 0);
        return reportClass(classNode, config);
    }

    public AsmFrontendArtifactReport reportArtifactSnapshot(byte[] classBytes) {
        return reportArtifactSnapshot(classBytes, AsmValidationConfig.defaultConfig());
    }

    public AsmFrontendArtifactReport reportArtifactSnapshot(byte[] classBytes, AsmValidationConfig config) {
        Objects.requireNonNull(classBytes, "classBytes");
        Objects.requireNonNull(config, "config");
        ClassNode classNode = new ClassNode();
        new ClassReader(classBytes).accept(classNode, 0);
        return reportArtifactSnapshot(classNode, config);
    }

    public AsmFrontendFailureReport reportClassFile(Path classFile) {
        return reportClassFile(classFile, AsmValidationConfig.defaultConfig());
    }

    public AsmFrontendFailureReport reportClassFile(Path classFile, AsmValidationConfig config) {
        Objects.requireNonNull(classFile, "classFile");
        Objects.requireNonNull(config, "config");
        try {
            return reportClass(Files.readAllBytes(classFile), config);
        } catch (IOException exception) {
            throw new AsmFrontendException(
                    "Unable to read ASM class file for preflight: " + classFile + "; " + exception.getMessage()
            );
        }
    }

    public AsmFrontendFailureReport reportArtifact(Path artifact) {
        return reportArtifact(artifact, AsmValidationConfig.defaultConfig());
    }

    public AsmFrontendFailureReport reportArtifact(Path artifact, AsmValidationConfig config) {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(config, "config");
        if (Files.isDirectory(artifact)) {
            return reportClassDirectory(artifact, config);
        }
        if (Files.isRegularFile(artifact)) {
            String fileName = artifact.getFileName().toString().toLowerCase(Locale.ROOT);
            if (fileName.endsWith(".class")) {
                return reportClassFile(artifact, config);
            }
            if (fileName.endsWith(".jar")) {
                return reportJar(artifact, config);
            }
        }
        throw new IllegalArgumentException(
                "artifact must be a .class file, .jar file, or class directory: " + artifact
        );
    }

    public AsmFrontendArtifactReport reportArtifactSnapshot(Path artifact) {
        return reportArtifactSnapshot(artifact, AsmValidationConfig.defaultConfig());
    }

    public AsmFrontendArtifactReport reportArtifactSnapshot(Path artifact, AsmValidationConfig config) {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(config, "config");
        return new AsmFrontendArtifactReport(
                reportArtifact(artifact, config),
                inventoryArtifact(artifact, config)
        );
    }

    public AsmBytecodeShapeInventoryReport inventory(String ownerInternalName, MethodNode methodNode) {
        return inventory(ownerInternalName, methodNode, AsmValidationConfig.defaultConfig());
    }

    public AsmBytecodeShapeInventoryReport inventory(
            String ownerInternalName,
            MethodNode methodNode,
            AsmValidationConfig config
    ) {
        return inventoryScanner.scan(ownerInternalName, methodNode, config);
    }

    public AsmBytecodeShapeInventoryReport inventoryAll(List<AsmGpuMethod> methods) {
        return inventoryAll(methods, AsmValidationConfig.defaultConfig());
    }

    public AsmBytecodeShapeInventoryReport inventoryAll(List<AsmGpuMethod> methods, AsmValidationConfig config) {
        Objects.requireNonNull(methods, "methods");
        Objects.requireNonNull(config, "config");

        List<AsmBytecodeShapeObservation> observations = new ArrayList<>();
        for (AsmGpuMethod method : methods) {
            Objects.requireNonNull(method, "method");
            observations.addAll(inventory(method.ownerInternalName(), method.methodNode(), config).observations());
        }
        return new AsmBytecodeShapeInventoryReport(observations);
    }

    public AsmBytecodeShapeInventoryReport inventoryClass(ClassNode classNode) {
        return inventoryClass(classNode, AsmValidationConfig.defaultConfig());
    }

    public AsmBytecodeShapeInventoryReport inventoryClass(byte[] classBytes) {
        return inventoryClass(classBytes, AsmValidationConfig.defaultConfig());
    }

    public AsmBytecodeShapeInventoryReport inventoryClass(byte[] classBytes, AsmValidationConfig config) {
        Objects.requireNonNull(classBytes, "classBytes");
        Objects.requireNonNull(config, "config");
        ClassNode classNode = new ClassNode();
        new ClassReader(classBytes).accept(classNode, 0);
        return inventoryClass(classNode, config);
    }

    public AsmBytecodeShapeInventoryReport inventoryClassFile(Path classFile) {
        return inventoryClassFile(classFile, AsmValidationConfig.defaultConfig());
    }

    public AsmBytecodeShapeInventoryReport inventoryClassFile(Path classFile, AsmValidationConfig config) {
        Objects.requireNonNull(classFile, "classFile");
        Objects.requireNonNull(config, "config");
        try {
            return inventoryClass(Files.readAllBytes(classFile), config);
        } catch (IOException exception) {
            throw new AsmFrontendException(
                    "Unable to read ASM class file for inventory: " + classFile + "; " + exception.getMessage()
            );
        }
    }

    public AsmBytecodeShapeInventoryReport inventoryArtifact(Path artifact) {
        return inventoryArtifact(artifact, AsmValidationConfig.defaultConfig());
    }

    public AsmBytecodeShapeInventoryReport inventoryArtifact(Path artifact, AsmValidationConfig config) {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(config, "config");
        if (Files.isDirectory(artifact)) {
            return inventoryClassDirectory(artifact, config);
        }
        if (Files.isRegularFile(artifact)) {
            String fileName = artifact.getFileName().toString().toLowerCase(Locale.ROOT);
            if (fileName.endsWith(".class")) {
                return inventoryClassFile(artifact, config);
            }
            if (fileName.endsWith(".jar")) {
                return inventoryJar(artifact, config);
            }
        }
        throw new IllegalArgumentException(
                "artifact must be a .class file, .jar file, or class directory: " + artifact
        );
    }

    public AsmBytecodeShapeInventoryReport inventoryClassDirectory(Path classDirectory) {
        return inventoryClassDirectory(classDirectory, AsmValidationConfig.defaultConfig());
    }

    public AsmBytecodeShapeInventoryReport inventoryClassDirectory(Path classDirectory, AsmValidationConfig config) {
        Objects.requireNonNull(classDirectory, "classDirectory");
        Objects.requireNonNull(config, "config");
        if (!Files.isDirectory(classDirectory)) {
            throw new IllegalArgumentException("classDirectory must be a directory: " + classDirectory);
        }

        List<AsmBytecodeShapeObservation> observations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(classDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .sorted()
                    .forEach(path -> observations.addAll(inventoryClassFile(path, config).observations()));
        } catch (IOException exception) {
            throw new AsmFrontendException(
                    "Unable to read ASM class directory for inventory: "
                            + classDirectory
                            + "; "
                            + exception.getMessage()
            );
        }
        return new AsmBytecodeShapeInventoryReport(observations);
    }

    public AsmBytecodeShapeInventoryReport inventoryJar(Path jarFile) {
        return inventoryJar(jarFile, AsmValidationConfig.defaultConfig());
    }

    public AsmBytecodeShapeInventoryReport inventoryJar(Path jarFile, AsmValidationConfig config) {
        Objects.requireNonNull(jarFile, "jarFile");
        Objects.requireNonNull(config, "config");
        if (!Files.isRegularFile(jarFile)) {
            throw new IllegalArgumentException("jarFile must be a regular file: " + jarFile);
        }

        List<AsmBytecodeShapeObservation> observations = new ArrayList<>();
        try (JarFile openedJar = new JarFile(jarFile.toFile())) {
            List<JarEntry> classEntries = openedJar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .sorted(Comparator.comparing(JarEntry::getName))
                    .toList();
            for (JarEntry classEntry : classEntries) {
                observations.addAll(inventoryJarClass(openedJar, classEntry, config).observations());
            }
        } catch (IOException exception) {
            throw new AsmFrontendException(
                    "Unable to read ASM jar for inventory: "
                            + jarFile
                            + "; "
                            + exception.getMessage()
            );
        }
        return new AsmBytecodeShapeInventoryReport(observations);
    }

    private AsmBytecodeShapeInventoryReport inventoryJarClass(
            JarFile jarFile,
            JarEntry classEntry,
            AsmValidationConfig config
    ) throws IOException {
        try (InputStream inputStream = jarFile.getInputStream(classEntry)) {
            return inventoryClass(inputStream.readAllBytes(), config);
        }
    }

    public AsmFrontendFailureReport reportClassDirectory(Path classDirectory) {
        return reportClassDirectory(classDirectory, AsmValidationConfig.defaultConfig());
    }

    public AsmFrontendFailureReport reportClassDirectory(Path classDirectory, AsmValidationConfig config) {
        Objects.requireNonNull(classDirectory, "classDirectory");
        Objects.requireNonNull(config, "config");
        if (!Files.isDirectory(classDirectory)) {
            throw new IllegalArgumentException("classDirectory must be a directory: " + classDirectory);
        }

        List<AsmFrontendFailureMetadata> failures = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(classDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .sorted()
                    .forEach(path -> failures.addAll(reportClassFile(path, config).failures()));
        } catch (IOException exception) {
            throw new AsmFrontendException(
                    "Unable to read ASM class directory for preflight: "
                            + classDirectory
                            + "; "
                            + exception.getMessage()
            );
        }
        return new AsmFrontendFailureReport(failures);
    }

    public AsmFrontendFailureReport reportJar(Path jarFile) {
        return reportJar(jarFile, AsmValidationConfig.defaultConfig());
    }

    public AsmFrontendFailureReport reportJar(Path jarFile, AsmValidationConfig config) {
        Objects.requireNonNull(jarFile, "jarFile");
        Objects.requireNonNull(config, "config");
        if (!Files.isRegularFile(jarFile)) {
            throw new IllegalArgumentException("jarFile must be a regular file: " + jarFile);
        }

        List<AsmFrontendFailureMetadata> failures = new ArrayList<>();
        try (JarFile openedJar = new JarFile(jarFile.toFile())) {
            List<JarEntry> classEntries = openedJar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .sorted(Comparator.comparing(JarEntry::getName))
                    .toList();
            for (JarEntry classEntry : classEntries) {
                failures.addAll(reportJarClass(openedJar, classEntry, config).failures());
            }
        } catch (IOException exception) {
            throw new AsmFrontendException(
                    "Unable to read ASM jar for preflight: "
                            + jarFile
                            + "; "
                            + exception.getMessage()
            );
        }
        return new AsmFrontendFailureReport(failures);
    }

    private AsmFrontendFailureReport reportJarClass(
            JarFile jarFile,
            JarEntry classEntry,
            AsmValidationConfig config
    ) throws IOException {
        try (InputStream inputStream = jarFile.getInputStream(classEntry)) {
            return reportClass(inputStream.readAllBytes(), config);
        }
    }

    public AsmFrontendFailureReport reportClass(ClassNode classNode, AsmValidationConfig config) {
        Objects.requireNonNull(classNode, "classNode");
        Objects.requireNonNull(config, "config");
        if (classNode.name == null || classNode.name.isBlank()) {
            throw new IllegalArgumentException("classNode.name must not be blank");
        }

        List<AsmFrontendFailureMetadata> failures = new ArrayList<>();
        for (MethodNode methodNode : classNode.methods) {
            if (methodNode == null || isJvmLifecycleMethod(methodNode)) {
                continue;
            }
            AsmFrontendFailureReport report = report(classNode.name, methodNode, config);
            failures.addAll(report.failures());
        }
        return new AsmFrontendFailureReport(failures);
    }

    public AsmFrontendArtifactReport reportArtifactSnapshot(ClassNode classNode) {
        return reportArtifactSnapshot(classNode, AsmValidationConfig.defaultConfig());
    }

    public AsmFrontendArtifactReport reportArtifactSnapshot(ClassNode classNode, AsmValidationConfig config) {
        return new AsmFrontendArtifactReport(
                reportClass(classNode, config),
                inventoryClass(classNode, config)
        );
    }

    public AsmBytecodeShapeInventoryReport inventoryClass(ClassNode classNode, AsmValidationConfig config) {
        Objects.requireNonNull(classNode, "classNode");
        Objects.requireNonNull(config, "config");
        if (classNode.name == null || classNode.name.isBlank()) {
            throw new IllegalArgumentException("classNode.name must not be blank");
        }

        List<AsmBytecodeShapeObservation> observations = new ArrayList<>();
        for (MethodNode methodNode : classNode.methods) {
            if (methodNode == null || isJvmLifecycleMethod(methodNode)) {
                continue;
            }
            observations.addAll(inventory(classNode.name, methodNode, config).observations());
        }
        return new AsmBytecodeShapeInventoryReport(observations);
    }

    private boolean isJvmLifecycleMethod(MethodNode methodNode) {
        return "<init>".equals(methodNode.name) || "<clinit>".equals(methodNode.name);
    }
}
