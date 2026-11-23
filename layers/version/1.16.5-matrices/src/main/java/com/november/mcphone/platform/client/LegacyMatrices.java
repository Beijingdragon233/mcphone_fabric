package com.november.mcphone.platform.client;

import com.mojang.math.Matrix4f;
import com.mojang.math.Vector4f;

public final class LegacyMatrices {
    private LegacyMatrices() {}

    public static float translationX(Matrix4f matrix) {
        Vector4f origin = new Vector4f(0, 0, 0, 1);
        origin.transform(matrix);
        return origin.x();
    }
}
