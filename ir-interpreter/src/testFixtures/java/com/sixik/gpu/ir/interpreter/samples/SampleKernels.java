package com.sixik.gpu.ir.interpreter.samples;

import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Каталог образцов-ядер: настоящих Java-методов {@code @Kernel}, которые annotation processor {@code frontend-java}
 * собрал в IR при компиляции этих фикстур. IR читается из ресурсов {@code META-INF/gpu/kernels.idx} и
 * {@code META-INF/gpu/ir/<id>.gir} - так же, как его прочитает рантайм, поэтому тесты проверяют весь путь
 * «исходник &rarr; фронтенд &rarr; ресурс &rarr; чтение».
 *
 * <p>Образцы общие для тестов {@code ir-interpreter} (сверка с JVM) и {@code codegen-opencl} (golden-файлы и
 * сверка с интерпретатором).</p>
 */
public final class SampleKernels {

    private static final String INDEX = "/META-INF/gpu/kernels.idx";

    private SampleKernels() {
    }

    /**
     * Ядро образца.
     *
     * @param owner  класс образца
     * @param method оригинальный Java-метод
     * @param module IR-модуль ядра
     */
    public record Kernel(Class<?> owner, Method method, IrModule module) {

        /**
         * @return имя вида {@code "IntKernels.arithmetic"} для сообщений и имён golden-файлов
         */
        public String label() {
            return owner.getSimpleName() + "." + method.getName();
        }
    }

    /**
     * @param cls    класс образца
     * @param method имя ядра (перегрузок у образцов нет)
     * @return метод и его IR-модуль
     * @throws IllegalStateException если ядра нет в индексе или ресурс не читается
     */
    public static Kernel kernel(Class<?> cls, String method) {
        Method found = null;
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(method)) {
                if (found != null) {
                    throw new IllegalStateException("the sample has several methods " + method);
                }
                found = m;
            }
        }
        if (found == null) {
            throw new IllegalStateException("no method " + method + " in " + cls.getName());
        }
        String descriptor = MethodType.methodType(found.getReturnType(), found.getParameterTypes())
                .toMethodDescriptorString();
        String key = cls.getName() + "\t" + method + "\t" + descriptor;
        for (String[] row : index()) {
            if ((row[0] + "\t" + row[1] + "\t" + row[2]).equals(key)) {
                return new Kernel(cls, found, read(row[3]));
            }
        }
        throw new IllegalStateException("no kernel in kernels.idx " + cls.getName() + "." + method + descriptor);
    }

    /**
     * Все ядра образцов в порядке индекса (по классу и методу).
     *
     * @return список ядер
     */
    public static List<Kernel> all() {
        List<Kernel> out = new ArrayList<>();
        for (String[] row : index()) {
            Class<?> cls;
            try {
                cls = Class.forName(row[0]);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
            out.add(kernel(cls, row[1]));
        }
        return out;
    }

    private static List<String[]> index() {
        try (InputStream in = SampleKernels.class.getResourceAsStream(INDEX)) {
            if (in == null) {
                throw new IllegalStateException("no " + INDEX + ": the samples were not processed by the frontend-java processor");
            }
            List<String[]> rows = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank()) {
                    rows.add(line.split("\t"));
                }
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static IrModule read(String id) {
        try (InputStream in = SampleKernels.class.getResourceAsStream("/META-INF/gpu/ir/" + id + ".gir")) {
            if (in == null) {
                throw new IllegalStateException("no IR resource " + id);
            }
            Result<IrModule> r = IrBinary.read(in.readAllBytes());
            if (!r.isOk()) {
                throw new IllegalStateException("IR is unreadable: " + r.diagnostics());
            }
            return r.value();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
