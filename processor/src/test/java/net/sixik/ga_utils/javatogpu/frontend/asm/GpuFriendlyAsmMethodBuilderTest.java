package net.sixik.ga_utils.javatogpu.frontend.asm;

import net.sixik.ga_utils.javatogpu.frontend.GpuProgramCompiler;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuFriendlyAsmMethodBuilderTest {

    private static final String DEMO_OWNER = "sample/BuilderDemo";
    private static final String GPU_OWNER = Type.getInternalName(net.sixik.ga_utils.javatogpu.api.GPU.class);

    @Test
    void buildsCanonicalWhileSwitchShapeThroughHelperApi() {
        GpuFriendlyAsmMethodBuilder builder = GpuFriendlyAsmMethodBuilder.staticMethod("kernel", "([I[I)V");

        int inputSlot = builder.parameterSlot(0);
        int outputSlot = builder.parameterSlot(1);
        int indexSlot = builder.newTemp(Type.INT_TYPE);

        builder.pushInt(0);
        builder.storeLocal(indexSlot, Type.INT_TYPE);

        builder.emitCanonicalWhileLoop(loop -> {
            loop.loadLocal(indexSlot, Type.INT_TYPE);
            loop.pushInt(4);
        }, org.objectweb.asm.Opcodes.IF_ICMPGE, loop -> {
            loop.emitCanonicalSwitch(selector -> loop.loadLocal(indexSlot, Type.INT_TYPE), List.of(
                    GpuFriendlyAsmMethodBuilder.SwitchCase.of(0, caseBuilder -> {
                        caseBuilder.emitCanonicalIfElse(cond -> {
                            cond.loadLocal(indexSlot, Type.INT_TYPE);
                        }, org.objectweb.asm.Opcodes.IFNE, thenBlock -> {
                            thenBlock.loadLocal(outputSlot, Type.getType("[I"));
                            thenBlock.loadLocal(indexSlot, Type.INT_TYPE);
                            thenBlock.pushInt(7);
                            thenBlock.emitArrayStore(Type.INT_TYPE);
                            thenBlock.emitBreakLoop();
                        }, elseBlock -> {
                            elseBlock.loadLocal(outputSlot, Type.getType("[I"));
                            elseBlock.loadLocal(indexSlot, Type.INT_TYPE);
                            elseBlock.pushInt(1);
                            elseBlock.emitArrayStore(Type.INT_TYPE);
                            elseBlock.emitBreakSwitch();
                        });
                    }),
                    GpuFriendlyAsmMethodBuilder.SwitchCase.of(1, caseBuilder -> {
                        caseBuilder.loadLocal(outputSlot, Type.getType("[I"));
                        caseBuilder.loadLocal(indexSlot, Type.INT_TYPE);
                        caseBuilder.pushInt(3);
                        caseBuilder.emitArrayStore(Type.INT_TYPE);
                        caseBuilder.emitContinueLoop();
                    })
            ), defaultBlock -> {
                defaultBlock.loadLocal(outputSlot, Type.getType("[I"));
                defaultBlock.loadLocal(indexSlot, Type.INT_TYPE);
                defaultBlock.pushInt(5);
                defaultBlock.emitArrayStore(Type.INT_TYPE);
            });

            loop.iinc(indexSlot, 1);
        });

        builder.emitVoidReturn();

        GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();
        String kernel = compiler.compileStructuredAsm(new AsmGpuMethod(
                DEMO_OWNER,
                parsedMethod("BuilderDemo", "sample.BuilderDemo", "kernel", "void", List.of(
                        globalArrayParameter("input", "int[]"),
                        globalArrayParameter("output", "int[]")
                )),
                builder.toMethodNode()
        ), List.of());

        assertTrue(kernel.contains("while ((tmp2 < 4)) {"));
        assertTrue(kernel.contains("switch (tmp2) {"));
        assertTrue(kernel.contains("goto __jtg_loop_break_0;"));
        assertTrue(kernel.contains("continue;"));
    }

    @Test
    void buildsTempHeavyIntrinsicShapeThroughHelperApi() {
        GpuFriendlyAsmMethodBuilder builder = GpuFriendlyAsmMethodBuilder.staticMethod("kernel", "([F[F)V");

        int inputSlot = builder.parameterSlot(0);
        int outputSlot = builder.parameterSlot(1);
        int idSlot = builder.newTemp(Type.INT_TYPE);

        builder.pushInt(0);
        builder.emitStaticCall(GPU_OWNER, "get_global_id", "(I)I");
        builder.storeLocal(idSlot, Type.INT_TYPE);

        builder.loadLocal(inputSlot, Type.getType("[F"));
        builder.loadLocal(idSlot, Type.INT_TYPE);
        builder.emitArrayLoad(Type.FLOAT_TYPE);
        int valueSlot = builder.emitTempStore(Type.FLOAT_TYPE);

        builder.loadLocal(valueSlot, Type.FLOAT_TYPE);
        builder.pushFloat(2.0f);
        builder.emitInsn(org.objectweb.asm.Opcodes.FADD);
        int sumSlot = builder.emitTempStore(Type.FLOAT_TYPE);

        builder.loadLocal(sumSlot, Type.FLOAT_TYPE);
        builder.emitStaticCall(GPU_OWNER, "sin", "(F)F");
        int resultSlot = builder.emitTempStore(Type.FLOAT_TYPE);

        builder.loadLocal(outputSlot, Type.getType("[F"));
        builder.loadLocal(idSlot, Type.INT_TYPE);
        builder.loadLocal(resultSlot, Type.FLOAT_TYPE);
        builder.emitArrayStore(Type.FLOAT_TYPE);
        builder.emitVoidReturn();

        GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();
        String kernel = compiler.compileStructuredAsm(new AsmGpuMethod(
                DEMO_OWNER,
                parsedMethod("BuilderDemo", "sample.BuilderDemo", "kernel", "void", List.of(
                        globalArrayParameter("input", "float[]"),
                        globalArrayParameter("output", "float[]")
                )),
                builder.toMethodNode()
        ), List.of());

        assertTrue(kernel.contains("tmp4 = (tmp3 + 2.0f);"));
        assertTrue(kernel.contains("tmp5 = sin(tmp4);"));
    }

    private ParsedGpuMethod parsedMethod(
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

    private ParsedGpuParameter globalArrayParameter(String name, String javaType) {
        return new ParsedGpuParameter(name, javaType, GpuAddressSpace.GLOBAL, false);
    }
}
