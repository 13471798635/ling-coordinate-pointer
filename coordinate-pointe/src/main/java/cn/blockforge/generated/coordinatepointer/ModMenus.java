package cn.blockforge.generated.coordinatepointer;

import cn.blockforge.generated.coordinatepointer.menu.CoordinatePointerMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 界面容器的注册表：右键打开的那块面板。 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, CoordinatePointerMod.MOD_ID);

    /**
     * 打开界面时会多带一个数字过去：这个界面绑定的是物品栏里的第几格。
     * 服务器和客户端读的是同一个数字，所以客户端看到的、服务器存下的，
     * 永远是同一件物品自己的坐标。
     */
    public static final Supplier<MenuType<CoordinatePointerMenu>> COORDINATE_POINTER = MENUS.register(
            "coordinate_pointer",
            () -> IMenuTypeExtension.create(
                    (containerId, inventory, buffer) ->
                            new CoordinatePointerMenu(containerId, inventory, buffer.readVarInt())));

    private ModMenus() {
    }
}
