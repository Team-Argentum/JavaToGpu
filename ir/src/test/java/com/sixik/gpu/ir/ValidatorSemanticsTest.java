package com.sixik.gpu.ir;

import org.junit.jupiter.api.Test;

import static com.sixik.gpu.ir.Fixtures.ASSUME_UNIFORM;
import static com.sixik.gpu.ir.Fixtures.ATOMIC_ADD;
import static com.sixik.gpu.ir.Fixtures.GET_GROUP_ID;
import static com.sixik.gpu.ir.Fixtures.GET_LOCAL_ID;
import static com.sixik.gpu.ir.Fixtures.NO_PARAMS;
import static com.sixik.gpu.ir.Fixtures.function;
import static com.sixik.gpu.ir.Fixtures.kernel;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Негативные тесты правил V, E, C, M, F, B и проверка вычисляемых метаданных. */
class ValidatorSemanticsTest {

    private static void expect(IrModule m, String code) {
        Validator.Report r = Validator.validate(m);
        assertTrue(r.has(code), () -> code + " was expected, got: " + r.diagnostics());
    }

    private static void expectNot(IrModule m, String code) {
        Validator.Report r = Validator.validate(m);
        assertFalse(r.has(code), () -> code + " was not expected, got: " + r.diagnostics());
    }

    // ---------------------------------------------------------------- IR-V ----------------------

    @Test
    void v01_useBeforeDeclaration() {
        expect(kernel(NO_PARAMS, b -> {
            int l = b.declareLocal(Types.I32);
            return new int[]{b.exprStmt(b.localGet(l))};
        }), "IR-V01");
    }

    @Test
    void v01_redeclarationInVisibleScope() {
        expect(kernel(NO_PARAMS, b -> {
            int l = b.declareLocal(Types.I32);
            return new int[]{b.localDecl(l, b.constI32(1)), b.localDecl(l, b.constI32(2))};
        }), "IR-V01");
    }

    @Test
    void v01_useAfterScopeEnded() {
        expect(kernel(NO_PARAMS, b -> {
            int l = b.declareLocal(Types.I32);
            int inner = b.block(b.localDecl(l, b.constI32(1)));
            return new int[]{inner, b.exprStmt(b.localGet(l))};
        }), "IR-V01");
    }

    @Test
    void v01_initializerCannotSeeItsOwnLocal() {
        expect(kernel(NO_PARAMS, b -> {
            int l = b.declareLocal(Types.I32);
            return new int[]{b.localDecl(l, b.localGet(l))};
        }), "IR-V01");
    }

    @Test
    void v02_declarationWithoutInitializer() {
        expect(kernel(NO_PARAMS, b -> {
            int l = b.declareLocal(Types.I32);
            return new int[]{b.node(Op.LOCAL_DECL, Types.VOID, l, -1, 0)};
        }), "IR-V02");
    }

    @Test
    void v03_addressOfArrayLocal() {
        expect(kernel(NO_PARAMS, b -> {
            int l = b.declareLocal(b.array(AddressSpace.PRIVATE, Types.F32));
            int decl = b.privateArrayDecl(l, b.constI32(4));
            return new int[]{decl, b.exprStmt(b.localAddr(l))};
        }), "IR-V03");
    }

    @Test
    void v04_arrayLocalDeclaredAsOrdinaryLocal() {
        expect(kernel(NO_PARAMS, b -> {
            int arr = b.array(AddressSpace.PRIVATE, Types.F32);
            int l = b.declareLocal(arr);
            return new int[]{b.node(Op.LOCAL_DECL, Types.VOID, l, b.constI32(0), 0)};
        }), "IR-V04");
    }

    // ---------------------------------------------------------------- IR-E ----------------------

    private static final Fixtures.Params I32_ARRAY = b -> new int[]{b.array(AddressSpace.GLOBAL, Types.I32)};

    @Test
    void e01_effectfulNodeNestedInExpression() {
        expect(kernel(I32_ARRAY, b -> {
            int at = b.intrinsic(ATOMIC_ADD, b.elemPtr(b.param(0), b.constI32(0)), b.constI32(1));
            return new int[]{b.exprStmt(b.binary(Op.ADD, at, b.constI32(2)))};
        }), "IR-E01");
    }

    @Test
    void e01_effectfulNodeAsStatementValueIsAllowed() {
        expectNot(kernel(I32_ARRAY, b -> {
            int at = b.intrinsic(ATOMIC_ADD, b.elemPtr(b.param(0), b.constI32(0)), b.constI32(1));
            return new int[]{b.exprStmt(at)};
        }), "IR-E01");
    }

    @Test
    void e01_effectfulCallNestedInExpression() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.DEVICE, Types.I32, arr);
        int store = b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constI32(1));
        b.endFunction(b.block(store, b.returnStmt(b.constI32(0))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int call = b.call(0, b.param(0));
        b.endFunction(b.block(b.exprStmt(b.binary(Op.ADD, call, b.constI32(1)))));
        expect(b.build(), "IR-E01");
    }

    @Test
    void e02_openNodeWithTwoParents() {
        expect(kernel(NO_PARAMS, b -> {
            int l = b.declareLocal(Types.I32);
            int decl = b.localDecl(l, b.constI32(1));
            int x = b.localGet(l);
            return new int[]{decl, b.exprStmt(b.binary(Op.ADD, x, x))};
        }), "IR-E02");
    }

    @Test
    void e02_closedNodeMayBeShared() {
        expectNot(kernel(NO_PARAMS, b -> {
            int one = b.constI32(1);
            return new int[]{b.exprStmt(b.binary(Op.ADD, one, one))};
        }), "IR-E02");
    }

    // ---------------------------------------------------------------- IR-C ----------------------

    @Test
    void c01_breakOutsideLoop() {
        expect(kernel(NO_PARAMS, b -> new int[]{b.breakStmt()}), "IR-C01");
    }

    @Test
    void c01_continueInsideSwitchOnly() {
        expect(kernel(NO_PARAMS, b -> {
            int body = b.block(b.continueStmt());
            return new int[]{b.switchStmt(b.constI32(0), new boolean[]{true}, new int[]{0}, new int[]{body})};
        }), "IR-C01");
    }

    @Test
    void c01_breakInsideSwitchIsAllowed() {
        expectNot(kernel(NO_PARAMS, b -> {
            int body = b.block(b.breakStmt());
            return new int[]{b.switchStmt(b.constI32(0), new boolean[]{true}, new int[]{0}, new int[]{body})};
        }), "IR-C01");
    }

    @Test
    void c02_returnValueInVoidFunction() {
        expect(kernel(NO_PARAMS, b -> new int[]{b.returnStmt(b.constI32(1))}), "IR-C02");
    }

    @Test
    void c02_kernelMustReturnVoid() {
        expect(function(IrModule.KERNEL, Types.I32, NO_PARAMS, b -> new int[]{b.returnStmt(b.constI32(1))}), "IR-C02");
    }

    @Test
    void c02_missingValueInNonVoidFunction() {
        expect(function(IrModule.DEVICE, Types.I32, NO_PARAMS, b -> new int[]{b.returnStmt(-1)}), "IR-C02");
    }

    @Test
    void c02_returnTypeMismatch() {
        expect(function(IrModule.DEVICE, Types.I32, NO_PARAMS, b -> new int[]{b.returnStmt(b.constF32(1.0f))}), "IR-C02");
    }

    @Test
    void c03_duplicateCaseValues() {
        expect(kernel(NO_PARAMS, b -> {
            int a = b.block(b.breakStmt());
            int c = b.block(b.breakStmt());
            return new int[]{b.switchStmt(b.constI32(0), new boolean[]{false, false}, new int[]{1, 1}, new int[]{a, c})};
        }), "IR-C03");
    }

    @Test
    void c03_twoDefaultBranches() {
        expect(kernel(NO_PARAMS, b -> {
            int a = b.block(b.breakStmt());
            int c = b.block(b.breakStmt());
            return new int[]{b.switchStmt(b.constI32(0), new boolean[]{true, true}, new int[]{0, 0}, new int[]{a, c})};
        }), "IR-C03");
    }

    @Test
    void c03_switchKeyMustBeInteger() {
        expect(kernel(NO_PARAMS, b -> {
            int a = b.block(b.breakStmt());
            return new int[]{b.switchStmt(b.constF32(1.0f), new boolean[]{true}, new int[]{0}, new int[]{a})};
        }), "IR-C03");
    }

    @Test
    void s08_syncBuiltinMustBeABarrierNode() {
        expect(kernel(NO_PARAMS, b -> new int[]{b.exprStmt(b.intrinsic(Builtins.idOf("barrier")))}), "IR-S08");
    }

    @Test
    void features_extendedInt64AtomicsNeedTheirOwnBit() {
        java.util.function.Function<String, Integer> features = name -> {
            IrModule m = kernel(b -> new int[]{b.ptr(AddressSpace.GLOBAL, Types.I64)},
                    b -> new int[]{b.exprStmt(b.intrinsic(Builtins.idOf(name), b.param(0), b.constI64(1)))});
            return Validator.validate(m).functions()[0].requiredFeatures();
        };
        assertEquals(Features.INT64_ATOMICS, (int) features.apply("atomicAdd"));
        assertEquals(Features.INT64_ATOMICS | Features.INT64_EXT_ATOMICS, (int) features.apply("atomicMax"));
    }

    // ---------------------------------------------------------------- IR-M ----------------------

    @Test
    void m01_storeIntoConstantMemory() {
        expect(kernel(b -> new int[]{b.ptr(AddressSpace.CONSTANT, Types.F32)},
                b -> new int[]{b.store(b.param(0), b.constF32(1.0f))}), "IR-M01");
    }

    @Test
    void m02_localArrayOutsideKernelTopLevel() {
        expect(kernel(NO_PARAMS, b -> {
            int l = b.declareLocal(b.array(AddressSpace.LOCAL, Types.F32));
            int decl = b.localArrayDecl(l, b.constI32(8));
            return new int[]{b.ifStmt(b.constBool(true), b.block(decl), -1)};
        }), "IR-M02");
    }

    @Test
    void m02_localArrayInDeviceFunction() {
        expect(function(IrModule.DEVICE, Types.VOID, NO_PARAMS, b -> {
            int l = b.declareLocal(b.array(AddressSpace.LOCAL, Types.F32));
            return new int[]{b.localArrayDecl(l, b.constI32(8))};
        }), "IR-M02");
    }

    @Test
    void m03_privateArrayLengthMustBeConstant() {
        expect(kernel(b -> new int[]{Types.I32}, b -> {
            int l = b.declareLocal(b.array(AddressSpace.PRIVATE, Types.F32));
            return new int[]{b.privateArrayDecl(l, b.param(0))};
        }), "IR-M03");
    }

    @Test
    void m03_privateArrayLengthMustBePositive() {
        expect(kernel(NO_PARAMS, b -> {
            int l = b.declareLocal(b.array(AddressSpace.PRIVATE, Types.F32));
            return new int[]{b.privateArrayDecl(l, b.constI32(0))};
        }), "IR-M03");
    }

    @Test
    void m04_kernelParameterInPrivateMemory() {
        expect(kernel(b -> new int[]{b.ptr(AddressSpace.PRIVATE, Types.F32)}, b -> new int[0]), "IR-M04");
    }

    @Test
    void m04_deviceFunctionMayTakePrivatePointer() {
        expectNot(function(IrModule.DEVICE, Types.VOID, b -> new int[]{b.ptr(AddressSpace.PRIVATE, Types.F32)},
                b -> new int[0]), "IR-M04");
    }

    @Test
    void m05_structWithArrayField() {
        expect(kernel(b -> {
            b.struct(new int[]{b.array(AddressSpace.GLOBAL, Types.F32)}, 0);
            return new int[0];
        }, b -> new int[0]), "IR-M05");
    }

    @Test
    void m06_elemPtrIndexMustBeI32() {
        expect(kernel(b -> new int[]{b.array(AddressSpace.GLOBAL, Types.F32)},
                b -> new int[]{b.exprStmt(b.elemPtr(b.param(0), b.constU32(0)))}), "IR-M06");
    }

    // ---------------------------------------------------------------- IR-F ----------------------

    @Test
    void f01_directRecursion() {
        expect(function(IrModule.DEVICE, Types.I32, NO_PARAMS,
                b -> new int[]{b.returnStmt(b.call(0))}), "IR-F01");
    }

    @Test
    void f01_mutualRecursion() {
        IrBuilder b = new IrBuilder();
        // f0 вызывает f1, которая ещё не определена: строим узлы вручную после объявления обеих
        b.beginFunction(IrModule.DEVICE, Types.I32);
        int callF1 = b.node(Op.CALL, Types.I32, 1, b.rawList(), 0);
        b.endFunction(b.block(b.returnStmt(callF1)));
        b.beginFunction(IrModule.DEVICE, Types.I32);
        b.endFunction(b.block(b.returnStmt(b.call(0))));
        expect(b.build(), "IR-F01");
    }

    @Test
    void f02_callWithWrongArgumentCount() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
        b.endFunction(b.block(b.returnStmt(b.param(0))));
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.endFunction(b.block(b.exprStmt(b.call(0))));
        expect(b.build(), "IR-F02");
    }

    @Test
    void f02_callWithWrongArgumentType() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
        b.endFunction(b.block(b.returnStmt(b.param(0))));
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.endFunction(b.block(b.exprStmt(b.call(0, b.constF32(1.0f)))));
        expect(b.build(), "IR-F02");
    }

    @Test
    void f03_workGroupSizeOnDeviceFunction() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.DEVICE, Types.VOID);
        b.requireWorkGroup(8, 8, 1);
        b.endFunction(b.block());
        expect(b.build(), "IR-F03");
    }

    @Test
    void f03_workGroupSizeMustBePositiveOnAllAxes() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.requireWorkGroup(0, 4, 4);
        b.endFunction(b.block());
        expect(b.build(), "IR-F03");
    }

    @Test
    void f04_unknownInlinePolicy() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.inlinePolicy(9);
        b.endFunction(b.block());
        expect(b.build(), "IR-F04");
    }

    @Test
    void f04_optLevelOutOfRange() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.optLevel(7);
        b.endFunction(b.block());
        expect(b.build(), "IR-F04");
    }

    @Test
    void f04_fallbackWithDifferentAbi() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID, Types.I32);
        b.endFunction(b.block());
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.fallback(0);
        b.endFunction(b.block());
        expect(b.build(), "IR-F04");
    }

    @Test
    void f04_fallbackWithSameAbiIsAllowed() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID, Types.I32);
        b.endFunction(b.block());
        b.beginFunction(IrModule.KERNEL, Types.VOID, Types.I32);
        b.fallback(0);
        b.endFunction(b.block());
        expectNot(b.build(), "IR-F04");
    }

    // ---------------------------------------------------------------- IR-B ----------------------

    private static IrModule barrierUnder(boolean useAssumeUniform, boolean useGroupId) {
        return kernel(NO_PARAMS, b -> {
            int id = b.declareLocal(Types.I32);
            int decl = b.localDecl(id, b.intrinsic(useGroupId ? GET_GROUP_ID : GET_LOCAL_ID, b.constI32(0)));
            int idv = b.localGet(id);
            if (useAssumeUniform) {
                idv = b.intrinsic(ASSUME_UNIFORM, idv);
            }
            int cond = b.binary(Op.LT, idv, b.constI32(4));
            return new int[]{decl, b.ifStmt(cond, b.block(b.barrier(1)), -1)};
        });
    }

    @Test
    void b01_barrierUnderDivergentConditionIsWarningByDefault() {
        Validator.Report r = Validator.validate(barrierUnder(false, false));
        assertTrue(r.has("IR-B01"));
        assertFalse(r.hasErrors(), "by default IR-B01 is a warning");
    }

    @Test
    void b01_becomesErrorInStrictMode() {
        Validator.Report r = Validator.validate(barrierUnder(false, false), new Validator.Options(true));
        assertTrue(r.has("IR-B01"));
        assertTrue(r.hasErrors());
    }

    @Test
    void b01_assumeUniformSuppresses() {
        expectNot(barrierUnder(true, false), "IR-B01");
    }

    @Test
    void b01_groupIdIsUniformInsideGroup() {
        expectNot(barrierUnder(false, true), "IR-B01");
    }

    @Test
    void b01_barrierAfterDivergentReturn() {
        expect(kernel(NO_PARAMS, b -> {
            int id = b.declareLocal(Types.I32);
            int decl = b.localDecl(id, b.intrinsic(GET_LOCAL_ID, b.constI32(0)));
            int cond = b.binary(Op.LT, b.localGet(id), b.constI32(4));
            return new int[]{decl, b.ifStmt(cond, b.block(b.returnStmt(-1)), -1), b.barrier(1)};
        }), "IR-B01");
    }

    @Test
    void b01_breakInDivergentIfDoesNotAffectCodeAfterLoop() {
        expectNot(kernel(NO_PARAMS, b -> {
            int id = b.declareLocal(Types.I32);
            int decl = b.localDecl(id, b.intrinsic(GET_LOCAL_ID, b.constI32(0)));
            int cond = b.binary(Op.LT, b.localGet(id), b.constI32(4));
            int body = b.block(b.ifStmt(cond, b.block(b.breakStmt()), -1));
            int loop = b.loop(b.constBool(true), body, -1);
            return new int[]{decl, loop, b.barrier(1)};
        }), "IR-B01");
    }

    @Test
    void b01_callToSyncFunctionUnderDivergentConditionIsReported() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.DEVICE, Types.VOID);
        b.endFunction(b.block(b.barrier(1)));
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        int id = b.declareLocal(Types.I32);
        int decl = b.localDecl(id, b.intrinsic(GET_LOCAL_ID, b.constI32(0)));
        int cond = b.binary(Op.LT, b.localGet(id), b.constI32(4));
        b.endFunction(b.block(decl, b.ifStmt(cond, b.block(b.exprStmt(b.call(0))), -1)));
        expect(b.build(), "IR-B01");
    }

    // ----------------------------------------------------- вычисляемые метаданные ---------------

    @Test
    void features_fp64FromParameterType() {
        IrModule m = kernel(b -> new int[]{b.array(AddressSpace.GLOBAL, Types.F64)}, b -> new int[0]);
        assertEquals(Features.FP64, Validator.validate(m).functions()[0].requiredFeatures());
    }

    @Test
    void features_fp64FromLocalOnly() {
        IrModule m = kernel(NO_PARAMS, b -> {
            int l = b.declareLocal(Types.F64);
            return new int[]{b.localDecl(l, b.constF64(1.0))};
        });
        assertTrue((Validator.validate(m).functions()[0].requiredFeatures() & Features.FP64) != 0);
    }

    @Test
    void features_fp64PropagatesFromCallee() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.DEVICE, Types.F64);
        b.endFunction(b.block(b.returnStmt(b.constF64(1.0))));
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.endFunction(b.block(b.exprStmt(b.call(0))));
        assertTrue((Validator.validate(b.build()).functions()[1].requiredFeatures() & Features.FP64) != 0);
    }

    @Test
    void features_int64Atomics() {
        IrModule m = kernel(b -> new int[]{b.array(AddressSpace.GLOBAL, Types.I64)}, b -> {
            int at = b.intrinsic(ATOMIC_ADD, b.elemPtr(b.param(0), b.constI32(0)), b.constI64(1));
            return new int[]{b.exprStmt(at)};
        });
        assertTrue((Validator.validate(m).functions()[0].requiredFeatures() & Features.INT64_ATOMICS) != 0);
    }

    @Test
    void groupSync_fromBarrier() {
        IrModule m = kernel(NO_PARAMS, b -> new int[]{b.barrier(1)});
        assertTrue(Validator.validate(m).functions()[0].usesGroupSync());
    }

    @Test
    void groupSync_fromLocalArray() {
        IrModule m = kernel(NO_PARAMS, b -> {
            int l = b.declareLocal(b.array(AddressSpace.LOCAL, Types.F32));
            return new int[]{b.localArrayDecl(l, b.constI32(8))};
        });
        assertTrue(Validator.validate(m).functions()[0].usesGroupSync());
    }

    @Test
    void groupSync_fromLocalPointerParameter() {
        IrModule m = kernel(b -> new int[]{b.ptr(AddressSpace.LOCAL, Types.F32)}, b -> new int[0]);
        assertTrue(Validator.validate(m).functions()[0].usesGroupSync());
    }

    @Test
    void groupSync_propagatesFromCallee() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.DEVICE, Types.VOID);
        b.endFunction(b.block(b.barrier(1)));
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.endFunction(b.block(b.exprStmt(b.call(0))));
        Validator.Report r = Validator.validate(b.build());
        assertTrue(r.functions()[1].usesGroupSync());
    }

    @Test
    void groupSync_absentInPlainKernel() {
        assertFalse(Validator.validate(Fixtures.computeSine()).functions()[0].usesGroupSync());
    }

    @Test
    void access_propagatesThroughCall() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.DEVICE, Types.VOID, arr);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constF32(1.0f))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.endFunction(b.block(b.exprStmt(b.call(0, b.param(0)))));
        Validator.Report r = Validator.validate(b.build());
        assertFalse(r.hasErrors(), r.diagnostics().toString());
        assertArrayEquals(new int[]{Validator.ACCESS_WRITE}, r.functions()[1].paramAccess());
    }

    @Test
    void access_pointerStoredInLocalMarksBaseParameterConservatively() {
        IrBuilder b = new IrBuilder();
        int pg = b.ptr(AddressSpace.GLOBAL, Types.F32);
        int pc = b.ptr(AddressSpace.CONSTANT, Types.F32);
        int g = b.array(AddressSpace.GLOBAL, Types.F32);
        int c = b.array(AddressSpace.CONSTANT, Types.F32);
        int other = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, g, c, other);
        int p = b.declareLocal(pg);
        int q = b.declareLocal(pc);
        b.endFunction(b.block(
                b.localDecl(p, b.elemPtr(b.param(0), b.constI32(4))),
                b.localDecl(q, b.elemPtr(b.param(1), b.constI32(1))),
                b.store(b.elemPtr(b.param(2), b.constI32(0)), b.constF32(1))));
        Validator.Report r = Validator.validate(b.build());
        assertFalse(r.hasErrors(), r.diagnostics().toString());
        assertArrayEquals(new int[]{Validator.ACCESS_READ | Validator.ACCESS_WRITE, Validator.ACCESS_READ,
                Validator.ACCESS_WRITE}, r.functions()[0].paramAccess(),
                "reads/writes through a local pointer are not visible, the mode cannot be understated; constant is read-only");
    }

    @Test
    void access_pointerReturnedFromFunctionMarksBaseParameter() {
        IrBuilder b = new IrBuilder();
        int pg = b.ptr(AddressSpace.GLOBAL, Types.F32);
        int g = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.DEVICE, pg, g);
        b.endFunction(b.block(b.returnStmt(b.elemPtr(b.param(0), b.constI32(2)))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, g);
        b.endFunction(b.block());
        Validator.Report r = Validator.validate(b.build());
        assertFalse(r.hasErrors(), r.diagnostics().toString());
        assertArrayEquals(new int[]{Validator.ACCESS_READ | Validator.ACCESS_WRITE}, r.functions()[0].paramAccess());
    }

    @Test
    void access_onlyReadParameter() {
        IrModule m = kernel(b -> new int[]{b.array(AddressSpace.GLOBAL, Types.F32), b.array(AddressSpace.GLOBAL, Types.F32)},
                b -> {
                    int load = b.load(b.elemPtr(b.param(0), b.constI32(0)));
                    return new int[]{b.store(b.elemPtr(b.param(1), b.constI32(0)), load)};
                });
        assertArrayEquals(new int[]{Validator.ACCESS_READ, Validator.ACCESS_WRITE},
                Validator.validate(m).functions()[0].paramAccess());
    }
}
