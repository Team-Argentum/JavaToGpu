package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClIrTextBodyEmitterTest {

    @Test
    void emitsSimpleFlatOpenClStatements() {
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  var int id = get_global_id(0)
                  set output[id] = input[id]
                  return
                """);

        OpenClIrTextBodyEmissionResult emission = OpenClIrTextBodyEmitter.INSTANCE.emit(parseResult);

        assertTrue(emission.emitted());
        assertTrue(emission.blockers().isEmpty());
        assertEquals("""
                    int id = get_global_id(0);
                    output[id] = input[id];
                    return;
                """, emission.body());
    }

    @Test
    void emitsSimpleSwitchBlock() {
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  switch selector
                    case 0,1
                      set output[0] = 1
                      break
                    default
                      set output[0] = 0
                  return
                """);

        OpenClIrTextBodyEmissionResult emission = OpenClIrTextBodyEmitter.INSTANCE.emit(parseResult);

        assertTrue(emission.emitted());
        assertEquals("""
                    switch (selector) {
                        case 0:
                        case 1:
                            output[0] = 1;
                            break;
                        default:
                            output[0] = 0;
                    }
                    return;
                """, emission.body());
    }

    @Test
    void lowersOpaqueHelperExpressionToOpenClCall() {
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  set output[0] = helper(jtg_fn_square_float args=[input[0]])
                  return
                """);

        OpenClIrTextBodyEmissionResult emission = OpenClIrTextBodyEmitter.INSTANCE.emit(parseResult);

        assertTrue(emission.emitted());
        assertEquals("""
                    output[0] = jtg_fn_square_float(input[0]);
                    return;
                """, emission.body());
    }

    @Test
    void lowersNestedOpaqueHelperExpressionsRecursively() {
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  set output[0] = (scale * helper(jtg_fn_mix_double_double args=[helper(jtg_fn_wrap_double args=[x]), helper(jtg_fn_wrap_double args=[y])]))
                  return
                """);

        OpenClIrTextBodyEmissionResult emission = OpenClIrTextBodyEmitter.INSTANCE.emit(parseResult);

        assertTrue(emission.emitted());
        assertEquals("""
                    output[0] = (scale * jtg_fn_mix_double_double(jtg_fn_wrap_double(x), jtg_fn_wrap_double(y)));
                    return;
                """, emission.body());
    }

    @Test
    void lowersOpaqueCastExpressionsToOpenClCasts() {
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  var long whole = cast<long>(value)
                  set output[0] = cast<int>(helper(jtg_fn_floor_long args=[value]))
                  return cast<double>(helper(jtg_fn_floor_long args=[(value + 0.5)]))
                """);

        OpenClIrTextBodyEmissionResult emission = OpenClIrTextBodyEmitter.INSTANCE.emit(parseResult);

        assertTrue(emission.emitted());
        assertEquals("""
                    long whole = ((long) value);
                    output[0] = ((int) jtg_fn_floor_long(value));
                    return ((double) jtg_fn_floor_long((value + 0.5)));
                """, emission.body());
    }

    @Test
    void lowersOpaqueIntrinsicTemplatesRecursively() {
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse("""
                method jtg_kernel source=kernel
                helpers -
                body
                  var int id = intrinsic(get_global_id template="" args=[0])
                  var GlobalBytePtr root = intrinsic(global template="({0})" args=[blob])
                  var int sampler = (*intrinsic(asIntPtr recv=intrinsic(add recv=root template="(({this}) + ({0}))" args=[(view.samplerOffset + (id * 4))]) template="((__global int*) ({this}))" args=[]))
                  set output[id] = (sampler + density)
                """);

        OpenClIrTextBodyEmissionResult emission = OpenClIrTextBodyEmitter.INSTANCE.emit(parseResult);

        assertTrue(emission.emitted());
        assertEquals("""
                    int id = get_global_id(0);
                    __global char* root = (blob);
                    int sampler = (*((__global int*) (((root) + ((view.samplerOffset + (id * 4)))))));
                    output[id] = (sampler + density);
                """, emission.body());
    }

    @Test
    void emitsExpressionStatementsAsOpenClCalls() {
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  expr intrinsic(write_imagef template="write_imagef({0}, {1}, {2})" args=[outputImage, coords, init<float4>(1.0f, 0.5f, 0.25f, 1.0f)])
                  return
                """);

        OpenClIrTextBodyEmissionResult emission = OpenClIrTextBodyEmitter.INSTANCE.emit(parseResult);

        assertTrue(emission.emitted());
        assertTrue(emission.blockers().isEmpty());
        assertEquals("""
                    write_imagef(outputImage, coords, (float4)(1.0f, 0.5f, 0.25f, 1.0f));
                    return;
                """, emission.body());
    }

    @Test
    void emitsPrivateArrayDeclarations() {
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  private-array float scratch[4]
                  set scratch[0] = input[0]
                  set output[0] = scratch[0]
                  return
                """);

        OpenClIrTextBodyEmissionResult emission = OpenClIrTextBodyEmitter.INSTANCE.emit(parseResult);

        assertTrue(emission.emitted());
        assertTrue(emission.blockers().isEmpty());
        assertEquals("""
                    float scratch[4];
                    scratch[0] = input[0];
                    output[0] = scratch[0];
                    return;
                """, emission.body());
    }

    @Test
    void emitsSimpleIfElseBlock() {
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  if (value > 0)
                    set output[0] = value
                  else
                    set output[0] = 0
                  return
                """);

        OpenClIrTextBodyEmissionResult emission = OpenClIrTextBodyEmitter.INSTANCE.emit(parseResult);

        assertTrue(emission.emitted());
        assertEquals("""
                    if ((value > 0)) {
                        output[0] = value;
                    } else {
                        output[0] = 0;
                    }
                    return;
                """, emission.body());
    }

    @Test
    void emitsSimpleForLoopBlock() {
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  for init=(var int i = 0) cond=(i < 4) update=(set i = (i + 1))
                    set output[i] = input[i]
                  return
                """);

        OpenClIrTextBodyEmissionResult emission = OpenClIrTextBodyEmitter.INSTANCE.emit(parseResult);

        assertTrue(emission.emitted());
        assertEquals("""
                    for (int i = 0; (i < 4); i = (i + 1)) {
                        output[i] = input[i];
                    }
                    return;
                """, emission.body());
    }

    @Test
    void emitsSimpleWhileLoopBlock() {
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  while (i < count)
                    set i = (i + 1)
                  return
                """);

        OpenClIrTextBodyEmissionResult emission = OpenClIrTextBodyEmitter.INSTANCE.emit(parseResult);

        assertTrue(emission.emitted());
        assertEquals("""
                    while ((i < count)) {
                        i = (i + 1);
                    }
                    return;
                """, emission.body());
    }

    @Test
    void emitsSimpleDoWhileLoopBlock() {
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  do
                    set i = (i + 1)
                  while (i < count)
                  return
                """);

        OpenClIrTextBodyEmissionResult emission = OpenClIrTextBodyEmitter.INSTANCE.emit(parseResult);

        assertTrue(emission.emitted());
        assertEquals("""
                    do {
                        i = (i + 1);
                    } while ((i < count));
                    return;
                """, emission.body());
    }

    @Test
    void emitsBreakContinueAndLoopBreakStatements() {
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  while (i < count)
                    if (i == 4)
                      break
                    if (i == 2)
                      continue
                    loop-break
                  return
                """);

        OpenClIrTextBodyEmissionResult emission = OpenClIrTextBodyEmitter.INSTANCE.emit(parseResult);

        assertTrue(emission.emitted());
        assertEquals("""
                    while ((i < count)) {
                        if ((i == 4)) {
                            break;
                        }
                        if ((i == 2)) {
                            continue;
                        }
                        break;
                    }
                    return;
                """, emission.body());
    }
}
