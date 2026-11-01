package cn.blockforge.generated.coordinatepointer;

import cn.blockforge.generated.coordinatepointer.data.PointerData;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品上的自定义数据：5 组坐标 + 当前指向哪一组。
 * 用数据组件（data component）存，好处是会自动跟着物品从服务器同步到客户端，
 * 右上角 HUD 才能直接读到。
 */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents TYPES =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CoordinatePointerMod.MOD_ID);

    public static final Supplier<DataComponentType<PointerData>> POINTER_DATA = TYPES.registerComponentType(
            "pointer_data",
            builder -> builder
                    .persistent(PointerData.CODEC)
                    .networkSynchronized(PointerData.STREAM_CODEC));

    private ModDataComponents() {
    }
}
