package pers.roinflam.battlecorrection.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.editor.ItemEditorAccess;
import pers.roinflam.battlecorrection.editor.NbtEditAccess;
import pers.roinflam.battlecorrection.editor.NbtEditTarget;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：把编辑后的实体 / 方块实体 NBT 写回
 * <p>
 * 校验：功能开关 → OP 且创造 → 目标还在（且不是玩家、在距离内）→ NBT 大小没超上限。写法见 {@link NbtEditAccess}。
 */
public class ApplyNbtEditC2SPacket {

    private final NbtEditTarget target;
    private final CompoundTag tag;

    public ApplyNbtEditC2SPacket(@Nonnull NbtEditTarget target, @Nonnull CompoundTag tag) {
        this.target = target;
        this.tag = tag;
    }

    public void encode(@Nonnull FriendlyByteBuf buf) {
        target.write(buf);
        buf.writeNbt(tag);
    }

    @Nonnull
    public static ApplyNbtEditC2SPacket decode(@Nonnull FriendlyByteBuf buf) {
        NbtEditTarget target = NbtEditTarget.read(buf);
        CompoundTag tag = buf.readNbt();
        return new ApplyNbtEditC2SPacket(target, tag == null ? new CompoundTag() : tag);
    }

    public void handle(@Nonnull Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) {
            return;
        }
        if (!ItemEditorAccess.isEnabled()) {
            player.sendSystemMessage(ItemEditorAccess.disabledMessage());
            return;
        }
        if (!ItemEditorAccess.canUse(player)) {
            player.sendSystemMessage(ItemEditorAccess.noPermissionMessage());
            return;
        }

        FriendlyByteBuf sizer = new FriendlyByteBuf(Unpooled.buffer());
        int bytes;
        try {
            sizer.writeNbt(tag);
            bytes = sizer.readableBytes();
        } finally {
            sizer.release();
        }
        if (bytes > ItemEditorAccess.maxNbtBytes()) {
            player.sendSystemMessage(Component.translatable("message.battlecorrection.editor.save_failed.too_large",
                    ConfigAttribute.ITEM_EDITOR_MAX_NBT_KB.get()).withStyle(ChatFormatting.RED));
            return;
        }

        if (target.isEntity()) {
            Entity entity = NbtEditAccess.resolveEntity(player, target.entityId());
            if (entity == null) {
                player.sendSystemMessage(Component.translatable("message.battlecorrection.nbt.no_entity").withStyle(ChatFormatting.RED));
                return;
            }
            NbtEditAccess.write(entity, tag);
            player.sendSystemMessage(Component.translatable("message.battlecorrection.nbt.saved", NbtEditAccess.name(entity)).withStyle(ChatFormatting.GREEN));
            LogUtil.info(String.format("%s 用 NBT 编辑器修改了实体 %s（%s）", player.getName().getString(),
                    NbtEditAccess.name(entity).getString(), entity.getUUID()));
        } else {
            BlockEntity blockEntity = NbtEditAccess.resolveBlockEntity(player, target.pos());
            if (blockEntity == null) {
                player.sendSystemMessage(Component.translatable("message.battlecorrection.nbt.no_block_entity").withStyle(ChatFormatting.RED));
                return;
            }
            NbtEditAccess.write(blockEntity, tag);
            player.sendSystemMessage(Component.translatable("message.battlecorrection.nbt.saved", NbtEditAccess.name(blockEntity)).withStyle(ChatFormatting.GREEN));
            LogUtil.info(String.format("%s 用 NBT 编辑器修改了方块实体 %s @ %s", player.getName().getString(),
                    NbtEditAccess.name(blockEntity).getString(), target.pos()));
        }
    }
}
