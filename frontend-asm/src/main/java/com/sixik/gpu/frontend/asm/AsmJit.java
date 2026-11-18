package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.ir.IrModule;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Компиляция байткода, который программа сгенерировала во время работы (FRONTEND_ASM_RUNTIME.md, раздел 18, шаг B1):
 * классы приходят деревом {@link ClassNode} вместе с библиотекой из {@link ClassSource}, точки входа перечислены явно.
 *
 * <p>Отличия от {@link AsmFrontend#compile}:</p>
 * <ul>
 *   <li>сгенерированные классы не сериализуются и не проходят {@code ClassReader}: нет лимита 64 КБ на метод и 65535
 *       записей пула констант, не нужны {@code COMPUTE_FRAMES} (18.2-S1);</li>
 *   <li>ядра не ищутся по аннотациям и {@code $jvm}: точки входа - {@link Entry}; метод без {@code @Kernel} получает
 *       атрибуты из {@link Entry#kernel()} и {@link Entry#workGroup()};</li>
 *   <li>статические методы сгенерированных классов вызываются как device-функции без {@code @DeviceFunction}
 *       (контракт 18.3); библиотека в jar по-прежнему помечает свои функции аннотацией;</li>
 *   <li>ресурсы {@code META-INF/gpu/**} не пишутся - результат только в памяти.</li>
 * </ul>
 *
 * <p>Чистая функция: без файлов, потоков и глобального состояния. До шага B4 модуль IR - по одному на точку входа
 * (ядро - последняя функция модуля, как у {@link AsmFrontend}); B4 сведёт их в один модуль с несколькими ядрами.</p>
 */
public final class AsmJit {

    private AsmJit() {
    }

    /**
     * Точка входа модуля.
     *
     * @param owner     внутреннее имя класса ({@code pkg/Gen$1})
     * @param name      имя метода
     * @param desc      дескриптор метода
     * @param kernel    явные элементы {@code @Kernel} (как {@code KernelAttributes.parse}: {@code workgroup},
     *                  {@code compilationProperties}, {@code required}, ...) для метода без аннотации; {@code null} -
     *                  атрибуты из аннотации метода либо значения по умолчанию
     * @param workGroup явные элементы {@code @WorkGroup} ({@code x}, {@code y}, {@code z}) либо {@code null}
     */
    public record Entry(String owner, String name, String desc, Map<String, Object> kernel,
                        Map<String, Object> workGroup) {

        /** Проверяет поля и копирует карты. */
        public Entry {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(desc, "desc");
            kernel = kernel == null ? null : copy(kernel);
            workGroup = workGroup == null ? null : copy(workGroup);
        }

        /**
         * Точка входа с атрибутами из аннотации метода (или по умолчанию).
         *
         * @param owner внутреннее имя класса
         * @param name  имя метода
         * @param desc  дескриптор
         * @return точка входа
         */
        public static Entry of(String owner, String name, String desc) {
            return new Entry(owner, name, desc, null, null);
        }

        private static Map<String, Object> copy(Map<String, Object> m) {
            return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(m));
        }

        /** @return {@code Класс.метод} для сообщений */
        String shown() {
            return owner.replace('/', '.') + "." + name;
        }
    }

    /**
     * Единица компиляции.
     *
     * @param classes   библиотека и прочие классы по имени (jar мода, загрузчик игры; см. {@link ClassSource#chain})
     * @param generated сгенерированные классы деревом: внутреннее имя → узел; ищутся раньше {@code classes}
     * @param entries   точки входа в порядке результата
     */
    public record Unit(ClassSource classes, Map<String, ClassNode> generated, List<Entry> entries) {

        /** Проверяет поля и копирует коллекции. */
        public Unit {
            classes = classes == null ? ClassSource.empty() : classes;
            generated = generated == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(generated));
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }

    /**
     * Настройки.
     *
     * @param strictBarriers барьер под расходящимся управлением - ошибка {@code GPU0131}, а не предупреждение
     *                       {@code GPU0130}
     */
    public record Options(boolean strictBarriers) {
        /** Настройки по умолчанию. */
        public static final Options DEFAULT = new Options(false);
    }

    /**
     * Построенная точка входа.
     *
     * @param entry    точка входа
     * @param module   модуль IR (ядро - последняя функция)
     * @param kernelId хэш модуля ({@code IrBinary.kernelId}), ключ кэша рантайма
     */
    public record Kernel(Entry entry, IrModule module, String kernelId) {
    }

    /**
     * Результат.
     *
     * @param kernels     построенные точки входа в порядке {@link Unit#entries()} (без точек с ошибками)
     * @param diagnostics все диагностики по порядку
     */
    public record Result(List<Kernel> kernels, List<AsmDiagnostic> diagnostics) {
        /** @return {@code true}, если среди диагностик нет ошибок */
        public boolean ok() {
            return diagnostics.stream().noneMatch(AsmDiagnostic::error);
        }
    }

    /**
     * Строит IR точек входа.
     *
     * @param unit    классы и точки входа
     * @param options настройки
     * @return построенные точки и диагностики
     */
    public static Result compile(Unit unit, Options options) {
        List<AsmDiagnostic> diags = new ArrayList<>();
        List<Kernel> kernels = new ArrayList<>();
        ModuleBuilder mb = new ModuleBuilder(unit.classes(), diags);
        for (Map.Entry<String, ClassNode> g : unit.generated().entrySet()) {
            ClassNode cn = g.getValue();
            if (!g.getKey().equals(cn.name)) {
                diags.add(AsmDiagnostic.of("GPU0306", g.getKey().replace('/', '.'), "", 0, g.getKey().replace('/', '.'),
                        "the generated class is registered under another name " + cn.name));
                continue;
            }
            mb.addGenerated(cn);
        }
        AsmFrontend.Options fo = new AsmFrontend.Options(options.strictBarriers());
        for (Entry e : unit.entries()) {
            String cls = e.owner().replace('/', '.');
            ClassNode cn = mb.find(e.owner());
            if (cn == null) {
                diags.add(AsmDiagnostic.of("GPU0306", cls, "", 0, cls, "class file not found"));
                continue;
            }
            MethodNode mn = method(cn, e);
            if (mn == null) {
                diags.add(AsmDiagnostic.of("GPU0311", cls, e.name(), 0, e.shown() + e.desc(), "no such method"));
                continue;
            }
            boolean annotated = Annotations.find(mn.invisibleAnnotations, Annotations.KERNEL) != null;
            boolean reduction = Annotations.find(mn.invisibleAnnotations, Annotations.REDUCTION) != null;
            if (annotated || reduction) {
                if (e.kernel() != null || e.workGroup() != null) {
                    diags.add(AsmDiagnostic.of("GPU0311", cls, e.name(), 0, e.shown(),
                            "@Kernel elements are given both by the annotation and by the entry"));
                    continue;
                }
            } else {
                mb.declareEntry(cn, mn, e.kernel() == null ? Map.of() : e.kernel(), e.workGroup());
            }
            AsmFrontend.Kernel k = AsmFrontend.guarded(mb, cn, mn, reduction, fo, diags);
            if (k != null) {
                kernels.add(new Kernel(e, k.module(), k.entry().kernelId()));
            }
        }
        return new Result(List.copyOf(kernels), List.copyOf(diags));
    }

    private static MethodNode method(ClassNode cn, Entry e) {
        for (MethodNode m : cn.methods) {
            if (m.name.equals(e.name()) && m.desc.equals(e.desc())) {
                return m;
            }
        }
        return null;
    }
}
