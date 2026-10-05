package pers.roinflam.battlecorrection.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.battlecorrection.editor.ItemEditorAccess;
import pers.roinflam.battlecorrection.network.ModNetwork;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 分片传输：把大包切成若干小块发，收齐再拼回来
 * <p>
 * 原版对自定义网络包有硬上限：客户端 → 服务端 32767 字节，服务端 → 客户端 1 MB，超了直接断线。
 * 编辑器要传整份 NBT，物品、实体、箱子随随便便就超 32 KB，所以所有 NBT 相关的包都走这里：
 * 把内层包编码成字节，按 30000 一块切开发送，对面收齐后再解码成内层包正常处理。
 * 网络层面不再有大小限制；NBT 能多大由配置里的上限（{@link ItemEditorAccess#maxNbtBytes()}）决定，
 * 真正的瓶颈是原版客户端解码物品格子的 NBT，不是这条通道。
 * <p>
 * 内层包不注册到通道上，它们只当编解码器用；{@link Kind} 决定收齐后交给谁、以及这种传输该从哪个方向来。
 * 注册到通道上的是两个方向各一个的子类 {@link ChunkedC2SPacket} 和 {@link ChunkedS2CPacket}。
 * <p>
 * 收包这一侧的防护（块里的数字全是对面说了算，服务端不能信）：
 * <ul>
 *   <li>方向不对的丢弃：客户端不能冒充服务端发"打开编辑器"，反过来也一样</li>
 *   <li>块数、单块大小、总字节数都有上限，拼装缓冲不会被一个伪造的 total 撑爆</li>
 *   <li>服务端在收到一次传输的第一块时就校验功能开关和权限、并按配置上限提前拒绝过大的传输，
 *   没资格的玩家一个字节都不会被缓存</li>
 *   <li>每个来源同时只保留一次进行中的传输，新的开始就把旧的丢掉；超时没收齐的也会清掉</li>
 * </ul>
 */
public abstract class ChunkedPacket {

    /**
     * 造具体方向的分片包（{@link ChunkedC2SPacket} / {@link ChunkedS2CPacket}）
     * <p>
     * Forge 的 SimpleChannel 按"类"找编解码器，同一个类不能同时注册两个方向——后注册的会盖掉前面的，
     * 客户端发出去时会被按服务端方向编码，服务端一校验方向就断线。所以两个方向必须是两个类。
     */
    @FunctionalInterface
    protected interface Factory {
        @Nonnull
        ChunkedPacket create(int transferId, @Nonnull Kind kind, int index, int total, @Nonnull byte[] data);
    }

    /**
     * 内层包类型；顺序是网络协议的一部分，别调换
     */
    public enum Kind {
        APPLY_ITEM(true),
        APPLY_NBT(true),
        OPEN_NBT(false);

        private final boolean toServer;

        Kind(boolean toServer) {
            this.toServer = toServer;
        }

        /**
         * @return true = 这种传输只该从客户端发给服务端
         */
        public boolean toServer() {
            return toServer;
        }
    }

    /**
     * 每块字节数，留在原版 32767 上限之下（块头最多再占十几个字节）
     */
    private static final int CHUNK_SIZE = 30_000;

    /**
     * 一次传输的总字节硬上限，不管配置怎么写都不会超过
     */
    private static final long HARD_MAX_BYTES = 16L << 20;

    /**
     * 内层包里 NBT 的记账上限：和原版客户端解物品格子、方块实体包用的一样（2 MB，按内存估算而不是序列化字节数）
     */
    public static final long VANILLA_NBT_QUOTA = 2_097_152L;

    /**
     * 客户端收服务端发来的 NBT 时的记账上限：服务端自己写的数据，只防畸形包，按一次传输的硬上限放宽
     */
    public static final long RECEIVE_NBT_QUOTA = HARD_MAX_BYTES;

    /**
     * 配置上限之外留给内层包头（目标、物品 ID、数量等）的余量
     */
    private static final int HEADER_ALLOWANCE = 4096;

    /**
     * 一次传输最多多少块
     */
    private static final int MAX_CHUNKS = (int) ((HARD_MAX_BYTES + CHUNK_SIZE - 1) / CHUNK_SIZE);

    /**
     * 没收齐的传输多久作废（毫秒）
     */
    private static final long STALE_MS = 60_000L;

    private static final AtomicInteger NEXT_TRANSFER_ID = new AtomicInteger();

    /**
     * 进行中的传输，按来源记（服务端按玩家，客户端只有服务端一个来源）。
     * 单人游戏里内置服务端和客户端在不同线程上各自收包，所以用并发表；单个传输对象只被一个来源的线程碰。
     */
    private static final Map<String, Assembly> ASSEMBLIES = new ConcurrentHashMap<>();

    /**
     * 一次进行中的传输
     */
    private static final class Assembly {
        final int transferId;
        final Kind kind;
        /**
         * 被拒绝的传输不再缓存，后续的块直接忽略（parts 为 null）
         */
        @Nullable
        final byte[][] parts;
        int received;
        long bytes;
        final long createdMs = System.currentTimeMillis();

        Assembly(int transferId, @Nonnull Kind kind, @Nullable byte[][] parts) {
            this.transferId = transferId;
            this.kind = kind;
            this.parts = parts;
        }
    }

    private final int transferId;
    private final Kind kind;
    private final int index;
    private final int total;
    private final byte[] data;

    protected ChunkedPacket(int transferId, @Nonnull Kind kind, int index, int total, @Nonnull byte[] data) {
        this.transferId = transferId;
        this.kind = kind;
        this.index = index;
        this.total = total;
        this.data = data;
    }

    public void encode(@Nonnull FriendlyByteBuf buf) {
        buf.writeVarInt(transferId);
        buf.writeEnum(kind);
        buf.writeVarInt(index);
        buf.writeVarInt(total);
        buf.writeByteArray(data);
    }

    /**
     * 解码（子类的 decode 传入自己的工厂）
     */
    @Nonnull
    protected static ChunkedPacket decode(@Nonnull FriendlyByteBuf buf, @Nonnull Factory factory) {
        int transferId = buf.readVarInt();
        Kind kind = buf.readEnum(Kind.class);
        int index = buf.readVarInt();
        int total = buf.readVarInt();
        byte[] data = buf.readByteArray(CHUNK_SIZE + 64);
        return factory.create(transferId, kind, index, total, data);
    }

    // ═══════════════════════════════════════════════════════════════
    // 发送
    // ═══════════════════════════════════════════════════════════════

    /**
     * 客户端 → 服务端
     *
     * @param kind    内层包类型
     * @param encoder 内层包的编码
     */
    public static void sendToServer(@Nonnull Kind kind, @Nonnull Consumer<FriendlyByteBuf> encoder) {
        for (ChunkedPacket chunk : split(kind, encoder, ChunkedC2SPacket::new)) {
            ModNetwork.sendToServer(chunk);
        }
    }

    /**
     * 服务端 → 某个玩家
     *
     * @param player  玩家
     * @param kind    内层包类型
     * @param encoder 内层包的编码
     */
    public static void sendToPlayer(@Nonnull ServerPlayer player, @Nonnull Kind kind, @Nonnull Consumer<FriendlyByteBuf> encoder) {
        for (ChunkedPacket chunk : split(kind, encoder, ChunkedS2CPacket::new)) {
            ModNetwork.sendToPlayer(player, chunk);
        }
    }

    @Nonnull
    private static ChunkedPacket[] split(@Nonnull Kind kind, @Nonnull Consumer<FriendlyByteBuf> encoder,
                                         @Nonnull Factory factory) {
        FriendlyByteBuf inner = new FriendlyByteBuf(Unpooled.buffer());
        byte[] bytes;
        try {
            encoder.accept(inner);
            bytes = new byte[inner.readableBytes()];
            inner.readBytes(bytes);
        } finally {
            inner.release();
        }
        int total = Math.max(1, (bytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE);
        int transferId = NEXT_TRANSFER_ID.incrementAndGet();
        ChunkedPacket[] chunks = new ChunkedPacket[total];
        for (int i = 0; i < total; i++) {
            int from = i * CHUNK_SIZE;
            int to = Math.min(bytes.length, from + CHUNK_SIZE);
            byte[] part = new byte[to - from];
            System.arraycopy(bytes, from, part, 0, part.length);
            chunks[i] = factory.create(transferId, kind, i, total, part);
        }
        return chunks;
    }

    // ═══════════════════════════════════════════════════════════════
    // 接收
    // ═══════════════════════════════════════════════════════════════

    /**
     * 收一块；收齐了就解码内层包并处理（在主线程）
     *
     * @param ctx 网络上下文
     */
    public void handle(@Nonnull Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        boolean onServer = sender != null;
        String key = onServer ? "p:" + sender.getUUID() : "server";

        dropStale();
        if (kind.toServer() != onServer) {
            LogUtil.warn("收到方向不对的分片包，已丢弃: " + key + " kind=" + kind);
            return;
        }
        if (total < 1 || total > MAX_CHUNKS || index < 0 || index >= total || data.length > CHUNK_SIZE) {
            LogUtil.warn("收到非法的分片包: " + key + " index=" + index + " total=" + total + " size=" + data.length);
            ASSEMBLIES.remove(key);
            return;
        }

        Assembly assembly = ASSEMBLIES.get(key);
        if (assembly == null || assembly.transferId != transferId) {
            // 新的一次传输：同一来源上一次没收齐的就此作废
            assembly = open(sender, key);
            ASSEMBLIES.put(key, assembly);
        } else if (assembly.kind != kind || (assembly.parts != null && assembly.parts.length != total)) {
            LogUtil.warn("分片包和之前的不匹配，丢弃: " + key);
            ASSEMBLIES.remove(key);
            return;
        }
        byte[][] parts = assembly.parts;
        if (parts == null) {
            return;
        }

        if (parts[index] == null) {
            parts[index] = data;
            assembly.received++;
            assembly.bytes += data.length;
        }
        long limit = limitFor(kind);
        if (assembly.bytes > limit) {
            reject(sender, kind, key, limit);
            return;
        }
        if (assembly.received < total) {
            return;
        }
        ASSEMBLIES.remove(key);

        byte[] joined = new byte[(int) assembly.bytes];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, joined, offset, part.length);
            offset += part.length;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(joined));
        try {
            switch (kind) {
                case APPLY_ITEM -> ApplyItemEditC2SPacket.decode(buf).handle(ctx);
                case APPLY_NBT -> ApplyNbtEditC2SPacket.decode(buf).handle(ctx);
                case OPEN_NBT -> OpenNbtEditorS2CPacket.decode(buf).handle(ctx);
            }
        } catch (RuntimeException e) {
            LogUtil.error("解码分片传输的内层包失败: " + key, e);
            if (sender != null) {
                sender.sendSystemMessage(Component.translatable("message.battlecorrection.editor.save_failed.undecodable")
                        .withStyle(ChatFormatting.RED));
            }
        } finally {
            buf.release();
        }
    }

    /**
     * 一次新传输的第一块到了：该收就建缓冲，不该收（没权限、声明的块数已经超过上限）就建一个不缓存的占位，
     * 让这次传输后面的块被直接忽略
     */
    @Nonnull
    private Assembly open(@Nullable ServerPlayer sender, @Nonnull String key) {
        if (sender != null) {
            if (!ItemEditorAccess.isEnabled()) {
                sender.sendSystemMessage(ItemEditorAccess.disabledMessage());
                return new Assembly(transferId, kind, null);
            }
            if (!ItemEditorAccess.canUse(sender)) {
                sender.sendSystemMessage(ItemEditorAccess.noPermissionMessage());
                LogUtil.warn(String.format("玩家 %s 没有权限却发来了编辑器的分片传输，已忽略", sender.getName().getString()));
                return new Assembly(transferId, kind, null);
            }
        }
        long limit = limitFor(kind);
        if ((long) (total - 1) * CHUNK_SIZE >= limit) {
            reject(sender, kind, key, limit);
            return new Assembly(transferId, kind, null);
        }
        return new Assembly(transferId, kind, new byte[total][]);
    }

    /**
     * 这种传输最多收多少字节：服务端收 NBT 按配置上限，客户端收服务端发来的按硬上限
     */
    private static long limitFor(@Nonnull Kind kind) {
        if (!kind.toServer()) {
            return HARD_MAX_BYTES;
        }
        return Math.min(HARD_MAX_BYTES, (long) ItemEditorAccess.maxNbtBytes() + HEADER_ALLOWANCE);
    }

    /**
     * 拒收：释放缓冲，留一个占位让后续的块被忽略，并告诉玩家
     */
    private void reject(@Nullable ServerPlayer sender, @Nonnull Kind kind, @Nonnull String key, long limit) {
        ASSEMBLIES.put(key, new Assembly(transferId, kind, null));
        // 上限里含内层包头的余量，提示给玩家看配置里写的那个数
        long kb = (limit - (kind.toServer() ? HEADER_ALLOWANCE : 0)) / 1024;
        LogUtil.warn(String.format("分片传输超过上限 %d KB，已拒收: %s", kb, key));
        if (sender != null) {
            sender.sendSystemMessage(Component.translatable("message.battlecorrection.editor.save_failed.too_large", kb)
                    .withStyle(ChatFormatting.RED));
        }
    }

    /**
     * 清掉超时没收齐的传输
     */
    private static void dropStale() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Assembly>> iterator = ASSEMBLIES.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().createdMs > STALE_MS) {
                iterator.remove();
            }
        }
    }
}
