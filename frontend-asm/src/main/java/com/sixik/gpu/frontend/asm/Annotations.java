package com.sixik.gpu.frontend.asm;

import org.objectweb.asm.tree.AnnotationNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Аннотации из class-файла (retention {@code CLASS}, атрибут {@code RuntimeInvisibleAnnotations}). Имена аннотаций
 * API - данные (дескрипторы), модуль {@code api} фронтенду байткода не нужен (REWORK_ARCHITECTURE.md, 17.2).
 */
final class Annotations {

    static final String KERNEL = "Lcom/sixik/gpu/api/annotations/Kernel;";
    static final String REDUCTION = "Lcom/sixik/gpu/api/annotations/Reduction;";
    static final String WORK_GROUP = "Lcom/sixik/gpu/api/annotations/WorkGroup;";
    static final String DEVICE_FUNCTION = "Lcom/sixik/gpu/api/annotations/DeviceFunction;";
    static final String EXTERN = "Lcom/sixik/gpu/api/annotations/Extern;";

    private Annotations() {
    }

    /**
     * @param anns аннотации элемента (может быть {@code null})
     * @param desc дескриптор типа аннотации
     * @return аннотация либо {@code null}
     */
    static AnnotationNode find(List<AnnotationNode> anns, String desc) {
        if (anns != null) {
            for (AnnotationNode a : anns) {
                if (a.desc.equals(desc)) {
                    return a;
                }
            }
        }
        return null;
    }

    /**
     * Явно заданные элементы в форме {@code KernelAttributes.parse}: в class-файле лежат только явные значения
     * (значения по умолчанию - в самом классе аннотации), поэтому правило «устаревший элемент учитывается, только если
     * задан явно» работает так же, как в {@code frontend-java}. Константа перечисления - её имя, массив - {@link List}.
     *
     * @param a аннотация
     * @return карта «имя элемента → значение»
     */
    static Map<String, Object> explicitValues(AnnotationNode a) {
        Map<String, Object> r = new LinkedHashMap<>();
        if (a.values != null) {
            for (int i = 0; i + 1 < a.values.size(); i += 2) {
                r.put((String) a.values.get(i), plain(a.values.get(i + 1)));
            }
        }
        return r;
    }

    private static Object plain(Object v) {
        if (v instanceof String[] e && e.length == 2) {
            return e[1]; // константа перечисления: {дескриптор, имя}
        }
        if (v instanceof List<?> list) {
            List<Object> r = new ArrayList<>();
            for (Object o : list) {
                r.add(plain(o));
            }
            return r;
        }
        return v;
    }
}
