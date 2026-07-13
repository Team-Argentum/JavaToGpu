package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeIrTypedNodeGraphTest {

    @Test
    void callNameRecognizesSerializedIntrinsicBackendName() {
        IrGpuTypedNode intrinsic = new IrGpuTypedNode(
                0,
                "GpuIrIntrinsicCall",
                Map.of("backendName", "min"),
                Map.of("arguments", List.of(1, 2))
        );
        GpuRuntimeIrTypedNodeGraph graph = GpuRuntimeIrTypedNodeGraph.from(new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0),
                List.of(
                        intrinsic,
                        new IrGpuTypedNode(1, "GpuIrVariableRef", Map.of("name", "x"), Map.of()),
                        new IrGpuTypedNode(2, "GpuIrVariableRef", Map.of("name", "y"), Map.of())
                )
        ));

        assertEquals("min", graph.callName(intrinsic));
        assertTrue(graph.isCall(intrinsic, "min"));
        assertEquals(List.of(1, 2), graph.callArguments(intrinsic));
    }
}
