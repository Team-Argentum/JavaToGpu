package net.sixik.ga_utils.javatogpu.frontend.validation;

import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.parser.GpuStructParser;
import net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GpuSubsetValidatorTest {

    private final GpuMethodParser parser = new GpuMethodParser();
    private final GpuStructParser structParser = new GpuStructParser();
    private final GpuSubsetValidator validator = new GpuSubsetValidator(GpuIntrinsicDatabase.createDefault());

    @Test
    void acceptsPrimitiveArraysForLoopsAndGpuIntrinsics() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    for (int i = 0; i < 16; i++) {
                        float value = input[i];
                        output[i] = GPU.sin(value) + GPU.cos(value);
                    }
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void rejectsUnsupportedIntrinsicUsage() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    output[0] = GPU.sin(true);
                }
                """;

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> validator.validate(parser.parseMethod(methodSource))
        );

        assertEquals(
                "Unknown GPU intrinsic overload: GPU.sin[boolean]; check the supported overloads for that intrinsic or cast arguments to a supported GPU scalar/vector type",
                exception.getMessage()
        );
    }

    @Test
    void rejectsObjectAllocation() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    Object value = new Object();
                    output[0] = 1.0f;
                }
                """;

        assertThrows(GpuValidationException.class, () -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void rejectsExternalJavaCalls() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    output[0] = (float) Math.sin(1.0);
                }
                """;

        assertThrows(GpuValidationException.class, () -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void acceptsWhileDoWhileAndSwitchStatements() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int i = 0;
                    while (i < 4) {
                        if ((i % 2) == 0) {
                            i++;
                            continue;
                        }
                        output[i] = input[i];
                        i++;
                    }
                    do {
                        i--;
                    } while (i > 0);
                    switch (input[0] & 3) {
                        case 0:
                            output[0] = 1;
                            break;
                        case 1:
                        case 2:
                            output[0] = 2;
                            break;
                        default:
                            output[0] = 3;
                    }
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void acceptsRuleStyleSwitchEntries() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    switch (input[0] & 3) {
                        case 0 -> output[0] = 1;
                        case 1 -> {
                            output[0] = 2;
                        }
                        default -> output[0] = 3;
                    }
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void rejectsIfBranchesWithoutBraces() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    if (output[0] > 0)
                        output[0] = 1;
                }
                """;

        assertThrows(GpuValidationException.class, () -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void acceptsIfElseBranchesWithBraces() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    if (input[id] > 0.0f) {
                        output[id] = GPU.sin(input[id]);
                    } else {
                        output[id] = GPU.cos(input[id]);
                    }
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void acceptsLogicalOperatorsInIfConditions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    if ((input[id] > 0.0f && input[id] < 10.0f) || !(input[id] > 100.0f)) {
                        output[id] = GPU.sin(input[id]);
                    } else {
                        output[id] = GPU.cos(input[id]);
                    }
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void acceptsElseIfChains() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    if (input[id] > 10.0f) {
                        output[id] = GPU.sin(input[id]);
                    } else if (input[id] > 0.0f) {
                        output[id] = GPU.cos(input[id]);
                    } else {
                        output[id] = input[id];
                    }
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void acceptsTernaryExpressions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = input[id] > 0.0f ? GPU.sin(input[id]) : GPU.cos(input[id]);
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void acceptsDivisionModuloAndUnaryMinusExpressions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    int value = -input[id];
                    output[id] = (value / 2) % 3;
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void acceptsCompoundAssignmentsAndDecrementUpdates() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    int value = input[id];
                    value += 2;
                    value <<= 1;
                    output[id] = value;
                    for (int i = 3; i > 0; i--) {
                        output[id] += i;
                    }
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void acceptsPrimitiveScalarCasts() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int i = 1;
                    output[0] = GPU.sin((float) i);
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void acceptsDoubleMathIntrinsics() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal double[] input, @GPUGlobal double[] output) {
                    int id = GPU.get_global_id(0);
                    double value = GPU.sqrt(input[id]) + GPU.pow(input[id], 2.0);
                    output[id] = GPU.max(value, GPU.log(input[id]));
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void rejectsNonScalarCasts() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    output[0] = (float) input;
                }
                """;

        assertThrows(GpuValidationException.class, () -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void rejectsExpressionStatementsThatAreNotAssignmentsOrDeclarations() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    GPU.sin(output[0]);
                }
                """;

        assertThrows(GpuValidationException.class, () -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void rejectsExpressionStatementsWithQuickFixHint() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    GPU.sin(output[0]);
                }
                """;

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> validator.validate(parser.parseMethod(methodSource))
        );

        assertTrue(exception.getMessage().contains("Only void @CCode helper calls can be used as standalone statements in @GPU methods"));
        assertTrue(exception.getMessage().contains("assign the helper result to a variable"));
    }

    @Test
    void acceptsFloatLiteralsAndParenthesizedExpressions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    output[0] = GPU.sin((1.0f + 2.0f));
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void rejectsNonVoidGpuMethods() {
        String methodSource = """
                @GPU
                float kernel(@GPUGlobal float[] input) {
                    return input[0];
                }
                """;

        assertThrows(GpuValidationException.class, () -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void rejectsBooleanParametersBecauseRuntimeDoesNotMarshalThem() {
        String methodSource = """
                @GPU
                void kernel(boolean enabled, @GPUGlobal float[] output) {
                    output[0] = 1.0f;
                }
                """;

        assertThrows(GpuValidationException.class, () -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void rejectsGlobalScalarParameters() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int count, @GPUGlobal float[] output) {
                    output[0] = count;
                }
                """;

        assertThrows(GpuValidationException.class, () -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void rejectsArrayParametersWithoutGlobalAnnotation() {
        String methodSource = """
                @GPU
                void kernel(float[] input, @GPUGlobal float[] output) {
                    output[0] = input[0];
                }
                """;

        assertThrows(GpuValidationException.class, () -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void rejectsArrayParametersWithoutGlobalAnnotationWithQuickFixHint() {
        String methodSource = """
                @GPU
                void kernel(float[] input, @GPUGlobal float[] output) {
                    output[0] = input[0];
                }
                """;

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> validator.validate(parser.parseMethod(methodSource))
        );

        assertTrue(exception.getMessage().contains("Array parameters must be annotated with @GPUGlobal, @GPUConstant, or @GPULocal"));
        assertTrue(exception.getMessage().contains("@GPUGlobal float[] input"));
        assertTrue(exception.getMessage().contains("move buffer-backed data out of private locals"));
    }

    @Test
    void acceptsConstantAndLocalArrayParameters() {
        String methodSource = """
                @GPU
                void kernel(@GPUConstant float[] lookup, @GPULocal float[] scratch, @GPUGlobal float[] output) {
                    int lid = GPU.get_local_id(0);
                    scratch[lid] = lookup[lid] * 2.0f;
                    GPU.barrier(GPU.CLK_LOCAL_MEM_FENCE);
                    output[lid] = scratch[lid];
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void acceptsAddressSpaceAnnotationsOnHelperParameters() {
        String kernelSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    helper(output);
                }
                """;
        String helperSource = """
                @CCode
                void helper(@GPULocal float[] scratch) {
                }
                """;

        assertDoesNotThrow(() -> validator.validateKernel(
                parser.parseMethod(kernelSource),
                java.util.List.of(parser.parseMethod(helperSource))
        ));
    }

    @Test
    void acceptsVectorLocalsAndHelperReturns() {
        String kernelSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    Float2 base = new Float2(input[id], input[id] * 2.0f);
                    Float2 bias = new Float2(1.0f);
                    Float2 shifted = GpuUtils.add(base, bias);
                    output[id] = shifted.x + shifted.y;
                }
                """;
        String helperSource = """
                @CCode
                Float2 add(Float2 left, Float2 right) {
                    return new Float2(left.x + right.x, left.y + right.y);
                }
                """;

        assertDoesNotThrow(() -> validator.validateKernel(
                parser.parseMethod(kernelSource, "Demo", "sample.Demo"),
                java.util.List.of(parser.parseMethod(helperSource, "GpuUtils", "sample.GpuUtils")),
                java.util.List.of()
        ));
    }

    @Test
    void acceptsVectorKernelParameters() {
        String methodSource = """
                @GPU
                void kernel(Float2 value, @GPUGlobal float[] output) {
                    output[0] = value.x + value.y;
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void acceptsStructKernelParameters() {
        String structSource = """
                @GPUStruct
                class Sample {
                    float x;
                    float y;
                }
                """;
        String methodSource = """
                @GPU
                void kernel(Sample sample, @GPUGlobal float[] output) {
                    output[0] = sample.x + sample.y;
                }
                """;

        assertDoesNotThrow(() -> validator.validateKernel(
                parser.parseMethod(methodSource, "Demo", "sample.Demo"),
                java.util.List.of(),
                java.util.List.of(structParser.parseStruct(structSource, "Sample", "sample.Sample"))
        ));
    }

    @Test
    void acceptsStructArrayKernelParameters() {
        String structSource = """
                @GPUStruct
                class Sample {
                    float x;
                    float y;
                }
                """;
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal Sample[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = input[id].x + input[id].y;
                }
                """;

        assertDoesNotThrow(() -> validator.validateKernel(
                parser.parseMethod(methodSource, "Demo", "sample.Demo"),
                java.util.List.of(),
                java.util.List.of(structParser.parseStruct(structSource, "Sample", "sample.Sample"))
        ));
    }

    @Test
    void rejectsPackedOpenClAttributeOnStructField() {
        String structSource = """
                @GPUStruct
                class Sample {
                    @OpenCLAttributes({"packed"})
                    float x;
                }
                """;
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    output[0] = 1.0f;
                }
                """;

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> validator.validateKernel(
                        parser.parseMethod(methodSource, "Demo", "sample.Demo"),
                        java.util.List.of(),
                        java.util.List.of(structParser.parseStruct(structSource, "Sample", "sample.Sample"))
                )
        );

        assertEquals(
                "OpenCL attribute 'packed' is not valid on @GPUStruct fields",
                exception.getMessage()
        );
    }

    @Test
    void rejectsMalformedAlignedAttributeOnStructField() {
        String structSource = """
                @GPUStruct
                class Sample {
                    @OpenCLAttributes({"aligned(0)"})
                    float x;
                }
                """;
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    output[0] = 1.0f;
                }
                """;

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> validator.validateKernel(
                        parser.parseMethod(methodSource, "Demo", "sample.Demo"),
                        java.util.List.of(),
                        java.util.List.of(structParser.parseStruct(structSource, "Sample", "sample.Sample"))
                )
        );

        assertEquals(
                "@GPUStruct field aligned(...) requires a single positive integer",
                exception.getMessage()
        );
    }

    @Test
    void rejectsStructArrayFieldsWithKernelParameterHint() {
        String childSource = """
                @GPUStruct
                class Child {
                    float x;
                }
                """;
        String parentSource = """
                @GPUStruct
                class Parent {
                    Child[] values;
                }
                """;
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    output[0] = 1.0f;
                }
                """;

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> validator.validateKernel(
                        parser.parseMethod(methodSource, "Demo", "sample.Demo"),
                        java.util.List.of(),
                        java.util.List.of(
                                structParser.parseStruct(childSource, "Child", "sample.Child"),
                                structParser.parseStruct(parentSource, "Parent", "sample.Parent")
                        )
                )
        );

        assertTrue(exception.getMessage().contains("Unsupported @GPUStruct field type: Child[]"));
        assertTrue(exception.getMessage().contains("arrays are not supported inside @GPUStruct fields in the current OpenCL ABI"));
        assertTrue(exception.getMessage().contains("move the array to a kernel parameter or flatten it"));
    }

    @Test
    void rejectsStructConstructorArgumentCountMismatch() {
        String structSource = """
                @GPUStruct
                class Sample {
                    float x;
                    float y;
                }
                """;
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    Sample sample = new Sample(input[0]);
                    output[0] = sample.x;
                }
                """;

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> validator.validateKernel(
                        parser.parseMethod(methodSource, "Demo", "sample.Demo"),
                        java.util.List.of(),
                        java.util.List.of(structParser.parseStruct(structSource, "Sample", "sample.Sample"))
                )
        );

        assertEquals(
                "Struct constructor argument count mismatch in @GPU methods: expected 0 or 2 but got 1",
                exception.getMessage()
        );
    }

    @Test
    void rejectsStructConstructorArgumentTypeMismatch() {
        String structSource = """
                @GPUStruct
                class Sample {
                    float x;
                    float y;
                }
                """;
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    boolean enabled = true;
                    Sample sample = new Sample(enabled, 1.0f);
                    output[0] = sample.y;
                }
                """;

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> validator.validateKernel(
                        parser.parseMethod(methodSource, "Demo", "sample.Demo"),
                        java.util.List.of(),
                        java.util.List.of(structParser.parseStruct(structSource, "Sample", "sample.Sample"))
                )
        );

        assertEquals(
                "Struct constructor argument type mismatch: expected float but got boolean",
                exception.getMessage()
        );
    }

    @Test
    void rejectsKernelOnlyOpenClAttributeOnHelperMethod() {
        String kernelSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    helper(output);
                }
                """;
        String helperSource = """
                @OpenCLAttributes({"reqd_work_group_size(16, 1, 1)"})
                @CCode
                void helper(float[] output) {
                }
                """;

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> validator.validateKernel(
                        parser.parseMethod(kernelSource, "Demo", "sample.Demo"),
                        java.util.List.of(parser.parseMethod(helperSource, "Helpers", "sample.Helpers")),
                        java.util.List.of()
                )
        );

        assertEquals(
                "OpenCL attribute 'reqd_work_group_size' is not valid on @CCode helpers",
                exception.getMessage()
        );
    }

    @Test
    void acceptsAlwaysInlineOpenClAttributeOnHelperMethod() {
        String kernelSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = helper(input[id]);
                }
                """;
        String helperSource = """
                @OpenCLAttributes({"always_inline"})
                @CCode
                float helper(float value) {
                    return value * 2.0f;
                }
                """;

        assertDoesNotThrow(() -> validator.validateKernel(
                parser.parseMethod(kernelSource, "Demo", "sample.Demo"),
                java.util.List.of(parser.parseMethod(helperSource, "Helpers", "sample.Helpers")),
                java.util.List.of()
        ));
    }

    @Test
    void acceptsConstOpenClQualifierOnHelperPointerParameter() {
        String kernelSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    FloatPtr ptr = new FloatPtr(input[id]);
                    output[id] = Helpers.read(ptr);
                }
                """;
        String helperSource = """
                @CCode
                float read(@OpenCLQualifiers({"const"}) FloatPtr ptr) {
                    return ptr.value;
                }
                """;

        assertDoesNotThrow(() -> validator.validateKernel(
                parser.parseMethod(kernelSource, "Demo", "sample.Demo"),
                java.util.List.of(parser.parseMethod(helperSource, "Helpers", "sample.Helpers")),
                java.util.List.of()
        ));
    }

    @Test
    void rejectsStructOnlyOpenClAttributeOnKernelMethod() {
        String methodSource = """
                @OpenCLAttributes({"packed"})
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    output[0] = 1.0f;
                }
                """;

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> validator.validate(parser.parseMethod(methodSource))
        );

        assertEquals(
                "OpenCL attribute 'packed' is not valid on @GPU methods",
                exception.getMessage()
        );
    }

    @Test
    void rejectsMalformedRequiredWorkGroupSizeAttributeOnKernelMethod() {
        String methodSource = """
                @OpenCLAttributes({"reqd_work_group_size(16, 0, 1)"})
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    output[0] = 1.0f;
                }
                """;

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> validator.validate(parser.parseMethod(methodSource))
        );

        assertEquals(
                "reqd_work_group_size(...) requires exactly three positive integer arguments",
                exception.getMessage()
        );
    }

    @Test
    void rejectsDuplicateKernelMethodAttributes() {
        String methodSource = """
                @OpenCLAttributes({"vec_type_hint(float4)", "vec_type_hint(int4)"})
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    output[0] = 1.0f;
                }
                """;

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> validator.validate(parser.parseMethod(methodSource))
        );

        assertEquals(
                "Duplicate OpenCL attribute: vec_type_hint",
                exception.getMessage()
        );
    }

    @Test
    void acceptsVectorArrayKernelParameters() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal Float2[] input, @GPUGlobal Float2[] output) {
                    int id = GPU.get_global_id(0);
                    output[id].x = input[id].x + 1.0f;
                    output[id].y = input[id].y + 2.0f;
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void rejectsVectorConstructorArgumentCountMismatch() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    Float2 value = new Float2(1.0f, 2.0f, 3.0f);
                    output[0] = value.x;
                }
                """;

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> validator.validate(parser.parseMethod(methodSource))
        );

        assertEquals(
                "Vector constructor argument count mismatch in @GPU methods: expected 0, 1 or 2 but got 3",
                exception.getMessage()
        );
    }

    @Test
    void rejectsVectorConstructorScalarArgumentTypeMismatch() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    boolean enabled = true;
                    Float2 value = new Float2(enabled);
                    output[0] = value.x;
                }
                """;

        GpuValidationException exception = assertThrows(
                GpuValidationException.class,
                () -> validator.validate(parser.parseMethod(methodSource))
        );

        assertEquals(
                "Vector constructor scalar argument type mismatch: expected float but got boolean",
                exception.getMessage()
        );
    }

    @Test
    void acceptsBooleanLocalsAndLiterals() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    boolean enabled = input[0] > 0.0f;
                    if (enabled) {
                        output[0] = 1.0f;
                    } else {
                        boolean zero = false;
                        if (!zero) {
                            output[0] = 0.0f;
                        }
                    }
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void acceptsBitwiseIntegerOperators() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = ((~input[id]) << 1) ^ ((input[id] >> 1) | (input[id] & 7));
                }
                """;

        assertDoesNotThrow(() -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void rejectsUnsignedRightShiftOperator() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = input[id] >>> 1;
                }
                """;

        assertThrows(GpuValidationException.class, () -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void rejectsUnsignedRightShiftAssignmentOperator() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] output) {
                    int value = 4;
                    value >>>= 1;
                    output[0] = value;
                }
                """;

        assertThrows(GpuValidationException.class, () -> validator.validate(parser.parseMethod(methodSource)));
    }

    @Test
    void rejectsBitwiseOperatorsForFloatingPointExpressions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = input[id] << 1;
                }
                """;

        assertThrows(GpuValidationException.class, () -> validator.validate(parser.parseMethod(methodSource)));
    }
}
