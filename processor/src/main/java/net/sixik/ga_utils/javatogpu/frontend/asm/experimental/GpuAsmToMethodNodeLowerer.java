package net.sixik.ga_utils.javatogpu.frontend.asm.experimental;

import net.sixik.ga_utils.javatogpu.frontend.asm.GpuFriendlyAsmMethodBuilder;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Optional lowering helper from a tiny neutral AST into canonical GPU-friendly ASM.
 *
 * <p>This is intentionally small and conservative. It is aimed at generator authors and examples,
 * not at regular end users.</p>
 */
public final class GpuAsmToMethodNodeLowerer {

    public MethodNode lowerStaticMethod(GpuAsmMethod method) {
        Objects.requireNonNull(method, "method");
        String descriptor = Type.getMethodDescriptor(
                method.returnType(),
                method.parameters().stream().map(GpuAsmParameter::type).toArray(Type[]::new)
        );
        GpuFriendlyAsmMethodBuilder builder = GpuFriendlyAsmMethodBuilder.staticMethod(method.name(), descriptor);

        LoweringContext context = new LoweringContext(builder);
        for (int index = 0; index < method.parameters().size(); index++) {
            GpuAsmParameter parameter = method.parameters().get(index);
            context.localsByName.put(parameter.name(), new LocalBinding(builder.parameterSlot(index), parameter.type()));
        }

        lowerStatements(method.statements(), context);
        return builder.toMethodNode();
    }

    private void lowerStatements(List<GpuAsmStmt> statements, LoweringContext context) {
        for (GpuAsmStmt statement : statements) {
            lowerStatement(statement, context);
        }
    }

    private void lowerStatement(GpuAsmStmt statement, LoweringContext context) {
        switch (statement) {
            case GpuAsmStmt.LocalVar localVar -> {
                emitExpression(localVar.initializer(), context);
                int slot = context.builder.newTemp(localVar.type());
                context.localsByName.put(localVar.name(), new LocalBinding(slot, localVar.type()));
                context.builder.storeLocal(slot, localVar.type());
            }
            case GpuAsmStmt.AssignLocal assignLocal -> {
                LocalBinding binding = requireLocal(context, assignLocal.name());
                emitExpression(assignLocal.value(), context);
                context.builder.storeLocal(binding.slot(), binding.type());
            }
            case GpuAsmStmt.ArrayStore arrayStore -> {
                LocalBinding arrayBinding = requireLocal(context, arrayStore.arrayName());
                context.builder.loadLocal(arrayBinding.slot(), arrayBinding.type());
                emitExpression(arrayStore.index(), context);
                emitExpression(arrayStore.value(), context);
                context.builder.emitArrayStore(arrayStore.elementType());
            }
            case GpuAsmStmt.IfElse ifElse -> {
                if (ifElse.elseBranch().isEmpty()) {
                    context.builder.emitCanonicalIf(
                            builder -> emitCondition(ifElse.condition(), context),
                            falseJumpOpcode(ifElse.condition(), context),
                            builder -> lowerStatements(ifElse.thenBranch(), context)
                    );
                } else {
                    context.builder.emitCanonicalIfElse(
                            builder -> emitCondition(ifElse.condition(), context),
                            falseJumpOpcode(ifElse.condition(), context),
                            builder -> lowerStatements(ifElse.thenBranch(), context),
                            builder -> lowerStatements(ifElse.elseBranch(), context)
                    );
                }
            }
            case GpuAsmStmt.WhileLoop whileLoop -> context.builder.emitCanonicalWhileLoop(
                    builder -> emitCondition(whileLoop.condition(), context),
                    falseJumpOpcode(whileLoop.condition(), context),
                    builder -> lowerStatements(whileLoop.body(), context)
            );
            case GpuAsmStmt.DoWhileLoop doWhileLoop -> context.builder.emitCanonicalDoWhileLoop(
                    builder -> lowerStatements(doWhileLoop.body(), context),
                    builder -> emitCondition(doWhileLoop.condition(), context),
                    trueJumpOpcode(doWhileLoop.condition(), context)
            );
            case GpuAsmStmt.SwitchStmt switchStmt -> context.builder.emitCanonicalSwitch(
                    builder -> emitExpression(switchStmt.selector(), context),
                    switchStmt.cases().stream()
                            .map(switchCase -> GpuFriendlyAsmMethodBuilder.SwitchCase.of(
                                    switchCase.keys(),
                                    builder -> lowerStatements(switchCase.statements(), context),
                                    switchCase.fallThrough()
                            ))
                            .toList(),
                    builder -> lowerStatements(switchStmt.defaultStatements(), context)
            );
            case GpuAsmStmt.ExprStmt exprStmt -> emitExpression(exprStmt.expression(), context);
            case GpuAsmStmt.BreakSwitch ignored -> context.builder.emitBreakSwitch();
            case GpuAsmStmt.BreakLoop ignored -> context.builder.emitBreakLoop();
            case GpuAsmStmt.ContinueLoop ignored -> context.builder.emitContinueLoop();
            case GpuAsmStmt.ReturnValue returnValue -> {
                emitExpression(returnValue.value(), context);
                context.builder.emitReturn(expressionType(returnValue.value(), context));
            }
            case GpuAsmStmt.ReturnVoid ignored -> context.builder.emitVoidReturn();
        }
    }

    private void emitExpression(GpuAsmExpr expression, LoweringContext context) {
        switch (expression) {
            case GpuAsmExpr.IntLiteral literal -> context.builder.pushInt(literal.value());
            case GpuAsmExpr.LongLiteral literal -> context.builder.pushLong(literal.value());
            case GpuAsmExpr.FloatLiteral literal -> context.builder.pushFloat(literal.value());
            case GpuAsmExpr.DoubleLiteral literal -> context.builder.pushDouble(literal.value());
            case GpuAsmExpr.LocalRef localRef -> {
                LocalBinding binding = requireLocal(context, localRef.name());
                context.builder.loadLocal(binding.slot(), binding.type());
            }
            case GpuAsmExpr.ArrayLoad arrayLoad -> {
                LocalBinding arrayBinding = requireLocal(context, arrayLoad.arrayName());
                context.builder.loadLocal(arrayBinding.slot(), arrayBinding.type());
                emitExpression(arrayLoad.index(), context);
                context.builder.emitArrayLoad(arrayLoad.elementType());
            }
            case GpuAsmExpr.Binary binary -> {
                emitExpression(binary.left(), context);
                emitExpression(binary.right(), context);
                context.builder.emitInsn(binaryOpcode(binary.operator(), binary.resultType()));
            }
            case GpuAsmExpr.Unary unary -> emitUnary(unary, context);
            case GpuAsmExpr.Cast cast -> {
                emitExpression(cast.expression(), context);
                int opcode = castOpcode(expressionType(cast.expression(), context), cast.targetType());
                if (opcode != Opcodes.NOP) {
                    context.builder.emitInsn(opcode);
                }
            }
            case GpuAsmExpr.StaticCall staticCall -> {
                for (GpuAsmExpr argument : staticCall.arguments()) {
                    emitExpression(argument, context);
                }
                context.builder.emitStaticCall(staticCall.ownerInternalName(), staticCall.methodName(), staticCall.descriptor());
            }
        }
    }

    private void emitCondition(GpuAsmCondition condition, LoweringContext context) {
        switch (condition) {
            case GpuAsmCondition.Comparison comparison -> {
                emitExpression(comparison.left(), context);
                emitExpression(comparison.right(), context);
                Type comparisonType = comparisonType(comparison, context);
                if (comparisonType.getSort() == Type.LONG) {
                    context.builder.emitInsn(Opcodes.LCMP);
                } else if (comparisonType.getSort() == Type.FLOAT) {
                    context.builder.emitInsn(floatCompareOpcode(comparison.operator()));
                } else if (comparisonType.getSort() == Type.DOUBLE) {
                    context.builder.emitInsn(doubleCompareOpcode(comparison.operator()));
                }
            }
            case GpuAsmCondition.Truthy truthy -> {
                emitExpression(truthy.expression(), context);
                Type truthyType = expressionType(truthy.expression(), context);
                if (truthyType.getSort() == Type.LONG) {
                    context.builder.pushLong(0L);
                    context.builder.emitInsn(Opcodes.LCMP);
                } else if (truthyType.getSort() == Type.FLOAT) {
                    context.builder.pushFloat(0.0f);
                    context.builder.emitInsn(Opcodes.FCMPG);
                } else if (truthyType.getSort() == Type.DOUBLE) {
                    context.builder.pushDouble(0.0d);
                    context.builder.emitInsn(Opcodes.DCMPG);
                }
            }
        }
    }

    private int falseJumpOpcode(GpuAsmCondition condition, LoweringContext context) {
        return switch (condition) {
            case GpuAsmCondition.Truthy ignored -> Opcodes.IFEQ;
            case GpuAsmCondition.Comparison comparison -> comparisonFalseJumpOpcode(comparison.operator(), comparisonType(comparison, context));
        };
    }

    private int trueJumpOpcode(GpuAsmCondition condition, LoweringContext context) {
        return switch (condition) {
            case GpuAsmCondition.Truthy ignored -> Opcodes.IFNE;
            case GpuAsmCondition.Comparison comparison -> comparisonTrueJumpOpcode(comparison.operator(), comparisonType(comparison, context));
        };
    }

    private int binaryOpcode(String operator, Type resultType) {
        return switch (operator) {
            case "+" -> resultType.getOpcode(Opcodes.IADD);
            case "-" -> resultType.getOpcode(Opcodes.ISUB);
            case "*" -> resultType.getOpcode(Opcodes.IMUL);
            case "/" -> resultType.getOpcode(Opcodes.IDIV);
            case "%" -> resultType.getOpcode(Opcodes.IREM);
            case "&" -> resultType.getOpcode(Opcodes.IAND);
            case "|" -> resultType.getOpcode(Opcodes.IOR);
            case "^" -> resultType.getOpcode(Opcodes.IXOR);
            case "<<" -> resultType.getOpcode(Opcodes.ISHL);
            case ">>" -> resultType.getOpcode(Opcodes.ISHR);
            case ">>>" -> resultType.getOpcode(Opcodes.IUSHR);
            default -> throw new IllegalArgumentException("Unsupported binary operator: " + operator);
        };
    }

    private void emitUnary(GpuAsmExpr.Unary unary, LoweringContext context) {
        emitExpression(unary.expression(), context);
        Type operandType = expressionType(unary.expression(), context);
        switch (unary.operator()) {
            case "+" -> {
                return;
            }
            case "-" -> context.builder.emitInsn(operandType.getOpcode(Opcodes.INEG));
            case "~" -> emitBitwiseNot(operandType, context);
            default -> throw new IllegalArgumentException("Unsupported unary operator: " + unary.operator());
        }
    }

    private void emitBitwiseNot(Type operandType, LoweringContext context) {
        if (isIntLike(operandType)) {
            context.builder.pushInt(-1);
            context.builder.emitInsn(Opcodes.IXOR);
            return;
        }
        if (operandType.getSort() == Type.LONG) {
            context.builder.pushLong(-1L);
            context.builder.emitInsn(Opcodes.LXOR);
            return;
        }
        throw new IllegalArgumentException("Bitwise not is only supported for integral experimental GPU ASM values: " + operandType);
    }

    private int castOpcode(Type sourceType, Type targetType) {
        if (sourceType.equals(targetType)) {
            return Opcodes.NOP;
        }
        return switch (sourceType.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> switch (targetType.getSort()) {
                case Type.BOOLEAN, Type.INT -> Opcodes.NOP;
                case Type.LONG -> Opcodes.I2L;
                case Type.FLOAT -> Opcodes.I2F;
                case Type.DOUBLE -> Opcodes.I2D;
                case Type.BYTE -> Opcodes.I2B;
                case Type.CHAR -> Opcodes.I2C;
                case Type.SHORT -> Opcodes.I2S;
                default -> throw new IllegalArgumentException("Unsupported cast in experimental GPU ASM AST: " + sourceType + " -> " + targetType);
            };
            case Type.LONG -> switch (targetType.getSort()) {
                case Type.INT -> Opcodes.L2I;
                case Type.FLOAT -> Opcodes.L2F;
                case Type.DOUBLE -> Opcodes.L2D;
                default -> throw new IllegalArgumentException("Unsupported cast in experimental GPU ASM AST: " + sourceType + " -> " + targetType);
            };
            case Type.FLOAT -> switch (targetType.getSort()) {
                case Type.INT -> Opcodes.F2I;
                case Type.LONG -> Opcodes.F2L;
                case Type.DOUBLE -> Opcodes.F2D;
                default -> throw new IllegalArgumentException("Unsupported cast in experimental GPU ASM AST: " + sourceType + " -> " + targetType);
            };
            case Type.DOUBLE -> switch (targetType.getSort()) {
                case Type.INT -> Opcodes.D2I;
                case Type.LONG -> Opcodes.D2L;
                case Type.FLOAT -> Opcodes.D2F;
                default -> throw new IllegalArgumentException("Unsupported cast in experimental GPU ASM AST: " + sourceType + " -> " + targetType);
            };
            default -> throw new IllegalArgumentException("Unsupported cast in experimental GPU ASM AST: " + sourceType + " -> " + targetType);
        };
    }

    private int comparisonFalseJumpOpcode(String operator, Type comparisonType) {
        if (isIntLike(comparisonType)) {
            return switch (operator) {
                case "==" -> Opcodes.IF_ICMPNE;
                case "!=" -> Opcodes.IF_ICMPEQ;
                case "<" -> Opcodes.IF_ICMPGE;
                case "<=" -> Opcodes.IF_ICMPGT;
                case ">" -> Opcodes.IF_ICMPLE;
                case ">=" -> Opcodes.IF_ICMPLT;
                default -> throw new IllegalArgumentException("Unsupported comparison operator: " + operator);
            };
        }
        return switch (operator) {
            case "==" -> Opcodes.IFNE;
            case "!=" -> Opcodes.IFEQ;
            case "<" -> Opcodes.IFGE;
            case "<=" -> Opcodes.IFGT;
            case ">" -> Opcodes.IFLE;
            case ">=" -> Opcodes.IFLT;
            default -> throw new IllegalArgumentException("Unsupported comparison operator: " + operator);
        };
    }

    private int comparisonTrueJumpOpcode(String operator, Type comparisonType) {
        if (isIntLike(comparisonType)) {
            return switch (operator) {
                case "==" -> Opcodes.IF_ICMPEQ;
                case "!=" -> Opcodes.IF_ICMPNE;
                case "<" -> Opcodes.IF_ICMPLT;
                case "<=" -> Opcodes.IF_ICMPLE;
                case ">" -> Opcodes.IF_ICMPGT;
                case ">=" -> Opcodes.IF_ICMPGE;
                default -> throw new IllegalArgumentException("Unsupported comparison operator: " + operator);
            };
        }
        return switch (operator) {
            case "==" -> Opcodes.IFEQ;
            case "!=" -> Opcodes.IFNE;
            case "<" -> Opcodes.IFLT;
            case "<=" -> Opcodes.IFLE;
            case ">" -> Opcodes.IFGT;
            case ">=" -> Opcodes.IFGE;
            default -> throw new IllegalArgumentException("Unsupported comparison operator: " + operator);
        };
    }

    private int floatCompareOpcode(String operator) {
        return switch (operator) {
            case "<", "<=" -> Opcodes.FCMPG;
            case ">", ">=" -> Opcodes.FCMPL;
            case "==", "!=" -> Opcodes.FCMPG;
            default -> throw new IllegalArgumentException("Unsupported comparison operator: " + operator);
        };
    }

    private int doubleCompareOpcode(String operator) {
        return switch (operator) {
            case "<", "<=" -> Opcodes.DCMPG;
            case ">", ">=" -> Opcodes.DCMPL;
            case "==", "!=" -> Opcodes.DCMPG;
            default -> throw new IllegalArgumentException("Unsupported comparison operator: " + operator);
        };
    }

    private Type comparisonType(GpuAsmCondition.Comparison comparison, LoweringContext context) {
        return expressionType(comparison.left(), context);
    }

    private Type expressionType(GpuAsmExpr expression, LoweringContext context) {
        return switch (expression) {
            case GpuAsmExpr.IntLiteral ignored -> Type.INT_TYPE;
            case GpuAsmExpr.LongLiteral ignored -> Type.LONG_TYPE;
            case GpuAsmExpr.FloatLiteral ignored -> Type.FLOAT_TYPE;
            case GpuAsmExpr.DoubleLiteral ignored -> Type.DOUBLE_TYPE;
            case GpuAsmExpr.LocalRef localRef -> requireLocal(context, localRef.name()).type();
            case GpuAsmExpr.ArrayLoad arrayLoad -> arrayLoad.elementType();
            case GpuAsmExpr.Binary binary -> binary.resultType();
            case GpuAsmExpr.Unary unary -> expressionType(unary.expression(), context);
            case GpuAsmExpr.Cast cast -> cast.targetType();
            case GpuAsmExpr.StaticCall staticCall -> staticCall.returnType();
        };
    }

    private boolean isIntLike(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> true;
            default -> false;
        };
    }

    private LocalBinding requireLocal(LoweringContext context, String name) {
        LocalBinding binding = context.localsByName.get(name);
        if (binding == null) {
            throw new IllegalArgumentException("Unknown local in experimental GPU ASM AST: " + name);
        }
        return binding;
    }

    private record LocalBinding(int slot, Type type) {
    }

    private static final class LoweringContext {
        private final GpuFriendlyAsmMethodBuilder builder;
        private final Map<String, LocalBinding> localsByName;

        private LoweringContext(GpuFriendlyAsmMethodBuilder builder) {
            this(builder, new LinkedHashMap<>());
        }

        private LoweringContext(GpuFriendlyAsmMethodBuilder builder, Map<String, LocalBinding> localsByName) {
            this.builder = builder;
            this.localsByName = localsByName;
        }
    }
}
