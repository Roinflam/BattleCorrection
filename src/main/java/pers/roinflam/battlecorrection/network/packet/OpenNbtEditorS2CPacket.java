package pers.roinflam.battlecorrection.network.packet;

import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import pers.roinflam.battlecorrection.client.editor.ItemEditorClient;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.editor.NbtEditTarget;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * 服务端 → 客户端：把实体 / 方块实体的 NBT 发过去并打开编辑器
 * <p>
 * 处理时才碰客户端类（只在客户端执行到那一行），专用服务端加载这个类不会去找 Minecraft 类。
 */
public class OpenNbtEditorS2CPacket {

    private final NbtEditTarget target;
    private final NBTTagCompound tag;
    private final ITextComponent name;
    private final EditorPage page;

    public OpenNbtEditorS2CPacket(@Nonnull NbtEditTarget target, @Nonnull NBTTagCompound tag, @Nonnull ITextComponent name,
                                  @Nonnull EditorPage page) {
        this.target = target;
        this.tag = tag;
        this.name = name;
        this.page = page;
    }

    public void encode(@Nonnull PacketBuffer buf) {
        target.write(buf);
        ChunkedPacket.writeNbt(buf, tag);
        buf.writeTextComponent(name);
        buf.writeVarInt(page.ordinal());
    }

    @Nonnull
    public static OpenNbtEditorS2CPacket decode(@Nonnull PacketBuffer buf) {
        NbtEditTarget target = NbtEditTarget.read(buf);
        NBTTagCompound tag = ChunkedPacket.readNbt(buf, ChunkedPacket.RECEIVE_NBT_QUOTA);
        ITextComponent name;
        try {
            name = buf.readTextComponent();
        } catch (IOException e) {
            throw new DecoderException(e);
        }
        EditorPage page = EditorPage.byOrdinal(buf.readVarInt());
        return new OpenNbtEditorS2CPacket(target, tag == null ? new NBTTagCompound() : tag, name, page);
    }

    public void handle(@Nonnull MessageContext ctx) {
        if (ctx.side == Side.CLIENT) {
            ItemEditorClient.openNbtFromServer(target, tag, name, page);
        }
    }
}
