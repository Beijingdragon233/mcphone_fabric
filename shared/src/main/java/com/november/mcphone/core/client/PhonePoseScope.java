package com.november.mcphone.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

/** 保存完整 GUI 矩阵栈；即使附属少 pop、多 pop 或异常退出，也恢复宿主的栈深和矩阵。 */
public final class PhonePoseScope implements AutoCloseable {
    private static final class Pose {
        private final Matrix4f matrix;
        private final Matrix3f normal;

        private Pose(Matrix4f matrix, Matrix3f normal) {
            this.matrix = matrix;
            this.normal = normal;
        }

        public Matrix4f matrix() { return matrix; }

        public Matrix3f normal() { return normal; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Pose)) return false;
            Pose that = (Pose) other;
            return java.util.Objects.equals(this.matrix, that.matrix)
                    && java.util.Objects.equals(this.normal, that.normal);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(matrix);
            hash = 31 * hash + java.util.Objects.hashCode(normal);
            return hash;
        }

        @Override public String toString() { return "Pose[matrix=" + matrix + ", normal=" + normal + "]"; }
}
    private final PoseStack stack;
    private final List<Pose> saved = new ArrayList<>();

    public PhonePoseScope(PoseStack stack) {
        this.stack = stack;
        do {
            saved.add(new Pose(new Matrix4f(stack.last().pose()), new Matrix3f(stack.last().normal())));
            if (com.november.mcphone.platform.client.Draw.poseAtBase(stack)) break;
            stack.popPose();
        } while (true);
        restore();
    }

    private void restore() {
        while (!com.november.mcphone.platform.client.Draw.poseAtBase(stack)) stack.popPose();
        for (int i = saved.size() - 1; i >= 0; i--) {
            if (i != saved.size() - 1) stack.pushPose();
            // 新版 Pose 另有 trustedNormals 缓存，直接 set 矩阵不会更新它。
            // 经公开的非等比 scale 使其采用保守的法线归一化，再恢复实际矩阵；
            // 不能让继承自栈底的“正交”标记误用于手机缩放后的法线矩阵。
            stack.scale(1, 1, 2);
            stack.last().pose().set(saved.get(i).matrix());
            stack.last().normal().set(saved.get(i).normal());
        }
    }

    @Override public void close() { restore(); }
}
