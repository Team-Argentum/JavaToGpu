package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Features;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Выбор варианта ядра по {@code required} (M12): при нехватке возможностей устройства берётся {@code fallback}-вариант,
 * без измерений. Устройства - фейки с заданными расширениями.
 */
class VariantSelectionTest {

    private static final String FP64 = "cl_khr_fp64";
    private static final String ATOMICS = "cl_khr_int64_base_atomics";

    private static FakeDeviceApi device(String extensions) {
        return new FakeDeviceApi(FakeDeviceApi.Spec.gpu().withExtensions(extensions));
    }

    private static GpuContext open(FakeDeviceApi fake, List<GpuEvent> events) {
        return GpuContext.builder().api(fake).ignoreEnvironment().listener(events::add).build();
    }

    /** Простое ядро {@code out[0] = v}: основа вариантов (одна и та же сигнатура - массив float). */
    private static int simpleKernel(IrBuilder b, float v, String... required) {
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        int f = b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        if (required.length > 0) {
            b.required(required);
        }
        return f;
    }

    /** Цепочка: f0 (без требований), f1 (INT64_ATOMICS, fallback f0), f2 (FP64, fallback f1). */
    private static IrModule chain() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constF32(0))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.required("INT64_ATOMICS");
        b.fallback(0);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constF32(1))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.required("FP64");
        b.fallback(1);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constF32(2))));
        IrModule m = b.build();
        assertFalse(Validator.validate(m).hasErrors(), Validator.validate(m).diagnostics().toString());
        return m;
    }

    private static String detail(List<GpuEvent> events) {
        return events.stream().filter(e -> e.kind() == GpuEvent.Kind.COMPILE).reduce((a, c) -> c).orElseThrow().detail();
    }

    @Test
    void deviceWithEverythingRunsTheMainKernel() {
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = open(device(FP64 + " " + ATOMICS), events)) {
            Result<CompiledKernel> r = ctx.compile(chain());
            assertTrue(r.isOk(), r.diagnostics().toString());
            assertEquals(2, r.value().function());
            assertEquals("built", detail(events));
        }
    }

    @Test
    void missingFp64SelectsTheNextVariantOfTheChain() {
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = open(device(ATOMICS), events)) {
            Result<CompiledKernel> r = ctx.compile(chain());
            assertEquals(1, r.value().function(), "f2 requires FP64, f1 only INT64_ATOMICS");
            assertTrue(detail(events).contains("variant f1 instead of f2"), detail(events));
            assertTrue(detail(events).contains("FP64"), detail(events));
        }
    }

    @Test
    void missingEverythingFallsBackToTheEndOfTheChain() {
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = open(device(""), events)) {
            Result<CompiledKernel> r = ctx.compile(chain());
            assertEquals(0, r.value().function());
            assertTrue(detail(events).contains("variant f0 instead of f2"), detail(events));
        }
    }

    @Test
    void theChoiceIsDeterministicAndSharesTheCacheWithTheVariantItself() {
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = open(device(""), events)) {
            IrModule m = chain();
            CompiledKernel viaMain = ctx.compile(m, 2, com.sixik.gpu.codegen.opencl.EmitOptions.RELEASE).value();
            CompiledKernel direct = ctx.compile(m, 0, com.sixik.gpu.codegen.opencl.EmitOptions.RELEASE).value();
            assertSame(viaMain, direct, "the main kernel request and the variant request are one cache entry");
            assertEquals(new CacheStats(1, 1, 1), ctx.cacheStats());
            assertTrue(detail(events).startsWith("cache-hit"), detail(events));
        }
    }

    @Test
    void declaredRequirementWithoutAFallbackIsAnError() {
        IrBuilder b = new IrBuilder();
        simpleKernel(b, 1f, "FP64");
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constF32(1))));
        try (GpuContext ctx = open(device(""), new ArrayList<>())) {
            Result<CompiledKernel> r = ctx.compile(b.build());
            assertFalse(r.isOk());
            assertEquals("RT-D02", r.diagnostics().get(0).code());
            assertTrue(r.diagnostics().get(0).message().contains("FP64"), r.diagnostics().toString());
            assertFalse(r.diagnostics().get(0).message().contains("fallback"), "there is no variant and we do not mention it");
        }
    }

    @Test
    void whenEvenTheFallbackIsUnsupportedTheErrorSaysSo() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.required("FP16");
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constF32(0))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.required("FP64");
        b.fallback(0);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constF32(1))));
        try (GpuContext ctx = open(device(""), new ArrayList<>())) {
            Result<CompiledKernel> r = ctx.compile(b.build());
            assertFalse(r.isOk());
            assertEquals("RT-D02", r.diagnostics().get(0).code());
            assertTrue(r.diagnostics().get(0).message().contains("fallback"), r.diagnostics().toString());
        }
    }

    @Test
    void featuresUsedByTheBodyCountEvenWithoutADeclaration() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constF32(0))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.fallback(0);
        int d = b.declareLocal(Types.F64); // double в теле: возможность FP64 вычисляет валидатор, required не объявлен
        b.endFunction(b.block(b.localDecl(d, b.constF64(1)),
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.convert(Types.F32, b.localGet(d)))));
        IrModule m = b.build();
        assertEquals(Features.FP64, Validator.validate(m).functions()[1].requiredFeatures());
        try (GpuContext withFp64 = open(device(FP64), new ArrayList<>());
             GpuContext without = open(device(""), new ArrayList<>())) {
            assertEquals(1, withFp64.compile(m).value().function());
            assertEquals(0, without.compile(m).value().function(), "a computed shortfall also leads to a variant");
        }
    }

    @Test
    void kernelWithoutFallbackIsUntouched() {
        IrBuilder b = new IrBuilder();
        simpleKernel(b, 1f);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constF32(1))));
        try (GpuContext ctx = open(device(""), new ArrayList<>())) {
            assertEquals(0, ctx.compile(b.build()).value().function());
        }
    }

    @Test
    void featureNamesMapToMasks() {
        assertEquals(Features.FP64, Features.fromName("FP64"));
        assertEquals(Features.INT64_EXT_ATOMICS, Features.fromName("INT64_EXT_ATOMICS"));
        assertEquals(0, Features.fromName("SIMD"), "an unknown name requires nothing");
    }
}
