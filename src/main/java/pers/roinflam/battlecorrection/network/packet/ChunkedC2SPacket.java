package pers.roinflam.battlecorrection.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 分片包（客户端 → 服务端方向）
 * <p>
 * 逻辑全在 {@link ChunkedPacket}，这里只是给这个方向一个独立的包类型。
 */
public final class ChunkedC2SPacket extends ChunkedPacket {

    public static final Type<ChunkedC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "chunked_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkedC2SPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ChunkedPacket::encode, buf -> (ChunkedC2SPacket) decode(buf, ChunkedC2SPacket::new));

    ChunkedC2SPacket(int transferId, @Nonnull Kind kind, int index, int total, @Nonnull byte[] data) {
        super(transferId, kind, index, total, data);
    }

    @Nonnull
    @Override
    public Type<ChunkedC2SPacket> type() {
        return TYPE;
    }
}
