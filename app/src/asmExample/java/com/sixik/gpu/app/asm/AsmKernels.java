package com.sixik.gpu.app.asm;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.annotations.Struct;
import com.sixik.gpu.api.types.Float4;
import com.sixik.gpu.app.asmlib.ColorMath;

/**
 * Ядра примера фронтенда байткода. Этот source set компилируется <b>без</b> annotation processor'а: IR строит
 * {@code frontend-asm} из готовых class-файлов (как плагин при {@code gpu { frontend = 'asm' }}). Device-функции
 * {@link ColorMath} лежат в отдельном jar без исходников - processor отверг бы такой вызов ({@code GPU0114}).
 */
public final class AsmKernels {

    /** Запрещает создание экземпляров класса ядер. */
    private AsmKernels() {
    }

    /**
     * Частица: положение, скорость и оставшаяся «жизнь» в [0, 1]. Форма {@code @Struct}: открытые изменяемые поля,
     * конструктор без аргументов, конструктор по всем полям и глубокий {@code copy()}.
     */
    @Struct
    public static final class Particle {
        /** Положение (w не используется). */
        public Float4 pos;
        /** Скорость, единиц в секунду (w не используется). */
        public Float4 vel;
        /** Оставшаяся жизнь в [0, 1]. */
        public float life;

        /** Нулевая частица (хост создаёт объекты при возврате результата). */
        public Particle() {
            this.pos = new Float4(0, 0, 0, 0);
            this.vel = new Float4(0, 0, 0, 0);
        }

        /**
         * @param pos  положение
         * @param vel  скорость
         * @param life оставшаяся жизнь
         */
        public Particle(Float4 pos, Float4 vel, float life) {
            this.pos = pos;
            this.vel = vel;
            this.life = life;
        }

        /** @return самостоятельная копия (в ядре - свежее значение, правило {@code GPU0193}) */
        public Particle copy() {
            return new Particle(pos, vel, life);
        }

        @Override
        public String toString() {
            return "Particle{pos=" + pos + ", vel=" + vel + ", life=" + life + "}";
        }
    }

    /**
     * Яркость пикселей: {@code out[i] = clamp(luma(colors[i]) * gain, 0, 1)}; обе функции - из jar библиотеки.
     *
     * @param colors цвета RGBA
     * @param out    яркость, длина не меньше числа work-item
     * @param gain   усиление
     */
    @Kernel
    public static void grayscale(@Global Float4[] colors, @Global float[] out, float gain) {
        int g = Gpu.getGlobalId(0);
        out[g] = ColorMath.clamp(ColorMath.luma(colors[g]) * gain, 0f, 1f);
    }

    /**
     * Шаг частиц по Эйлеру: {@code pos += vel * dt}, {@code life = clamp(life - dt, 0, 1)}; погасшая частица стоит.
     *
     * @param ps частицы, изменяются на месте
     * @param dt шаг времени, секунды
     */
    @Kernel
    public static void step(@Global Particle[] ps, float dt) {
        int g = Gpu.getGlobalId(0);
        Particle p = ps[g].copy();
        if (p.life > 0f) {
            p.pos = p.pos.add(p.vel.mul(dt));
            p.life = ColorMath.clamp(p.life - dt, 0f, 1f);
        }
        // запись struct в массив - только свежим значением (иначе GPU0193: на JVM это была бы общая ссылка)
        ps[g] = p.copy();
    }
}
