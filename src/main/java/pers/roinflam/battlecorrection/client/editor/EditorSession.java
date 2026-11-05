package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.Util;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.component.CustomData;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一次编辑会话
 * <p>
 * 两种模式：
 * <ul>
 *   <li>物品：编辑的是哪一格 + 原物品 + 正在改的副本，页面直接改 {@link #working()}（改它的数据组件），保存时把副本整件发给服务端</li>
 *   <li>实体 / 方块实体：服务端发来的一份 NBT + 正在改的副本，页面通过 {@link #loadRoot()} / {@link #pushRoot} 改，保存时整份发回</li>
 * </ul>
 * 撤销：界面在有输入的那几个 tick 里调 {@link #trackChanges(long)}，发现内容变了就把变化前的状态压栈；一秒内的连续改动合并成一步，
 * 不然打字每敲一下就是一步。Ctrl+Z / Ctrl+Y 走 {@link #undo()} / {@link #redo()}。
 * 换物品（{@link #changeItem}）也在撤销范围里：快照存的是整件物品的副本，物品种类跟着走。
 * <p>
 * 1.21.1：物品没有 tag 了，数据都在数据组件里。"整件物品的 NBT"指 {@code {id, count, components:{...}}} 这个根
 * （见 {@link ItemRoots}），树页、SNBT 页编辑的就是它。1.20.1 的 ForgeCaps（能力数据）在 1.21.1 不存在了，
 * 原来"能力数据编辑不了"的提示随之去掉。
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
     * 撤销栈快照占用的内存估算上限（字节）：每步存一整份数据，大物品 60 步会吃掉很多内存，超了就丢最老的
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

    /**
     * 一份快照：物品模式存整件物品的副本（物品种类、数量、组件都在里面），NBT 模式存整份 NBT
     */
    private record Snapshot(@Nullable ItemStack stack, @Nullable CompoundTag tag) {
    }

    /**
     * 后台测出的 NBT 大小，附带它对应的版本号，晚到的旧结果不会盖掉新结果
     */
    private record Measured(int revision, int bytes, boolean overQuota) {
    }

    private final Mode mode;

    // 物品模式
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

    // NBT 模式
    @Nullable
    private final NbtEditTarget nbtTarget;
    @Nullable
    private final CompoundTag nbtOriginal;
    @Nullable
    private CompoundTag nbtWorking;
    @Nonnull
    private final Component subjectName;

    // 撤销
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
        this.itemId = stack == null ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
        this.originalItemId = this.itemId;
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
     * 客户端当前的注册表（附魔是数据驱动的，物品编解码要靠它）
     */
    @Nonnull
    public RegistryAccess registries() {
        return ItemRoots.registries();
    }

    /**
     * 标题栏显示的名字：物品用当前名字，实体/方块实体用打开时的名字
     */
    @Nonnull
    public Component subjectName() {
        if (!isItem() || working == null) {
            return subjectName;
        }
        // 每帧都会问，按内容版本缓存
        if (cachedNameRevision != revision || cachedName == null) {
            cachedName = working.getHoverName();
            cachedNameRevision = revision;
        }
        return cachedName;
    }

    // 物品模式

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
     * <p>
     * 换物品、撤销、整件替换（SNBT 页应用、导入、整份套用模板）时副本会换成新的对象，页面每次用都从这里取，别自己留着引用。
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
     * 换成另一种物品：数据和数量原样带过去
     * <p>
     * 1.21.1 带过去的是组件补丁（和原物品默认值不同的那部分），和 1.20.1 原样带 tag 是一个意思：
     * 原物品自带的默认组件（比如剑的默认攻击属性）不跟过去，新物品用它自己的默认值。
     * ID 不存在或者是空气会抛 IllegalArgumentException，信息是直接给玩家看的；抛出时什么都没改。
     *
     * @param id 新物品的注册名
     */
    public void changeItem(@Nonnull ResourceLocation id) {
        ItemStack current = requireItem(working);
        if (id.equals(itemId)) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : null;
        if (item == null || item == Items.AIR) {
            throw new IllegalArgumentException(I18n.get("gui.battlecorrection.editor.item.unknown", id));
        }
        working = current.transmuteCopy(item, Mth.clamp(current.getCount(), ApplyItemEditC2SPacket.MIN_COUNT,
                ApplyItemEditC2SPacket.MAX_COUNT));
        itemId = id;
        revision++;
    }

    /**
     * 整件换掉正在编辑的副本（整份套用模板时用）
     *
     * @param stack 新物品（会复制；数量夹到允许的范围）
     */
    public void replaceWorking(@Nonnull ItemStack stack) {
        requireItem(working);
        if (stack.isEmpty()) {
            throw new IllegalArgumentException(I18n.get("gui.battlecorrection.editor.item.unknown", "minecraft:air"));
        }
        working = stack.copyWithCount(Mth.clamp(stack.getCount(), ApplyItemEditC2SPacket.MIN_COUNT,
                ApplyItemEditC2SPacket.MAX_COUNT));
        itemId = BuiltInRegistries.ITEM.getKey(working.getItem());
        revision++;
    }

    /**
     * 物品换过没有
     */
    public boolean isItemChanged() {
        return isItem() && !Objects.equals(itemId, originalItemId);
    }

    /**
     * 这件物品带不带 Forge 能力数据
     * <p>
     * 1.20.1 有 ForgeCaps，编辑器改不了它，界面上要提示一句；1.21.1 没有这种数据了（模组改用数据附件 / 数据组件），恒为 false。
     */
    public boolean hasForgeCaps() {
        return false;
    }

    /**
     * 清理空壳：custom_data 被改空了就整个去掉
     * <p>
     * 1.20.1 这里删空的 display、把空 tag 置空；1.21.1 对应的是空的 custom_data（其余组件删空时各页自己会还原默认值）。
     */
    public void cleanupEmpty() {
        if (!isItem()) {
            return;
        }
        CustomData data = working().get(DataComponents.CUSTOM_DATA);
        if (data != null && data.isEmpty()) {
            working().remove(DataComponents.CUSTOM_DATA);
        }
    }

    // NBT 模式

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

    // 树 / 原始 NBT 页用的统一入口

    /**
     * 取一份可以随便改的"根"：物品是 {@code {id, count, components}}，实体/方块实体就是整份 NBT
     *
     * @return 副本，改完用 {@link #pushRoot} 写回
     */
    @Nonnull
    public CompoundTag loadRoot() {
        if (isItem()) {
            return ItemRoots.save(working(), registries());
        }
        return nbtWorking().copy();
    }

    /**
     * 把树/原始页改过的根写回：物品按根整件重建，实体/方块实体整份替换
     * <p>
     * id 和现在的不同就是换物品；id 写法不对、不是已注册的物品、或者组件解析不了都会抛 IllegalArgumentException
     * （信息可直接给玩家看），这时物品一点都没动。没写命名空间的 id 按 minecraft 补全；没写 id / count 的沿用现在的；
     * 数量先按 long 取再夹到 1 ~ {@link ApplyItemEditC2SPacket#MAX_COUNT}。
     * 粘进来的是 1.20.1 的 {@code {id, Count, tag}} 写法时，先用原版数据修复器升级再读。
     *
     * @param root 根
     */
    public void pushRoot(@Nonnull CompoundTag root) {
        if (!isItem()) {
            nbtWorking = root.copy();
            return;
        }
        CompoundTag input = root.copy();
        if (ItemRoots.isLegacy(input)) {
            if (!input.contains(ItemRoots.KEY_ID, Tag.TAG_STRING)) {
                input.putString(ItemRoots.KEY_ID, itemId().toString());
            }
            CompoundTag upgraded = ItemRoots.upgradeLegacy(input);
            if (upgraded == null) {
                throw new IllegalArgumentException(I18n.get("gui.battlecorrection.editor.item.legacy_failed"));
            }
            input = upgraded;
        }
        ResourceLocation id = itemId();
        if (input.contains(ItemRoots.KEY_ID, Tag.TAG_STRING)) {
            String text = input.getString(ItemRoots.KEY_ID).trim();
            ResourceLocation parsed = ResourceLocation.tryParse(text.indexOf(':') >= 0 ? text : "minecraft:" + text);
            if (parsed == null) {
                throw new IllegalArgumentException(I18n.get("gui.battlecorrection.editor.item.invalid_id", text));
            }
            Item item = BuiltInRegistries.ITEM.containsKey(parsed) ? BuiltInRegistries.ITEM.get(parsed) : null;
            if (item == null || item == Items.AIR) {
                throw new IllegalArgumentException(I18n.get("gui.battlecorrection.editor.item.unknown", parsed));
            }
            id = parsed;
        }
        input.putString(ItemRoots.KEY_ID, id.toString());
        long count = working().getCount();
        if (input.get(ItemRoots.KEY_COUNT) instanceof NumericTag number) {
            // 先按 long 取再夹：写成 3000000000L 这种超出 int 的数，getInt 会绕回成负数被夹到 1，而不是上限
            count = number.getAsLong();
        }
        input.putInt(ItemRoots.KEY_COUNT, (int) Mth.clamp(count, (long) ApplyItemEditC2SPacket.MIN_COUNT,
                (long) ApplyItemEditC2SPacket.MAX_COUNT));
        working = ItemRoots.parse(registries(), input);
        if (!id.equals(itemId)) {
            itemId = id;
            revision++;
        }
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
            RegistryAccess registries = registries();
            return ChangeSummary.diff(ItemRoots.save(original(), registries), ItemRoots.save(working(), registries), limit);
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

    // 撤销 / 重做

    @Nonnull
    private Snapshot capture() {
        if (isItem()) {
            return new Snapshot(working().copy(), null);
        }
        return new Snapshot(null, nbtWorking().copy());
    }

    private void restore(@Nonnull Snapshot snapshot) {
        if (isItem()) {
            working = Objects.requireNonNull(snapshot.stack()).copy();
            itemId = BuiltInRegistries.ITEM.getKey(working.getItem());
        } else {
            nbtWorking = snapshot.tag() == null ? new CompoundTag() : snapshot.tag().copy();
        }
        lastKnown = capture();
        revision++;
        measureAsync(lastKnown);
    }

    /**
     * 现在的内容和上次记下的快照比，变了没有：不拷贝，只有真变了才付拷贝的代价
     * <p>
     * 物品比的是种类、数量和整份组件（组件都是不可变的值，比较不分配）。
     */
    private boolean differsFromLastKnown() {
        if (isItem()) {
            ItemStack known = lastKnown.stack();
            return known == null || !ItemStack.matches(working(), known);
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
     * 界面只在有键鼠输入的前后几个 tick 里调用，闲着不动的时候不会去比较整份数据。
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
     * <p>
     * 1.21.1 物品测的是 {@code save()} 出来的根（{@code {id, count, components}}），和保存时发给服务端的是同一份东西。
     * 组件都是不可变的值，在后台线程编码不会和主线程抢；注册表在主线程取好再交给后台。
     */
    private void measureAsync(@Nonnull Snapshot snapshot) {
        int rev = revision;
        ItemStack stack = snapshot.stack();
        CompoundTag nbt = snapshot.tag();
        if (stack == null && nbt == null) {
            measured.set(new Measured(rev, 0, false));
            return;
        }
        RegistryAccess registries = stack == null ? null : registries();
        Util.backgroundExecutor().execute(() -> {
            int bytes;
            boolean over = false;
            try {
                CompoundTag tag = stack != null ? ItemRoots.save(stack, Objects.requireNonNull(registries)) : nbt;
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(4096);
                try (DataOutputStream out = new DataOutputStream(buffer)) {
                    NbtIo.write(tag, out);
                }
                byte[] data = buffer.toByteArray();
                bytes = data.length;
                // 原版客户端解物品格子 / 方块实体用 2 MB 的内存记账（1.21.1 里 custom_data 这类 NBT 组件同样如此），
                // 紧凑的复合标签记账能到序列化字节数的 8 倍左右，光看字节数看不出来；照原版的记账方式真读一遍，
                // 超了就是这件东西会让对面的客户端解码失败掉线
                try {
                    NbtIo.read(new DataInputStream(new ByteArrayInputStream(data)), NbtAccounter.create(ChunkedPacket.VANILLA_NBT_QUOTA));
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
