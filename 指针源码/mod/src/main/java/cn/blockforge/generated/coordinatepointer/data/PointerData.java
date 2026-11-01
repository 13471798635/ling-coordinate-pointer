package cn.blockforge.generated.coordinatepointer.data;

import cn.blockforge.generated.coordinatepointer.ModDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * 坐标指针记事本：5 个存档位，外加"现在指哪一格"。
 *
 * @param activeSlot 当前指向的格子下标（0~4）
 * @param targets    5 个存档位的内容
 */
public record PointerData(int activeSlot, List<TargetEntry> targets) {
    public static final int SLOT_COUNT = 5;

    /** 原版允许的坐标上限，超出就没意义了。 */
    public static final int MAX_COORDINATE = 29_999_999;

    public static final PointerData EMPTY = new PointerData(0, List.of());

    public static final Codec<PointerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("active").orElse(0).forGetter(PointerData::activeSlot),
            TargetEntry.CODEC.listOf().fieldOf("slots").orElse(List.of()).forGetter(PointerData::targets)
    ).apply(instance, PointerData::new));

    public static final StreamCodec<ByteBuf, PointerData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PointerData::activeSlot,
            TargetEntry.STREAM_CODEC.apply(ByteBufCodecs.list(SLOT_COUNT)), PointerData::targets,
            PointerData::new);

    /** 从物品上读出记事本，没有就返回空的。 */
    public static PointerData of(ItemStack stack) {
        PointerData data = stack.get(ModDataComponents.POINTER_DATA.get());
        return data == null ? EMPTY : data;
    }

    /** 把记事本写回物品，服务器改完会自动同步给客户端。 */
    public void saveTo(ItemStack stack) {
        stack.set(ModDataComponents.POINTER_DATA.get(), this);
    }

    /** 第 index 格的坐标，没有就返回空位。 */
    public TargetEntry entry(int index) {
        if (index < 0 || index >= this.targets.size()) {
            return TargetEntry.EMPTY;
        }
        return this.targets.get(index);
    }

    /** 当前指向的那一格，可能为空。 */
    public Optional<TargetEntry> activeTarget() {
        TargetEntry entry = entry(this.activeSlot);
        return entry.set() ? Optional.of(entry) : Optional.empty();
    }

    public int activeSlotSafe() {
        return Math.max(0, Math.min(SLOT_COUNT - 1, this.activeSlot));
    }

    /** 存一格新坐标，并顺手把指针切到这一格。 */
    public PointerData withEntry(int index, TargetEntry value) {
        int slot = Math.max(0, Math.min(SLOT_COUNT - 1, index));
        List<TargetEntry> copy = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            copy.add(i == slot ? value : entry(i));
        }
        return new PointerData(slot, List.copyOf(copy));
    }

    /** 只改名字，坐标和当前指向都不动。 */
    public PointerData withName(int index, String name) {
        return withEntry(index, entry(index).withName(name)).withActiveSlot(this.activeSlot);
    }

    /** 只切换指向的格子，不改坐标。 */
    public PointerData withActiveSlot(int index) {
        return new PointerData(Math.max(0, Math.min(SLOT_COUNT - 1, index)), this.targets);
    }

    /** 服务器收到客户端数据后先过一遍：数值夹紧、名字裁短、补齐 5 格，防止被改包。 */
    public PointerData sanitized() {
        List<TargetEntry> copy = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            TargetEntry entry = entry(i);
            int x = clamp(entry.x());
            int z = clamp(entry.z());
            copy.add(new TargetEntry(entry.set(), x, z, TargetEntry.cleanName(entry.name()), entry.dimension()));
        }
        return new PointerData(activeSlotSafe(), List.copyOf(copy));
    }

    private static int clamp(int value) {
        return Math.max(-MAX_COORDINATE, Math.min(MAX_COORDINATE, value));
    }
}
