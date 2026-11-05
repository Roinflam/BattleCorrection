package pers.roinflam.battlecorrection.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 分片包（服务端 → 客户端方向）
 * <p>
 * 逻辑全在 {@link ChunkedPacket}，这里只是给这个方向一个独立的包类型。
 */
public final class ChunkedS2CPacket extends ChunkedPacket {

    public static final Type<ChunkedS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "chunked_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkedS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ChunkedPacket::encode, buf -> (ChunkedS2CPacket) decode(buf, ChunkedS2CPacket::new));

    ChunkedS2CPacket(int transferId, @Nonnull Kind kind, int index, int total, @Nonnull byte[] data) {
        super(transferId, kind, index, total, data);
    }

    @Nonnull
    @Override
    public Type<ChunkedS2CPacket> type() {
        return TYPE;
    }
}
