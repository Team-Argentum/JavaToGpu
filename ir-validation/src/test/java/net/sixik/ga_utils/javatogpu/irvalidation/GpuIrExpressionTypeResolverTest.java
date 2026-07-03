package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrExpressionTypeResolverTest {
    private final GpuIrExpressionTypeResolver resolver = new GpuIrExpressionTypeResolver();

    @Test
    void resolvesDeclaredVariableAndNumericBinaryTypes() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "x", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("float", "y", new GpuIrLiteral("2.0f"))
        ));

        assertEquals("int", resolver.typeOf(method, new GpuIrVariableRef("x")).orElseThrow());
        assertEquals("float", resolver.typeOf(method, new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))).orElseThrow());
    }

    @Test
    void resolvesBooleanComparisonUnaryCastAndIntrinsicTypes() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "x", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("int", "y", new GpuIrLiteral("2"))
        ));

        assertEquals("boolean", resolver.typeOf(method, new GpuIrBinary("<", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))).orElseThrow());
        assertEquals("boolean", resolver.typeOf(method, new GpuIrUnary("!", new GpuIrBinary("==", new GpuIrVariableRef("x"), new GpuIrVariableRef("y")))).orElseThrow());
        assertEquals("double", resolver.typeOf(method, new GpuIrCast("double", new GpuIrVariableRef("x"))).orElseThrow());
        assertEquals("float", resolver.typeOf(method, new GpuIrIntrinsicCall(null, "sqrt", "sqrt($0)", "float", List.of(new GpuIrVariableRef("x")))).orElseThrow());
    }

    @Test
    void resolvesTernaryTypesAndRefusesUnknownVariables() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "x", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("long", "y", new GpuIrLiteral("2L"))
        ));

        assertEquals("long", resolver.typeOf(method, new GpuIrTernary(
                new GpuIrBinary("!=", new GpuIrVariableRef("x"), new GpuIrLiteral("0")),
                new GpuIrVariableRef("x"),
                new GpuIrVariableRef("y")
        )).orElseThrow());
        assertTrue(resolver.typeOf(method, new GpuIrVariableRef("missing")).isEmpty());
    }

    @Test
    void resolvesParameterTypesFromCompiledMethodMetadata() {
        GpuIrCompiledMethod method = compiledMethod(new GpuIrMethod("kernel", List.of()), List.of(
                new ParsedGpuParameter("x", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                new ParsedGpuParameter("y", "float", GpuAddressSpace.PRIVATE, false, List.of())
        ));

        assertEquals("float", resolver.typeOf(method, new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))).orElseThrow());
    }

    private GpuIrCompiledMethod compiledMethod(GpuIrMethod irMethod, List<ParsedGpuParameter> parameters) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                irMethod.name(),
                "void",
                parameters,
                List.of(),
                List.of(),
                null,
                false,
                List.of(),
                null,
                "",
                null,
                false
        );
        return new GpuIrCompiledMethod(parsedMethod, irMethod, "jtg_kernel", List.of());
    }
}
