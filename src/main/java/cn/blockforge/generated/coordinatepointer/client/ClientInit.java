package cn.blockforge.generated.coordinatepointer.client;

import cn.blockforge.generated.coordinatepointer.CoordinatePointerMod;
import cn.blockforge.generated.coordinatepointer.ModItems;
import cn.blockforge.generated.coordinatepointer.ModMenus;
import cn.blockforge.generated.coordinatepointer.PointerLogic;
import cn.blockforge.generated.coordinatepointer.data.PointerData;
import cn.blockforge.generated.coordinatepointer.data.TargetEntry;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** 客户端专属的接线：界面、HUD、贴图旋转、到达提示音。只在客户端加载。 */
public final class ClientInit {
    public static final ResourceLocation NEEDLE_ANGLE =
            ResourceLocation.fromNamespaceAndPath(CoordinatePointerMod.MOD_ID, "angle");
    private static final ResourceLocation HUD_LAYER =
            ResourceLocation.fromNamespaceAndPath(CoordinatePointerMod.MOD_ID, "pointer_hud");

    private static int arriveSoundCooldown;

    private ClientInit() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientInit::registerScreens);
        modBus.addListener(ClientInit::registerGuiLayers);
        modBus.addListener(ClientInit::clientSetup);
        NeoForge.EVENT_BUS.addListener(ClientInit::clientTick);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.COORDINATE_POINTER.get(), CoordinatePointerScreen::new);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, HUD_LAYER, PointerHud::render);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.COORDINATE_POINTER.get(),
                NEEDLE_ANGLE,
                ClientInit::needleAngle));
    }

    /**
     * 指针贴图该用第几个方向（0~15）。分三种情况：
     *
     * <ul>
     *   <li>拿在手上 / 画在界面里：渲染器会把玩家给我们，这时按玩家朝向算，
     *       目标在正前方指针就朝上；</li>
     *   <li>摆在世界里（展示框里的、掉在地上的）：没有玩家，只给一个 entityId，
     *       顺着这个 id 找到那个实体本身，按它的实际摆法算出世界方向；</li>
     *   <li>被别的模组的方块"画"出来（航空学的导航台就是这种）：什么都不给，
     *       这时一律用"指针朝上"的默认图。导航台会把整个物品按目标方位转一圈，
     *       所以指针最后还是指着坐标的方向，也不会和方块自己的旋转打架。</li>
     * </ul>
     */
    private static float needleAngle(ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
        Optional<TargetEntry> maybeTarget = PointerData.of(stack).activeTarget();
        if (maybeTarget.isEmpty()) {
            return 0.0F;
        }
        TargetEntry target = maybeTarget.get();
        Entity holder = entity;
        // 展示框、掉落物这类渲染只给 entityId，不给实体本身，得自己顺着 id 找回来。
        if (holder == null && level != null && seed != 0) {
            holder = level.getEntity(seed);
        }
        if (holder instanceof Player player) {
            return PointerLogic.needleBucket(player, target);
        }
        if (holder != null) {
            return NeedleAngles.bucketFor(holder, target);
        }
        return 0.0F;
    }

    /** 走进 5 格以内时，隔几秒响一声清脆的铃音。 */
    private static void clientTick(ClientTickEvent.Post event) {
        if (arriveSoundCooldown > 0) {
            arriveSoundCooldown--;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        ItemStack stack = PointerLogic.findHeldPointer(player);
        if (stack.isEmpty()) {
            return;
        }
        TargetEntry target = PointerLogic.activeTarget(stack).orElse(null);
        if (target == null || !PointerLogic.sameDimension(player, target)) {
            return;
        }
        if (PointerLogic.horizontalDistance(player, target) >= PointerLogic.ARRIVE_RADIUS) {
            return;
        }
        if (arriveSoundCooldown > 0) {
            return;
        }
        arriveSoundCooldown = 80;
        minecraft.level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.5F, 1.6F, false);
    }
}
