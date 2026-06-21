package net.sixik.ga_utils.javatogpu.frontend.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AsmControlFlowGraphBuilder {

    private final AsmSubsetValidator validator;

    public AsmControlFlowGraphBuilder() {
        this(new AsmSubsetValidator());
    }

    public AsmControlFlowGraphBuilder(AsmSubsetValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public AsmControlFlowGraph build(String ownerInternalName, MethodNode methodNode) {
        return build(ownerInternalName, methodNode, AsmValidationConfig.defaultConfig());
    }

    public AsmControlFlowGraph build(String ownerInternalName, MethodNode methodNode, AsmValidationConfig config) {
        Objects.requireNonNull(ownerInternalName, "ownerInternalName");
        Objects.requireNonNull(methodNode, "methodNode");
        Objects.requireNonNull(config, "config");

        validator.validate(ownerInternalName, methodNode, config);

        AbstractInsnNode firstInstruction = nextExecutable(methodNode.instructions.getFirst());
        if (firstInstruction == null) {
            return new AsmControlFlowGraph(ownerInternalName, methodNode.name, methodNode.desc, List.of());
        }

        Map<LabelNode, String> labelNames = assignLabelNames(methodNode);
        Map<AbstractInsnNode, String> primaryLabels = primaryLabelsByInstruction(methodNode, labelNames);
        LinkedHashSet<AbstractInsnNode> leaders = collectLeaders(methodNode, firstInstruction);
        List<BlockDraft> blockDrafts = createBlockDrafts(methodNode, leaders, primaryLabels);
        Map<AbstractInsnNode, String> instructionToBlockIds = instructionToBlockIds(blockDrafts);
        List<AsmBasicBlock> blocks = finalizeBlocks(blockDrafts, instructionToBlockIds);

        return new AsmControlFlowGraph(ownerInternalName, methodNode.name, methodNode.desc, blocks);
    }

    private Map<LabelNode, String> assignLabelNames(MethodNode methodNode) {
        Map<LabelNode, String> names = new IdentityHashMap<>();
        int labelIndex = 0;
        for (AbstractInsnNode instruction = methodNode.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof LabelNode labelNode) {
                names.put(labelNode, "L" + labelIndex++);
            }
        }
        return names;
    }

    private Map<AbstractInsnNode, String> primaryLabelsByInstruction(
            MethodNode methodNode,
            Map<LabelNode, String> labelNames
    ) {
        Map<AbstractInsnNode, String> primaryLabels = new IdentityHashMap<>();
        List<LabelNode> pendingLabels = new ArrayList<>();
        for (AbstractInsnNode instruction = methodNode.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof LabelNode labelNode) {
                pendingLabels.add(labelNode);
                continue;
            }
            if (!isExecutable(instruction)) {
                continue;
            }
            if (!pendingLabels.isEmpty()) {
                LabelNode primaryLabel = pendingLabels.get(0);
                primaryLabels.put(instruction, labelNames.get(primaryLabel));
                pendingLabels.clear();
            }
        }
        return primaryLabels;
    }

    private LinkedHashSet<AbstractInsnNode> collectLeaders(MethodNode methodNode, AbstractInsnNode firstInstruction) {
        LinkedHashSet<AbstractInsnNode> leaders = new LinkedHashSet<>();
        leaders.add(firstInstruction);

        for (AbstractInsnNode instruction = firstInstruction;
             instruction != null;
             instruction = nextExecutable(instruction.getNext())) {
            if (instruction instanceof JumpInsnNode jumpInsnNode) {
                addIfNonNull(leaders, nextExecutable(jumpInsnNode.label));
                if (isConditionalJump(jumpInsnNode.getOpcode())) {
                    addIfNonNull(leaders, nextExecutable(instruction.getNext()));
                }
            } else if (instruction instanceof TableSwitchInsnNode tableSwitchInsnNode) {
                addIfNonNull(leaders, nextExecutable(tableSwitchInsnNode.dflt));
                addAllIfNonNull(leaders, nextExecutable(tableSwitchInsnNode.labels));
            } else if (instruction instanceof LookupSwitchInsnNode lookupSwitchInsnNode) {
                addIfNonNull(leaders, nextExecutable(lookupSwitchInsnNode.dflt));
                addAllIfNonNull(leaders, nextExecutable(lookupSwitchInsnNode.labels));
            }
        }

        return leaders;
    }

    private List<BlockDraft> createBlockDrafts(
            MethodNode methodNode,
            LinkedHashSet<AbstractInsnNode> leaders,
            Map<AbstractInsnNode, String> primaryLabels
    ) {
        List<BlockDraft> blocks = new ArrayList<>();
        BlockDraft currentBlock = null;
        int instructionIndex = 0;
        int lineNumber = -1;

        for (AbstractInsnNode instruction = methodNode.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof LineNumberNode lineNode) {
                lineNumber = lineNode.line;
                continue;
            }
            if (!isExecutable(instruction)) {
                continue;
            }

            instructionIndex++;
            if (leaders.contains(instruction)) {
                if (currentBlock != null && !currentBlock.instructions.isEmpty()) {
                    blocks.add(currentBlock);
                }
                currentBlock = new BlockDraft(
                        "B" + blocks.size(),
                        primaryLabels.get(instruction),
                        blocks.isEmpty()
                );
            }
            if (currentBlock == null) {
                currentBlock = new BlockDraft("B" + blocks.size(), primaryLabels.get(instruction), blocks.isEmpty());
            }

            currentBlock.instructions.add(new AsmInstruction(
                    instructionIndex,
                    instruction.getOpcode(),
                    AsmOpcodeNames.nameOf(instruction.getOpcode()),
                    lineNumber,
                    instruction
            ));

            AbstractInsnNode nextInstruction = nextExecutable(instruction.getNext());
            if (shouldCloseBlock(instruction, nextInstruction, leaders)) {
                blocks.add(currentBlock);
                currentBlock = null;
            }
        }

        if (currentBlock != null && !currentBlock.instructions.isEmpty()) {
            blocks.add(currentBlock);
        }

        return blocks;
    }

    private boolean shouldCloseBlock(
            AbstractInsnNode instruction,
            AbstractInsnNode nextInstruction,
            Collection<AbstractInsnNode> leaders
    ) {
        if (isTerminator(instruction)) {
            return true;
        }
        return nextInstruction != null && leaders.contains(nextInstruction);
    }

    private Map<AbstractInsnNode, String> instructionToBlockIds(List<BlockDraft> blocks) {
        Map<AbstractInsnNode, String> instructionToBlockId = new IdentityHashMap<>();
        for (BlockDraft block : blocks) {
            for (AsmInstruction instruction : block.instructions) {
                instructionToBlockId.put(instruction.node(), block.id);
            }
        }
        return instructionToBlockId;
    }

    private List<AsmBasicBlock> finalizeBlocks(List<BlockDraft> blockDrafts, Map<AbstractInsnNode, String> instructionToBlockIds) {
        List<AsmBasicBlock> blocks = new ArrayList<>(blockDrafts.size());
        for (int index = 0; index < blockDrafts.size(); index++) {
            BlockDraft draft = blockDrafts.get(index);
            AsmInstruction lastInstruction = draft.instructions.get(draft.instructions.size() - 1);
            AsmTerminatorKind terminatorKind = terminatorKind(lastInstruction.node(), index, blockDrafts.size());
            List<String> successors = computeSuccessors(lastInstruction.node(), index, blockDrafts, instructionToBlockIds);
            blocks.add(new AsmBasicBlock(
                    draft.id,
                    draft.labelName,
                    draft.entry,
                    List.copyOf(draft.instructions),
                    terminatorKind,
                    successors
            ));
        }
        return List.copyOf(blocks);
    }

    private AsmTerminatorKind terminatorKind(AbstractInsnNode instruction, int blockIndex, int blockCount) {
        if (instruction instanceof JumpInsnNode jumpInsnNode) {
            return jumpInsnNode.getOpcode() == Opcodes.GOTO
                    ? AsmTerminatorKind.GOTO
                    : AsmTerminatorKind.CONDITIONAL_JUMP;
        }
        if (instruction instanceof TableSwitchInsnNode || instruction instanceof LookupSwitchInsnNode) {
            return AsmTerminatorKind.SWITCH;
        }
        if (isReturnInstruction(instruction.getOpcode())) {
            return AsmTerminatorKind.RETURN;
        }
        return blockIndex < blockCount - 1 ? AsmTerminatorKind.FALLTHROUGH : AsmTerminatorKind.NONE;
    }

    private List<String> computeSuccessors(
            AbstractInsnNode instruction,
            int blockIndex,
            List<BlockDraft> blockDrafts,
            Map<AbstractInsnNode, String> instructionToBlockIds
    ) {
        LinkedHashSet<String> successors = new LinkedHashSet<>();
        if (instruction instanceof JumpInsnNode jumpInsnNode) {
            addBlockId(successors, instructionToBlockIds.get(nextExecutable(jumpInsnNode.label)));
            if (isConditionalJump(jumpInsnNode.getOpcode())) {
                addBlockId(successors, blockIndex + 1 < blockDrafts.size() ? blockDrafts.get(blockIndex + 1).id : null);
            }
            return List.copyOf(successors);
        }
        if (instruction instanceof TableSwitchInsnNode tableSwitchInsnNode) {
            addBlockId(successors, instructionToBlockIds.get(nextExecutable(tableSwitchInsnNode.dflt)));
            for (LabelNode label : tableSwitchInsnNode.labels) {
                addBlockId(successors, instructionToBlockIds.get(nextExecutable(label)));
            }
            return List.copyOf(successors);
        }
        if (instruction instanceof LookupSwitchInsnNode lookupSwitchInsnNode) {
            addBlockId(successors, instructionToBlockIds.get(nextExecutable(lookupSwitchInsnNode.dflt)));
            for (LabelNode label : lookupSwitchInsnNode.labels) {
                addBlockId(successors, instructionToBlockIds.get(nextExecutable(label)));
            }
            return List.copyOf(successors);
        }
        if (isReturnInstruction(instruction.getOpcode())) {
            return List.of();
        }
        if (blockIndex + 1 < blockDrafts.size()) {
            return List.of(blockDrafts.get(blockIndex + 1).id);
        }
        return List.of();
    }

    private boolean isExecutable(AbstractInsnNode instruction) {
        return instruction != null && instruction.getOpcode() >= 0;
    }

    private AbstractInsnNode nextExecutable(LabelNode labelNode) {
        return nextExecutable((AbstractInsnNode) labelNode);
    }

    private List<AbstractInsnNode> nextExecutable(List<LabelNode> labels) {
        List<AbstractInsnNode> resolved = new ArrayList<>(labels.size());
        for (LabelNode label : labels) {
            AbstractInsnNode instruction = nextExecutable(label);
            if (instruction != null) {
                resolved.add(instruction);
            }
        }
        return resolved;
    }

    private AbstractInsnNode nextExecutable(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction;
        while (current != null && !isExecutable(current)) {
            current = current.getNext();
        }
        return current;
    }

    private boolean isTerminator(AbstractInsnNode instruction) {
        if (instruction instanceof JumpInsnNode) {
            return true;
        }
        if (instruction instanceof TableSwitchInsnNode || instruction instanceof LookupSwitchInsnNode) {
            return true;
        }
        return isReturnInstruction(instruction.getOpcode());
    }

    private boolean isConditionalJump(int opcode) {
        return opcode != Opcodes.GOTO;
    }

    private boolean isReturnInstruction(int opcode) {
        return opcode == Opcodes.IRETURN
                || opcode == Opcodes.LRETURN
                || opcode == Opcodes.FRETURN
                || opcode == Opcodes.DRETURN
                || opcode == Opcodes.ARETURN
                || opcode == Opcodes.RETURN;
    }

    private void addIfNonNull(LinkedHashSet<AbstractInsnNode> nodes, AbstractInsnNode instruction) {
        if (instruction != null) {
            nodes.add(instruction);
        }
    }

    private void addAllIfNonNull(LinkedHashSet<AbstractInsnNode> nodes, Collection<AbstractInsnNode> instructions) {
        for (AbstractInsnNode instruction : instructions) {
            addIfNonNull(nodes, instruction);
        }
    }

    private void addBlockId(LinkedHashSet<String> successors, String blockId) {
        if (blockId != null) {
            successors.add(blockId);
        }
    }

    private static final class BlockDraft {

        private final String id;
        private final String labelName;
        private final boolean entry;
        private final List<AsmInstruction> instructions = new ArrayList<>();

        private BlockDraft(String id, String labelName, boolean entry) {
            this.id = id;
            this.labelName = labelName;
            this.entry = entry;
        }
    }
}
