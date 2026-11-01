package cn.blockforge.generated.coordinatepointer.menu;

import cn.blockforge.generated.coordinatepointer.ModItems;
import cn.blockforge.generated.coordinatepointer.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 坐标指针的界面容器：负责让服务器知道"这个玩家正开着指针界面"，
 * 真正画出来的按钮和输入框在客户端的 CoordinatePointerScreen 里。
 *
 * <p>关键是 {@link #pointerSlot}：开界面的时候就把"是物品栏里第几格的那个指针"记下来，
 * 之后读数据、存数据都只认这一格。
 * 每个指针的坐标本来就各自存在自己的物品数据里，再钉死格子之后，
 * 两个指针不可能再互相串数据（换手、主副手同时拿、物品栏换位都不会）。
 */
public class CoordinatePointerMenu extends AbstractContainerMenu {
    public static final int IMAGE_WIDTH = 200;
    public static final int IMAGE_HEIGHT = 134;

    /** 玩家物品栏（客户端那边就是客户端玩家的物品栏，两边下标一致）。 */
    private final Inventory playerInventory;
    /** 这个界面绑定的是物品栏里的第几格（0~35 是背包/快捷栏，40 是副手）。 */
    private final int pointerSlot;

    /**
     * 这是一块纯控制面板：没有物品格，只有 5 个存档按钮和两个输入框，
     * 所以不需要背包槽位（有槽位反而会出现看不见却点得到的格子）。
     */
    public CoordinatePointerMenu(int containerId, Inventory playerInventory, int pointerSlot) {
        super(ModMenus.COORDINATE_POINTER.get(), containerId);
        this.playerInventory = playerInventory;
        this.pointerSlot = clampSlot(playerInventory, pointerSlot);
    }

    /** 界面绑定的物品格下标。 */
    public int pointerSlot() {
        return this.pointerSlot;
    }

    /** 界面绑定的那一叠物品；格子空了或者被换成别的东西就是空的。 */
    public ItemStack pointerStack() {
        return stackAt(this.playerInventory, this.pointerSlot);
    }

    /** 这个格子里现在还是不是坐标指针。 */
    public boolean hasPointer() {
        return isPointer(pointerStack());
    }

    /** 下标夹到物品栏范围内，防止改包塞个乱七八糟的数字进来。 */
    public static int clampSlot(Inventory inventory, int slot) {
        int size = inventory.getContainerSize();
        return Math.max(0, Math.min(size - 1, slot));
    }

    /** 安全取格子：越界就当空的，不抛异常。 */
    public static ItemStack stackAt(Inventory inventory, int slot) {
        if (slot < 0 || slot >= inventory.getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return inventory.getItem(slot);
    }

    public static boolean isPointer(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.COORDINATE_POINTER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** 绑定的那一格不再是坐标指针（丢掉了、换掉了），界面就自动关掉。 */
    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && isPointer(stackAt(player.getInventory(), this.pointerSlot));
    }
}
