package pers.roinflam.battlecorrection.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import pers.roinflam.battlecorrection.network.packet.ChunkedC2SPacket;
import pers.roinflam.battlecorrection.network.packet.ChunkedS2CPacket;
import pers.roinflam.battlecorrection.network.packet.OpenItemEditorS2CPacket;
import pers.roinflam.battlecorrection.network.packet.RequestNbtEditC2SPacket;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 本模组的网络通道
 * <p>
 * Forge 的 SimpleNetworkWrapper：每种包一个类，注册时给处理器、包类、编号和接收的一端。
 * 协议版本号写在通道名里：两端版本不同就是两条不同的通道，互相收不到对方的包，不会把新旧格式的数据解析成乱码。
 * 改了包的字段就把版本号加一。通道名最长 20 个字符（"battlecorrection_" 已占 17 个）。
 * <p>
 * 目前只有物品编辑器用：服务端 → 客户端"打开编辑器"，客户端 → 服务端"保存修改"、"请求实体 / 方块实体 NBT"。
 */
public final class ModNetwork {

    /**
     * 协议版本，包格式变化时递增
     */
    public static final String PROTOCOL_VERSION = "1";

    /**
     * 通道名
     */
    public static final String CHANNEL_NAME = Reference.MOD_ID + "_" + PROTOCOL_VERSION;

    /**
     * 通道实例
     */
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);

    /**
     * 包编号计数器，注册顺序决定编号，两端注册顺序一致即可
     */
    private static int nextId = 0;

    private ModNetwork() {
    }

    /**
     * 注册所有网络包（预初始化时调用，两端都要）
     */
    public static void register() {
        CHANNEL.registerMessage(new OpenItemEditorS2CPacket.Handler(), OpenItemEditorS2CPacket.class, nextId++, Side.CLIENT);
        CHANNEL.registerMessage(new RequestNbtEditC2SPacket.Handler(), RequestNbtEditC2SPacket.class, nextId++, Side.SERVER);

        // 保存物品 / 保存 NBT / 打开 NBT 编辑器这三种包不直接注册到通道上：它们的内层编解码只给分片层用。
        // 直接注册的话，任何玩家都能绕开分片层的权限和体积检查，凭一个声明了超大数组长度的小包让服务端分配巨额内存。
        // 分片传输：两个方向各一个类，处理器各自只挂在接收的一端
        CHANNEL.registerMessage(new ChunkedC2SPacket.Handler(), ChunkedC2SPacket.class, nextId++, Side.SERVER);
        CHANNEL.registerMessage(new ChunkedS2CPacket.Handler(), ChunkedS2CPacket.class, nextId++, Side.CLIENT);

        LogUtil.info("网络通道注册完成，共 " + nextId + " 种网络包");
    }

    /**
     * 取下一个包编号（在 {@link #register()} 之后注册别的包时用，两端调用顺序要一致）
     *
     * @return 编号
     */
    public static int nextId() {
        return nextId++;
    }

    /**
     * 发给指定玩家（服务端调用）
     *
     * @param player 玩家
     * @param packet 包
     */
    public static void sendToPlayer(@Nonnull EntityPlayerMP player, @Nonnull IMessage packet) {
        CHANNEL.sendTo(packet, player);
    }

    /**
     * 发给服务端（客户端调用）
     *
     * @param packet 包
     */
    public static void sendToServer(@Nonnull IMessage packet) {
        CHANNEL.sendToServer(packet);
    }
}
