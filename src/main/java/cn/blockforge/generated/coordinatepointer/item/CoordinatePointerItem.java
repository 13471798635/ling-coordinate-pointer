package cn.blockforge.generated.coordinatepointer.item;

import cn.blockforge.generated.coordinatepointer.PointerLogic;
import cn.blockforge.generated.coordinatepointer.compat.AeronauticsCompat;
import cn.blockforge.generated.coordinatepointer.data.PointerData;
import cn.blockforge.generated.coordinatepointer.data.TargetEntry;
import cn.blockforge.generated.coordinatepointer.menu.CoordinatePointerMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 坐标指针本体：潜行 + 右键打开录入界面，鼠标放上去能看到当前指向。
 * 普通右键不做事，免得站着右键也弹界面挡视线。
 *
 * <p>打开界面时会把"手上这一格是物品栏的第几格"一起传给界面，
 * 所以每个指针只会改到自己身上的数据，不会串到别的指针。
 */
public class CoordinatePointerItem extends Item {
    public CoordinatePointerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 只有蹲下右键才开界面。
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            // 主手就是快捷栏选中的那一格，副手固定是第 40 格。
            int slot = hand == InteractionHand.MAIN_HAND
                    ? player.getInventory().selected
                    : Inventory.SLOT_OFFHAND;
            player.openMenu(new SimpleMenuProvider(
                            (containerId, inventory, p) -> new CoordinatePointerMenu(containerId, inventory, slot),
                            Component.translatable("screen.coordinate_pointer.title")),
                    buffer -> buffer.writeVarInt(slot));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * 物品名 = 基础名字 + 「·」 + 当前指向的那一格（A / B / C … 或者玩家给那一格起的名字）。
     * 这样在物品栏里一眼就能看出这把指针指的是哪个坐标。
     *
     * <p>用铁砧改过名的物品走的是"自定义名字"那条路，见 {@link #inventoryTick}。
     */
    @Override
    public Component getName(ItemStack stack) {
        return PointerLogic.itemName(Component.translatable(this.getDescriptionId(stack)), PointerData.of(stack));
    }

    /**
     * 铁砧改过名之后，显示的名字是玩家自己起的，原版不会再走 {@link #getName}。
     * 所以这里每刻检查一下：名字末尾少了「·当前格」就补上，多了旧的就去掉。
     * 只在服务器改，改完会照常同步给客户端。
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide) {
            return;
        }
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (custom == null) {
            return;
        }
        PointerData data = PointerData.of(stack);
        // 去掉名字里可能已经带着的旧后缀，再用"基础名 + 彩色当前格"重新拼一遍。
        Component wanted = PointerLogic.itemName(Component.literal(PointerLogic.stripSlotSuffix(custom.getString(), data)), data);
        if (!wanted.equals(custom)) {
            stack.set(DataComponents.CUSTOM_NAME, wanted);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        PointerData data = PointerData.of(stack);
        int slot = data.activeSlotSafe();
        TargetEntry target = data.entry(slot);
        tooltip.add(Component.translatable("tooltip.coordinate_pointer.slot",
                        Component.literal(PointerLogic.displayName(target, slot))
                                .withStyle(PointerLogic.slotColor(slot)))
                .withStyle(ChatFormatting.GRAY));
        if (target.set()) {
            tooltip.add(Component.translatable("tooltip.coordinate_pointer.target", target.x(), target.z())
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.coordinate_pointer.empty").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.coordinate_pointer.open").withStyle(ChatFormatting.DARK_GRAY));
        if (AeronauticsCompat.isActive()) {
            tooltip.add(Component.translatable("tooltip.coordinate_pointer.nav_table").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
