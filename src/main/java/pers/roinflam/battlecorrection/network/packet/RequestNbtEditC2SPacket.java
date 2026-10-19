package pers.roinflam.battlecorrection.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
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
public class RequestNbtEditC2SPacket implements IMessage {

    private NbtEditTarget target = NbtEditTarget.entity(-1);
    private EditorPage page = EditorPage.TREE;

    /**
     * 收包用（网络层反射创建）
     */
    public RequestNbtEditC2SPacket() {
    }

    public RequestNbtEditC2SPacket(@Nonnull NbtEditTarget target, @Nonnull EditorPage page) {
        this.target = target;
        this.page = page;
    }

    public void encode(@Nonnull PacketBuffer buf) {
        target.write(buf);
        buf.writeVarInt(page.ordinal());
    }

    @Nonnull
    public static RequestNbtEditC2SPacket decode(@Nonnull PacketBuffer buf) {
        NbtEditTarget target = NbtEditTarget.read(buf);
        EditorPage page = EditorPage.byOrdinal(buf.readVarInt());
        return new RequestNbtEditC2SPacket(target, page);
    }

    @Override
    public void toBytes(@Nonnull ByteBuf buf) {
        encode(new PacketBuffer(buf));
    }

    @Override
    public void fromBytes(@Nonnull ByteBuf buf) {
        RequestNbtEditC2SPacket decoded = decode(new PacketBuffer(buf));
        this.target = decoded.target;
        this.page = decoded.page;
    }

    /**
     * 处理（服务端主线程）
     *
     * @param player 发包的玩家
     */
    public void handle(@Nonnull EntityPlayerMP player) {
        if (!ItemEditorAccess.isEnabled()) {
            player.sendMessage(ItemEditorAccess.disabledMessage());
            return;
        }
        if (!ItemEditorAccess.canUse(player)) {
            player.sendMessage(ItemEditorAccess.noPermissionMessage());
            return;
        }

        NBTTagCompound tag;
        ITextComponent name;
        if (target.isEntity()) {
            Entity entity = NbtEditAccess.resolveEntity(player, target.entityId());
            if (entity == null) {
                player.sendMessage(ChunkedPacket.red("message.battlecorrection.nbt.no_entity"));
                return;
            }
            tag = NbtEditAccess.read(entity);
            name = NbtEditAccess.name(entity);
        } else {
            TileEntity blockEntity = NbtEditAccess.resolveBlockEntity(player, target.pos());
            if (blockEntity == null) {
                player.sendMessage(ChunkedPacket.red("message.battlecorrection.nbt.no_block_entity"));
                return;
            }
            tag = NbtEditAccess.read(blockEntity);
            name = NbtEditAccess.name(blockEntity);
        }
        OpenNbtEditorS2CPacket open = new OpenNbtEditorS2CPacket(target, tag, name, page);
        ChunkedPacket.sendToPlayer(player, ChunkedPacket.Kind.OPEN_NBT, open::encode);
    }

    /**
     * 处理器：切到服务端主线程
     */
    public static final class Handler implements IMessageHandler<RequestNbtEditC2SPacket, IMessage> {
        @Override
        public IMessage onMessage(@Nonnull RequestNbtEditC2SPacket message, @Nonnull MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> message.handle(player));
            return null;
        }
    }
}
