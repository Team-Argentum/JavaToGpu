package net.sixik.ga_utils.javatogpu.frontend.validation;

import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GpuSubsetValidatorTest {

    private final GpuMethodParser parser = new GpuMethodParser();
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
