package pers.roinflam.battlecorrection.network.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.EncoderException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import pers.roinflam.battlecorrection.editor.ItemEditorAccess;
import pers.roinflam.battlecorrection.network.ModNetwork;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 分片传输：把大包切成若干小块发，收齐再拼回来
 * <p>
 * 原版对自定义网络包有硬上限：客户端 → 服务端 32767 字节，服务端 → 客户端 1 MB，超了直接断线。
 * 编辑器要传整份 NBT，物品、实体、箱子随随便便就超 32 KB，所以所有 NBT 相关的包都走这里：
 * 把内层包编码成字节，按 30000 一块切开发送，对面收齐后再解码成内层包正常处理。
 * 网络层面不再有大小限制；NBT 能多大由配置里的上限（{@link ItemEditorAccess#maxNbtBytes()}）决定，
 * 真正的瓶颈是原版同步物品格子、方块实体的包，不是这条通道。
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
 *   <li>丢包时的警告按来源限频，刷小包刷不爆日志</li>
 * </ul>
 * 1.12.2 的消息处理器跑在网络线程上，这里收到每一块都先切到主线程再处理，和 1.20.1 的 consumerMainThread 一样。
 */
public abstract class ChunkedPacket implements IMessage {

    /**
     * 造具体方向的分片包（{@link ChunkedC2SPacket} / {@link ChunkedS2CPacket}）
     * <p>
     * 两个方向分成两个类注册：处理器按方向各挂一边，收到方向不对的类根本没有处理器。
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
     * 内层包里 NBT 的记账上限：和原版 PacketBuffer#readCompoundTag 解物品格子、方块实体用的一样
     * （NBTSizeTracker(2097152)，按内存估算的字节数，不是序列化字节数）
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

    /**
     * 同一来源两条警告之间至少隔多久（毫秒）
     */
    private static final long WARN_INTERVAL_MS = 10_000L;

    private static final AtomicInteger NEXT_TRANSFER_ID = new AtomicInteger();

    /**
     * 各来源上次记警告的时间、之后压下去的条数（{时间, 条数}）
     * <p>
     * 块头里的数字全是对面填的，没有权限的玩家也能发：每个几十字节的小包都能装成一次新传输或一个非法块。
     * 这几条警告要是每包都记，每秒几万个小包就是几万行日志，而且是在主线程上写控制台和日志文件，
     * 整个服务器都会跟着卡，日志文件也会被撑大。所以同一来源在间隔内只记第一条，后面的只计数。
     */
    private static final Map<String, long[]> WARNINGS = new ConcurrentHashMap<>();

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

    private int transferId;
    private Kind kind = Kind.APPLY_ITEM;
    private int index;
    private int total;
    private byte[] data = new byte[0];

    /**
     * 收包时由网络层用无参构造创建，再 {@link #fromBytes} 填字段
     */
    protected ChunkedPacket() {
    }

    protected ChunkedPacket(int transferId, @Nonnull Kind kind, int index, int total, @Nonnull byte[] data) {
        this.transferId = transferId;
        this.kind = kind;
        this.index = index;
        this.total = total;
        this.data = data;
    }

    public void encode(@Nonnull PacketBuffer buf) {
        buf.writeVarInt(transferId);
        buf.writeEnumValue(kind);
        buf.writeVarInt(index);
        buf.writeVarInt(total);
        buf.writeByteArray(data);
    }

    @Override
    public void toBytes(@Nonnull ByteBuf buf) {
        encode(new PacketBuffer(buf));
    }

    @Override
    public void fromBytes(@Nonnull ByteBuf buf) {
        PacketBuffer packet = new PacketBuffer(buf);
        transferId = packet.readVarInt();
        kind = packet.readEnumValue(Kind.class);
        index = packet.readVarInt();
        total = packet.readVarInt();
        data = packet.readByteArray(CHUNK_SIZE + 64);
    }

    // ═══════════════════════════════════════════════════════════════
    // 内层包的 NBT 读写
    // ═══════════════════════════════════════════════════════════════

    /**
     * 写 NBT（null 写一个 0 字节，同原版 writeCompoundTag）
     *
     * @param buf 缓冲
     * @param tag NBT
     */
    public static void writeNbt(@Nonnull PacketBuffer buf, @Nullable NBTTagCompound tag) {
        buf.writeCompoundTag(tag);
    }

    /**
     * 按指定记账上限读 NBT（原版 readCompoundTag 写死了 2 MB，这里要能放宽或收紧）
     *
     * @param buf   缓冲
     * @param quota 记账上限（字节）
     * @return NBT；写的是 null 时为 null
     */
    @Nullable
    public static NBTTagCompound readNbt(@Nonnull PacketBuffer buf, long quota) {
        int start = buf.readerIndex();
        if (buf.readByte() == 0) {
            return null;
        }
        buf.readerIndex(start);
        try {
            return CompressedStreamTools.read(new ByteBufInputStream(buf), new NBTSizeTracker(quota));
        } catch (IOException e) {
            throw new EncoderException(e);
        }
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
    public static void sendToServer(@Nonnull Kind kind, @Nonnull Consumer<PacketBuffer> encoder) {
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
    public static void sendToPlayer(@Nonnull EntityPlayerMP player, @Nonnull Kind kind, @Nonnull Consumer<PacketBuffer> encoder) {
        for (ChunkedPacket chunk : split(kind, encoder, ChunkedS2CPacket::new)) {
            ModNetwork.sendToPlayer(player, chunk);
        }
    }

    @Nonnull
    private static ChunkedPacket[] split(@Nonnull Kind kind, @Nonnull Consumer<PacketBuffer> encoder,
                                         @Nonnull Factory factory) {
        PacketBuffer inner = new PacketBuffer(Unpooled.buffer());
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
     * 网络线程收到一块：切到主线程再处理
     *
     * @param ctx 网络上下文
     */
    void schedule(@Nonnull MessageContext ctx) {
        FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> handle(ctx));
    }

    /**
     * 收一块；收齐了就解码内层包并处理（在主线程）
     *
     * @param ctx 网络上下文
     */
    public void handle(@Nonnull MessageContext ctx) {
        EntityPlayerMP sender = ctx.side == Side.SERVER ? ctx.getServerHandler().player : null;
        boolean onServer = sender != null;
        String key = onServer ? "p:" + sender.getUniqueID() : "server";

        dropStale();
        if (kind.toServer() != onServer) {
            warnThrottled(key, "收到方向不对的分片包，已丢弃: " + key + " kind=" + kind);
            return;
        }
        if (total < 1 || total > MAX_CHUNKS || index < 0 || index >= total || data.length > CHUNK_SIZE) {
            warnThrottled(key, "收到非法的分片包: " + key + " index=" + index + " total=" + total + " size=" + data.length);
            ASSEMBLIES.remove(key);
            return;
        }

        Assembly assembly = ASSEMBLIES.get(key);
        if (assembly == null || assembly.transferId != transferId) {
            // 新的一次传输：同一来源上一次没收齐的就此作废
            assembly = open(sender, key);
            ASSEMBLIES.put(key, assembly);
        } else if (assembly.kind != kind || (assembly.parts != null && assembly.parts.length != total)) {
            warnThrottled(key, "分片包和之前的不匹配，丢弃: " + key);
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
        PacketBuffer buf = new PacketBuffer(Unpooled.wrappedBuffer(joined));
        try {
            switch (kind) {
                case APPLY_ITEM:
                    ApplyItemEditC2SPacket.decode(buf).handle(ctx);
                    break;
                case APPLY_NBT:
                    ApplyNbtEditC2SPacket.decode(buf).handle(ctx);
                    break;
                case OPEN_NBT:
                    OpenNbtEditorS2CPacket.decode(buf).handle(ctx);
                    break;
                default:
                    break;
            }
        } catch (RuntimeException e) {
            LogUtil.error("解码分片传输的内层包失败: " + key, e);
            if (sender != null) {
                sender.sendMessage(red("message.battlecorrection.editor.save_failed.undecodable"));
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
    private Assembly open(@Nullable EntityPlayerMP sender, @Nonnull String key) {
        if (sender != null) {
            if (!ItemEditorAccess.isEnabled()) {
                sender.sendMessage(ItemEditorAccess.disabledMessage());
                return new Assembly(transferId, kind, null);
            }
            if (!ItemEditorAccess.canUse(sender)) {
                sender.sendMessage(ItemEditorAccess.noPermissionMessage());
                warnThrottled(key, String.format("玩家 %s 没有权限却发来了编辑器的分片传输，已忽略", sender.getName()));
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
    private void reject(@Nullable EntityPlayerMP sender, @Nonnull Kind kind, @Nonnull String key, long limit) {
        ASSEMBLIES.put(key, new Assembly(transferId, kind, null));
        // 上限里含内层包头的余量，提示给玩家看配置里写的那个数
        long kb = (limit - (kind.toServer() ? HEADER_ALLOWANCE : 0)) / 1024;
        LogUtil.warn(String.format("分片传输超过上限 %d KB，已拒收: %s", kb, key));
        if (sender != null) {
            sender.sendMessage(red("message.battlecorrection.editor.save_failed.too_large", kb));
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
        // 过了间隔的警告计数清掉；间隔里压下去过的补记一行总数，刷屏的那段在日志里还看得出来
        Iterator<Map.Entry<String, long[]>> warnings = WARNINGS.entrySet().iterator();
        while (warnings.hasNext()) {
            Map.Entry<String, long[]> entry = warnings.next();
            long[] state = entry.getValue();
            if (now - state[0] >= WARN_INTERVAL_MS) {
                warnings.remove();
                if (state[1] > 0) {
                    LogUtil.warn(String.format("%s 在 %d 秒内另有 %d 条分片包警告没有记录",
                            entry.getKey(), WARN_INTERVAL_MS / 1000, state[1]));
                }
            }
        }
    }

    /**
     * 记一条警告，同一来源 {@link #WARN_INTERVAL_MS} 内只记第一条，之后的只计数（见 {@link #WARNINGS}）
     *
     * @param key     来源
     * @param message 警告内容
     */
    private static void warnThrottled(@Nonnull String key, @Nonnull String message) {
        long now = System.currentTimeMillis();
        long[] state = WARNINGS.get(key);
        if (state != null && now - state[0] < WARN_INTERVAL_MS) {
            state[1]++;
            return;
        }
        WARNINGS.put(key, new long[]{now, 0L});
        LogUtil.warn(message);
    }

    /**
     * 红字提示（服务端发给玩家，客户端按自己的语言翻译）
     *
     * @param key  翻译键
     * @param args 参数
     * @return 提示
     */
    @Nonnull
    static ITextComponent red(@Nonnull String key, @Nonnull Object... args) {
        return new TextComponentTranslation(key, args).setStyle(new Style().setColor(TextFormatting.RED));
    }

    /**
     * 绿字提示
     *
     * @param key  翻译键
     * @param args 参数
     * @return 提示
     */
    @Nonnull
    static ITextComponent green(@Nonnull String key, @Nonnull Object... args) {
        return new TextComponentTranslation(key, args).setStyle(new Style().setColor(TextFormatting.GREEN));
    }
}
