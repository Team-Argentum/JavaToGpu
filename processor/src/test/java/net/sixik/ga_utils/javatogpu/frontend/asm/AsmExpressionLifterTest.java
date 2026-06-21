package net.sixik.ga_utils.javatogpu.frontend.asm;

import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrExpressionStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrLoopBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrDoWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitchCase;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsmExpressionLifterTest {

    private static final String KERNEL_OWNER = "sample/Kernel";
    private static final String HELPER_OWNER = "sample/Helpers";
    private static final String GPU_OWNER = Type.getInternalName(GPU.class);

    private final AsmExpressionLifter lifter = new AsmExpressionLifter(GpuIntrinsicDatabase.createDefault());

    @Test
    void liftsLinearKernelLikeMethodIntoIrStatements() {
        MethodNode method = methodNode(KERNEL_OWNER, "kernel", "([F[F)V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, GPU_OWNER, "get_global_id", "(I)I", false);
            mv.visitVarInsn(Opcodes.ISTORE, 2);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.FALOAD);
            mv.visitVarInsn(Opcodes.FSTORE, 3);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitVarInsn(Opcodes.FLOAD, 3);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, GPU_OWNER, "sin", "(F)F", false);
            mv.visitInsn(Opcodes.FASTORE);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftLinearMethod(KERNEL_OWNER, method);

        assertEquals(4, result.irMethod().statements().size());

        GpuIrVariableDeclaration idDeclaration = assertInstanceOf(
                GpuIrVariableDeclaration.class,
                result.irMethod().statements().get(0)
        );
        assertEquals("int", idDeclaration.typeName());
        assertEquals("tmp2", idDeclaration.name());
        GpuIrIntrinsicCall getGlobalId = assertInstanceOf(GpuIrIntrinsicCall.class, idDeclaration.initializer());
        assertEquals("get_global_id", getGlobalId.backendName());

        GpuIrVariableDeclaration valueDeclaration = assertInstanceOf(
                GpuIrVariableDeclaration.class,
                result.irMethod().statements().get(1)
        );
        assertEquals("float", valueDeclaration.typeName());
        assertEquals("tmp3", valueDeclaration.name());
        GpuIrArrayAccess inputAccess = assertInstanceOf(GpuIrArrayAccess.class, valueDeclaration.initializer());
        assertEquals("arg0", inputAccess.arrayName());

        GpuIrAssignment outputAssignment = assertInstanceOf(
                GpuIrAssignment.class,
                result.irMethod().statements().get(2)
        );
        GpuIrArrayAccess outputAccess = assertInstanceOf(GpuIrArrayAccess.class, outputAssignment.target());
        assertEquals("arg1", outputAccess.arrayName());
        GpuIrIntrinsicCall sinCall = assertInstanceOf(GpuIrIntrinsicCall.class, outputAssignment.value());
        assertEquals("sin", sinCall.backendName());

        GpuIrReturn gpuReturn = assertInstanceOf(GpuIrReturn.class, result.irMethod().statements().get(3));
        assertEquals(null, gpuReturn.value());
        assertEquals(List.of(), result.helperDependencies());
    }

    @Test
    void liftsHelperCallsAndTracksDependencies() {
        MethodNode method = methodNode(KERNEL_OWNER, "compute", "(F)F", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitVarInsn(Opcodes.FLOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER_OWNER, "square", "(F)F", false);
            mv.visitInsn(Opcodes.FRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmValidationConfig config = AsmValidationConfig.defaultConfig().withHelperOwner(HELPER_OWNER);

        AsmLiftingResult result = lifter.liftLinearMethod(KERNEL_OWNER, method, config);

        assertEquals(1, result.irMethod().statements().size());
        GpuIrReturn gpuReturn = assertInstanceOf(GpuIrReturn.class, result.irMethod().statements().get(0));
        GpuIrHelperCall helperCall = assertInstanceOf(GpuIrHelperCall.class, gpuReturn.value());
        assertEquals("jtg_fn_Helpers_square_float", helperCall.helperName());
        assertEquals(List.of("jtg_fn_Helpers_square_float"), result.helperDependencies());
    }

    @Test
    void usesLocalVariableTableNamesWhenAvailable() {
        MethodNode method = methodNode(KERNEL_OWNER, "kernel", "([F[F)V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label start = new org.objectweb.asm.Label();
            org.objectweb.asm.Label end = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitLabel(start);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, GPU_OWNER, "get_global_id", "(I)I", false);
            mv.visitVarInsn(Opcodes.ISTORE, 2);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.FALOAD);
            mv.visitVarInsn(Opcodes.FSTORE, 3);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitVarInsn(Opcodes.FLOAD, 3);
            mv.visitInsn(Opcodes.FASTORE);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitLabel(end);
            mv.visitLocalVariable("input", "[F", null, start, end, 0);
            mv.visitLocalVariable("output", "[F", null, start, end, 1);
            mv.visitLocalVariable("id", "I", null, start, end, 2);
            mv.visitLocalVariable("value", "F", null, start, end, 3);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftLinearMethod(KERNEL_OWNER, method);

        GpuIrVariableDeclaration idDeclaration = assertInstanceOf(
                GpuIrVariableDeclaration.class,
                result.irMethod().statements().get(0)
        );
        assertEquals("id", idDeclaration.name());

        GpuIrVariableDeclaration valueDeclaration = assertInstanceOf(
                GpuIrVariableDeclaration.class,
                result.irMethod().statements().get(1)
        );
        assertEquals("value", valueDeclaration.name());

        GpuIrArrayAccess inputAccess = assertInstanceOf(GpuIrArrayAccess.class, valueDeclaration.initializer());
        assertEquals("input", inputAccess.arrayName());

        GpuIrAssignment outputAssignment = assertInstanceOf(
                GpuIrAssignment.class,
                result.irMethod().statements().get(2)
        );
        GpuIrArrayAccess outputAccess = assertInstanceOf(GpuIrArrayAccess.class, outputAssignment.target());
        assertEquals("output", outputAccess.arrayName());
    }

    @Test
    void liftsArithmeticCastsAndIinc() {
        MethodNode method = methodNode(KERNEL_OWNER, "mathy", "(I)F", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.IADD);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitIincInsn(1, 2);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.I2F);
            mv.visitInsn(Opcodes.FRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftLinearMethod(KERNEL_OWNER, method);

        assertEquals(3, result.irMethod().statements().size(), result.irMethod().statements().toString());
        GpuIrVariableDeclaration declaration = assertInstanceOf(
                GpuIrVariableDeclaration.class,
                result.irMethod().statements().get(0)
        );
        GpuIrBinary initializer = assertInstanceOf(GpuIrBinary.class, declaration.initializer());
        assertEquals("+", initializer.operator());

        GpuIrAssignment increment = assertInstanceOf(
                GpuIrAssignment.class,
                result.irMethod().statements().get(1)
        );
        GpuIrBinary incrementValue = assertInstanceOf(GpuIrBinary.class, increment.value());
        assertEquals("+", incrementValue.operator());
        GpuIrLiteral incrementLiteral = assertInstanceOf(GpuIrLiteral.class, incrementValue.right());
        assertEquals("2", incrementLiteral.sourceText());

        GpuIrReturn gpuReturn = assertInstanceOf(GpuIrReturn.class, result.irMethod().statements().get(2));
        GpuIrCast cast = assertInstanceOf(GpuIrCast.class, gpuReturn.value());
        assertEquals("float", cast.targetType());
    }

    @Test
    void liftsVoidIntrinsicCallsAsExpressionStatements() {
        MethodNode method = methodNode(KERNEL_OWNER, "barrierCall", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, GPU_OWNER, "local_barrier", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftLinearMethod(KERNEL_OWNER, method);

        assertEquals(2, result.irMethod().statements().size());
        GpuIrExpressionStatement callStatement = assertInstanceOf(
                GpuIrExpressionStatement.class,
                result.irMethod().statements().get(0)
        );
        GpuIrIntrinsicCall intrinsicCall = assertInstanceOf(GpuIrIntrinsicCall.class, callStatement.expression());
        assertEquals("local_barrier", intrinsicCall.backendName());
    }

    @Test
    void rejectsControlFlowUntilCfgToIrStageExists() {
        MethodNode method = methodNode(KERNEL_OWNER, "branching", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label elseLabel = new org.objectweb.asm.Label();
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

        AsmFrontendException exception = assertThrows(
                AsmFrontendException.class,
                () -> lifter.liftLinearMethod(KERNEL_OWNER, method)
        );

        assertEquals(true, exception.getMessage().contains("only linear ASM blocks"));
    }

    @Test
    void liftsCanonicalIfElseIntoGpuIrIf() {
        MethodNode method = methodNode(KERNEL_OWNER, "branching", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label elseLabel = new org.objectweb.asm.Label();
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

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        assertEquals(1, result.irMethod().statements().size());
        GpuIrIf ifStatement = assertInstanceOf(GpuIrIf.class, result.irMethod().statements().get(0));
        assertInstanceOf(GpuIrVariableRef.class, ifStatement.condition());
        assertEquals(1, ifStatement.thenBranch().size());
        assertEquals(1, ifStatement.elseBranch().size());
        GpuIrReturn thenReturn = assertInstanceOf(GpuIrReturn.class, ifStatement.thenBranch().get(0));
        GpuIrReturn elseReturn = assertInstanceOf(GpuIrReturn.class, ifStatement.elseBranch().get(0));
        GpuIrLiteral thenValue = assertInstanceOf(GpuIrLiteral.class, thenReturn.value());
        GpuIrLiteral elseValue = assertInstanceOf(GpuIrLiteral.class, elseReturn.value());
        assertEquals("1", thenValue.sourceText());
        assertEquals("2", elseValue.sourceText());
    }

    @Test
    void liftsCanonicalIfWithoutElseIntoGpuIrIf() {
        MethodNode method = methodNode(KERNEL_OWNER, "guard", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label endLabel = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IFEQ, endLabel);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(endLabel);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);
        System.out.println("doContinue statements=" + result.irMethod().statements());

        assertEquals(3, result.irMethod().statements().size());
        assertInstanceOf(GpuIrVariableDeclaration.class, result.irMethod().statements().get(0));
        GpuIrIf ifStatement = assertInstanceOf(GpuIrIf.class, result.irMethod().statements().get(1));
        assertEquals(1, ifStatement.thenBranch().size());
        assertEquals(0, ifStatement.elseBranch().size());
        GpuIrAssignment thenAssignment = assertInstanceOf(GpuIrAssignment.class, ifStatement.thenBranch().get(0));
        GpuIrLiteral assignedValue = assertInstanceOf(GpuIrLiteral.class, thenAssignment.value());
        assertEquals("1", assignedValue.sourceText());
        assertInstanceOf(GpuIrReturn.class, result.irMethod().statements().get(2));
    }

    @Test
    void liftsCanonicalWhileLoopIntoGpuIrWhile() {
        MethodNode method = methodNode(KERNEL_OWNER, "looping", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label loopCheck = new org.objectweb.asm.Label();
            org.objectweb.asm.Label loopEnd = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(loopCheck);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);
            mv.visitIincInsn(1, 1);
            mv.visitJumpInsn(Opcodes.GOTO, loopCheck);
            mv.visitLabel(loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);
        System.out.println("doBreak statements=" + result.irMethod().statements());

        assertEquals(3, result.irMethod().statements().size());
        assertInstanceOf(GpuIrVariableDeclaration.class, result.irMethod().statements().get(0));
        GpuIrWhileLoop whileLoop = assertInstanceOf(GpuIrWhileLoop.class, result.irMethod().statements().get(1));
        GpuIrBinary condition = assertInstanceOf(GpuIrBinary.class, whileLoop.condition());
        assertEquals("<", condition.operator());
        assertEquals(1, whileLoop.body().size());
        GpuIrAssignment increment = assertInstanceOf(GpuIrAssignment.class, whileLoop.body().get(0));
        GpuIrBinary incrementValue = assertInstanceOf(GpuIrBinary.class, increment.value());
        assertEquals("+", incrementValue.operator());
        assertInstanceOf(GpuIrReturn.class, result.irMethod().statements().get(2));
    }

    @Test
    void liftsCanonicalDoWhileLoopIntoGpuIrDoWhile() {
        MethodNode method = methodNode(KERNEL_OWNER, "doLooping", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label loopBody = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(loopBody);
            mv.visitIincInsn(1, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, loopBody);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        assertEquals(3, result.irMethod().statements().size());
        assertInstanceOf(GpuIrVariableDeclaration.class, result.irMethod().statements().get(0));
        GpuIrDoWhileLoop doWhileLoop = assertInstanceOf(GpuIrDoWhileLoop.class, result.irMethod().statements().get(1));
        assertEquals(1, doWhileLoop.body().size());
        GpuIrAssignment increment = assertInstanceOf(GpuIrAssignment.class, doWhileLoop.body().get(0));
        GpuIrBinary incrementValue = assertInstanceOf(GpuIrBinary.class, increment.value());
        assertEquals("+", incrementValue.operator());
        GpuIrBinary condition = assertInstanceOf(GpuIrBinary.class, doWhileLoop.condition());
        assertEquals("<", condition.operator());
        assertInstanceOf(GpuIrReturn.class, result.irMethod().statements().get(2));
    }

    @Test
    void liftsCanonicalSwitchIntoGpuIrSwitch() {
        MethodNode method = methodNode(KERNEL_OWNER, "switching", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label caseZero = new org.objectweb.asm.Label();
            org.objectweb.asm.Label groupedCase = new org.objectweb.asm.Label();
            org.objectweb.asm.Label defaultCase = new org.objectweb.asm.Label();
            org.objectweb.asm.Label merge = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitInsn(Opcodes.ICONST_3);
            mv.visitInsn(Opcodes.IAND);
            mv.visitTableSwitchInsn(0, 2, defaultCase, caseZero, groupedCase, groupedCase);
            mv.visitLabel(caseZero);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitJumpInsn(Opcodes.GOTO, merge);
            mv.visitLabel(groupedCase);
            mv.visitInsn(Opcodes.ICONST_2);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitJumpInsn(Opcodes.GOTO, merge);
            mv.visitLabel(defaultCase);
            mv.visitInsn(Opcodes.ICONST_3);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(merge);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        assertEquals(3, result.irMethod().statements().size());
        assertInstanceOf(GpuIrVariableDeclaration.class, result.irMethod().statements().get(0));
        GpuIrSwitch gpuSwitch = assertInstanceOf(GpuIrSwitch.class, result.irMethod().statements().get(1));
        GpuIrBinary selector = assertInstanceOf(GpuIrBinary.class, gpuSwitch.selector());
        assertEquals("&", selector.operator());
        assertEquals(3, gpuSwitch.cases().size());

        GpuIrSwitchCase zeroCase = gpuSwitch.cases().get(0);
        assertEquals(false, zeroCase.defaultCase());
        assertEquals(1, zeroCase.labels().size());
        GpuIrLiteral zeroLabel = assertInstanceOf(GpuIrLiteral.class, zeroCase.labels().get(0));
        assertEquals("0", zeroLabel.sourceText());
        assertInstanceOf(GpuIrBreak.class, zeroCase.statements().get(1));

        GpuIrSwitchCase groupedSwitchCase = gpuSwitch.cases().get(1);
        assertEquals(2, groupedSwitchCase.labels().size());
        GpuIrLiteral oneLabel = assertInstanceOf(GpuIrLiteral.class, groupedSwitchCase.labels().get(0));
        GpuIrLiteral twoLabel = assertInstanceOf(GpuIrLiteral.class, groupedSwitchCase.labels().get(1));
        assertEquals("1", oneLabel.sourceText());
        assertEquals("2", twoLabel.sourceText());
        assertInstanceOf(GpuIrBreak.class, groupedSwitchCase.statements().get(1));

        GpuIrSwitchCase defaultSwitchCase = gpuSwitch.cases().get(2);
        assertEquals(true, defaultSwitchCase.defaultCase());
        assertEquals(0, defaultSwitchCase.labels().size());

        assertInstanceOf(GpuIrReturn.class, result.irMethod().statements().get(2));
    }

    @Test
    void liftsWhileLoopWithIfContinuePattern() {
        MethodNode method = methodNode(KERNEL_OWNER, "loopContinue", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label loopCheck = new org.objectweb.asm.Label();
            org.objectweb.asm.Label loopEnd = new org.objectweb.asm.Label();
            org.objectweb.asm.Label workBlock = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(loopCheck);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitJumpInsn(Opcodes.IFNE, workBlock);
            mv.visitIincInsn(1, 1);
            mv.visitJumpInsn(Opcodes.GOTO, loopCheck);
            mv.visitLabel(workBlock);
            mv.visitIincInsn(1, 2);
            mv.visitJumpInsn(Opcodes.GOTO, loopCheck);
            mv.visitLabel(loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        assertEquals(3, result.irMethod().statements().size());
        GpuIrWhileLoop whileLoop = assertInstanceOf(GpuIrWhileLoop.class, result.irMethod().statements().get(1));
        assertEquals(2, whileLoop.body().size());
        GpuIrIf ifStatement = assertInstanceOf(GpuIrIf.class, whileLoop.body().get(0));
        assertEquals(2, ifStatement.thenBranch().size());
        assertInstanceOf(GpuIrAssignment.class, ifStatement.thenBranch().get(0));
        assertInstanceOf(GpuIrContinue.class, ifStatement.thenBranch().get(1));
        GpuIrAssignment tailAssignment = assertInstanceOf(GpuIrAssignment.class, whileLoop.body().get(1));
        GpuIrBinary tailValue = assertInstanceOf(GpuIrBinary.class, tailAssignment.value());
        assertEquals("+", tailValue.operator());
    }

    @Test
    void liftsWhileLoopWithIfBreakPattern() {
        MethodNode method = methodNode(KERNEL_OWNER, "loopBreak", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label loopCheck = new org.objectweb.asm.Label();
            org.objectweb.asm.Label loopEnd = new org.objectweb.asm.Label();
            org.objectweb.asm.Label workBlock = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(loopCheck);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitJumpInsn(Opcodes.IFNE, workBlock);
            mv.visitJumpInsn(Opcodes.GOTO, loopEnd);
            mv.visitLabel(workBlock);
            mv.visitIincInsn(1, 2);
            mv.visitJumpInsn(Opcodes.GOTO, loopCheck);
            mv.visitLabel(loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        assertEquals(3, result.irMethod().statements().size());
        GpuIrWhileLoop whileLoop = assertInstanceOf(GpuIrWhileLoop.class, result.irMethod().statements().get(1));
        assertEquals(2, whileLoop.body().size());
        GpuIrIf ifStatement = assertInstanceOf(GpuIrIf.class, whileLoop.body().get(0));
        assertEquals(1, ifStatement.thenBranch().size());
        assertInstanceOf(GpuIrBreak.class, ifStatement.thenBranch().get(0));
        GpuIrAssignment tailAssignment = assertInstanceOf(GpuIrAssignment.class, whileLoop.body().get(1));
        GpuIrBinary tailValue = assertInstanceOf(GpuIrBinary.class, tailAssignment.value());
        assertEquals("+", tailValue.operator());
    }

    @Test
    void liftsWhileLoopWithNestedIfElsePattern() {
        MethodNode method = methodNode(KERNEL_OWNER, "loopIfElse", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label loopCheck = new org.objectweb.asm.Label();
            org.objectweb.asm.Label loopEnd = new org.objectweb.asm.Label();
            org.objectweb.asm.Label elseBlock = new org.objectweb.asm.Label();
            org.objectweb.asm.Label mergeBlock = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(loopCheck);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitJumpInsn(Opcodes.IFNE, elseBlock);
            mv.visitIincInsn(1, 1);
            mv.visitJumpInsn(Opcodes.GOTO, mergeBlock);
            mv.visitLabel(elseBlock);
            mv.visitIincInsn(1, 2);
            mv.visitLabel(mergeBlock);
            mv.visitJumpInsn(Opcodes.GOTO, loopCheck);
            mv.visitLabel(loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        assertEquals(3, result.irMethod().statements().size());
        GpuIrWhileLoop whileLoop = assertInstanceOf(GpuIrWhileLoop.class, result.irMethod().statements().get(1));
        assertEquals(1, whileLoop.body().size());
        GpuIrIf ifStatement = assertInstanceOf(GpuIrIf.class, whileLoop.body().get(0));
        assertEquals(1, ifStatement.thenBranch().size());
        assertEquals(1, ifStatement.elseBranch().size());
        GpuIrAssignment thenAssignment = assertInstanceOf(GpuIrAssignment.class, ifStatement.thenBranch().get(0));
        GpuIrAssignment elseAssignment = assertInstanceOf(GpuIrAssignment.class, ifStatement.elseBranch().get(0));
        assertEquals("+", assertInstanceOf(GpuIrBinary.class, thenAssignment.value()).operator());
        assertEquals("+", assertInstanceOf(GpuIrBinary.class, elseAssignment.value()).operator());
    }

    @Test
    void liftsWhileLoopWithNestedSwitchPattern() {
        MethodNode method = methodNode(KERNEL_OWNER, "loopSwitch", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label loopCheck = new org.objectweb.asm.Label();
            org.objectweb.asm.Label loopEnd = new org.objectweb.asm.Label();
            org.objectweb.asm.Label caseZero = new org.objectweb.asm.Label();
            org.objectweb.asm.Label defaultCase = new org.objectweb.asm.Label();
            org.objectweb.asm.Label switchMerge = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(loopCheck);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.IAND);
            mv.visitTableSwitchInsn(0, 0, defaultCase, caseZero);
            mv.visitLabel(caseZero);
            mv.visitIincInsn(1, 2);
            mv.visitJumpInsn(Opcodes.GOTO, switchMerge);
            mv.visitLabel(defaultCase);
            mv.visitIincInsn(1, 1);
            mv.visitLabel(switchMerge);
            mv.visitJumpInsn(Opcodes.GOTO, loopCheck);
            mv.visitLabel(loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        assertEquals(3, result.irMethod().statements().size());
        GpuIrWhileLoop whileLoop = assertInstanceOf(GpuIrWhileLoop.class, result.irMethod().statements().get(1));
        assertEquals(1, whileLoop.body().size());
        GpuIrSwitch gpuSwitch = assertInstanceOf(GpuIrSwitch.class, whileLoop.body().get(0));
        assertEquals(2, gpuSwitch.cases().size());
        GpuIrSwitchCase zeroCase = gpuSwitch.cases().get(0);
        assertEquals(false, zeroCase.defaultCase());
        assertEquals(1, zeroCase.labels().size());
        assertInstanceOf(GpuIrBreak.class, zeroCase.statements().get(1));
        GpuIrSwitchCase defaultCase = gpuSwitch.cases().get(1);
        assertEquals(true, defaultCase.defaultCase());
        assertEquals(1, defaultCase.statements().size());
    }

    @Test
    void liftsWhileLoopWithNestedSwitchContinuePattern() {
        MethodNode method = methodNode(KERNEL_OWNER, "loopSwitchContinue", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label loopCheck = new org.objectweb.asm.Label();
            org.objectweb.asm.Label loopEnd = new org.objectweb.asm.Label();
            org.objectweb.asm.Label caseZero = new org.objectweb.asm.Label();
            org.objectweb.asm.Label defaultCase = new org.objectweb.asm.Label();
            org.objectweb.asm.Label switchMerge = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(loopCheck);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.IAND);
            mv.visitTableSwitchInsn(0, 0, defaultCase, caseZero);
            mv.visitLabel(caseZero);
            mv.visitIincInsn(1, 1);
            mv.visitJumpInsn(Opcodes.GOTO, loopCheck);
            mv.visitLabel(defaultCase);
            mv.visitIincInsn(1, 2);
            mv.visitJumpInsn(Opcodes.GOTO, switchMerge);
            mv.visitLabel(switchMerge);
            mv.visitIincInsn(1, 10);
            mv.visitJumpInsn(Opcodes.GOTO, loopCheck);
            mv.visitLabel(loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        assertEquals(3, result.irMethod().statements().size());
        GpuIrWhileLoop whileLoop = assertInstanceOf(GpuIrWhileLoop.class, result.irMethod().statements().get(1));
        assertEquals(2, whileLoop.body().size());
        GpuIrSwitch gpuSwitch = assertInstanceOf(GpuIrSwitch.class, whileLoop.body().get(0));
        assertEquals(2, gpuSwitch.cases().size());

        GpuIrSwitchCase zeroCase = gpuSwitch.cases().get(0);
        assertEquals(false, zeroCase.defaultCase());
        assertEquals(2, zeroCase.statements().size());
        assertInstanceOf(GpuIrContinue.class, zeroCase.statements().get(1));

        GpuIrSwitchCase defaultCase = gpuSwitch.cases().get(1);
        assertEquals(true, defaultCase.defaultCase());
        assertEquals(2, defaultCase.statements().size());
        assertInstanceOf(GpuIrBreak.class, defaultCase.statements().get(1));

        GpuIrAssignment tailAssignment = assertInstanceOf(GpuIrAssignment.class, whileLoop.body().get(1));
        assertEquals("+", assertInstanceOf(GpuIrBinary.class, tailAssignment.value()).operator());
    }

    @Test
    void liftsSwitchWithMultiBlockCaseBody() {
        MethodNode method = methodNode(KERNEL_OWNER, "switchMultiBlock", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label caseZero = new org.objectweb.asm.Label();
            org.objectweb.asm.Label caseZeroTail = new org.objectweb.asm.Label();
            org.objectweb.asm.Label defaultCase = new org.objectweb.asm.Label();
            org.objectweb.asm.Label merge = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitTableSwitchInsn(0, 0, defaultCase, caseZero);
            mv.visitLabel(caseZero);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(caseZeroTail);
            mv.visitIincInsn(1, 2);
            mv.visitJumpInsn(Opcodes.GOTO, merge);
            mv.visitLabel(defaultCase);
            mv.visitInsn(Opcodes.ICONST_5);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(merge);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        GpuIrSwitch gpuSwitch = assertInstanceOf(GpuIrSwitch.class, result.irMethod().statements().get(1));
        assertEquals(2, gpuSwitch.cases().size());

        GpuIrSwitchCase zeroCase = gpuSwitch.cases().get(0);
        assertEquals(3, zeroCase.statements().size());
        assertInstanceOf(GpuIrAssignment.class, zeroCase.statements().get(0));
        assertInstanceOf(GpuIrAssignment.class, zeroCase.statements().get(1));
        assertInstanceOf(GpuIrBreak.class, zeroCase.statements().get(2));

        GpuIrSwitchCase defaultCase = gpuSwitch.cases().get(1);
        assertEquals(true, defaultCase.defaultCase());
        assertEquals(1, defaultCase.statements().size());
    }

    @Test
    void liftsSwitchWithCanonicalFallthroughBetweenCases() {
        MethodNode method = methodNode(KERNEL_OWNER, "switchFallthrough", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label caseZero = new org.objectweb.asm.Label();
            org.objectweb.asm.Label caseOne = new org.objectweb.asm.Label();
            org.objectweb.asm.Label defaultCase = new org.objectweb.asm.Label();
            org.objectweb.asm.Label merge = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitTableSwitchInsn(0, 1, defaultCase, caseZero, caseOne);
            mv.visitLabel(caseZero);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(caseOne);
            mv.visitIincInsn(1, 2);
            mv.visitJumpInsn(Opcodes.GOTO, merge);
            mv.visitLabel(defaultCase);
            mv.visitInsn(Opcodes.ICONST_5);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(merge);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        GpuIrSwitch gpuSwitch = assertInstanceOf(GpuIrSwitch.class, result.irMethod().statements().get(1));
        assertEquals(3, gpuSwitch.cases().size());

        GpuIrSwitchCase zeroCase = gpuSwitch.cases().get(0);
        assertEquals(1, zeroCase.statements().size());
        assertInstanceOf(GpuIrAssignment.class, zeroCase.statements().get(0));

        GpuIrSwitchCase oneCase = gpuSwitch.cases().get(1);
        assertEquals(2, oneCase.statements().size());
        assertInstanceOf(GpuIrAssignment.class, oneCase.statements().get(0));
        assertInstanceOf(GpuIrBreak.class, oneCase.statements().get(1));

        GpuIrSwitchCase defaultCase = gpuSwitch.cases().get(2);
        assertEquals(true, defaultCase.defaultCase());
        assertEquals(1, defaultCase.statements().size());
    }

    @Test
    void liftsSwitchWithNestedIfElseInsideCaseRange() {
        MethodNode method = methodNode(KERNEL_OWNER, "switchCaseIfElse", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label caseZero = new org.objectweb.asm.Label();
            org.objectweb.asm.Label elseBlock = new org.objectweb.asm.Label();
            org.objectweb.asm.Label caseTail = new org.objectweb.asm.Label();
            org.objectweb.asm.Label defaultCase = new org.objectweb.asm.Label();
            org.objectweb.asm.Label merge = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitTableSwitchInsn(0, 0, defaultCase, caseZero);
            mv.visitLabel(caseZero);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IFNE, elseBlock);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitJumpInsn(Opcodes.GOTO, caseTail);
            mv.visitLabel(elseBlock);
            mv.visitInsn(Opcodes.ICONST_2);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(caseTail);
            mv.visitIincInsn(1, 3);
            mv.visitJumpInsn(Opcodes.GOTO, merge);
            mv.visitLabel(defaultCase);
            mv.visitInsn(Opcodes.ICONST_5);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(merge);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        GpuIrSwitch gpuSwitch = assertInstanceOf(GpuIrSwitch.class, result.irMethod().statements().get(1));
        assertEquals(2, gpuSwitch.cases().size());

        GpuIrSwitchCase zeroCase = gpuSwitch.cases().get(0);
        assertEquals(3, zeroCase.statements().size());
        GpuIrIf ifStatement = assertInstanceOf(GpuIrIf.class, zeroCase.statements().get(0));
        assertEquals(1, ifStatement.thenBranch().size());
        assertEquals(1, ifStatement.elseBranch().size());
        assertInstanceOf(GpuIrAssignment.class, ifStatement.thenBranch().get(0));
        assertInstanceOf(GpuIrAssignment.class, ifStatement.elseBranch().get(0));
        assertInstanceOf(GpuIrAssignment.class, zeroCase.statements().get(1));
        assertInstanceOf(GpuIrBreak.class, zeroCase.statements().get(2));

        GpuIrSwitchCase defaultCase = gpuSwitch.cases().get(1);
        assertEquals(true, defaultCase.defaultCase());
        assertEquals(1, defaultCase.statements().size());
    }

    @Test
    void liftsSwitchWithNestedSwitchInsideCaseRange() {
        MethodNode method = methodNode(KERNEL_OWNER, "switchCaseSwitch", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label outerCaseZero = new org.objectweb.asm.Label();
            org.objectweb.asm.Label innerCaseZero = new org.objectweb.asm.Label();
            org.objectweb.asm.Label innerDefault = new org.objectweb.asm.Label();
            org.objectweb.asm.Label innerMerge = new org.objectweb.asm.Label();
            org.objectweb.asm.Label outerDefault = new org.objectweb.asm.Label();
            org.objectweb.asm.Label outerMerge = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitTableSwitchInsn(0, 0, outerDefault, outerCaseZero);
            mv.visitLabel(outerCaseZero);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.IAND);
            mv.visitTableSwitchInsn(0, 0, innerDefault, innerCaseZero);
            mv.visitLabel(innerCaseZero);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitJumpInsn(Opcodes.GOTO, innerMerge);
            mv.visitLabel(innerDefault);
            mv.visitInsn(Opcodes.ICONST_2);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(innerMerge);
            mv.visitIincInsn(1, 3);
            mv.visitJumpInsn(Opcodes.GOTO, outerMerge);
            mv.visitLabel(outerDefault);
            mv.visitInsn(Opcodes.ICONST_5);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(outerMerge);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        GpuIrSwitch outerSwitch = assertInstanceOf(GpuIrSwitch.class, result.irMethod().statements().get(1));
        assertEquals(2, outerSwitch.cases().size());

        GpuIrSwitchCase zeroCase = outerSwitch.cases().get(0);
        assertEquals(3, zeroCase.statements().size());
        GpuIrSwitch innerSwitch = assertInstanceOf(GpuIrSwitch.class, zeroCase.statements().get(0));
        assertEquals(2, innerSwitch.cases().size());
        assertInstanceOf(GpuIrAssignment.class, zeroCase.statements().get(1));
        assertInstanceOf(GpuIrBreak.class, zeroCase.statements().get(2));

        GpuIrSwitchCase innerZeroCase = innerSwitch.cases().get(0);
        assertEquals(2, innerZeroCase.statements().size());
        assertInstanceOf(GpuIrAssignment.class, innerZeroCase.statements().get(0));
        assertInstanceOf(GpuIrBreak.class, innerZeroCase.statements().get(1));

        GpuIrSwitchCase innerDefaultCase = innerSwitch.cases().get(1);
        assertEquals(true, innerDefaultCase.defaultCase());
        assertEquals(1, innerDefaultCase.statements().size());
    }

    @Test
    void liftsSwitchCaseConditionalBreakTransfer() {
        MethodNode method = methodNode(KERNEL_OWNER, "switchCaseBreakTransfer", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label caseZero = new org.objectweb.asm.Label();
            org.objectweb.asm.Label continueBlock = new org.objectweb.asm.Label();
            org.objectweb.asm.Label defaultCase = new org.objectweb.asm.Label();
            org.objectweb.asm.Label merge = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitTableSwitchInsn(0, 0, defaultCase, caseZero);
            mv.visitLabel(caseZero);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IFNE, continueBlock);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitJumpInsn(Opcodes.GOTO, merge);
            mv.visitLabel(continueBlock);
            mv.visitIincInsn(1, 2);
            mv.visitIincInsn(1, 3);
            mv.visitJumpInsn(Opcodes.GOTO, merge);
            mv.visitLabel(defaultCase);
            mv.visitInsn(Opcodes.ICONST_5);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(merge);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        GpuIrSwitch gpuSwitch = assertInstanceOf(GpuIrSwitch.class, result.irMethod().statements().get(1));
        GpuIrSwitchCase zeroCase = gpuSwitch.cases().get(0);
        assertEquals(4, zeroCase.statements().size());
        GpuIrIf ifStatement = assertInstanceOf(GpuIrIf.class, zeroCase.statements().get(0));
        assertEquals(2, ifStatement.thenBranch().size());
        assertInstanceOf(GpuIrAssignment.class, ifStatement.thenBranch().get(0));
        assertInstanceOf(GpuIrBreak.class, ifStatement.thenBranch().get(1));
        assertInstanceOf(GpuIrAssignment.class, zeroCase.statements().get(1));
        assertInstanceOf(GpuIrAssignment.class, zeroCase.statements().get(2));
        assertInstanceOf(GpuIrBreak.class, zeroCase.statements().get(3));
    }

    @Test
    void liftsLoopSwitchCaseConditionalContinueTransfer() {
        MethodNode method = methodNode(KERNEL_OWNER, "loopSwitchCaseContinueTransfer", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label loopCheck = new org.objectweb.asm.Label();
            org.objectweb.asm.Label loopEnd = new org.objectweb.asm.Label();
            org.objectweb.asm.Label caseZero = new org.objectweb.asm.Label();
            org.objectweb.asm.Label continueBlock = new org.objectweb.asm.Label();
            org.objectweb.asm.Label defaultCase = new org.objectweb.asm.Label();
            org.objectweb.asm.Label switchMerge = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(loopCheck);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitTableSwitchInsn(0, 0, defaultCase, caseZero);
            mv.visitLabel(caseZero);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitJumpInsn(Opcodes.IFNE, continueBlock);
            mv.visitIincInsn(1, 1);
            mv.visitJumpInsn(Opcodes.GOTO, switchMerge);
            mv.visitLabel(continueBlock);
            mv.visitIincInsn(1, 2);
            mv.visitJumpInsn(Opcodes.GOTO, loopCheck);
            mv.visitLabel(defaultCase);
            mv.visitIincInsn(1, 3);
            mv.visitLabel(switchMerge);
            mv.visitIincInsn(1, 10);
            mv.visitJumpInsn(Opcodes.GOTO, loopCheck);
            mv.visitLabel(loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        GpuIrWhileLoop whileLoop = assertInstanceOf(GpuIrWhileLoop.class, result.irMethod().statements().get(1));
        GpuIrSwitch gpuSwitch = assertInstanceOf(GpuIrSwitch.class, whileLoop.body().get(0));
        GpuIrSwitchCase zeroCase = gpuSwitch.cases().get(0);
        GpuIrIf ifStatement = assertInstanceOf(GpuIrIf.class, zeroCase.statements().get(0));
        assertEquals(2, ifStatement.thenBranch().size());
        assertInstanceOf(GpuIrAssignment.class, ifStatement.thenBranch().get(0));
        assertInstanceOf(GpuIrBreak.class, ifStatement.thenBranch().get(1));
        assertInstanceOf(GpuIrAssignment.class, zeroCase.statements().get(1));
        assertInstanceOf(GpuIrContinue.class, zeroCase.statements().get(2));
    }

    @Test
    void liftsLoopSwitchCaseDirectOuterBreakTransfer() {
        MethodNode method = methodNode(KERNEL_OWNER, "loopSwitchOuterBreak", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label loopCheck = new org.objectweb.asm.Label();
            org.objectweb.asm.Label loopEnd = new org.objectweb.asm.Label();
            org.objectweb.asm.Label caseZero = new org.objectweb.asm.Label();
            org.objectweb.asm.Label defaultCase = new org.objectweb.asm.Label();
            org.objectweb.asm.Label switchMerge = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(loopCheck);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitTableSwitchInsn(0, 0, defaultCase, caseZero);
            mv.visitLabel(caseZero);
            mv.visitIincInsn(1, 2);
            mv.visitJumpInsn(Opcodes.GOTO, loopEnd);
            mv.visitLabel(defaultCase);
            mv.visitIincInsn(1, 3);
            mv.visitJumpInsn(Opcodes.GOTO, switchMerge);
            mv.visitLabel(switchMerge);
            mv.visitIincInsn(1, 10);
            mv.visitJumpInsn(Opcodes.GOTO, loopCheck);
            mv.visitLabel(loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        GpuIrWhileLoop whileLoop = assertInstanceOf(GpuIrWhileLoop.class, result.irMethod().statements().get(1));
        GpuIrSwitch gpuSwitch = assertInstanceOf(GpuIrSwitch.class, whileLoop.body().get(0));
        GpuIrSwitchCase zeroCase = gpuSwitch.cases().get(0);
        assertEquals(2, zeroCase.statements().size());
        assertInstanceOf(GpuIrAssignment.class, zeroCase.statements().get(0));
        assertInstanceOf(GpuIrLoopBreak.class, zeroCase.statements().get(1));
    }

    @Test
    void liftsLoopSwitchCaseConditionalOuterBreakTransfer() {
        MethodNode method = methodNode(KERNEL_OWNER, "loopSwitchConditionalOuterBreak", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label loopCheck = new org.objectweb.asm.Label();
            org.objectweb.asm.Label loopEnd = new org.objectweb.asm.Label();
            org.objectweb.asm.Label caseZero = new org.objectweb.asm.Label();
            org.objectweb.asm.Label continuePath = new org.objectweb.asm.Label();
            org.objectweb.asm.Label defaultCase = new org.objectweb.asm.Label();
            org.objectweb.asm.Label switchMerge = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(loopCheck);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitTableSwitchInsn(0, 0, defaultCase, caseZero);
            mv.visitLabel(caseZero);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitJumpInsn(Opcodes.IFNE, continuePath);
            mv.visitIincInsn(1, 2);
            mv.visitJumpInsn(Opcodes.GOTO, loopEnd);
            mv.visitLabel(continuePath);
            mv.visitIincInsn(1, 3);
            mv.visitJumpInsn(Opcodes.GOTO, switchMerge);
            mv.visitLabel(defaultCase);
            mv.visitIincInsn(1, 4);
            mv.visitJumpInsn(Opcodes.GOTO, switchMerge);
            mv.visitLabel(switchMerge);
            mv.visitIincInsn(1, 10);
            mv.visitJumpInsn(Opcodes.GOTO, loopCheck);
            mv.visitLabel(loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        GpuIrWhileLoop whileLoop = assertInstanceOf(GpuIrWhileLoop.class, result.irMethod().statements().get(1));
        GpuIrSwitch gpuSwitch = assertInstanceOf(GpuIrSwitch.class, whileLoop.body().get(0));
        GpuIrSwitchCase zeroCase = gpuSwitch.cases().get(0);
        GpuIrIf ifStatement = assertInstanceOf(GpuIrIf.class, zeroCase.statements().get(0));
        assertEquals(2, ifStatement.thenBranch().size());
        assertInstanceOf(GpuIrAssignment.class, ifStatement.thenBranch().get(0));
        assertInstanceOf(GpuIrLoopBreak.class, ifStatement.thenBranch().get(1));
        assertInstanceOf(GpuIrAssignment.class, zeroCase.statements().get(1));
        assertInstanceOf(GpuIrBreak.class, zeroCase.statements().get(2));
    }

    @Test
    void liftsDoWhileLoopWithIfContinuePattern() {
        MethodNode method = methodNode(KERNEL_OWNER, "doLoopContinue", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label bodyStart = new org.objectweb.asm.Label();
            org.objectweb.asm.Label workBlock = new org.objectweb.asm.Label();
            org.objectweb.asm.Label loopCondition = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(bodyStart);
            mv.visitIincInsn(1, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitJumpInsn(Opcodes.IFNE, workBlock);
            mv.visitJumpInsn(Opcodes.GOTO, loopCondition);
            mv.visitLabel(workBlock);
            mv.visitIincInsn(1, 2);
            mv.visitLabel(loopCondition);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, bodyStart);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        assertEquals(3, result.irMethod().statements().size());
        GpuIrDoWhileLoop doWhileLoop = assertInstanceOf(GpuIrDoWhileLoop.class, result.irMethod().statements().get(1));
        assertEquals(3, doWhileLoop.body().size());
        GpuIrAssignment headAssignment = assertInstanceOf(GpuIrAssignment.class, doWhileLoop.body().get(0));
        GpuIrBinary headValue = assertInstanceOf(GpuIrBinary.class, headAssignment.value());
        assertEquals("+", headValue.operator());
        GpuIrIf ifStatement = assertInstanceOf(GpuIrIf.class, doWhileLoop.body().get(1));
        assertEquals(1, ifStatement.thenBranch().size());
        assertInstanceOf(GpuIrContinue.class, ifStatement.thenBranch().get(0));
        GpuIrAssignment tailAssignment = assertInstanceOf(GpuIrAssignment.class, doWhileLoop.body().get(2));
        GpuIrBinary tailValue = assertInstanceOf(GpuIrBinary.class, tailAssignment.value());
        assertEquals("+", tailValue.operator());
    }

    @Test
    void liftsDoWhileLoopWithIfBreakPattern() {
        MethodNode method = methodNode(KERNEL_OWNER, "doLoopBreak", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label bodyStart = new org.objectweb.asm.Label();
            org.objectweb.asm.Label workBlock = new org.objectweb.asm.Label();
            org.objectweb.asm.Label loopCondition = new org.objectweb.asm.Label();
            org.objectweb.asm.Label loopExit = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(bodyStart);
            mv.visitIincInsn(1, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitJumpInsn(Opcodes.IFNE, workBlock);
            mv.visitJumpInsn(Opcodes.GOTO, loopExit);
            mv.visitLabel(workBlock);
            mv.visitIincInsn(1, 2);
            mv.visitJumpInsn(Opcodes.GOTO, loopCondition);
            mv.visitLabel(loopCondition);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, bodyStart);
            mv.visitLabel(loopExit);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmLiftingResult result = lifter.liftStructuredMethod(KERNEL_OWNER, method);

        assertEquals(3, result.irMethod().statements().size());
        GpuIrDoWhileLoop doWhileLoop = assertInstanceOf(GpuIrDoWhileLoop.class, result.irMethod().statements().get(1));
        assertEquals(3, doWhileLoop.body().size());
        GpuIrAssignment headAssignment = assertInstanceOf(GpuIrAssignment.class, doWhileLoop.body().get(0));
        GpuIrBinary headValue = assertInstanceOf(GpuIrBinary.class, headAssignment.value());
        assertEquals("+", headValue.operator());
        GpuIrIf ifStatement = assertInstanceOf(GpuIrIf.class, doWhileLoop.body().get(1));
        assertEquals(1, ifStatement.thenBranch().size());
        assertInstanceOf(GpuIrBreak.class, ifStatement.thenBranch().get(0));
        GpuIrAssignment tailAssignment = assertInstanceOf(GpuIrAssignment.class, doWhileLoop.body().get(2));
        GpuIrBinary tailValue = assertInstanceOf(GpuIrBinary.class, tailAssignment.value());
        assertEquals("+", tailValue.operator());
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
