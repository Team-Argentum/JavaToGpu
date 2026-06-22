package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.Image1DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DBufferReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DBufferWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.lang.reflect.Array;

final class OpenClAbiDebug {

    static final String PROPERTY = "javatogpu.opencl.debugAbi";

    private OpenClAbiDebug() {
    }

    static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    static String describeInvocation(GpuKernelDescriptor descriptor, Object[] arguments) {
        StringBuilder builder = new StringBuilder();
        builder.append("OpenCL ABI debug for kernel ")
                .append(descriptor.kernelName())
                .append("\n");

        for (int i = 0; i < descriptor.parameterDescriptors().size(); i++) {
            GpuKernelParameterDescriptor parameter = descriptor.parameterDescriptors().get(i);
            Object argument = i < arguments.length ? arguments[i] : null;
            builder.append("- ")
                    .append(parameter.name())
                    .append(" : ")
                    .append(parameter.javaType())
                    .append(" [")
                    .append(parameter.access())
                    .append("]\n");
            appendArgumentDebug(builder, parameter, argument);
        }

        return builder.toString();
    }

    static String describeParameterFailure(GpuKernelParameterDescriptor parameter, Object argument) {
        StringBuilder builder = new StringBuilder();
        builder.append("Parameter ")
                .append(parameter.name())
                .append(" : ")
                .append(parameter.javaType())
                .append(" [")
                .append(parameter.access())
                .append("]");
        if (argument == null) {
            builder.append("\n  runtime argument is null");
            return builder.toString();
        }
        builder.append("\n  runtime type: ").append(argument.getClass().getName());
        appendArgumentDebug(builder, parameter, argument);
        return builder.toString();
    }

    private static void appendArgumentDebug(StringBuilder builder, GpuKernelParameterDescriptor parameter, Object argument) {
        if (argument == null) {
            builder.append("  value: null\n");
            return;
        }

        if (GpuTypeSupport.isSupportedVectorType(parameter.javaType())) {
            appendIndented(builder, OpenClAbiSupport.debugVectorType(parameter.javaType()), 1);
            return;
        }
        if (argument instanceof Image2DReadOnly image) {
            builder.append("  native image handle: ").append(image.handle()).append("\n");
            builder.append("  image size: ").append(image.width()).append("x").append(image.height()).append("\n");
            return;
        }
        if (argument instanceof Image1DReadOnly image) {
            builder.append("  native image handle: ").append(image.handle()).append("\n");
            builder.append("  image size: ").append(image.width()).append("\n");
            return;
        }
        if (argument instanceof Image1DWriteOnly image) {
            builder.append("  native image handle: ").append(image.handle()).append("\n");
            builder.append("  image size: ").append(image.width()).append("\n");
            return;
        }
        if (argument instanceof Image1DArrayReadOnly image) {
            builder.append("  native image handle: ").append(image.handle()).append("\n");
            builder.append("  image size: ").append(image.width()).append(" x ").append(image.layers()).append(" layers\n");
            return;
        }
        if (argument instanceof Image1DArrayWriteOnly image) {
            builder.append("  native image handle: ").append(image.handle()).append("\n");
            builder.append("  image size: ").append(image.width()).append(" x ").append(image.layers()).append(" layers\n");
            return;
        }
        if (argument instanceof Image1DBufferReadOnly image) {
            builder.append("  native image handle: ").append(image.handle()).append("\n");
            builder.append("  image size: ").append(image.width()).append(" (buffer-backed)\n");
            return;
        }
        if (argument instanceof Image1DBufferWriteOnly image) {
            builder.append("  native image handle: ").append(image.handle()).append("\n");
            builder.append("  image size: ").append(image.width()).append(" (buffer-backed)\n");
            return;
        }
        if (argument instanceof Image2DWriteOnly image) {
            builder.append("  native image handle: ").append(image.handle()).append("\n");
            builder.append("  image size: ").append(image.width()).append("x").append(image.height()).append("\n");
            return;
        }
        if (argument instanceof Image2DArrayReadOnly image) {
            builder.append("  native image handle: ").append(image.handle()).append("\n");
            builder.append("  image size: ").append(image.width()).append("x").append(image.height()).append(" x ").append(image.layers()).append(" layers\n");
            return;
        }
        if (argument instanceof Image2DArrayWriteOnly image) {
            builder.append("  native image handle: ").append(image.handle()).append("\n");
            builder.append("  image size: ").append(image.width()).append("x").append(image.height()).append(" x ").append(image.layers()).append(" layers\n");
            return;
        }
        if (argument instanceof Image3DReadOnly image) {
            builder.append("  native image handle: ").append(image.handle()).append("\n");
            builder.append("  image size: ").append(image.width()).append("x").append(image.height()).append("x").append(image.depth()).append("\n");
            return;
        }
        if (argument instanceof Image3DWriteOnly image) {
            builder.append("  native image handle: ").append(image.handle()).append("\n");
            builder.append("  image size: ").append(image.width()).append("x").append(image.height()).append("x").append(image.depth()).append("\n");
            return;
        }
        if (argument instanceof Sampler sampler) {
            builder.append("  native sampler handle: ").append(sampler.handle()).append("\n");
            return;
        }
        if (OpenClAbiSupport.isStructInstance(argument)) {
            appendIndented(builder, OpenClAbiSupport.debugStructType(argument.getClass()), 1);
            return;
        }
        if (OpenClAbiSupport.isVectorArrayInstance(argument)) {
            Class<?> componentType = argument.getClass().getComponentType();
            builder.append("  length: ").append(Array.getLength(argument)).append("\n");
            appendIndented(builder, OpenClAbiSupport.debugVectorType(componentType.getName()), 1);
            return;
        }
        if (OpenClAbiSupport.isStructArrayInstance(argument)) {
            Class<?> componentType = argument.getClass().getComponentType();
            builder.append("  length: ").append(Array.getLength(argument)).append("\n");
            appendIndented(builder, OpenClAbiSupport.debugStructType(componentType), 1);
            return;
        }
        if (argument.getClass().isArray()) {
            builder.append("  length: ").append(Array.getLength(argument)).append("\n");
            return;
        }
        builder.append("  value: ").append(argument).append("\n");
    }

    private static void appendIndented(StringBuilder builder, String text, int indentLevel) {
        String prefix = "  ".repeat(indentLevel);
        for (String line : text.split("\\R")) {
            if (line.isEmpty()) {
                continue;
            }
            builder.append(prefix).append(line).append("\n");
        }
    }
}
