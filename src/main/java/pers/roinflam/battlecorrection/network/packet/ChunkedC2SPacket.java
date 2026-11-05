package pers.roinflam.battlecorrection.network.packet;

import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nonnull;

/**
 * 分片包（客户端 → 服务端方向）
 * <p>
 * 逻辑全在 {@link ChunkedPacket}，这里只是给这个方向一个独立的类，Forge 的 SimpleChannel 按类区分方向。
 */
public final class ChunkedC2SPacket extends ChunkedPacket {

    ChunkedC2SPacket(int transferId, @Nonnull Kind kind, int index, int total, @Nonnull byte[] data) {
        super(transferId, kind, index, total, data);
    }

    @Nonnull
    public static ChunkedC2SPacket decode(@Nonnull FriendlyByteBuf buf) {
        return (ChunkedC2SPacket) decode(buf, ChunkedC2SPacket::new);
    }
}
