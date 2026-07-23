package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUPacked;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

@GPUStruct
@GPUPacked
public final class PackedBlobView {

    public int primaryOffset;
    public int secondaryOffset;
    public int bias;
    public int itemCount;

    public PackedBlobView() {
    }

    public PackedBlobView(int primaryOffset, int secondaryOffset, int bias, int itemCount) {
        this.primaryOffset = primaryOffset;
        this.secondaryOffset = secondaryOffset;
        this.bias = bias;
        this.itemCount = itemCount;
    }
}
