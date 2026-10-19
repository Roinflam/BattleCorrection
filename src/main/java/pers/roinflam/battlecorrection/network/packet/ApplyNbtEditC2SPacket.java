package pers.roinflam.battlecorrection.network.packet;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import pers.roinflam.battlecorrection.editor.ItemEditorAccess;
import pers.roinflam.battlecorrection.editor.NbtEditAccess;
import pers.roinflam.battlecorrection.editor.NbtEditTarget;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;

/**
 * 客户端 → 服务端：把编辑后的实体 / 方块实体 NBT 写回
 * <p>
 * 校验：功能开关 → OP 且创造 → 目标还在（且不是玩家、在距离内）。NBT 大小不限（走 {@link ChunkedPacket} 分片）。写法见 {@link NbtEditAccess}。
 */
public class ApplyNbtEditC2SPacket {

    private final NbtEditTarget target;
    private final NBTTagCompound tag;

    public ApplyNbtEditC2SPacket(@Nonnull NbtEditTarget target, @Nonnull NBTTagCompound tag) {
        this.target = target;
        this.tag = tag;
    }

    public void encode(@Nonnull PacketBuffer buf) {
        target.write(buf);
        ChunkedPacket.writeNbt(buf, tag);
    }

    @Nonnull
    public static ApplyNbtEditC2SPacket decode(@Nonnull PacketBuffer buf) {
        NbtEditTarget target = NbtEditTarget.read(buf);
        NBTTagCompound tag = ChunkedPacket.readNbt(buf, ChunkedPacket.VANILLA_NBT_QUOTA);
        return new ApplyNbtEditC2SPacket(target, tag == null ? new NBTTagCompound() : tag);
    }

    public void handle(@Nonnull MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        EntityPlayerMP player = ctx.getServerHandler().player;
        if (player == null) {
            return;
        }
        if (!ItemEditorAccess.isEnabled()) {
            player.sendMessage(ItemEditorAccess.disabledMessage());
            return;
        }
        if (!ItemEditorAccess.canUse(player)) {
            player.sendMessage(ItemEditorAccess.noPermissionMessage());
            return;
        }

        if (target.isEntity()) {
            Entity entity = NbtEditAccess.resolveEntity(player, target.entityId());
            if (entity == null) {
                player.sendMessage(ChunkedPacket.red("message.battlecorrection.nbt.no_entity"));
                return;
            }
            NbtEditAccess.write(entity, tag);
            player.sendMessage(ChunkedPacket.green("message.battlecorrection.nbt.saved", NbtEditAccess.name(entity)));
            LogUtil.info(String.format("%s 用 NBT 编辑器修改了实体 %s（%s）", player.getName(),
                    NbtEditAccess.name(entity).getUnformattedText(), entity.getUniqueID()));
        } else {
            TileEntity blockEntity = NbtEditAccess.resolveBlockEntity(player, target.pos());
            if (blockEntity == null) {
                player.sendMessage(ChunkedPacket.red("message.battlecorrection.nbt.no_block_entity"));
                return;
            }
            NbtEditAccess.write(blockEntity, tag);
            player.sendMessage(ChunkedPacket.green("message.battlecorrection.nbt.saved", NbtEditAccess.name(blockEntity)));
            LogUtil.info(String.format("%s 用 NBT 编辑器修改了方块实体 %s @ %s", player.getName(),
                    NbtEditAccess.name(blockEntity).getUnformattedText(), target.pos()));
        }
    }
}
