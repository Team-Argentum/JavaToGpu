package com.sixik.gpu.ir.interpreter.samples;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.DeviceFunction;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.annotations.Struct;
import com.sixik.gpu.api.types.Float4;

/** {@code @Struct}: массивы и параметры struct, поля, вложенные struct, запись в поля, {@code copy()}, device-функции. */
public class StructKernels {

    /** Частица: float, float, float4, int (в памяти устройства 48 байт: float4 выравнен по 16). */
    @Struct
    public static final class Particle {
        public float x;
        public float y;
        public Float4 v;
        public int id;

        public Particle() {
            this.v = new Float4(0, 0, 0, 0);
        }

        public Particle(float x, float y, Float4 v, int id) {
            this.x = x;
            this.y = y;
            this.v = v;
            this.id = id;
        }

        public Particle copy() {
            return new Particle(x, y, v, id);
        }
    }

    /** Тело: вложенный struct и double. */
    @Struct
    public static final class Body {
        public Particle p;
        public double mass;

        public Body() {
            this.p = new Particle();
        }

        public Body(Particle p, double mass) {
            this.p = p;
            this.mass = mass;
        }

        public Body copy() {
            return new Body(p.copy(), mass);
        }
    }

    /** Пара без выравнивающих дыр: int и long (16 байт). */
    @Struct
    public static final class Pair {
        public int a;
        public long b;

        public Pair() {
        }

        public Pair(int a, long b) {
            this.a = a;
            this.b = b;
        }

        public Pair copy() {
            return new Pair(a, b);
        }
    }

    @DeviceFunction
    static Particle bump(Particle p, float d) {
        return new Particle(p.x + d, p.y, p.v, p.id);
    }

    @DeviceFunction
    static float energy(Particle p) {
        return 0.5f * (p.v.x * p.v.x + p.v.y * p.v.y + p.v.z * p.v.z);
    }

    @Kernel
    public static void step(@Global Particle[] ps, float dt) {
        int g = Gpu.getGlobalId(0);
        ps[g].x += dt * ps[g].v.x;
        ps[g].y = ps[g].y + dt * ps[g].v.y;
        ps[g].id++;
        ps[g].v = ps[g].v.mul(0.5f).withW(g);
    }

    @Kernel
    public static void locals(@Global Particle[] ps, @Global float[] out) {
        int g = Gpu.getGlobalId(0);
        Particle p = ps[g].copy();
        p.x = p.x * 2;
        p.id += 10;
        Particle q = p.copy();
        q.y = 7;
        Particle r = bump(q, 0.5f);
        Body body = new Body(r.copy(), 2.5);
        body.p.x = 99;
        body.mass *= 2;
        int o = g * 8;
        out[o] = p.x;
        out[o + 1] = q.y;
        out[o + 2] = p.y;
        out[o + 3] = r.x;
        out[o + 4] = (float) body.mass;
        out[o + 5] = body.p.x + body.p.v.w;
        out[o + 6] = energy(ps[g]);
        ps[g] = body.p.copy();
        Particle pick = g % 2 == 0 ? new Particle(1, 2, Float4.splat(3), 4) : q.copy();
        out[o + 7] = pick.x + pick.id;
    }

    @Kernel
    public static void bodies(@Global Body[] bs, @Global Pair[] pairs) {
        int g = Gpu.getGlobalId(0);
        Body b = bs[g].copy();
        b.p.v = b.p.v.add(1f);
        b.p.id = g;
        bs[g] = b.copy();
        Pair pr = pairs[g].copy();
        pairs[g] = new Pair(pr.a * 3, pr.b + g);
    }

    @Kernel
    public static void withParameter(@Global float[] out, Particle init, Float4 scale) {
        int g = Gpu.getGlobalId(0);
        Particle p = bump(init, g);
        out[g * 3] = p.x + scale.x;
        out[g * 3 + 1] = p.v.w * scale.w;
        out[g * 3 + 2] = p.id;
    }
}
