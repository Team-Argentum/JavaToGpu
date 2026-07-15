package net.sixik.ga_utils.javatogpu.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public final class GpuGeneratedLauncherInvoker {

    private GpuGeneratedLauncherInvoker() {
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

    public static GpuKernelDescriptor descriptor(Class<?> ownerClass, String methodName) {
        return launcherBinding(ownerClass, methodName).descriptor();
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

    private static Object invokeLauncherMethod(Class<?> ownerClass, String methodName, String launcherMethodName, Object... arguments) {
        try {
            Class<?> launcherClass = Class.forName(GpuLauncherNaming.launcherClassName(ownerClass, methodName), true, ownerClass.getClassLoader());
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
