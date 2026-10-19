package pers.roinflam.battlecorrection.editor;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * 实体 / 方块实体 NBT 编辑的目标
 * <p>
 * 实体用网络 ID（客户端和服务端在同一个世界里是一致的），方块实体用坐标。
 * 服务端处理时都会再检查一遍：实体还活着、不是玩家、离玩家不超过 {@link #MAX_DISTANCE} 格；方块实体所在区块已加载。
 */
public final class NbtEditTarget {

    /**
     * 允许编辑的最远距离（格）
     */
    public static final double MAX_DISTANCE = 16.0D;

    public enum Kind {
        ENTITY,
        BLOCK_ENTITY
    }

    @Nonnull
    private final Kind kind;
    private final int entityId;
    @Nonnull
    private final BlockPos pos;

    /**
     * @param kind     类型
     * @param entityId 实体网络 ID（方块实体时为 -1）
     * @param pos      方块坐标（实体时为原点，不用）
     */
    public NbtEditTarget(@Nonnull Kind kind, int entityId, @Nonnull BlockPos pos) {
        this.kind = kind;
        this.entityId = entityId;
        this.pos = pos;
    }

    @Nonnull
    public Kind kind() {
        return kind;
    }

    public int entityId() {
        return entityId;
    }

    @Nonnull
    public BlockPos pos() {
        return pos;
    }

    @Nonnull
    public static NbtEditTarget entity(int entityId) {
        return new NbtEditTarget(Kind.ENTITY, entityId, BlockPos.ORIGIN);
    }

    @Nonnull
    public static NbtEditTarget blockEntity(@Nonnull BlockPos pos) {
        return new NbtEditTarget(Kind.BLOCK_ENTITY, -1, pos.toImmutable());
    }

    public boolean isEntity() {
        return kind == Kind.ENTITY;
    }

    public void write(@Nonnull PacketBuffer buf) {
        buf.writeEnumValue(kind);
        buf.writeVarInt(entityId);
        buf.writeBlockPos(pos);
    }

    @Nonnull
    public static NbtEditTarget read(@Nonnull PacketBuffer buf) {
        Kind kind = buf.readEnumValue(Kind.class);
        int entityId = buf.readVarInt();
        BlockPos pos = buf.readBlockPos();
        return new NbtEditTarget(kind, entityId, pos);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NbtEditTarget)) {
            return false;
        }
        NbtEditTarget other = (NbtEditTarget) o;
        return kind == other.kind && entityId == other.entityId && pos.equals(other.pos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, entityId, pos);
    }

    @Override
    public String toString() {
        return "NbtEditTarget[kind=" + kind + ", entityId=" + entityId + ", pos=" + pos + "]";
    }
}
