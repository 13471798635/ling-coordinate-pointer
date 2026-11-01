package cn.blockforge.generated.coordinatepointer;

import cn.blockforge.generated.coordinatepointer.data.PointerData;
import cn.blockforge.generated.coordinatepointer.data.TargetEntry;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** 服务器和客户端都要用的计算：找到手上的指针、算距离、算方位。 */
public final class PointerLogic {
    /** 这一格以内就算"到达"，会响提示音。 */
    public static final double ARRIVE_RADIUS = 5.0D;

    /** 指针一共 16 个朝向，每个 22.5 度。 */
    public static final int NEEDLE_STEPS = 16;

    /** 物品名和格子名之间那个小圆点。 */
    public static final String NAME_JOINER = "·";

    /** 5 个存档位没起名字时的默认叫法：A、B、C、D、E。 */
    private static final String[] SLOT_LETTERS = {"A", "B", "C", "D", "E"};

    /** 5 个格子的标识色，跟着 A~E 走：红、蓝、黄、绿、紫。 */
    private static final ChatFormatting[] SLOT_COLORS = {
            ChatFormatting.RED,
            ChatFormatting.BLUE,
            ChatFormatting.YELLOW,
            ChatFormatting.GREEN,
            ChatFormatting.LIGHT_PURPLE
    };

    private PointerLogic() {
    }

    /** 主手或副手上拿着坐标指针吗？拿着的话直接返回那一叠物品。 */
    public static ItemStack findHeldPointer(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(ModItems.COORDINATE_POINTER.get())) {
            return mainHand;
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.is(ModItems.COORDINATE_POINTER.get())) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }

    public static Optional<TargetEntry> activeTarget(ItemStack stack) {
        return PointerData.of(stack).activeTarget();
    }

    /** 目标跟玩家是不是同一个世界（同一维度）。 */
    public static boolean sameDimension(Player player, TargetEntry target) {
        return player.level().dimension().location().equals(target.dimension());
    }

    /** 水平直线距离（格），就是 X/Z 平面上的直线距离。 */
    public static double horizontalDistance(Player player, TargetEntry target) {
        return distanceFrom(player.getX(), player.getZ(), target);
    }

    /** 从任意一点（比如展示框的位置）算到目标的水平距离。 */
    public static double distanceFrom(double fromX, double fromZ, TargetEntry target) {
        double dx = (double) target.x() + 0.5D - fromX;
        double dz = (double) target.z() + 0.5D - fromZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 从任意一点看目标的世界方位角：0 = 正北，顺时针一圈 360。 */
    public static double bearingFrom(double fromX, double fromZ, TargetEntry target) {
        double dx = (double) target.x() + 0.5D - fromX;
        double dz = (double) target.z() + 0.5D - fromZ;
        return Mth.positiveModulo(Math.toDegrees(Math.atan2(dx, -dz)), 360.0D);
    }

    public static double bearing(Player player, TargetEntry target) {
        return bearingFrom(player.getX(), player.getZ(), target);
    }

    /**
     * 手持时指针该用第几个朝向（0~15）：
     * 把"目标在世界的方位"减去"玩家面朝的方向"，目标在正前方时指针就朝上。
     */
    public static int needleBucket(Player player, TargetEntry target) {
        if (!target.set()) {
            return 0;
        }
        double facing = Mth.positiveModulo(player.getYRot() + 180.0D, 360.0D);
        return bucketForDegrees(bearing(player, target) - facing);
    }

    /** 把任意角度（度，顺时针）换算成 0~15 的指针朝向。 */
    public static int bucketForDegrees(double degrees) {
        int bucket = (int) Math.round(Mth.positiveModulo(degrees, 360.0D) / (360.0D / NEEDLE_STEPS)) % NEEDLE_STEPS;
        return bucket < 0 ? bucket + NEEDLE_STEPS : bucket;
    }

    /** 显示用的名字：玩家给这一格起过名字就用名字，没起过就用 A / B / C … */
    public static String displayName(TargetEntry entry, int slotIndex) {
        return slotLabel(entry, slotIndex);
    }

    /** 第 slotIndex 格的叫法：自定义名字优先，没名字就退回字母。 */
    public static String slotLabel(TargetEntry entry, int slotIndex) {
        if (entry != null && !entry.name().isEmpty()) {
            return entry.name();
        }
        return slotIndex >= 0 && slotIndex < SLOT_LETTERS.length
                ? SLOT_LETTERS[slotIndex]
                : String.valueOf(slotIndex + 1);
    }

    /**
     * 物品该显示的名字：基础名字 + "·" + 当前指向的那一格。
     *
     * <p>例如没改过名就是「坐标指针·A」，用铁砧改成「坐标高级罗盘」之后就是
     * 「坐标高级罗盘·A」，换到 B 格就自动变成「坐标高级罗盘·B」。
     * 小圆点后面那截按格子上色：A 红、B 蓝、C 黄、D 绿、E 紫。
     *
     * @param base 玩家看到的基础名字（原版名或者铁砧里起的名字）
     * @param data 这把指针自己的记事本
     */
    public static Component itemName(Component base, PointerData data) {
        int slot = data.activeSlotSafe();
        return Component.empty()
                .append(base)
                .append(Component.literal(NAME_JOINER).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(slotLabel(data.entry(slot), slot)).withStyle(slotColor(slot)));
    }

    /** 第 slotIndex 个格子的标识色：红、蓝、黄、绿、紫。 */
    public static ChatFormatting slotColor(int slotIndex) {
        return slotIndex >= 0 && slotIndex < SLOT_COLORS.length ? SLOT_COLORS[slotIndex] : ChatFormatting.GRAY;
    }

    /**
     * 去掉名字末尾已经带上的旧后缀，避免改名、换格之后叠成「·A·B」。
     * 只认 5 个格子现在的叫法，玩家名字里别的地方出现的小圆点不会被动。
     */
    public static String stripSlotSuffix(String base, PointerData data) {
        String result = base == null ? "" : base;
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            for (int i = 0; i < PointerData.SLOT_COUNT; i++) {
                String suffix = NAME_JOINER + slotLabel(data.entry(i), i);
                if (result.length() > suffix.length() && result.endsWith(suffix)) {
                    result = result.substring(0, result.length() - suffix.length());
                    stripped = true;
                    break;
                }
            }
        }
        return result;
    }
}
