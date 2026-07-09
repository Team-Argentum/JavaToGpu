package net.sixik.ga_utils.javatogpu.frontend;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.Type;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBodyIndex;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuConstantDataMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuConstantMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModuleMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuOptimizerPolicyMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuStructFieldMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuStructMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTextBodyRenderer;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBodyBuilder;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.opencl.OpenClKernelEmitter;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrFieldAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrStructInit;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassRunner;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrDoWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrExpressionStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrLoopBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrPrivateArrayDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitchCase;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationDiagnosticPolicy;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationMode;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationReportEntry;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationRunner;
import net.sixik.ga_utils.javatogpu.frontend.lowering.GpuIrLowerer;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;
import net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser;
import net.sixik.ga_utils.javatogpu.frontend.validation.GpuSubsetValidator;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class GpuFrontendService {

    private final GpuMethodParser parser;
    private final GpuSubsetValidator validator;
    private final GpuIrLowerer lowerer;
    private final OpenClKernelEmitter emitter;
    private final GpuIrPassRunner passRunner;
    private final GpuIrValidationRunner validationRunner;

    public GpuFrontendService(
            GpuMethodParser parser,
            GpuSubsetValidator validator,
            GpuIrLowerer lowerer,
            OpenClKernelEmitter emitter,
            GpuIrPassRunner passRunner
    ) {
        this(
                parser,
                validator,
                lowerer,
                emitter,
                passRunner,
                GpuIrValidationRunner.disabled()
        );
    }

    public GpuFrontendService(
            GpuMethodParser parser,
            GpuSubsetValidator validator,
            GpuIrLowerer lowerer,
            OpenClKernelEmitter emitter,
            GpuIrPassRunner passRunner,
            GpuIrValidationRunner validationRunner
    ) {
        this.parser = parser;
        this.validator = validator;
        this.lowerer = lowerer;
        this.emitter = emitter;
        this.passRunner = passRunner;
        this.validationRunner = validationRunner;
    }

    public GpuFrontendService(
            GpuMethodParser parser,
            GpuSubsetValidator validator,
            GpuIrLowerer lowerer,
            OpenClKernelEmitter emitter
    ) {
        this(parser, validator, lowerer, emitter, GpuIrPassRunner.loadFromServiceLoader());
    }

    public static GpuFrontendService createDefault() {
        return create(GpuIntrinsicDatabase.createDefault());
    }

    public static GpuFrontendService create(GpuIntrinsicDatabase intrinsicDatabase) {
        return create(intrinsicDatabase, GpuIrValidationMode.OFF);
    }

    public static GpuFrontendService create(GpuIntrinsicDatabase intrinsicDatabase, GpuIrValidationMode validationMode) {
        return create(intrinsicDatabase, validationMode, ignored -> { });
    }

    public static GpuFrontendService create(
            GpuIntrinsicDatabase intrinsicDatabase,
            GpuIrValidationMode validationMode,
            Consumer<String> diagnosticReporter
    ) {
        return create(
                intrinsicDatabase,
                validationMode,
                GpuIrValidationDiagnosticPolicy.SUMMARY,
                diagnosticReporter
        );
    }

    public static GpuFrontendService create(
            GpuIntrinsicDatabase intrinsicDatabase,
            GpuIrValidationMode validationMode,
            GpuIrValidationDiagnosticPolicy diagnosticPolicy,
            Consumer<String> diagnosticReporter
    ) {
        return create(intrinsicDatabase, validationMode, diagnosticPolicy, diagnosticReporter, ignored -> { });
    }

    public static GpuFrontendService create(
            GpuIntrinsicDatabase intrinsicDatabase,
            GpuIrValidationMode validationMode,
            GpuIrValidationDiagnosticPolicy diagnosticPolicy,
            Consumer<String> diagnosticReporter,
            Consumer<GpuIrValidationReportEntry> reportSink
    ) {
        return new GpuFrontendService(
                new GpuMethodParser(),
                new GpuSubsetValidator(intrinsicDatabase),
                new GpuIrLowerer(intrinsicDatabase),
                new OpenClKernelEmitter(),
                GpuIrPassRunner.loadFromServiceLoader(),
                GpuIrValidationRunner.loadFromServiceLoader(validationMode, diagnosticPolicy, diagnosticReporter, reportSink)
        );
    }

    public ParsedGpuMethod parseAndValidate(String methodSource) {
        ParsedGpuMethod method = parser.parseMethod(methodSource);
        validator.validateKernel(method, List.of(), List.of());
        return method;
    }

    public GpuIrMethod parseValidateAndLower(String methodSource) {
        ParsedGpuMethod method = parseAndValidate(methodSource);
        return lowerer.lower(method);
    }

    public String parseValidateLowerAndEmit(String methodSource) {
        ParsedGpuMethod method = parseAndValidate(methodSource);
        GpuIrMethod irMethod = lowerer.lower(method);
        GpuIrCompiledMethod compiledMethod = new GpuIrCompiledMethod(method, irMethod, method.name(), List.of());
        passRunner.run(compiledMethod, List.of(), List.of());
        validationRunner.run(compiledMethod, List.of(), List.of());
        return emitter.emit(method, irMethod);
    }

    public String parseValidateLowerAndEmit(String methodSource, List<String> helperMethodSources) {
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource);
        List<ParsedGpuMethod> helperMethods = helperMethodSources.stream()
                .map(parser::parseMethod)
                .toList();

        return validateLowerAndEmit(kernelMethod, helperMethods, List.of());
    }

    public String validateLowerAndEmit(ParsedGpuMethod kernelMethod, List<ParsedGpuMethod> helperMethods) {
        return validateLowerAndEmit(kernelMethod, helperMethods, List.of());
    }

    public String validateLowerAndEmit(
            ParsedGpuMethod kernelMethod,
            List<ParsedGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs
    ) {
        return compile(kernelMethod, helperMethods, structs, "").openClSource();
    }

    public GpuFrontendCompilationResult compile(
            ParsedGpuMethod kernelMethod,
            List<ParsedGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs,
            String derivedOpenClResource
    ) {
        List<ParsedGpuStruct> relevantStructs = selectRelevantStructs(kernelMethod, helperMethods, structs);

        validator.validateKernel(kernelMethod, helperMethods, relevantStructs);

        List<GpuIrCompiledMethod> compiledMethods = lowerer.lower(kernelMethod, helperMethods, relevantStructs);
        List<GpuIrCompiledMethod> compiledHelpers = compiledMethods.subList(0, helperMethods.size());
        GpuIrCompiledMethod compiledKernel = compiledMethods.get(compiledMethods.size() - 1);
        passRunner.run(compiledKernel, compiledHelpers, relevantStructs);
        validationRunner.run(compiledKernel, compiledHelpers, relevantStructs);
        List<GpuIrCompiledMethod> reachableHelpers = GpuProgramAssemblySupport.selectReachableHelpers(
                compiledKernel,
                compiledHelpers,
                "Lowered kernel references unknown helper: ",
                "Recursive @CCode helper calls are not supported: "
        );
        String openClSource = emitter.emitProgram(
                compiledKernel,
                reachableHelpers,
                relevantStructs
        );
        return new GpuFrontendCompilationResult(
                openClSource,
                buildIrGpuArtifact(compiledKernel, reachableHelpers, relevantStructs, derivedOpenClResource, "java-source")
        );
    }

    static IrGpuArtifact buildIrGpuArtifact(
            GpuIrCompiledMethod compiledKernel,
            List<GpuIrCompiledMethod> helperMethods,
            List<ParsedGpuStruct> structs,
            String derivedOpenClResource,
            String sourceFrontend
    ) {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.sourceFrontendV1(sourceFrontend),
                new IrGpuModule(
                        compiledKernel.parsedMethod().name(),
                        compiledKernel.emittedName(),
                        compiledKernel.parsedMethod().openClAttributes(),
                        helperMethods.stream()
                                .map(helper -> new IrGpuModuleMethod(
                                        helper.parsedMethod().name(),
                                        helper.emittedName(),
                                        helper.parsedMethod().returnType(),
                                        buildMethodParameters(helper),
                                        helper.parsedMethod().openClAttributes(),
                                        helper.parsedMethod().inline()
                                ))
                                .toList(),
                        structs.stream()
                                .map(ParsedGpuStruct::ownerQualifiedName)
                                .toList(),
                        buildIrGpuMethodBodies(compiledKernel, helperMethods, sourceFrontend)
                ),
                buildEntryParameters(compiledKernel),
                buildLaunchMetadata(compiledKernel),
                IrGpuValidationMetadata.frontendSubset(),
                buildFeatureMetadata(compiledKernel),
                buildOptimizerPolicyMetadata(compiledKernel),
                IrGpuRegenerationMetadata.transitionalIrText(),
                buildStructMetadata(structs),
                buildConstantMetadata(compiledKernel, helperMethods, structs),
                buildConstantDataMetadata(compiledKernel, helperMethods, structs),
                List.of(IrGpuBackendOutput.openClSource(derivedOpenClResource)),
                "opencl",
                "off"
        );
    }

    private static List<IrGpuEntryParameter> buildEntryParameters(GpuIrCompiledMethod compiledKernel) {
        return buildMethodParameters(compiledKernel);
    }

    private static List<IrGpuEntryParameter> buildMethodParameters(GpuIrCompiledMethod compiledMethod) {
        return compiledMethod.parsedMethod().parameters().stream()
                .map(parameter -> new IrGpuEntryParameter(
                        parameter.name(),
                        parameter.javaType(),
                        parameter.addressSpace().name(),
                        parameter.constant(),
                        parameter.openClQualifiers()
                ))
                .toList();
    }

    private static IrGpuLaunchMetadata buildLaunchMetadata(GpuIrCompiledMethod compiledKernel) {
        int requiredDimensions = Math.max(1, Math.min(3, requiredLaunchDimensions(compiledKernel)));
        return new IrGpuLaunchMetadata(requiredDimensions, "first-buffer-parameter", true);
    }

    private static int requiredLaunchDimensions(GpuIrCompiledMethod compiledMethod) {
        String body = IrGpuTextBodyRenderer.render(compiledMethod);
        int dimensions = 1;
        if (body.contains("get_global_id template=\"get_global_id($0)\" args=[1]")
                || body.contains("get_global_size template=\"get_global_size($0)\" args=[1]")) {
            dimensions = 2;
        }
        if (body.contains("get_global_id template=\"get_global_id($0)\" args=[2]")
                || body.contains("get_global_size template=\"get_global_size($0)\" args=[2]")) {
            dimensions = 3;
        }
        return dimensions;
    }

    private static IrGpuFeatureMetadata buildFeatureMetadata(GpuIrCompiledMethod compiledKernel) {
        ArrayList<String> requiredFeatures = new ArrayList<>();
        for (IrGpuEntryParameter parameter : buildEntryParameters(compiledKernel)) {
            if ("LOCAL".equals(parameter.addressSpace()) && !requiredFeatures.contains("opencl.local-memory")) {
                requiredFeatures.add("opencl.local-memory");
            }
            if (parameter.javaType().startsWith("double") && !requiredFeatures.contains("fp64")) {
                requiredFeatures.add("fp64");
            }
            if (parameter.javaType().contains("Image") && !requiredFeatures.contains("images")) {
                requiredFeatures.add("images");
            }
        }
        return new IrGpuFeatureMetadata(requiredFeatures, List.of("opencl-source-compat"));
    }

    private static IrGpuOptimizerPolicyMetadata buildOptimizerPolicyMetadata(GpuIrCompiledMethod compiledKernel) {
        MethodDeclaration declaration = compiledKernel.parsedMethod().declaration();
        if (declaration == null) {
            return IrGpuOptimizerPolicyMetadata.defaultStrict();
        }

        return declaration.getAnnotationByName("GPUOptimize")
                .map(annotation -> {
                    boolean fastMath = annotation.isNormalAnnotationExpr()
                            && annotation.asNormalAnnotationExpr().getPairs().stream()
                            .filter(pair -> pair.getNameAsString().equals("fastMath"))
                            .findFirst()
                            .map(pair -> Boolean.parseBoolean(pair.getValue().toString()))
                            .orElse(false);
                    return IrGpuOptimizerPolicyMetadata.fromGpuOptimize(fastMath);
                })
                .orElseGet(IrGpuOptimizerPolicyMetadata::defaultStrict);
    }

    private static List<IrGpuStructMetadata> buildStructMetadata(List<ParsedGpuStruct> structs) {
        return structs.stream()
                .map(struct -> new IrGpuStructMetadata(
                        struct.ownerQualifiedName(),
                        struct.ownerSimpleName(),
                        struct.fields().stream()
                                .map(field -> new IrGpuStructFieldMetadata(
                                        field.name(),
                                        field.javaType(),
                                        field.openClAttributes()
                                ))
                                .toList(),
                        struct.openClAttributes()
                ))
                .toList();
    }

    private static List<IrGpuConstantMetadata> buildConstantMetadata(
            GpuIrCompiledMethod compiledKernel,
            List<GpuIrCompiledMethod> helperMethods,
            List<ParsedGpuStruct> structs
    ) {
        java.util.LinkedHashMap<String, IrGpuConstantMetadata> constants = new java.util.LinkedHashMap<>();
        collectConstants(constants, compiledKernel.parsedMethod().constants());
        helperMethods.forEach(helper -> collectConstants(constants, helper.parsedMethod().constants()));
        structs.forEach(struct -> collectConstants(constants, struct.constants()));
        return List.copyOf(constants.values());
    }

    private static void collectConstants(
            java.util.LinkedHashMap<String, IrGpuConstantMetadata> constants,
            List<net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstant> sourceConstants
    ) {
        for (net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstant constant : sourceConstants) {
            IrGpuConstantMetadata metadata = new IrGpuConstantMetadata(
                    constant.ownerQualifiedName(),
                    constant.ownerSimpleName(),
                    constant.name(),
                    constant.javaType(),
                    constant.sourceText()
            );
            constants.putIfAbsent(metadata.ownerQualifiedName() + "#" + metadata.name(), metadata);
        }
    }

    private static List<IrGpuConstantDataMetadata> buildConstantDataMetadata(
            GpuIrCompiledMethod compiledKernel,
            List<GpuIrCompiledMethod> helperMethods,
            List<ParsedGpuStruct> structs
    ) {
        java.util.LinkedHashMap<String, IrGpuConstantDataMetadata> constantData = new java.util.LinkedHashMap<>();
        collectConstantData(constantData, compiledKernel.parsedMethod().constantData());
        helperMethods.forEach(helper -> collectConstantData(constantData, helper.parsedMethod().constantData()));
        structs.forEach(struct -> collectConstantData(constantData, struct.constantData()));
        return List.copyOf(constantData.values());
    }

    private static void collectConstantData(
            java.util.LinkedHashMap<String, IrGpuConstantDataMetadata> constantData,
            List<net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstantData> sourceConstants
    ) {
        for (net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstantData constant : sourceConstants) {
            IrGpuConstantDataMetadata metadata = new IrGpuConstantDataMetadata(
                    constant.ownerQualifiedName(),
                    constant.ownerSimpleName(),
                    constant.name(),
                    constant.javaType(),
                    constant.initializerSource(),
                    constant.kind().name()
            );
            constantData.putIfAbsent(metadata.ownerQualifiedName() + "#" + metadata.name(), metadata);
        }
    }

    private static List<IrGpuMethodBody> buildIrGpuMethodBodies(
            GpuIrCompiledMethod compiledKernel,
            List<GpuIrCompiledMethod> helperMethods,
            String sourceFrontend
    ) {
        ArrayList<IrGpuMethodBody> methodBodies = new ArrayList<>();
        methodBodies.add(IrGpuMethodBody.entry(
                compiledKernel.parsedMethod().name(),
                compiledKernel.emittedName(),
                IrGpuTextBodyRenderer.render(compiledKernel),
                IrGpuTypedBodyBuilder.fromStatements(compiledKernel.irMethod().statements()),
                buildBodyIndex(compiledKernel),
                compiledKernel.helperDependencies(),
                sourceLocation(compiledKernel, sourceFrontend)
        ));
        helperMethods.stream()
                .map(helper -> buildIrGpuHelperMethodBody(helper, sourceFrontend))
                .forEach(methodBodies::add);
        return List.copyOf(methodBodies);
    }

    private static IrGpuMethodBody buildIrGpuHelperMethodBody(
            GpuIrCompiledMethod helper,
            String sourceFrontend
    ) {
        IrGpuSourceLocation sourceLocation = sourceLocation(helper, sourceFrontend);
        if (!helper.parsedMethod().nativeCode().isBlank()) {
            return IrGpuMethodBody.nativeOpenClHelper(
                    helper.parsedMethod().name(),
                    helper.emittedName(),
                    helper.parsedMethod().nativeCode(),
                    helper.helperDependencies(),
                    sourceLocation
            );
        }
        return IrGpuMethodBody.helper(
                helper.parsedMethod().name(),
                helper.emittedName(),
                IrGpuTextBodyRenderer.render(helper),
                IrGpuTypedBodyBuilder.fromStatements(helper.irMethod().statements()),
                buildBodyIndex(helper),
                helper.helperDependencies(),
                sourceLocation
        );
    }

    private static IrGpuBodyIndex buildBodyIndex(GpuIrCompiledMethod compiledMethod) {
        BodyIndexCollector collector = new BodyIndexCollector();
        collector.visitStatements(compiledMethod.irMethod().statements());
        return collector.toBodyIndex();
    }

    private static final class BodyIndexCollector {
        private int statementCount;
        private final java.util.LinkedHashSet<String> statementKinds = new java.util.LinkedHashSet<>();
        private final java.util.LinkedHashSet<String> expressionKinds = new java.util.LinkedHashSet<>();
        private final java.util.LinkedHashSet<String> intrinsicCalls = new java.util.LinkedHashSet<>();
        private final java.util.LinkedHashSet<String> helperCalls = new java.util.LinkedHashSet<>();
        private boolean writesMemory;
        private boolean hasControlFlow;

        private void visitStatements(List<GpuIrStatement> statements) {
            for (GpuIrStatement statement : statements) {
                visitStatement(statement);
            }
        }

        private void visitStatement(GpuIrStatement statement) {
            if (statement == null) {
                return;
            }
            statementCount++;
            statementKinds.add(statement.getClass().getSimpleName());
            if (statement instanceof GpuIrVariableDeclaration declaration) {
                visitExpression(declaration.initializer());
                return;
            }
            if (statement instanceof GpuIrPrivateArrayDeclaration declaration) {
                visitExpression(declaration.size());
                return;
            }
            if (statement instanceof GpuIrAssignment assignment) {
                writesMemory = true;
                visitExpression(assignment.target());
                visitExpression(assignment.value());
                return;
            }
            if (statement instanceof GpuIrExpressionStatement expressionStatement) {
                visitExpression(expressionStatement.expression());
                return;
            }
            if (statement instanceof GpuIrForLoop loop) {
                hasControlFlow = true;
                visitStatement(loop.initializer());
                visitExpression(loop.condition());
                visitStatement(loop.update());
                visitStatements(loop.body());
                return;
            }
            if (statement instanceof GpuIrIf ifStatement) {
                hasControlFlow = true;
                visitExpression(ifStatement.condition());
                visitStatements(ifStatement.thenBranch());
                visitStatements(ifStatement.elseBranch());
                return;
            }
            if (statement instanceof GpuIrWhileLoop loop) {
                hasControlFlow = true;
                visitExpression(loop.condition());
                visitStatements(loop.body());
                return;
            }
            if (statement instanceof GpuIrDoWhileLoop loop) {
                hasControlFlow = true;
                visitStatements(loop.body());
                visitExpression(loop.condition());
                return;
            }
            if (statement instanceof GpuIrSwitch switchStatement) {
                hasControlFlow = true;
                visitExpression(switchStatement.selector());
                for (GpuIrSwitchCase switchCase : switchStatement.cases()) {
                    switchCase.labels().forEach(this::visitExpression);
                    visitStatements(switchCase.statements());
                }
                return;
            }
            if (statement instanceof GpuIrReturn gpuIrReturn) {
                visitExpression(gpuIrReturn.value());
                return;
            }
            if (statement instanceof GpuIrBreak || statement instanceof GpuIrContinue || statement instanceof GpuIrLoopBreak) {
                hasControlFlow = true;
            }
        }

        private void visitExpression(GpuIrExpression expression) {
            if (expression == null) {
                return;
            }
            expressionKinds.add(expression.getClass().getSimpleName());
            if (expression instanceof GpuIrArrayAccess arrayAccess) {
                visitExpression(arrayAccess.index());
                return;
            }
            if (expression instanceof GpuIrBinary binary) {
                visitExpression(binary.left());
                visitExpression(binary.right());
                return;
            }
            if (expression instanceof GpuIrCast cast) {
                visitExpression(cast.expression());
                return;
            }
            if (expression instanceof GpuIrFieldAccess fieldAccess) {
                visitExpression(fieldAccess.target());
                return;
            }
            if (expression instanceof GpuIrHelperCall helperCall) {
                helperCalls.add(helperCall.helperName());
                helperCall.arguments().forEach(this::visitExpression);
                return;
            }
            if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
                intrinsicCalls.add(intrinsicCall.backendName());
                visitExpression(intrinsicCall.receiver());
                intrinsicCall.arguments().forEach(this::visitExpression);
                return;
            }
            if (expression instanceof GpuIrStructInit structInit) {
                structInit.arguments().forEach(this::visitExpression);
                return;
            }
            if (expression instanceof GpuIrTernary ternary) {
                visitExpression(ternary.condition());
                visitExpression(ternary.whenTrue());
                visitExpression(ternary.whenFalse());
                return;
            }
            if (expression instanceof GpuIrUnary unary) {
                visitExpression(unary.operand());
                return;
            }
            if (expression instanceof GpuIrLiteral || expression instanceof GpuIrVariableRef) {
                return;
            }
        }

        private IrGpuBodyIndex toBodyIndex() {
            return new IrGpuBodyIndex(
                    statementCount,
                    List.copyOf(statementKinds),
                    List.copyOf(expressionKinds),
                    List.copyOf(intrinsicCalls),
                    List.copyOf(helperCalls),
                    writesMemory,
                    hasControlFlow
            );
        }
    }

    private static IrGpuSourceLocation sourceLocation(GpuIrCompiledMethod compiledMethod, String fallbackSourceKind) {
        ParsedGpuMethod parsedMethod = compiledMethod.parsedMethod();
        if (parsedMethod.declaration() == null || parsedMethod.declaration().getRange().isEmpty()) {
            return new IrGpuSourceLocation(fallbackSourceKind, parsedMethod.ownerQualifiedName(), parsedMethod.name(), -1, -1, -1, -1);
        }
        com.github.javaparser.Range range = parsedMethod.declaration().getRange().get();
        return new IrGpuSourceLocation(
                parsedMethod.nativeDeclaration() ? "asm" : fallbackSourceKind,
                parsedMethod.ownerQualifiedName(),
                parsedMethod.name(),
                range.begin.line,
                range.begin.column,
                range.end.line,
                range.end.column
        );
    }

    private List<ParsedGpuStruct> selectRelevantStructs(
            ParsedGpuMethod kernelMethod,
            List<ParsedGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs
    ) {
        if (structs.isEmpty()) {
            return List.of();
        }

        GpuStructAliasRegistry<ParsedGpuStruct> registry = buildStructAliasRegistry(structs);
        LinkedHashSet<String> reachableStructNames = new LinkedHashSet<>();

        registerMethodStructReferences(kernelMethod, registry, reachableStructNames);
        helperMethods.forEach(helperMethod -> registerMethodStructReferences(helperMethod, registry, reachableStructNames));

        Deque<String> pending = new ArrayDeque<>(reachableStructNames);
        while (!pending.isEmpty()) {
            String structName = pending.removeFirst();
            ParsedGpuStruct struct = registry.resolve(structName);
            if (struct == null) {
                continue;
            }

            for (String nestedStructName : referencedStructNames(struct.fields().stream().map(field -> field.javaType()).toList(), registry)) {
                if (reachableStructNames.add(nestedStructName)) {
                    pending.addLast(nestedStructName);
                }
            }
        }

        return structs.stream()
                .filter(struct -> reachableStructNames.contains(struct.ownerQualifiedName()))
                .toList();
    }

    private void registerMethodStructReferences(
            ParsedGpuMethod method,
            GpuStructAliasRegistry<ParsedGpuStruct> registry,
            Set<String> reachableStructNames
    ) {
        List<String> referencedTypes = new ArrayList<>();
        referencedTypes.add(method.returnType());
        method.parameters().forEach(parameter -> referencedTypes.add(parameter.javaType()));
        if (method.declaration() != null) {
            method.declaration().findAll(Type.class).forEach(type -> referencedTypes.add(type.asString()));
        }
        reachableStructNames.addAll(referencedStructNames(referencedTypes, registry));
    }

    private Set<String> referencedStructNames(List<String> referencedTypes, GpuStructAliasRegistry<ParsedGpuStruct> registry) {
        LinkedHashSet<String> structNames = new LinkedHashSet<>();
        for (String referencedType : referencedTypes) {
            String normalizedType = normalizeReferencedType(referencedType);
            if (normalizedType == null) {
                continue;
            }
            ParsedGpuStruct struct = registry.resolve(normalizedType);
            if (struct != null) {
                structNames.add(struct.ownerQualifiedName());
            }
        }
        return structNames;
    }

    private String normalizeReferencedType(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return null;
        }
        String normalized = GpuTypeSupport.declaredType(typeName.strip());
        while (GpuTypeSupport.isArrayType(normalized)) {
            normalized = GpuTypeSupport.componentType(normalized);
        }
        return normalized;
    }

    private GpuStructAliasRegistry<ParsedGpuStruct> buildStructAliasRegistry(List<ParsedGpuStruct> structs) {
        return GpuStructAliasRegistry.create(
                structs,
                ParsedGpuStruct::ownerSimpleName,
                ParsedGpuStruct::ownerQualifiedName,
                (left, right) -> left.ownerQualifiedName().equals(right.ownerQualifiedName())
        );
    }
}
