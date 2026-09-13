package pers.roinflam.battlecorrection.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.battlecorrection.client.editor.ItemEditorClient;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.editor.NbtEditTarget;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：把实体 / 方块实体的 NBT 发过去并打开编辑器
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

    public void encode(@Nonnull FriendlyByteBuf buf) {
        target.write(buf);
        buf.writeNbt(tag);
        buf.writeComponent(name);
        buf.writeVarInt(page.ordinal());
    }

    @Nonnull
    public static OpenNbtEditorS2CPacket decode(@Nonnull FriendlyByteBuf buf) {
        NbtEditTarget target = NbtEditTarget.read(buf);
        CompoundTag tag = buf.readNbt();
        Component name = buf.readComponent();
        EditorPage page = EditorPage.byOrdinal(buf.readVarInt());
        return new OpenNbtEditorS2CPacket(target, tag == null ? new CompoundTag() : tag, name, page);
    }

    public void handle(@Nonnull Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ItemEditorClient.openNbtFromServer(target, tag, name, page));
    }
}
