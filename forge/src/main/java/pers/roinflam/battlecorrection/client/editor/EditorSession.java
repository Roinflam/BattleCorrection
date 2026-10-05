package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.Util;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.battlecorrection.editor.ItemEditTarget;
import pers.roinflam.battlecorrection.editor.NbtEditTarget;
import pers.roinflam.battlecorrection.network.packet.ApplyItemEditC2SPacket;
import pers.roinflam.battlecorrection.network.packet.ChunkedPacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一次编辑会话
 * <p>
 * 两种模式：
 * <ul>
 *   <li>物品：编辑的是哪一格 + 原物品 + 正在改的副本，页面直接改 {@link #working()}，保存时把副本的 NBT 和数量发给服务端</li>
 *   <li>实体 / 方块实体：服务端发来的一份 NBT + 正在改的副本，页面通过 {@link #loadRoot()} / {@link #pushRoot} 改，保存时整份发回</li>
 * </ul>
 * 撤销：界面在有输入的那几个 tick 里调 {@link #trackChanges(long)}，发现内容变了就把变化前的状态压栈；一秒内的连续改动合并成一步，
 * 不然打字每敲一下就是一步。Ctrl+Z / Ctrl+Y 走 {@link #undo()} / {@link #redo()}。
 * 换物品（{@link #changeItem}）也在撤销范围里：快照里记着物品 ID。
 */
public final class EditorSession {

    /**
     * 编辑模式
     */
    public enum Mode {
        ITEM,
        ENTITY,
        BLOCK_ENTITY
    }

    /**
     * 撤销栈最多存多少步
     */
    private static final int MAX_HISTORY = 60;

    /**
     * 撤销栈快照占用的内存估算上限（字节）：每步存一整份 NBT，大物品 60 步会吃掉很多内存，超了就丢最老的
     */
    private static final long MAX_HISTORY_BYTES = 96L << 20;

    /**
     * 连续改动合并的窗口（tick）
     */
    private static final long MERGE_WINDOW_TICKS = 20L;

    /**
     * "还没有过改动"的占位 tick：离任何真实 tick 都足够远，又不会在做减法时溢出
     */
    private static final long NO_CHANGE_TICK = -1_000_000L;

    private static final String KEY_FORGE_CAPS = "ForgeCaps";

    /**
     * 一份快照：物品模式存 tag + 数量 + 物品 ID，NBT 模式存整份 NBT
     */
    private record Snapshot(@Nullable CompoundTag tag, int count, @Nullable ResourceLocation itemId) {
    }

    /**
     * 后台测出的 NBT 大小，附带它对应的版本号，晚到的旧结果不会盖掉新结果
     */
    private record Measured(int revision, int bytes, boolean overQuota) {
    }

    private final Mode mode;

    // ── 物品模式 ──
    @Nullable
    private final ItemEditTarget target;
    @Nullable
    private final ItemStack original;
    @Nullable
    private ItemStack working;
    @Nullable
    private ResourceLocation itemId;
    @Nullable
    private final ResourceLocation originalItemId;
    private boolean hasForgeCaps;

    // ── NBT 模式 ──
    @Nullable
    private final NbtEditTarget nbtTarget;
    @Nullable
    private final CompoundTag nbtOriginal;
    @Nullable
    private CompoundTag nbtWorking;
    @Nonnull
    private final Component subjectName;

    // ── 撤销 ──
    private final Deque<Snapshot> undoStack = new ArrayDeque<>();
    private final Deque<Snapshot> redoStack = new ArrayDeque<>();
    private Snapshot lastKnown;
    private long lastChangeTick = NO_CHANGE_TICK;

    /**
     * 内容版本号：每次检测到变化（或撤销 / 重做 / 换物品）加一，界面据此判断缓存的预览、大小要不要重算
     */
    private int revision;
    @Nullable
    private Component cachedName;
    private int cachedNameRevision = -1;
    private final AtomicReference<Measured> measured = new AtomicReference<>(new Measured(-1, 0, false));

    private EditorSession(@Nonnull Mode mode, @Nullable ItemEditTarget target, @Nullable ItemStack stack,
                          @Nullable NbtEditTarget nbtTarget, @Nullable CompoundTag nbt, @Nonnull Component subjectName) {
        this.mode = mode;
        this.target = target;
        this.original = stack == null ? null : stack.copy();
        this.working = stack == null ? null : stack.copy();
        ResourceLocation id = stack == null ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
        this.itemId = stack == null ? null : (id == null ? new ResourceLocation("minecraft", "air") : id);
        this.originalItemId = this.itemId;
        this.hasForgeCaps = stack != null && stack.save(new CompoundTag()).contains(KEY_FORGE_CAPS);
        this.nbtTarget = nbtTarget;
        this.nbtOriginal = nbt == null ? null : nbt.copy();
        this.nbtWorking = nbt == null ? null : nbt.copy();
        this.subjectName = subjectName;
        this.lastKnown = capture();
        measureAsync(lastKnown);
    }

    /**
     * 物品编辑会话
     *
     * @param target 编辑目标
     * @param stack  目标位置上的物品（会复制，不持有原引用）
     * @return 会话
     */
    @Nonnull
    public static EditorSession forItem(@Nonnull ItemEditTarget target, @Nonnull ItemStack stack) {
        return new EditorSession(Mode.ITEM, target, stack, null, null, stack.getHoverName());
    }

    /**
     * 实体 / 方块实体 NBT 编辑会话
     *
     * @param target 目标
     * @param nbt    服务端发来的 NBT
     * @param name   显示在标题栏的名字
     * @return 会话
     */
    @Nonnull
    public static EditorSession forNbt(@Nonnull NbtEditTarget target, @Nonnull CompoundTag nbt, @Nonnull Component name) {
        return new EditorSession(target.isEntity() ? Mode.ENTITY : Mode.BLOCK_ENTITY, null, null, target, nbt, name);
    }

    @Nonnull
    public Mode mode() {
        return mode;
    }

    public boolean isItem() {
        return mode == Mode.ITEM;
    }

    /**
     * 标题栏显示的名字：物品用当前名字，实体/方块实体用打开时的名字
     */
    @Nonnull
    public Component subjectName() {
        if (!isItem() || working == null) {
            return subjectName;
        }
        // 每帧都会问；按内容版本缓存，并且在副本上取名字：原版遇到解析不了的 display.Name 会把它从被查询的物品上删掉
        if (cachedNameRevision != revision || cachedName == null) {
            cachedName = working.copy().getHoverName();
            cachedNameRevision = revision;
        }
        return cachedName;
    }

    // ═══════════════════════════════════════════════════════════════
    // 物品模式
    // ═══════════════════════════════════════════════════════════════

    @Nonnull
    public ItemEditTarget target() {
        return requireItem(target);
    }

    @Nonnull
    public ItemStack original() {
        return requireItem(original);
    }

    /**
     * 正在编辑的副本，页面直接改它（仅物品模式）
     */
    @Nonnull
    public ItemStack working() {
        return requireItem(working);
    }

    /**
     * 现在的物品 ID（换过物品就是新的）
     */
    @Nonnull
    public ResourceLocation itemId() {
        return requireItem(itemId);
    }

    /**
     * 打开编辑器时那一格的物品 ID：保存时服务端靠它确认格子里的东西没被换过
     */
    @Nonnull
    public ResourceLocation originalItemId() {
        return requireItem(originalItemId);
    }

    /**
     * 换成另一种物品：NBT 和数量原样带过去
     * <p>
     * 原物品的 Forge 能力数据（ForgeCaps）属于旧物品，不带。ID 不存在或者是空气会抛 IllegalArgumentException，
     * 信息是直接给玩家看的；抛出时什么都没改。
     *
     * @param id 新物品的注册名
     */
    public void changeItem(@Nonnull ResourceLocation id) {
        ItemStack current = requireItem(working);
        if (id.equals(itemId)) {
            return;
        }
        Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
        if (item == null || item == Items.AIR) {
            throw new IllegalArgumentException(I18n.get("gui.battlecorrection.editor.item.unknown", id));
        }
        ItemStack next = new ItemStack(item, Mth.clamp(current.getCount(), ApplyItemEditC2SPacket.MIN_COUNT,
                ApplyItemEditC2SPacket.MAX_COUNT));
        CompoundTag tag = current.getTag();
        next.setTag(tag == null ? null : tag.copy());
        working = next;
        itemId = id;
        hasForgeCaps = false;
        revision++;
    }

    /**
     * 物品换过没有
     */
    public boolean isItemChanged() {
        return isItem() && !Objects.equals(itemId, originalItemId);
    }

    /**
     * 这件物品带不带 Forge 能力数据（ForgeCaps）
     * <p>
     * 匠魂进化模组之类会把关键数据放在能力里而不是 tag 里。编辑器只改 tag，服务端写回时是在原物品的副本上换 tag，
     * 能力数据原样保留——这正是某些 NBT 编辑器"一保存特效就没了"的原因：它们把物品整个重建了一遍。
     */
    public boolean hasForgeCaps() {
        return hasForgeCaps;
    }

    @Nonnull
    public CompoundTag tag() {
        return working().getOrCreateTag();
    }

    @Nullable
    public CompoundTag tagOrNull() {
        return working().getTag();
    }

    public void setTag(@Nullable CompoundTag tag) {
        working().setTag(tag == null || tag.isEmpty() ? null : tag);
    }

    /**
     * 清理空壳：display 空了就删掉 display，整个 NBT 空了就置空
     */
    public void cleanupEmpty() {
        if (!isItem()) {
            return;
        }
        CompoundTag tag = working().getTag();
        if (tag == null) {
            return;
        }
        if (tag.contains("display") && tag.getCompound("display").isEmpty()) {
            tag.remove("display");
        }
        if (tag.isEmpty()) {
            working().setTag(null);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // NBT 模式
    // ═══════════════════════════════════════════════════════════════

    @Nonnull
    public NbtEditTarget nbtTarget() {
        if (nbtTarget == null) {
            throw new IllegalStateException("不是实体/方块实体编辑会话");
        }
        return nbtTarget;
    }

    /**
     * 正在编辑的 NBT 副本（仅 NBT 模式）
     */
    @Nonnull
    public CompoundTag nbtWorking() {
        if (nbtWorking == null) {
            throw new IllegalStateException("不是实体/方块实体编辑会话");
        }
        return nbtWorking;
    }

    // ═══════════════════════════════════════════════════════════════
    // 树 / 原始 NBT 页用的统一入口
    // ═══════════════════════════════════════════════════════════════

    /**
     * 取一份可以随便改的"根"：物品是 {id, Count, tag}（去掉 ForgeCaps），实体/方块实体就是整份 NBT
     *
     * @return 副本，改完用 {@link #pushRoot} 写回
     */
    @Nonnull
    public CompoundTag loadRoot() {
        if (isItem()) {
            CompoundTag full = working().save(new CompoundTag());
            full.remove(KEY_FORGE_CAPS);
            return full;
        }
        return nbtWorking().copy();
    }

    /**
     * 把树/原始页改过的根写回：物品取 id、Count 和 tag，实体/方块实体整份替换
     * <p>
     * id 和现在的不同就换物品；id 写法不对或者不是已注册的物品会抛 IllegalArgumentException（信息可直接给玩家看），
     * 这时 Count 和 tag 都还没动。没写命名空间的 id 按 minecraft 补全。
     *
     * @param root 根
     */
    public void pushRoot(@Nonnull CompoundTag root) {
        if (isItem()) {
            if (root.contains("id", Tag.TAG_STRING)) {
                String text = root.getString("id").trim();
                ResourceLocation id = ResourceLocation.tryParse(text.indexOf(':') >= 0 ? text : "minecraft:" + text);
                if (id == null) {
                    throw new IllegalArgumentException(I18n.get("gui.battlecorrection.editor.item.invalid_id", text));
                }
                changeItem(id);
            }
            if (root.contains("Count", Tag.TAG_ANY_NUMERIC) && root.get("Count") instanceof NumericTag number) {
                // 先按 long 取再夹：写成 3000000000L 这种超出 int 的数，getInt 会绕回成负数被夹到 1，而不是 127
                long value = number.getAsLong();
                working().setCount((int) Mth.clamp(value, (long) ApplyItemEditC2SPacket.MIN_COUNT,
                        (long) ApplyItemEditC2SPacket.MAX_COUNT));
            }
            setTag(root.contains("tag", Tag.TAG_COMPOUND) ? root.getCompound("tag").copy() : null);
            return;
        }
        nbtWorking = root.copy();
    }

    /**
     * 和打开时比，改了哪些地方（关闭确认框用，开销和整份 NBT 的大小成正比，别放在每帧调用的路径上）
     *
     * @param limit 最多列多少条
     * @return 摘要
     */
    @Nonnull
    public ChangeSummary.Result changeSummary(int limit) {
        if (isItem()) {
            CompoundTag before = original().save(new CompoundTag());
            before.remove(KEY_FORGE_CAPS);
            CompoundTag after = working().save(new CompoundTag());
            after.remove(KEY_FORGE_CAPS);
            return ChangeSummary.diff(before, after, limit);
        }
        return ChangeSummary.diff(Objects.requireNonNull(nbtOriginal), nbtWorking(), limit);
    }

    /**
     * 是否改过
     */
    public boolean isModified() {
        if (isItem()) {
            return !ItemStack.matches(original(), working());
        }
        return nbtOriginal == null || !nbtOriginal.equals(nbtWorking);
    }

    // ═══════════════════════════════════════════════════════════════
    // 撤销 / 重做
    // ═══════════════════════════════════════════════════════════════

    @Nonnull
    private Snapshot capture() {
        if (isItem()) {
            CompoundTag tag = working().getTag();
            return new Snapshot(tag == null ? null : tag.copy(), working().getCount(), itemId);
        }
        return new Snapshot(nbtWorking().copy(), 0, null);
    }

    private void restore(@Nonnull Snapshot snapshot) {
        if (isItem()) {
            ResourceLocation id = snapshot.itemId();
            if (id != null && !id.equals(itemId)) {
                changeItem(id);
            }
            working().setTag(snapshot.tag() == null ? null : snapshot.tag().copy());
            working().setCount(snapshot.count());
        } else {
            nbtWorking = snapshot.tag() == null ? new CompoundTag() : snapshot.tag().copy();
        }
        lastKnown = capture();
        revision++;
        measureAsync(lastKnown);
    }

    /**
     * 现在的内容和上次记下的快照比，变了没有：不拷贝，每次检测零分配，只有真变了才付拷贝的代价
     */
    private boolean differsFromLastKnown() {
        if (isItem()) {
            ItemStack stack = working();
            return stack.getCount() != lastKnown.count()
                    || !Objects.equals(itemId, lastKnown.itemId())
                    || !Objects.equals(stack.getTag(), lastKnown.tag());
        }
        return !Objects.equals(nbtWorking(), lastKnown.tag());
    }

    /**
     * 内容版本号：变一次加一
     */
    public int revision() {
        return revision;
    }

    /**
     * 每个有输入的 tick 调一次：内容变了就记一步撤销（一秒内的连续改动合并）
     * <p>
     * 界面只在有键鼠输入的前后几个 tick 里调用，闲着不动的时候不会去比较整份 NBT。
     *
     * @param tick 当前 tick 计数
     */
    public void trackChanges(long tick) {
        if (!differsFromLastKnown()) {
            return;
        }
        Snapshot now = capture();
        if (tick - lastChangeTick > MERGE_WINDOW_TICKS) {
            undoStack.push(lastKnown);
            trimHistory();
            redoStack.clear();
        }
        lastChangeTick = tick;
        lastKnown = now;
        revision++;
        measureAsync(now);
    }

    /**
     * 撤销栈太长或估算占用太大就丢最老的
     */
    private void trimHistory() {
        long perStep = Math.max(1, measured.get().bytes());
        while (undoStack.size() > MAX_HISTORY || (undoStack.size() > 4 && undoStack.size() * perStep > MAX_HISTORY_BYTES)) {
            undoStack.removeLast();
        }
    }

    /**
     * 最近一次测出的 NBT 序列化大小（字节）；还没测出来之前是 0
     */
    public int measuredBytes() {
        return measured.get().bytes();
    }

    /**
     * 最近一次测出的结果是不是针对现在这个版本的内容（后台还没测完时是 false）
     */
    public boolean measuredIsCurrent() {
        return measured.get().revision() == revision;
    }

    /**
     * 按原版客户端的 2 MB 记账，这份 NBT 会不会让对面解码失败
     */
    public boolean measuredOverQuota() {
        return measured.get().overQuota();
    }

    /**
     * 后台线程测一份快照序列化后多大。快照是私有拷贝，之后不会再被改动，所以后台读是安全的。
     */
    private void measureAsync(@Nonnull Snapshot snapshot) {
        CompoundTag tag = snapshot.tag();
        int rev = revision;
        if (tag == null) {
            measured.set(new Measured(rev, 0, false));
            return;
        }
        Util.backgroundExecutor().execute(() -> {
            int bytes;
            boolean over = false;
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(4096);
                try (DataOutputStream out = new DataOutputStream(buffer)) {
                    NbtIo.write(tag, out);
                }
                byte[] data = buffer.toByteArray();
                bytes = data.length;
                // 原版客户端解物品格子 / 方块实体用 2 MB 的内存记账，紧凑的复合标签记账能到序列化字节数的 8 倍左右，
                // 光看字节数看不出来；照原版的记账方式真读一遍，超了就是这件东西会让对面的客户端解码失败掉线
                try {
                    NbtIo.read(new DataInputStream(new ByteArrayInputStream(data)), new NbtAccounter(ChunkedPacket.VANILLA_NBT_QUOTA));
                } catch (RuntimeException e) {
                    over = true;
                }
            } catch (IOException | RuntimeException e) {
                return;
            }
            Measured result = new Measured(rev, bytes, over);
            measured.accumulateAndGet(result, (old, now) -> now.revision() >= old.revision() ? now : old);
        });
    }

    /**
     * 撤销一步
     *
     * @return true = 撤了；false = 没得撤
     */
    public boolean undo() {
        Snapshot previous = undoStack.poll();
        if (previous == null) {
            return false;
        }
        redoStack.push(capture());
        restore(previous);
        lastChangeTick = NO_CHANGE_TICK;
        return true;
    }

    /**
     * 重做一步
     *
     * @return true = 重做了；false = 没得重做
     */
    public boolean redo() {
        Snapshot next = redoStack.poll();
        if (next == null) {
            return false;
        }
        undoStack.push(capture());
        restore(next);
        lastChangeTick = NO_CHANGE_TICK;
        return true;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    @Nonnull
    private <T> T requireItem(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("不是物品编辑会话");
        }
        return value;
    }
}
