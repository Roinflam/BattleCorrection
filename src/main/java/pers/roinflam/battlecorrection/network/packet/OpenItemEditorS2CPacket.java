package pers.roinflam.battlecorrection.network.packet;

import pers.roinflam.battlecorrection.utils.Reference;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.FriendlyByteBuf;
import pers.roinflam.battlecorrection.client.editor.ItemEditorClient;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.editor.ItemEditTarget;

import javax.annotation.Nonnull;

/**
 * 服务端 → 客户端：打开物品编辑器
 * <p>
 * 只传"编辑哪一格"和"先显示哪一页"，不传物品本身：客户端自己那份背包数据是服务端实时同步的，
 * 按位置取就行，少传一份 NBT。命令 {@code /battlecorrection edit} 走这条路；
 * 快捷键在客户端本地直接开界面，不经过这个包。
 * <p>
 * 处理方法里引用了客户端类：这种包只登记为发往客户端，处理方法只会在客户端执行，专用服务端不会解析到那些类。
 */
public class OpenItemEditorS2CPacket implements CustomPacketPayload {

    public static final Type<OpenItemEditorS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "open_item_editor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenItemEditorS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(OpenItemEditorS2CPacket::encode, OpenItemEditorS2CPacket::decode);

    private final ItemEditTarget target;
    private final EditorPage page;

    /**
     * @param target 编辑目标
     * @param page   初始页面
     */
    public OpenItemEditorS2CPacket(@Nonnull ItemEditTarget target, @Nonnull EditorPage page) {
        this.target = target;
        this.page = page;
    }

    /**
     * 编码
     *
     * @param buf 缓冲
     */
    public void encode(@Nonnull FriendlyByteBuf buf) {
        target.write(buf);
        buf.writeVarInt(page.ordinal());
    }

    /**
     * 解码
     *
     * @param buf 缓冲
     * @return 包
     */
    @Nonnull
    public static OpenItemEditorS2CPacket decode(@Nonnull FriendlyByteBuf buf) {
        ItemEditTarget target = ItemEditTarget.read(buf);
        EditorPage page = EditorPage.byOrdinal(buf.readVarInt());
        return new OpenItemEditorS2CPacket(target, page);
    }

    /**
     * 处理（客户端主线程）
     *
     * @param ctx 网络上下文
     */
    public void handle(@Nonnull IPayloadContext ctx) {
        ItemEditorClient.openFromServer(target, page);
    }

    @Nonnull
    @Override
    public Type<OpenItemEditorS2CPacket> type() {
        return TYPE;
    }
}
