package net.sixik.ga_utils.javatogpu.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public final class GpuGeneratedLauncherInvoker {

    private GpuGeneratedLauncherInvoker() {
    }

    public static GeneratedLauncher launcher(Class<?> ownerClass, String methodName) {
        return new GeneratedLauncher(ownerClass, methodName, launcherBinding(ownerClass, methodName));
    }

    public static Object invoke(Class<?> ownerClass, String methodName, Object... arguments) {
        return invokeLauncherMethod(ownerClass, methodName, "invoke", arguments);
    }

    public static Object invokeWithGlobalWorkSize(Class<?> ownerClass, String methodName, long globalWorkSize, Object... arguments) {
        Object[] fullArguments = new Object[arguments.length + 1];
        fullArguments[0] = globalWorkSize;
        System.arraycopy(arguments, 0, fullArguments, 1, arguments.length);
        return invokeLauncherMethod(ownerClass, methodName, "invokeWithGlobalWorkSize", fullArguments);
    }

    public static Object invokeReturningFirst(Class<?> ownerClass, String methodName, Object... arguments) {
        return invokeLauncherMethod(ownerClass, methodName, "invokeReturningFirst", arguments);
    }

    public static <T> T invokeReturningFirstAs(
            Class<T> resultType,
            Class<?> ownerClass,
            String methodName,
            Object... arguments
    ) {
        Class<?> expectedWrapper = validateReturningFirstResultType(resultType, ownerClass, methodName);
        Object value = invokeReturningFirst(ownerClass, methodName, arguments);
        return castReturningFirstValue(resultType, expectedWrapper, ownerClass, methodName, value);
    }

    public static Object invokeReturningFirstWithGlobalWorkSize(
            Class<?> ownerClass,
            String methodName,
            long globalWorkSize,
            Object... arguments
    ) {
        Object[] fullArguments = new Object[arguments.length + 1];
        fullArguments[0] = globalWorkSize;
        System.arraycopy(arguments, 0, fullArguments, 1, arguments.length);
        return invokeLauncherMethod(ownerClass, methodName, "invokeReturningFirst", fullArguments);
    }

    public static <T> T invokeReturningFirstWithGlobalWorkSizeAs(
            Class<T> resultType,
            Class<?> ownerClass,
            String methodName,
            long globalWorkSize,
            Object... arguments
    ) {
        Class<?> expectedWrapper = validateReturningFirstResultType(resultType, ownerClass, methodName);
        Object value = invokeReturningFirstWithGlobalWorkSize(ownerClass, methodName, globalWorkSize, arguments);
        return castReturningFirstValue(resultType, expectedWrapper, ownerClass, methodName, value);
    }

    public static Object invokeReturningFirstWithConfig(
            Class<?> ownerClass,
            String methodName,
            GpuExecutionConfig executionConfig,
            Object... arguments
    ) {
        Object[] fullArguments = new Object[arguments.length + 1];
        fullArguments[0] = executionConfig;
        System.arraycopy(arguments, 0, fullArguments, 1, arguments.length);
        return invokeLauncherMethod(ownerClass, methodName, "invokeReturningFirstWithConfig", fullArguments);
    }

    public static <T> T invokeReturningFirstWithConfigAs(
            Class<T> resultType,
            Class<?> ownerClass,
            String methodName,
            GpuExecutionConfig executionConfig,
            Object... arguments
    ) {
        Class<?> expectedWrapper = validateReturningFirstResultType(resultType, ownerClass, methodName);
        Object value = invokeReturningFirstWithConfig(ownerClass, methodName, executionConfig, arguments);
        return castReturningFirstValue(resultType, expectedWrapper, ownerClass, methodName, value);
    }

    public static Object invokeReturningFirstWithCompileOptions(
            Class<?> ownerClass,
            String methodName,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        Object[] fullArguments = new Object[arguments.length + 1];
        fullArguments[0] = compileOptions;
        System.arraycopy(arguments, 0, fullArguments, 1, arguments.length);
        return invokeLauncherMethod(ownerClass, methodName, "invokeReturningFirstWithCompileOptions", fullArguments);
    }

    public static <T> T invokeReturningFirstWithCompileOptionsAs(
            Class<T> resultType,
            Class<?> ownerClass,
            String methodName,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        Class<?> expectedWrapper = validateReturningFirstResultType(resultType, ownerClass, methodName);
        Object value = invokeReturningFirstWithCompileOptions(ownerClass, methodName, compileOptions, arguments);
        return castReturningFirstValue(resultType, expectedWrapper, ownerClass, methodName, value);
    }

    public static Object invokeReturningFirstWithGlobalWorkSizeAndCompileOptions(
            Class<?> ownerClass,
            String methodName,
            long globalWorkSize,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        Object[] fullArguments = new Object[arguments.length + 2];
        fullArguments[0] = globalWorkSize;
        fullArguments[1] = compileOptions;
        System.arraycopy(arguments, 0, fullArguments, 2, arguments.length);
        return invokeLauncherMethod(ownerClass, methodName, "invokeReturningFirstWithGlobalWorkSizeAndCompileOptions", fullArguments);
    }

    public static <T> T invokeReturningFirstWithGlobalWorkSizeAndCompileOptionsAs(
            Class<T> resultType,
            Class<?> ownerClass,
            String methodName,
            long globalWorkSize,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        Class<?> expectedWrapper = validateReturningFirstResultType(resultType, ownerClass, methodName);
        Object value = invokeReturningFirstWithGlobalWorkSizeAndCompileOptions(
                ownerClass,
                methodName,
                globalWorkSize,
                compileOptions,
                arguments
        );
        return castReturningFirstValue(resultType, expectedWrapper, ownerClass, methodName, value);
    }

    public static Object invokeReturningFirstWithConfigAndCompileOptions(
            Class<?> ownerClass,
            String methodName,
            GpuExecutionConfig executionConfig,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        Object[] fullArguments = new Object[arguments.length + 2];
        fullArguments[0] = executionConfig;
        fullArguments[1] = compileOptions;
        System.arraycopy(arguments, 0, fullArguments, 2, arguments.length);
        return invokeLauncherMethod(ownerClass, methodName, "invokeReturningFirstWithConfigAndCompileOptions", fullArguments);
    }

    public static <T> T invokeReturningFirstWithConfigAndCompileOptionsAs(
            Class<T> resultType,
            Class<?> ownerClass,
            String methodName,
            GpuExecutionConfig executionConfig,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        Class<?> expectedWrapper = validateReturningFirstResultType(resultType, ownerClass, methodName);
        Object value = invokeReturningFirstWithConfigAndCompileOptions(
                ownerClass,
                methodName,
                executionConfig,
                compileOptions,
                arguments
        );
        return castReturningFirstValue(resultType, expectedWrapper, ownerClass, methodName, value);
    }

    public static Object invokeReturningFirstWithStandardBackendAndDevice(
            Class<?> ownerClass,
            String methodName,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        try (GpuRuntimeScope ignored = GpuRuntime.useStandardBackendAndDevice(compileOptions)) {
            return invokeReturningFirstWithCompileOptions(ownerClass, methodName, compileOptions, arguments);
        }
    }

    public static <T> T invokeReturningFirstWithStandardBackendAndDeviceAs(
            Class<T> resultType,
            Class<?> ownerClass,
            String methodName,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        Class<?> expectedWrapper = validateReturningFirstResultType(resultType, ownerClass, methodName);
        Object value = invokeReturningFirstWithStandardBackendAndDevice(ownerClass, methodName, compileOptions, arguments);
        return castReturningFirstValue(resultType, expectedWrapper, ownerClass, methodName, value);
    }

    public static Object invokeReturningFirstWithGlobalWorkSizeAndStandardBackendAndDevice(
            Class<?> ownerClass,
            String methodName,
            long globalWorkSize,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        try (GpuRuntimeScope ignored = GpuRuntime.useStandardBackendAndDevice(compileOptions)) {
            return invokeReturningFirstWithGlobalWorkSizeAndCompileOptions(
                    ownerClass,
                    methodName,
                    globalWorkSize,
                    compileOptions,
                    arguments
            );
        }
    }

    public static <T> T invokeReturningFirstWithGlobalWorkSizeAndStandardBackendAndDeviceAs(
            Class<T> resultType,
            Class<?> ownerClass,
            String methodName,
            long globalWorkSize,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        Class<?> expectedWrapper = validateReturningFirstResultType(resultType, ownerClass, methodName);
        Object value = invokeReturningFirstWithGlobalWorkSizeAndStandardBackendAndDevice(
                ownerClass,
                methodName,
                globalWorkSize,
                compileOptions,
                arguments
        );
        return castReturningFirstValue(resultType, expectedWrapper, ownerClass, methodName, value);
    }

    public static Object invokeReturningFirstWithConfigAndStandardBackendAndDevice(
            Class<?> ownerClass,
            String methodName,
            GpuExecutionConfig executionConfig,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        try (GpuRuntimeScope ignored = GpuRuntime.useStandardBackendAndDevice(compileOptions)) {
            return invokeReturningFirstWithConfigAndCompileOptions(
                    ownerClass,
                    methodName,
                    executionConfig,
                    compileOptions,
                    arguments
            );
        }
    }

    public static <T> T invokeReturningFirstWithConfigAndStandardBackendAndDeviceAs(
            Class<T> resultType,
            Class<?> ownerClass,
            String methodName,
            GpuExecutionConfig executionConfig,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        Class<?> expectedWrapper = validateReturningFirstResultType(resultType, ownerClass, methodName);
        Object value = invokeReturningFirstWithConfigAndStandardBackendAndDevice(
                ownerClass,
                methodName,
                executionConfig,
                compileOptions,
                arguments
        );
        return castReturningFirstValue(resultType, expectedWrapper, ownerClass, methodName, value);
    }

    public static Object invokeWith3DWorkSize(
            Class<?> ownerClass,
            String methodName,
            long globalX,
            long globalY,
            long globalZ,
            Object... arguments
    ) {
        return invokeWithConfig(ownerClass, methodName, GpuExecutionConfig.threeDimensional(globalX, globalY, globalZ), arguments);
    }

    public static Object invokeWithConfig(Class<?> ownerClass, String methodName, GpuExecutionConfig executionConfig, Object... arguments) {
        LauncherBinding binding = launcherBinding(ownerClass, methodName);
        GpuRuntime.invokeFromGeneratedLauncher(binding.launcherClass(), executionConfig, binding.descriptor(), arguments);
        return null;
    }

    public static Object invokeWithCompileOptions(
            Class<?> ownerClass,
            String methodName,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        LauncherBinding binding = launcherBinding(ownerClass, methodName);
        GpuRuntime.invokeFromGeneratedLauncherWithCompileOptions(
                binding.launcherClass(),
                compileOptions,
                binding.descriptor(),
                arguments
        );
        return null;
    }

    public static Object invokeWithGlobalWorkSizeAndCompileOptions(
            Class<?> ownerClass,
            String methodName,
            long globalWorkSize,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        LauncherBinding binding = launcherBinding(ownerClass, methodName);
        GpuRuntime.invokeFromGeneratedLauncherWithCompileOptions(
                binding.launcherClass(),
                globalWorkSize,
                compileOptions,
                binding.descriptor(),
                arguments
        );
        return null;
    }

    public static Object invokeWith3DWorkSizeAndCompileOptions(
            Class<?> ownerClass,
            String methodName,
            long globalX,
            long globalY,
            long globalZ,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        return invokeWithConfigAndCompileOptions(
                ownerClass,
                methodName,
                GpuExecutionConfig.threeDimensional(globalX, globalY, globalZ),
                compileOptions,
                arguments
        );
    }

    public static Object invokeWithConfigAndCompileOptions(
            Class<?> ownerClass,
            String methodName,
            GpuExecutionConfig executionConfig,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        LauncherBinding binding = launcherBinding(ownerClass, methodName);
        GpuRuntime.invokeFromGeneratedLauncherWithCompileOptions(
                binding.launcherClass(),
                executionConfig,
                compileOptions,
                binding.descriptor(),
                arguments
        );
        return null;
    }

    public static Object invokeWithStandardBackendAndDevice(
            Class<?> ownerClass,
            String methodName,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        try (GpuRuntimeScope ignored = GpuRuntime.useStandardBackendAndDevice(compileOptions)) {
            return invokeWithCompileOptions(ownerClass, methodName, compileOptions, arguments);
        }
    }

    public static Object invokeWithGlobalWorkSizeAndStandardBackendAndDevice(
            Class<?> ownerClass,
            String methodName,
            long globalWorkSize,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        try (GpuRuntimeScope ignored = GpuRuntime.useStandardBackendAndDevice(compileOptions)) {
            return invokeWithGlobalWorkSizeAndCompileOptions(
                    ownerClass,
                    methodName,
                    globalWorkSize,
                    compileOptions,
                    arguments
            );
        }
    }

    public static Object invokeWith3DWorkSizeAndStandardBackendAndDevice(
            Class<?> ownerClass,
            String methodName,
            long globalX,
            long globalY,
            long globalZ,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        return invokeWithConfigAndStandardBackendAndDevice(
                ownerClass,
                methodName,
                GpuExecutionConfig.threeDimensional(globalX, globalY, globalZ),
                compileOptions,
                arguments
        );
    }

    public static Object invokeWithConfigAndStandardBackendAndDevice(
            Class<?> ownerClass,
            String methodName,
            GpuExecutionConfig executionConfig,
            GpuRuntimeCompileOptions compileOptions,
            Object... arguments
    ) {
        try (GpuRuntimeScope ignored = GpuRuntime.useStandardBackendAndDevice(compileOptions)) {
            return invokeWithConfigAndCompileOptions(
                    ownerClass,
                    methodName,
                    executionConfig,
                    compileOptions,
                    arguments
            );
        }
    }

    public static GpuKernelDescriptor descriptor(Class<?> ownerClass, String methodName) {
        return launcherBinding(ownerClass, methodName).descriptor();
    }

    public static GpuGeneratedLauncherReturnValueConvenienceReport returnValueConvenience(
            Class<?> ownerClass,
            String methodName
    ) {
        Class<?> launcherClass = launcherBinding(ownerClass, methodName).launcherClass();
        return returnValueConvenience(launcherClass, ownerClass, methodName);
    }

    private static GpuGeneratedLauncherReturnValueConvenienceReport returnValueConvenience(
            Class<?> launcherClass,
            Class<?> ownerClass,
            String methodName
    ) {
        try {
            return new GpuGeneratedLauncherReturnValueConvenienceReport(
                    launcherClass.getField("RETURN_VALUE_CONVENIENCE_AVAILABLE").getBoolean(null),
                    launcherStringField(launcherClass, "RETURN_VALUE_CONVENIENCE_STATUS"),
                    launcherStringField(launcherClass, "RETURN_VALUE_CONVENIENCE_REASON"),
                    launcherStringField(launcherClass, "RETURN_VALUE_CONVENIENCE_OUTPUT_PARAMETER"),
                    launcherStringField(launcherClass, "RETURN_VALUE_CONVENIENCE_OUTPUT_TYPE"),
                    launcherStringField(launcherClass, "RETURN_VALUE_CONVENIENCE_RETURN_TYPE")
            );
        } catch (NoSuchFieldException exception) {
            return new GpuGeneratedLauncherReturnValueConvenienceReport(
                    false,
                    "unavailable",
                    "metadata-not-generated",
                    "",
                    "",
                    ""
            );
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Generated GPU launcher return-value convenience metadata is not accessible for "
                            + ownerClass.getName()
                            + "#"
                            + methodName,
                    exception
            );
        }
    }

    private static LauncherBinding launcherBinding(Class<?> ownerClass, String methodName) {
        try {
            Class<?> launcherClass = Class.forName(
                    GpuLauncherNaming.launcherClassName(ownerClass, methodName),
                    true,
                    ownerClass.getClassLoader()
            );
            return new LauncherBinding(
                    launcherClass,
                    (GpuKernelDescriptor) launcherClass.getField("KERNEL_DESCRIPTOR").get(null)
            );
        } catch (ClassNotFoundException exception) {
            throw new IllegalArgumentException(
                    "Generated GPU launcher not found for "
                            + ownerClass.getName()
                            + "#"
                            + methodName
                            + " at "
                            + GpuLauncherNaming.launcherClassName(ownerClass, methodName),
                    exception
            );
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Generated GPU launcher KERNEL_DESCRIPTOR is not accessible for "
                            + ownerClass.getName()
                            + "#"
                            + methodName,
                    exception
            );
        }
    }

    private record LauncherBinding(Class<?> launcherClass, GpuKernelDescriptor descriptor) {
    }

    public static final class GeneratedLauncher {

        private final Class<?> ownerClass;
        private final String methodName;
        private final LauncherBinding binding;
        private final GpuGeneratedLauncherReturnValueConvenienceReport returnValueConvenience;

        public GeneratedLauncher(Class<?> ownerClass, String methodName) {
            this(ownerClass, methodName, launcherBinding(ownerClass, methodName));
        }

        private GeneratedLauncher(Class<?> ownerClass, String methodName, LauncherBinding binding) {
            if (ownerClass == null) {
                throw new NullPointerException("ownerClass");
            }
            if (methodName == null || methodName.isBlank()) {
                throw new IllegalArgumentException("methodName must not be blank");
            }
            if (binding == null) {
                throw new NullPointerException("binding");
            }
            this.ownerClass = ownerClass;
            this.methodName = methodName;
            this.binding = binding;
            this.returnValueConvenience = GpuGeneratedLauncherInvoker.returnValueConvenience(
                    binding.launcherClass(),
                    ownerClass,
                    methodName
            );
        }

        public Class<?> ownerClass() {
            return ownerClass;
        }

        public String methodName() {
            return methodName;
        }

        public Class<?> launcherClass() {
            return binding.launcherClass();
        }

        public Object invoke(Object... arguments) {
            return invokeGenerated("invoke", arguments);
        }

        public Object invokeWithGlobalWorkSize(long globalWorkSize, Object... arguments) {
            Object[] fullArguments = new Object[arguments.length + 1];
            fullArguments[0] = globalWorkSize;
            System.arraycopy(arguments, 0, fullArguments, 1, arguments.length);
            return invokeGenerated("invokeWithGlobalWorkSize", fullArguments);
        }

        public Object invokeWith3DWorkSize(long globalX, long globalY, long globalZ, Object... arguments) {
            Object[] fullArguments = new Object[arguments.length + 3];
            fullArguments[0] = globalX;
            fullArguments[1] = globalY;
            fullArguments[2] = globalZ;
            System.arraycopy(arguments, 0, fullArguments, 3, arguments.length);
            return invokeGenerated("invokeWith3DWorkSize", fullArguments);
        }

        public Object invokeWithConfig(GpuExecutionConfig executionConfig, Object... arguments) {
            Object[] fullArguments = new Object[arguments.length + 1];
            fullArguments[0] = executionConfig;
            System.arraycopy(arguments, 0, fullArguments, 1, arguments.length);
            return invokeGenerated("invokeWithConfig", fullArguments);
        }

        public Object invokeWithCompileOptions(GpuRuntimeCompileOptions compileOptions, Object... arguments) {
            Object[] fullArguments = new Object[arguments.length + 1];
            fullArguments[0] = compileOptions;
            System.arraycopy(arguments, 0, fullArguments, 1, arguments.length);
            return invokeGenerated("invokeWithCompileOptions", fullArguments);
        }

        public Object invokeWithGlobalWorkSizeAndCompileOptions(
                long globalWorkSize,
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            Object[] fullArguments = new Object[arguments.length + 2];
            fullArguments[0] = globalWorkSize;
            fullArguments[1] = compileOptions;
            System.arraycopy(arguments, 0, fullArguments, 2, arguments.length);
            return invokeGenerated("invokeWithGlobalWorkSizeAndCompileOptions", fullArguments);
        }

        public Object invokeWith3DWorkSizeAndCompileOptions(
                long globalX,
                long globalY,
                long globalZ,
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            Object[] fullArguments = new Object[arguments.length + 4];
            fullArguments[0] = globalX;
            fullArguments[1] = globalY;
            fullArguments[2] = globalZ;
            fullArguments[3] = compileOptions;
            System.arraycopy(arguments, 0, fullArguments, 4, arguments.length);
            return invokeGenerated("invokeWith3DWorkSizeAndCompileOptions", fullArguments);
        }

        public Object invokeWithConfigAndCompileOptions(
                GpuExecutionConfig executionConfig,
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            Object[] fullArguments = new Object[arguments.length + 2];
            fullArguments[0] = executionConfig;
            fullArguments[1] = compileOptions;
            System.arraycopy(arguments, 0, fullArguments, 2, arguments.length);
            return invokeGenerated("invokeWithConfigAndCompileOptions", fullArguments);
        }

        public Object invokeWithStandardBackendAndDevice(
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            return GpuGeneratedLauncherInvoker.invokeWithStandardBackendAndDevice(
                    ownerClass,
                    methodName,
                    compileOptions,
                    arguments
            );
        }

        public Object invokeWithGlobalWorkSizeAndStandardBackendAndDevice(
                long globalWorkSize,
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            return GpuGeneratedLauncherInvoker.invokeWithGlobalWorkSizeAndStandardBackendAndDevice(
                    ownerClass,
                    methodName,
                    globalWorkSize,
                    compileOptions,
                    arguments
            );
        }

        public Object invokeWith3DWorkSizeAndStandardBackendAndDevice(
                long globalX,
                long globalY,
                long globalZ,
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            return GpuGeneratedLauncherInvoker.invokeWith3DWorkSizeAndStandardBackendAndDevice(
                    ownerClass,
                    methodName,
                    globalX,
                    globalY,
                    globalZ,
                    compileOptions,
                    arguments
            );
        }

        public Object invokeWithConfigAndStandardBackendAndDevice(
                GpuExecutionConfig executionConfig,
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            return GpuGeneratedLauncherInvoker.invokeWithConfigAndStandardBackendAndDevice(
                    ownerClass,
                    methodName,
                    executionConfig,
                    compileOptions,
                    arguments
            );
        }

        public Object invokeReturningFirst(Object... arguments) {
            return invokeGenerated("invokeReturningFirst", arguments);
        }

        public <T> T invokeReturningFirstAs(Class<T> resultType, Object... arguments) {
            Class<?> expectedWrapper = validateReturningFirstResultType(
                    resultType,
                    returnValueConvenience(),
                    ownerClass,
                    methodName
            );
            Object value = invokeReturningFirst(arguments);
            return castReturningFirstValue(resultType, expectedWrapper, ownerClass, methodName, value);
        }

        public Object invokeReturningFirstWithGlobalWorkSize(long globalWorkSize, Object... arguments) {
            Object[] fullArguments = new Object[arguments.length + 1];
            fullArguments[0] = globalWorkSize;
            System.arraycopy(arguments, 0, fullArguments, 1, arguments.length);
            return invokeGenerated("invokeReturningFirst", fullArguments);
        }

        public <T> T invokeReturningFirstWithGlobalWorkSizeAs(
                Class<T> resultType,
                long globalWorkSize,
                Object... arguments
        ) {
            Class<?> expectedWrapper = validateReturningFirstResultType(
                    resultType,
                    returnValueConvenience(),
                    ownerClass,
                    methodName
            );
            Object value = invokeReturningFirstWithGlobalWorkSize(globalWorkSize, arguments);
            return castReturningFirstValue(resultType, expectedWrapper, ownerClass, methodName, value);
        }

        public Object invokeReturningFirstWithConfig(GpuExecutionConfig executionConfig, Object... arguments) {
            Object[] fullArguments = new Object[arguments.length + 1];
            fullArguments[0] = executionConfig;
            System.arraycopy(arguments, 0, fullArguments, 1, arguments.length);
            return invokeGenerated("invokeReturningFirstWithConfig", fullArguments);
        }

        public <T> T invokeReturningFirstWithConfigAs(
                Class<T> resultType,
                GpuExecutionConfig executionConfig,
                Object... arguments
        ) {
            Class<?> expectedWrapper = validateReturningFirstResultType(
                    resultType,
                    returnValueConvenience(),
                    ownerClass,
                    methodName
            );
            Object value = invokeReturningFirstWithConfig(executionConfig, arguments);
            return castReturningFirstValue(resultType, expectedWrapper, ownerClass, methodName, value);
        }

        public Object invokeReturningFirstWithCompileOptions(
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            Object[] fullArguments = new Object[arguments.length + 1];
            fullArguments[0] = compileOptions;
            System.arraycopy(arguments, 0, fullArguments, 1, arguments.length);
            return invokeGenerated("invokeReturningFirstWithCompileOptions", fullArguments);
        }

        public <T> T invokeReturningFirstWithCompileOptionsAs(
                Class<T> resultType,
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            Class<?> expectedWrapper = validateReturningFirstResultType(
                    resultType,
                    returnValueConvenience(),
                    ownerClass,
                    methodName
            );
            Object value = invokeReturningFirstWithCompileOptions(compileOptions, arguments);
            return castReturningFirstValue(resultType, expectedWrapper, ownerClass, methodName, value);
        }

        public Object invokeReturningFirstWithGlobalWorkSizeAndCompileOptions(
                long globalWorkSize,
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            Object[] fullArguments = new Object[arguments.length + 2];
            fullArguments[0] = globalWorkSize;
            fullArguments[1] = compileOptions;
            System.arraycopy(arguments, 0, fullArguments, 2, arguments.length);
            return invokeGenerated("invokeReturningFirstWithGlobalWorkSizeAndCompileOptions", fullArguments);
        }

        public <T> T invokeReturningFirstWithGlobalWorkSizeAndCompileOptionsAs(
                Class<T> resultType,
                long globalWorkSize,
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            Class<?> expectedWrapper = validateReturningFirstResultType(
                    resultType,
                    returnValueConvenience(),
                    ownerClass,
                    methodName
            );
            Object value = invokeReturningFirstWithGlobalWorkSizeAndCompileOptions(globalWorkSize, compileOptions, arguments);
            return castReturningFirstValue(resultType, expectedWrapper, ownerClass, methodName, value);
        }

        public Object invokeReturningFirstWithConfigAndCompileOptions(
                GpuExecutionConfig executionConfig,
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            Object[] fullArguments = new Object[arguments.length + 2];
            fullArguments[0] = executionConfig;
            fullArguments[1] = compileOptions;
            System.arraycopy(arguments, 0, fullArguments, 2, arguments.length);
            return invokeGenerated("invokeReturningFirstWithConfigAndCompileOptions", fullArguments);
        }

        public <T> T invokeReturningFirstWithConfigAndCompileOptionsAs(
                Class<T> resultType,
                GpuExecutionConfig executionConfig,
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            Class<?> expectedWrapper = validateReturningFirstResultType(
                    resultType,
                    returnValueConvenience(),
                    ownerClass,
                    methodName
            );
            Object value = invokeReturningFirstWithConfigAndCompileOptions(executionConfig, compileOptions, arguments);
            return castReturningFirstValue(resultType, expectedWrapper, ownerClass, methodName, value);
        }

        public Object invokeReturningFirstWithStandardBackendAndDevice(
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            return GpuGeneratedLauncherInvoker.invokeReturningFirstWithStandardBackendAndDevice(
                    ownerClass,
                    methodName,
                    compileOptions,
                    arguments
            );
        }

        public <T> T invokeReturningFirstWithStandardBackendAndDeviceAs(
                Class<T> resultType,
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            return GpuGeneratedLauncherInvoker.invokeReturningFirstWithStandardBackendAndDeviceAs(
                    resultType,
                    ownerClass,
                    methodName,
                    compileOptions,
                    arguments
            );
        }

        public Object invokeReturningFirstWithGlobalWorkSizeAndStandardBackendAndDevice(
                long globalWorkSize,
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            return GpuGeneratedLauncherInvoker.invokeReturningFirstWithGlobalWorkSizeAndStandardBackendAndDevice(
                    ownerClass,
                    methodName,
                    globalWorkSize,
                    compileOptions,
                    arguments
            );
        }

        public <T> T invokeReturningFirstWithGlobalWorkSizeAndStandardBackendAndDeviceAs(
                Class<T> resultType,
                long globalWorkSize,
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            return GpuGeneratedLauncherInvoker.invokeReturningFirstWithGlobalWorkSizeAndStandardBackendAndDeviceAs(
                    resultType,
                    ownerClass,
                    methodName,
                    globalWorkSize,
                    compileOptions,
                    arguments
            );
        }

        public Object invokeReturningFirstWithConfigAndStandardBackendAndDevice(
                GpuExecutionConfig executionConfig,
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            return GpuGeneratedLauncherInvoker.invokeReturningFirstWithConfigAndStandardBackendAndDevice(
                    ownerClass,
                    methodName,
                    executionConfig,
                    compileOptions,
                    arguments
            );
        }

        public <T> T invokeReturningFirstWithConfigAndStandardBackendAndDeviceAs(
                Class<T> resultType,
                GpuExecutionConfig executionConfig,
                GpuRuntimeCompileOptions compileOptions,
                Object... arguments
        ) {
            return GpuGeneratedLauncherInvoker.invokeReturningFirstWithConfigAndStandardBackendAndDeviceAs(
                    resultType,
                    ownerClass,
                    methodName,
                    executionConfig,
                    compileOptions,
                    arguments
            );
        }

        public GpuKernelDescriptor descriptor() {
            return binding.descriptor();
        }

        public GpuGeneratedLauncherReturnValueConvenienceReport returnValueConvenience() {
            return returnValueConvenience;
        }

        private Object invokeGenerated(String launcherMethodName, Object... arguments) {
            return invokeLauncherMethod(binding.launcherClass(), ownerClass, methodName, launcherMethodName, arguments);
        }
    }

    private static String launcherStringField(Class<?> launcherClass, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        Object value = launcherClass.getField(fieldName).get(null);
        return value == null ? "" : value.toString();
    }

    private static <T> Class<?> validateReturningFirstResultType(
            Class<T> resultType,
            Class<?> ownerClass,
            String methodName
    ) {
        return validateReturningFirstResultType(
                resultType,
                returnValueConvenience(ownerClass, methodName),
                ownerClass,
                methodName
        );
    }

    private static <T> Class<?> validateReturningFirstResultType(
            Class<T> resultType,
            GpuGeneratedLauncherReturnValueConvenienceReport report,
            Class<?> ownerClass,
            String methodName
    ) {
        if (resultType == null) {
            throw new NullPointerException("resultType");
        }
        if (!report.available()) {
            throw new IllegalStateException(
                    "Return-first convenience is not available for "
                            + ownerClass.getName()
                            + "#"
                            + methodName
                            + ": "
                            + report.summary()
            );
        }
        Class<?> expectedWrapper = boxedType(resultType);
        Class<?> actualWrapper = boxedTypeName(report.returnType());
        if (actualWrapper != null && !expectedWrapper.equals(actualWrapper)) {
            throw new IllegalArgumentException(
                    "Return-first convenience for "
                            + ownerClass.getName()
                            + "#"
                            + methodName
                            + " returns "
                            + report.returnType()
                            + ", not "
                            + resultType.getTypeName()
            );
        }
        return expectedWrapper;
    }

    private static <T> T castReturningFirstValue(
            Class<T> resultType,
            Class<?> expectedWrapper,
            Class<?> ownerClass,
            String methodName,
            Object value
    ) {
        if (value == null) {
            return null;
        }
        if (!expectedWrapper.isInstance(value)) {
            throw new ClassCastException(
                    "Generated GPU launcher returned "
                            + value.getClass().getName()
                            + " for "
                            + ownerClass.getName()
                            + "#"
                            + methodName
                            + ", expected "
                            + expectedWrapper.getName()
            );
        }
        @SuppressWarnings("unchecked")
        T typedValue = (T) value;
        return typedValue;
    }

    private static Class<?> boxedType(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        return switch (type.getName()) {
            case "boolean" -> Boolean.class;
            case "byte" -> Byte.class;
            case "short" -> Short.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            case "char" -> Character.class;
            default -> type;
        };
    }

    private static Class<?> boxedTypeName(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return null;
        }
        return switch (typeName) {
            case "boolean" -> Boolean.class;
            case "byte" -> Byte.class;
            case "short" -> Short.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            case "char" -> Character.class;
            default -> null;
        };
    }

    private static Object invokeLauncherMethod(Class<?> ownerClass, String methodName, String launcherMethodName, Object... arguments) {
        try {
            Class<?> launcherClass = Class.forName(GpuLauncherNaming.launcherClassName(ownerClass, methodName), true, ownerClass.getClassLoader());
            return invokeLauncherMethod(launcherClass, ownerClass, methodName, launcherMethodName, arguments);
        } catch (ClassNotFoundException exception) {
            throw new IllegalArgumentException(
                    "Generated GPU launcher not found for "
                            + ownerClass.getName()
                            + "#"
                            + methodName
                            + " at "
                            + GpuLauncherNaming.launcherClassName(ownerClass, methodName),
                    exception
            );
        }
    }

    private static Object invokeLauncherMethod(
            Class<?> launcherClass,
            Class<?> ownerClass,
            String methodName,
            String launcherMethodName,
            Object... arguments
    ) {
        try {
            Method invokeMethod = Arrays.stream(launcherClass.getMethods())
                    .filter(method -> method.getName().equals(launcherMethodName))
                    .filter(method -> Modifier.isStatic(method.getModifiers()))
                    .filter(method -> parametersMatch(method.getParameterTypes(), arguments))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No generated GPU launcher " + launcherMethodName + "(...) overload matches "
                                    + ownerClass.getName()
                                    + "#"
                                    + methodName
                    ));
            return invokeMethod.invoke(null, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Generated GPU launcher " + launcherMethodName + "(...) is not accessible for "
                            + ownerClass.getName()
                            + "#"
                            + methodName,
                    exception
            );
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(
                    "Generated GPU launcher " + launcherMethodName + "(...) invocation failed for "
                            + ownerClass.getName()
                            + "#"
                            + methodName,
                    cause
            );
        }
    }

    private static boolean parametersMatch(Class<?>[] parameterTypes, Object[] arguments) {
        if (parameterTypes.length != arguments.length) {
            return false;
        }

        for (int i = 0; i < parameterTypes.length; i++) {
            if (!parameterMatches(parameterTypes[i], arguments[i])) {
                return false;
            }
        }

        return true;
    }

    private static boolean parameterMatches(Class<?> parameterType, Object argument) {
        if (argument == null) {
            return !parameterType.isPrimitive();
        }

        if (parameterType.isPrimitive()) {
            return switch (parameterType.getName()) {
                case "boolean" -> argument instanceof Boolean;
                case "byte" -> argument instanceof Byte;
                case "short" -> argument instanceof Short;
                case "int" -> argument instanceof Integer;
                case "long" -> argument instanceof Long;
                case "float" -> argument instanceof Float;
                case "double" -> argument instanceof Double;
                case "char" -> argument instanceof Character;
                default -> false;
            };
        }

        return parameterType.isInstance(argument);
    }
}
