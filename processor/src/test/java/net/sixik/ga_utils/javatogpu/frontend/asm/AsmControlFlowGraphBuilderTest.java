package net.sixik.ga_utils.javatogpu.frontend.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsmControlFlowGraphBuilderTest {

    private static final String KERNEL_OWNER = "sample/Kernel";

    private final AsmControlFlowGraphBuilder builder = new AsmControlFlowGraphBuilder();

    @Test
    void buildsSingleLinearBlockForStraightLineMethod() {
        MethodNode method = methodNode(KERNEL_OWNER, "linear", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.IADD);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmControlFlowGraph graph = builder.build(KERNEL_OWNER, method);

        assertEquals(1, graph.blocks().size());
        AsmBasicBlock entry = graph.entryBlock();
        assertNotNull(entry);
        assertTrue(entry.entry());
        assertEquals(AsmTerminatorKind.RETURN, entry.terminatorKind());
        assertEquals(List.of(), entry.successorIds());
        assertEquals(4, entry.instructions().size());
        assertEquals("ILOAD", entry.instructions().get(0).opcodeName());
        assertEquals("IRETURN", entry.instructions().get(3).opcodeName());
    }

    @Test
    void buildsConditionalBlocksForIfElsePattern() {
        MethodNode method = methodNode(KERNEL_OWNER, "branching", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            Label elseLabel = new Label();
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitLabel(elseLabel);
            mv.visitInsn(Opcodes.ICONST_2);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmControlFlowGraph graph = builder.build(KERNEL_OWNER, method);

        assertEquals(3, graph.blocks().size());
        assertEquals(AsmTerminatorKind.CONDITIONAL_JUMP, graph.blocks().get(0).terminatorKind());
        assertEquals(List.of("B2", "B1"), graph.blocks().get(0).successorIds());
        assertEquals(AsmTerminatorKind.RETURN, graph.blocks().get(1).terminatorKind());
        assertEquals(AsmTerminatorKind.RETURN, graph.blocks().get(2).terminatorKind());
    }

    @Test
    void buildsBackEdgeForWhileLikeLoop() {
        MethodNode method = methodNode(KERNEL_OWNER, "looping", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            Label loopCheck = new Label();
            Label loopBody = new Label();
            Label loopEnd = new Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(loopCheck);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);
            mv.visitLabel(loopBody);
            mv.visitIincInsn(1, 1);
            mv.visitJumpInsn(Opcodes.GOTO, loopCheck);
            mv.visitLabel(loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmControlFlowGraph graph = builder.build(KERNEL_OWNER, method);

        assertEquals(4, graph.blocks().size());
        assertEquals(AsmTerminatorKind.FALLTHROUGH, graph.blocks().get(0).terminatorKind());
        assertEquals(List.of("B1"), graph.blocks().get(0).successorIds());
        assertEquals(AsmTerminatorKind.CONDITIONAL_JUMP, graph.blocks().get(1).terminatorKind());
        assertEquals(List.of("B3", "B2"), graph.blocks().get(1).successorIds());
        assertEquals(AsmTerminatorKind.GOTO, graph.blocks().get(2).terminatorKind());
        assertEquals(List.of("B1"), graph.blocks().get(2).successorIds());
        assertEquals(AsmTerminatorKind.RETURN, graph.blocks().get(3).terminatorKind());
    }

    @Test
    void buildsSwitchDispatchBlockWithCaseSuccessors() {
        MethodNode method = methodNode(KERNEL_OWNER, "switching", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            Label caseZero = new Label();
            Label caseOne = new Label();
            Label defaultCase = new Label();
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitTableSwitchInsn(0, 1, defaultCase, caseZero, caseOne);
            mv.visitLabel(caseZero);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitLabel(caseOne);
            mv.visitInsn(Opcodes.ICONST_2);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitLabel(defaultCase);
            mv.visitInsn(Opcodes.ICONST_3);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmControlFlowGraph graph = builder.build(KERNEL_OWNER, method);

        assertEquals(4, graph.blocks().size());
        assertEquals(AsmTerminatorKind.SWITCH, graph.blocks().get(0).terminatorKind());
        assertEquals(List.of("B3", "B1", "B2"), graph.blocks().get(0).successorIds());
        assertEquals(AsmTerminatorKind.RETURN, graph.blocks().get(1).terminatorKind());
        assertEquals(AsmTerminatorKind.RETURN, graph.blocks().get(2).terminatorKind());
        assertEquals(AsmTerminatorKind.RETURN, graph.blocks().get(3).terminatorKind());
    }

    private MethodNode methodNode(
            String ownerInternalName,
            String methodName,
            String descriptor,
            int access,
            MethodBodyWriter bodyWriter
    ) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, ownerInternalName, null, "java/lang/Object", null);
        MethodVisitor methodVisitor = writer.visitMethod(access, methodName, descriptor, null, null);
        bodyWriter.write(methodVisitor);
        writer.visitEnd();

        ClassNode classNode = new ClassNode();
        new ClassReader(writer.toByteArray()).accept(classNode, 0);
        return classNode.methods.stream()
                .filter(method -> method.name.equals(methodName) && method.desc.equals(descriptor))
                .findFirst()
                .orElseThrow();
    }

    @FunctionalInterface
    private interface MethodBodyWriter {
        void write(MethodVisitor methodVisitor);
    }
}
