package pers.roinflam.battlecorrection.network.packet;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import pers.roinflam.battlecorrection.client.editor.ItemEditorClient;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.editor.NbtEditTarget;

import javax.annotation.Nonnull;

/**
 * 服务端 → 客户端：把实体 / 方块实体的 NBT 发过去并打开编辑器
 * <p>
 * 1.21.1：缓冲不再自带读写聊天组件的方法，名字用原版的组件编解码器（{@link ComponentSerialization#TRUSTED_STREAM_CODEC}，
 * 服务端发来的数据），所以编解码用带注册表的 {@link RegistryFriendlyByteBuf}（分片层给的就是它）。
 */
public class OpenNbtEditorS2CPacket {

    private final NbtEditTarget target;
    private final CompoundTag tag;
    private final Component name;
    private final EditorPage page;

    public OpenNbtEditorS2CPacket(@Nonnull NbtEditTarget target, @Nonnull CompoundTag tag, @Nonnull Component name,
                                  @Nonnull EditorPage page) {
        this.target = target;
        this.tag = tag;
        this.name = name;
        this.page = page;
    }

    public void encode(@Nonnull RegistryFriendlyByteBuf buf) {
        target.write(buf);
        buf.writeNbt(tag);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, name);
        buf.writeVarInt(page.ordinal());
    }

    @Nonnull
    public static OpenNbtEditorS2CPacket decode(@Nonnull RegistryFriendlyByteBuf buf) {
        NbtEditTarget target = NbtEditTarget.read(buf);
        CompoundTag tag = ChunkedPacket.readCompound(buf, ChunkedPacket.RECEIVE_NBT_QUOTA);
        Component name = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        EditorPage page = EditorPage.byOrdinal(buf.readVarInt());
        return new OpenNbtEditorS2CPacket(target, tag == null ? new CompoundTag() : tag, name, page);
    }

    public void handle(@Nonnull IPayloadContext ctx) {
        ItemEditorClient.openNbtFromServer(target, tag, name, page);
    }
}
