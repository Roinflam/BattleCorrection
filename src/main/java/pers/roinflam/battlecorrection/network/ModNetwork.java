package pers.roinflam.battlecorrection.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import pers.roinflam.battlecorrection.network.packet.ChunkedC2SPacket;
import pers.roinflam.battlecorrection.network.packet.ChunkedS2CPacket;
import pers.roinflam.battlecorrection.network.packet.OpenItemEditorS2CPacket;
import pers.roinflam.battlecorrection.network.packet.RequestNbtEditC2SPacket;
import pers.roinflam.battlecorrection.network.packet.SetCurioSlotsC2SPacket;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;

/**
 * 本模组的网络包
 * <p>
 * 1.21.1（NeoForge）：每种包是一个 {@link CustomPacketPayload}，带自己的类型 ID 和编解码器，
 * 在模组总线的 {@link RegisterPayloadHandlersEvent} 里登记；处理方法默认在主线程跑（和 1.20.1 的 consumerMainThread 一样）。
 * 协议版本号两端必须一致，不一致进服时直接断开，改了包的字段就把版本号加一，免得新旧版本互相解析出乱数据。
 * <p>
 * 目前只有物品编辑器用：服务端 → 客户端"打开编辑器"，客户端 → 服务端"保存修改"、"设置自己的饰品栏数量"。
 */
public final class ModNetwork {

    /**
     * 协议版本，包格式变化时递增（7：1.21.1 起物品按数据组件格式传）
     */
    private static final String PROTOCOL_VERSION = "7";

    private ModNetwork() {
    }

    /**
     * 登记所有网络包（模组总线事件，两端都会收到）
     */
    public static void register(@Nonnull RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToClient(OpenItemEditorS2CPacket.TYPE, OpenItemEditorS2CPacket.STREAM_CODEC, OpenItemEditorS2CPacket::handle);
        registrar.playToServer(SetCurioSlotsC2SPacket.TYPE, SetCurioSlotsC2SPacket.STREAM_CODEC, SetCurioSlotsC2SPacket::handle);
        registrar.playToServer(RequestNbtEditC2SPacket.TYPE, RequestNbtEditC2SPacket.STREAM_CODEC, RequestNbtEditC2SPacket::handle);

        // 保存物品 / 保存 NBT / 打开 NBT 编辑器这三种包不直接登记：它们的内层编解码只给分片层用。
        // 直接登记的话，任何玩家都能绕开分片层的权限和体积检查，凭一个声明了超大数组长度的小包让服务端分配巨额内存。
        // 分片传输两个方向各一个类型，收包时还要再核一次方向（见 ChunkedPacket）
        registrar.playToServer(ChunkedC2SPacket.TYPE, ChunkedC2SPacket.STREAM_CODEC, ChunkedC2SPacket::handle);
        registrar.playToClient(ChunkedS2CPacket.TYPE, ChunkedS2CPacket.STREAM_CODEC, ChunkedS2CPacket::handle);

        LogUtil.info("网络包登记完成，共 5 种");
    }

    /**
     * 发给指定玩家（服务端调用）
     *
     * @param player 玩家
     * @param packet 包
     */
    public static void sendToPlayer(@Nonnull ServerPlayer player, @Nonnull CustomPacketPayload packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    /**
     * 发给服务端（客户端调用）
     *
     * @param packet 包
     */
    public static void sendToServer(@Nonnull CustomPacketPayload packet) {
        PacketDistributor.sendToServer(packet);
    }
}
