package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeLifecycleEventBusTest {

    @Test
    void dispatchesListenersInExtensionOrder() {
        ArrayList<String> events = new ArrayList<>();
        GpuRuntimeLifecycleEventListener later = listener("test.lifecycle.zeta", 20, events);
        GpuRuntimeLifecycleEventListener earlier = listener("test.lifecycle.alpha", 10, events);
        GpuRuntimeLifecycleEvent event = new GpuRuntimeLifecycleEvent(
                GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_STARTED,
                GpuBackendTarget.OPENCL,
                "kernels/test.cl",
                "diagnostic",
                "backend compile started",
                Map.of("source", "irgpu")
        );

        GpuRuntimeLifecycleEventReport report = GpuRuntimeLifecycleEventBus.of(List.of(later, earlier))
                .publish(event);

        assertEquals(List.of("test.lifecycle.alpha:BACKEND_COMPILATION_STARTED", "test.lifecycle.zeta:BACKEND_COMPILATION_STARTED"), events);
        assertEquals(2, report.listenerReports().size());
        assertTrue(report.allListenersSucceeded());
        assertEquals("BACKEND_COMPILATION_STARTED", report.artifactFields("lifecycle").get("lifecycle.event.kind"));
        assertEquals("OPENCL", report.artifactFields("lifecycle").get("lifecycle.event.backendTarget"));
        assertEquals("source", report.artifactFields("lifecycle").get("lifecycle.event.field.0.key"));
        assertEquals("irgpu", report.artifactFields("lifecycle").get("lifecycle.event.field.0.value"));
    }

    @Test
    void listenerFailureIsIsolatedAndFollowingListenersStillRun() {
        AtomicBoolean followingInvoked = new AtomicBoolean();
        GpuRuntimeLifecycleEventListener failing = new OrderedLifecycleListener("test.lifecycle.failing", 0) {
            @Override
            public void onRuntimeLifecycleEvent(GpuRuntimeLifecycleEvent event) {
                throw new IllegalStateException("listener exploded");
            }
        };
        GpuRuntimeLifecycleEventListener following = new OrderedLifecycleListener("test.lifecycle.following", 10) {
            @Override
            public void onRuntimeLifecycleEvent(GpuRuntimeLifecycleEvent event) {
                followingInvoked.set(true);
            }
        };

        GpuRuntimeLifecycleEventReport report = GpuRuntimeLifecycleEventBus.of(List.of(failing, following))
                .publish(GpuRuntimeLifecycleEvent.of(GpuRuntimeLifecycleEventKind.INVOCATION_STARTED));

        assertTrue(followingInvoked.get());
        assertFalse(report.allListenersSucceeded());
        assertEquals(GpuExtensionExecutionOutcome.FAILED_CONTINUED, report.listenerReports().get(0).outcome());
        assertEquals(GpuExtensionExecutionOutcome.SUCCEEDED, report.listenerReports().get(1).outcome());
        assertTrue(report.listenerReports().get(0).pipelineContinued());
        assertTrue(report.toPropertiesText().contains("listener exploded"));
    }

    @Test
    void busRejectsWrongExtensionPhaseCapabilityOrPermission() {
        GpuRuntimeLifecycleEventListener wrongPhase = new OrderedLifecycleListener("test.lifecycle.wrong-phase", 0) {
            @Override
            public GpuExtensionPhase extensionPhase() {
                return GpuExtensionPhase.DIAGNOSTICS;
            }
        };
        GpuRuntimeLifecycleEventListener wrongCapability = new OrderedLifecycleListener("test.lifecycle.wrong-capability", 0) {
            @Override
            public Set<GpuExtensionCapability> extensionCapabilities() {
                return Set.of(GpuExtensionCapability.DIAGNOSTIC_REPORTING);
            }
        };
        GpuRuntimeLifecycleEventListener wrongPermission = new OrderedLifecycleListener("test.lifecycle.wrong-permission", 0) {
            @Override
            public GpuExtensionPermission extensionPermission() {
                return GpuExtensionPermission.MUTATION_PROPOSAL;
            }
        };

        assertThrows(IllegalArgumentException.class, () -> GpuRuntimeLifecycleEventBus.of(List.of(wrongPhase)));
        assertThrows(IllegalArgumentException.class, () -> GpuRuntimeLifecycleEventBus.of(List.of(wrongCapability)));
        assertThrows(IllegalArgumentException.class, () -> GpuRuntimeLifecycleEventBus.of(List.of(wrongPermission)));
    }

    @Test
    void eventFieldsAreImmutableAndPropertiesSafe() {
        GpuRuntimeLifecycleEvent event = GpuRuntimeLifecycleEvent
                .of(GpuRuntimeLifecycleEventKind.ARTIFACT_DUMP_COMPLETED)
                .withField("path", "build/out\nnext-line");

        assertThrows(UnsupportedOperationException.class, () -> event.fields().put("other", "value"));

        String properties = new GpuRuntimeLifecycleEventReport(event, List.of()).toPropertiesText();

        assertTrue(properties.contains("runtimeLifecycle.event.kind=ARTIFACT_DUMP_COMPLETED"));
        assertTrue(properties.contains("runtimeLifecycle.event.field.0.value=build/out\\nnext-line"));
    }

    private static GpuRuntimeLifecycleEventListener listener(
            String id,
            int order,
            List<String> events
    ) {
        return new OrderedLifecycleListener(id, order) {
            @Override
            public void onRuntimeLifecycleEvent(GpuRuntimeLifecycleEvent event) {
                events.add(extensionId() + ":" + event.kind().name());
            }
        };
    }

    private static class OrderedLifecycleListener implements GpuRuntimeLifecycleEventListener {
        private final String id;
        private final int order;

        private OrderedLifecycleListener(String id, int order) {
            this.id = id;
            this.order = order;
        }

        @Override
        public void onRuntimeLifecycleEvent(GpuRuntimeLifecycleEvent event) {
        }

        @Override
        public String extensionId() {
            return id;
        }

        @Override
        public String extensionVersion() {
            return "1";
        }

        @Override
        public int extensionOrder() {
            return order;
        }
    }
}
