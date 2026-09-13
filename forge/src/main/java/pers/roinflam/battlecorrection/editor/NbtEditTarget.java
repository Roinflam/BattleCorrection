package pers.roinflam.battlecorrection.editor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nonnull;

/**
 * 实体 / 方块实体 NBT 编辑的目标
 * <p>
 * 实体用网络 ID（客户端和服务端在同一个世界里是一致的），方块实体用坐标。
 * 服务端处理时都会再检查一遍：实体还活着、不是玩家、离玩家不超过 {@link #MAX_DISTANCE} 格；方块实体所在区块已加载。
 *
 * @param kind     类型
 * @param entityId 实体网络 ID（方块实体时为 -1）
 * @param pos      方块坐标（实体时为原点，不用）
 */
public record NbtEditTarget(Kind kind, int entityId, BlockPos pos) {

    /**
     * 允许编辑的最远距离（格）
     */
    public static final double MAX_DISTANCE = 16.0D;

    public enum Kind {
        ENTITY,
        BLOCK_ENTITY
    }

    @Nonnull
    public static NbtEditTarget entity(int entityId) {
        return new NbtEditTarget(Kind.ENTITY, entityId, BlockPos.ZERO);
    }

    @Nonnull
    public static NbtEditTarget blockEntity(@Nonnull BlockPos pos) {
        return new NbtEditTarget(Kind.BLOCK_ENTITY, -1, pos.immutable());
    }

    public boolean isEntity() {
        return kind == Kind.ENTITY;
    }

    public void write(@Nonnull FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeVarInt(entityId);
        buf.writeBlockPos(pos);
    }

    @Nonnull
    public static NbtEditTarget read(@Nonnull FriendlyByteBuf buf) {
        Kind kind = buf.readEnum(Kind.class);
        int entityId = buf.readVarInt();
        BlockPos pos = buf.readBlockPos();
        return new NbtEditTarget(kind, entityId, pos);
    }
}
