package cn.blockforge.generated.coordinatepointer;

import cn.blockforge.generated.coordinatepointer.item.CoordinatePointerItem;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 所有物品的注册表。 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CoordinatePointerMod.MOD_ID);

    public static final DeferredItem<CoordinatePointerItem> COORDINATE_POINTER = ITEMS.registerItem(
            "coordinate_pointer",
            CoordinatePointerItem::new,
            new net.minecraft.world.item.Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE));

    private ModItems() {
    }
}
