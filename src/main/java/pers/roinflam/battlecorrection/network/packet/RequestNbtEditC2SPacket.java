package pers.roinflam.battlecorrection.network.packet;

import pers.roinflam.battlecorrection.utils.Reference;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.editor.ItemEditorAccess;
import pers.roinflam.battlecorrection.editor.NbtEditAccess;
import pers.roinflam.battlecorrection.editor.NbtEditTarget;

import javax.annotation.Nonnull;

/**
 * 客户端 → 服务端：请求编辑看着的实体 / 方块实体
 * <p>
 * 客户端手里没有完整的实体 NBT（同步给客户端的只是一部分），所以要先找服务端要。
 * 服务端校验 OP + 创造 + 目标合法后，把 NBT 连同名字用 {@link OpenNbtEditorS2CPacket} 发回去开界面。
 */
public class RequestNbtEditC2SPacket implements CustomPacketPayload {

    public static final Type<RequestNbtEditC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "request_nbt_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestNbtEditC2SPacket> STREAM_CODEC =
            CustomPacketPayload.codec(RequestNbtEditC2SPacket::encode, RequestNbtEditC2SPacket::decode);

    private final NbtEditTarget target;
    private final EditorPage page;

    public RequestNbtEditC2SPacket(@Nonnull NbtEditTarget target, @Nonnull EditorPage page) {
        this.target = target;
        this.page = page;
    }

    public void encode(@Nonnull FriendlyByteBuf buf) {
        target.write(buf);
        buf.writeVarInt(page.ordinal());
    }

    @Nonnull
    public static RequestNbtEditC2SPacket decode(@Nonnull FriendlyByteBuf buf) {
        NbtEditTarget target = NbtEditTarget.read(buf);
        EditorPage page = EditorPage.byOrdinal(buf.readVarInt());
        return new RequestNbtEditC2SPacket(target, page);
    }

    public void handle(@Nonnull IPayloadContext ctx) {
        ServerPlayer player = ctx.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
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

        CompoundTag tag;
        Component name;
        if (target.isEntity()) {
            Entity entity = NbtEditAccess.resolveEntity(player, target.entityId());
            if (entity == null) {
                player.sendSystemMessage(Component.translatable("message.battlecorrection.nbt.no_entity").withStyle(ChatFormatting.RED));
                return;
            }
            tag = NbtEditAccess.read(entity);
            name = NbtEditAccess.name(entity);
        } else {
            BlockEntity blockEntity = NbtEditAccess.resolveBlockEntity(player, target.pos());
            if (blockEntity == null) {
                player.sendSystemMessage(Component.translatable("message.battlecorrection.nbt.no_block_entity").withStyle(ChatFormatting.RED));
                return;
            }
            tag = NbtEditAccess.read(blockEntity);
            name = NbtEditAccess.name(blockEntity);
        }
        OpenNbtEditorS2CPacket open = new OpenNbtEditorS2CPacket(target, tag, name, page);
        ChunkedPacket.sendToPlayer(player, ChunkedPacket.Kind.OPEN_NBT, open::encode);
    }

    @Nonnull
    @Override
    public Type<RequestNbtEditC2SPacket> type() {
        return TYPE;
    }
}
