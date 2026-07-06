package net.sixik.ga_utils.javatogpu.frontend.asm;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs the existing ASM subset validator in read-only reporting mode across multiple methods.
 */
public final class AsmFrontendFailureReporter {

    private final AsmSubsetValidator validator;
    private final AsmUnsupportedPatternScanner scanner;

    public AsmFrontendFailureReporter() {
        this(new AsmSubsetValidator(), new AsmUnsupportedPatternScanner());
    }

    public AsmFrontendFailureReporter(AsmSubsetValidator validator) {
        this(validator, new AsmUnsupportedPatternScanner());
    }

    public AsmFrontendFailureReporter(AsmSubsetValidator validator, AsmUnsupportedPatternScanner scanner) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
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

    private boolean isJvmLifecycleMethod(MethodNode methodNode) {
        return "<init>".equals(methodNode.name) || "<clinit>".equals(methodNode.name);
    }
}
