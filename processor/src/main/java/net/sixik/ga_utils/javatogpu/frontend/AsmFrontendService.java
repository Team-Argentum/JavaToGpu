package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.asm.AsmExpressionLifter;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmFrontendException;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmLiftingResult;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmValidationConfig;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;
import net.sixik.ga_utils.javatogpu.frontend.opencl.OpenClKernelEmitter;
import net.sixik.ga_utils.javatogpu.frontend.opencl.OpenClKernelNaming;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

public final class AsmFrontendService {

    private final AsmExpressionLifter lifter;
    private final OpenClKernelEmitter emitter;

    public AsmFrontendService(
            AsmExpressionLifter lifter,
            OpenClKernelEmitter emitter
    ) {
        this.lifter = lifter;
        this.emitter = emitter;
    }

    public static AsmFrontendService createDefault() {
        return create(GpuIntrinsicDatabase.createDefault());
    }

    public static AsmFrontendService create(GpuIntrinsicDatabase intrinsicDatabase) {
        return new AsmFrontendService(
                new AsmExpressionLifter(intrinsicDatabase),
                new OpenClKernelEmitter()
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

        if (parsedMethod.parameters().size() != methodType.getArgumentTypes().length) {
            throw new AsmFrontendException("Parsed method signature does not match ASM descriptor parameter count for "
                    + method.ownerInternalName() + "." + parsedMethod.name());
        }

        for (int index = 0; index < parsedMethod.parameters().size(); index++) {
            String parsedType = parsedMethod.parameters().get(index).javaType();
            String asmType = toJavaTypeName(methodType.getArgumentTypes()[index]);
            if (!parsedType.equals(asmType)) {
                throw new AsmFrontendException("Parsed method parameter type does not match ASM descriptor at index "
                        + index + " for " + method.ownerInternalName() + "." + parsedMethod.name()
                        + ": expected " + parsedType + " but got " + asmType);
            }
        }

        String parsedReturnType = parsedMethod.returnType();
        String asmReturnType = toJavaTypeName(methodType.getReturnType());
        if (!parsedReturnType.equals(asmReturnType)) {
            throw new AsmFrontendException("Parsed method return type does not match ASM descriptor for "
                    + method.ownerInternalName() + "." + parsedMethod.name()
                    + ": expected " + parsedReturnType + " but got " + asmReturnType);
        }
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
            default -> throw new AsmFrontendException("Unsupported ASM type in AsmFrontendService: " + type.getDescriptor());
        };
    }

    private String simpleInternalName(String internalName) {
        int separator = internalName.lastIndexOf('/');
        return separator >= 0 ? internalName.substring(separator + 1) : internalName;
    }

}
