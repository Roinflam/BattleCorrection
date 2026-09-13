package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.battlecorrection.editor.ItemEditTarget;
import pers.roinflam.battlecorrection.editor.NbtEditTarget;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * 一次编辑会话
 * <p>
 * 两种模式：
 * <ul>
 *   <li>物品：编辑的是哪一格 + 原物品 + 正在改的副本，页面直接改 {@link #working()}，保存时把副本的 NBT 和数量发给服务端</li>
 *   <li>实体 / 方块实体：服务端发来的一份 NBT + 正在改的副本，页面通过 {@link #loadRoot()} / {@link #pushRoot} 改，保存时整份发回</li>
 * </ul>
 * 撤销：界面每 tick 调 {@link #trackChanges(long)}，发现内容变了就把变化前的状态压栈；一秒内的连续改动合并成一步，
 * 不然打字每敲一下就是一步。Ctrl+Z / Ctrl+Y 走 {@link #undo()} / {@link #redo()}。
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
     * 连续改动合并的窗口（tick）
     */
    private static final long MERGE_WINDOW_TICKS = 20L;

    private static final String KEY_FORGE_CAPS = "ForgeCaps";

    /**
     * 一份快照：物品模式存 tag + 数量，NBT 模式存整份 NBT
     */
    private record Snapshot(@Nullable CompoundTag tag, int count) {
    }

    private final Mode mode;

    // ── 物品模式 ──
    @Nullable
    private final ItemEditTarget target;
    @Nullable
    private final ItemStack original;
    @Nullable
    private final ItemStack working;
    @Nullable
    private final ResourceLocation itemId;
    private final boolean hasForgeCaps;

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
    private long lastChangeTick = Long.MIN_VALUE;

    private EditorSession(@Nonnull Mode mode, @Nullable ItemEditTarget target, @Nullable ItemStack stack,
                          @Nullable NbtEditTarget nbtTarget, @Nullable CompoundTag nbt, @Nonnull Component subjectName) {
        this.mode = mode;
        this.target = target;
        this.original = stack == null ? null : stack.copy();
        this.working = stack == null ? null : stack.copy();
        ResourceLocation id = stack == null ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
        this.itemId = stack == null ? null : (id == null ? new ResourceLocation("minecraft", "air") : id);
        this.hasForgeCaps = stack != null && stack.save(new CompoundTag()).contains(KEY_FORGE_CAPS);
        this.nbtTarget = nbtTarget;
        this.nbtOriginal = nbt == null ? null : nbt.copy();
        this.nbtWorking = nbt == null ? null : nbt.copy();
        this.subjectName = subjectName;
        this.lastKnown = capture();
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
        return isItem() && working != null ? working.getHoverName() : subjectName;
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

    @Nonnull
    public ResourceLocation itemId() {
        return requireItem(itemId);
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
     * 把树/原始页改过的根写回：物品取 Count 和 tag（id 由页面自己校验），实体/方块实体整份替换
     *
     * @param root 根
     */
    public void pushRoot(@Nonnull CompoundTag root) {
        if (isItem()) {
            if (root.contains("Count", Tag.TAG_ANY_NUMERIC)) {
                working().setCount(Mth.clamp(root.getInt("Count"), 1, 64));
            }
            setTag(root.contains("tag", Tag.TAG_COMPOUND) ? root.getCompound("tag").copy() : null);
            return;
        }
        nbtWorking = root.copy();
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
            return new Snapshot(tag == null ? null : tag.copy(), working().getCount());
        }
        return new Snapshot(nbtWorking().copy(), 0);
    }

    private void restore(@Nonnull Snapshot snapshot) {
        if (isItem()) {
            working().setTag(snapshot.tag() == null ? null : snapshot.tag().copy());
            working().setCount(snapshot.count());
        } else {
            nbtWorking = snapshot.tag() == null ? new CompoundTag() : snapshot.tag().copy();
        }
        lastKnown = capture();
    }

    private static boolean same(@Nonnull Snapshot a, @Nonnull Snapshot b) {
        return a.count() == b.count() && Objects.equals(a.tag(), b.tag());
    }

    /**
     * 每 tick 调一次：内容变了就记一步撤销（一秒内的连续改动合并）
     *
     * @param tick 当前 tick 计数
     */
    public void trackChanges(long tick) {
        Snapshot now = capture();
        if (same(now, lastKnown)) {
            return;
        }
        if (tick - lastChangeTick > MERGE_WINDOW_TICKS) {
            undoStack.push(lastKnown);
            if (undoStack.size() > MAX_HISTORY) {
                undoStack.removeLast();
            }
            redoStack.clear();
        }
        lastChangeTick = tick;
        lastKnown = now;
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
        lastChangeTick = Long.MIN_VALUE;
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
        lastChangeTick = Long.MIN_VALUE;
        return true;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    @Nonnull
    private <T> T requireItem(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("不是物品编辑会话");
        }
        return value;
    }
}
