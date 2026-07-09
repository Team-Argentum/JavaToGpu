package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionException;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrValidationRunnerTest {
    @Test
    void skipsProvidersWhenValidationIsOff() {
        CapturingProvider provider = new CapturingProvider();
        GpuIrValidationRunner runner = new GpuIrValidationRunner(List.of(provider), GpuIrValidationMode.OFF);

        runner.run(method("kernel"), List.of(method("helper")), List.of());

        assertTrue(provider.requests.isEmpty());
    }

    @Test
    void runsProvidersForHelpersThenKernelWithRequestedMode() {
        CapturingProvider provider = new CapturingProvider();
        GpuIrValidationRunner runner = new GpuIrValidationRunner(List.of(provider), GpuIrValidationMode.STRICT_OPTIMIZER);

        runner.run(method("kernel"), List.of(method("helper")), List.of());

        assertEquals(2, provider.requests.size());
        assertEquals("helper", provider.requests.get(0).method().irMethod().name());
        assertEquals("kernel", provider.requests.get(1).method().irMethod().name());
        assertEquals(GpuIrValidationMode.STRICT_OPTIMIZER, provider.requests.get(0).mode());
        assertEquals(GpuIrValidationMode.STRICT_OPTIMIZER, provider.requests.get(1).mode());
        assertTrue(provider.requests.get(1).entryPoint());
    }

    @Test
    void exposesDeterministicValidationExtensionMetadataWithoutReorderingExplicitProviders() {
        GpuIrValidationProvider later = namedProvider("validator:zeta", "2", 20);
        GpuIrValidationProvider earlier = namedProvider("validator:alpha", "1", 10);

        GpuIrValidationRunner runner = new GpuIrValidationRunner(
                List.of(later, earlier),
                GpuIrValidationMode.DIAGNOSTIC
        );

        assertSame(later, runner.validationProviders().get(0));
        assertSame(earlier, runner.validationProviders().get(1));
        assertEquals("validator:alpha", runner.extensionRegistry().descriptors().get(0).id());
        assertEquals(
                List.of(GpuExtensionCapability.IR_VALIDATION),
                runner.extensionRegistry().descriptors().get(0).capabilities()
        );
        assertEquals(GpuExtensionPhase.IR_VALIDATION, runner.extensionRegistry().descriptors().get(0).phase());
        assertEquals(GpuExtensionPermission.READ_ONLY, runner.extensionRegistry().descriptors().get(0).permission());
        assertEquals("2", runner.extensionArtifactFields().get("irValidationExtension.count"));
        assertEquals("validator:alpha", runner.extensionArtifactFields().get("irValidationExtension.0.id"));
        assertEquals("IR_VALIDATION", runner.extensionArtifactFields().get("irValidationExtension.0.phase"));
        assertEquals("READ_ONLY", runner.extensionArtifactFields().get("irValidationExtension.0.permission"));
    }

    @Test
    void rejectsDuplicateValidationExtensionIdsFailClosed() {
        GpuIrValidationProvider first = namedProvider("validator:duplicate", "1", 0);
        GpuIrValidationProvider second = namedProvider("validator:duplicate", "2", 1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new GpuIrValidationRunner(
                        List.of(first, second),
                        GpuIrValidationMode.DIAGNOSTIC
                )
        );

        assertTrue(exception.getMessage().contains("Duplicate GPU extension id 'validator:duplicate'"));
    }

    @Test
    void rejectsValidationProviderThatRequestsMutationPermission() {
        GpuIrValidationProvider provider = namedProvider(
                "validator:mutating",
                "1",
                0,
                GpuExtensionPermission.MUTATION_PROPOSAL
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new GpuIrValidationRunner(List.of(provider), GpuIrValidationMode.DIAGNOSTIC)
        );

        assertTrue(exception.getMessage().contains("permission MUTATION_PROPOSAL"));
        assertTrue(exception.getMessage().contains("maximum READ_ONLY"));
    }

    @Test
    void diagnosticValidationFailureIsReportedAndFollowingProviderContinues() {
        GpuIrValidationProvider failing = request -> {
            throw new IllegalStateException("validator exploded");
        };
        CapturingProvider following = new CapturingProvider();
        GpuIrValidationRunner runner = new GpuIrValidationRunner(
                List.of(failing, following),
                GpuIrValidationMode.DIAGNOSTIC
        );

        List<GpuExtensionExecutionReport> reports = runner.runWithReport(method("kernel"), List.of(), List.of());

        assertEquals(2, reports.size());
        assertEquals(GpuExtensionExecutionOutcome.FAILED_CONTINUED, reports.get(0).outcome());
        assertEquals(GpuExtensionExecutionOutcome.SUCCEEDED, reports.get(1).outcome());
        assertEquals(1, following.requests.size());
    }

    @Test
    void strictValidationFailureThrowsAndStopsFollowingProviders() {
        GpuIrValidationProvider failing = request -> {
            throw new IllegalStateException("strict validator exploded");
        };
        CapturingProvider following = new CapturingProvider();
        GpuIrValidationRunner runner = new GpuIrValidationRunner(
                List.of(failing, following),
                GpuIrValidationMode.STRICT_SAFETY
        );

        GpuExtensionExecutionException exception = assertThrows(
                GpuExtensionExecutionException.class,
                () -> runner.runWithReport(method("kernel"), List.of(), List.of())
        );

        assertEquals(GpuExtensionExecutionOutcome.FAILED_CLOSED, exception.report().outcome());
        assertTrue(following.requests.isEmpty());
    }

    @Test
    void runnerEnrichesLegacyProviderEntriesWithExtensionMetadataAndSourceAnchor() {
        List<GpuIrValidationReportEntry> entries = new ArrayList<>();
        GpuIrValidationProvider provider = new GpuIrValidationProvider() {
            @Override
            public void validate(GpuIrValidationRequest request) {
                request.reportEntry(new GpuIrValidationReportEntry(
                        "legacy-provider-alias",
                        request.method().parsedMethod().name(),
                        request.entryPoint(),
                        java.util.Map.of("result", "ok")
                ));
            }

            @Override
            public String extensionId() {
                return "test.validation-extension";
            }

            @Override
            public String extensionVersion() {
                return "2";
            }
        };
        GpuIrValidationRunner runner = new GpuIrValidationRunner(
                List.of(provider),
                GpuIrValidationMode.DIAGNOSTIC,
                GpuIrValidationDiagnosticPolicy.SUMMARY,
                ignored -> { },
                entries::add
        );

        runner.run(method("kernel"), List.of(), List.of());

        assertEquals(1, entries.size());
        GpuIrValidationReportEntry entry = entries.get(0);
        assertEquals("legacy-provider-alias", entry.provider());
        assertEquals("test.validation-extension", entry.extensionId());
        assertEquals("2", entry.extensionVersion());
        assertEquals("legacy-provider-alias.summary", entry.ruleId());
        assertEquals(GpuIrValidationSeverity.INFO, entry.severity());
        assertEquals("asm:test.Owner#kernel", entry.sourceAnchor());
        assertEquals("test.validation-extension", entry.artifactFields("entry").get("entry.extensionId"));
    }

    private GpuIrCompiledMethod method(String name) {
        return new GpuIrCompiledMethod(parsedMethod(name), new GpuIrMethod(name, List.of()), "jtg_" + name, List.of());
    }

    private ParsedGpuMethod parsedMethod(String name) {
        return new ParsedGpuMethod(
                "Owner",
                "test.Owner",
                name,
                "void",
                List.of(),
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
    }

    private static GpuIrValidationProvider namedProvider(String id, String version, int order) {
        return namedProvider(id, version, order, GpuExtensionPermission.READ_ONLY);
    }

    private static GpuIrValidationProvider namedProvider(
            String id,
            String version,
            int order,
            GpuExtensionPermission permission
    ) {
        return new GpuIrValidationProvider() {
            @Override
            public void validate(GpuIrValidationRequest request) {
            }

            @Override
            public String extensionId() {
                return id;
            }

            @Override
            public String extensionVersion() {
                return version;
            }

            @Override
            public int extensionOrder() {
                return order;
            }

            @Override
            public GpuExtensionPermission extensionPermission() {
                return permission;
            }
        };
    }

    private static final class CapturingProvider implements GpuIrValidationProvider {
        private final List<GpuIrValidationRequest> requests = new ArrayList<>();

        @Override
        public void validate(GpuIrValidationRequest request) {
            requests.add(request);
        }
    }
}
