package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.asm.AsmFrontendException;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmBytecodeShapeInventoryReport;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmFrontendArtifactReport;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmFrontendFailureReport;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmFrontendFailureReportIO;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmFrontendFailureReporter;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmFrontendReadinessReport;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmValidationConfig;
import net.sixik.ga_utils.javatogpu.frontend.diagnostics.AsmFrontendDiagnosticAdapter;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Public facade over the JavaToGpu frontend pipeline.
 *
 * <p>This class keeps the existing source-first flow, but also exposes the structured ASM frontend as
 * another official entry point into the same IR and OpenCL emitter pipeline.</p>
 */
public final class GpuProgramCompiler {

    private final GpuFrontendService sourceFrontend;
    private final AsmFrontendService asmFrontend;
    private final AsmFrontendDiagnosticAdapter asmDiagnosticAdapter;
    private final AsmFrontendFailureReporter asmFailureReporter;

    public GpuProgramCompiler(
            GpuFrontendService sourceFrontend,
            AsmFrontendService asmFrontend
    ) {
        this(sourceFrontend, asmFrontend, new AsmFrontendDiagnosticAdapter(), new AsmFrontendFailureReporter());
    }

    public GpuProgramCompiler(
            GpuFrontendService sourceFrontend,
            AsmFrontendService asmFrontend,
            AsmFrontendDiagnosticAdapter asmDiagnosticAdapter
    ) {
        this(sourceFrontend, asmFrontend, asmDiagnosticAdapter, new AsmFrontendFailureReporter());
    }

    public GpuProgramCompiler(
            GpuFrontendService sourceFrontend,
            AsmFrontendService asmFrontend,
            AsmFrontendDiagnosticAdapter asmDiagnosticAdapter,
            AsmFrontendFailureReporter asmFailureReporter
    ) {
        this.sourceFrontend = sourceFrontend;
        this.asmFrontend = asmFrontend;
        this.asmDiagnosticAdapter = Objects.requireNonNull(asmDiagnosticAdapter, "asmDiagnosticAdapter");
        this.asmFailureReporter = Objects.requireNonNull(asmFailureReporter, "asmFailureReporter");
    }

    public static GpuProgramCompiler createDefault() {
        return create(GpuIntrinsicDatabase.createDefault());
    }

    public static GpuProgramCompiler create(GpuIntrinsicDatabase intrinsicDatabase) {
        return new GpuProgramCompiler(
                GpuFrontendService.create(intrinsicDatabase),
                AsmFrontendService.create(intrinsicDatabase)
        );
    }

    public ParsedGpuMethod parseAndValidateSource(String methodSource) {
        return sourceFrontend.parseAndValidate(methodSource);
    }

    public GpuIrMethod lowerSource(String methodSource) {
        return sourceFrontend.parseValidateAndLower(methodSource);
    }

    public String compileSource(String methodSource) {
        return sourceFrontend.parseValidateLowerAndEmit(methodSource);
    }

    public String compileSource(String methodSource, List<String> helperMethodSources) {
        return sourceFrontend.parseValidateLowerAndEmit(methodSource, helperMethodSources);
    }

    public String compileSource(
            ParsedGpuMethod kernelMethod,
            List<ParsedGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs
    ) {
        return sourceFrontend.validateLowerAndEmit(kernelMethod, helperMethods, structs);
    }

    public GpuIrMethod liftStructuredAsm(AsmGpuMethod method) {
        return asmFrontend.validateAndLiftStructured(method);
    }

    public AsmFrontendFailureReport reportStructuredAsm(AsmGpuMethod method) {
        return asmFailureReporter.report(method.ownerInternalName(), method.methodNode());
    }

    public AsmFrontendFailureReport reportStructuredAsm(AsmGpuMethod method, AsmValidationConfig config) {
        return asmFailureReporter.report(method.ownerInternalName(), method.methodNode(), config);
    }

    public AsmFrontendFailureReport reportStructuredAsm(List<AsmGpuMethod> methods) {
        return asmFailureReporter.reportAll(methods);
    }

    public AsmFrontendFailureReport reportStructuredAsm(List<AsmGpuMethod> methods, AsmValidationConfig config) {
        return asmFailureReporter.reportAll(methods, config);
    }

    public AsmFrontendFailureReport reportStructuredAsmClass(ClassNode classNode) {
        return asmFailureReporter.reportClass(classNode);
    }

    public AsmFrontendFailureReport reportStructuredAsmClass(ClassNode classNode, AsmValidationConfig config) {
        return asmFailureReporter.reportClass(classNode, config);
    }

    public AsmFrontendFailureReport reportStructuredAsmClass(byte[] classBytes) {
        return asmFailureReporter.reportClass(classBytes);
    }

    public AsmFrontendFailureReport reportStructuredAsmClass(byte[] classBytes, AsmValidationConfig config) {
        return asmFailureReporter.reportClass(classBytes, config);
    }

    public AsmFrontendFailureReport reportStructuredAsmClassFile(Path classFile) {
        return asmFailureReporter.reportClassFile(classFile);
    }

    public AsmFrontendFailureReport reportStructuredAsmClassFile(Path classFile, AsmValidationConfig config) {
        return asmFailureReporter.reportClassFile(classFile, config);
    }

    public AsmFrontendFailureReport reportStructuredAsmClassDirectory(Path classDirectory) {
        return asmFailureReporter.reportClassDirectory(classDirectory);
    }

    public AsmFrontendFailureReport reportStructuredAsmClassDirectory(Path classDirectory, AsmValidationConfig config) {
        return asmFailureReporter.reportClassDirectory(classDirectory, config);
    }

    public AsmFrontendFailureReport reportStructuredAsmJar(Path jarFile) {
        return asmFailureReporter.reportJar(jarFile);
    }

    public AsmFrontendFailureReport reportStructuredAsmJar(Path jarFile, AsmValidationConfig config) {
        return asmFailureReporter.reportJar(jarFile, config);
    }

    public AsmFrontendFailureReport reportStructuredAsmArtifact(Path artifact) {
        return asmFailureReporter.reportArtifact(artifact);
    }

    public AsmFrontendFailureReport reportStructuredAsmArtifact(Path artifact, AsmValidationConfig config) {
        return asmFailureReporter.reportArtifact(artifact, config);
    }

    public AsmFrontendReadinessReport reportStructuredAsmArtifactReadiness(Path artifact) {
        return reportStructuredAsmArtifact(artifact).readinessReport();
    }

    public AsmFrontendReadinessReport reportStructuredAsmArtifactReadiness(Path artifact, AsmValidationConfig config) {
        return reportStructuredAsmArtifact(artifact, config).readinessReport();
    }

    public AsmFrontendArtifactReport reportStructuredAsmArtifactSnapshot(Path artifact) {
        return asmFailureReporter.reportArtifactSnapshot(artifact);
    }

    public AsmFrontendArtifactReport reportStructuredAsmArtifactSnapshot(Path artifact, AsmValidationConfig config) {
        return asmFailureReporter.reportArtifactSnapshot(artifact, config);
    }

    public AsmBytecodeShapeInventoryReport inventoryStructuredAsmArtifact(Path artifact) {
        return asmFailureReporter.inventoryArtifact(artifact);
    }

    public AsmBytecodeShapeInventoryReport inventoryStructuredAsmArtifact(Path artifact, AsmValidationConfig config) {
        return asmFailureReporter.inventoryArtifact(artifact, config);
    }

    public AsmBytecodeShapeInventoryReport writeStructuredAsmArtifactInventory(
            Path artifact,
            Path reportFile
    ) throws IOException {
        AsmBytecodeShapeInventoryReport report = inventoryStructuredAsmArtifact(artifact);
        AsmFrontendFailureReportIO.writeInventory(reportFile, report);
        return report;
    }

    public AsmBytecodeShapeInventoryReport writeStructuredAsmArtifactInventory(
            Path artifact,
            Path reportFile,
            AsmValidationConfig config
    ) throws IOException {
        AsmBytecodeShapeInventoryReport report = inventoryStructuredAsmArtifact(artifact, config);
        AsmFrontendFailureReportIO.writeInventory(reportFile, report);
        return report;
    }

    public AsmFrontendArtifactReport writeStructuredAsmArtifactSnapshot(
            Path artifact,
            Path reportFile
    ) throws IOException {
        AsmFrontendArtifactReport report = reportStructuredAsmArtifactSnapshot(artifact);
        AsmFrontendFailureReportIO.writeArtifactSnapshot(reportFile, report);
        return report;
    }

    public AsmFrontendArtifactReport writeStructuredAsmArtifactSnapshot(
            Path artifact,
            Path reportFile,
            AsmValidationConfig config
    ) throws IOException {
        AsmFrontendArtifactReport report = reportStructuredAsmArtifactSnapshot(artifact, config);
        AsmFrontendFailureReportIO.writeArtifactSnapshot(reportFile, report);
        return report;
    }

    public AsmFrontendArtifactReport writeAndRequireStructuredAsmArtifactSnapshot(
            Path artifact,
            Path reportFile
    ) throws IOException {
        return writeStructuredAsmArtifactSnapshot(artifact, reportFile).requireSuccessful();
    }

    public AsmFrontendArtifactReport writeAndRequireStructuredAsmArtifactSnapshot(
            Path artifact,
            Path reportFile,
            AsmValidationConfig config
    ) throws IOException {
        return writeStructuredAsmArtifactSnapshot(artifact, reportFile, config).requireSuccessful();
    }

    public AsmFrontendFailureReport writeStructuredAsmArtifactReport(Path artifact, Path reportFile) throws IOException {
        AsmFrontendFailureReport report = reportStructuredAsmArtifact(artifact);
        AsmFrontendFailureReportIO.write(reportFile, report);
        return report;
    }

    public AsmFrontendFailureReport writeStructuredAsmArtifactReport(
            Path artifact,
            Path reportFile,
            AsmValidationConfig config
    ) throws IOException {
        AsmFrontendFailureReport report = reportStructuredAsmArtifact(artifact, config);
        AsmFrontendFailureReportIO.write(reportFile, report);
        return report;
    }

    public AsmFrontendFailureReport requireStructuredAsmArtifact(Path artifact) {
        return reportStructuredAsmArtifact(artifact).requireSuccessful();
    }

    public AsmFrontendFailureReport requireStructuredAsmArtifact(Path artifact, AsmValidationConfig config) {
        return reportStructuredAsmArtifact(artifact, config).requireSuccessful();
    }

    public AsmFrontendReadinessReport requireStructuredAsmArtifactReadiness(Path artifact) {
        return reportStructuredAsmArtifactReadiness(artifact).requireSupported();
    }

    public AsmFrontendReadinessReport requireStructuredAsmArtifactReadiness(Path artifact, AsmValidationConfig config) {
        return reportStructuredAsmArtifactReadiness(artifact, config).requireSupported();
    }

    public AsmFrontendFailureReport writeAndRequireStructuredAsmArtifactReport(
            Path artifact,
            Path reportFile
    ) throws IOException {
        return writeStructuredAsmArtifactReport(artifact, reportFile).requireSuccessful();
    }

    public AsmFrontendFailureReport writeAndRequireStructuredAsmArtifactReport(
            Path artifact,
            Path reportFile,
            AsmValidationConfig config
    ) throws IOException {
        return writeStructuredAsmArtifactReport(artifact, reportFile, config).requireSuccessful();
    }

    public GpuIrMethod liftStructuredAsm(
            AsmGpuMethod method,
            String sourceName,
            List<String> sourceLines,
            Consumer<String> diagnosticReporter
    ) {
        try {
            return asmFrontend.validateAndLiftStructured(method);
        } catch (AsmFrontendException exception) {
            reportAsmDiagnostic(method, exception, sourceName, sourceLines, diagnosticReporter);
            throw exception;
        }
    }

    public GpuIrMethod liftLinearAsm(AsmGpuMethod method) {
        return asmFrontend.validateAndLiftLinear(method);
    }

    public GpuIrMethod liftLinearAsm(
            AsmGpuMethod method,
            String sourceName,
            List<String> sourceLines,
            Consumer<String> diagnosticReporter
    ) {
        try {
            return asmFrontend.validateAndLiftLinear(method);
        } catch (AsmFrontendException exception) {
            reportAsmDiagnostic(method, exception, sourceName, sourceLines, diagnosticReporter);
            throw exception;
        }
    }

    public String compileStructuredAsm(AsmGpuMethod kernelMethod, List<AsmGpuMethod> helperMethods) {
        return asmFrontend.validateLowerAndEmitStructured(kernelMethod, helperMethods);
    }

    public GpuFrontendCompilationResult compileStructuredAsmResult(
            AsmGpuMethod kernelMethod,
            List<AsmGpuMethod> helperMethods,
            String derivedOpenClResource
    ) {
        return asmFrontend.compileStructured(kernelMethod, helperMethods, List.of(), derivedOpenClResource);
    }

    public GpuFrontendCompilationResult compileStructuredAsmResult(
            AsmGpuMethod kernelMethod,
            List<AsmGpuMethod> helperMethods
    ) {
        return compileStructuredAsmResult(
                kernelMethod,
                helperMethods,
                GpuFrontendResourcePaths.openClResource(kernelMethod.parsedMethod())
        );
    }

    public String compileStructuredAsm(
            AsmGpuMethod kernelMethod,
            List<AsmGpuMethod> helperMethods,
            String sourceName,
            List<String> sourceLines,
            Consumer<String> diagnosticReporter
    ) {
        return compileStructuredAsm(kernelMethod, helperMethods, List.of(), sourceName, sourceLines, diagnosticReporter);
    }

    public String compileStructuredAsm(
            AsmGpuMethod kernelMethod,
            List<AsmGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs
    ) {
        return asmFrontend.validateLowerAndEmitStructured(kernelMethod, helperMethods, structs);
    }

    public GpuFrontendCompilationResult compileStructuredAsmResult(
            AsmGpuMethod kernelMethod,
            List<AsmGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs,
            String derivedOpenClResource
    ) {
        return asmFrontend.compileStructured(kernelMethod, helperMethods, structs, derivedOpenClResource);
    }

    public GpuFrontendCompilationResult compileStructuredAsmResult(
            AsmGpuMethod kernelMethod,
            List<AsmGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs
    ) {
        return compileStructuredAsmResult(
                kernelMethod,
                helperMethods,
                structs,
                GpuFrontendResourcePaths.openClResource(kernelMethod.parsedMethod())
        );
    }

    public String compileStructuredAsm(
            AsmGpuMethod kernelMethod,
            List<AsmGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs,
            String sourceName,
            List<String> sourceLines,
            Consumer<String> diagnosticReporter
    ) {
        try {
            return asmFrontend.validateLowerAndEmitStructured(kernelMethod, helperMethods, structs);
        } catch (AsmFrontendException exception) {
            reportAsmDiagnostic(kernelMethod, exception, sourceName, sourceLines, diagnosticReporter);
            throw exception;
        }
    }

    public GpuFrontendCompilationResult compileStructuredAsmResult(
            AsmGpuMethod kernelMethod,
            List<AsmGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs,
            String derivedOpenClResource,
            String sourceName,
            List<String> sourceLines,
            Consumer<String> diagnosticReporter
    ) {
        try {
            return asmFrontend.compileStructured(kernelMethod, helperMethods, structs, derivedOpenClResource);
        } catch (AsmFrontendException exception) {
            reportAsmDiagnostic(kernelMethod, exception, sourceName, sourceLines, diagnosticReporter);
            throw exception;
        }
    }

    private void reportAsmDiagnostic(
            AsmGpuMethod method,
            AsmFrontendException exception,
            String sourceName,
            List<String> sourceLines,
            Consumer<String> diagnosticReporter
    ) {
        Consumer<String> reporter = diagnosticReporter == null ? ignored -> { } : diagnosticReporter;
        List<String> lines = sourceLines == null ? List.of() : sourceLines;
        asmDiagnosticAdapter.render(method, exception, sourceName, lines)
                .ifPresent(reporter);
    }
}
