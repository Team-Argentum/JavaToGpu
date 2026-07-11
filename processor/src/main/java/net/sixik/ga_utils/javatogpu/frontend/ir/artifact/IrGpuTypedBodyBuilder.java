package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitchCase;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Builds a deterministic flattened node graph from the current typed frontend IR records.
 */
public final class IrGpuTypedBodyBuilder {

    private final ArrayList<IrGpuTypedNode> nodes = new ArrayList<>();

    private IrGpuTypedBodyBuilder() {
    }

    public static IrGpuTypedBody fromStatements(List<GpuIrStatement> statements) {
        IrGpuTypedBodyBuilder builder = new IrGpuTypedBodyBuilder();
        ArrayList<Integer> roots = new ArrayList<>();
        if (statements != null) {
            for (GpuIrStatement statement : statements) {
                roots.add(builder.addNode(statement));
            }
        }
        return roots.isEmpty()
                ? IrGpuTypedBody.none()
                : new IrGpuTypedBody(IrGpuTypedBody.FORMAT, roots, builder.nodes);
    }

    private int addNode(Object value) {
        if (!isStructuralNode(value)) {
            throw new IllegalArgumentException("Unsupported typed IrGpu node: " + value);
        }
        int id = nodes.size();
        nodes.add(null);
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        LinkedHashMap<String, List<Integer>> children = new LinkedHashMap<>();
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            Object componentValue = readComponent(component, value);
            collectComponent(component.getName(), componentValue, attributes, children);
        }
        nodes.set(id, new IrGpuTypedNode(id, value.getClass().getSimpleName(), attributes, children));
        return id;
    }

    private void collectComponent(
            String name,
            Object value,
            LinkedHashMap<String, String> attributes,
            LinkedHashMap<String, List<Integer>> children
    ) {
        if (value == null) {
            attributes.put(name + ".null", "true");
            return;
        }
        if (isStructuralNode(value)) {
            children.put(name, List.of(addNode(value)));
            return;
        }
        if (value instanceof List<?> values) {
            collectList(name, values, attributes, children);
            return;
        }
        attributes.put(name, String.valueOf(value));
    }

    private void collectList(
            String name,
            List<?> values,
            LinkedHashMap<String, String> attributes,
            LinkedHashMap<String, List<Integer>> children
    ) {
        if (values.isEmpty()) {
            attributes.put(name + ".count", "0");
            return;
        }
        boolean structural = values.stream().allMatch(IrGpuTypedBodyBuilder::isStructuralNode);
        if (structural) {
            ArrayList<Integer> childIds = new ArrayList<>();
            for (Object value : values) {
                childIds.add(addNode(value));
            }
            children.put(name, childIds);
            return;
        }
        attributes.put(name + ".count", Integer.toString(values.size()));
        for (int index = 0; index < values.size(); index++) {
            attributes.put(name + "." + index, String.valueOf(values.get(index)));
        }
    }

    private static Object readComponent(RecordComponent component, Object owner) {
        try {
            return component.getAccessor().invoke(owner);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException(
                    "Failed to read typed IrGpu component " + owner.getClass().getSimpleName() + "." + component.getName(),
                    exception
            );
        }
    }

    private static boolean isStructuralNode(Object value) {
        return value instanceof GpuIrExpression
                || value instanceof GpuIrStatement
                || value instanceof GpuIrSwitchCase;
    }
}
