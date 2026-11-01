package cn.blockforge.generated.coordinatepointer;

import cn.blockforge.generated.coordinatepointer.client.ClientInit;
import cn.blockforge.generated.coordinatepointer.compat.AeronauticsCompat;
import cn.blockforge.generated.coordinatepointer.net.PointerNetwork;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * 坐标指针：一个让你手动录入 X/Z 坐标、存 5 组、并且指针一直指向目标的指南针。
 */
@Mod(CoordinatePointerMod.MOD_ID)
public final class CoordinatePointerMod {
    public static final String MOD_ID = "coordinate_pointer";

    public CoordinatePointerMod(IEventBus modBus) {
        ModItems.ITEMS.register(modBus);
        ModDataComponents.TYPES.register(modBus);
        ModMenus.MENUS.register(modBus);
        modBus.addListener(PointerNetwork::register);
        modBus.addListener(CoordinatePointerMod::addToCreativeTab);

        // 客户端专属的内容（界面、HUD、贴图旋转）只在客户端注册，
        // 这样专用服务器开服时不会因为找不到客户端类而崩。
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.register(modBus);
        }

        // 装了「机械动力：航空学」的话，顺便把指针接进它的导航台；没装就什么都不做。
        AeronauticsCompat.bootstrap(modBus);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.COORDINATE_POINTER.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
