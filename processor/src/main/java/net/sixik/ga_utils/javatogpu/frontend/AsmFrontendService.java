package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.asm.AsmExpressionLifter;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmFrontendException;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmFrontendFailureMetadata;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmLiftingResult;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmValidationConfig;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassRunner;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationDiagnosticPolicy;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationMode;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationReportEntry;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationRunner;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;
import net.sixik.ga_utils.javatogpu.frontend.opencl.OpenClKernelEmitter;
import net.sixik.ga_utils.javatogpu.frontend.opencl.OpenClKernelNaming;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class AsmFrontendService {

    private final AsmExpressionLifter lifter;
    private final OpenClKernelEmitter emitter;
    private final GpuIrPassRunner passRunner;
    private final GpuIrValidationRunner validationRunner;

    public AsmFrontendService(
            AsmExpressionLifter lifter,
            OpenClKernelEmitter emitter,
            GpuIrPassRunner passRunner
    ) {
        this(lifter, emitter, passRunner, GpuIrValidationRunner.disabled());
    }

    public AsmFrontendService(
            AsmExpressionLifter lifter,
            OpenClKernelEmitter emitter,
            GpuIrPassRunner passRunner,
            GpuIrValidationRunner validationRunner
    ) {
        this.lifter = lifter;
        this.emitter = emitter;
        this.passRunner = passRunner;
        this.validationRunner = validationRunner;
    }

    public AsmFrontendService(
            AsmExpressionLifter lifter,
            OpenClKernelEmitter emitter
    ) {
        this(lifter, emitter, GpuIrPassRunner.loadFromServiceLoader());
    }

    public static AsmFrontendService createDefault() {
        return create(GpuIntrinsicDatabase.createDefault());
    }

    public static AsmFrontendService create(GpuIntrinsicDatabase intrinsicDatabase) {
        return create(intrinsicDatabase, GpuIrValidationMode.OFF);
    }

    public static AsmFrontendService create(GpuIntrinsicDatabase intrinsicDatabase, GpuIrValidationMode validationMode) {
        return create(intrinsicDatabase, validationMode, ignored -> { });
    }

    public static AsmFrontendService create(
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

    public static AsmFrontendService create(
            GpuIntrinsicDatabase intrinsicDatabase,
            GpuIrValidationMode validationMode,
            GpuIrValidationDiagnosticPolicy diagnosticPolicy,
            Consumer<String> diagnosticReporter
    ) {
        return create(intrinsicDatabase, validationMode, diagnosticPolicy, diagnosticReporter, ignored -> { });
    }

    public static AsmFrontendService create(
            GpuIntrinsicDatabase intrinsicDatabase,
            GpuIrValidationMode validationMode,
            GpuIrValidationDiagnosticPolicy diagnosticPolicy,
            Consumer<String> diagnosticReporter,
            Consumer<GpuIrValidationReportEntry> reportSink
    ) {
        return new AsmFrontendService(
                new AsmExpressionLifter(intrinsicDatabase),
                new OpenClKernelEmitter(),
                GpuIrPassRunner.loadFromServiceLoader(),
                GpuIrValidationRunner.loadFromServiceLoader(validationMode, diagnosticPolicy, diagnosticReporter, reportSink)
        );
    }

    public GpuIrMethod validateAndLiftLinear(AsmGpuMethod method) {
        validateSignatureCompatibility(method);
        return lifter.liftLinearMethod(
                method.ownerInternalName(),
                method.methodNode(),
                validationConfig(List.of(), List.of())
        ).irMethod();
    }

    public GpuIrMethod validateAndLiftStructured(AsmGpuMethod method) {
        validateSignatureCompatibility(method);
        return lifter.liftStructuredMethod(
                method.ownerInternalName(),
                method.methodNode(),
                validationConfig(List.of(), List.of())
        ).irMethod();
    }

    public String validateLowerAndEmitStructured(AsmGpuMethod kernelMethod, List<AsmGpuMethod> helperMethods) {
        return validateLowerAndEmitStructured(kernelMethod, helperMethods, List.of());
    }

    public String validateLowerAndEmitStructured(
            AsmGpuMethod kernelMethod,
            List<AsmGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs
    ) {
        validateSignatureCompatibility(kernelMethod);
        helperMethods.forEach(this::validateSignatureCompatibility);

        AsmValidationConfig validationConfig = validationConfig(helperMethods, structs);

        List<GpuIrCompiledMethod> compiledMethods = new ArrayList<>();
        for (AsmGpuMethod helperMethod : helperMethods) {
            AsmLiftingResult liftingResult = lifter.liftStructuredMethod(
                    helperMethod.ownerInternalName(),
                    helperMethod.methodNode(),
                    validationConfig
            );
            compiledMethods.add(new GpuIrCompiledMethod(
                    helperMethod.parsedMethod(),
                    liftingResult.irMethod(),
                    emittedHelperName(helperMethod.parsedMethod()),
                    liftingResult.helperDependencies()
            ));
        }

        AsmLiftingResult kernelResult = lifter.liftStructuredMethod(
                kernelMethod.ownerInternalName(),
                kernelMethod.methodNode(),
                validationConfig
        );
        GpuIrCompiledMethod compiledKernel = new GpuIrCompiledMethod(
                kernelMethod.parsedMethod(),
                kernelResult.irMethod(),
                OpenClKernelNaming.toEntryPointName(kernelMethod.parsedMethod().name()),
                kernelResult.helperDependencies()
        );
        passRunner.run(compiledKernel, compiledMethods, structs);
        validationRunner.run(compiledKernel, compiledMethods, structs);

        return emitter.emitProgram(
                compiledKernel,
                GpuProgramAssemblySupport.selectReachableHelpers(
                        compiledKernel,
                        compiledMethods,
                        "Lifted ASM kernel references unknown helper: ",
                        "Recursive ASM helper calls are not supported: "
                ),
                structs
        );
    }

    private void validateSignatureCompatibility(AsmGpuMethod method) {
        ParsedGpuMethod parsedMethod = method.parsedMethod();
        Type methodType = Type.getMethodType(method.methodNode().desc);
        String methodLabel = method.ownerInternalName() + "." + parsedMethod.name();

        if (parsedMethod.parameters().size() != methodType.getArgumentTypes().length) {
            throw signatureMismatch(method,
                    "ASM frontend signature mismatch for "
                            + methodLabel
                            + ": parsed method parameter count does not match the ASM descriptor; regenerate the parsed signature from the same source/ASM pair"
            );
        }

        for (int index = 0; index < parsedMethod.parameters().size(); index++) {
            String parsedType = parsedMethod.parameters().get(index).javaType();
            String asmType = toJavaTypeName(methodType.getArgumentTypes()[index]);
            if (!parsedType.equals(asmType)) {
                throw signatureMismatch(method,
                        "ASM frontend signature mismatch for "
                                + methodLabel
                                + ": parsed parameter type does not match the ASM descriptor at index "
                                + index
                                + "; expected "
                                + parsedType
                                + " but got "
                                + asmType
                                + "; regenerate the parsed signature from the same source/ASM pair"
                );
            }
        }

        String parsedReturnType = parsedMethod.returnType();
        String asmReturnType = toJavaTypeName(methodType.getReturnType());
        if (!parsedReturnType.equals(asmReturnType)) {
            throw signatureMismatch(method,
                    "ASM frontend signature mismatch for "
                            + methodLabel
                            + ": parsed return type does not match the ASM descriptor; expected "
                            + parsedReturnType
                            + " but got "
                            + asmReturnType
                            + "; regenerate the parsed signature from the same source/ASM pair"
            );
        }
    }

    private AsmFrontendException signatureMismatch(AsmGpuMethod method, String detail) {
        return new AsmFrontendException(detail, new AsmFrontendFailureMetadata(
                "signatureMismatch",
                method.ownerInternalName(),
                method.methodNode().name,
                method.methodNode().desc,
                0,
                -1,
                "",
                detail
        ));
    }

    private AsmValidationConfig validationConfig(List<AsmGpuMethod> helperMethods, List<ParsedGpuStruct> structs) {
        AsmValidationConfig config = AsmValidationConfig.defaultConfig();
        for (AsmGpuMethod helperMethod : helperMethods) {
            config = config.withHelperOwner(helperMethod.ownerInternalName());
        }
        for (ParsedGpuStruct struct : structs) {
            config = config.withStructOwner(internalName(struct.ownerQualifiedName(), struct.ownerSimpleName()));
        }
        return config;
    }

    private String emittedHelperName(ParsedGpuMethod parsedMethod) {
        List<String> argumentTypes = parsedMethod.parameters().stream()
                .map(parameter -> parameter.javaType())
                .toList();
        return OpenClKernelNaming.toHelperFunctionName(
                parsedMethod.ownerSimpleName(),
                parsedMethod.name(),
                argumentTypes
        );
    }

    private String internalName(String qualifiedName, String simpleName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return simpleName;
        }
        return qualifiedName.replace('.', '/');
    }

    private String toJavaTypeName(Type type) {
        return switch (type.getSort()) {
            case Type.VOID -> "void";
            case Type.BOOLEAN -> "boolean";
            case Type.CHAR -> "char";
            case Type.BYTE -> "byte";
            case Type.SHORT -> "short";
            case Type.INT -> "int";
            case Type.FLOAT -> "float";
            case Type.LONG -> "long";
            case Type.DOUBLE -> "double";
            case Type.ARRAY -> toJavaTypeName(type.getElementType()) + "[]";
            case Type.OBJECT -> simpleInternalName(type.getInternalName());
            default -> throw new AsmFrontendException(
                    "Unsupported ASM type in GPU frontend: "
                            + type.getDescriptor()
                            + "; use primitive scalars, single-dimension arrays, supported vectors, pointers, images/samplers, or @GPUStruct values"
            );
        };
    }

    private String simpleInternalName(String internalName) {
        int separator = internalName.lastIndexOf('/');
        return separator >= 0 ? internalName.substring(separator + 1) : internalName;
    }

}
