package cn.blockforge.generated.coordinatepointer.client;

import cn.blockforge.generated.coordinatepointer.PointerLogic;
import cn.blockforge.generated.coordinatepointer.data.TargetEntry;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * 不在玩家手上时（展示框里的、掉在地上的）指针该朝哪个方向。
 *
 * <p>展示框会把物品在自己的平面里转一个角度，所以不能按玩家的朝向算。
 * 这里直接把展示框实际用的那套旋转（俯仰、朝向、框内旋转）算出来，
 * 再在 16 个指针贴图里挑一个真正指向目标的那张，显示器怎么摆都能读。
 */
final class NeedleAngles {
    /** 指针一共 16 个朝向，每个 22.5 度。 */
    private static final double STEP_DEGREES = 360.0D / PointerLogic.NEEDLE_STEPS;

    private NeedleAngles() {
    }

    /**
     * 返回 0~15 的贴图朝向。
     *
     * <p>这里故意不检查"目标是不是同一个维度"：航空学的飞船、载具是单独的子世界，
     * 维度名和指针记下来的对不上，要是因为这个就返回"不指"，展示框里的指针会莫名其妙失灵。
     * 坐标本来就是 X/Z，指个方向总比什么都不指有用。
     */
    static int bucketFor(Entity holder, TargetEntry target) {
        double bearing = PointerLogic.bearingFrom(holder.getX(), holder.getZ(), target);
        if (!(holder instanceof ItemFrame frame)) {
            // 掉在地上的指针：像指南针一样指着世界里的方位。
            return PointerLogic.bucketForDegrees(bearing);
        }
        Vec3 aim = aimVector(frame, bearing);
        return bestBucket(frameRotation(frame), aim);
    }

    /**
     * 想让它"看起来"指的方向：
     * 立在墙上的框当成挂在墙上的地图——朝上就是北、朝右就是东；
     * 平放的框（地板/天花板）直接按世界方位算，人站在哪边看都一样。
     */
    private static Vec3 aimVector(ItemFrame frame, double bearingDegrees) {
        double radians = Math.toRadians(bearingDegrees);
        Direction facing = frame.getDirection();
        if (facing.getAxis().isHorizontal()) {
            Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
            Vec3 right = normal.scale(-1.0D).cross(up).normalize();
            return right.scale(Math.sin(radians)).add(up.scale(Math.cos(radians))).normalize();
        }
        return new Vec3(Math.sin(radians), 0.0D, -Math.cos(radians));
    }

    /** 和 ItemFrameRenderer 一模一样的旋转：先俯仰、再朝向、最后框内那 45 度一格。 */
    private static Matrix3f frameRotation(ItemFrame frame) {
        Matrix3f rotation = new Matrix3f();
        rotation.rotateX((float) Math.toRadians(frame.getXRot()));
        rotation.rotateY((float) Math.toRadians(180.0F - frame.getYRot()));
        rotation.rotateZ((float) Math.toRadians(frame.getRotation() * 45.0F));
        return rotation;
    }

    /** 在 16 张指针贴图里挑最接近目标方向的那张。 */
    private static int bestBucket(Matrix3f rotation, Vec3 aim) {
        Vec3 desired = aim.normalize();
        int best = 0;
        double bestScore = -Double.MAX_VALUE;
        for (int bucket = 0; bucket < PointerLogic.NEEDLE_STEPS; bucket++) {
            double radians = Math.toRadians(bucket * STEP_DEGREES);
            // 贴图里第 bucket 张是"从正上顺时针转 bucket*22.5 度"。
            // 物品模型在展示框里会先按 fixed 显示设置绕 Y 轴转 180 度（原版 item/generated 就这么摆的），
            // 所以这里的 X 分量要取反，算出来的方向才是屏幕上看到的那一侧。
            Vector3f needle = new Vector3f((float) -Math.sin(radians), (float) Math.cos(radians), 0.0F);
            rotation.transform(needle);
            double score = new Vec3(needle.x, needle.y, needle.z).dot(desired);
            if (score > bestScore) {
                bestScore = score;
                best = bucket;
            }
        }
        return best;
    }
}
