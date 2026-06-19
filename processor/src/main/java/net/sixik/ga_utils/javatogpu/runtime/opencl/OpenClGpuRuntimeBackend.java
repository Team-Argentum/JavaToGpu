package net.sixik.ga_utils.javatogpu.runtime.opencl;

import dev.denismasterherobrine.packager.opencl.core.OpenClBuffer;
import dev.denismasterherobrine.packager.opencl.core.OpenClCommandQueue;
import dev.denismasterherobrine.packager.opencl.core.OpenClEvents;
import dev.denismasterherobrine.packager.opencl.core.OpenClException;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackend;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import org.lwjgl.opencl.CL10;

public class OpenClGpuRuntimeBackend implements GpuRuntimeBackend, AutoCloseable {

    private final Map<GpuKernelDescriptor, OpenClCompiledKernel> compiledKernels = new ConcurrentHashMap<>();
    private final OpenClDeviceBufferRegistry bufferRegistry = new OpenClDeviceBufferRegistry();
    private final OpenClExecutionPreparer executionPreparer = new OpenClExecutionPreparer(bufferRegistry);
    private final Map<String, Object> nativeBuffers = new ConcurrentHashMap<>();
    private volatile OpenClRuntimeSession session;

    @Override
    public final void invoke(GpuKernelInvocation invocation) {
        if (OpenClAbiDebug.enabled()) {
            System.err.println(OpenClAbiDebug.describeInvocation(invocation.descriptor(), invocation.arguments()));
        }
        OpenClCompiledKernel compiledKernel = compiledKernels.computeIfAbsent(
                invocation.descriptor(),
                this::compileKernel
        );
        OpenClKernelArguments arguments = OpenClArgumentMarshaller.marshall(invocation.descriptor(), invocation.arguments());
        OpenClExecutionPlan plan = OpenClExecutionPlanner.plan(arguments);
        executeKernel(executionPreparer.prepare(compiledKernel, plan));
    }

    protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
        return session().compileKernel(kernelDescriptor);
    }

    protected void executeKernel(OpenClPreparedExecution execution) {
        for (OpenClPreparedBufferBinding binding : execution.bufferBindings()) {
            Object nativeBuffer = resolveNativeBuffer(binding);
            if (binding.binding().uploadRequired()) {
                uploadToDeviceBuffer(nativeBuffer, binding.binding());
            }
        }

        for (OpenClPreparedArgumentBinding binding : execution.argumentBindings()) {
            if (binding.bufferBinding() != null) {
                bindBufferArgument(
                        execution.compiledKernel(),
                        binding.parameterIndex(),
                        resolveNativeBuffer(binding.bufferBinding())
                );
                continue;
            }

            if (binding.localBinding() != null) {
                bindLocalArgument(execution.compiledKernel(), binding.parameterIndex(), binding.localBinding());
                continue;
            }

            bindScalarArgument(execution.compiledKernel(), binding.parameterIndex(), binding.scalarBinding());
        }

        enqueueKernel(execution.compiledKernel(), resolveGlobalWorkSize(execution));

        for (OpenClPreparedBufferBinding binding : execution.bufferBindings()) {
            if (binding.binding().readbackRequired()) {
                readBackFromDeviceBuffer(resolveNativeBuffer(binding), binding.binding());
            }
        }
    }

    protected OpenClRuntimeSession createSession() {
        return OpenClRuntimeSession.createDefault();
    }

    protected Object createDeviceBuffer(OpenClBufferBinding binding) {
        return session().createReadWriteBuffer(bytesFor(binding));
    }

    protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
        if (binding.sourceArray() instanceof byte[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length);
            buffer.put(values).flip();
            session().queue().writeBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            return;
        }
        if (binding.sourceArray() instanceof short[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length * Short.BYTES);
            for (short value : values) {
                buffer.putShort(value);
            }
            buffer.flip();
            writeBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            return;
        }
        if (binding.sourceArray() instanceof int[] values) {
            IntBuffer buffer = allocateIntBuffer(values.length);
            buffer.put(values).flip();
            session().queue().writeBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            return;
        }
        if (binding.sourceArray() instanceof long[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length * Long.BYTES);
            for (long value : values) {
                buffer.putLong(value);
            }
            buffer.flip();
            writeBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            return;
        }
        if (binding.sourceArray() instanceof float[] values) {
            FloatBuffer buffer = allocateFloatBuffer(values.length);
            buffer.put(values).flip();
            session().queue().writeBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            return;
        }
        if (binding.sourceArray() instanceof double[] values) {
            DoubleBuffer buffer = allocateDoubleBuffer(values.length);
            buffer.put(values).flip();
            readWriteBufferDirect((OpenClBuffer) nativeBuffer, buffer, true);
            return;
        }
        if (binding.kind() == OpenClArgumentKind.STRUCT_ARRAY) {
            writeBufferDirect((OpenClBuffer) nativeBuffer, OpenClValuePacker.packStructArray(binding.sourceArray()));
            return;
        }
        if (binding.kind() == OpenClArgumentKind.VECTOR_ARRAY) {
            writeBufferDirect((OpenClBuffer) nativeBuffer, OpenClValuePacker.packVectorArray(binding.sourceArray()));
            return;
        }

        throw new IllegalArgumentException("Unsupported OpenCL upload source type: " + binding.sourceArray().getClass().getName());
    }

    protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
        compiledKernel.kernel().setArg(parameterIndex, (OpenClBuffer) nativeBuffer);
    }

    protected void bindLocalArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClLocalBinding binding) {
        checkCl(
                CL10.clSetKernelArg(compiledKernel.kernel().handle(), parameterIndex, binding.byteSize()),
                "clSetKernelArg"
        );
    }

    protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
        switch (binding.kind()) {
            case INT8 -> checkCl(
                    CL10.clSetKernelArg1b(compiledKernel.kernel().handle(), parameterIndex, (Byte) binding.value()),
                    "clSetKernelArg1b"
            );
            case INT16 -> checkCl(
                    CL10.clSetKernelArg1s(compiledKernel.kernel().handle(), parameterIndex, (Short) binding.value()),
                    "clSetKernelArg1s"
            );
            case INT32 -> compiledKernel.kernel().setArgInt(parameterIndex, (Integer) binding.value());
            case INT64 -> checkCl(
                    CL10.clSetKernelArg1l(compiledKernel.kernel().handle(), parameterIndex, (Long) binding.value()),
                    "clSetKernelArg1l"
            );
            case FLOAT32 -> compiledKernel.kernel().setArgFloat(parameterIndex, (Float) binding.value());
            case FLOAT64 -> checkCl(
                    CL10.clSetKernelArg1d(compiledKernel.kernel().handle(), parameterIndex, (Double) binding.value()),
                    "clSetKernelArg1d"
            );
            case PACKED_VALUE -> {
                ByteBuffer valueBuffer = ((ByteBuffer) binding.value()).duplicate().order(ByteOrder.nativeOrder());
                valueBuffer.clear();
                checkCl(
                        CL10.clSetKernelArg(compiledKernel.kernel().handle(), parameterIndex, valueBuffer),
                        "clSetKernelArg"
                );
            }
            default -> throw new IllegalArgumentException("Unsupported OpenCL scalar binding kind: " + binding.kind());
        }
    }

    protected void enqueueKernel(OpenClCompiledKernel compiledKernel, long globalWorkSize) {
        long event = compiledKernel.kernel().enqueue1D(session().queue(), globalWorkSize, null);
        try {
            OpenClEvents.waitFor(event);
        } finally {
            OpenClEvents.release(event);
        }
        session().queue().finish();
    }

    protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
        if (binding.sourceArray() instanceof byte[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length);
            session().queue().readBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            buffer.position(0);
            buffer.get(values);
            return;
        }
        if (binding.sourceArray() instanceof short[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length * Short.BYTES);
            readBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            buffer.position(0);
            for (int i = 0; i < values.length; i++) {
                values[i] = buffer.getShort();
            }
            return;
        }
        if (binding.sourceArray() instanceof int[] values) {
            IntBuffer buffer = allocateIntBuffer(values.length);
            session().queue().readBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            buffer.position(0);
            buffer.get(values);
            return;
        }
        if (binding.sourceArray() instanceof long[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length * Long.BYTES);
            readBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            buffer.position(0);
            for (int i = 0; i < values.length; i++) {
                values[i] = buffer.getLong();
            }
            return;
        }
        if (binding.sourceArray() instanceof float[] values) {
            FloatBuffer buffer = allocateFloatBuffer(values.length);
            session().queue().readBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            buffer.position(0);
            buffer.get(values);
            return;
        }
        if (binding.sourceArray() instanceof double[] values) {
            DoubleBuffer buffer = allocateDoubleBuffer(values.length);
            readWriteBufferDirect((OpenClBuffer) nativeBuffer, buffer, false);
            buffer.position(0);
            buffer.get(values);
            return;
        }
        if (binding.kind() == OpenClArgumentKind.STRUCT_ARRAY) {
            ByteBuffer buffer = allocateByteBuffer(OpenClValuePacker.structArrayByteSize(binding.sourceArray()));
            readBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            OpenClValuePacker.unpackStructArray(buffer, binding.sourceArray());
            return;
        }
        if (binding.kind() == OpenClArgumentKind.VECTOR_ARRAY) {
            ByteBuffer buffer = allocateByteBuffer(OpenClValuePacker.vectorArrayByteSize(binding.sourceArray()));
            readBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            OpenClValuePacker.unpackVectorArray(buffer, binding.sourceArray());
            return;
        }

        throw new IllegalArgumentException("Unsupported OpenCL readback target type: " + binding.sourceArray().getClass().getName());
    }

    @Override
    public void close() {
        compiledKernels.values().forEach(OpenClCompiledKernel::close);
        compiledKernels.clear();

        nativeBuffers.values().forEach(value -> {
            if (value instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception exception) {
                    throw new RuntimeException("Failed to close OpenCL device buffer", exception);
                }
            }
        });
        nativeBuffers.clear();

        OpenClRuntimeSession currentSession = session;
        session = null;
        if (currentSession != null) {
            currentSession.close();
        }
    }

    int cacheSize() {
        return compiledKernels.size();
    }

    private OpenClRuntimeSession session() {
        OpenClRuntimeSession current = session;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            current = session;
            if (current == null) {
                try {
                    current = createSession();
                } catch (UnsatisfiedLinkError | IllegalStateException exception) {
                    throw new UnsupportedOperationException("OpenCL runtime is unavailable: " + exception.getMessage(), exception);
                }
                session = current;
            }
        }

        return current;
    }

    private Object resolveNativeBuffer(OpenClPreparedBufferBinding binding) {
        return nativeBuffers.computeIfAbsent(
                binding.handle().handleId(),
                ignored -> createDeviceBuffer(binding.binding())
        );
    }

    private long resolveGlobalWorkSize(OpenClPreparedExecution execution) {
        if (execution.bufferBindings().isEmpty()) {
            throw new UnsupportedOperationException(
                    "OpenCL execution requires at least one buffer argument to derive global work size for kernel "
                            + execution.compiledKernel().descriptor().kernelName()
            );
        }

        int expectedLength = execution.bufferBindings().get(0).binding().length();
        for (OpenClPreparedBufferBinding binding : execution.bufferBindings()) {
            if (binding.binding().length() != expectedLength) {
                throw new IllegalArgumentException(
                        "Mismatched GPU array lengths for kernel "
                                + execution.compiledKernel().descriptor().kernelName()
                                + ": expected "
                                + expectedLength
                                + " but found "
                                + binding.binding().length()
                );
            }
        }

        return expectedLength;
    }

    private long bytesFor(OpenClBufferBinding binding) {
        return switch (binding.kind()) {
            case BYTE_ARRAY -> binding.length();
            case SHORT_ARRAY -> (long) binding.length() * Short.BYTES;
            case INT_ARRAY -> (long) binding.length() * Integer.BYTES;
            case LONG_ARRAY -> (long) binding.length() * Long.BYTES;
            case FLOAT_ARRAY -> (long) binding.length() * Float.BYTES;
            case DOUBLE_ARRAY -> (long) binding.length() * Double.BYTES;
            case STRUCT_ARRAY -> OpenClValuePacker.structArrayByteSize(binding.sourceArray());
            case VECTOR_ARRAY -> OpenClValuePacker.vectorArrayByteSize(binding.sourceArray());
            default -> throw new IllegalArgumentException("Unsupported OpenCL buffer kind: " + binding.kind());
        };
    }

    private void writeBufferDirect(OpenClBuffer buffer, ByteBuffer values) {
        checkCl(
                CL10.clEnqueueWriteBuffer(session().queue().handle(), buffer.handle(), true, 0L, values, null, null),
                "clEnqueueWriteBuffer"
        );
        session().queue().finish();
    }

    private void readBufferDirect(OpenClBuffer buffer, ByteBuffer values) {
        checkCl(
                CL10.clEnqueueReadBuffer(session().queue().handle(), buffer.handle(), true, 0L, values, null, null),
                "clEnqueueReadBuffer"
        );
        session().queue().finish();
    }

    private void readWriteBufferDirect(OpenClBuffer buffer, DoubleBuffer values, boolean write) {
        int result = write
                ? CL10.clEnqueueWriteBuffer(session().queue().handle(), buffer.handle(), true, 0L, values, null, null)
                : CL10.clEnqueueReadBuffer(session().queue().handle(), buffer.handle(), true, 0L, values, null, null);
        checkCl(result, write ? "clEnqueueWriteBuffer" : "clEnqueueReadBuffer");
        session().queue().finish();
    }

    private void checkCl(int errorCode, String operation) {
        OpenClException.check(errorCode, operation);
    }

    private ByteBuffer allocateByteBuffer(int sizeBytes) {
        return ByteBuffer.allocateDirect(sizeBytes).order(ByteOrder.nativeOrder());
    }

    private IntBuffer allocateIntBuffer(int length) {
        return allocateByteBuffer(length * Integer.BYTES).asIntBuffer();
    }

    private FloatBuffer allocateFloatBuffer(int length) {
        return allocateByteBuffer(length * Float.BYTES).asFloatBuffer();
    }

    private DoubleBuffer allocateDoubleBuffer(int length) {
        return allocateByteBuffer(length * Double.BYTES).asDoubleBuffer();
    }
}
