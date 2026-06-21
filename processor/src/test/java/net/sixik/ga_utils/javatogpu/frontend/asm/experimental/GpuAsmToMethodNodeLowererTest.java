package net.sixik.ga_utils.javatogpu.frontend.asm.experimental;

import net.sixik.ga_utils.javatogpu.frontend.GpuProgramCompiler;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuAsmToMethodNodeLowererTest {

    private static final String DEMO_OWNER = "sample/ExperimentalDemo";
    private static final String GPU_OWNER = Type.getInternalName(net.sixik.ga_utils.javatogpu.api.GPU.class);

    @Test
    void lowersTinyAstIntoCanonicalAsmAndCompilesIt() {
        GpuAsmMethod method = new GpuAsmMethod(
                "kernel",
                Type.VOID_TYPE,
                List.of(
                        new GpuAsmParameter("input", Type.getType("[F")),
                        new GpuAsmParameter("output", Type.getType("[F"))
                ),
                List.of(
                        new GpuAsmStmt.LocalVar(
                                "id",
                                Type.INT_TYPE,
                                new GpuAsmExpr.StaticCall(
                                        GPU_OWNER,
                                        "get_global_id",
                                        "(I)I",
                                        Type.INT_TYPE,
                                        List.of(new GpuAsmExpr.IntLiteral(0))
                                )
                        ),
                        new GpuAsmStmt.LocalVar(
                                "value",
                                Type.FLOAT_TYPE,
                                new GpuAsmExpr.ArrayLoad("input", new GpuAsmExpr.LocalRef("id"), Type.FLOAT_TYPE)
                        ),
                        new GpuAsmStmt.WhileLoop(
                                new GpuAsmCondition.Comparison("<", new GpuAsmExpr.LocalRef("id"), new GpuAsmExpr.IntLiteral(4)),
                                List.of(
                                        new GpuAsmStmt.SwitchStmt(
                                                new GpuAsmExpr.LocalRef("id"),
                                                List.of(
                                                        new GpuAsmSwitchCase(
                                                                new int[]{0},
                                                                List.of(
                                                                        new GpuAsmStmt.ArrayStore(
                                                                                "output",
                                                                                new GpuAsmExpr.LocalRef("id"),
                                                                                new GpuAsmExpr.FloatLiteral(7.0f),
                                                                                Type.FLOAT_TYPE
                                                                        ),
                                                                        new GpuAsmStmt.BreakLoop()
                                                                ),
                                                                false
                                                        )
                                                ),
                                                List.of(
                                                        new GpuAsmStmt.ArrayStore(
                                                                "output",
                                                                new GpuAsmExpr.LocalRef("id"),
                                                                new GpuAsmExpr.Binary(
                                                                        "+",
                                                                        new GpuAsmExpr.LocalRef("value"),
                                                                        new GpuAsmExpr.FloatLiteral(2.0f),
                                                                        Type.FLOAT_TYPE
                                                                ),
                                                                Type.FLOAT_TYPE
                                                        ),
                                                        new GpuAsmStmt.BreakSwitch()
                                                )
                                        ),
                                        new GpuAsmStmt.AssignLocal(
                                                "id",
                                                new GpuAsmExpr.Binary(
                                                        "+",
                                                        new GpuAsmExpr.LocalRef("id"),
                                                        new GpuAsmExpr.IntLiteral(1),
                                                        Type.INT_TYPE
                                                )
                                        )
                                )
                        ),
                        new GpuAsmStmt.ReturnVoid()
                )
        );

        GpuAsmToMethodNodeLowerer lowerer = new GpuAsmToMethodNodeLowerer();
        GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();
        String kernel = compiler.compileStructuredAsm(
                new AsmGpuMethod(
                        DEMO_OWNER,
                        parsedMethod("ExperimentalDemo", "sample.ExperimentalDemo", "kernel", "void", List.of(
                                globalArrayParameter("input", "float[]"),
                                globalArrayParameter("output", "float[]")
                        )),
                        lowerer.lowerStaticMethod(method)
                ),
                List.of()
        );

        assertTrue(kernel.contains("int tmp2 = get_global_id(0);"));
        assertTrue(kernel.contains("while ((tmp2 < 4)) {"));
        assertTrue(kernel.contains("switch (tmp2) {"));
        assertTrue(kernel.contains("goto __jtg_loop_break_0;"));
    }

    @Test
    void lowersExtendedTinyAstWithDoWhileAndHelperReturn() {
        GpuAsmToMethodNodeLowerer lowerer = new GpuAsmToMethodNodeLowerer();
        GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();

        GpuAsmMethod helperMethod = new GpuAsmMethod(
                "scale",
                Type.DOUBLE_TYPE,
                List.of(new GpuAsmParameter("value", Type.DOUBLE_TYPE)),
                List.of(
                        new GpuAsmStmt.ReturnValue(
                                new GpuAsmExpr.Binary(
                                        "*",
                                        new GpuAsmExpr.LocalRef("value"),
                                        new GpuAsmExpr.DoubleLiteral(2.0d),
                                        Type.DOUBLE_TYPE
                                )
                        )
                )
        );

        GpuAsmMethod kernelMethod = new GpuAsmMethod(
                "kernel",
                Type.VOID_TYPE,
                List.of(
                        new GpuAsmParameter("input", Type.getType("[D")),
                        new GpuAsmParameter("output", Type.getType("[D"))
                ),
                List.of(
                        new GpuAsmStmt.LocalVar(
                                "id",
                                Type.INT_TYPE,
                                new GpuAsmExpr.StaticCall(
                                        GPU_OWNER,
                                        "get_global_id",
                                        "(I)I",
                                        Type.INT_TYPE,
                                        List.of(new GpuAsmExpr.IntLiteral(0))
                                )
                        ),
                        new GpuAsmStmt.LocalVar(
                                "count",
                                Type.LONG_TYPE,
                                new GpuAsmExpr.LongLiteral(0L)
                        ),
                        new GpuAsmStmt.LocalVar(
                                "value",
                                Type.DOUBLE_TYPE,
                                new GpuAsmExpr.ArrayLoad("input", new GpuAsmExpr.LocalRef("id"), Type.DOUBLE_TYPE)
                        ),
                        new GpuAsmStmt.DoWhileLoop(
                                List.of(
                                        new GpuAsmStmt.AssignLocal(
                                                "value",
                                                new GpuAsmExpr.StaticCall(
                                                        DEMO_OWNER,
                                                        "scale",
                                                        "(D)D",
                                                        Type.DOUBLE_TYPE,
                                                        List.of(
                                                                new GpuAsmExpr.Binary(
                                                                        "+",
                                                                        new GpuAsmExpr.LocalRef("value"),
                                                                        new GpuAsmExpr.Unary(
                                                                                "-",
                                                                                new GpuAsmExpr.Cast(Type.DOUBLE_TYPE, new GpuAsmExpr.LocalRef("count"))
                                                                        ),
                                                                        Type.DOUBLE_TYPE
                                                                )
                                                        )
                                                )
                                        ),
                                        new GpuAsmStmt.AssignLocal(
                                                "count",
                                                new GpuAsmExpr.Binary(
                                                        "+",
                                                        new GpuAsmExpr.LocalRef("count"),
                                                        new GpuAsmExpr.LongLiteral(1L),
                                                        Type.LONG_TYPE
                                                )
                                        ),
                                        new GpuAsmStmt.IfElse(
                                                new GpuAsmCondition.Comparison(
                                                        "==",
                                                        new GpuAsmExpr.LocalRef("count"),
                                                        new GpuAsmExpr.LongLiteral(1L)
                                                ),
                                                List.of(new GpuAsmStmt.ContinueLoop()),
                                                List.of()
                                        ),
                                        new GpuAsmStmt.IfElse(
                                                new GpuAsmCondition.Comparison(
                                                        ">",
                                                        new GpuAsmExpr.LocalRef("value"),
                                                        new GpuAsmExpr.DoubleLiteral(10.0d)
                                                ),
                                                List.of(
                                                        new GpuAsmStmt.AssignLocal(
                                                                "value",
                                                                new GpuAsmExpr.Binary(
                                                                        "/",
                                                                        new GpuAsmExpr.LocalRef("value"),
                                                                        new GpuAsmExpr.DoubleLiteral(2.0d),
                                                                        Type.DOUBLE_TYPE
                                                                )
                                                        )
                                                ),
                                                List.of()
                                        )
                                ),
                                new GpuAsmCondition.Comparison(
                                        "<",
                                        new GpuAsmExpr.LocalRef("count"),
                                        new GpuAsmExpr.LongLiteral(3L)
                                )
                        ),
                        new GpuAsmStmt.ArrayStore(
                                "output",
                                new GpuAsmExpr.LocalRef("id"),
                                new GpuAsmExpr.LocalRef("value"),
                                Type.DOUBLE_TYPE
                        ),
                        new GpuAsmStmt.ReturnVoid()
                )
        );

        String kernel = compiler.compileStructuredAsm(
                new AsmGpuMethod(
                        DEMO_OWNER,
                        parsedMethod("ExperimentalDemo", "sample.ExperimentalDemo", "kernel", "void", List.of(
                                globalArrayParameter("input", "double[]"),
                                globalArrayParameter("output", "double[]")
                        )),
                        lowerer.lowerStaticMethod(kernelMethod)
                ),
                List.of(
                        new AsmGpuMethod(
                                DEMO_OWNER,
                                parsedMethod("ExperimentalDemo", "sample.ExperimentalDemo", "scale", "double", List.of(
                                        parameter("value", "double")
                                )),
                                lowerer.lowerStaticMethod(helperMethod)
                        )
                )
        );

        assertTrue(kernel.contains("double jtg_fn_ExperimentalDemo_scale_double(double value);"));
        assertTrue(kernel.contains("do {"));
        assertTrue(kernel.contains("continue;"));
        assertTrue(kernel.contains("while ((tmp3 < 3L));"));
        assertTrue(kernel.contains("10.0"));
        assertTrue(kernel.contains("(-((double) tmp3))"));
    }

    private ParsedGpuMethod parsedMethod(
            String ownerSimpleName,
            String ownerQualifiedName,
            String name,
            String returnType,
            List<ParsedGpuParameter> parameters
    ) {
        return new ParsedGpuMethod(
                ownerSimpleName,
                ownerQualifiedName,
                name,
                returnType,
                parameters,
                List.of(),
                null,
                false,
                List.of(),
                "",
                false
        );
    }

    private ParsedGpuParameter globalArrayParameter(String name, String javaType) {
        return new ParsedGpuParameter(name, javaType, GpuAddressSpace.GLOBAL, false);
    }

    private ParsedGpuParameter parameter(String name, String javaType) {
        return new ParsedGpuParameter(name, javaType, GpuAddressSpace.PRIVATE, false);
    }
}
