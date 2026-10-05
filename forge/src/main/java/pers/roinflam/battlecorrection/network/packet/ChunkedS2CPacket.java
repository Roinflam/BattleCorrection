package pers.roinflam.battlecorrection.network.packet;

import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nonnull;

/**
 * 分片包（服务端 → 客户端方向）
 * <p>
 * 逻辑全在 {@link ChunkedPacket}，这里只是给这个方向一个独立的类，Forge 的 SimpleChannel 按类区分方向。
 */
public final class ChunkedS2CPacket extends ChunkedPacket {

    ChunkedS2CPacket(int transferId, @Nonnull Kind kind, int index, int total, @Nonnull byte[] data) {
        super(transferId, kind, index, total, data);
    }

    @Nonnull
    public static ChunkedS2CPacket decode(@Nonnull FriendlyByteBuf buf) {
        return (ChunkedS2CPacket) decode(buf, ChunkedS2CPacket::new);
    }
}
