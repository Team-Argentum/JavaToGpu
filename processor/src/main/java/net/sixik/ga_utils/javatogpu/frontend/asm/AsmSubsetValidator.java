package net.sixik.ga_utils.javatogpu.frontend.asm;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Objects;
import java.util.Set;

public final class AsmSubsetValidator {

    private static final String GPU_OWNER = AsmGpuTypeRules.GPU_OWNER;

    private static final Set<Integer> ALLOWED_SIMPLE_INSN_OPCODES = Set.of(
            Opcodes.NOP,
            Opcodes.ACONST_NULL,
            Opcodes.ICONST_M1,
            Opcodes.ICONST_0,
            Opcodes.ICONST_1,
            Opcodes.ICONST_2,
            Opcodes.ICONST_3,
            Opcodes.ICONST_4,
            Opcodes.ICONST_5,
            Opcodes.LCONST_0,
            Opcodes.LCONST_1,
            Opcodes.FCONST_0,
            Opcodes.FCONST_1,
            Opcodes.FCONST_2,
            Opcodes.DCONST_0,
            Opcodes.DCONST_1,
            Opcodes.IALOAD,
            Opcodes.LALOAD,
            Opcodes.FALOAD,
            Opcodes.DALOAD,
            Opcodes.AALOAD,
            Opcodes.BALOAD,
            Opcodes.CALOAD,
            Opcodes.SALOAD,
            Opcodes.IASTORE,
            Opcodes.LASTORE,
            Opcodes.FASTORE,
            Opcodes.DASTORE,
            Opcodes.AASTORE,
            Opcodes.BASTORE,
            Opcodes.CASTORE,
            Opcodes.SASTORE,
            Opcodes.POP,
            Opcodes.DUP,
            Opcodes.IADD,
            Opcodes.LADD,
            Opcodes.FADD,
            Opcodes.DADD,
            Opcodes.ISUB,
            Opcodes.LSUB,
            Opcodes.FSUB,
            Opcodes.DSUB,
            Opcodes.IMUL,
            Opcodes.LMUL,
            Opcodes.FMUL,
            Opcodes.DMUL,
            Opcodes.IDIV,
            Opcodes.LDIV,
            Opcodes.FDIV,
            Opcodes.DDIV,
            Opcodes.IREM,
            Opcodes.LREM,
            Opcodes.FREM,
            Opcodes.DREM,
            Opcodes.INEG,
            Opcodes.LNEG,
            Opcodes.FNEG,
            Opcodes.DNEG,
            Opcodes.ISHL,
            Opcodes.LSHL,
            Opcodes.ISHR,
            Opcodes.LSHR,
            Opcodes.IUSHR,
            Opcodes.LUSHR,
            Opcodes.IAND,
            Opcodes.LAND,
            Opcodes.IOR,
            Opcodes.LOR,
            Opcodes.IXOR,
            Opcodes.LXOR,
            Opcodes.I2L,
            Opcodes.I2F,
            Opcodes.I2D,
            Opcodes.L2I,
            Opcodes.L2F,
            Opcodes.L2D,
            Opcodes.F2I,
            Opcodes.F2L,
            Opcodes.F2D,
            Opcodes.D2I,
            Opcodes.D2L,
            Opcodes.D2F,
            Opcodes.I2B,
            Opcodes.I2C,
            Opcodes.I2S,
            Opcodes.LCMP,
            Opcodes.FCMPL,
            Opcodes.FCMPG,
            Opcodes.DCMPL,
            Opcodes.DCMPG,
            Opcodes.IRETURN,
            Opcodes.LRETURN,
            Opcodes.FRETURN,
            Opcodes.DRETURN,
            Opcodes.ARETURN,
            Opcodes.RETURN
    );

    private static final Set<Integer> ALLOWED_VAR_OPCODES = Set.of(
            Opcodes.ILOAD,
            Opcodes.LLOAD,
            Opcodes.FLOAD,
            Opcodes.DLOAD,
            Opcodes.ALOAD,
            Opcodes.ISTORE,
            Opcodes.LSTORE,
            Opcodes.FSTORE,
            Opcodes.DSTORE,
            Opcodes.ASTORE
    );

    private static final Set<Integer> ALLOWED_INT_OPCODES = Set.of(
            Opcodes.BIPUSH,
            Opcodes.SIPUSH
    );

    private static final Set<Integer> ALLOWED_JUMP_OPCODES = Set.of(
            Opcodes.IFEQ,
            Opcodes.IFNE,
            Opcodes.IFLT,
            Opcodes.IFGE,
            Opcodes.IFGT,
            Opcodes.IFLE,
            Opcodes.IF_ICMPEQ,
            Opcodes.IF_ICMPNE,
            Opcodes.IF_ICMPLT,
            Opcodes.IF_ICMPGE,
            Opcodes.IF_ICMPGT,
            Opcodes.IF_ICMPLE,
            Opcodes.GOTO
    );

    public void validate(String ownerInternalName, MethodNode methodNode) {
        validate(ownerInternalName, methodNode, AsmValidationConfig.defaultConfig());
    }

    public void validate(String ownerInternalName, MethodNode methodNode, AsmValidationConfig config) {
        Objects.requireNonNull(ownerInternalName, "ownerInternalName");
        Objects.requireNonNull(methodNode, "methodNode");
        Objects.requireNonNull(config, "config");

        validateMethodContract(ownerInternalName, methodNode, config);
        validateInstructions(ownerInternalName, methodNode, config);
    }

    private void validateMethodContract(String ownerInternalName, MethodNode methodNode, AsmValidationConfig config) {
        if ((methodNode.access & Opcodes.ACC_STATIC) == 0) {
            failMethod(ownerInternalName, methodNode, "methodContract",
                    "ASM GPU frontend only supports static methods: "
                            + formatMethod(ownerInternalName, methodNode)
                            + "; rewrite instance state into explicit parameters");
        }
        if ((methodNode.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            failMethod(ownerInternalName, methodNode, "monitorSynchronization",
                    "Synchronized methods are not supported by ASM GPU frontend: "
                            + formatMethod(ownerInternalName, methodNode)
                            + "; remove monitor-based control flow before lowering to GPU ASM");
        }
        if ((methodNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            failMethod(ownerInternalName, methodNode, "methodContract",
                    "Abstract methods are not supported by ASM GPU frontend: "
                            + formatMethod(ownerInternalName, methodNode)
                            + "; provide a concrete static implementation");
        }
        if ((methodNode.access & Opcodes.ACC_NATIVE) != 0) {
            failMethod(ownerInternalName, methodNode, "methodContract",
                    "Native methods are not supported by ASM GPU frontend: "
                            + formatMethod(ownerInternalName, methodNode)
                            + "; lower the logic into JVM bytecode first or model it as a helper/intrinsic");
        }
        if (!methodNode.tryCatchBlocks.isEmpty()) {
            failMethod(ownerInternalName, methodNode, "exceptionControlFlow",
                    "Exception handlers are not supported by ASM GPU frontend: "
                            + formatMethod(ownerInternalName, methodNode)
                            + "; rewrite the control flow without try/catch blocks");
        }

        validateMethodDescriptor(ownerInternalName, methodNode, config);
    }

    private void validateMethodDescriptor(String ownerInternalName, MethodNode methodNode, AsmValidationConfig config) {
        Type methodType = Type.getMethodType(methodNode.desc);
        for (Type argumentType : methodType.getArgumentTypes()) {
            if (!AsmGpuTypeRules.isSupportedValueType(argumentType, config, true)) {
                failMethod(ownerInternalName, methodNode, "methodDescriptor",
                        "Unsupported ASM method parameter type in " + formatMethod(ownerInternalName, methodNode)
                                + ": " + argumentType.getDescriptor()
                                + "; use primitive scalars, single-dimension arrays, supported vectors, pointers, images/samplers, or whitelisted struct values"
                );
            }
        }

        Type returnType = methodType.getReturnType();
        if (returnType.getSort() != Type.VOID && !AsmGpuTypeRules.isSupportedValueType(returnType, config, false)) {
            failMethod(ownerInternalName, methodNode, "methodDescriptor",
                    "Unsupported ASM method return type in " + formatMethod(ownerInternalName, methodNode)
                            + ": " + returnType.getDescriptor()
                            + "; use void, primitive scalars, supported vectors, or whitelisted struct values"
            );
        }
    }

    private void validateInstructions(String ownerInternalName, MethodNode methodNode, AsmValidationConfig config) {
        int instructionIndex = 0;
        int lineNumber = -1;
        AbstractInsnNode[] instructions = methodNode.instructions.toArray();
        for (AbstractInsnNode instruction : instructions) {
            if (instruction instanceof LineNumberNode line) {
                lineNumber = line.line;
                continue;
            }
            if (instruction instanceof LabelNode) {
                continue;
            }
            if (instruction.getType() == AbstractInsnNode.FRAME) {
                continue;
            }

            int opcode = instruction.getOpcode();
            if (opcode >= 0) {
                instructionIndex++;
            }

            if (instruction instanceof InsnNode insnNode) {
                validateInsnNode(ownerInternalName, methodNode, insnNode, instructionIndex, lineNumber);
                continue;
            }
            if (instruction instanceof VarInsnNode varInsnNode) {
                validateVarInsnNode(ownerInternalName, methodNode, varInsnNode, instructionIndex, lineNumber);
                continue;
            }
            if (instruction instanceof IntInsnNode intInsnNode) {
                validateIntInsnNode(ownerInternalName, methodNode, intInsnNode, instructionIndex, lineNumber);
                continue;
            }
            if (instruction instanceof LdcInsnNode ldcInsnNode) {
                validateLdcInsnNode(ownerInternalName, methodNode, ldcInsnNode, instructionIndex, lineNumber);
                continue;
            }
            if (instruction instanceof IincInsnNode iincInsnNode) {
                validateIincInsnNode(ownerInternalName, methodNode, iincInsnNode, instructionIndex, lineNumber);
                continue;
            }
            if (instruction instanceof JumpInsnNode jumpInsnNode) {
                validateJumpInsnNode(ownerInternalName, methodNode, jumpInsnNode, instructionIndex, lineNumber);
                continue;
            }
            if (instruction instanceof TableSwitchInsnNode tableSwitchInsnNode) {
                validateTableSwitchInsnNode(ownerInternalName, methodNode, tableSwitchInsnNode, instructionIndex, lineNumber);
                continue;
            }
            if (instruction instanceof LookupSwitchInsnNode lookupSwitchInsnNode) {
                validateLookupSwitchInsnNode(ownerInternalName, methodNode, lookupSwitchInsnNode, instructionIndex, lineNumber);
                continue;
            }
            if (instruction instanceof FieldInsnNode fieldInsnNode) {
                validateFieldInsnNode(ownerInternalName, methodNode, fieldInsnNode, config, instructionIndex, lineNumber);
                continue;
            }
            if (instruction instanceof TypeInsnNode typeInsnNode) {
                validateTypeInsnNode(ownerInternalName, methodNode, typeInsnNode, config, instructionIndex, lineNumber);
                continue;
            }
            if (instruction instanceof MethodInsnNode methodInsnNode) {
                validateMethodInsnNode(ownerInternalName, methodNode, methodInsnNode, config, instructionIndex, lineNumber);
                continue;
            }
            if (instruction instanceof InvokeDynamicInsnNode invokeDynamicInsnNode) {
                validateInvokeDynamicInsnNode(ownerInternalName, methodNode, invokeDynamicInsnNode, instructionIndex, lineNumber);
                continue;
            }
            if (instruction instanceof MultiANewArrayInsnNode multiANewArrayInsnNode) {
                validateMultiANewArrayInsnNode(ownerInternalName, methodNode, multiANewArrayInsnNode, instructionIndex, lineNumber);
                continue;
            }

            fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "Unsupported ASM instruction node type: " + instruction.getClass().getSimpleName());
        }
    }

    private void validateInsnNode(
            String ownerInternalName,
            MethodNode methodNode,
            InsnNode instruction,
            int instructionIndex,
            int lineNumber
    ) {
        if (!ALLOWED_SIMPLE_INSN_OPCODES.contains(instruction.getOpcode())) {
            fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    unsupportedSimpleOpcodeMessage(instruction.getOpcode()));
        }
    }

    private void validateVarInsnNode(
            String ownerInternalName,
            MethodNode methodNode,
            VarInsnNode instruction,
            int instructionIndex,
            int lineNumber
    ) {
        if (!ALLOWED_VAR_OPCODES.contains(instruction.getOpcode())) {
            fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "Unsupported local-variable opcode for ASM GPU frontend: " + opcodeName(instruction.getOpcode()));
        }
    }

    private void validateIntInsnNode(
            String ownerInternalName,
            MethodNode methodNode,
            IntInsnNode instruction,
            int instructionIndex,
            int lineNumber
    ) {
        if (!ALLOWED_INT_OPCODES.contains(instruction.getOpcode())) {
            fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    unsupportedIntegerOpcodeMessage(instruction.getOpcode()));
        }
    }

    private void validateLdcInsnNode(
            String ownerInternalName,
            MethodNode methodNode,
            LdcInsnNode instruction,
            int instructionIndex,
            int lineNumber
    ) {
        Object constant = instruction.cst;
        if (constant instanceof Integer
                || constant instanceof Long
                || constant instanceof Float
                || constant instanceof Double) {
            return;
        }
        fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                "Unsupported LDC constant for ASM GPU frontend: "
                        + constant.getClass().getSimpleName()
                        + "; only integer/long/float/double LDC constants are supported in the current GPU-friendly ASM subset");
    }

    private void validateIincInsnNode(
            String ownerInternalName,
            MethodNode methodNode,
            IincInsnNode instruction,
            int instructionIndex,
            int lineNumber
    ) {
        if (instruction.var < 0) {
            fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "Invalid local slot index for IINC: " + instruction.var);
        }
    }

    private void validateJumpInsnNode(
            String ownerInternalName,
            MethodNode methodNode,
            JumpInsnNode instruction,
            int instructionIndex,
            int lineNumber
    ) {
        if (!ALLOWED_JUMP_OPCODES.contains(instruction.getOpcode())) {
            fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "Unsupported control-flow opcode for ASM GPU frontend: " + opcodeName(instruction.getOpcode()));
        }
    }

    private void validateTableSwitchInsnNode(
            String ownerInternalName,
            MethodNode methodNode,
            TableSwitchInsnNode instruction,
            int instructionIndex,
            int lineNumber
    ) {
        if (instruction.dflt == null || instruction.labels == null || instruction.labels.isEmpty()) {
            fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "Malformed TABLESWITCH for ASM GPU frontend");
        }
    }

    private void validateLookupSwitchInsnNode(
            String ownerInternalName,
            MethodNode methodNode,
            LookupSwitchInsnNode instruction,
            int instructionIndex,
            int lineNumber
    ) {
        if (instruction.dflt == null || instruction.labels == null || instruction.keys == null) {
            fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "Malformed LOOKUPSWITCH for ASM GPU frontend");
        }
    }

    private void validateFieldInsnNode(
            String ownerInternalName,
            MethodNode methodNode,
            FieldInsnNode instruction,
            AsmValidationConfig config,
            int instructionIndex,
            int lineNumber
    ) {
        Type fieldType = Type.getType(instruction.desc);
        switch (instruction.getOpcode()) {
            case Opcodes.GETFIELD, Opcodes.PUTFIELD -> {
            if (!AsmGpuTypeRules.isAllowedFieldOwner(instruction.owner, config)) {
                    fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "Unsupported field owner for ASM GPU frontend: " + instruction.owner);
                }
                if (!AsmGpuTypeRules.isSupportedValueType(fieldType, config, false)) {
                    fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "Unsupported field descriptor for ASM GPU frontend: " + instruction.desc);
                }
            }
            case Opcodes.GETSTATIC -> {
                if (!GPU_OWNER.equals(instruction.owner) && !config.allowedStructOwners().contains(instruction.owner)) {
                    fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "Unsupported static field owner for ASM GPU frontend: " + instruction.owner);
                }
                if (!AsmGpuTypeRules.isSupportedValueType(fieldType, config, false)) {
                    fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "Unsupported static field descriptor for ASM GPU frontend: " + instruction.desc);
                }
            }
            default -> fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "Unsupported field opcode for ASM GPU frontend: " + opcodeName(instruction.getOpcode()));
        }
    }

    private void validateTypeInsnNode(
            String ownerInternalName,
            MethodNode methodNode,
            TypeInsnNode instruction,
            AsmValidationConfig config,
            int instructionIndex,
            int lineNumber
    ) {
        switch (instruction.getOpcode()) {
            case Opcodes.NEW -> {
                if (!AsmGpuTypeRules.isAllowedConstructorOwner(instruction.desc, config)) {
                    fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "Unsupported constructor owner for ASM GPU frontend: "
                                    + instruction.desc
                                    + "; instantiate only supported pointer/vector/scalar-alias wrappers or whitelisted struct values");
                }
            }
            case Opcodes.ANEWARRAY -> fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "Object arrays are not supported by ASM GPU frontend; use primitive arrays, vector arrays, or struct arrays instead");
            case Opcodes.CHECKCAST, Opcodes.INSTANCEOF -> fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "Object type checks and casts are not supported by ASM GPU frontend: "
                            + opcodeName(instruction.getOpcode())
                            + "; keep values in explicit GPU-safe primitive, pointer, vector, or whitelisted struct types");
            default -> fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "Unsupported type opcode for ASM GPU frontend: " + opcodeName(instruction.getOpcode()));
        }
    }

    private void validateMethodInsnNode(
            String ownerInternalName,
            MethodNode methodNode,
            MethodInsnNode instruction,
            AsmValidationConfig config,
            int instructionIndex,
            int lineNumber
    ) {
        validateMethodInsnDescriptor(ownerInternalName, methodNode, instruction, config, instructionIndex, lineNumber);

        switch (instruction.getOpcode()) {
            case Opcodes.INVOKESTATIC -> {
                if (!GPU_OWNER.equals(instruction.owner)
                        && !config.allowedHelperOwners().contains(instruction.owner)
                        && !ownerInternalName.equals(instruction.owner)) {
                    fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "Unsupported static call owner for ASM GPU frontend: "
                                    + instruction.owner
                                    + "; use GPU builtins, a whitelisted helper owner, or a static method on the current owner");
                }
            }
            case Opcodes.INVOKESPECIAL -> {
                if (!"<init>".equals(instruction.name)) {
                    fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "Unsupported invokespecial target for ASM GPU frontend: " + instruction.owner + "." + instruction.name);
                }
                if (!AsmGpuTypeRules.isAllowedConstructorOwner(instruction.owner, config)) {
                    fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "Unsupported constructor owner for ASM GPU frontend: "
                                    + instruction.owner
                                    + "; instantiate only supported pointer/vector/scalar-alias wrappers or whitelisted struct values");
                }
            }
            case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKEINTERFACE -> fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "Unsupported method invocation kind for ASM GPU frontend: " + opcodeName(instruction.getOpcode()));
            default -> fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "Unsupported method opcode for ASM GPU frontend: " + opcodeName(instruction.getOpcode()));
        }
    }

    private void validateMethodInsnDescriptor(
            String ownerInternalName,
            MethodNode methodNode,
            MethodInsnNode instruction,
            AsmValidationConfig config,
            int instructionIndex,
            int lineNumber
    ) {
        Type methodType = Type.getMethodType(instruction.desc);
        for (Type argumentType : methodType.getArgumentTypes()) {
            if (!AsmGpuTypeRules.isSupportedValueType(argumentType, config, true)) {
                fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                        "Unsupported call argument type for ASM GPU frontend: "
                                + argumentType.getDescriptor()
                                + "; use primitive scalars, single-dimension arrays, supported vectors, pointers, images/samplers, or whitelisted struct values");
            }
        }
        Type returnType = methodType.getReturnType();
        if (returnType.getSort() != Type.VOID && !AsmGpuTypeRules.isSupportedValueType(returnType, config, false)) {
            fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "Unsupported call return type for ASM GPU frontend: "
                            + returnType.getDescriptor()
                            + "; use void, primitive scalars, supported vectors, or whitelisted struct values");
        }
    }

    private void validateInvokeDynamicInsnNode(
            String ownerInternalName,
            MethodNode methodNode,
            InvokeDynamicInsnNode instruction,
            int instructionIndex,
            int lineNumber
    ) {
        Handle handle = instruction.bsm;
        String bootstrap = handle == null ? "<unknown>" : handle.getOwner() + "." + handle.getName();
        fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                "invokedynamic is not supported by ASM GPU frontend: "
                        + bootstrap
                        + "; lower lambdas, string concatenation, and dynamic language features into explicit static helper methods before GPU lowering");
    }

    private void validateMultiANewArrayInsnNode(
            String ownerInternalName,
            MethodNode methodNode,
            MultiANewArrayInsnNode instruction,
            int instructionIndex,
            int lineNumber
    ) {
        fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                "Multi-dimensional arrays are not supported by ASM GPU frontend: "
                        + instruction.desc
                        + "; flatten the data into a single-dimension array and pass dimensions/strides explicitly");
    }

    private String unsupportedSimpleOpcodeMessage(int opcode) {
        return switch (opcode) {
            case Opcodes.ATHROW ->
                    "Exception throwing is not supported by ASM GPU frontend: ATHROW; use explicit status/output flags or GPU.trap/GPU.unreachable for intentional failure paths";
            case Opcodes.MONITORENTER, Opcodes.MONITOREXIT ->
                    "Monitor-based synchronization is not supported by ASM GPU frontend: "
                            + opcodeName(opcode)
                            + "; remove synchronized blocks and host object locking before GPU lowering";
            case Opcodes.ARRAYLENGTH ->
                    "Runtime array length reads are not supported by ASM GPU frontend: ARRAYLENGTH; pass required lengths or bounds as explicit kernel/helper parameters";
            default -> "Unsupported bytecode opcode for ASM GPU frontend: " + opcodeName(opcode);
        };
    }

    private String unsupportedIntegerOpcodeMessage(int opcode) {
        if (opcode == Opcodes.NEWARRAY) {
            return "Runtime primitive array allocation is not supported by ASM GPU frontend: NEWARRAY; allocate buffers on the host side and pass them as array parameters";
        }
        return "Unsupported integer opcode for ASM GPU frontend: " + opcodeName(opcode);
    }

    private void fail(
            String ownerInternalName,
            MethodNode methodNode,
            int instructionIndex,
            int lineNumber,
            String detail
    ) {
        AsmFrontendFailureMetadata metadata = new AsmFrontendFailureMetadata(
                failureFamily(detail),
                ownerInternalName,
                methodNode.name,
                methodNode.desc,
                instructionIndex,
                lineNumber,
                failureOpcode(detail),
                detail
        );
        StringBuilder message = new StringBuilder();
        message.append(detail)
                .append(" in ")
                .append(formatMethod(ownerInternalName, methodNode))
                .append(" at instruction ")
                .append(instructionIndex);
        if (lineNumber >= 0) {
            message.append(", line ").append(lineNumber);
        }
        message.append("; rewrite the bytecode into the GPU-friendly ASM subset from docs/ASM-Contract.md");
        throw new AsmFrontendException(message.toString(), metadata);
    }

    private void failMethod(
            String ownerInternalName,
            MethodNode methodNode,
            String family,
            String detail
    ) {
        throw new AsmFrontendException(detail, methodFailureMetadata(ownerInternalName, methodNode, family, detail));
    }

    private AsmFrontendFailureMetadata methodFailureMetadata(
            String ownerInternalName,
            MethodNode methodNode,
            String family,
            String detail
    ) {
        return new AsmFrontendFailureMetadata(
                family,
                ownerInternalName,
                methodNode.name,
                methodNode.desc,
                0,
                -1,
                "",
                detail
        );
    }

    private String failureFamily(String detail) {
        if (detail.startsWith("Exception throwing is not supported")
                || detail.startsWith("Exception handlers are not supported")) {
            return "exceptionControlFlow";
        }
        if (detail.startsWith("Monitor-based synchronization is not supported")
                || detail.startsWith("Synchronized methods are not supported")) {
            return "monitorSynchronization";
        }
        if (detail.startsWith("Runtime array length reads are not supported")) {
            return "arrayLength";
        }
        if (detail.startsWith("Runtime primitive array allocation is not supported")
                || detail.startsWith("Object arrays are not supported")
                || detail.startsWith("Multi-dimensional arrays are not supported")) {
            return "arrayAllocation";
        }
        if (detail.startsWith("invokedynamic is not supported")) {
            return "dynamicInvocation";
        }
        if (detail.startsWith("Unsupported method invocation kind")
                || detail.startsWith("Unsupported static call owner")
                || detail.startsWith("Unsupported invokespecial target")
                || detail.startsWith("Unsupported method opcode")) {
            return "methodInvocation";
        }
        if (detail.startsWith("Object type checks and casts are not supported")
                || detail.startsWith("Unsupported type opcode")
                || detail.startsWith("Unsupported constructor owner")) {
            return "objectType";
        }
        if (detail.startsWith("Unsupported LDC constant")) {
            return "constant";
        }
        if (detail.startsWith("Unsupported control-flow opcode")
                || detail.startsWith("Malformed TABLESWITCH")
                || detail.startsWith("Malformed LOOKUPSWITCH")) {
            return "controlFlow";
        }
        if (detail.startsWith("Unsupported field")
                || detail.startsWith("Unsupported static field")) {
            return "fieldAccess";
        }
        if (detail.startsWith("Unsupported local-variable opcode")
                || detail.startsWith("Invalid local slot index")) {
            return "localVariable";
        }
        if (detail.startsWith("Unsupported integer opcode")
                || detail.startsWith("Unsupported bytecode opcode")) {
            return "opcode";
        }
        return "unsupportedBytecode";
    }

    private String failureOpcode(String detail) {
        int marker = detail.indexOf(": ");
        if (marker < 0 || marker + 2 >= detail.length()) {
            return "";
        }
        String suffix = detail.substring(marker + 2);
        int separator = suffix.indexOf(';');
        if (separator >= 0) {
            suffix = suffix.substring(0, separator);
        }
        int space = suffix.indexOf(' ');
        if (space >= 0) {
            suffix = suffix.substring(0, space);
        }
        if (suffix.matches("[A-Z][A-Z0-9_]*")) {
            return suffix;
        }
        return "";
    }

    private String formatMethod(String ownerInternalName, MethodNode methodNode) {
        return ownerInternalName + "." + methodNode.name + methodNode.desc;
    }

    private String opcodeName(int opcode) {
        return AsmOpcodeNames.nameOf(opcode);
    }
}
