package pers.roinflam.battlecorrection.editor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 实体 / 方块实体 NBT 的服务端读写（照原版 /data 命令的做法）
 * <p>
 * 读：实体用 saveWithoutId（不带 id，编辑器不允许换实体类型），方块实体用 saveWithFullMetadata（带 id 和坐标，load 时会忽略）。
 * 写：实体 load 之后把 UUID 塞回去（load 会把 UUID 也读进来，防止被改）；方块实体 load 后 setChanged 并广播方块更新让客户端刷新。
 * 玩家实体一律不允许编辑，和 /data 一样。
 * <p>
 * 1.21.1：方块实体的读写要带注册表（{@code saveWithFullMetadata(Provider)} / {@code loadWithComponents(tag, Provider)}，
 * 后者和 /data 命令一样连同 NBT 里的 components 一起读），行为和 1.20.1 的 {@code load} 相同。
 */
public final class NbtEditAccess {

    private NbtEditAccess() {
    }

    /**
     * 找目标实体：存在、活着、不是玩家、离操作者不超过 MAX_DISTANCE
     *
     * @param player   操作者
     * @param entityId 实体网络 ID
     * @return 实体；不满足返回 null
     */
    @Nullable
    public static Entity resolveEntity(@Nonnull ServerPlayer player, int entityId) {
        ServerLevel level = player.serverLevel();
        Entity entity = level.getEntity(entityId);
        if (entity == null || !entity.isAlive() || entity instanceof Player) {
            return null;
        }
        if (player.distanceToSqr(entity) > NbtEditTarget.MAX_DISTANCE * NbtEditTarget.MAX_DISTANCE) {
            return null;
        }
        return entity;
    }

    /**
     * 找目标方块实体：区块已加载、位置上有方块实体、离操作者不超过 MAX_DISTANCE
     *
     * @param player 操作者
     * @param pos    坐标
     * @return 方块实体；不满足返回 null
     */
    @Nullable
    public static BlockEntity resolveBlockEntity(@Nonnull ServerPlayer player, @Nonnull BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        double distance = player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (distance > NbtEditTarget.MAX_DISTANCE * NbtEditTarget.MAX_DISTANCE) {
            return null;
        }
        return level.getBlockEntity(pos);
    }

    @Nonnull
    public static CompoundTag read(@Nonnull Entity entity) {
        return entity.saveWithoutId(new CompoundTag());
    }

    @Nonnull
    public static CompoundTag read(@Nonnull BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        return blockEntity.saveWithFullMetadata(level != null ? level.registryAccess()
                : RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    /**
     * 写回实体（UUID 保持不变）
     */
    public static void write(@Nonnull Entity entity, @Nonnull CompoundTag tag) {
        UUID uuid = entity.getUUID();
        try {
            entity.load(tag);
        } finally {
            // load 先读 UUID、后读子类数据，子类读到坏数据抛异常时 UUID 已经换成了 NBT 里的，出异常也要换回来
            entity.setUUID(uuid);
        }
    }

    /**
     * 写回方块实体并通知客户端刷新
     */
    public static void write(@Nonnull BlockEntity blockEntity, @Nonnull CompoundTag tag) {
        Level level = blockEntity.getLevel();
        blockEntity.loadWithComponents(tag, level != null ? level.registryAccess()
                : RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        blockEntity.setChanged();
        if (level != null) {
            BlockPos pos = blockEntity.getBlockPos();
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
    }

    @Nonnull
    public static Component name(@Nonnull Entity entity) {
        return entity.getDisplayName();
    }

    @Nonnull
    public static Component name(@Nonnull BlockEntity blockEntity) {
        return blockEntity.getBlockState().getBlock().getName();
    }
}
