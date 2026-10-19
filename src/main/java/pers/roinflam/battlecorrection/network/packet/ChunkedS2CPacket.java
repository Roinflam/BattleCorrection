package pers.roinflam.battlecorrection.network.packet;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nonnull;

/**
 * 分片包（服务端 → 客户端方向）
 * <p>
 * 逻辑全在 {@link ChunkedPacket}，这里只是给这个方向一个独立的类，处理器只注册在客户端。
 */
public final class ChunkedS2CPacket extends ChunkedPacket {

    /**
     * 收包用（网络层反射创建）
     */
    public ChunkedS2CPacket() {
    }

    ChunkedS2CPacket(int transferId, @Nonnull Kind kind, int index, int total, @Nonnull byte[] data) {
        super(transferId, kind, index, total, data);
    }

    /**
     * 处理器：切到主线程交给 {@link ChunkedPacket#handle}
     */
    public static final class Handler implements IMessageHandler<ChunkedS2CPacket, IMessage> {
        @Override
        public IMessage onMessage(@Nonnull ChunkedS2CPacket message, @Nonnull MessageContext ctx) {
            message.schedule(ctx);
            return null;
        }
    }
}
