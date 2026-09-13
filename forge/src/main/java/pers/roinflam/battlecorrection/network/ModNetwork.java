package pers.roinflam.battlecorrection.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import pers.roinflam.battlecorrection.network.packet.ApplyItemEditC2SPacket;
import pers.roinflam.battlecorrection.network.packet.OpenItemEditorS2CPacket;
import pers.roinflam.battlecorrection.network.packet.SetCurioSlotsC2SPacket;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 本模组的网络通道
 * <p>
 * Forge 的 SimpleChannel：每种包一个类，注册时给编码、解码、处理三个方法。
 * 协议版本号两端必须一致，改了包的字段就把版本号加一，免得新旧版本互相解析出乱数据。
 * <p>
 * 目前只有物品编辑器用：服务端 → 客户端"打开编辑器"，客户端 → 服务端"保存修改"、"设置自己的饰品栏数量"。
 */
public final class ModNetwork {

    /**
     * 协议版本，包格式变化时递增
     */
    private static final String PROTOCOL_VERSION = "1";

    /**
     * 通道实例
     */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Reference.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    /**
     * 包 ID 计数器，注册顺序决定 ID，两端注册顺序一致即可
     */
    private static int nextId = 0;

    private ModNetwork() {
    }

    /**
     * 注册所有网络包（主类构造时调用，两端都要）
     */
    public static void register() {
        CHANNEL.messageBuilder(OpenItemEditorS2CPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenItemEditorS2CPacket::encode)
                .decoder(OpenItemEditorS2CPacket::decode)
                .consumerMainThread(OpenItemEditorS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(ApplyItemEditC2SPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ApplyItemEditC2SPacket::encode)
                .decoder(ApplyItemEditC2SPacket::decode)
                .consumerMainThread(ApplyItemEditC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetCurioSlotsC2SPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetCurioSlotsC2SPacket::encode)
                .decoder(SetCurioSlotsC2SPacket::decode)
                .consumerMainThread(SetCurioSlotsC2SPacket::handle)
                .add();

        LogUtil.info("网络通道注册完成，共 " + nextId + " 种网络包");
    }

    /**
     * 发给指定玩家（服务端调用）
     *
     * @param player 玩家
     * @param packet 包
     */
    public static void sendToPlayer(@Nonnull ServerPlayer player, @Nonnull Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * 发给服务端（客户端调用）
     *
     * @param packet 包
     */
    public static void sendToServer(@Nonnull Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
