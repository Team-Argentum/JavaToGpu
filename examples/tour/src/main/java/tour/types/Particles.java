package tour.types;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.annotations.Struct;
import com.sixik.gpu.api.types.Float4;
import tour.Show;

import java.util.Locale;

/**
 * Урок 3. Векторы и структуры.
 *
 * <p>{@code Float4} (и {@code Float2/3/8/16}, {@code Int*}, {@code Double*}, {@code Long*}) - неизменяемые векторы:
 * {@code add}, {@code mul}, {@code withY} возвращают новый вектор и компилируются в векторные операции OpenCL.
 * {@code @Struct} - класс с открытыми полями (скаляры, векторы, вложенные struct), массив таких объектов
 * передаётся в ядро как массив структур C: рантайм упаковывает поля по ABI OpenCL и после запуска записывает
 * значения обратно в те же объекты.</p>
 *
 * <p>Value-семантика: struct в ядре копируется, как в C. Поэтому читать элемент массива нужно через {@code copy()}
 * (или собирать новый через {@code new}) - простое присваивание {@code Particle p = ps[i]} запрещено ({@code GPU0193}):
 * на JVM это была бы общая ссылка, на GPU - копия.</p>
 *
 * <p>Попробуйте: добавить поле {@code Float4 color}; сделать отскок упругим; убрать {@code copy()} и посмотреть на
 * ошибку компиляции.</p>
 */
public final class Particles {

    private Particles() {
    }

    /** Частица: положение, скорость, оставшаяся жизнь. */
    @Struct
    public static final class Particle {
        public Float4 pos;
        public Float4 vel;
        public float life;

        /** Пустая частица (нужен хосту для распаковки). */
        public Particle() {
            this.pos = new Float4(0, 0, 0, 0);
            this.vel = new Float4(0, 0, 0, 0);
        }

        /**
         * @param pos  положение
         * @param vel  скорость
         * @param life оставшаяся жизнь, с
         */
        public Particle(Float4 pos, Float4 vel, float life) {
            this.pos = pos;
            this.vel = vel;
            this.life = life;
        }

        /** @return самостоятельная копия (value-семантика struct) */
        public Particle copy() {
            return new Particle(pos, vel, life);
        }
    }

    /**
     * Шаг явного Эйлера с гравитацией и отскоком от пола {@code y = 0}.
     *
     * @param ps      частицы
     * @param dt      шаг, с
     * @param gravity ускорение (вектор передаётся по значению, как скаляр)
     */
    @Kernel
    public static void step(@Global Particle[] ps, float dt, Float4 gravity) {
        int i = Gpu.getGlobalId(0);
        Particle p = ps[i].copy();
        Float4 vel = p.vel.add(gravity.mul(dt));
        Float4 pos = p.pos.add(vel.mul(dt));
        if (pos.y < 0f) {
            pos = pos.withY(-pos.y);
            vel = vel.withY(-vel.y * 0.8f);       // отскок теряет 20% скорости
        }
        ps[i] = new Particle(pos, vel, p.life - dt);
    }

    /**
     * Скорость каждой частицы - длина вектора скорости.
     *
     * @param ps    частицы
     * @param speed выход
     */
    @Kernel
    public static void speeds(@Global Particle[] ps, @Global float[] speed) {
        int i = Gpu.getGlobalId(0);
        Float4 v = ps[i].copy().vel;
        speed[i] = Gpu.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
    }

    /** Программа урока. */
    public static void run() {
        int n = 4096;
        Particle[] ps = new Particle[n];
        for (int i = 0; i < n; i++) {
            float a = i * 0.0015f;
            ps[i] = new Particle(new Float4(0, 5, 0, 1), new Float4((float) Math.cos(a) * 3, 2, (float) Math.sin(a) * 3, 0),
                    10f);
        }
        Float4 g = new Float4(0, -9.81f, 0, 0);
        for (int s = 0; s < 200; s++) {
            step(ps, 0.01f, g);                   // объекты массива обновляются на месте
        }
        Show.section("after 2 s of simulation");
        for (int i = 0; i < 3; i++) {
            Particle p = ps[i * 1000];
            System.out.printf(Locale.ROOT, "particle %4d: pos (%6.2f, %6.2f, %6.2f) vel (%6.2f, %6.2f, %6.2f) life %.2f%n",
                    i * 1000, p.pos.x, p.pos.y, p.pos.z, p.vel.x, p.vel.y, p.vel.z, p.life);
        }
        float[] speed = new float[n];
        speeds(ps, speed);
        Show.head("speeds", speed, 4);
        Show.check(Math.abs(ps[0].life - 8f) < 1e-3f, "life decreases by dt per step");
        for (Particle p : ps) {
            Show.check(p.pos.y >= 0f, "no particle under the floor");
        }
    }
}
