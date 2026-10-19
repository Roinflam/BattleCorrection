package pers.roinflam.battlecorrection.editor;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 实体 / 方块实体 NBT 的服务端读写（照原版 /entitydata、/blockdata 命令的做法）
 * <p>
 * 读：实体用 writeToNBT（不带 id，编辑器不允许换实体类型），方块实体用 writeToNBT（带 id 和坐标）。
 * 写：实体读入之后把 UUID 塞回去（readFromNBT 会把 UUID 也读进来，防止被改）；
 * 方块实体读入前把坐标改回真实位置（1.12.2 的 readFromNBT 会按 x/y/z 挪方块实体），读入后 markDirty 并广播方块更新让客户端刷新。
 * 玩家实体一律不允许编辑，和 /entitydata 一样。
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
    public static Entity resolveEntity(@Nonnull EntityPlayerMP player, int entityId) {
        WorldServer world = player.getServerWorld();
        Entity entity = world.getEntityByID(entityId);
        if (entity == null || !entity.isEntityAlive() || entity instanceof EntityPlayer) {
            return null;
        }
        if (player.getDistanceSq(entity) > NbtEditTarget.MAX_DISTANCE * NbtEditTarget.MAX_DISTANCE) {
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
    public static TileEntity resolveBlockEntity(@Nonnull EntityPlayerMP player, @Nonnull BlockPos pos) {
        WorldServer world = player.getServerWorld();
        if (!world.isBlockLoaded(pos)) {
            return null;
        }
        double distance = player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (distance > NbtEditTarget.MAX_DISTANCE * NbtEditTarget.MAX_DISTANCE) {
            return null;
        }
        return world.getTileEntity(pos);
    }

    @Nonnull
    public static NBTTagCompound read(@Nonnull Entity entity) {
        return entity.writeToNBT(new NBTTagCompound());
    }

    @Nonnull
    public static NBTTagCompound read(@Nonnull TileEntity blockEntity) {
        return blockEntity.writeToNBT(new NBTTagCompound());
    }

    /**
     * 写回实体（UUID 保持不变）
     */
    public static void write(@Nonnull Entity entity, @Nonnull NBTTagCompound tag) {
        UUID uuid = entity.getUniqueID();
        try {
            entity.readFromNBT(tag);
        } finally {
            // readFromNBT 先读 UUID、后读子类数据，子类读到坏数据抛异常时（比如下落方块的 Block 写了不存在的方块）
            // UUID 已经换成了 NBT 里的，出异常也要换回来
            entity.setUniqueId(uuid);
        }
    }

    /**
     * 写回方块实体并通知客户端刷新
     */
    public static void write(@Nonnull TileEntity blockEntity, @Nonnull NBTTagCompound tag) {
        BlockPos pos = blockEntity.getPos();
        NBTTagCompound copy = tag.copy();
        copy.setInteger("x", pos.getX());
        copy.setInteger("y", pos.getY());
        copy.setInteger("z", pos.getZ());
        blockEntity.readFromNBT(copy);
        blockEntity.markDirty();
        World world = blockEntity.getWorld();
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Nonnull
    public static ITextComponent name(@Nonnull Entity entity) {
        return entity.getDisplayName();
    }

    @Nonnull
    public static ITextComponent name(@Nonnull TileEntity blockEntity) {
        World world = blockEntity.getWorld();
        IBlockState state = world == null ? blockEntity.getBlockType().getDefaultState() : world.getBlockState(blockEntity.getPos());
        return new TextComponentTranslation(state.getBlock().getUnlocalizedName() + ".name");
    }
}
