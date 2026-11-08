package com.sixik.gpu.runtime.device;

import com.sixik.gpu.ir.Features;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link DevicePolicy} и {@link DeviceInfo}: выбор устройства и разбор возможностей. */
class DevicePolicyTest {

    private static final FakeDeviceApi.Spec NVIDIA = FakeDeviceApi.Spec.gpu().withName("GeForce RTX 5070")
            .withVendor("NVIDIA Corporation");
    private static final FakeDeviceApi.Spec AMD = FakeDeviceApi.Spec.gpu().withName("Radeon RX 9070")
            .withVendor("Advanced Micro Devices").withExtensions("cl_khr_fp64");
    private static final FakeDeviceApi.Spec CPU = FakeDeviceApi.Spec.cpu().withName("Intel i9").withVendor("Intel");

    private static List<DeviceInfo> devices(FakeDeviceApi.Spec... specs) {
        return DeviceInfo.enumerate(new FakeDeviceApi(specs));
    }

    // ---- DeviceInfo -----------------------------------------------------------------------------

    @Test
    void enumerateReadsEveryProperty() {
        List<DeviceInfo> all = devices(NVIDIA, CPU);
        assertEquals(2, all.size());
        DeviceInfo d = all.get(0);
        assertEquals(0, d.index());
        assertEquals("GeForce RTX 5070", d.name());
        assertEquals("NVIDIA Corporation", d.vendor());
        assertEquals("Fake Platform", d.platformName());
        assertTrue(d.isGpu());
        assertFalse(d.isCpu());
        assertEquals(8L << 30, d.globalMemory());
        assertEquals(1024, d.maxWorkGroupSize());
        assertEquals(8, d.computeUnits());
        assertEquals("1.0-fake", d.driverVersion());
        assertTrue(d.openClVersion().startsWith("OpenCL"));
        assertEquals(48 * 1024, d.localMemory());
        assertEquals(1, all.get(1).index());
        assertTrue(all.get(1).isCpu());
    }

    @Test
    void enumerateOfNothingIsEmpty() {
        assertTrue(devices().isEmpty());
    }

    @Test
    void featuresComeFromExtensions() {
        assertEquals(Features.FP64 | Features.INT64_ATOMICS,
                DeviceInfo.featuresOf("cl_khr_byte_addressable_store cl_khr_fp64 cl_khr_int64_base_atomics"));
        assertEquals(Features.FP16, DeviceInfo.featuresOf("cl_khr_fp16"));
        assertEquals(0, DeviceInfo.featuresOf(""));
        assertEquals(0, DeviceInfo.featuresOf("cl_khr_fp64_not_really"), "сравнение по целому слову");
    }

    @Test
    void supportsAndDescribe() {
        DeviceInfo d = devices(NVIDIA).get(0);
        assertTrue(d.supports(Features.FP64));
        assertTrue(d.supports(Features.FP64 | Features.INT64_ATOMICS));
        assertFalse(d.supports(Features.FP16));
        assertTrue(d.supports(0));
        assertEquals("GeForce RTX 5070 [NVIDIA Corporation, GPU, 8192 MiB]", d.describe());
        assertTrue(devices(CPU).get(0).describe().contains("CPU"));
    }

    // ---- DevicePolicy ---------------------------------------------------------------------------

    @Test
    void defaultPolicyPrefersGpuThenEnumerationOrder() {
        List<DeviceInfo> all = devices(CPU, AMD, NVIDIA);
        assertEquals("Radeon RX 9070", DevicePolicy.DEFAULT.select(all).orElseThrow().name());
        assertEquals(List.of("Radeon RX 9070", "GeForce RTX 5070", "Intel i9"),
                DevicePolicy.DEFAULT.candidates(all).stream().map(DeviceInfo::name).toList());
    }

    @Test
    void emptyListSelectsNothing() {
        assertEquals(Optional.empty(), DevicePolicy.DEFAULT.select(List.of()));
    }

    @Test
    void kindFilter() {
        List<DeviceInfo> all = devices(CPU, NVIDIA);
        assertEquals("Intel i9", DevicePolicy.DEFAULT.withKind(DevicePolicy.Kind.CPU).select(all).orElseThrow().name());
        assertEquals("GeForce RTX 5070", DevicePolicy.DEFAULT.withKind(DevicePolicy.Kind.GPU).select(all).orElseThrow().name());
        assertTrue(DevicePolicy.DEFAULT.withKind(DevicePolicy.Kind.CPU).select(devices(NVIDIA)).isEmpty());
    }

    @Test
    void vendorFilterMatchesVendorOrNameIgnoringCase() {
        List<DeviceInfo> all = devices(NVIDIA, AMD, CPU);
        assertEquals("Radeon RX 9070", DevicePolicy.DEFAULT.withVendor("advanced").select(all).orElseThrow().name());
        assertEquals("Intel i9", DevicePolicy.DEFAULT.withVendor("I9").select(all).orElseThrow().name());
        assertTrue(DevicePolicy.DEFAULT.withVendor("qualcomm").select(all).isEmpty());
    }

    @Test
    void memoryAndFeatureFilters() {
        List<DeviceInfo> all = devices(AMD, NVIDIA);
        assertEquals("GeForce RTX 5070", DevicePolicy.DEFAULT.withRequiredFeatures(Features.INT64_ATOMICS)
                .select(all).orElseThrow().name());
        assertTrue(DevicePolicy.DEFAULT.withMinGlobalMemory(16L << 30).select(all).isEmpty());
        assertEquals(2, DevicePolicy.DEFAULT.withMinGlobalMemory(8L << 30).candidates(all).size());
    }

    @Test
    void overrideByIndexNameAndNamedIndexIgnoresFilters() {
        List<DeviceInfo> all = devices(NVIDIA, AMD, CPU, FakeDeviceApi.Spec.gpu().withName("Second NVIDIA")
                .withVendor("NVIDIA Corporation"));
        DevicePolicy onlyCpu = DevicePolicy.DEFAULT.withKind(DevicePolicy.Kind.CPU);
        assertEquals("Radeon RX 9070", onlyCpu.withOverride("1").select(all).orElseThrow().name(), "номер среди всех");
        assertEquals("GeForce RTX 5070", onlyCpu.withOverride("nvidia").select(all).orElseThrow().name());
        assertEquals("Second NVIDIA", onlyCpu.withOverride("nvidia:1").select(all).orElseThrow().name());
        assertEquals("GeForce RTX 5070", onlyCpu.withOverride("nvidia:0").select(all).orElseThrow().name());
        assertEquals("Intel i9", onlyCpu.withOverride(" intel ").select(all).orElseThrow().name());
        assertEquals("GeForce RTX 5070", onlyCpu.withOverride(":0").select(all).orElseThrow().name(), "пустое имя - любое");
    }

    @Test
    void overrideThatPointsNowhereSelectsNothing() {
        List<DeviceInfo> all = devices(NVIDIA, CPU);
        assertTrue(DevicePolicy.DEFAULT.withOverride("7").select(all).isEmpty());
        assertTrue(DevicePolicy.DEFAULT.withOverride("nvidia:3").select(all).isEmpty());
        assertTrue(DevicePolicy.DEFAULT.withOverride("qualcomm").select(all).isEmpty());
    }

    @Test
    void withersDoNotMutate() {
        DevicePolicy p = DevicePolicy.DEFAULT.withKind(DevicePolicy.Kind.GPU).withVendor("a").withMinGlobalMemory(5)
                .withRequiredFeatures(Features.FP64).withOverride("x");
        assertEquals(new DevicePolicy(DevicePolicy.Kind.GPU, "a", 5, Features.FP64, "x"), p);
        assertEquals(DevicePolicy.Kind.ANY, DevicePolicy.DEFAULT.kind());
        assertThrows(IllegalArgumentException.class, () -> new DevicePolicy(null, null, 0, 0, null));
    }

    @Test
    void environmentOverrideComesFromSystemProperty() {
        String old = System.getProperty(DevicePolicy.PROPERTY);
        try {
            System.setProperty(DevicePolicy.PROPERTY, "amd");
            assertEquals("amd", DevicePolicy.DEFAULT.withEnvironment().override());
            System.setProperty(DevicePolicy.PROPERTY, "  ");
            // пустое свойство приравнивается к отсутствию; переменная окружения в тесте не задана
            if (System.getenv(DevicePolicy.ENVIRONMENT) == null) {
                assertEquals(null, DevicePolicy.DEFAULT.withEnvironment().override());
            }
        } finally {
            if (old == null) {
                System.clearProperty(DevicePolicy.PROPERTY);
            } else {
                System.setProperty(DevicePolicy.PROPERTY, old);
            }
        }
    }

    @Test
    void contextHonoursTheEnvironmentOverrideUnlessTold() {
        FakeDeviceApi fake = new FakeDeviceApi(NVIDIA, CPU);
        String old = System.getProperty(DevicePolicy.PROPERTY);
        try {
            System.setProperty(DevicePolicy.PROPERTY, "intel");
            try (var ctx = com.sixik.gpu.runtime.GpuContext.builder().api(fake).build()) {
                assertEquals("Intel i9", ctx.device().name());
            }
            try (var ctx = com.sixik.gpu.runtime.GpuContext.builder().api(fake).ignoreEnvironment().build()) {
                assertEquals("GeForce RTX 5070", ctx.device().name());
            }
        } finally {
            if (old == null) {
                System.clearProperty(DevicePolicy.PROPERTY);
            } else {
                System.setProperty(DevicePolicy.PROPERTY, old);
            }
        }
    }
}
