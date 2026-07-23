package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuMemorySlice;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationBindingSummary;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Built-in CUDA Driver API argument-binder preflight.
 */
final class CudaDriverArgumentBinderBridge implements CudaArgumentBinderBridge {

    static final String ID = "cuda-argument-binder:driver";

    @Override
    public String binderId() {
        return ID;
    }

    @Override
    public int binderOrder() {
        return 100;
    }

    @Override
    public boolean supports(CudaArgumentBindingRequest request) {
        return request != null
                && GpuBackendCompileOptions.CUDA_ARGUMENT_BINDER_DRIVER.equals(request.binderMode());
    }

    @Override
    public CudaArgumentBindingResult bind(CudaArgumentBindingRequest request) {
        CudaDriverLoadedModule loadedModule = request.moduleLoadResult().loadedModule();
        if (loadedModule == null) {
            return CudaArgumentBindingResult.unsupported(
                    request.binderMode(),
                    List.of("cuda-driver-module-handle-missing"),
                    List.of("CUDA driver argument binding requires a real CUDA Driver API module/function handle")
            );
        }

        GpuRuntimeInvocationBindingSummary summary = request.descriptorBindingSummary();
        if (summary.argumentBindingCount() == 0) {
            CudaKernelArgumentFrame frame = CudaKernelArgumentFrame.empty(binderId());
            return CudaArgumentBindingResult.succeeded(
                    binderId(),
                    summary,
                    frame,
                    List.of("CUDA driver argument frame prepared for zero-argument kernel"),
                    request.executionPlan()
            );
        }

        ArrayList<String> blockers = new ArrayList<>();
        if (!request.invocationArgumentsPresent()) {
            blockers.add("cuda-driver-argument-values-missing");
            if (summary.bufferBindingCount() > 0) {
                blockers.add("cuda-driver-buffer-binding-missing");
            }
            if (summary.scalarBindingCount() > 0) {
                blockers.add("cuda-driver-scalar-value-binding-missing");
            }
            if (summary.localBindingCount() > 0) {
                blockers.add("cuda-driver-local-binding-missing");
            }
        } else if (!request.invocationArgumentCountMatchesDescriptor()) {
            blockers.add("cuda-driver-argument-count-mismatch");
            List<GpuKernelParameterDescriptor> parameters = summaryParameters(request);
            return CudaArgumentBindingResult.unsupportedWithExecutionPlan(
                    request.binderMode(),
                    request.executionPlan(),
                    CudaImageSamplerRuntimeBindingPlan.from(parameters, request.invocationArguments()),
                    CudaImageSamplerDescriptorBuildPlan.from(parameters, request.invocationArguments()),
                    blockers,
                    List.of("CUDA driver argument binding received "
                            + request.invocationArgumentCount()
                            + " invocation values for "
                            + request.descriptorArgumentCount()
                            + " descriptor parameters")
            );
        }
        if (blockers.isEmpty()) {
            return bindNativeArguments(request, loadedModule, summary);
        }
        return CudaArgumentBindingResult.unsupportedWithExecutionPlan(
                request.binderMode(),
                request.executionPlan(),
                blockers,
                List.of(request.invocationArgumentsPresent()
                        ? "CUDA driver argument values are available, but native argument binding is blocked before allocation"
                        : "CUDA driver argument binding needs invocation argument values before device memory can be allocated")
        );
    }

    private CudaArgumentBindingResult bindNativeArguments(
            CudaArgumentBindingRequest request,
            CudaDriverLoadedModule loadedModule,
            GpuRuntimeInvocationBindingSummary summary
    ) {
        List<GpuKernelParameterDescriptor> parameters = request.descriptor().parameterDescriptors();
        Object[] arguments = request.invocationArguments();
        CudaImageSamplerRuntimeBindingPlan imageSamplerRuntimeBindingPlan = CudaImageSamplerRuntimeBindingPlan.from(parameters, arguments);
        CudaImageSamplerDescriptorBuildPlan imageSamplerDescriptorBuildPlan = CudaImageSamplerDescriptorBuildPlan.from(parameters, arguments);
        ArrayList<String> blockers = validateNativeArguments(parameters, arguments);
        if (!blockers.isEmpty()) {
            return CudaArgumentBindingResult.unsupportedWithExecutionPlan(
                    request.binderMode(),
                    request.executionPlan(),
                    imageSamplerRuntimeBindingPlan,
                    imageSamplerDescriptorBuildPlan,
                    blockers,
                    unsupportedNativeArgumentDiagnostics(imageSamplerRuntimeBindingPlan)
            );
        }
        MemorySymbols symbols = requiresDeviceMemory(parameters)
                ? resolveMemorySymbols(loadedModule)
                : MemorySymbols.noMemoryRequired();
        if (!symbols.available()) {
            return CudaArgumentBindingResult.unsupportedWithExecutionPlan(
                    request.binderMode(),
                    request.executionPlan(),
                    symbols.blockers(),
                    List.of("CUDA Driver API memory symbols are required for native argument binding")
            );
        }

        ArrayList<CudaDriverDeviceAllocation> allocations = new ArrayList<>();
        PointerBuffer parameterTable = null;
        ArrayList<PointerBuffer> argumentSlots = new ArrayList<>();
        ArrayList<ByteBuffer> scalarSlots = new ArrayList<>();
        CudaLocalSharedMemoryLayout localSharedMemoryLayout = CudaLocalSharedMemoryLayout.from(parameters, arguments);
        try {
            int kernelParameterSlotCount = kernelParameterSlotCount(parameters, localSharedMemoryLayout);
            if (kernelParameterSlotCount > 0) {
                parameterTable = MemoryUtil.memAllocPointer(kernelParameterSlotCount);
            }
            int kernelParameterIndex = 0;
            for (int index = 0; index < parameters.size(); index++) {
                GpuKernelParameterDescriptor parameter = parameters.get(index);
                if (parameter.access() == GpuKernelParameterAccess.LOCAL) {
                    continue;
                }
                if (parameter.access() == GpuKernelParameterAccess.VALUE) {
                    ByteBuffer scalarSlot = allocateScalarArgumentSlot(parameter, index, arguments[index]);
                    scalarSlots.add(scalarSlot);
                    parameterTable.put(kernelParameterIndex++, MemoryUtil.memAddress(scalarSlot));
                    continue;
                }
                Object values = arguments[index];
                CudaDriverDeviceAllocation allocation = allocatePrimitiveArray(parameter, index, values, loadedModule, symbols);
                allocations.add(allocation);
                PointerBuffer pointerSlot = MemoryUtil.memAllocPointer(1);
                pointerSlot.put(0, allocation.devicePointer());
                argumentSlots.add(pointerSlot);
                parameterTable.put(kernelParameterIndex++, MemoryUtil.memAddress(pointerSlot));
            }
            for (CudaLocalSharedMemoryLayout.Slice slice : localSharedMemoryLayout.slices()) {
                if (slice.hiddenOffsetParameterName().isBlank()) {
                    continue;
                }
                ByteBuffer offsetSlot = allocateLocalOffsetArgumentSlot(slice);
                scalarSlots.add(offsetSlot);
                parameterTable.put(kernelParameterIndex++, MemoryUtil.memAddress(offsetSlot));
            }
            CudaKernelArgumentFrame frame = CudaKernelArgumentFrame.nativeBindings(
                    binderId(),
                    summary,
                    allocations,
                    parameterTable,
                    argumentSlots,
                    scalarSlots,
                    localSharedMemoryLayout.totalByteSize(),
                    localSharedMemoryLayout
            );
            return CudaArgumentBindingResult.succeeded(
                    binderId(),
                    summary,
                    frame,
                    List.of("CUDA driver native argument frame prepared for primitive/vector/struct array buffers, scalar or @GPUStruct VALUE arguments, and primitive/struct LOCAL shared memory"),
                    request.executionPlan()
            );
        } catch (DriverCallException exception) {
            CudaKernelArgumentFrame.freeArgumentStorage(parameterTable, argumentSlots, scalarSlots);
            closeAllocationsQuietly(allocations);
            return CudaArgumentBindingResult.failedWithExecutionPlan(
                    binderId(),
                    request.executionPlan(),
                    List.of(exception.blocker()),
                    List.of(exception.getMessage())
            );
        } catch (RuntimeException exception) {
            CudaKernelArgumentFrame.freeArgumentStorage(parameterTable, argumentSlots, scalarSlots);
            closeAllocationsQuietly(allocations);
            return CudaArgumentBindingResult.failedWithExecutionPlan(
                    binderId(),
                    request.executionPlan(),
                    List.of("cuda-driver-argument-binding-exception:" + exception.getClass().getSimpleName()),
                    List.of(exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage())
            );
        }
    }

    private static List<GpuKernelParameterDescriptor> summaryParameters(CudaArgumentBindingRequest request) {
        return request.descriptor() == null || request.descriptor().parameterDescriptors() == null
                ? List.of()
                : request.descriptor().parameterDescriptors();
    }

    private static List<String> unsupportedNativeArgumentDiagnostics(
            CudaImageSamplerRuntimeBindingPlan imageSamplerRuntimeBindingPlan
    ) {
        String base = "CUDA driver argument binding currently supports non-empty primitive/vector/struct array buffers, primitive scalar or @GPUStruct VALUE arguments, and primitive/struct array LOCAL shared-memory bindings";
        if (imageSamplerRuntimeBindingPlan == null || !imageSamplerRuntimeBindingPlan.present()) {
            return List.of(base);
        }
        return List.of(
                base,
                "CUDA image/sampler runtime binding preflight recorded planned texture/surface/sampler slots, but active CUDA image/sampler binding remains fail-closed"
        );
    }

    private static ArrayList<String> validateNativeArguments(
            List<GpuKernelParameterDescriptor> parameters,
            Object[] arguments
    ) {
        ArrayList<String> blockers = new ArrayList<>();
        for (int index = 0; index < parameters.size(); index++) {
            GpuKernelParameterDescriptor parameter = parameters.get(index);
            if (parameter == null) {
                blockers.add("cuda-driver-parameter-descriptor-missing:" + index);
                continue;
            }
            if (GpuTypeSupport.isSupportedImageOrSamplerType(parameter.javaType())) {
                validateImageOrSamplerArgument(parameter, index, blockers);
                continue;
            }
            if (parameter.access() == GpuKernelParameterAccess.VALUE) {
                validateScalarArgument(parameter, index, arguments[index], blockers);
                continue;
            }
            if (parameter.access() == GpuKernelParameterAccess.LOCAL) {
                validateLocalArgument(parameters, parameter, index, arguments[index], blockers);
                continue;
            }
            if (parameter.access() != GpuKernelParameterAccess.READ_ONLY
                    && parameter.access() != GpuKernelParameterAccess.READ_WRITE) {
                blockers.add("cuda-driver-buffer-access-unsupported:" + index + ":" + parameter.access());
                continue;
            }
            String declaredType = GpuTypeSupport.declaredType(parameter.javaType());
            if (!isSupportedBufferArrayArgumentType(declaredType)) {
                blockers.add("cuda-driver-buffer-type-unsupported:" + index + ":" + parameter.javaType());
                continue;
            }
            Object argument = arguments[index];
            if (!arrayArgumentCompatible(declaredType, argument)) {
                blockers.add("cuda-driver-array-argument-type-mismatch:" + index + ":" + declaredType);
                continue;
            }
            int elementCount = arrayLength(argument);
            if (elementCount == 0) {
                blockers.add("cuda-driver-empty-buffer-unsupported:" + index);
                continue;
            }
            long byteSize = arrayByteSize(declaredType, argument, elementCount);
            if (byteSize > Integer.MAX_VALUE) {
                blockers.add("cuda-driver-host-upload-too-large:" + index);
            }
        }
        validateLocalSharedMemoryLayout(parameters, arguments, blockers);
        return blockers;
    }

    private static void validateImageOrSamplerArgument(
            GpuKernelParameterDescriptor parameter,
            int parameterIndex,
            List<String> blockers
    ) {
        CudaImageSamplerAbi.descriptorFor(parameter.javaType())
                .map(descriptor -> descriptor.unsupportedBlocker(parameterIndex, parameter.javaType()))
                .ifPresentOrElse(
                        blockers::add,
                        () -> blockers.add("cuda-driver-image-sampler-argument-unsupported:"
                                + parameterIndex
                                + ":"
                                + parameter.javaType())
                );
    }

    private static boolean isSupportedBufferArrayArgumentType(String declaredType) {
        return isSupportedPrimitiveArrayArgumentType(declaredType)
                || isSupportedVectorArrayArgumentType(declaredType)
                || isPotentialStructArrayArgumentType(declaredType);
    }

    private static boolean isSupportedPrimitiveArrayArgumentType(String declaredType) {
        if (declaredType == null || !declaredType.endsWith("[]")) {
            return false;
        }
        return isSupportedPrimitiveBufferComponentType(GpuTypeSupport.componentType(declaredType));
    }

    private static boolean isSupportedVectorArrayArgumentType(String declaredType) {
        if (declaredType == null || !declaredType.endsWith("[]")) {
            return false;
        }
        return GpuTypeSupport.isSupportedVectorType(GpuTypeSupport.componentType(declaredType));
    }

    private static boolean isPotentialStructArrayArgumentType(String declaredType) {
        if (declaredType == null || !declaredType.endsWith("[]")) {
            return false;
        }
        String componentType = GpuTypeSupport.componentType(declaredType);
        return !isSupportedPrimitiveBufferComponentType(componentType)
                && !GpuTypeSupport.isSupportedVectorType(componentType);
    }

    private static boolean isSupportedPrimitiveBufferComponentType(String componentType) {
        return switch (componentType) {
            case "byte", "short", "char", "int", "long", "float", "double" -> true;
            default -> false;
        };
    }

    private static boolean primitiveArrayArgumentCompatible(String declaredType, Object argument) {
        Object array = hostArray(argument);
        return switch (declaredType) {
            case "byte[]" -> array instanceof byte[];
            case "short[]" -> array instanceof short[];
            case "char[]" -> array instanceof char[];
            case "int[]" -> array instanceof int[];
            case "long[]" -> array instanceof long[];
            case "float[]" -> array instanceof float[];
            case "double[]" -> array instanceof double[];
            default -> false;
        };
    }

    private static boolean arrayArgumentCompatible(String declaredType, Object argument) {
        if (isSupportedPrimitiveArrayArgumentType(declaredType)) {
            return primitiveArrayArgumentCompatible(declaredType, argument);
        }
        Object array = hostArray(argument);
        return CudaValuePacker.vectorArrayCompatible(declaredType, array)
                || CudaValuePacker.structArrayCompatible(declaredType, array);
    }

    private static int arrayLength(Object argument) {
        if (argument instanceof GpuMemorySlice<?> slice) {
            return slice.length();
        }
        if (CudaValuePacker.isStructArrayInstance(argument)) {
            return CudaValuePacker.structArrayLength(argument);
        }
        if (CudaValuePacker.isVectorArrayInstance(argument)) {
            return CudaValuePacker.vectorArrayLength(argument);
        }
        return primitiveArrayLength(argument);
    }

    private static long arrayByteSize(String declaredType, Object argument, int elementCount) {
        Object array = hostArray(argument);
        if (CudaValuePacker.vectorArrayCompatible(declaredType, array)) {
            return CudaValuePacker.vectorArrayByteSize(declaredType, elementCount);
        }
        if (CudaValuePacker.structArrayCompatible(declaredType, array)) {
            return CudaValuePacker.structArrayByteSize(array, elementCount);
        }
        return primitiveArrayByteSize(declaredType, elementCount);
    }

    private static Object hostArray(Object argument) {
        return argument instanceof GpuMemorySlice<?> slice ? slice.array() : argument;
    }

    private static int hostElementOffset(Object argument) {
        return argument instanceof GpuMemorySlice<?> slice ? slice.offset() : 0;
    }

    private static int primitiveArrayLength(Object argument) {
        if (argument instanceof GpuMemorySlice<?> slice) {
            return slice.length();
        }
        Object array = hostArray(argument);
        if (array instanceof byte[] values) {
            return values.length;
        }
        if (array instanceof short[] values) {
            return values.length;
        }
        if (array instanceof char[] values) {
            return values.length;
        }
        if (array instanceof int[] values) {
            return values.length;
        }
        if (array instanceof long[] values) {
            return values.length;
        }
        if (array instanceof float[] values) {
            return values.length;
        }
        if (array instanceof double[] values) {
            return values.length;
        }
        return 0;
    }

    private static long primitiveArrayByteSize(String declaredType, int elementCount) {
        return (long) elementCount * GpuTypeSupport.scalarByteSize(GpuTypeSupport.componentType(declaredType));
    }

    private static void validateLocalArgument(
            List<GpuKernelParameterDescriptor> parameters,
            GpuKernelParameterDescriptor parameter,
            int parameterIndex,
            Object argument,
            List<String> blockers
    ) {
        String declaredType = GpuTypeSupport.declaredType(parameter.javaType());
        boolean primitiveLocal = isSupportedPrimitiveArrayArgumentType(declaredType);
        boolean structLocal = CudaValuePacker.structArrayCompatible(declaredType, hostArray(argument));
        if (!primitiveLocal && !structLocal) {
            blockers.add("cuda-driver-local-buffer-type-unsupported:" + parameterIndex + ":" + parameter.javaType());
            return;
        }
        if (primitiveLocal && !primitiveArrayArgumentCompatible(declaredType, argument)) {
            blockers.add("cuda-driver-local-array-argument-type-mismatch:" + parameterIndex + ":" + declaredType);
            return;
        }
        int elementCount = arrayLength(argument);
        if (elementCount == 0) {
            blockers.add("cuda-driver-local-empty-buffer-unsupported:" + parameterIndex);
            return;
        }
        long byteSize = primitiveLocal
                ? primitiveArrayByteSize(declaredType, elementCount)
                : arrayByteSize(declaredType, argument, elementCount);
        if (byteSize > Integer.MAX_VALUE) {
            blockers.add("cuda-driver-local-shared-memory-too-large:" + parameterIndex);
        }
    }

    private static int nonLocalKernelParameterSlotCount(List<GpuKernelParameterDescriptor> parameters) {
        int count = 0;
        for (GpuKernelParameterDescriptor parameter : parameters == null ? List.<GpuKernelParameterDescriptor>of() : parameters) {
            if (parameter != null && parameter.access() != GpuKernelParameterAccess.LOCAL) {
                count++;
            }
        }
        return count;
    }

    private static int kernelParameterSlotCount(
            List<GpuKernelParameterDescriptor> parameters,
            CudaLocalSharedMemoryLayout localSharedMemoryLayout
    ) {
        return nonLocalKernelParameterSlotCount(parameters)
                + localSharedMemoryLayout.hiddenOffsetParameterCount();
    }

    private static void validateLocalSharedMemoryLayout(
            List<GpuKernelParameterDescriptor> parameters,
            Object[] arguments,
            List<String> blockers
    ) {
        boolean localBlockerPresent = false;
        for (int index = 0; index < parameters.size(); index++) {
            GpuKernelParameterDescriptor parameter = parameters.get(index);
            if (parameter == null || parameter.access() != GpuKernelParameterAccess.LOCAL) {
                continue;
            }
            String typedPrefix = ":" + index + ":";
            String terminalPrefix = ":" + index;
            localBlockerPresent = blockers.stream().anyMatch(blocker -> blocker.contains(typedPrefix)
                    || blocker.endsWith(terminalPrefix));
            if (localBlockerPresent) {
                break;
            }
        }
        if (localBlockerPresent || CudaLocalSharedMemoryLayout.localParameterCount(parameters) == 0) {
            return;
        }
        CudaLocalSharedMemoryLayout layout = CudaLocalSharedMemoryLayout.from(parameters, arguments);
        if (layout.totalByteSize() > Integer.MAX_VALUE) {
            blockers.add("cuda-driver-local-shared-memory-layout-too-large");
        }
        for (CudaLocalSharedMemoryLayout.Slice slice : layout.slices()) {
            if (slice.byteOffset() > Integer.MAX_VALUE) {
                blockers.add("cuda-driver-local-shared-memory-offset-too-large:" + slice.parameterIndex());
            }
        }
    }

    private static void validateScalarArgument(
            GpuKernelParameterDescriptor parameter,
            int parameterIndex,
            Object argument,
            List<String> blockers
    ) {
        String declaredType = GpuTypeSupport.declaredType(parameter.javaType());
        if (!isSupportedScalarArgumentType(declaredType)) {
            if (CudaValuePacker.structValueCompatible(declaredType, argument)) {
                long byteSize = CudaValuePacker.structValueByteSize(argument);
                if (byteSize > Integer.MAX_VALUE) {
                    blockers.add("cuda-driver-struct-value-too-large:" + parameterIndex + ":" + parameter.javaType());
                }
                return;
            }
            blockers.add("cuda-driver-value-type-unsupported:" + parameterIndex + ":" + parameter.javaType());
            return;
        }
        if (!scalarArgumentCompatible(declaredType, argument)) {
            blockers.add("cuda-driver-scalar-argument-type-mismatch:" + parameterIndex + ":" + declaredType);
        }
    }

    private static boolean requiresDeviceMemory(List<GpuKernelParameterDescriptor> parameters) {
        for (GpuKernelParameterDescriptor parameter : parameters) {
            if (parameter != null
                    && (parameter.access() == GpuKernelParameterAccess.READ_ONLY
                    || parameter.access() == GpuKernelParameterAccess.READ_WRITE)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSupportedScalarArgumentType(String declaredType) {
        if (declaredType == null || declaredType.endsWith("[]")) {
            return false;
        }
        try {
            GpuTypeSupport.scalarByteSize(declaredType);
            return GpuTypeSupport.isSupportedScalarType(declaredType);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean scalarArgumentCompatible(String declaredType, Object argument) {
        if (argument == null) {
            return false;
        }
        if (GpuTypeSupport.isSupportedScalarAliasType(declaredType)) {
            return scalarAliasArgumentCompatible(declaredType, argument);
        }
        return switch (declaredType) {
            case "byte" -> argument instanceof Byte;
            case "short" -> argument instanceof Short;
            case "char" -> argument instanceof Character;
            case "int" -> argument instanceof Integer;
            case "long" -> argument instanceof Long;
            case "float" -> argument instanceof Float;
            case "double" -> argument instanceof Double;
            case "boolean" -> argument instanceof Boolean;
            default -> false;
        };
    }

    private static boolean scalarAliasArgumentCompatible(String declaredType, Object argument) {
        String expectedSimpleName = GpuTypeSupport.simpleTypeName(declaredType);
        Class<?> argumentClass = argument.getClass();
        if (!declaredType.equals(argumentClass.getName()) && !expectedSimpleName.equals(argumentClass.getSimpleName())) {
            return false;
        }
        try {
            Object aliasValue = argumentClass.getField("value").get(argument);
            return scalarArgumentCompatible(GpuTypeSupport.scalarAliasValueType(declaredType), aliasValue);
        } catch (IllegalAccessException | NoSuchFieldException exception) {
            return false;
        }
    }

    private static ByteBuffer allocateScalarArgumentSlot(
            GpuKernelParameterDescriptor parameter,
            int parameterIndex,
            Object argument
    ) {
        String declaredType = GpuTypeSupport.declaredType(parameter.javaType());
        if (CudaValuePacker.structValueCompatible(declaredType, argument)) {
            return CudaValuePacker.packStructValue(argument);
        }
        int byteSize = GpuTypeSupport.scalarByteSize(declaredType);
        ByteBuffer slot = MemoryUtil.memAlloc(byteSize).order(ByteOrder.nativeOrder());
        writeScalarValue(slot, parameterIndex, declaredType, argument);
        return slot;
    }

    private static ByteBuffer allocateLocalOffsetArgumentSlot(CudaLocalSharedMemoryLayout.Slice slice) {
        if (slice.byteOffset() > Integer.MAX_VALUE) {
            throw new DriverCallException(
                    "cuda-driver-local-shared-memory-offset-too-large:" + slice.parameterIndex(),
                    "CUDA local shared-memory offset exceeds Driver API int range for parameter "
                            + slice.parameterName()
            );
        }
        ByteBuffer slot = MemoryUtil.memAlloc(Integer.BYTES).order(ByteOrder.nativeOrder());
        slot.putInt(0, (int) slice.byteOffset());
        return slot;
    }

    private static void writeScalarValue(ByteBuffer slot, int parameterIndex, String declaredType, Object argument) {
        if (GpuTypeSupport.isSupportedScalarAliasType(declaredType)) {
            writeScalarValue(slot, parameterIndex, GpuTypeSupport.scalarAliasValueType(declaredType), scalarAliasValue(argument, parameterIndex));
            return;
        }
        switch (declaredType) {
            case "byte" -> slot.put(0, (Byte) argument);
            case "short" -> slot.putShort(0, (Short) argument);
            case "char" -> slot.putChar(0, (Character) argument);
            case "int" -> slot.putInt(0, (Integer) argument);
            case "long" -> slot.putLong(0, (Long) argument);
            case "float" -> slot.putFloat(0, (Float) argument);
            case "double" -> slot.putDouble(0, (Double) argument);
            case "boolean" -> slot.put(0, (byte) ((Boolean) argument ? 1 : 0));
            default -> throw new DriverCallException(
                    "cuda-driver-scalar-type-unsupported:" + parameterIndex + ":" + declaredType,
                    "CUDA driver scalar VALUE binding does not support parameter type " + declaredType
            );
        }
    }

    private static Object scalarAliasValue(Object argument, int parameterIndex) {
        try {
            Field field = argument.getClass().getField("value");
            return field.get(argument);
        } catch (IllegalAccessException | NoSuchFieldException exception) {
            throw new DriverCallException(
                    "cuda-driver-scalar-argument-type-mismatch:" + parameterIndex + ":" + argument.getClass().getName(),
                    "CUDA driver scalar alias argument must expose a public value field"
            );
        }
    }

    private static CudaDriverDeviceAllocation allocatePrimitiveArray(
            GpuKernelParameterDescriptor parameter,
            int parameterIndex,
            Object values,
            CudaDriverLoadedModule loadedModule,
            MemorySymbols symbols
    ) {
        String declaredType = GpuTypeSupport.declaredType(parameter.javaType());
        int elementCount = arrayLength(values);
        long byteSize = arrayByteSize(declaredType, values, elementCount);
        long devicePointer;
        CudaDriverLibrary.DriverApiInvoker invoker = loadedModule.driverApiInvoker();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer devicePointerOut = stack.mallocPointer(1);
            int allocStatus = invoker.cuMemAlloc(
                    MemoryUtil.memAddress(devicePointerOut),
                    byteSize,
                    symbols.cuMemAlloc()
            );
            if (allocStatus != CudaDriverLibrary.CUDA_SUCCESS) {
                throw new DriverCallException(
                        "cuda-driver-cuMemAlloc-failed:" + allocStatus,
                        "CUDA cuMemAlloc failed for parameter " + parameter.name() + " with code " + allocStatus
                );
            }
            devicePointer = devicePointerOut.get(0);
        }
        int hostElementOffset = hostElementOffset(values);
        ByteBuffer hostBuffer = allocateHostUploadBuffer(declaredType, values, (int) byteSize);
        try {
            int copyStatus = invoker.cuMemcpyHtoD(
                    devicePointer,
                    MemoryUtil.memAddress(hostBuffer),
                    byteSize,
                    symbols.cuMemcpyHtoD()
            );
            if (copyStatus != CudaDriverLibrary.CUDA_SUCCESS) {
                int freeStatus = invoker.cuMemFree(devicePointer, symbols.cuMemFree());
                throw new DriverCallException(
                        "cuda-driver-cuMemcpyHtoD-failed:" + copyStatus,
                        "CUDA cuMemcpyHtoD failed for parameter "
                                + parameter.name()
                                + " with code "
                                + copyStatus
                                + "; cleanup cuMemFree returned "
                                + freeStatus
                );
            }
            return new CudaDriverDeviceAllocation(
                    parameterIndex,
                    parameter.name(),
                    parameter.javaType(),
                    parameter.access(),
                    hostArray(values),
                    hostElementOffset,
                    elementCount,
                    byteSize,
                    devicePointer,
                    true,
                    symbols.cuMemFree(),
                    invoker
            );
        } finally {
            MemoryUtil.memFree(hostBuffer);
        }
    }

    private static ByteBuffer allocateHostUploadBuffer(String declaredType, Object values, int byteSize) {
        Object array = hostArray(values);
        int offset = hostElementOffset(values);
        int elementCount = arrayLength(values);
        if (CudaValuePacker.vectorArrayCompatible(declaredType, array)) {
            return CudaValuePacker.packVectorArray(declaredType, array, offset, elementCount);
        }
        if (CudaValuePacker.structArrayCompatible(declaredType, array)) {
            return CudaValuePacker.packStructArray(array, offset, elementCount);
        }
        ByteBuffer hostBuffer = MemoryUtil.memAlloc(byteSize).order(ByteOrder.nativeOrder());
        writePrimitiveArrayToBuffer(hostBuffer, declaredType, array, offset, elementCount);
        return hostBuffer;
    }

    private static void writePrimitiveArrayToBuffer(
            ByteBuffer buffer,
            String declaredType,
            Object values,
            int offset,
            int elementCount
    ) {
        switch (declaredType) {
            case "byte[]" -> buffer.put((byte[]) values, offset, elementCount);
            case "short[]" -> buffer.asShortBuffer().put((short[]) values, offset, elementCount);
            case "char[]" -> buffer.asCharBuffer().put((char[]) values, offset, elementCount);
            case "int[]" -> buffer.asIntBuffer().put((int[]) values, offset, elementCount);
            case "long[]" -> buffer.asLongBuffer().put((long[]) values, offset, elementCount);
            case "float[]" -> buffer.asFloatBuffer().put((float[]) values, offset, elementCount);
            case "double[]" -> buffer.asDoubleBuffer().put((double[]) values, offset, elementCount);
            default -> throw new DriverCallException(
                    "cuda-driver-buffer-type-unsupported:" + declaredType,
                    "CUDA driver primitive array binding does not support parameter type " + declaredType
            );
        }
    }

    private static MemorySymbols resolveMemorySymbols(CudaDriverLoadedModule loadedModule) {
        ArrayList<String> missing = new ArrayList<>();
        long cuMemAlloc = requiredSymbol(loadedModule, "cuMemAlloc_v2", missing);
        long cuMemcpyHtoD = requiredSymbol(loadedModule, "cuMemcpyHtoD_v2", missing);
        long cuMemFree = requiredSymbol(loadedModule, "cuMemFree_v2", missing);
        return new MemorySymbols(cuMemAlloc, cuMemcpyHtoD, cuMemFree, missing);
    }

    private static long requiredSymbol(CudaDriverLoadedModule loadedModule, String name, List<String> missing) {
        long address = loadedModule.findSymbol(name);
        if (address == 0L) {
            missing.add(name);
        }
        return address;
    }

    private static void closeAllocationsQuietly(List<CudaDriverDeviceAllocation> allocations) {
        for (CudaDriverDeviceAllocation allocation : allocations) {
            try {
                allocation.close();
            } catch (RuntimeException ignored) {
                // Best-effort cleanup after a failed binding attempt.
            }
        }
    }

    private record MemorySymbols(
            long cuMemAlloc,
            long cuMemcpyHtoD,
            long cuMemFree,
            List<String> missingSymbols
    ) {

        private MemorySymbols {
            missingSymbols = missingSymbols == null ? List.of() : List.copyOf(missingSymbols);
        }

        private boolean available() {
            return missingSymbols.isEmpty();
        }

        private List<String> blockers() {
            return java.util.stream.Stream.concat(
                            java.util.stream.Stream.of("cuda-driver-symbols-missing"),
                            missingSymbols.stream().map(symbol -> "cuda-driver-symbol-missing:" + symbol)
                    )
                    .toList();
        }

        private static MemorySymbols noMemoryRequired() {
            return new MemorySymbols(0L, 0L, 0L, List.of());
        }
    }

    private static final class DriverCallException extends RuntimeException {
        private final String blocker;

        private DriverCallException(String blocker, String message) {
            super(message);
            this.blocker = blocker;
        }

        private String blocker() {
            return blocker;
        }
    }
}
