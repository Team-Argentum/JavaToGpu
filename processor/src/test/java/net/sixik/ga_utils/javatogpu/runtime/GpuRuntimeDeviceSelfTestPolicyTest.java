package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeDeviceSelfTestPolicyTest {

    @Test
    void selectsConservativeWorkloadProfilesForIgpuAndCpuDevices() {
        GpuRuntimeDeviceSelfTestProfile discrete = GpuRuntimeDeviceSelfTestProfile.forDevice(device(
                "opencl-0",
                "Discrete GPU",
                "Vendor A",
                "driver-a",
                GpuDeviceClassTarget.DGPU,
                48
        ));
        GpuRuntimeDeviceSelfTestProfile integrated = GpuRuntimeDeviceSelfTestProfile.forDevice(device(
                "opencl-1",
                "Integrated GPU",
                "Vendor B",
                "driver-b",
                GpuDeviceClassTarget.IGPU,
                16
        ));
        GpuRuntimeDeviceSelfTestProfile cpu = GpuRuntimeDeviceSelfTestProfile.forDevice(device(
                "opencl-2",
                "CPU OpenCL",
                "Vendor C",
                "driver-c",
                GpuDeviceClassTarget.CPU,
                8
        ));

        assertEquals("dgpu-balanced-v1", discrete.profileId());
        assertEquals("igpu-unified-v1", integrated.profileId());
        assertEquals("cpu-opencl-conservative-v1", cpu.profileId());
        assertTrue(discrete.computeWorkItems() > integrated.computeWorkItems());
        assertTrue(integrated.computeWorkItems() > cpu.computeWorkItems());
        assertTrue(discrete.transferBytesOneWay() > integrated.transferBytesOneWay());
        assertTrue(integrated.transferBytesOneWay() > cpu.transferBytesOneWay());
        assertEquals("unified-memory-round-trip", integrated.transferModel());
        assertEquals(250_000, integrated.transferScoreCap());
        assertEquals(100_000, cpu.transferScoreCap());
        assertTrue(integrated.maxNoisePermille() > discrete.maxNoisePermille());
    }

    @Test
    void cacheIdentityIncludesDeviceClassAndUnifiedMemoryTopology() {
        GpuRuntimeDeviceProfile discrete = deviceWithUnifiedMemory(
                "opencl-0",
                "Shared Label",
                "Vendor",
                "driver",
                GpuDeviceClassTarget.DGPU,
                16,
                false
        );
        GpuRuntimeDeviceProfile integrated = deviceWithUnifiedMemory(
                "opencl-0",
                "Shared Label",
                "Vendor",
                "driver",
                GpuDeviceClassTarget.IGPU,
                16,
                true
        );
        GpuRuntimeDeviceProfile integratedWithoutUnifiedFlag = deviceWithUnifiedMemory(
                "opencl-0",
                "Shared Label",
                "Vendor",
                "driver",
                GpuDeviceClassTarget.IGPU,
                16,
                false
        );

        assertTrue(!GpuRuntimeDeviceSelfTestIdentity.profileFingerprint(discrete).equals(
                GpuRuntimeDeviceSelfTestIdentity.profileFingerprint(integrated)
        ));
        assertTrue(!GpuRuntimeDeviceSelfTestIdentity.profileFingerprint(integrated).equals(
                GpuRuntimeDeviceSelfTestIdentity.profileFingerprint(integratedWithoutUnifiedFlag)
        ));
    }

    @Test
    void summarizesBoundedSamplesWithTrimmedMedianAndNoiseGate() {
        GpuRuntimeDeviceSelfTestSampleSummary stable = GpuRuntimeDeviceSelfTestSampleSummary.summarize(
                List.of(100L, 102L, 101L, 5_000L, 99L),
                50
        );
        GpuRuntimeDeviceSelfTestSampleSummary noisy = GpuRuntimeDeviceSelfTestSampleSummary.summarize(
                List.of(100L, 200L, 300L),
                300
        );

        assertEquals(101L, stable.medianNanos());
        assertEquals(3, stable.acceptedSampleCount());
        assertEquals(2, stable.discardedSampleCount());
        assertEquals(19, stable.noisePermille());
        assertTrue(stable.stable());
        assertEquals(1_000, noisy.noisePermille());
        assertTrue(!noisy.stable());
    }

    @Test
    void preparesEachDeviceOnceAndReusesCachedEvidence() {
        GpuRuntimeDeviceSelfTestCache cache = new GpuRuntimeDeviceSelfTestCache();
        AtomicInteger runs = new AtomicInteger();
        GpuRuntimeDeviceSelfTestRunner runner = passingRunner("test.correctness", "1", runs);
        List<GpuRuntimeDeviceProfile> candidates = List.of(
                device("opencl-0", "RTX 5070", "NVIDIA", "driver-a", GpuDeviceClassTarget.DGPU, 48),
                device("opencl-1", "Arc A310", "Intel", "driver-b", GpuDeviceClassTarget.DGPU, 8)
        );

        List<GpuRuntimeDeviceSelfTestCacheEntry> first = GpuRuntimeDeviceSelfTests.prepare(
                GpuRuntimeDeviceSelfTestMode.AUTO,
                candidates,
                runner,
                cache
        );
        List<GpuRuntimeDeviceSelfTestCacheEntry> second = GpuRuntimeDeviceSelfTests.prepare(
                GpuRuntimeDeviceSelfTestMode.AUTO,
                candidates,
                runner,
                cache
        );

        assertEquals(2, runs.get());
        assertTrue(first.stream().noneMatch(GpuRuntimeDeviceSelfTestCacheEntry::cacheHit));
        assertTrue(second.stream().allMatch(GpuRuntimeDeviceSelfTestCacheEntry::cacheHit));
        assertEquals(2, cache.size());
    }

    @Test
    void concurrentRequestsExecuteOneSelfTestPerIdentity() throws Exception {
        GpuRuntimeDeviceSelfTestCache cache = new GpuRuntimeDeviceSelfTestCache();
        AtomicInteger runs = new AtomicInteger();
        GpuRuntimeDeviceProfile profile = device(
                "opencl-0",
                "RTX 5070",
                "NVIDIA",
                "driver-a",
                GpuDeviceClassTarget.DGPU,
                48
        );
        GpuRuntimeDeviceSelfTestRunner runner = passingRunner("test.concurrent", "1", runs);
        GpuRuntimeDeviceSelfTestIdentity identity = GpuRuntimeDeviceSelfTestIdentity.from(profile, runner);
        Callable<GpuRuntimeDeviceSelfTestCacheEntry> request = () -> cache.getOrRun(
                identity,
                () -> {
                    try {
                        Thread.sleep(20L);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("self-test interrupted", exception);
                    }
                    return passed(identity, runs);
                }
        );

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<GpuRuntimeDeviceSelfTestCacheEntry>> futures = executor.invokeAll(
                    java.util.Collections.nCopies(8, request)
            );
            List<GpuRuntimeDeviceSelfTestCacheEntry> entries = futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError("Concurrent self-test request failed", exception);
                        }
                    })
                    .toList();

            assertEquals(1, runs.get());
            assertEquals(1L, entries.stream().filter(entry -> !entry.cacheHit()).count());
            assertEquals(7L, entries.stream().filter(GpuRuntimeDeviceSelfTestCacheEntry::cacheHit).count());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test
    void cacheIdentityInvalidatesOnDriverRunnerAndCompilerChanges() {
        GpuRuntimeDeviceSelfTestCache cache = new GpuRuntimeDeviceSelfTestCache();
        AtomicInteger runs = new AtomicInteger();
        GpuRuntimeDeviceProfile firstDriver = device(
                "opencl-0",
                "RTX 5070",
                "NVIDIA",
                "driver-a",
                GpuDeviceClassTarget.DGPU,
                48
        );
        GpuRuntimeDeviceProfile secondDriver = device(
                "opencl-0",
                "RTX 5070",
                "NVIDIA",
                "driver-b",
                GpuDeviceClassTarget.DGPU,
                48
        );
        GpuRuntimeDeviceSelfTestRunner runnerV1 = passingRunner("test.correctness", "1", runs);
        GpuRuntimeDeviceSelfTestRunner runnerV2 = passingRunner("test.correctness", "2", runs);

        GpuRuntimeDeviceSelfTests.prepare(
                GpuRuntimeDeviceSelfTestMode.REQUIRED,
                List.of(firstDriver),
                runnerV1,
                cache
        );
        GpuRuntimeDeviceSelfTests.prepare(
                GpuRuntimeDeviceSelfTestMode.REQUIRED,
                List.of(secondDriver),
                runnerV1,
                cache
        );
        GpuRuntimeDeviceSelfTests.prepare(
                GpuRuntimeDeviceSelfTestMode.REQUIRED,
                List.of(firstDriver),
                runnerV2,
                cache
        );

        GpuRuntimeDeviceSelfTestIdentity compilerA = identity(firstDriver, "test.correctness", "3", "compiler-a");
        GpuRuntimeDeviceSelfTestIdentity compilerB = identity(firstDriver, "test.correctness", "3", "compiler-b");
        cache.getOrRun(compilerA, () -> passed(compilerA, runs));
        cache.getOrRun(compilerB, () -> passed(compilerB, runs));

        assertEquals(5, runs.get());
        assertEquals(2, cache.size());
    }

    @Test
    void failedCorrectnessEvidenceRejectsOtherwiseStrongestDevice() {
        GpuRuntimeDeviceSelfTestCache cache = new GpuRuntimeDeviceSelfTestCache();
        GpuRuntimeDeviceProfile strong = device(
                "opencl-0",
                "RTX 5070",
                "NVIDIA",
                "driver-a",
                GpuDeviceClassTarget.DGPU,
                48
        );
        GpuRuntimeDeviceProfile fallback = device(
                "opencl-1",
                "Intel Arc A310",
                "Intel",
                "driver-b",
                GpuDeviceClassTarget.DGPU,
                8
        );
        GpuRuntimeDeviceSelfTestRunner runner = passingRunner(
                "test.correctness",
                "1",
                new AtomicInteger()
        );
        GpuRuntimeDeviceSelfTestIdentity strongIdentity = GpuRuntimeDeviceSelfTestIdentity.from(strong, runner);
        GpuRuntimeDeviceSelfTestIdentity fallbackIdentity = GpuRuntimeDeviceSelfTestIdentity.from(fallback, runner);
        cache.record(GpuRuntimeDeviceSelfTestResult.failed(
                strongIdentity,
                10L,
                List.of("readback mismatch")
        ));
        cache.record(GpuRuntimeDeviceSelfTestResult.passed(
                fallbackIdentity,
                20L,
                100_000,
                List.of("correctness smoke passed")
        ));

        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.of(List.of(
                new GpuRuntimeDeviceSelfTestPolicy(cache)
        )).select(context(
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                List.of(strong, fallback)
        ));

        assertEquals("Intel Arc A310", selection.selectedDevice().orElseThrow().deviceLabel());
        assertTrue(selection.rankedCandidates().stream()
                .filter(candidate -> candidate.profile().equals(strong))
                .allMatch(GpuRuntimeDeviceCandidateRanking::rejected));
        Map<String, String> fields = selection.artifactFields("device");
        assertTrue(fields.values().contains("failed"));
        assertTrue(fields.values().contains("test.correctness"));
    }

    @Test
    void stableRelativePerformanceCanReorderDevicesWithinTheSameClass() {
        GpuRuntimeDeviceSelfTestCache cache = new GpuRuntimeDeviceSelfTestCache();
        GpuRuntimeDeviceProfile slowerLarge = device(
                "opencl-0",
                "Large but slower dGPU",
                "Vendor A",
                "driver-a",
                GpuDeviceClassTarget.DGPU,
                48
        );
        GpuRuntimeDeviceProfile fasterSmall = device(
                "opencl-1",
                "Smaller but faster dGPU",
                "Vendor B",
                "driver-b",
                GpuDeviceClassTarget.DGPU,
                8
        );
        GpuRuntimeDeviceSelfTestRunner runner = passingRunner(
                "test.performance",
                "1",
                new AtomicInteger()
        );
        cache.record(GpuRuntimeDeviceSelfTestResult.passed(
                GpuRuntimeDeviceSelfTestIdentity.from(slowerLarge, runner),
                1_000L,
                100_000,
                performance(1_000L, 1_000L, true),
                List.of("stable slower evidence")
        ));
        cache.record(GpuRuntimeDeviceSelfTestResult.passed(
                GpuRuntimeDeviceSelfTestIdentity.from(fasterSmall, runner),
                1_000L,
                100_000,
                performance(100L, 100L, true),
                List.of("stable faster evidence")
        ));

        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.of(List.of(
                new GpuRuntimeDeviceSelfTestPolicy(cache)
        )).select(context(
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                List.of(slowerLarge, fasterSmall)
        ));

        assertEquals("Smaller but faster dGPU", selection.selectedDevice().orElseThrow().deviceLabel());
        GpuRuntimeDeviceCandidateRanking fasterRanking = selection.rankedCandidates().stream()
                .filter(candidate -> candidate.profile().equals(fasterSmall))
                .findFirst()
                .orElseThrow();
        GpuRuntimeDeviceCandidateRanking slowerRanking = selection.rankedCandidates().stream()
                .filter(candidate -> candidate.profile().equals(slowerLarge))
                .findFirst()
                .orElseThrow();
        assertTrue(fasterRanking.policyScoreAdjustment() > slowerRanking.policyScoreAdjustment());
        Map<String, String> facts = selection.policyDecisions().get(0).capabilityFacts();
        assertEquals(
                "3000000",
                facts.get(GpuRuntimeDevicePolicyContext.deviceKey(fasterSmall)
                        + ".selfTest.performance.computeScore")
        );
        assertEquals(
                "1000000",
                facts.get(GpuRuntimeDevicePolicyContext.deviceKey(fasterSmall)
                        + ".selfTest.performance.transferScore")
        );
    }

    @Test
    void noisyPerformanceRemainsTelemetryOnly() {
        GpuRuntimeDeviceSelfTestCache cache = new GpuRuntimeDeviceSelfTestCache();
        GpuRuntimeDeviceProfile stable = device(
                "opencl-0",
                "Stable dGPU",
                "Vendor A",
                "driver-a",
                GpuDeviceClassTarget.DGPU,
                48
        );
        GpuRuntimeDeviceProfile noisy = device(
                "opencl-1",
                "Noisy dGPU",
                "Vendor B",
                "driver-b",
                GpuDeviceClassTarget.DGPU,
                8
        );
        GpuRuntimeDeviceSelfTestRunner runner = passingRunner(
                "test.performance",
                "1",
                new AtomicInteger()
        );
        cache.record(GpuRuntimeDeviceSelfTestResult.passed(
                GpuRuntimeDeviceSelfTestIdentity.from(stable, runner),
                1_000L,
                100_000,
                performance(1_000L, 1_000L, true),
                List.of("stable evidence")
        ));
        cache.record(GpuRuntimeDeviceSelfTestResult.passed(
                GpuRuntimeDeviceSelfTestIdentity.from(noisy, runner),
                1_000L,
                100_000,
                performance(10L, 10L, false),
                List.of("noisy evidence")
        ));

        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.of(List.of(
                new GpuRuntimeDeviceSelfTestPolicy(cache)
        )).select(context(
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                List.of(stable, noisy)
        ));

        assertEquals("Stable dGPU", selection.selectedDevice().orElseThrow().deviceLabel());
        assertTrue(selection.diagnostics().stream().anyMatch(message -> message.contains("too noisy")));
    }

    @Test
    void stableComputeStillAffectsRankingWhenTransferEvidenceIsNoisy() {
        GpuRuntimeDeviceSelfTestCache cache = new GpuRuntimeDeviceSelfTestCache();
        GpuRuntimeDeviceProfile slower = device(
                "opencl-0",
                "Slower stable dGPU",
                "Vendor A",
                "driver-a",
                GpuDeviceClassTarget.DGPU,
                48
        );
        GpuRuntimeDeviceProfile faster = device(
                "opencl-1",
                "Faster dGPU with noisy transfer",
                "Vendor B",
                "driver-b",
                GpuDeviceClassTarget.DGPU,
                8
        );
        GpuRuntimeDeviceSelfTestRunner runner = passingRunner(
                "test.partial-stability",
                "1",
                new AtomicInteger()
        );
        cache.record(GpuRuntimeDeviceSelfTestResult.passed(
                GpuRuntimeDeviceSelfTestIdentity.from(slower, runner),
                1_000L,
                100_000,
                performance(1_000L, true, 1_000L, true),
                List.of("fully stable evidence")
        ));
        cache.record(GpuRuntimeDeviceSelfTestResult.passed(
                GpuRuntimeDeviceSelfTestIdentity.from(faster, runner),
                1_000L,
                100_000,
                performance(100L, true, 10L, false),
                List.of("compute-only stable evidence")
        ));

        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.of(List.of(
                new GpuRuntimeDeviceSelfTestPolicy(cache)
        )).select(context(
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                List.of(slower, faster)
        ));

        assertEquals("Faster dGPU with noisy transfer", selection.selectedDevice().orElseThrow().deviceLabel());
        Map<String, String> facts = selection.policyDecisions().get(0).capabilityFacts();
        String fasterKey = GpuRuntimeDevicePolicyContext.deviceKey(faster);
        assertEquals("3000000", facts.get(fasterKey + ".selfTest.performance.computeScore"));
        assertEquals("0", facts.get(fasterKey + ".selfTest.performance.transferScore"));
        assertTrue(selection.diagnostics().stream()
                .anyMatch(message -> message.contains(fasterKey + ": transfer performance evidence is too noisy")));
    }

    @Test
    void unifiedMemoryTransferScoresStayInsideTheIgpuProfile() {
        GpuRuntimeDeviceSelfTestCache cache = new GpuRuntimeDeviceSelfTestCache();
        GpuRuntimeDeviceProfile discrete = device(
                "opencl-0",
                "Discrete GPU",
                "Vendor A",
                "driver-a",
                GpuDeviceClassTarget.DGPU,
                32
        );
        GpuRuntimeDeviceProfile integrated = device(
                "opencl-1",
                "Integrated GPU",
                "Vendor B",
                "driver-b",
                GpuDeviceClassTarget.IGPU,
                16
        );
        GpuRuntimeDeviceSelfTestRunner runner = passingRunner(
                "test.profiled-performance",
                "1",
                new AtomicInteger()
        );
        GpuRuntimeDeviceSelfTestProfile discreteProfile = GpuRuntimeDeviceSelfTestProfile.forDevice(discrete);
        GpuRuntimeDeviceSelfTestProfile integratedProfile = GpuRuntimeDeviceSelfTestProfile.forDevice(integrated);
        cache.record(GpuRuntimeDeviceSelfTestResult.passed(
                GpuRuntimeDeviceSelfTestIdentity.from(discrete, runner),
                1_000L,
                100_000,
                performance(discreteProfile, false, 1_000L, 1_000L, true),
                List.of("discrete evidence")
        ));
        cache.record(GpuRuntimeDeviceSelfTestResult.passed(
                GpuRuntimeDeviceSelfTestIdentity.from(integrated, runner),
                1_000L,
                100_000,
                performance(integratedProfile, true, 100L, 10L, true),
                List.of("integrated evidence")
        ));

        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.of(List.of(
                new GpuRuntimeDeviceSelfTestPolicy(cache)
        )).select(context(
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                List.of(discrete, integrated)
        ));

        Map<String, String> facts = selection.policyDecisions().get(0).capabilityFacts();
        assertEquals("Discrete GPU", selection.selectedDevice().orElseThrow().deviceLabel());
        assertEquals(
                "1000000",
                facts.get(GpuRuntimeDevicePolicyContext.deviceKey(discrete)
                        + ".selfTest.performance.transferScore")
        );
        assertEquals(
                "250000",
                facts.get(GpuRuntimeDevicePolicyContext.deviceKey(integrated)
                        + ".selfTest.performance.transferScore")
        );
        assertEquals(
                "unified-memory-round-trip",
                facts.get(GpuRuntimeDevicePolicyContext.deviceKey(integrated)
                        + ".selfTest.performance.transferModel")
        );
    }

    @Test
    void disabledModeIgnoresCachedFailureWhileRequiredModeRejectsMissingEvidence() {
        GpuRuntimeDeviceSelfTestCache cache = new GpuRuntimeDeviceSelfTestCache();
        GpuRuntimeDeviceProfile strong = device(
                "opencl-0",
                "RTX 5070",
                "NVIDIA",
                "driver-a",
                GpuDeviceClassTarget.DGPU,
                48
        );
        GpuRuntimeDeviceSelfTestRunner runner = passingRunner(
                "test.correctness",
                "1",
                new AtomicInteger()
        );
        GpuRuntimeDeviceSelfTestIdentity identity = GpuRuntimeDeviceSelfTestIdentity.from(strong, runner);
        cache.record(GpuRuntimeDeviceSelfTestResult.failed(identity, 1L, List.of("forced failure")));
        GpuRuntimeDevicePolicyRegistry registry = GpuRuntimeDevicePolicyRegistry.of(List.of(
                new GpuRuntimeDeviceSelfTestPolicy(cache)
        ));

        GpuRuntimeDeviceSelection disabledSelection = registry.select(context(
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                        .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED),
                List.of(strong)
        ));
        GpuRuntimeDeviceProfile missing = device(
                "opencl-1",
                "Unverified GPU",
                "Vendor",
                "driver-x",
                GpuDeviceClassTarget.DGPU,
                16
        );
        GpuRuntimeDeviceSelection requiredSelection = registry.select(context(
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                        .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.REQUIRED),
                List.of(missing)
        ));

        assertEquals(strong, disabledSelection.selectedDevice().orElseThrow());
        assertTrue(requiredSelection.selectedDevice().isEmpty());
        assertEquals("compatible-device-missing", requiredSelection.firstBlocker());
    }

    @Test
    void autoModeSkipsSingleDeviceAndRunnerFailuresBecomeCachedFailedEvidence() {
        GpuRuntimeDeviceSelfTestCache cache = new GpuRuntimeDeviceSelfTestCache();
        GpuRuntimeDeviceProfile profile = device(
                "opencl-0",
                "RTX 5070",
                "NVIDIA",
                "driver-a",
                GpuDeviceClassTarget.DGPU,
                48
        );
        GpuRuntimeDeviceSelfTestRunner failingRunner = new GpuRuntimeDeviceSelfTestRunner() {
            @Override
            public GpuRuntimeDeviceSelfTestResult run(GpuRuntimeDeviceSelfTestRequest request) {
                throw new IllegalStateException("driver smoke failed");
            }

            @Override
            public String runnerId() {
                return "test.failing";
            }
        };

        assertTrue(GpuRuntimeDeviceSelfTests.prepare(
                GpuRuntimeDeviceSelfTestMode.AUTO,
                List.of(profile),
                failingRunner,
                cache
        ).isEmpty());
        List<GpuRuntimeDeviceSelfTestCacheEntry> required = GpuRuntimeDeviceSelfTests.prepare(
                GpuRuntimeDeviceSelfTestMode.REQUIRED,
                List.of(profile),
                failingRunner,
                cache
        );

        assertEquals(1, required.size());
        assertEquals(GpuRuntimeDeviceSelfTestOutcome.FAILED, required.get(0).result().outcome());
        assertTrue(required.get(0).result().rejectDevice());
        assertTrue(required.get(0).result().diagnostics().get(0).contains("driver smoke failed"));
    }

    @Test
    void rejectsUnknownSelfTestModeAtCompileOptionConstruction() {
        GpuBackendCompileOptions backendOptions = GpuBackendCompileOptions.openCl(
                List.of(),
                Map.of(GpuBackendCompileOptions.RUNTIME_DEVICE_SELF_TEST_PROPERTY, "sometimes")
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new GpuRuntimeCompileOptions(
                        GpuBackendTarget.OPENCL,
                        List.of(),
                        "off",
                        backendOptions
                )
        );

        assertTrue(exception.getMessage().contains("expected auto, disabled, or required"));
    }

    private static GpuRuntimeDeviceSelfTestRunner passingRunner(
            String id,
            String version,
            AtomicInteger runs
    ) {
        return new GpuRuntimeDeviceSelfTestRunner() {
            @Override
            public GpuRuntimeDeviceSelfTestResult run(GpuRuntimeDeviceSelfTestRequest request) {
                return passed(request.identity(), runs);
            }

            @Override
            public String runnerId() {
                return id;
            }

            @Override
            public String runnerVersion() {
                return version;
            }
        };
    }

    private static GpuRuntimeDeviceSelfTestResult passed(
            GpuRuntimeDeviceSelfTestIdentity identity,
            AtomicInteger runs
    ) {
        runs.incrementAndGet();
        return GpuRuntimeDeviceSelfTestResult.passed(
                identity,
                10L,
                100_000,
                List.of("passed")
        );
    }

    private static GpuRuntimeDeviceSelfTestIdentity identity(
            GpuRuntimeDeviceProfile profile,
            String runnerId,
            String runnerVersion,
            String compilerIdentity
    ) {
        return new GpuRuntimeDeviceSelfTestIdentity(
                GpuRuntimeDeviceSelfTestIdentity.profileFingerprint(profile),
                profile.backendTarget(),
                profile.deviceId(),
                profile.vendor(),
                profile.deviceLabel(),
                profile.driverVersion(),
                profile.apiVersionText(),
                runnerId,
                runnerVersion,
                compilerIdentity
        );
    }

    private static GpuRuntimeDeviceSelfTestPerformance performance(
            long computeMedianNanos,
            long transferMedianNanos,
            boolean stable
    ) {
        return performance(computeMedianNanos, stable, transferMedianNanos, stable);
    }

    private static GpuRuntimeDeviceSelfTestPerformance performance(
            long computeMedianNanos,
            boolean computeStable,
            long transferMedianNanos,
            boolean transferStable
    ) {
        return new GpuRuntimeDeviceSelfTestPerformance(
                1_000_000L,
                summary(computeMedianNanos, computeStable),
                8L * 1024L * 1024L,
                summary(transferMedianNanos, transferStable)
        );
    }

    private static GpuRuntimeDeviceSelfTestPerformance performance(
            GpuRuntimeDeviceSelfTestProfile profile,
            boolean unifiedMemory,
            long computeMedianNanos,
            long transferMedianNanos,
            boolean stable
    ) {
        return new GpuRuntimeDeviceSelfTestPerformance(
                profile.profileId(),
                profile.transferModel(),
                unifiedMemory,
                profile.computeScoreCap(),
                profile.transferScoreCap(),
                profile.computeOperations(4),
                summary(computeMedianNanos, stable),
                profile.transferBytesRoundTrip(),
                summary(transferMedianNanos, stable)
        );
    }

    private static GpuRuntimeDeviceSelfTestSampleSummary summary(long medianNanos, boolean stable) {
        int noisePermille = stable ? 10 : 1_000;
        return new GpuRuntimeDeviceSelfTestSampleSummary(
                medianNanos,
                Math.max(1L, medianNanos - 1L),
                medianNanos + 1L,
                5,
                3,
                2,
                noisePermille,
                stable
        );
    }

    private static GpuRuntimeDevicePolicyContext context(
            GpuRuntimeCompileOptions options,
            List<GpuRuntimeDeviceProfile> profiles
    ) {
        return GpuRuntimeDevicePolicyContext.forBackendDiscovery(options, profiles);
    }

    private static GpuRuntimeDeviceProfile device(
            String id,
            String label,
            String vendor,
            String driver,
            GpuDeviceClassTarget deviceClass,
            long computeUnits
    ) {
        return deviceWithUnifiedMemory(
                id,
                label,
                vendor,
                driver,
                deviceClass,
                computeUnits,
                deviceClass == GpuDeviceClassTarget.IGPU
        );
    }

    private static GpuRuntimeDeviceProfile deviceWithUnifiedMemory(
            String id,
            String label,
            String vendor,
            String driver,
            GpuDeviceClassTarget deviceClass,
            long computeUnits,
            boolean unifiedMemory
    ) {
        return GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                id,
                label,
                vendor,
                driver,
                "OpenCL 3.0 Test",
                deviceClass,
                computeUnits,
                8L * 1024L * 1024L * 1024L,
                64L * 1024L,
                1024L,
                1L,
                unifiedMemory,
                true,
                true,
                true
        );
    }
}
