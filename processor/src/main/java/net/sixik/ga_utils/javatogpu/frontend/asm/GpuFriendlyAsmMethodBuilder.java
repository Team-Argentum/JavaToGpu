package net.sixik.ga_utils.javatogpu.frontend.asm;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Small helper for generating canonical GPU-friendly ASM shapes.
 *
 * <p>This builder intentionally prefers predictable control-flow and explicit temporary locals over
 * compact JVM-specific bytecode tricks.</p>
 */
public final class GpuFriendlyAsmMethodBuilder {

    private final MethodNode methodNode;
    private final Type[] argumentTypes;
    private final Deque<LoopContext> loopContexts = new ArrayDeque<>();
    private final Deque<Label> switchBreakLabels = new ArrayDeque<>();
    private int nextLocalSlot;

    public GpuFriendlyAsmMethodBuilder(int access, String methodName, String descriptor) {
        this.methodNode = new MethodNode(Opcodes.ASM9, access, methodName, descriptor, null, null);
        this.argumentTypes = Type.getArgumentTypes(descriptor);
        this.nextLocalSlot = (access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
        for (Type argumentType : argumentTypes) {
            nextLocalSlot += argumentType.getSize();
        }
    }

    public static GpuFriendlyAsmMethodBuilder staticMethod(String methodName, String descriptor) {
        return new GpuFriendlyAsmMethodBuilder(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodName, descriptor);
    }

    public MethodNode toMethodNode() {
        return methodNode;
    }

    public int parameterSlot(int parameterIndex) {
        if (parameterIndex < 0 || parameterIndex >= argumentTypes.length) {
            throw new IllegalArgumentException("Parameter index out of range: " + parameterIndex);
        }
        int slot = 0;
        for (int index = 0; index < parameterIndex; index++) {
            slot += argumentTypes[index].getSize();
        }
        return slot;
    }

    public int newTemp(Type type) {
        Objects.requireNonNull(type, "type");
        int slot = nextLocalSlot;
        nextLocalSlot += type.getSize();
        return slot;
    }

    public int emitTempStore(Type type) {
        int slot = newTemp(type);
        storeLocal(slot, type);
        return slot;
    }

    public Label newLabel() {
        return new Label();
    }

    public void mark(Label label) {
        methodNode.visitLabel(label);
    }

    public void pushInt(int value) {
        switch (value) {
            case -1 -> methodNode.visitInsn(Opcodes.ICONST_M1);
            case 0 -> methodNode.visitInsn(Opcodes.ICONST_0);
            case 1 -> methodNode.visitInsn(Opcodes.ICONST_1);
            case 2 -> methodNode.visitInsn(Opcodes.ICONST_2);
            case 3 -> methodNode.visitInsn(Opcodes.ICONST_3);
            case 4 -> methodNode.visitInsn(Opcodes.ICONST_4);
            case 5 -> methodNode.visitInsn(Opcodes.ICONST_5);
            default -> {
                if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
                    methodNode.visitIntInsn(Opcodes.BIPUSH, value);
                } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
                    methodNode.visitIntInsn(Opcodes.SIPUSH, value);
                } else {
                    methodNode.visitLdcInsn(value);
                }
            }
        }
    }

    public void pushLong(long value) {
        if (value == 0L) {
            methodNode.visitInsn(Opcodes.LCONST_0);
        } else if (value == 1L) {
            methodNode.visitInsn(Opcodes.LCONST_1);
        } else {
            methodNode.visitLdcInsn(value);
        }
    }

    public void pushFloat(float value) {
        if (value == 0.0f) {
            methodNode.visitInsn(Opcodes.FCONST_0);
        } else if (value == 1.0f) {
            methodNode.visitInsn(Opcodes.FCONST_1);
        } else if (value == 2.0f) {
            methodNode.visitInsn(Opcodes.FCONST_2);
        } else {
            methodNode.visitLdcInsn(value);
        }
    }

    public void pushDouble(double value) {
        if (value == 0.0d) {
            methodNode.visitInsn(Opcodes.DCONST_0);
        } else if (value == 1.0d) {
            methodNode.visitInsn(Opcodes.DCONST_1);
        } else {
            methodNode.visitLdcInsn(value);
        }
    }

    public void loadLocal(int slot, Type type) {
        methodNode.visitVarInsn(type.getOpcode(Opcodes.ILOAD), slot);
    }

    public void storeLocal(int slot, Type type) {
        methodNode.visitVarInsn(type.getOpcode(Opcodes.ISTORE), slot);
    }

    public void iinc(int slot, int increment) {
        methodNode.visitIincInsn(slot, increment);
    }

    public void emitStaticCall(String ownerInternalName, String methodName, String descriptor) {
        methodNode.visitMethodInsn(Opcodes.INVOKESTATIC, ownerInternalName, methodName, descriptor, false);
    }

    public void emitInsn(int opcode) {
        methodNode.visitInsn(opcode);
    }

    public void emitJump(int opcode, Label label) {
        methodNode.visitJumpInsn(opcode, label);
    }

    public void emitArrayLoad(Type elementType) {
        methodNode.visitInsn(arrayLoadOpcode(elementType));
    }

    public void emitArrayStore(Type elementType) {
        methodNode.visitInsn(arrayStoreOpcode(elementType));
    }

    public void emitReturn(Type returnType) {
        methodNode.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
    }

    public void emitVoidReturn() {
        methodNode.visitInsn(Opcodes.RETURN);
    }

    public void emitCanonicalIfElse(
            ConditionEmitter conditionEmitter,
            int falseJumpOpcode,
            BlockEmitter thenBlock,
            BlockEmitter elseBlock
    ) {
        Label elseLabel = newLabel();
        Label endLabel = newLabel();
        conditionEmitter.emit(this);
        emitJump(falseJumpOpcode, elseLabel);
        thenBlock.emit(this);
        emitJump(Opcodes.GOTO, endLabel);
        mark(elseLabel);
        elseBlock.emit(this);
        mark(endLabel);
    }

    public void emitCanonicalIf(
            ConditionEmitter conditionEmitter,
            int falseJumpOpcode,
            BlockEmitter thenBlock
    ) {
        Label endLabel = newLabel();
        conditionEmitter.emit(this);
        emitJump(falseJumpOpcode, endLabel);
        thenBlock.emit(this);
        mark(endLabel);
    }

    public void emitCanonicalWhileLoop(
            ConditionEmitter conditionEmitter,
            int exitJumpOpcode,
            BlockEmitter bodyEmitter
    ) {
        Label loopCheck = newLabel();
        Label loopEnd = newLabel();
        loopContexts.addLast(new LoopContext(loopCheck, loopEnd));
        mark(loopCheck);
        conditionEmitter.emit(this);
        emitJump(exitJumpOpcode, loopEnd);
        bodyEmitter.emit(this);
        emitJump(Opcodes.GOTO, loopCheck);
        mark(loopEnd);
        loopContexts.removeLast();
    }

    public void emitCanonicalDoWhileLoop(
            BlockEmitter bodyEmitter,
            ConditionEmitter conditionEmitter,
            int continueJumpOpcode
    ) {
        Label loopStart = newLabel();
        Label loopCondition = newLabel();
        Label loopEnd = newLabel();
        loopContexts.addLast(new LoopContext(loopCondition, loopEnd));
        mark(loopStart);
        bodyEmitter.emit(this);
        mark(loopCondition);
        conditionEmitter.emit(this);
        emitJump(continueJumpOpcode, loopStart);
        mark(loopEnd);
        loopContexts.removeLast();
    }

    public void emitCanonicalSwitch(
            ConditionEmitter selectorEmitter,
            List<SwitchCase> cases,
            BlockEmitter defaultBlock
    ) {
        Objects.requireNonNull(selectorEmitter, "selectorEmitter");
        Objects.requireNonNull(cases, "cases");
        Objects.requireNonNull(defaultBlock, "defaultBlock");

        selectorEmitter.emit(this);

        Label defaultLabel = newLabel();
        Label mergeLabel = newLabel();
        List<Label> caseLabels = new ArrayList<>(cases.size());
        List<KeyTarget> keyTargets = new ArrayList<>();
        for (SwitchCase switchCase : cases) {
            Label caseLabel = newLabel();
            caseLabels.add(caseLabel);
            for (int key : switchCase.keys()) {
                keyTargets.add(new KeyTarget(key, caseLabel));
            }
        }
        keyTargets.sort(Comparator.comparingInt(KeyTarget::key));

        int[] keys = new int[keyTargets.size()];
        Label[] labels = new Label[keyTargets.size()];
        for (int index = 0; index < keyTargets.size(); index++) {
            keys[index] = keyTargets.get(index).key();
            labels[index] = keyTargets.get(index).label();
        }

        methodNode.visitLookupSwitchInsn(defaultLabel, keys, labels);

        switchBreakLabels.addLast(mergeLabel);
        for (int index = 0; index < cases.size(); index++) {
            mark(caseLabels.get(index));
            SwitchCase switchCase = cases.get(index);
            switchCase.body().emit(this);
            if (!switchCase.fallThrough()) {
                emitBreakSwitch();
            }
        }
        mark(defaultLabel);
        defaultBlock.emit(this);
        emitBreakSwitch();
        mark(mergeLabel);
        switchBreakLabels.removeLast();
    }

    public void emitBreakSwitch() {
        if (switchBreakLabels.isEmpty()) {
            throw new IllegalStateException("emitBreakSwitch() used outside of a switch");
        }
        emitJump(Opcodes.GOTO, switchBreakLabels.getLast());
    }

    public void emitContinueLoop() {
        if (loopContexts.isEmpty()) {
            throw new IllegalStateException("emitContinueLoop() used outside of a loop");
        }
        emitJump(Opcodes.GOTO, loopContexts.getLast().continueLabel());
    }

    public void emitBreakLoop() {
        if (loopContexts.isEmpty()) {
            throw new IllegalStateException("emitBreakLoop() used outside of a loop");
        }
        emitJump(Opcodes.GOTO, loopContexts.getLast().breakLabel());
    }

    private int arrayLoadOpcode(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN, Type.BYTE -> Opcodes.BALOAD;
            case Type.CHAR -> Opcodes.CALOAD;
            case Type.SHORT -> Opcodes.SALOAD;
            case Type.INT -> Opcodes.IALOAD;
            case Type.FLOAT -> Opcodes.FALOAD;
            case Type.LONG -> Opcodes.LALOAD;
            case Type.DOUBLE -> Opcodes.DALOAD;
            default -> Opcodes.AALOAD;
        };
    }

    private int arrayStoreOpcode(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN, Type.BYTE -> Opcodes.BASTORE;
            case Type.CHAR -> Opcodes.CASTORE;
            case Type.SHORT -> Opcodes.SASTORE;
            case Type.INT -> Opcodes.IASTORE;
            case Type.FLOAT -> Opcodes.FASTORE;
            case Type.LONG -> Opcodes.LASTORE;
            case Type.DOUBLE -> Opcodes.DASTORE;
            default -> Opcodes.AASTORE;
        };
    }

    @FunctionalInterface
    public interface ConditionEmitter {
        void emit(GpuFriendlyAsmMethodBuilder builder);
    }

    @FunctionalInterface
    public interface BlockEmitter {
        void emit(GpuFriendlyAsmMethodBuilder builder);
    }

    public record SwitchCase(
            int[] keys,
            BlockEmitter body,
            boolean fallThrough
    ) {
        public SwitchCase {
            Objects.requireNonNull(keys, "keys");
            Objects.requireNonNull(body, "body");
            keys = Arrays.copyOf(keys, keys.length);
        }

        public static SwitchCase of(int key, BlockEmitter body) {
            return new SwitchCase(new int[]{key}, body, false);
        }

        public static SwitchCase of(int[] keys, BlockEmitter body, boolean fallThrough) {
            return new SwitchCase(keys, body, fallThrough);
        }
    }

    private record LoopContext(
            Label continueLabel,
            Label breakLabel
    ) {
    }

    private record KeyTarget(
            int key,
            Label label
    ) {
    }
}
