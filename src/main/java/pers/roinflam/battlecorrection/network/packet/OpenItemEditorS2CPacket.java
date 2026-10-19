package pers.roinflam.battlecorrection.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
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
 * 处理器只注册在客户端，引用的客户端类只在客户端执行到那一行时才加载，专用服务端加载这个类不会去找 Minecraft 类。
 */
public class OpenItemEditorS2CPacket implements IMessage {

    private ItemEditTarget target = ItemEditTarget.mainHand();
    private EditorPage page = EditorPage.NAME;

    /**
     * 收包用（网络层反射创建）
     */
    public OpenItemEditorS2CPacket() {
    }

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
    public void encode(@Nonnull PacketBuffer buf) {
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
    public static OpenItemEditorS2CPacket decode(@Nonnull PacketBuffer buf) {
        ItemEditTarget target = ItemEditTarget.read(buf);
        EditorPage page = EditorPage.byOrdinal(buf.readVarInt());
        return new OpenItemEditorS2CPacket(target, page);
    }

    @Override
    public void toBytes(@Nonnull ByteBuf buf) {
        encode(new PacketBuffer(buf));
    }

    @Override
    public void fromBytes(@Nonnull ByteBuf buf) {
        OpenItemEditorS2CPacket decoded = decode(new PacketBuffer(buf));
        this.target = decoded.target;
        this.page = decoded.page;
    }

    /**
     * 处理（客户端主线程）
     */
    public void handle() {
        ItemEditorClient.openFromServer(target, page);
    }

    /**
     * 处理器：切到客户端主线程
     */
    public static final class Handler implements IMessageHandler<OpenItemEditorS2CPacket, IMessage> {
        @Override
        public IMessage onMessage(@Nonnull OpenItemEditorS2CPacket message, @Nonnull MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(message::handle);
            return null;
        }
    }
}
