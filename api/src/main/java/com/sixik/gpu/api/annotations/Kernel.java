package com.sixik.gpu.api.annotations;

import com.sixik.gpu.api.enums.InlinePolicy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Точка входа GPU-кода (OpenCL {@code __kernel}). Метод должен быть {@code static void}; нарушение подмножества
 * языка - ошибка компиляции с кодом {@code GPU####} (REWORK_ARCHITECTURE.md, 3.3).
 *
 * <p>Элементы - директивы ядра; они превращаются в атрибуты функции IR. Вызов метода из обычного Java-кода
 * запускает его на устройстве (тело подменяет weaver), а исходное тело сохраняется как JVM-реализация.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Kernel {

    /**
     * Настройки генерации кода ядра одной строкой: пары {@code ключ=значение} через {@code ;}, списки значений через
     * запятую. Строку разбирает annotation processor: неизвестный ключ или значение - ошибка компиляции с подсказкой.
     * Значение аннотации - константа, поэтому общий профиль удобно вынести в {@code static final String} и склеивать:
     * {@code compilationProperties = Profiles.FAST + "; opt=2"}.
     *
     * <table>
     *   <caption>Свойства</caption>
     *   <tr><th>Ключ</th><th>Значения</th><th>Смысл</th></tr>
     *   <tr><td>{@code fp}</td><td>{@code contract}, {@code reciprocal}, {@code finite}, {@code nsz}, {@code native},
     *       {@code relaxed}</td><td>что разрешено менять в вещественной арифметике (по умолчанию ничего - результат
     *       как на JVM): слияние {@code a*b+c} в {@code fma}; {@code x/c → x*(1/c)}; значения конечны (нет NaN и
     *       бесконечностей); знак нуля не важен; аппаратные приближённые функции ({@code native_sin} и т.п.);
     *       {@code relaxed} - всё перечисленное. Преобразования выполняет оптимизатор библиотеки явным кодом,
     *       опции сборки драйвера (например {@code -cl-fast-relaxed-math}) не используются, поэтому поведение не
     *       зависит от производителя устройства</td></tr>
     *   <tr><td>{@code opt}</td><td>{@code 0..3}</td><td>уровень оптимизатора IR для этого ядра; без ключа - уровень
     *       контекста</td></tr>
     *   <tr><td>{@code inline}</td><td>{@code default}, {@code always}, {@code never}</td><td>политика встраивания
     *       (записывается в IR, генератор пока её не учитывает)</td></tr>
     * </table>
     *
     * <p>Пример: {@code @Kernel(compilationProperties = "fp=contract,native; opt=3")}. JVM-реализация ядра (fallback)
     * всегда считает строго, как написано в Java.</p>
     *
     * @return строка свойств; пустая - всё по умолчанию
     */
    String compilationProperties() default "";

    /**
     * Политика встраивания функции.
     *
     * @return политика встраивания; по умолчанию {@link InlinePolicy#DEFAULT}
     * @deprecated используйте {@code compilationProperties = "inline=always"}; задание одного свойства обоими
     * способами - ошибка компиляции
     */
    @Deprecated
    InlinePolicy inline() default InlinePolicy.DEFAULT;

    /**
     * Уровень оптимизации функции.
     *
     * @return уровень оптимизации {@code 0..3}; значение по умолчанию означает «уровень контекста»
     * @deprecated используйте {@code compilationProperties = "opt=2"}; задание одного свойства обоими способами -
     * ошибка компиляции
     */
    @Deprecated
    int optLevel() default 0;

    /**
     * Сокращение для {@code @WorkGroup(x = workgroup)}: одномерный обязательный размер work-group.
     * Совместное использование с {@link WorkGroup} - ошибка.
     *
     * @return размер work-group по оси X либо 0, если не задан
     */
    int workgroup() default 0;

    /**
     * Возможности устройства, без которых ядро не может работать.
     *
     * @return имена требуемых возможностей устройства ({@code FP64}, {@code INT64_ATOMICS}, ...); если устройство
     * их не поддерживает, берётся вариант {@link #fallback()}
     */
    String[] required() default {};

    /**
     * Запасной вариант реализации.
     *
     * @return имя другого метода с той же сигнатурой - вариант реализации, который используется, когда
     * {@link #required()} не выполнено; пустая строка - варианта нет
     */
    String fallback() default "";
}
