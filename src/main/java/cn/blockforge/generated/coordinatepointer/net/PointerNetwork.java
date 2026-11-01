package cn.blockforge.generated.coordinatepointer.net;

import cn.blockforge.generated.coordinatepointer.CoordinatePointerMod;
import cn.blockforge.generated.coordinatepointer.PointerLogic;
import cn.blockforge.generated.coordinatepointer.data.PointerData;
import cn.blockforge.generated.coordinatepointer.data.TargetEntry;
import cn.blockforge.generated.coordinatepointer.menu.CoordinatePointerMenu;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 客户端把"整份坐标记事本"发给服务器，服务器检查一遍后写回**指定的那一格物品**。
 *
 * <p>包里带着物品栏下标，所以就算玩家同时拿着两把指针、或者开着界面的同时换了手，
 * 数据也只会写进他自己打开的那一把，不会串到别的指针上。
 */
public final class PointerNetwork {
    private PointerNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // 版本号跟着包格式走：这次多了一个"物品格"字段。
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToServer(UpdatePointerPayload.TYPE, UpdatePointerPayload.STREAM_CODEC, PointerNetwork::handleUpdate);
    }

    private static void handleUpdate(UpdatePointerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            int slot = resolveSlot(player, payload.pointerSlot());
            ItemStack stack = CoordinatePointerMenu.stackAt(player.getInventory(), slot);
            if (!CoordinatePointerMenu.isPointer(stack)) {
                // 那一格已经不是指针了（丢掉了 / 换掉了），什么都不写，免得写到别的东西上。
                return;
            }
            PointerData before = PointerData.of(stack);
            PointerData after = withServerDimension(payload.data().sanitized(), before, player);
            after.saveTo(stack);
            syncSlot(player, slot, stack);

            int active = after.activeSlotSafe();
            TargetEntry entry = after.entry(active);
            if (payload.announce() && coordsChanged(before.entry(active), entry)) {
                if (entry.set()) {
                    player.displayClientMessage(Component.translatable("message.coordinate_pointer.saved",
                            PointerLogic.displayName(entry, active), entry.x(), entry.z()), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.coordinate_pointer.cleared",
                            PointerLogic.displayName(entry, active)), true);
                }
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.PLAYERS, 0.35F, 1.7F);
        });
    }

    /**
     * 认准"要写哪一格物品"：优先用包里带的下标；
     * 万一那一格已经不是指针（比如数据有点偏差），就用玩家当前开着的界面绑定的格子；
     * 两个都不对的话调用处会直接放弃这次写入，绝不会写到别的物品上。
     */
    private static int resolveSlot(ServerPlayer player, int claimed) {
        int slot = CoordinatePointerMenu.clampSlot(player.getInventory(), claimed);
        if (CoordinatePointerMenu.isPointer(CoordinatePointerMenu.stackAt(player.getInventory(), slot))) {
            return slot;
        }
        if (player.containerMenu instanceof CoordinatePointerMenu menu) {
            return menu.pointerSlot();
        }
        return slot;
    }

    /**
     * 客户端填的"哪个世界"不可信：坐标真变了就用服务器这边玩家所在的世界，
     * 只改名字的时候保留原来记录的世界。
     */
    private static PointerData withServerDimension(PointerData sanitized, PointerData before, ServerPlayer player) {
        ResourceLocation dimension = player.level().dimension().location();
        List<TargetEntry> entries = new ArrayList<>(PointerData.SLOT_COUNT);
        for (int i = 0; i < PointerData.SLOT_COUNT; i++) {
            TargetEntry entry = sanitized.entry(i);
            boolean changed = coordsChanged(before.entry(i), entry);
            entries.add(changed && entry.set()
                    ? new TargetEntry(true, entry.x(), entry.z(), entry.name(), dimension)
                    : entry);
        }
        return new PointerData(sanitized.activeSlotSafe(), List.copyOf(entries));
    }

    private static boolean coordsChanged(TargetEntry before, TargetEntry after) {
        return before.set() != after.set() || (after.set() && (before.x() != after.x() || before.z() != after.z()));
    }

    /**
     * 数据组件改完之后，把变化推给客户端，指针贴图和面板才能立刻刷新。
     *
     * <p>除了常规的"整菜单比对同步"，再单独把这一格发一次（{@code -2} 就是"玩家物品栏"）：
     * 这样客户端手里那一叠指针的数据一定是新的，再打开界面看到的就是自己刚存的坐标，
     * 不会出现"看着像另一把指针的数据"这种情况。
     */
    private static void syncSlot(ServerPlayer player, int slot, ItemStack stack) {
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, slot, stack.copy()));
    }

    /** 整份记事本：5 格里存了什么、当前指哪一格，外加"这份数据属于物品栏的第几格"。 */
    public record UpdatePointerPayload(PointerData data, int pointerSlot, boolean announce)
            implements CustomPacketPayload {
        public static final Type<UpdatePointerPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CoordinatePointerMod.MOD_ID, "update_pointer"));

        public static final StreamCodec<ByteBuf, UpdatePointerPayload> STREAM_CODEC = StreamCodec.composite(
                PointerData.STREAM_CODEC, UpdatePointerPayload::data,
                ByteBufCodecs.VAR_INT, UpdatePointerPayload::pointerSlot,
                ByteBufCodecs.BOOL, UpdatePointerPayload::announce,
                UpdatePointerPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
