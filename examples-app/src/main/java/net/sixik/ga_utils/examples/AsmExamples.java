package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.frontend.GpuProgramCompiler;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.asm.GpuFriendlyAsmMethodBuilder;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.objectweb.asm.Type;

import java.util.List;

public final class AsmExamples {

    private static final String ASM_OWNER = "examples/AsmDemo";
    private static final String GPU_OWNER = Type.getInternalName(net.sixik.ga_utils.javatogpu.api.GPU.class);

    private AsmExamples() {
    }

    public static String compileStructuredAsmExample() {
        GpuFriendlyAsmMethodBuilder helperBuilder = GpuFriendlyAsmMethodBuilder.staticMethod("square", "(F)F");
        helperBuilder.loadLocal(helperBuilder.parameterSlot(0), Type.FLOAT_TYPE);
        helperBuilder.loadLocal(helperBuilder.parameterSlot(0), Type.FLOAT_TYPE);
        helperBuilder.emitInsn(org.objectweb.asm.Opcodes.FMUL);
        helperBuilder.emitReturn(Type.FLOAT_TYPE);

        GpuFriendlyAsmMethodBuilder kernelBuilder = GpuFriendlyAsmMethodBuilder.staticMethod("kernel", "([F[F)V");
        int inputSlot = kernelBuilder.parameterSlot(0);
        int outputSlot = kernelBuilder.parameterSlot(1);
        int idSlot = kernelBuilder.newTemp(Type.INT_TYPE);

        kernelBuilder.pushInt(0);
        kernelBuilder.emitStaticCall(GPU_OWNER, "get_global_id", "(I)I");
        kernelBuilder.storeLocal(idSlot, Type.INT_TYPE);

        kernelBuilder.loadLocal(inputSlot, Type.getType("[F"));
        kernelBuilder.loadLocal(idSlot, Type.INT_TYPE);
        kernelBuilder.emitArrayLoad(Type.FLOAT_TYPE);
        kernelBuilder.emitStaticCall(ASM_OWNER, "square", "(F)F");
        int resultSlot = kernelBuilder.emitTempStore(Type.FLOAT_TYPE);

        kernelBuilder.loadLocal(outputSlot, Type.getType("[F"));
        kernelBuilder.loadLocal(idSlot, Type.INT_TYPE);
        kernelBuilder.loadLocal(resultSlot, Type.FLOAT_TYPE);
        kernelBuilder.emitArrayStore(Type.FLOAT_TYPE);
        kernelBuilder.emitVoidReturn();

        GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();
        String opencl = compiler.compileStructuredAsm(
                new AsmGpuMethod(
                        ASM_OWNER,
                        parsedMethod("AsmDemo", "examples.AsmDemo", "kernel", "void", List.of(
                                globalArrayParameter("input", "float[]"),
                                globalArrayParameter("output", "float[]")
                        )),
                        kernelBuilder.toMethodNode()
                ),
                List.of(
                        new AsmGpuMethod(
                                ASM_OWNER,
                                parsedMethod("AsmDemo", "examples.AsmDemo", "square", "float", List.of(
                                        parameter("value", "float")
                                )),
                                helperBuilder.toMethodNode()
                        )
                )
        );

        return "Generated OpenCL from ASM:\n" + opencl;
    }

    private static ParsedGpuMethod parsedMethod(
            String ownerSimpleName,
            String ownerQualifiedName,
            String name,
            String returnType,
            List<ParsedGpuParameter> parameters
    ) {
        return new ParsedGpuMethod(
                ownerSimpleName,
                ownerQualifiedName,
                name,
                returnType,
                parameters,
                List.of(),
                null,
                false,
                List.of(),
                "",
                false
        );
    }

    private static ParsedGpuParameter parameter(String name, String javaType) {
        return new ParsedGpuParameter(name, javaType, GpuAddressSpace.PRIVATE, false, List.of());
    }

    private static ParsedGpuParameter globalArrayParameter(String name, String javaType) {
        return new ParsedGpuParameter(name, javaType, GpuAddressSpace.GLOBAL, false, List.of());
    }
}
