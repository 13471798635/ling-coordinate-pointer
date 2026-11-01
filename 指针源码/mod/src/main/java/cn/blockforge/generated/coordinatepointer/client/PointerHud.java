package cn.blockforge.generated.coordinatepointer.client;

import cn.blockforge.generated.coordinatepointer.PointerLogic;
import cn.blockforge.generated.coordinatepointer.data.PointerData;
import cn.blockforge.generated.coordinatepointer.data.TargetEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 左上角的文字面板：拿着坐标指针时出现。
 *
 * <p>样式照玩家给的参考图：只有纯文字，没有背景板也没有彩色条；
 * 每一组是"标题（白）+ 值（浅灰）"，组和组之间空一行，整体贴屏幕左上角。
 */
public final class PointerHud {
    /** 标题的颜色，和参考图一致，就是普通的白字。 */
    private static final int LABEL_COLOR = 0xFFFFFF;
    /** 值的颜色，参考图里是偏灰的白字。 */
    private static final int VALUE_COLOR = 0xA8A8A8;
    private static final int LINE_HEIGHT = 10;
    /** 每组占三行：标题、值、空行。 */
    private static final int GROUP_HEIGHT = LINE_HEIGHT * 3;
    private static final int MARGIN_LEFT = 4;
    /** 往下挪一截，避开别的模组摆在同一角落的 FPS / 坐标显示（一般占两三行）。 */
    private static final int MARGIN_TOP = 28;

    private PointerHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }

        ItemStack stack = PointerLogic.findHeldPointer(player);
        if (stack.isEmpty()) {
            return;
        }

        PointerData data = PointerData.of(stack);
        int slot = data.activeSlotSafe();
        TargetEntry entry = data.entry(slot);
        Optional<TargetEntry> maybeTarget = data.activeTarget();

        List<Component> labels = new ArrayList<>(3);
        List<Component> values = new ArrayList<>(3);

        labels.add(Component.translatable("hud.coordinate_pointer.name"));
        // 跟物品名后面那截一样上色：A 红、B 蓝、C 黄、D 绿、E 紫。
        values.add(Component.literal(PointerLogic.displayName(entry, slot))
                .withStyle(PointerLogic.slotColor(slot)));

        labels.add(Component.translatable("hud.coordinate_pointer.coords"));
        if (maybeTarget.isEmpty()) {
            values.add(Component.translatable("hud.coordinate_pointer.empty"));
            labels.add(Component.translatable("hud.coordinate_pointer.hint"));
            values.add(Component.translatable("hud.coordinate_pointer.open"));
        } else {
            TargetEntry target = maybeTarget.get();
            values.add(Component.literal("X " + target.x() + "  Z " + target.z()));
            labels.add(Component.translatable("hud.coordinate_pointer.distance"));
            if (PointerLogic.sameDimension(player, target)) {
                values.add(Component.literal(format(PointerLogic.horizontalDistance(player, target))
                        + Component.translatable("hud.coordinate_pointer.unit").getString()));
            } else {
                values.add(Component.translatable("hud.coordinate_pointer.other_world"));
            }
        }

        Font font = minecraft.font;
        int x = MARGIN_LEFT;
        int y = MARGIN_TOP;
        for (int i = 0; i < labels.size(); i++) {
            graphics.drawString(font, labels.get(i), x, y, LABEL_COLOR, true);
            graphics.drawString(font, values.get(i), x, y + LINE_HEIGHT, VALUE_COLOR, true);
            y += GROUP_HEIGHT;
        }
    }

    private static String format(double distance) {
        return String.format(Locale.ROOT, "%.0f", distance);
    }
}
