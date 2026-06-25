package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

@GPUStruct
public final class Vec2 {

    public double x;
    public double y;

    public Vec2() {
    }

    public Vec2(double x, double y) {
        this.x = x;
        this.y = y;
    }
}
