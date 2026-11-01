package cn.blockforge.generated.coordinatepointer.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 一个坐标存档位：有没有存、X、Z、玩家自己起的名字、存在哪个世界。
 *
 * @param set       这一格是否已经录入了坐标
 * @param x         目标 X
 * @param z         目标 Z
 * @param name      玩家给这一格起的名字，可以是空的
 * @param dimension 录入时所在的维度，比如 minecraft:overworld
 */
public record TargetEntry(boolean set, int x, int z, String name, ResourceLocation dimension) {
    /** 名字最多几个字，太长了界面上放不下。 */
    public static final int MAX_NAME_LENGTH = 16;

    /** 没存过坐标的空位。 */
    public static final TargetEntry EMPTY =
            new TargetEntry(false, 0, 0, "", ResourceLocation.parse("minecraft:overworld"));

    public static final Codec<TargetEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("set").orElse(false).forGetter(TargetEntry::set),
            Codec.INT.fieldOf("x").orElse(0).forGetter(TargetEntry::x),
            Codec.INT.fieldOf("z").orElse(0).forGetter(TargetEntry::z),
            Codec.STRING.fieldOf("name").orElse("").forGetter(TargetEntry::name),
            ResourceLocation.CODEC.fieldOf("dimension").orElse(EMPTY.dimension()).forGetter(TargetEntry::dimension)
    ).apply(instance, TargetEntry::new));

    private static final StreamCodec<ByteBuf, ResourceLocation> DIMENSION_STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(ResourceLocation::parse, ResourceLocation::toString);

    public static final StreamCodec<ByteBuf, TargetEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TargetEntry::set,
            ByteBufCodecs.VAR_INT, TargetEntry::x,
            ByteBufCodecs.VAR_INT, TargetEntry::z,
            ByteBufCodecs.STRING_UTF8, TargetEntry::name,
            DIMENSION_STREAM_CODEC, TargetEntry::dimension,
            TargetEntry::new);

    /** 只换名字，坐标不动。 */
    public TargetEntry withName(String newName) {
        return new TargetEntry(this.set, this.x, this.z, cleanName(newName), this.dimension);
    }

    /** 清掉坐标但保留名字：这样"清空这一格"不会把玩家起的名字一起丢了。 */
    public TargetEntry cleared() {
        return new TargetEntry(false, 0, 0, this.name, this.dimension);
    }

    /** 把名字裁到合法长度，避免客户端塞进超长字符串。 */
    public static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.length() <= MAX_NAME_LENGTH ? trimmed : trimmed.substring(0, MAX_NAME_LENGTH);
    }
}
