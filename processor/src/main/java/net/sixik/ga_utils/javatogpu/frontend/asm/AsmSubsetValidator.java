package net.sixik.ga_utils.javatogpu.frontend.asm;

import net.sixik.ga_utils.javatogpu.api.BytePtr;
import net.sixik.ga_utils.javatogpu.api.CharPtr;
import net.sixik.ga_utils.javatogpu.api.Double2;
import net.sixik.ga_utils.javatogpu.api.Double3;
import net.sixik.ga_utils.javatogpu.api.Double4;
import net.sixik.ga_utils.javatogpu.api.DoublePtr;
import net.sixik.ga_utils.javatogpu.api.Float2;
import net.sixik.ga_utils.javatogpu.api.Float3;
import net.sixik.ga_utils.javatogpu.api.Float4;
import net.sixik.ga_utils.javatogpu.api.FloatPtr;
import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.Image1DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DBufferReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DBufferWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Int2;
import net.sixik.ga_utils.javatogpu.api.Int3;
import net.sixik.ga_utils.javatogpu.api.Int4;
import net.sixik.ga_utils.javatogpu.api.IntPtr;
import net.sixik.ga_utils.javatogpu.api.Long2;
import net.sixik.ga_utils.javatogpu.api.Long3;
import net.sixik.ga_utils.javatogpu.api.Long4;
import net.sixik.ga_utils.javatogpu.api.LongPtr;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.api.ShortPtr;
import net.sixik.ga_utils.javatogpu.api.UInt4;
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

    private static final String GPU_OWNER = Type.getInternalName(GPU.class);

    private static final Set<String> BUILTIN_CONSTRUCTOR_OWNERS = Set.of(
            Type.getInternalName(BytePtr.class),
            Type.getInternalName(CharPtr.class),
            Type.getInternalName(ShortPtr.class),
            Type.getInternalName(IntPtr.class),
            Type.getInternalName(LongPtr.class),
            Type.getInternalName(FloatPtr.class),
            Type.getInternalName(DoublePtr.class),
            Type.getInternalName(Float2.class),
            Type.getInternalName(Float3.class),
            Type.getInternalName(Float4.class),
            Type.getInternalName(Int2.class),
            Type.getInternalName(Int3.class),
            Type.getInternalName(Int4.class),
            Type.getInternalName(UInt4.class),
            Type.getInternalName(Long2.class),
            Type.getInternalName(Long3.class),
            Type.getInternalName(Long4.class),
            Type.getInternalName(Double2.class),
            Type.getInternalName(Double3.class),
            Type.getInternalName(Double4.class)
    );

    private static final Set<String> BUILTIN_VALUE_OWNERS = Set.of(
            Type.getInternalName(BytePtr.class),
            Type.getInternalName(CharPtr.class),
            Type.getInternalName(ShortPtr.class),
            Type.getInternalName(IntPtr.class),
            Type.getInternalName(LongPtr.class),
            Type.getInternalName(FloatPtr.class),
            Type.getInternalName(DoublePtr.class),
            Type.getInternalName(Float2.class),
            Type.getInternalName(Float3.class),
            Type.getInternalName(Float4.class),
            Type.getInternalName(Int2.class),
            Type.getInternalName(Int3.class),
            Type.getInternalName(Int4.class),
            Type.getInternalName(UInt4.class),
            Type.getInternalName(Long2.class),
            Type.getInternalName(Long3.class),
            Type.getInternalName(Long4.class),
            Type.getInternalName(Double2.class),
            Type.getInternalName(Double3.class),
            Type.getInternalName(Double4.class),
            Type.getInternalName(Image1DReadOnly.class),
            Type.getInternalName(Image1DWriteOnly.class),
            Type.getInternalName(Image1DArrayReadOnly.class),
            Type.getInternalName(Image1DArrayWriteOnly.class),
            Type.getInternalName(Image1DBufferReadOnly.class),
            Type.getInternalName(Image1DBufferWriteOnly.class),
            Type.getInternalName(Image2DReadOnly.class),
            Type.getInternalName(Image2DWriteOnly.class),
            Type.getInternalName(Image2DArrayReadOnly.class),
            Type.getInternalName(Image2DArrayWriteOnly.class),
            Type.getInternalName(Image3DReadOnly.class),
            Type.getInternalName(Image3DWriteOnly.class),
            Type.getInternalName(Sampler.class)
    );

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
            throw new AsmFrontendException("ASM GPU frontend only supports static methods: " + formatMethod(ownerInternalName, methodNode));
        }
        if ((methodNode.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            throw new AsmFrontendException("Synchronized methods are not supported by ASM GPU frontend: " + formatMethod(ownerInternalName, methodNode));
        }
        if ((methodNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            throw new AsmFrontendException("Abstract methods are not supported by ASM GPU frontend: " + formatMethod(ownerInternalName, methodNode));
        }
        if ((methodNode.access & Opcodes.ACC_NATIVE) != 0) {
            throw new AsmFrontendException("Native methods are not supported by ASM GPU frontend: " + formatMethod(ownerInternalName, methodNode));
        }
        if (!methodNode.tryCatchBlocks.isEmpty()) {
            throw new AsmFrontendException("Exception handlers are not supported by ASM GPU frontend: " + formatMethod(ownerInternalName, methodNode));
        }

        validateMethodDescriptor(ownerInternalName, methodNode, config);
    }

    private void validateMethodDescriptor(String ownerInternalName, MethodNode methodNode, AsmValidationConfig config) {
        Type methodType = Type.getMethodType(methodNode.desc);
        for (Type argumentType : methodType.getArgumentTypes()) {
            if (!isSupportedValueType(argumentType, config, true)) {
                throw new AsmFrontendException(
                        "Unsupported ASM method parameter type in " + formatMethod(ownerInternalName, methodNode)
                                + ": " + argumentType.getDescriptor()
                );
            }
        }

        Type returnType = methodType.getReturnType();
        if (returnType.getSort() != Type.VOID && !isSupportedValueType(returnType, config, false)) {
            throw new AsmFrontendException(
                    "Unsupported ASM method return type in " + formatMethod(ownerInternalName, methodNode)
                            + ": " + returnType.getDescriptor()
            );
        }
    }

    private void validateInstructions(String ownerInternalName, MethodNode methodNode, AsmValidationConfig config) {
        int instructionIndex = 0;
        int lineNumber = -1;
        for (AbstractInsnNode instruction = methodNode.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
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
                    "Unsupported bytecode opcode for ASM GPU frontend: " + opcodeName(instruction.getOpcode()));
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
                    "Unsupported integer opcode for ASM GPU frontend: " + opcodeName(instruction.getOpcode()));
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
                "Unsupported LDC constant for ASM GPU frontend: " + constant.getClass().getSimpleName());
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
                if (!isAllowedFieldOwner(instruction.owner, config)) {
                    fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "Unsupported field owner for ASM GPU frontend: " + instruction.owner);
                }
                if (!isSupportedValueType(fieldType, config, false)) {
                    fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "Unsupported field descriptor for ASM GPU frontend: " + instruction.desc);
                }
            }
            case Opcodes.GETSTATIC -> {
                if (!GPU_OWNER.equals(instruction.owner) && !config.allowedStructOwners().contains(instruction.owner)) {
                    fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "Unsupported static field owner for ASM GPU frontend: " + instruction.owner);
                }
                if (!isSupportedValueType(fieldType, config, false)) {
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
                if (!isAllowedConstructorOwner(instruction.desc, config)) {
                    fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "Unsupported constructor owner for ASM GPU frontend: " + instruction.desc);
                }
            }
            case Opcodes.ANEWARRAY -> fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "Object arrays are not supported by ASM GPU frontend");
            case Opcodes.CHECKCAST, Opcodes.INSTANCEOF -> fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "General object casting is not supported by ASM GPU frontend: " + opcodeName(instruction.getOpcode()));
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
                            "Unsupported static call owner for ASM GPU frontend: " + instruction.owner);
                }
            }
            case Opcodes.INVOKESPECIAL -> {
                if (!"<init>".equals(instruction.name)) {
                    fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "Unsupported invokespecial target for ASM GPU frontend: " + instruction.owner + "." + instruction.name);
                }
                if (!isAllowedConstructorOwner(instruction.owner, config)) {
                    fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                            "Unsupported constructor owner for ASM GPU frontend: " + instruction.owner);
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
            if (!isSupportedValueType(argumentType, config, true)) {
                fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                        "Unsupported call argument type for ASM GPU frontend: " + argumentType.getDescriptor());
            }
        }
        Type returnType = methodType.getReturnType();
        if (returnType.getSort() != Type.VOID && !isSupportedValueType(returnType, config, false)) {
            fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                    "Unsupported call return type for ASM GPU frontend: " + returnType.getDescriptor());
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
                "invokedynamic is not supported by ASM GPU frontend: " + bootstrap);
    }

    private void validateMultiANewArrayInsnNode(
            String ownerInternalName,
            MethodNode methodNode,
            MultiANewArrayInsnNode instruction,
            int instructionIndex,
            int lineNumber
    ) {
        fail(ownerInternalName, methodNode, instructionIndex, lineNumber,
                "Multi-dimensional arrays are not supported by ASM GPU frontend: " + instruction.desc);
    }

    private boolean isAllowedConstructorOwner(String ownerInternalName, AsmValidationConfig config) {
        return BUILTIN_CONSTRUCTOR_OWNERS.contains(ownerInternalName) || config.allowedStructOwners().contains(ownerInternalName);
    }

    private boolean isAllowedFieldOwner(String ownerInternalName, AsmValidationConfig config) {
        return BUILTIN_CONSTRUCTOR_OWNERS.contains(ownerInternalName) || config.allowedStructOwners().contains(ownerInternalName);
    }

    private boolean isSupportedValueType(Type type, AsmValidationConfig config, boolean allowArrays) {
        return switch (type.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT, Type.LONG, Type.FLOAT, Type.DOUBLE -> true;
            case Type.ARRAY -> allowArrays && isSupportedArrayType(type, config);
            case Type.OBJECT -> isSupportedObjectType(type.getInternalName(), config);
            default -> false;
        };
    }

    private boolean isSupportedArrayType(Type arrayType, AsmValidationConfig config) {
        if (arrayType.getDimensions() != 1) {
            return false;
        }
        Type elementType = arrayType.getElementType();
        return switch (elementType.getSort()) {
            case Type.BYTE, Type.CHAR, Type.SHORT, Type.INT, Type.LONG, Type.FLOAT, Type.DOUBLE -> true;
            case Type.OBJECT -> isAllowedArrayObjectElementOwner(elementType.getInternalName(), config);
            default -> false;
        };
    }

    private boolean isSupportedObjectType(String ownerInternalName, AsmValidationConfig config) {
        return BUILTIN_VALUE_OWNERS.contains(ownerInternalName) || config.allowedStructOwners().contains(ownerInternalName);
    }

    private boolean isAllowedArrayObjectElementOwner(String ownerInternalName, AsmValidationConfig config) {
        return BUILTIN_CONSTRUCTOR_OWNERS.contains(ownerInternalName) || config.allowedStructOwners().contains(ownerInternalName);
    }

    private void fail(
            String ownerInternalName,
            MethodNode methodNode,
            int instructionIndex,
            int lineNumber,
            String detail
    ) {
        StringBuilder message = new StringBuilder();
        message.append(detail)
                .append(" in ")
                .append(formatMethod(ownerInternalName, methodNode))
                .append(" at instruction ")
                .append(instructionIndex);
        if (lineNumber >= 0) {
            message.append(", line ").append(lineNumber);
        }
        throw new AsmFrontendException(message.toString());
    }

    private String formatMethod(String ownerInternalName, MethodNode methodNode) {
        return ownerInternalName + "." + methodNode.name + methodNode.desc;
    }

    private String opcodeName(int opcode) {
        return AsmOpcodeNames.nameOf(opcode);
    }
}
