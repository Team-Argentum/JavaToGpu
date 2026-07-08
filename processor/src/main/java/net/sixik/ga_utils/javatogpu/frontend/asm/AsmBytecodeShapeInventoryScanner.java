package net.sixik.ga_utils.javatogpu.frontend.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Counts risky JVM bytecode shapes without deciding whether the current frontend rejects them.
 */
public final class AsmBytecodeShapeInventoryScanner {

    public AsmBytecodeShapeInventoryReport scan(String ownerInternalName, MethodNode methodNode) {
        return scan(ownerInternalName, methodNode, AsmValidationConfig.defaultConfig());
    }

    public AsmBytecodeShapeInventoryReport scan(
            String ownerInternalName,
            MethodNode methodNode,
            AsmValidationConfig config
    ) {
        Objects.requireNonNull(ownerInternalName, "ownerInternalName");
        Objects.requireNonNull(methodNode, "methodNode");
        Objects.requireNonNull(config, "config");

        List<AsmBytecodeShapeObservation> observations = new ArrayList<>();
        scanMethodShape(ownerInternalName, methodNode, observations);

        int instructionIndex = 0;
        int lineNumber = -1;
        for (AbstractInsnNode instruction : methodNode.instructions.toArray()) {
            if (instruction instanceof LineNumberNode line) {
                lineNumber = line.line;
                continue;
            }
            if (instruction instanceof LabelNode || instruction.getType() == AbstractInsnNode.FRAME) {
                continue;
            }
            int opcode = instruction.getOpcode();
            if (opcode >= 0) {
                instructionIndex++;
            }
            scanInstruction(ownerInternalName, methodNode, instruction, config, instructionIndex, lineNumber, observations);
        }
        return new AsmBytecodeShapeInventoryReport(observations);
    }

    private void scanMethodShape(
            String ownerInternalName,
            MethodNode methodNode,
            List<AsmBytecodeShapeObservation> observations
    ) {
        if ((methodNode.access & Opcodes.ACC_STATIC) == 0) {
            add(ownerInternalName, methodNode, 0, -1, AsmBytecodeShapeKind.NON_STATIC_METHOD, "",
                    "Method is not static", observations);
        }
        if ((methodNode.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            add(ownerInternalName, methodNode, 0, -1, AsmBytecodeShapeKind.SYNCHRONIZED_METHOD, "",
                    "Method uses JVM synchronized method semantics", observations);
        }
        if ((methodNode.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            add(ownerInternalName, methodNode, 0, -1, AsmBytecodeShapeKind.ABSTRACT_OR_NATIVE_METHOD, "",
                    "Method is abstract or native", observations);
        }
        if (methodNode.tryCatchBlocks != null && !methodNode.tryCatchBlocks.isEmpty()) {
            add(ownerInternalName, methodNode, 0, -1, AsmBytecodeShapeKind.EXCEPTION_HANDLER, "",
                    "Method declares JVM exception handlers", observations);
        }
    }

    private void scanInstruction(
            String ownerInternalName,
            MethodNode methodNode,
            AbstractInsnNode instruction,
            AsmValidationConfig config,
            int instructionIndex,
            int lineNumber,
            List<AsmBytecodeShapeObservation> observations
    ) {
        int opcode = instruction.getOpcode();
        switch (opcode) {
            case Opcodes.ARRAYLENGTH -> add(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    AsmBytecodeShapeKind.ARRAY_LENGTH, "ARRAYLENGTH", "Reads JVM array length metadata", observations);
            case Opcodes.NEWARRAY, Opcodes.ANEWARRAY -> add(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    AsmBytecodeShapeKind.ARRAY_ALLOCATION, AsmOpcodeNames.nameOf(opcode), "Allocates arrays at runtime", observations);
            case Opcodes.ATHROW -> add(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    AsmBytecodeShapeKind.EXCEPTION_THROW, "ATHROW", "Throws a JVM exception", observations);
            case Opcodes.MONITORENTER, Opcodes.MONITOREXIT -> add(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    AsmBytecodeShapeKind.MONITOR_SYNCHRONIZATION, AsmOpcodeNames.nameOf(opcode), "Uses JVM monitor synchronization", observations);
            case Opcodes.GETFIELD, Opcodes.PUTFIELD, Opcodes.GETSTATIC, Opcodes.PUTSTATIC -> {
                add(ownerInternalName, methodNode, instructionIndex, lineNumber,
                        AsmBytecodeShapeKind.FIELD_ACCESS, AsmOpcodeNames.nameOf(opcode), "Reads or writes JVM field state", observations);
                scanTypedInstruction(ownerInternalName, methodNode, instruction, config, instructionIndex, lineNumber, observations);
            }
            case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKEINTERFACE -> {
                add(ownerInternalName, methodNode, instructionIndex, lineNumber,
                        AsmBytecodeShapeKind.VIRTUAL_DISPATCH, AsmOpcodeNames.nameOf(opcode), "Uses virtual/interface dispatch", observations);
                scanTypedInstruction(ownerInternalName, methodNode, instruction, config, instructionIndex, lineNumber, observations);
            }
            case Opcodes.INVOKESPECIAL -> {
                add(ownerInternalName, methodNode, instructionIndex, lineNumber,
                        AsmBytecodeShapeKind.SPECIAL_INVOCATION, "INVOKESPECIAL", "Uses invokespecial dispatch", observations);
                scanTypedInstruction(ownerInternalName, methodNode, instruction, config, instructionIndex, lineNumber, observations);
            }
            default -> scanTypedInstruction(ownerInternalName, methodNode, instruction, config, instructionIndex, lineNumber, observations);
        }
    }

    private void scanTypedInstruction(
            String ownerInternalName,
            MethodNode methodNode,
            AbstractInsnNode instruction,
            AsmValidationConfig config,
            int instructionIndex,
            int lineNumber,
            List<AsmBytecodeShapeObservation> observations
    ) {
        if (instruction instanceof TypeInsnNode typeInsnNode) {
            scanTypeInstruction(ownerInternalName, methodNode, typeInsnNode, config, instructionIndex, lineNumber, observations);
            return;
        }
        if (instruction instanceof MultiANewArrayInsnNode) {
            add(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    AsmBytecodeShapeKind.ARRAY_ALLOCATION, "MULTIANEWARRAY", "Allocates multi-dimensional arrays", observations);
            return;
        }
        if (instruction instanceof InvokeDynamicInsnNode) {
            add(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    AsmBytecodeShapeKind.DYNAMIC_INVOCATION, "INVOKEDYNAMIC", "Uses invokedynamic", observations);
            return;
        }
        if (instruction instanceof MethodInsnNode methodInsnNode) {
            scanMethodInstruction(ownerInternalName, methodNode, methodInsnNode, config, instructionIndex, lineNumber, observations);
            return;
        }
        if (instruction instanceof FieldInsnNode fieldInsnNode) {
            scanFieldDescriptor(ownerInternalName, methodNode, fieldInsnNode, config, instructionIndex, lineNumber, observations);
            return;
        }
        if (instruction instanceof IntInsnNode intInsnNode && intInsnNode.getOpcode() == Opcodes.NEWARRAY) {
            add(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    AsmBytecodeShapeKind.ARRAY_ALLOCATION, "NEWARRAY", "Allocates primitive arrays", observations);
        }
    }

    private void scanTypeInstruction(
            String ownerInternalName,
            MethodNode methodNode,
            TypeInsnNode instruction,
            AsmValidationConfig config,
            int instructionIndex,
            int lineNumber,
            List<AsmBytecodeShapeObservation> observations
    ) {
        switch (instruction.getOpcode()) {
            case Opcodes.NEW -> {
                if (!AsmGpuTypeRules.isAllowedConstructorOwner(instruction.desc, config)) {
                    add(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            AsmBytecodeShapeKind.OBJECT_ALLOCATION, "NEW", "Allocates JVM object " + instruction.desc, observations);
                }
            }
            case Opcodes.CHECKCAST, Opcodes.INSTANCEOF -> add(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    AsmBytecodeShapeKind.OBJECT_TYPE_CHECK, AsmOpcodeNames.nameOf(instruction.getOpcode()),
                    "Uses JVM object type check/cast", observations);
            default -> {
            }
        }
    }

    private void scanMethodInstruction(
            String ownerInternalName,
            MethodNode methodNode,
            MethodInsnNode instruction,
            AsmValidationConfig config,
            int instructionIndex,
            int lineNumber,
            List<AsmBytecodeShapeObservation> observations
    ) {
        if (instruction.getOpcode() == Opcodes.INVOKESTATIC
                && !AsmGpuTypeRules.GPU_OWNER.equals(instruction.owner)
                && !config.allowedHelperOwners().contains(instruction.owner)
                && !ownerInternalName.equals(instruction.owner)) {
            add(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    AsmBytecodeShapeKind.UNSUPPORTED_STATIC_OWNER, "INVOKESTATIC",
                    "Calls unsupported static owner " + instruction.owner, observations);
        }
        Type methodType = Type.getMethodType(instruction.desc);
        for (Type argumentType : methodType.getArgumentTypes()) {
            if (!AsmGpuTypeRules.isSupportedValueType(argumentType, config, true)) {
                add(ownerInternalName, methodNode, instructionIndex, lineNumber,
                        AsmBytecodeShapeKind.UNSUPPORTED_DESCRIPTOR, AsmOpcodeNames.nameOf(instruction.getOpcode()),
                        "Unsupported call argument descriptor " + argumentType.getDescriptor(), observations);
            }
        }
        Type returnType = methodType.getReturnType();
        if (returnType.getSort() != Type.VOID && !AsmGpuTypeRules.isSupportedValueType(returnType, config, false)) {
            add(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    AsmBytecodeShapeKind.UNSUPPORTED_DESCRIPTOR, AsmOpcodeNames.nameOf(instruction.getOpcode()),
                    "Unsupported call return descriptor " + returnType.getDescriptor(), observations);
        }
    }

    private void scanFieldDescriptor(
            String ownerInternalName,
            MethodNode methodNode,
            FieldInsnNode instruction,
            AsmValidationConfig config,
            int instructionIndex,
            int lineNumber,
            List<AsmBytecodeShapeObservation> observations
    ) {
        Type fieldType = Type.getType(instruction.desc);
        if (!AsmGpuTypeRules.isSupportedValueType(fieldType, config, false)) {
            add(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    AsmBytecodeShapeKind.UNSUPPORTED_DESCRIPTOR, AsmOpcodeNames.nameOf(instruction.getOpcode()),
                    "Unsupported field descriptor " + instruction.desc, observations);
        }
    }

    private void add(
            String ownerInternalName,
            MethodNode methodNode,
            int instructionIndex,
            int lineNumber,
            AsmBytecodeShapeKind kind,
            String opcodeName,
            String detail,
            List<AsmBytecodeShapeObservation> observations
    ) {
        observations.add(new AsmBytecodeShapeObservation(
                kind,
                ownerInternalName,
                methodNode.name,
                methodNode.desc,
                instructionIndex,
                lineNumber,
                opcodeName,
                detail
        ));
    }
}
