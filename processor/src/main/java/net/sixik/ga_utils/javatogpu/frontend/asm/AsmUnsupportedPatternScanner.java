package net.sixik.ga_utils.javatogpu.frontend.asm;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
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
 * Collects common non-GPU-safe bytecode patterns without changing validator acceptance rules.
 */
public final class AsmUnsupportedPatternScanner {

    private static final String GPU_OWNER = AsmGpuTypeRules.GPU_OWNER;

    public AsmFrontendFailureReport scan(String ownerInternalName, MethodNode methodNode) {
        return scan(ownerInternalName, methodNode, AsmValidationConfig.defaultConfig());
    }

    public AsmFrontendFailureReport scan(
            String ownerInternalName,
            MethodNode methodNode,
            AsmValidationConfig config
    ) {
        Objects.requireNonNull(ownerInternalName, "ownerInternalName");
        Objects.requireNonNull(methodNode, "methodNode");
        Objects.requireNonNull(config, "config");

        List<AsmFrontendFailureMetadata> failures = new ArrayList<>();
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
            scanInstruction(ownerInternalName, methodNode, instruction, config, instructionIndex, lineNumber, failures);
        }
        return new AsmFrontendFailureReport(failures);
    }

    private void scanInstruction(
            String ownerInternalName,
            MethodNode methodNode,
            AbstractInsnNode instruction,
            AsmValidationConfig config,
            int instructionIndex,
            int lineNumber,
            List<AsmFrontendFailureMetadata> failures
    ) {
        if (instruction instanceof InsnNode insnNode) {
            scanSimpleInsn(ownerInternalName, methodNode, insnNode.getOpcode(), instructionIndex, lineNumber, failures);
            return;
        }
        if (instruction instanceof IntInsnNode intInsnNode && intInsnNode.getOpcode() == Opcodes.NEWARRAY) {
            addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber, "arrayAllocation", "NEWARRAY",
                    "Runtime primitive array allocation is not supported by ASM GPU frontend: NEWARRAY; allocate buffers on the host side and pass them as array parameters",
                    failures);
            return;
        }
        if (instruction instanceof TypeInsnNode typeInsnNode) {
            scanTypeInsn(ownerInternalName, methodNode, typeInsnNode, config, instructionIndex, lineNumber, failures);
            return;
        }
        if (instruction instanceof FieldInsnNode fieldInsnNode) {
            scanFieldInsn(ownerInternalName, methodNode, fieldInsnNode, config, instructionIndex, lineNumber, failures);
            return;
        }
        if (instruction instanceof MethodInsnNode methodInsnNode) {
            scanMethodInsn(ownerInternalName, methodNode, methodInsnNode, config, instructionIndex, lineNumber, failures);
            return;
        }
        if (instruction instanceof InvokeDynamicInsnNode invokeDynamicInsnNode) {
            Handle handle = invokeDynamicInsnNode.bsm;
            String bootstrap = handle == null ? "<unknown>" : handle.getOwner() + "." + handle.getName();
            addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber, "dynamicInvocation", "INVOKEDYNAMIC",
                    "invokedynamic is not supported by ASM GPU frontend: "
                            + bootstrap
                            + "; lower lambdas, string concatenation, and dynamic language features into explicit static helper methods before GPU lowering",
                    failures);
            return;
        }
        if (instruction instanceof MultiANewArrayInsnNode multiANewArrayInsnNode) {
            addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber, "arrayAllocation", "MULTIANEWARRAY",
                    "Multi-dimensional arrays are not supported by ASM GPU frontend: "
                            + multiANewArrayInsnNode.desc
                            + "; flatten the data into a single-dimension array and pass dimensions/strides explicitly",
                    failures);
        }
    }

    private void scanSimpleInsn(
            String ownerInternalName,
            MethodNode methodNode,
            int opcode,
            int instructionIndex,
            int lineNumber,
            List<AsmFrontendFailureMetadata> failures
    ) {
        switch (opcode) {
            case Opcodes.ATHROW -> addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "exceptionControlFlow", "ATHROW",
                    "Exception throwing is not supported by ASM GPU frontend: ATHROW; use explicit status/output flags or GPU.trap/GPU.unreachable for intentional failure paths",
                    failures);
            case Opcodes.MONITORENTER, Opcodes.MONITOREXIT -> addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "monitorSynchronization", AsmOpcodeNames.nameOf(opcode),
                    "Monitor-based synchronization is not supported by ASM GPU frontend: "
                            + AsmOpcodeNames.nameOf(opcode)
                            + "; remove synchronized blocks and host object locking before GPU lowering",
                    failures);
            case Opcodes.ARRAYLENGTH -> addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "arrayLength", "ARRAYLENGTH",
                    "Runtime array length reads are not supported by ASM GPU frontend: ARRAYLENGTH; pass required lengths or bounds as explicit kernel/helper parameters",
                    failures);
            default -> {
            }
        }
    }

    private void scanTypeInsn(
            String ownerInternalName,
            MethodNode methodNode,
            TypeInsnNode instruction,
            AsmValidationConfig config,
            int instructionIndex,
            int lineNumber,
            List<AsmFrontendFailureMetadata> failures
    ) {
        switch (instruction.getOpcode()) {
            case Opcodes.ANEWARRAY -> addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "arrayAllocation", "ANEWARRAY",
                    "Object arrays are not supported by ASM GPU frontend; use primitive arrays, vector arrays, or struct arrays instead",
                    failures);
            case Opcodes.CHECKCAST, Opcodes.INSTANCEOF -> addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "objectType", AsmOpcodeNames.nameOf(instruction.getOpcode()),
                    "Object type checks and casts are not supported by ASM GPU frontend: "
                            + AsmOpcodeNames.nameOf(instruction.getOpcode())
                            + "; keep values in explicit GPU-safe primitive, pointer, vector, or whitelisted struct types",
                    failures);
            case Opcodes.NEW -> {
                if (!AsmGpuTypeRules.isAllowedConstructorOwner(instruction.desc, config)) {
                    addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "objectType", "NEW",
                            "Unsupported constructor owner for ASM GPU frontend: "
                                    + instruction.desc
                                    + "; instantiate only supported pointer/vector/scalar-alias wrappers or whitelisted struct values",
                            failures);
                }
            }
            default -> {
            }
        }
    }

    private void scanFieldInsn(
            String ownerInternalName,
            MethodNode methodNode,
            FieldInsnNode instruction,
            AsmValidationConfig config,
            int instructionIndex,
            int lineNumber,
            List<AsmFrontendFailureMetadata> failures
    ) {
        String opcodeName = AsmOpcodeNames.nameOf(instruction.getOpcode());
        Type fieldType = Type.getType(instruction.desc);
        switch (instruction.getOpcode()) {
            case Opcodes.GETFIELD, Opcodes.PUTFIELD -> {
                if (!AsmGpuTypeRules.isAllowedFieldOwner(instruction.owner, config)) {
                    addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "fieldAccess", opcodeName,
                            "Unsupported field owner for ASM GPU frontend: " + instruction.owner,
                            failures);
                }
                if (!AsmGpuTypeRules.isSupportedValueType(fieldType, config, false)) {
                    addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "fieldAccess", opcodeName,
                            "Unsupported field descriptor for ASM GPU frontend: " + instruction.desc,
                            failures);
                }
            }
            case Opcodes.GETSTATIC -> {
                if (!GPU_OWNER.equals(instruction.owner) && !config.allowedStructOwners().contains(instruction.owner)) {
                    addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "fieldAccess", opcodeName,
                            "Unsupported static field owner for ASM GPU frontend: " + instruction.owner,
                            failures);
                }
                if (!AsmGpuTypeRules.isSupportedValueType(fieldType, config, false)) {
                    addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "fieldAccess", opcodeName,
                            "Unsupported static field descriptor for ASM GPU frontend: " + instruction.desc,
                            failures);
                }
            }
            default -> addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "fieldAccess", opcodeName,
                    "Unsupported field opcode for ASM GPU frontend: " + opcodeName,
                    failures);
        }
    }

    private void scanMethodInsn(
            String ownerInternalName,
            MethodNode methodNode,
            MethodInsnNode instruction,
            AsmValidationConfig config,
            int instructionIndex,
            int lineNumber,
            List<AsmFrontendFailureMetadata> failures
    ) {
        scanMethodDescriptor(ownerInternalName, methodNode, instruction, config, instructionIndex, lineNumber, failures);

        if (instruction.getOpcode() == Opcodes.INVOKEVIRTUAL || instruction.getOpcode() == Opcodes.INVOKEINTERFACE) {
            String opcodeName = AsmOpcodeNames.nameOf(instruction.getOpcode());
            addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "methodInvocation", opcodeName,
                    "Unsupported method invocation kind for ASM GPU frontend: " + opcodeName,
                    failures);
            return;
        }
        if (instruction.getOpcode() == Opcodes.INVOKESTATIC
                && !GPU_OWNER.equals(instruction.owner)
                && !config.allowedHelperOwners().contains(instruction.owner)
                && !ownerInternalName.equals(instruction.owner)) {
            addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "methodInvocation", "INVOKESTATIC",
                    "Unsupported static call owner for ASM GPU frontend: "
                            + instruction.owner
                            + "; use GPU builtins, a whitelisted helper owner, or a static method on the current owner",
                    failures);
            return;
        }
        if (instruction.getOpcode() == Opcodes.INVOKESPECIAL) {
            if (!"<init>".equals(instruction.name)) {
                addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                        "methodInvocation", "INVOKESPECIAL",
                        "Unsupported invokespecial target for ASM GPU frontend: " + instruction.owner + "." + instruction.name,
                        failures);
            }
            if (!AsmGpuTypeRules.isAllowedConstructorOwner(instruction.owner, config)) {
                addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                        "objectType", "INVOKESPECIAL",
                        "Unsupported constructor owner for ASM GPU frontend: "
                                + instruction.owner
                                + "; instantiate only supported pointer/vector/scalar-alias wrappers or whitelisted struct values",
                        failures);
            }
        }
    }

    private void scanMethodDescriptor(
            String ownerInternalName,
            MethodNode methodNode,
            MethodInsnNode instruction,
            AsmValidationConfig config,
            int instructionIndex,
            int lineNumber,
            List<AsmFrontendFailureMetadata> failures
    ) {
        String opcodeName = AsmOpcodeNames.nameOf(instruction.getOpcode());
        Type methodType = Type.getMethodType(instruction.desc);
        for (Type argumentType : methodType.getArgumentTypes()) {
            if (!AsmGpuTypeRules.isSupportedValueType(argumentType, config, true)) {
                addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                        "methodDescriptor", opcodeName,
                        "Unsupported call argument type for ASM GPU frontend: "
                                + argumentType.getDescriptor()
                                + "; use primitive scalars, single-dimension arrays, supported vectors, pointers, images/samplers, or whitelisted struct values",
                        failures);
            }
        }
        Type returnType = methodType.getReturnType();
        if (returnType.getSort() != Type.VOID && !AsmGpuTypeRules.isSupportedValueType(returnType, config, false)) {
            addFailure(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "methodDescriptor", opcodeName,
                    "Unsupported call return type for ASM GPU frontend: "
                            + returnType.getDescriptor()
                            + "; use void, primitive scalars, supported vectors, or whitelisted struct values",
                    failures);
        }
    }

    private void addFailure(
            String ownerInternalName,
            MethodNode methodNode,
            int instructionIndex,
            int lineNumber,
            String family,
            String opcodeName,
            String detail,
            List<AsmFrontendFailureMetadata> failures
    ) {
        failures.add(new AsmFrontendFailureMetadata(
                family,
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
