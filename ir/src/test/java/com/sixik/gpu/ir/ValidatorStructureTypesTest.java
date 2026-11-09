package com.sixik.gpu.ir;

import org.junit.jupiter.api.Test;

import static com.sixik.gpu.ir.Fixtures.NO_PARAMS;
import static com.sixik.gpu.ir.Fixtures.SIN;
import static com.sixik.gpu.ir.Fixtures.kernel;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Негативные тесты правил {@code IR-S} (структура) и {@code IR-T} (типы): каждое правило ловит своё нарушение. */
class ValidatorStructureTypesTest {

    private static void expect(IrModule m, String code) {
        Validator.Report r = Validator.validate(m);
        assertTrue(r.has(code), () -> code + " was expected, got: " + r.diagnostics());
        assertTrue(r.hasErrors() || r.diagnostics().stream().anyMatch(d -> d.code().equals(code)));
    }

    // ---------------------------------------------------------------- IR-S ----------------------

    @Test
    void s01_unknownOpcode() {
        expect(kernel(NO_PARAMS, b -> new int[]{b.node(9999, Types.VOID, 0, 0, 0)}), "IR-S01");
    }

    @Test
    void s01_typeOutOfRange() {
        expect(kernel(NO_PARAMS, b -> new int[]{b.exprStmt(b.node(Op.CONST_INT, 999, 0, 0, 0))}), "IR-S01");
    }

    @Test
    void s01_reservedTypeIdIsNotUsable() {
        expect(kernel(NO_PARAMS, b -> new int[]{b.exprStmt(b.node(Op.CONST_INT, 13, 0, 0, 0))}), "IR-S01");
    }

    @Test
    void s01_paramIndexOutOfRange() {
        expect(kernel(NO_PARAMS, b -> new int[]{b.exprStmt(b.node(Op.PARAM, Types.I32, 5, 0, 0))}), "IR-S01");
    }

    @Test
    void s01_builtinMissingFromTable() {
        expect(kernel(NO_PARAMS, b -> new int[]{b.exprStmt(b.node(Op.INTRINSIC, Types.I32, 9999, b.rawList(), 0))}),
                "IR-S01");
    }

    @Test
    void s01_operandBeyondTable() {
        expect(kernel(NO_PARAMS, b -> new int[]{b.exprStmt(b.node(Op.NEG, Types.I32, 12345, 0, 0))}), "IR-S01");
    }

    @Test
    void s02_operandWithGreaterId() {
        IrModule m = kernel(NO_PARAMS, b -> {
            int bad = b.node(Op.ADD, Types.I32, 1, 1, 0);   // операнды: узел n1 - больше собственного id
            b.constI32(1);
            return new int[]{b.exprStmt(bad)};
        });
        expect(m, "IR-S02");
    }

    @Test
    void s02_selfReference() {
        IrModule m = kernel(NO_PARAMS, b -> new int[]{b.exprStmt(b.node(Op.NEG, Types.I32, 0, 0, 0))});
        expect(m, "IR-S02");
    }

    @Test
    void s03_unreachableNode() {
        IrModule m = kernel(NO_PARAMS, b -> {
            b.constI32(7);   // создан, но нигде не используется
            return new int[0];
        });
        expect(m, "IR-S03");
    }

    @Test
    void s03_nodeSharedBetweenFunctions() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        int shared = b.constI32(1);
        b.endFunction(b.block(b.exprStmt(shared)));
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.endFunction(b.block(b.exprStmt(shared)));
        expect(b.build(), "IR-S03");
    }

    @Test
    void s04_blockListOutsidePool() {
        expect(kernel(NO_PARAMS, b -> new int[]{b.node(Op.BLOCK, Types.VOID, 9999, 3, 0)}), "IR-S04");
    }

    @Test
    void s04_switchListOutsidePool() {
        IrModule m = kernel(NO_PARAMS, b -> {
            int key = b.constI32(0);
            return new int[]{b.node(Op.SWITCH, Types.VOID, key, 9999, 3)};
        });
        expect(m, "IR-S04");
    }

    @Test
    void s05_builtinsVersionMismatch() {
        expect(TestAccess.withBuiltinsVersion(Fixtures.computeSine(), 999), "IR-S05");
    }

    @Test
    void s07_bodyRootMustBeBlock() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        int stmt = b.exprStmt(b.constI32(1));
        b.endFunction(stmt);
        expect(b.build(), "IR-S07");
    }

    @Test
    void s07_conditionMustBeExpression() {
        IrModule m = kernel(NO_PARAMS, b -> new int[]{b.ifStmt(b.breakStmt(), b.block(), -1)});
        expect(m, "IR-S07");
    }

    @Test
    void s07_blockChildMustBeStatement() {
        IrModule m = kernel(NO_PARAMS, b -> new int[]{b.constI32(1)});
        expect(m, "IR-S07");
    }

    // ---------------------------------------------------------------- IR-T ----------------------

    @Test
    void t01_operandTypesDiffer() {
        expect(kernel(NO_PARAMS, b -> new int[]{
                b.exprStmt(b.binary(Op.ADD, b.constI32(1), b.constF32(1.0f)))}), "IR-T01");
    }

    @Test
    void t01_shiftCountMustMatchValueType() {
        expect(kernel(NO_PARAMS, b -> new int[]{
                b.exprStmt(b.binary(Op.SHL, b.constI64(1), b.constI32(1)))}), "IR-T01");
    }

    @Test
    void t02_arithmeticOnStorageType() {
        expect(kernel(NO_PARAMS, b -> new int[]{
                b.exprStmt(b.binary(Op.ADD, b.constInt(Types.I8, 1), b.constInt(Types.I8, 1)))}), "IR-T02");
    }

    @Test
    void t02_bitwiseOnFloat() {
        expect(kernel(NO_PARAMS, b -> new int[]{
                b.exprStmt(b.binary(Op.AND, b.constF32(1.0f), b.constF32(2.0f)))}), "IR-T02");
    }

    @Test
    void t02_orderingOnBool() {
        expect(kernel(NO_PARAMS, b -> new int[]{
                b.exprStmt(b.binary(Op.LT, b.constBool(true), b.constBool(false)))}), "IR-T02");
    }

    @Test
    void t03_vectorToScalarConvert() {
        expect(kernel(NO_PARAMS, b -> {
            int v = b.vec(Types.F32, 4);
            return new int[]{b.exprStmt(b.convert(Types.I32, b.splat(v, b.constF32(1.0f))))};
        }), "IR-T03");
    }

    @Test
    void t03_bitcastWithDifferentSizes() {
        expect(kernel(NO_PARAMS, b -> new int[]{b.exprStmt(b.bitcast(Types.I64, b.constI32(1)))}), "IR-T03");
    }

    @Test
    void t04_selectConditionNotBool() {
        expect(kernel(NO_PARAMS, b -> new int[]{
                b.exprStmt(b.select(b.constI32(1), b.constI32(2), b.constI32(3)))}), "IR-T04");
    }

    @Test
    void t04_selectBranchesDiffer() {
        expect(kernel(NO_PARAMS, b -> new int[]{
                b.exprStmt(b.select(b.constBool(true), b.constI32(2), b.constF32(3.0f)))}), "IR-T04");
    }

    @Test
    void t04_ifConditionNotBool() {
        expect(kernel(NO_PARAMS, b -> new int[]{b.ifStmt(b.constI32(1), b.block(), -1)}), "IR-T04");
    }

    @Test
    void t05_loadFromNonPointer() {
        expect(kernel(NO_PARAMS, b -> new int[]{b.exprStmt(b.node(Op.LOAD, Types.F32, b.constF32(1.0f), 0, 0))}),
                "IR-T05");
    }

    @Test
    void t05_storeValueTypeMismatch() {
        expect(kernel(p -> new int[]{p.array(AddressSpace.GLOBAL, Types.F32)}, b -> new int[]{
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constI32(1))}), "IR-T05");
    }

    @Test
    void t06_boolKernelParameter() {
        expect(kernel(p -> new int[]{Types.BOOL}, b -> new int[0]), "IR-T06");
    }

    @Test
    void t06_boolArrayElement() {
        expect(kernel(p -> {
            p.array(AddressSpace.GLOBAL, Types.BOOL);
            return new int[0];
        }, b -> new int[0]), "IR-T06");
    }

    @Test
    void t06_boolStructField() {
        expect(kernel(p -> {
            p.struct(new int[]{Types.I32, Types.BOOL}, 0);
            return new int[0];
        }, b -> new int[0]), "IR-T06");
    }

    @Test
    void t07_invalidLaneCount() {
        expect(kernel(p -> {
            p.rawType(Types.KIND_VEC, Types.F32, 5, 0, 0);
            return new int[0];
        }, b -> new int[0]), "IR-T07");
    }

    @Test
    void t07_extractLaneOutOfRange() {
        expect(kernel(NO_PARAMS, b -> {
            int v = b.vec(Types.F32, 2);
            return new int[]{b.exprStmt(b.extract(b.splat(v, b.constF32(1.0f)), 5))};
        }), "IR-T07");
    }

    @Test
    void t07_shuffleLaneOutOfRange() {
        expect(kernel(NO_PARAMS, b -> {
            int v = b.vec(Types.F32, 2);
            return new int[]{b.exprStmt(b.shuffle(b.splat(v, b.constF32(1.0f)), 0, 7))};
        }), "IR-T07");
    }

    @Test
    void t07_buildWithWrongComponentCount() {
        expect(kernel(NO_PARAMS, b -> {
            int v = b.vec(Types.F32, 4);
            return new int[]{b.exprStmt(b.build(v, b.constF32(1.0f), b.constF32(2.0f)))};
        }), "IR-T07");
    }

    @Test
    void t08_structBuildWithWrongFieldCount() {
        expect(kernel(NO_PARAMS, b -> {
            int s = b.struct(new int[]{Types.F32, Types.F32}, 0);
            return new int[]{b.exprStmt(b.structBuild(s, b.constF32(1.0f)))};
        }), "IR-T08");
    }

    @Test
    void t08_fieldIndexOutOfRange() {
        expect(kernel(NO_PARAMS, b -> {
            int s = b.struct(new int[]{Types.F32}, 0);
            int value = b.structBuild(s, b.constF32(1.0f));
            return new int[]{b.exprStmt(b.node(Op.FIELD_GET, Types.F32, value, 7, 0))};
        }), "IR-T08");
    }

    @Test
    void t08_structWithoutFields() {
        expect(kernel(p -> {
            p.struct(new int[0], 0);
            return new int[0];
        }, b -> new int[0]), "IR-T08");
    }

    @Test
    void t09_resultTypeDoesNotMatchOperands() {
        expect(kernel(NO_PARAMS, b -> new int[]{
                b.exprStmt(b.node(Op.ADD, Types.F32, b.constI32(1), b.constI32(2), 0))}), "IR-T09");
    }

    @Test
    void t09_statementWithNonVoidType() {
        expect(kernel(NO_PARAMS, b -> new int[]{b.node(Op.BREAK, Types.I32, 0, 0, 0)}), "IR-T09");
    }

    @Test
    void t09_voidUsedAsValue() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.DEVICE, Types.VOID);
        b.endFunction(b.block());
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        int call = b.call(0);
        int add = b.node(Op.ADD, Types.I32, call, call, 0);
        b.endFunction(b.block(b.exprStmt(add)));
        expect(b.build(), "IR-T09");
    }

    @Test
    void t10_builtinArgumentsDoNotMatchSignature() {
        expect(kernel(NO_PARAMS, b -> {
            int arg = b.constI32(1);
            return new int[]{b.exprStmt(b.node(Op.INTRINSIC, Types.F32, SIN, b.rawList(arg), 1))};
        }), "IR-T10");
    }
}
