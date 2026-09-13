package pers.roinflam.battlecorrection.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.battlecorrection.client.editor.ItemEditorClient;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.editor.ItemEditTarget;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：打开物品编辑器
 * <p>
 * 只传"编辑哪一格"和"先显示哪一页"，不传物品本身：客户端自己那份背包数据是服务端实时同步的，
 * 按位置取就行，少传一份 NBT。命令 {@code /battlecorrection edit} 走这条路；
 * 快捷键在客户端本地直接开界面，不经过这个包。
 * <p>
 * 处理方法里引用了客户端类，必须包在 DistExecutor 的 lambda 里，专用服务端加载这个类时才不会去找 Minecraft 类。
 */
public class OpenItemEditorS2CPacket {

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
    public void handle(@Nonnull Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ItemEditorClient.openFromServer(target, page));
    }
}
