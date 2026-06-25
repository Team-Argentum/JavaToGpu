package net.sixik.ga_utils.javatogpu.frontend.asm;

import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuBuiltinConstant;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsic;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrFieldAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrDoWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrExpressionStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrLoopBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitchCase;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.opencl.OpenClKernelNaming;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AsmExpressionLifter {

    private final GpuIntrinsicDatabase intrinsicDatabase;
    private final AsmControlFlowGraphBuilder graphBuilder;

    public AsmExpressionLifter(GpuIntrinsicDatabase intrinsicDatabase) {
        this(intrinsicDatabase, new AsmControlFlowGraphBuilder());
    }

    public AsmExpressionLifter(GpuIntrinsicDatabase intrinsicDatabase, AsmControlFlowGraphBuilder graphBuilder) {
        this.intrinsicDatabase = Objects.requireNonNull(intrinsicDatabase, "intrinsicDatabase");
        this.graphBuilder = Objects.requireNonNull(graphBuilder, "graphBuilder");
    }

    public AsmLiftingResult liftLinearMethod(String ownerInternalName, org.objectweb.asm.tree.MethodNode methodNode) {
        return liftLinearMethod(ownerInternalName, methodNode, AsmValidationConfig.defaultConfig());
    }

    public AsmLiftingResult liftLinearMethod(
            String ownerInternalName,
            org.objectweb.asm.tree.MethodNode methodNode,
            AsmValidationConfig config
    ) {
        Objects.requireNonNull(ownerInternalName, "ownerInternalName");
        Objects.requireNonNull(methodNode, "methodNode");
        Objects.requireNonNull(config, "config");

        AsmControlFlowGraph graph = graphBuilder.build(ownerInternalName, methodNode, config);
        ensureLinearGraph(ownerInternalName, methodNode, graph);

        LiftingState state = new LiftingState(ownerInternalName, methodNode, config);
        initializeArgumentLocals(methodNode, state);

        for (AsmBasicBlock block : graph.blocks()) {
            liftWholeBlock(state, block);
        }

        if (!state.stack.isEmpty()) {
            throw new AsmFrontendException("Linear ASM lifter finished with non-empty operand stack for "
                    + ownerInternalName + "." + methodNode.name + methodNode.desc);
        }

        return new AsmLiftingResult(
                new GpuIrMethod(methodNode.name, List.copyOf(state.rootStatements)),
                List.copyOf(state.helperDependencies)
        );
    }

    public AsmLiftingResult liftStructuredMethod(String ownerInternalName, org.objectweb.asm.tree.MethodNode methodNode) {
        return liftStructuredMethod(ownerInternalName, methodNode, AsmValidationConfig.defaultConfig());
    }

    public AsmLiftingResult liftStructuredMethod(
            String ownerInternalName,
            org.objectweb.asm.tree.MethodNode methodNode,
            AsmValidationConfig config
    ) {
        Objects.requireNonNull(ownerInternalName, "ownerInternalName");
        Objects.requireNonNull(methodNode, "methodNode");
        Objects.requireNonNull(config, "config");

        AsmControlFlowGraph graph = graphBuilder.build(ownerInternalName, methodNode, config);
        LiftingState state = new LiftingState(ownerInternalName, methodNode, config);
        initializeArgumentLocals(methodNode, state);

        liftStructuredRange(state, graph, 0, graph.blocks().size());

        if (!state.stack.isEmpty()) {
            throw new AsmFrontendException("Structured ASM lifter finished with non-empty operand stack for "
                    + ownerInternalName + "." + methodNode.name + methodNode.desc);
        }

        return new AsmLiftingResult(
                new GpuIrMethod(methodNode.name, List.copyOf(state.rootStatements)),
                List.copyOf(state.helperDependencies)
        );
    }

    private void ensureLinearGraph(
            String ownerInternalName,
            org.objectweb.asm.tree.MethodNode methodNode,
            AsmControlFlowGraph graph
    ) {
        for (AsmBasicBlock block : graph.blocks()) {
            if (block.terminatorKind() == AsmTerminatorKind.CONDITIONAL_JUMP
                    || block.terminatorKind() == AsmTerminatorKind.GOTO
                    || block.terminatorKind() == AsmTerminatorKind.SWITCH) {
                throw new AsmFrontendException("AsmExpressionLifter currently supports only linear ASM blocks, but got "
                        + block.terminatorKind() + " in " + ownerInternalName + "." + methodNode.name + methodNode.desc
                        + "; switch to structured ASM lifting or rewrite the bytecode into the GPU-friendly ASM subset from docs/gpu-friendly-asm-contract.md");
            }
        }
    }

    private void liftStructuredRange(LiftingState state, AsmControlFlowGraph graph, int startIndex, int endIndexExclusive) {
        int index = startIndex;
        while (index < endIndexExclusive) {
            AsmBasicBlock block = graph.blocks().get(index);
            switch (block.terminatorKind()) {
                case NONE, FALLTHROUGH -> {
                    Integer doWhileConditionIndex = findCanonicalDoWhileConditionIndex(graph, index, endIndexExclusive);
                    if (doWhileConditionIndex != null) {
                        index = liftDoWhileLoop(state, graph, index, doWhileConditionIndex);
                        continue;
                    }
                    liftBlockWithoutTerminator(state, block);
                    index++;
                }
                case RETURN -> {
                    liftWholeBlock(state, block);
                    index++;
                }
                case CONDITIONAL_JUMP -> index = liftConditionalBlock(state, graph, index, endIndexExclusive);
                case GOTO -> throw new AsmFrontendException("Unexpected top-level GOTO block in structured ASM lifting: "
                        + graph.ownerInternalName() + "." + graph.methodName() + graph.methodDescriptor()
                        + " at " + block.id());
                case SWITCH -> index = liftSwitchBlock(state, graph, index, endIndexExclusive);
            }
        }
    }

    private Integer findCanonicalDoWhileConditionIndex(
            AsmControlFlowGraph graph,
            int bodyStartIndex,
            int endIndexExclusive
    ) {
        if (bodyStartIndex + 1 >= endIndexExclusive) {
            return null;
        }
        AsmBasicBlock bodyStartBlock = graph.blocks().get(bodyStartIndex);
        if (!(bodyStartBlock.terminatorKind() == AsmTerminatorKind.FALLTHROUGH
                || bodyStartBlock.terminatorKind() == AsmTerminatorKind.NONE)) {
            return null;
        }

        for (int conditionIndex = bodyStartIndex + 1; conditionIndex < endIndexExclusive; conditionIndex++) {
            AsmBasicBlock conditionBlock = graph.blocks().get(conditionIndex);
            if (conditionBlock.terminatorKind() != AsmTerminatorKind.CONDITIONAL_JUMP || conditionBlock.successorIds().size() != 2) {
                continue;
            }
            int jumpTargetIndex = requireBlockIndex(graph, conditionBlock.successorIds().get(0));
            int exitIndex = requireBlockIndex(graph, conditionBlock.successorIds().get(1));
            if (jumpTargetIndex == bodyStartIndex && exitIndex == conditionIndex + 1 && exitIndex <= endIndexExclusive) {
                return conditionIndex;
            }
        }
        return null;
    }

    private Integer findCanonicalDoWhileConditionIndexFromConditionalStart(
            AsmControlFlowGraph graph,
            int bodyStartIndex,
            int endIndexExclusive
    ) {
        for (int conditionIndex = bodyStartIndex + 1; conditionIndex < endIndexExclusive; conditionIndex++) {
            AsmBasicBlock conditionBlock = graph.blocks().get(conditionIndex);
            if (conditionBlock.terminatorKind() != AsmTerminatorKind.CONDITIONAL_JUMP || conditionBlock.successorIds().size() != 2) {
                continue;
            }
            int jumpTargetIndex = requireBlockIndex(graph, conditionBlock.successorIds().get(0));
            int exitIndex = requireBlockIndex(graph, conditionBlock.successorIds().get(1));
            if (jumpTargetIndex == bodyStartIndex && exitIndex == conditionIndex + 1 && exitIndex <= endIndexExclusive) {
                return conditionIndex;
            }
        }
        return null;
    }

    private int liftDoWhileLoop(
            LiftingState state,
            AsmControlFlowGraph graph,
            int bodyStartIndex,
            int conditionIndex
    ) {
        AsmBasicBlock conditionBlock = graph.blocks().get(conditionIndex);
        List<GpuIrStatement> bodyStatements = liftLoopBodyRange(
                state,
                graph,
                bodyStartIndex,
                conditionIndex,
                conditionIndex,
                conditionIndex + 1
        );
        GpuIrExpression condition = liftLoopBackConditionExpression(state, conditionBlock);
        state.emit(new GpuIrDoWhileLoop(bodyStatements, condition));
        return conditionIndex + 1;
    }

    private boolean isCanonicalSingleBlockDoWhileLoop(
            int conditionIndex,
            int jumpTargetIndex,
            int fallthroughIndex,
            int endIndexExclusive
    ) {
        return jumpTargetIndex == conditionIndex
                && fallthroughIndex == conditionIndex + 1
                && fallthroughIndex <= endIndexExclusive;
    }

    private int liftSingleBlockDoWhileLoop(
            LiftingState state,
            AsmControlFlowGraph graph,
            int blockIndex
    ) {
        AsmBasicBlock block = graph.blocks().get(blockIndex);
        int bodyInstructionCount = block.instructions().size() - conditionSuffixInstructionCount(block);
        if (bodyInstructionCount <= 0) {
            throw new AsmFrontendException("Single-block do-while requires at least one body instruction: " + block.id());
        }
        List<GpuIrStatement> bodyStatements = liftNestedInstructions(state, block.instructions().subList(0, bodyInstructionCount));
        GpuIrExpression condition = liftLoopBackConditionExpression(state, block, bodyInstructionCount);
        state.emit(new GpuIrDoWhileLoop(bodyStatements, condition));
        return blockIndex + 1;
    }

    private int liftConditionalBlock(
            LiftingState state,
            AsmControlFlowGraph graph,
            int blockIndex,
            int endIndexExclusive
    ) {
        AsmBasicBlock conditionBlock = graph.blocks().get(blockIndex);
        if (conditionBlock.successorIds().size() != 2) {
            throw new AsmFrontendException("Conditional ASM block must have exactly two successors: " + conditionBlock.id());
        }

        int jumpTargetIndex = requireBlockIndex(graph, conditionBlock.successorIds().get(0));
        int fallthroughIndex = requireBlockIndex(graph, conditionBlock.successorIds().get(1));

        if (isCanonicalSingleBlockDoWhileLoop(blockIndex, jumpTargetIndex, fallthroughIndex, endIndexExclusive)) {
            return liftSingleBlockDoWhileLoop(state, graph, blockIndex);
        }

        Integer doWhileConditionIndex = findCanonicalDoWhileConditionIndexFromConditionalStart(graph, blockIndex, endIndexExclusive);
        if (doWhileConditionIndex != null) {
            return liftDoWhileLoop(state, graph, blockIndex, doWhileConditionIndex);
        }

        if (isCanonicalWhileLoop(graph, blockIndex, jumpTargetIndex, fallthroughIndex, endIndexExclusive)) {
            return liftWhileLoop(state, graph, blockIndex, jumpTargetIndex, fallthroughIndex);
        }

        if (isCanonicalIfElse(graph, blockIndex, jumpTargetIndex, fallthroughIndex, endIndexExclusive)) {
            return liftIfElse(state, graph, blockIndex, jumpTargetIndex, fallthroughIndex);
        }

        if (isCanonicalIfWithoutElse(graph, blockIndex, jumpTargetIndex, fallthroughIndex, endIndexExclusive)) {
            return liftIfWithoutElse(state, graph, blockIndex, jumpTargetIndex, fallthroughIndex);
        }

        throw new AsmFrontendException("Unsupported conditional CFG pattern for structured ASM lifting in "
                + graph.ownerInternalName() + "." + graph.methodName() + graph.methodDescriptor()
                + " at block " + conditionBlock.id());
    }

    private boolean isCanonicalWhileLoop(
            AsmControlFlowGraph graph,
            int conditionIndex,
            int exitIndex,
            int bodyIndex,
            int endIndexExclusive
    ) {
        if (bodyIndex != conditionIndex + 1 || exitIndex <= bodyIndex || exitIndex > endIndexExclusive) {
            return false;
        }
        String conditionBlockId = graph.blocks().get(conditionIndex).id();
        boolean hasBackEdge = false;
        for (int index = bodyIndex; index < exitIndex; index++) {
            AsmBasicBlock bodyBlock = graph.blocks().get(index);
            if (bodyBlock.terminatorKind() == AsmTerminatorKind.GOTO
                    && bodyBlock.successorIds().equals(List.of(conditionBlockId))) {
                hasBackEdge = true;
            }
        }
        return hasBackEdge;
    }

    private int liftWhileLoop(
            LiftingState state,
            AsmControlFlowGraph graph,
            int conditionIndex,
            int exitIndex,
            int bodyIndex
    ) {
        AsmBasicBlock conditionBlock = graph.blocks().get(conditionIndex);
        GpuIrExpression condition = liftConditionExpression(state, conditionBlock);
        List<GpuIrStatement> bodyStatements = liftLoopBodyRange(state, graph, conditionIndex + 1, exitIndex, conditionIndex, exitIndex);
        state.emit(new GpuIrWhileLoop(condition, bodyStatements));
        return exitIndex;
    }

    private List<GpuIrStatement> liftLoopBodyRange(
            LiftingState state,
            AsmControlFlowGraph graph,
            int startIndex,
            int endIndexExclusive,
            int continueTargetIndex,
            int breakTargetIndex
    ) {
        List<GpuIrStatement> nestedStatements = new ArrayList<>();
        state.pushStatementSink(nestedStatements);
        try {
            int index = startIndex;
            while (index < endIndexExclusive) {
                AsmBasicBlock block = graph.blocks().get(index);

                if (block.terminatorKind() == AsmTerminatorKind.CONDITIONAL_JUMP) {
                    int nextIndex = tryLiftLoopTransferIf(state, graph, index, endIndexExclusive, continueTargetIndex, breakTargetIndex);
                    if (nextIndex >= 0) {
                        index = nextIndex;
                        continue;
                    }
                }

                switch (block.terminatorKind()) {
                    case NONE, FALLTHROUGH -> {
                        liftBlockWithoutTerminator(state, block);
                        index++;
                    }
                    case RETURN -> {
                        liftWholeBlock(state, block);
                        index++;
                    }
                    case GOTO -> {
                        if (block.successorIds().size() != 1) {
                            throw new AsmFrontendException("Loop body GOTO must have exactly one successor: " + block.id());
                        }
                        int gotoTargetIndex = requireBlockIndex(graph, block.successorIds().get(0));
                        if (gotoTargetIndex == continueTargetIndex) {
                            liftBlockWithoutTerminator(state, block);
                            if (index < endIndexExclusive - 1) {
                                state.emit(new net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue());
                            }
                            index++;
                            continue;
                        }
                        if (gotoTargetIndex == breakTargetIndex) {
                            liftBlockWithoutTerminator(state, block);
                            state.emit(new GpuIrBreak());
                            index++;
                            continue;
                        }
                        throw new AsmFrontendException("Unsupported loop body GOTO target in structured lifting: " + block.id());
                    }
                    case SWITCH -> index = liftSwitchBlock(
                            state,
                            graph,
                            index,
                            endIndexExclusive,
                            continueTargetIndex,
                            breakTargetIndex
                    );
                    case CONDITIONAL_JUMP -> index = liftConditionalBlock(state, graph, index, endIndexExclusive);
                }
            }
        } finally {
            state.popStatementSink();
        }
        return List.copyOf(nestedStatements);
    }

    private int tryLiftLoopTransferIf(
            LiftingState state,
            AsmControlFlowGraph graph,
            int conditionIndex,
            int endIndexExclusive,
            int continueTargetIndex,
            int breakTargetIndex
    ) {
        AsmBasicBlock conditionBlock = graph.blocks().get(conditionIndex);
        if (conditionBlock.successorIds().size() != 2) {
            return -1;
        }

        int jumpTargetIndex = requireBlockIndex(graph, conditionBlock.successorIds().get(0));
        int fallthroughIndex = requireBlockIndex(graph, conditionBlock.successorIds().get(1));
        if (fallthroughIndex != conditionIndex + 1 || fallthroughIndex >= endIndexExclusive) {
            return -1;
        }

        AsmBasicBlock transferBlock = graph.blocks().get(fallthroughIndex);
        if (transferBlock.terminatorKind() != AsmTerminatorKind.GOTO || transferBlock.successorIds().size() != 1) {
            return -1;
        }

        int transferTargetIndex = requireBlockIndex(graph, transferBlock.successorIds().get(0));
        if (transferTargetIndex != continueTargetIndex && transferTargetIndex != breakTargetIndex) {
            return -1;
        }

        if (jumpTargetIndex <= fallthroughIndex || jumpTargetIndex > endIndexExclusive) {
            return -1;
        }

        GpuIrExpression condition = liftConditionExpression(state, conditionBlock);
        List<GpuIrStatement> thenStatements = new ArrayList<>(liftNestedBlock(state, transferBlock, true));
        if (transferTargetIndex == continueTargetIndex) {
            thenStatements = new ArrayList<>(thenStatements);
            thenStatements.add(new net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue());
        } else {
            thenStatements = new ArrayList<>(thenStatements);
            thenStatements.add(new GpuIrBreak());
        }
        state.emit(new GpuIrIf(condition, List.copyOf(thenStatements), List.of()));
        return jumpTargetIndex;
    }

    private int tryLiftSwitchCaseTransferIf(
            LiftingState state,
            AsmControlFlowGraph graph,
            int conditionIndex,
            int caseEndExclusive,
            Integer mergeIndex,
            Integer continueTargetIndex,
            Integer breakTargetIndex
    ) {
        AsmBasicBlock conditionBlock = graph.blocks().get(conditionIndex);
        if (conditionBlock.successorIds().size() != 2) {
            return -1;
        }

        int jumpTargetIndex = requireBlockIndex(graph, conditionBlock.successorIds().get(0));
        int fallthroughIndex = requireBlockIndex(graph, conditionBlock.successorIds().get(1));
        if (fallthroughIndex != conditionIndex + 1 || fallthroughIndex >= caseEndExclusive) {
            return -1;
        }

        AsmBasicBlock transferBlock = graph.blocks().get(fallthroughIndex);
        if (transferBlock.terminatorKind() != AsmTerminatorKind.GOTO || transferBlock.successorIds().size() != 1) {
            return -1;
        }

        int transferTargetIndex = requireBlockIndex(graph, transferBlock.successorIds().get(0));
        boolean switchBreakTransfer = mergeIndex != null && transferTargetIndex == mergeIndex;
        boolean loopContinueTransfer = continueTargetIndex != null && transferTargetIndex == continueTargetIndex;
        boolean loopBreakTransfer = breakTargetIndex != null && transferTargetIndex == breakTargetIndex;
        if (!switchBreakTransfer && !loopContinueTransfer && !loopBreakTransfer) {
            return -1;
        }

        if (jumpTargetIndex <= fallthroughIndex || jumpTargetIndex > caseEndExclusive) {
            return -1;
        }

        GpuIrExpression condition = liftConditionExpression(state, conditionBlock);
        List<GpuIrStatement> thenStatements = new ArrayList<>(liftNestedBlock(state, transferBlock, true));
        if (switchBreakTransfer) {
            thenStatements.add(new GpuIrBreak());
        } else if (loopContinueTransfer) {
            thenStatements.add(new net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue());
        } else {
            thenStatements.add(new GpuIrLoopBreak());
        }
        state.emit(new GpuIrIf(condition, List.copyOf(thenStatements), List.of()));
        return jumpTargetIndex;
    }

    private boolean isCanonicalIfWithoutElse(
            AsmControlFlowGraph graph,
            int conditionIndex,
            int mergeIndex,
            int thenIndex,
            int endIndexExclusive
    ) {
        if (thenIndex != conditionIndex + 1 || mergeIndex != thenIndex + 1 || mergeIndex > endIndexExclusive) {
            return false;
        }
        AsmBasicBlock thenBlock = graph.blocks().get(thenIndex);
        return thenBlock.terminatorKind() == AsmTerminatorKind.FALLTHROUGH
                || thenBlock.terminatorKind() == AsmTerminatorKind.NONE
                || thenBlock.terminatorKind() == AsmTerminatorKind.RETURN;
    }

    private int liftIfWithoutElse(
            LiftingState state,
            AsmControlFlowGraph graph,
            int conditionIndex,
            int mergeIndex,
            int thenIndex
    ) {
        AsmBasicBlock conditionBlock = graph.blocks().get(conditionIndex);
        GpuIrExpression condition = liftConditionExpression(state, conditionBlock);
        List<GpuIrStatement> thenStatements = liftNestedBlock(state, graph.blocks().get(thenIndex), true);
        state.emit(new GpuIrIf(condition, thenStatements, List.of()));
        return mergeIndex;
    }

    private boolean isCanonicalIfElse(
            AsmControlFlowGraph graph,
            int conditionIndex,
            int elseIndex,
            int thenIndex,
            int endIndexExclusive
    ) {
        if (thenIndex != conditionIndex + 1 || elseIndex <= thenIndex || elseIndex > endIndexExclusive) {
            return false;
        }

        AsmBasicBlock thenBlock = graph.blocks().get(thenIndex);
        AsmBasicBlock elseBlock = graph.blocks().get(elseIndex);

        boolean thenReturns = thenBlock.terminatorKind() == AsmTerminatorKind.RETURN;
        boolean elseReturns = elseBlock.terminatorKind() == AsmTerminatorKind.RETURN;
        if (thenReturns && elseReturns) {
            return elseIndex == thenIndex + 1;
        }

        if (thenBlock.terminatorKind() == AsmTerminatorKind.GOTO && thenBlock.successorIds().size() == 1) {
            int mergeIndex = requireBlockIndex(graph, thenBlock.successorIds().get(0));
            return mergeIndex == elseIndex + 1
                    && elseBlock.terminatorKind() == AsmTerminatorKind.FALLTHROUGH;
        }

        return false;
    }

    private int liftIfElse(
            LiftingState state,
            AsmControlFlowGraph graph,
            int conditionIndex,
            int elseIndex,
            int thenIndex
    ) {
        AsmBasicBlock conditionBlock = graph.blocks().get(conditionIndex);
        AsmBasicBlock thenBlock = graph.blocks().get(thenIndex);
        AsmBasicBlock elseBlock = graph.blocks().get(elseIndex);

        GpuIrExpression condition = liftConditionExpression(state, conditionBlock);
        List<GpuIrStatement> thenStatements = liftNestedBlock(state, thenBlock, true);
        List<GpuIrStatement> elseStatements = liftNestedBlock(state, elseBlock, true);
        state.emit(new GpuIrIf(condition, thenStatements, elseStatements));

        if (thenBlock.terminatorKind() == AsmTerminatorKind.RETURN && elseBlock.terminatorKind() == AsmTerminatorKind.RETURN) {
            return elseIndex + 1;
        }

        int mergeIndex = requireBlockIndex(graph, thenBlock.successorIds().get(0));
        return mergeIndex;
    }

    private int liftSwitchBlock(
            LiftingState state,
            AsmControlFlowGraph graph,
            int switchIndex,
            int endIndexExclusive
    ) {
        return liftSwitchBlock(state, graph, switchIndex, endIndexExclusive, null, null);
    }

    private int liftSwitchBlock(
            LiftingState state,
            AsmControlFlowGraph graph,
            int switchIndex,
            int endIndexExclusive,
            Integer continueTargetIndex,
            Integer breakTargetIndex
    ) {
        AsmBasicBlock switchBlock = graph.blocks().get(switchIndex);
        if (switchBlock.instructions().isEmpty()) {
            throw new AsmFrontendException("Empty switch block: " + switchBlock.id());
        }
        AsmInstruction terminatorInstruction = switchBlock.instructions().get(switchBlock.instructions().size() - 1);
        GpuIrExpression selector = liftSwitchSelector(state, switchBlock);

        SwitchLayout layout = switchLayout(graph, terminatorInstruction.node());
        if (layout.defaultTargetIndex != null && layout.caseLabelsByTargetIndex.containsKey(layout.defaultTargetIndex)) {
            throw new AsmFrontendException("Switch default target shares block with labeled case, which is not yet supported");
        }

        Integer mergeIndex = resolveSwitchMergeIndex(
                graph,
                layout.orderedTargetIndexes,
                endIndexExclusive,
                continueTargetIndex,
                breakTargetIndex
        );
        int nextIndex = mergeIndex != null ? mergeIndex : max(layout.orderedTargetIndexes) + 1;
        if (nextIndex > endIndexExclusive) {
            throw new AsmFrontendException("Switch merge index escapes current structured range in "
                    + graph.ownerInternalName() + "." + graph.methodName() + graph.methodDescriptor());
        }

        List<GpuIrSwitchCase> cases = new ArrayList<>();
        for (int targetPosition = 0; targetPosition < layout.orderedTargetIndexes.size(); targetPosition++) {
            Integer targetIndex = layout.orderedTargetIndexes.get(targetPosition);
            List<GpuIrExpression> labels = layout.caseLabelsByTargetIndex.getOrDefault(targetIndex, List.of());
            boolean defaultCase = layout.defaultTargetIndex != null && layout.defaultTargetIndex.equals(targetIndex);
            int caseEndExclusive = targetPosition + 1 < layout.orderedTargetIndexes.size()
                    ? layout.orderedTargetIndexes.get(targetPosition + 1)
                    : nextIndex;
            List<GpuIrStatement> statements = liftSwitchCaseStatements(
                    state,
                    graph,
                    targetIndex,
                    caseEndExclusive,
                    mergeIndex,
                    layout.caseTargetIndexes,
                    continueTargetIndex,
                    breakTargetIndex
            );
            cases.add(new GpuIrSwitchCase(labels, statements, defaultCase));
        }

        state.emit(new GpuIrSwitch(selector, List.copyOf(cases)));
        return nextIndex;
    }

    private GpuIrExpression liftSwitchSelector(LiftingState state, AsmBasicBlock switchBlock) {
        liftBlockInstructions(state, switchBlock.instructions(), switchBlock.instructions().size() - 1);
        return state.popValue().expression;
    }

    private SwitchLayout switchLayout(AsmControlFlowGraph graph, AbstractInsnNode switchNode) {
        Map<Integer, List<GpuIrExpression>> labelsByTargetIndex = new LinkedHashMap<>();
        Integer defaultTargetIndex;

        if (switchNode instanceof TableSwitchInsnNode tableSwitchInsnNode) {
            defaultTargetIndex = resolveLabelTargetIndex(graph, tableSwitchInsnNode.dflt);
            for (int key = tableSwitchInsnNode.min, labelIndex = 0; key <= tableSwitchInsnNode.max; key++, labelIndex++) {
                int targetIndex = resolveLabelTargetIndex(graph, tableSwitchInsnNode.labels.get(labelIndex));
                labelsByTargetIndex.computeIfAbsent(targetIndex, ignored -> new ArrayList<>())
                        .add(new GpuIrLiteral(Integer.toString(key)));
            }
        } else if (switchNode instanceof LookupSwitchInsnNode lookupSwitchInsnNode) {
            defaultTargetIndex = resolveLabelTargetIndex(graph, lookupSwitchInsnNode.dflt);
            for (int index = 0; index < lookupSwitchInsnNode.keys.size(); index++) {
                int targetIndex = resolveLabelTargetIndex(graph, lookupSwitchInsnNode.labels.get(index));
                labelsByTargetIndex.computeIfAbsent(targetIndex, ignored -> new ArrayList<>())
                        .add(new GpuIrLiteral(Integer.toString(lookupSwitchInsnNode.keys.get(index))));
            }
        } else {
            throw new AsmFrontendException("Unsupported switch terminator node: " + switchNode.getClass().getSimpleName());
        }

        LinkedHashSet<Integer> orderedTargetIndexes = new LinkedHashSet<>();
        if (defaultTargetIndex != null) {
            orderedTargetIndexes.add(defaultTargetIndex);
        }
        orderedTargetIndexes.addAll(labelsByTargetIndex.keySet());
        List<Integer> physicalOrder = orderedTargetIndexes.stream().sorted().toList();

        return new SwitchLayout(
                labelsByTargetIndex.entrySet().stream().collect(LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), List.copyOf(entry.getValue())),
                        LinkedHashMap::putAll),
                defaultTargetIndex,
                physicalOrder,
                Set.copyOf(physicalOrder)
        );
    }

    private int resolveLabelTargetIndex(AsmControlFlowGraph graph, org.objectweb.asm.tree.LabelNode labelNode) {
        AbstractInsnNode targetInstruction = labelNode;
        while (targetInstruction != null && targetInstruction.getOpcode() < 0) {
            targetInstruction = targetInstruction.getNext();
        }
        if (targetInstruction == null) {
            throw new AsmFrontendException("Switch target label does not resolve to an executable instruction");
        }

        for (AsmBasicBlock block : graph.blocks()) {
            if (!block.instructions().isEmpty() && block.instructions().get(0).node() == targetInstruction) {
                return requireBlockIndex(graph, block.id());
            }
        }
        throw new AsmFrontendException("Unable to resolve switch target label to CFG block");
    }

    private Integer resolveSwitchMergeIndex(
            AsmControlFlowGraph graph,
            List<Integer> targetIndexes,
            int switchEndIndexExclusive,
            Integer continueTargetIndex,
            Integer breakTargetIndex
    ) {
        Integer mergeIndex = null;
        for (int targetPosition = 0; targetPosition < targetIndexes.size(); targetPosition++) {
            int caseStartIndex = targetIndexes.get(targetPosition);
            int caseEndExclusive = targetPosition + 1 < targetIndexes.size()
                    ? targetIndexes.get(targetPosition + 1)
                    : switchEndIndexExclusive;
            Integer nextCaseIndex = targetPosition + 1 < targetIndexes.size()
                    ? targetIndexes.get(targetPosition + 1)
                    : null;
            for (int index = caseStartIndex; index < caseEndExclusive; index++) {
                if (mergeIndex != null && index >= mergeIndex) {
                    break;
                }
                AsmBasicBlock caseBlock = graph.blocks().get(index);
                if (caseBlock.terminatorKind() == AsmTerminatorKind.GOTO) {
                    if (caseBlock.successorIds().size() != 1) {
                        throw new AsmFrontendException("Switch case GOTO must have exactly one successor: " + caseBlock.id());
                    }
                    int candidateMergeIndex = requireBlockIndex(graph, caseBlock.successorIds().get(0));
                    if (continueTargetIndex != null && candidateMergeIndex == continueTargetIndex) {
                        continue;
                    }
                    if (breakTargetIndex != null && candidateMergeIndex == breakTargetIndex) {
                        continue;
                    }
                    if (nextCaseIndex != null
                            && candidateMergeIndex >= caseStartIndex
                            && candidateMergeIndex < nextCaseIndex) {
                        continue;
                    }
                    if (targetIndexes.contains(candidateMergeIndex)) {
                        continue;
                    }
                    if (mergeIndex == null) {
                        mergeIndex = candidateMergeIndex;
                    } else if (!mergeIndex.equals(candidateMergeIndex)) {
                        throw new AsmFrontendException("Switch cases jump to multiple merge blocks, which is not yet supported");
                    }
                }
            }
        }
        return mergeIndex;
    }

    private List<GpuIrStatement> liftSwitchCaseStatements(
            LiftingState state,
            AsmControlFlowGraph graph,
            int caseStartIndex,
            int caseEndExclusive,
            Integer mergeIndex,
            java.util.Set<Integer> caseTargetIndexes,
            Integer continueTargetIndex,
            Integer breakTargetIndex
    ) {
        List<GpuIrStatement> statements = new ArrayList<>();
        state.pushStatementSink(statements);
        try {
            int index = caseStartIndex;
            while (index < caseEndExclusive) {
                AsmBasicBlock caseBlock = graph.blocks().get(index);
                if (caseBlock.terminatorKind() == AsmTerminatorKind.CONDITIONAL_JUMP) {
                    int nextIndex = tryLiftSwitchCaseTransferIf(
                            state,
                            graph,
                            index,
                            caseEndExclusive,
                            mergeIndex,
                            continueTargetIndex,
                            breakTargetIndex
                    );
                    if (nextIndex >= 0) {
                        index = nextIndex;
                        continue;
                    }
                }
                switch (caseBlock.terminatorKind()) {
                    case NONE, FALLTHROUGH -> {
                        liftBlockWithoutTerminator(state, caseBlock);
                        index++;
                    }
                    case RETURN -> {
                        liftWholeBlock(state, caseBlock);
                        index = caseEndExclusive;
                    }
                    case GOTO -> {
                        liftBlockWithoutTerminator(state, caseBlock);
                        int gotoTarget = requireBlockIndex(graph, caseBlock.successorIds().get(0));
                        if (Objects.equals(mergeIndex, gotoTarget)) {
                            state.emit(new GpuIrBreak());
                            index = caseEndExclusive;
                            continue;
                        }
                        if (continueTargetIndex != null && gotoTarget == continueTargetIndex) {
                            state.emit(new net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue());
                            index = caseEndExclusive;
                            continue;
                        }
                        if (breakTargetIndex != null && gotoTarget == breakTargetIndex) {
                            state.emit(new GpuIrLoopBreak());
                            index = caseEndExclusive;
                            continue;
                        }
                        if (caseTargetIndexes.contains(gotoTarget) && gotoTarget == caseEndExclusive) {
                            index = caseEndExclusive;
                            continue;
                        }
                        throw new AsmFrontendException("Switch case jumps to non-merge target, which is not yet supported: " + caseBlock.id());
                    }
                    case CONDITIONAL_JUMP -> index = liftConditionalBlock(state, graph, index, caseEndExclusive);
                    case SWITCH -> index = liftSwitchBlock(state, graph, index, caseEndExclusive, continueTargetIndex, breakTargetIndex);
                    default -> throw new AsmFrontendException("Multi-block switch case lifting does not support terminator "
                            + caseBlock.terminatorKind() + " in block " + caseBlock.id());
                }
            }
        } finally {
            state.popStatementSink();
        }
        return List.copyOf(statements);
    }

    private int max(List<Integer> values) {
        int max = Integer.MIN_VALUE;
        for (Integer value : values) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    private List<GpuIrStatement> liftNestedBlock(LiftingState state, AsmBasicBlock block, boolean allowGotoTerminator) {
        List<GpuIrStatement> nestedStatements = new ArrayList<>();
        state.pushStatementSink(nestedStatements);
        try {
            switch (block.terminatorKind()) {
                case NONE, FALLTHROUGH -> liftBlockWithoutTerminator(state, block);
                case RETURN -> liftWholeBlock(state, block);
                case GOTO -> {
                    if (!allowGotoTerminator) {
                        throw new AsmFrontendException("Unexpected GOTO block in nested structured lifting: " + block.id());
                    }
                    liftBlockWithoutTerminator(state, block);
                }
                default -> throw new AsmFrontendException("Nested structured lifting currently supports only linear nested blocks, but got "
                        + block.terminatorKind() + " in block " + block.id());
            }
        } finally {
            state.popStatementSink();
        }
        return List.copyOf(nestedStatements);
    }

    private GpuIrExpression liftConditionExpression(LiftingState state, AsmBasicBlock block) {
        return liftConditionExpression(state, block, 0);
    }

    private GpuIrExpression liftConditionExpression(LiftingState state, AsmBasicBlock block, int prefixInstructionCount) {
        return liftJumpConditionExpression(state, block, prefixInstructionCount, true);
    }

    private GpuIrExpression liftLoopBackConditionExpression(LiftingState state, AsmBasicBlock block) {
        return liftLoopBackConditionExpression(state, block, 0);
    }

    private GpuIrExpression liftLoopBackConditionExpression(LiftingState state, AsmBasicBlock block, int prefixInstructionCount) {
        return liftJumpConditionExpression(state, block, prefixInstructionCount, false);
    }

    private GpuIrExpression liftJumpConditionExpression(
            LiftingState state,
            AsmBasicBlock block,
            int prefixInstructionCount,
            boolean invertJumpMeaning
    ) {
        if (block.instructions().isEmpty()) {
            throw new AsmFrontendException("Empty conditional block: " + block.id());
        }
        AsmInstruction terminatorInstruction = block.instructions().get(block.instructions().size() - 1);
        if (!(terminatorInstruction.node() instanceof JumpInsnNode jumpInsnNode)) {
            throw new AsmFrontendException("Expected jump terminator in conditional block: " + block.id());
        }

        List<AsmInstruction> conditionInstructions = block.instructions().subList(prefixInstructionCount, block.instructions().size());
        if (conditionInstructions.size() < conditionSuffixInstructionCount(block)) {
            throw new AsmFrontendException("Conditional block is too small for its jump pattern: " + block.id());
        }
        int comparisonInstructionCount = endsWithCompareInstruction(block) ? 2 : 1;
        liftBlockInstructions(state, conditionInstructions, conditionInstructions.size() - comparisonInstructionCount);

        if (endsWithCompareInstruction(block)) {
            AsmInstruction compareInstruction = block.instructions().get(block.instructions().size() - 2);
            StackValue right = state.popValue();
            StackValue left = state.popValue();
            return conditionFromComparisonOpcode(compareInstruction.opcode(), jumpInsnNode.getOpcode(), left.expression, right.expression, invertJumpMeaning);
        }

        if (isIntegerCompareJump(jumpInsnNode.getOpcode())) {
            StackValue right = state.popValue();
            StackValue left = state.popValue();
            return conditionFromIntegerCompareJump(jumpInsnNode.getOpcode(), left.expression, right.expression, invertJumpMeaning);
        }

        return conditionFromDirectJump(jumpInsnNode.getOpcode(), state.popValue().expression, invertJumpMeaning);
    }

    private int conditionSuffixInstructionCount(AsmBasicBlock block) {
        if (endsWithCompareInstruction(block)) {
            return 4;
        }
        if (isIntegerCompareJump(block.instructions().get(block.instructions().size() - 1).opcode())) {
            return 3;
        }
        return 2;
    }

    private List<GpuIrStatement> liftNestedInstructions(LiftingState state, List<AsmInstruction> instructions) {
        List<GpuIrStatement> nestedStatements = new ArrayList<>();
        state.pushStatementSink(nestedStatements);
        try {
            liftBlockInstructions(state, instructions, instructions.size());
        } finally {
            state.popStatementSink();
        }
        return List.copyOf(nestedStatements);
    }

    private boolean endsWithCompareInstruction(AsmBasicBlock block) {
        if (block.instructions().size() < 2) {
            return false;
        }
        int opcode = block.instructions().get(block.instructions().size() - 2).opcode();
        return opcode == Opcodes.LCMP
                || opcode == Opcodes.FCMPL
                || opcode == Opcodes.FCMPG
                || opcode == Opcodes.DCMPL
                || opcode == Opcodes.DCMPG;
    }

    private GpuIrExpression conditionFromDirectJump(int jumpOpcode, GpuIrExpression operand, boolean invertJumpMeaning) {
        return switch (jumpOpcode) {
            case Opcodes.IFEQ -> invertJumpMeaning ? operand : new GpuIrUnary("!", operand);
            case Opcodes.IFNE -> invertJumpMeaning ? new GpuIrUnary("!", operand) : operand;
            case Opcodes.IFLT -> new GpuIrBinary(invertJumpMeaning ? ">=" : "<", operand, new GpuIrLiteral("0"));
            case Opcodes.IFLE -> new GpuIrBinary(invertJumpMeaning ? ">" : "<=", operand, new GpuIrLiteral("0"));
            case Opcodes.IFGT -> new GpuIrBinary(invertJumpMeaning ? "<=" : ">", operand, new GpuIrLiteral("0"));
            case Opcodes.IFGE -> new GpuIrBinary(invertJumpMeaning ? "<" : ">=", operand, new GpuIrLiteral("0"));
            default -> throw new AsmFrontendException("Unsupported direct conditional jump opcode in structured lifting: "
                    + AsmOpcodeNames.nameOf(jumpOpcode));
        };
    }

    private GpuIrExpression conditionFromComparisonOpcode(
            int compareOpcode,
            int jumpOpcode,
            GpuIrExpression left,
            GpuIrExpression right,
            boolean invertJumpMeaning
    ) {
        String operator = comparisonOperator(jumpOpcode, invertJumpMeaning);
        return new GpuIrBinary(operator, left, right);
    }

    private GpuIrExpression conditionFromIntegerCompareJump(
            int jumpOpcode,
            GpuIrExpression left,
            GpuIrExpression right,
            boolean invertJumpMeaning
    ) {
        String operator = comparisonOperator(jumpOpcode, invertJumpMeaning);
        return new GpuIrBinary(operator, left, right);
    }

    private String comparisonOperator(int jumpOpcode, boolean invertJumpMeaning) {
        return switch (jumpOpcode) {
            case Opcodes.IFEQ, Opcodes.IF_ICMPEQ -> invertJumpMeaning ? "!=" : "==";
            case Opcodes.IFNE, Opcodes.IF_ICMPNE -> invertJumpMeaning ? "==" : "!=";
            case Opcodes.IFLT, Opcodes.IF_ICMPLT -> invertJumpMeaning ? ">=" : "<";
            case Opcodes.IFLE, Opcodes.IF_ICMPLE -> invertJumpMeaning ? ">" : "<=";
            case Opcodes.IFGT, Opcodes.IF_ICMPGT -> invertJumpMeaning ? "<=" : ">";
            case Opcodes.IFGE, Opcodes.IF_ICMPGE -> invertJumpMeaning ? "<" : ">=";
            default -> throw new AsmFrontendException("Unsupported comparison jump opcode in structured lifting: "
                    + AsmOpcodeNames.nameOf(jumpOpcode));
        };
    }

    private boolean isIntegerCompareJump(int jumpOpcode) {
        return jumpOpcode == Opcodes.IF_ICMPEQ
                || jumpOpcode == Opcodes.IF_ICMPNE
                || jumpOpcode == Opcodes.IF_ICMPLT
                || jumpOpcode == Opcodes.IF_ICMPLE
                || jumpOpcode == Opcodes.IF_ICMPGT
                || jumpOpcode == Opcodes.IF_ICMPGE;
    }

    private int requireBlockIndex(AsmControlFlowGraph graph, String blockId) {
        for (int index = 0; index < graph.blocks().size(); index++) {
            if (graph.blocks().get(index).id().equals(blockId)) {
                return index;
            }
        }
        throw new AsmFrontendException("Unknown CFG block id in structured lifting: " + blockId);
    }

    private void initializeArgumentLocals(org.objectweb.asm.tree.MethodNode methodNode, LiftingState state) {
        Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
        AbstractInsnNode firstInstruction = methodNode.instructions.getFirst();
        int slot = 0;
        int argumentIndex = 0;
        for (Type argumentType : argumentTypes) {
            String name = resolveLocalName(state, slot, firstInstruction, "arg" + argumentIndex);
            String javaType = toJavaTypeName(argumentType);
            LocalInfo local = new LocalInfo(name, javaType, true);
            putLocal(state, slot, javaType, local);
            slot += argumentType.getSize();
            argumentIndex++;
        }
    }

    private void liftInstruction(LiftingState state, AsmInstruction instruction) {
        AbstractInsnNode node = instruction.node();
        if (node instanceof VarInsnNode varInsnNode) {
            liftVarInstruction(state, varInsnNode, instruction);
            return;
        }
        if (node instanceof LdcInsnNode ldcInsnNode) {
            state.stack.push(new StackValue(new GpuIrLiteral(literalText(ldcInsnNode.cst)), literalType(ldcInsnNode.cst)));
            return;
        }
        if (node instanceof IntInsnNode intInsnNode) {
            state.stack.push(new StackValue(new GpuIrLiteral(Integer.toString(intInsnNode.operand)), "int"));
            return;
        }
        if (node instanceof IincInsnNode iincInsnNode) {
            liftIincInstruction(state, iincInsnNode);
            return;
        }
        if (node instanceof MethodInsnNode methodInsnNode) {
            liftMethodInstruction(state, methodInsnNode);
            return;
        }
        if (node instanceof FieldInsnNode fieldInsnNode) {
            liftFieldInstruction(state, fieldInsnNode);
            return;
        }
        if (node instanceof TypeInsnNode typeInsnNode) {
            liftTypeInstruction(state, typeInsnNode, instruction);
            return;
        }

        switch (instruction.opcode()) {
            case Opcodes.NOP -> {
            }
            case Opcodes.ACONST_NULL -> state.stack.push(new StackValue(new GpuIrLiteral("0"), "null"));
            case Opcodes.ICONST_M1 -> pushIntLiteral(state, -1);
            case Opcodes.ICONST_0 -> pushIntLiteral(state, 0);
            case Opcodes.ICONST_1 -> pushIntLiteral(state, 1);
            case Opcodes.ICONST_2 -> pushIntLiteral(state, 2);
            case Opcodes.ICONST_3 -> pushIntLiteral(state, 3);
            case Opcodes.ICONST_4 -> pushIntLiteral(state, 4);
            case Opcodes.ICONST_5 -> pushIntLiteral(state, 5);
            case Opcodes.LCONST_0 -> state.stack.push(new StackValue(new GpuIrLiteral("0L"), "long"));
            case Opcodes.LCONST_1 -> state.stack.push(new StackValue(new GpuIrLiteral("1L"), "long"));
            case Opcodes.FCONST_0 -> state.stack.push(new StackValue(new GpuIrLiteral("0.0f"), "float"));
            case Opcodes.FCONST_1 -> state.stack.push(new StackValue(new GpuIrLiteral("1.0f"), "float"));
            case Opcodes.FCONST_2 -> state.stack.push(new StackValue(new GpuIrLiteral("2.0f"), "float"));
            case Opcodes.DCONST_0 -> state.stack.push(new StackValue(new GpuIrLiteral("0.0"), "double"));
            case Opcodes.DCONST_1 -> state.stack.push(new StackValue(new GpuIrLiteral("1.0"), "double"));
            case Opcodes.POP -> liftPopInstruction(state);
            case Opcodes.DUP -> state.stack.push(state.peekValue().copy());
            case Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD, Opcodes.AALOAD, Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD ->
                    liftArrayLoad(state, instruction.opcode());
            case Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE, Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE ->
                    liftArrayStore(state);
            case Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD -> liftBinary(state, "+");
            case Opcodes.ISUB, Opcodes.LSUB, Opcodes.FSUB, Opcodes.DSUB -> liftBinary(state, "-");
            case Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL -> liftBinary(state, "*");
            case Opcodes.IDIV, Opcodes.LDIV, Opcodes.FDIV, Opcodes.DDIV -> liftBinary(state, "/");
            case Opcodes.IREM, Opcodes.LREM, Opcodes.FREM, Opcodes.DREM -> liftBinary(state, "%");
            case Opcodes.IAND, Opcodes.LAND -> liftBinary(state, "&");
            case Opcodes.IOR, Opcodes.LOR -> liftBinary(state, "|");
            case Opcodes.IXOR, Opcodes.LXOR -> liftBinary(state, "^");
            case Opcodes.ISHL, Opcodes.LSHL -> liftBinary(state, "<<");
            case Opcodes.ISHR, Opcodes.LSHR -> liftBinary(state, ">>");
            case Opcodes.IUSHR, Opcodes.LUSHR -> liftBinary(state, ">>>");
            case Opcodes.INEG, Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG -> liftUnary(state, "-");
            case Opcodes.I2L -> liftCast(state, "long");
            case Opcodes.I2F -> liftCast(state, "float");
            case Opcodes.I2D -> liftCast(state, "double");
            case Opcodes.L2I -> liftCast(state, "int");
            case Opcodes.L2F -> liftCast(state, "float");
            case Opcodes.L2D -> liftCast(state, "double");
            case Opcodes.F2I -> liftCast(state, "int");
            case Opcodes.F2L -> liftCast(state, "long");
            case Opcodes.F2D -> liftCast(state, "double");
            case Opcodes.D2I -> liftCast(state, "int");
            case Opcodes.D2L -> liftCast(state, "long");
            case Opcodes.D2F -> liftCast(state, "float");
            case Opcodes.I2B -> liftCast(state, "byte");
            case Opcodes.I2C -> liftCast(state, "char");
            case Opcodes.I2S -> liftCast(state, "short");
            case Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN ->
                    state.emit(new GpuIrReturn(state.popValue().expression));
            case Opcodes.RETURN -> state.emit(new GpuIrReturn(null));
            default -> throw new AsmFrontendException("AsmExpressionLifter does not yet support opcode "
                    + instruction.opcodeName() + " in linear lifting"
                    + "; rewrite this bytecode pattern into the GPU-friendly ASM subset from docs/gpu-friendly-asm-contract.md");
        }
    }

    private void liftWholeBlock(LiftingState state, AsmBasicBlock block) {
        liftBlockInstructions(state, block.instructions(), block.instructions().size());
    }

    private void liftBlockWithoutTerminator(LiftingState state, AsmBasicBlock block) {
        int count = switch (block.terminatorKind()) {
            case NONE, FALLTHROUGH -> block.instructions().size();
            default -> block.instructions().size() - 1;
        };
        liftBlockInstructions(state, block.instructions(), count);
    }

    private void liftBlockInstructions(LiftingState state, List<AsmInstruction> instructions, int count) {
        for (int index = 0; index < count; index++) {
            liftInstruction(state, instructions.get(index));
        }
    }

    private void liftVarInstruction(LiftingState state, VarInsnNode instruction, AsmInstruction asmInstruction) {
        switch (instruction.getOpcode()) {
            case Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD -> {
                LocalInfo local = requireLocal(state, instruction.var);
                state.stack.push(new StackValue(new GpuIrVariableRef(local.name), local.javaType));
            }
            case Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE -> {
                StackValue value = state.popValue();
                LocalInfo existing = state.localsBySlot.get(instruction.var);
                String resolvedName = resolveLocalName(
                        state,
                        instruction.var,
                        instruction,
                        existing != null ? existing.name : "tmp" + instruction.var
                );
                if (existing == null
                        || !existing.declared
                        || !existing.name.equals(resolvedName)
                        || !existing.javaType.equals(value.javaType)) {
                    LocalInfo local = new LocalInfo(resolvedName, value.javaType, true);
                    putLocal(state, instruction.var, value.javaType, local);
                    state.emit(new GpuIrVariableDeclaration(local.javaType, local.name, value.expression));
                } else {
                    state.emit(new GpuIrAssignment(new GpuIrVariableRef(existing.name), value.expression));
                }
            }
            default -> throw new AsmFrontendException("Unexpected var opcode in AsmExpressionLifter: " + asmInstruction.opcodeName());
        }
    }

    private void liftIincInstruction(LiftingState state, IincInsnNode instruction) {
        LocalInfo local = requireLocal(state, instruction.var);
        GpuIrExpression updated = new GpuIrBinary(
                instruction.incr >= 0 ? "+" : "-",
                new GpuIrVariableRef(local.name),
                new GpuIrLiteral(Integer.toString(Math.abs(instruction.incr)))
        );
        state.emit(new GpuIrAssignment(new GpuIrVariableRef(local.name), updated));
    }

    private void liftMethodInstruction(LiftingState state, MethodInsnNode instruction) {
        Type methodType = Type.getMethodType(instruction.desc);
        Type[] argumentTypes = methodType.getArgumentTypes();
        List<StackValue> stackArguments = new ArrayList<>(argumentTypes.length);
        for (int index = argumentTypes.length - 1; index >= 0; index--) {
            stackArguments.add(0, state.popValue());
        }
        List<GpuIrExpression> arguments = stackArguments.stream().map(value -> value.expression).toList();
        List<String> argumentTypeNames = stackArguments.stream().map(value -> value.javaType).toList();

        if (isGpuOwner(instruction.owner)) {
            GpuIntrinsic intrinsic = requireIntrinsic(instruction.owner, instruction.name, argumentTypeNames);
            GpuIrIntrinsicCall call = new GpuIrIntrinsicCall(
                    null,
                    intrinsic.backendName(),
                    intrinsic.codeTemplate(),
                    intrinsic.resultType(),
                    arguments
            );
            if (methodType.getReturnType().getSort() == Type.VOID) {
                state.emit(new GpuIrExpressionStatement(call));
            } else {
                state.stack.push(new StackValue(call, intrinsic.resultType()));
            }
            return;
        }

        String ownerSimpleName = simpleInternalName(instruction.owner);
        String emittedName = OpenClKernelNaming.toHelperFunctionName(ownerSimpleName, instruction.name, argumentTypeNames);
        String returnType = toJavaTypeName(methodType.getReturnType());
        GpuIrHelperCall call = new GpuIrHelperCall(emittedName, returnType, arguments);
        state.helperDependencies.add(emittedName);
        if (methodType.getReturnType().getSort() == Type.VOID) {
            state.emit(new GpuIrExpressionStatement(call));
        } else {
            state.stack.push(new StackValue(call, returnType));
        }
    }

    private void liftFieldInstruction(LiftingState state, FieldInsnNode instruction) {
        switch (instruction.getOpcode()) {
            case Opcodes.GETSTATIC -> {
                GpuBuiltinConstant constant = requireBuiltinConstant(instruction.owner, instruction.name);
                state.stack.push(new StackValue(new GpuIrLiteral(constant.sourceText()), constant.javaType()));
            }
            case Opcodes.GETFIELD -> {
                StackValue target = state.popValue();
                state.stack.push(new StackValue(
                        new GpuIrFieldAccess(target.expression, instruction.name),
                        toJavaTypeName(Type.getType(instruction.desc))
                ));
            }
            case Opcodes.PUTFIELD -> {
                StackValue value = state.popValue();
                StackValue target = state.popValue();
                state.emit(new GpuIrAssignment(
                        new GpuIrFieldAccess(target.expression, instruction.name),
                        value.expression
                ));
            }
            default -> throw new AsmFrontendException("Unexpected field opcode in AsmExpressionLifter: "
                    + AsmOpcodeNames.nameOf(instruction.getOpcode()));
        }
    }

    private void liftTypeInstruction(LiftingState state, TypeInsnNode instruction, AsmInstruction asmInstruction) {
        throw new AsmFrontendException("AsmExpressionLifter does not yet support object construction patterns like "
                + asmInstruction.opcodeName() + " (" + instruction.desc + ")"
                + "; restrict construction to supported pointer/vector/struct value patterns from docs/gpu-friendly-asm-contract.md");
    }

    private void liftPopInstruction(LiftingState state) {
        StackValue value = state.popValue();
        if (value.expression instanceof GpuIrIntrinsicCall || value.expression instanceof GpuIrHelperCall) {
            state.emit(new GpuIrExpressionStatement(value.expression));
        }
    }

    private void liftArrayLoad(LiftingState state, int opcode) {
        StackValue index = state.popValue();
        StackValue array = state.popValue();
        if (!(array.expression instanceof GpuIrVariableRef variableRef)) {
            throw new AsmFrontendException("AsmExpressionLifter currently supports array loads only from local/argument variables"
                    + "; store the array reference in a local slot before loading from it");
        }
        String elementType = elementType(array.javaType, opcode);
        state.stack.push(new StackValue(new GpuIrArrayAccess(variableRef.name(), index.expression), elementType));
    }

    private void liftArrayStore(LiftingState state) {
        StackValue value = state.popValue();
        StackValue index = state.popValue();
        StackValue array = state.popValue();
        if (!(array.expression instanceof GpuIrVariableRef variableRef)) {
            throw new AsmFrontendException("AsmExpressionLifter currently supports array stores only into local/argument variables"
                    + "; store the array reference in a local slot before writing into it");
        }
        state.emit(new GpuIrAssignment(
                new GpuIrArrayAccess(variableRef.name(), index.expression),
                value.expression
        ));
    }

    private void liftBinary(LiftingState state, String operator) {
        StackValue right = state.popValue();
        StackValue left = state.popValue();
        state.stack.push(new StackValue(
                new GpuIrBinary(operator, left.expression, right.expression),
                commonBinaryType(left.javaType, right.javaType)
        ));
    }

    private void liftUnary(LiftingState state, String operator) {
        StackValue operand = state.popValue();
        state.stack.push(new StackValue(new GpuIrUnary(operator, operand.expression), operand.javaType));
    }

    private void liftCast(LiftingState state, String targetType) {
        StackValue operand = state.popValue();
        state.stack.push(new StackValue(new GpuIrCast(targetType, operand.expression), targetType));
    }

    private void pushIntLiteral(LiftingState state, int value) {
        state.stack.push(new StackValue(new GpuIrLiteral(Integer.toString(value)), "int"));
    }

    private LocalInfo requireLocal(LiftingState state, int slot) {
        LocalInfo local = state.localsBySlot.get(slot);
        if (local == null) {
            throw new AsmFrontendException("Unknown local slot in AsmExpressionLifter: " + slot);
        }
        return local;
    }

    private void putLocal(LiftingState state, int slot, String javaType, LocalInfo local) {
        state.localsBySlot.put(slot, local);
        if ("long".equals(javaType) || "double".equals(javaType)) {
            state.localsBySlot.put(slot + 1, local);
        }
    }

    private StackValue popValue(LiftingState state) {
        return state.popValue();
    }

    private String resolveLocalName(
            LiftingState state,
            int slot,
            AbstractInsnNode anchor,
            String fallback
    ) {
        if (anchor == null || state.methodNode.localVariables == null || state.methodNode.localVariables.isEmpty()) {
            return fallback;
        }

        Integer anchorIndex = state.instructionIndexes.get(anchor);
        if (anchorIndex == null) {
            return fallback;
        }

        String slotOnlyFallback = null;
        for (org.objectweb.asm.tree.LocalVariableNode localVariable : state.methodNode.localVariables) {
            if (localVariable.index != slot || localVariable.name == null || localVariable.name.isBlank()) {
                continue;
            }
            if (slotOnlyFallback == null) {
                slotOnlyFallback = localVariable.name;
            }

            Integer startIndex = state.instructionIndexes.get(localVariable.start);
            Integer endIndex = state.instructionIndexes.get(localVariable.end);
            if (startIndex == null || endIndex == null) {
                continue;
            }
            if (anchorIndex >= startIndex && anchorIndex < endIndex) {
                return localVariable.name;
            }
        }

        return slotOnlyFallback != null ? slotOnlyFallback : fallback;
    }

    private String elementType(String arrayType, int opcode) {
        if (arrayType.endsWith("[]")) {
            return arrayType.substring(0, arrayType.length() - 2);
        }
        return switch (opcode) {
            case Opcodes.BALOAD -> "byte";
            case Opcodes.CALOAD -> "char";
            case Opcodes.SALOAD -> "short";
            case Opcodes.IALOAD -> "int";
            case Opcodes.LALOAD -> "long";
            case Opcodes.FALOAD -> "float";
            case Opcodes.DALOAD -> "double";
            default -> "Object";
        };
    }

    private String commonBinaryType(String leftType, String rightType) {
        if (leftType.equals(rightType)) {
            return leftType;
        }
        List<String> rank = List.of("byte", "short", "int", "long", "float", "double");
        int leftRank = rank.indexOf(leftType);
        int rightRank = rank.indexOf(rightType);
        if (leftRank >= 0 && rightRank >= 0) {
            return rank.get(Math.max(leftRank, rightRank));
        }
        return leftType;
    }

    private GpuIntrinsic requireIntrinsic(String ownerInternalName, String methodName, List<String> argumentTypeNames) {
        String qualifiedOwner = ownerInternalName.replace('/', '.');
        try {
            return intrinsicDatabase.require(qualifiedOwner, methodName, argumentTypeNames);
        } catch (IllegalArgumentException ignored) {
            return intrinsicDatabase.require(simpleInternalName(ownerInternalName), methodName, argumentTypeNames);
        }
    }

    private GpuBuiltinConstant requireBuiltinConstant(String ownerInternalName, String fieldName) {
        String qualifiedOwner = ownerInternalName.replace('/', '.');
        for (GpuBuiltinConstant constant : intrinsicDatabase.builtinConstants()) {
            boolean sameOwner = qualifiedOwner.equals(constant.ownerQualifiedName())
                    || simpleInternalName(ownerInternalName).equals(constant.ownerSimpleName());
            if (sameOwner && fieldName.equals(constant.name())) {
                return constant;
            }
        }
        throw new AsmFrontendException(
                "Unknown builtin constant in AsmExpressionLifter: "
                        + ownerInternalName
                        + "."
                        + fieldName
                        + "; use a known GPU builtin constant or register the constant through the intrinsic database"
        );
    }

    private boolean isGpuOwner(String ownerInternalName) {
        return "net/sixik/ga_utils/javatogpu/api/GPU".equals(ownerInternalName)
                || "GPU".equals(simpleInternalName(ownerInternalName));
    }

    private String literalText(Object constant) {
        if (constant instanceof Integer integer) {
            return Integer.toString(integer);
        }
        if (constant instanceof Long value) {
            return value + "L";
        }
        if (constant instanceof Float value) {
            return Float.toString(value) + "f";
        }
        if (constant instanceof Double value) {
            return Double.toString(value);
        }
        throw new AsmFrontendException(
                "Unsupported LDC constant in AsmExpressionLifter: "
                        + constant
                        + "; only integer/long/float/double constants are supported in the current GPU-friendly ASM subset"
        );
    }

    private String literalType(Object constant) {
        if (constant instanceof Integer) {
            return "int";
        }
        if (constant instanceof Long) {
            return "long";
        }
        if (constant instanceof Float) {
            return "float";
        }
        if (constant instanceof Double) {
            return "double";
        }
        throw new AsmFrontendException(
                "Unsupported LDC constant in AsmExpressionLifter: "
                        + constant
                        + "; only integer/long/float/double constants are supported in the current GPU-friendly ASM subset"
        );
    }

    private String toJavaTypeName(Type type) {
        return switch (type.getSort()) {
            case Type.VOID -> "void";
            case Type.BOOLEAN -> "boolean";
            case Type.CHAR -> "char";
            case Type.BYTE -> "byte";
            case Type.SHORT -> "short";
            case Type.INT -> "int";
            case Type.FLOAT -> "float";
            case Type.LONG -> "long";
            case Type.DOUBLE -> "double";
            case Type.ARRAY -> toJavaTypeName(type.getElementType()) + "[]";
            case Type.OBJECT -> simpleInternalName(type.getInternalName());
            default -> throw new AsmFrontendException("Unsupported ASM type in AsmExpressionLifter: " + type.getDescriptor());
        };
    }

    private String simpleInternalName(String internalName) {
        int separator = internalName.lastIndexOf('/');
        return separator >= 0 ? internalName.substring(separator + 1) : internalName;
    }

    private static final class LiftingState {
        private final String ownerInternalName;
        private final org.objectweb.asm.tree.MethodNode methodNode;
        private final AsmValidationConfig config;
        private final Deque<StackValue> stack = new ArrayDeque<>();
        private final Map<AbstractInsnNode, Integer> instructionIndexes = new IdentityHashMap<>();
        private final Map<Integer, LocalInfo> localsBySlot = new LinkedHashMap<>();
        private final List<GpuIrStatement> rootStatements = new ArrayList<>();
        private final Deque<List<GpuIrStatement>> statementSinks = new ArrayDeque<>();
        private final LinkedHashSet<String> helperDependencies = new LinkedHashSet<>();

        private LiftingState(
                String ownerInternalName,
                org.objectweb.asm.tree.MethodNode methodNode,
                AsmValidationConfig config
        ) {
            this.ownerInternalName = ownerInternalName;
            this.methodNode = methodNode;
            this.config = config;
            this.statementSinks.push(rootStatements);
            int instructionIndex = 0;
            for (AbstractInsnNode instruction = methodNode.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                instructionIndexes.put(instruction, instructionIndex++);
            }
        }

        private StackValue popValue() {
            if (stack.isEmpty()) {
                throw new AsmFrontendException("Operand stack underflow in AsmExpressionLifter for "
                        + ownerInternalName + "." + methodNode.name + methodNode.desc);
            }
            return stack.pop();
        }

        private StackValue peekValue() {
            if (stack.isEmpty()) {
                throw new AsmFrontendException("Operand stack underflow in AsmExpressionLifter for "
                        + ownerInternalName + "." + methodNode.name + methodNode.desc);
            }
            return stack.peek();
        }

        private void emit(GpuIrStatement statement) {
            statementSinks.peek().add(statement);
        }

        private void pushStatementSink(List<GpuIrStatement> statementSink) {
            statementSinks.push(statementSink);
        }

        private void popStatementSink() {
            statementSinks.pop();
        }
    }

    private record LocalInfo(
            String name,
            String javaType,
            boolean declared
    ) {
    }

    private record StackValue(
            GpuIrExpression expression,
            String javaType
    ) {
        private StackValue copy() {
            return new StackValue(expression, javaType);
        }
    }

    private record SwitchLayout(
            Map<Integer, List<GpuIrExpression>> caseLabelsByTargetIndex,
            Integer defaultTargetIndex,
            List<Integer> orderedTargetIndexes,
            Set<Integer> caseTargetIndexes
    ) {
    }
}
