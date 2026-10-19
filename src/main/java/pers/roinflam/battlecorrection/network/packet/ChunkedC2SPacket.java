package pers.roinflam.battlecorrection.network.packet;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nonnull;

/**
 * 分片包（客户端 → 服务端方向）
 * <p>
 * 逻辑全在 {@link ChunkedPacket}，这里只是给这个方向一个独立的类，处理器只注册在服务端。
 */
public final class ChunkedC2SPacket extends ChunkedPacket {

    /**
     * 收包用（网络层反射创建）
     */
    public ChunkedC2SPacket() {
    }

    ChunkedC2SPacket(int transferId, @Nonnull Kind kind, int index, int total, @Nonnull byte[] data) {
        super(transferId, kind, index, total, data);
    }

    /**
     * 处理器：切到主线程交给 {@link ChunkedPacket#handle}
     */
    public static final class Handler implements IMessageHandler<ChunkedC2SPacket, IMessage> {
        @Override
        public IMessage onMessage(@Nonnull ChunkedC2SPacket message, @Nonnull MessageContext ctx) {
            message.schedule(ctx);
            return null;
        }
    }
}
