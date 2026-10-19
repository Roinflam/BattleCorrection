package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.client.resources.I18n;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.Mth;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一次编辑会话
 * <p>
 * 两种模式：
 * <ul>
 *   <li>物品：编辑的是哪一格 + 原物品 + 正在改的副本，页面直接改 {@link #working()}，保存时把副本的 NBT、数量、损害值发给服务端</li>
 *   <li>实体 / 方块实体：服务端发来的一份 NBT + 正在改的副本，页面通过 {@link #loadRoot()} / {@link #pushRoot} 改，保存时整份发回</li>
 * </ul>
 * 撤销：界面在有输入的那几个 tick 里调 {@link #trackChanges(long)}，发现内容变了就把变化前的状态压栈；一秒内的连续改动合并成一步，
 * 不然打字每敲一下就是一步。Ctrl+Z / Ctrl+Y 走 {@link #undo()} / {@link #redo()}。
 * 换物品（{@link #changeItem}）也在撤销范围里：快照里记着物品 ID 和损害值。
 * <p>
 * 1.12.2 的物品身份是"物品 ID + 损害值"：羊毛、染料这类有子类型的物品靠损害值区分颜色，所以损害值和 ID 一起记、一起换。
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
     * 测 NBT 大小的后台线程（单线程、守护线程，关游戏时不用等它）
     */
    private static final ExecutorService MEASURE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BattleCorrection NBT measure");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    /**
     * 一份快照：物品模式存 tag + 数量 + 物品 ID + 损害值，NBT 模式存整份 NBT
     */
    private static final class Snapshot {
        @Nullable
        final NBTTagCompound tag;
        final int count;
        @Nullable
        final ResourceLocation itemId;
        final int damage;

        Snapshot(@Nullable NBTTagCompound tag, int count, @Nullable ResourceLocation itemId, int damage) {
            this.tag = tag;
            this.count = count;
            this.itemId = itemId;
            this.damage = damage;
        }
    }

    /**
     * 后台测出的 NBT 大小，附带它对应的版本号，晚到的旧结果不会盖掉新结果
     */
    private static final class Measured {
        final int revision;
        final int bytes;
        final boolean overQuota;

        Measured(int revision, int bytes, boolean overQuota) {
            this.revision = revision;
            this.bytes = bytes;
            this.overQuota = overQuota;
        }
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
    private final int originalDamage;
    private boolean hasForgeCaps;

    // ── NBT 模式 ──
    @Nullable
    private final NbtEditTarget nbtTarget;
    @Nullable
    private final NBTTagCompound nbtOriginal;
    @Nullable
    private NBTTagCompound nbtWorking;
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
                          @Nullable NbtEditTarget nbtTarget, @Nullable NBTTagCompound nbt, @Nonnull Component subjectName) {
        this.mode = mode;
        this.target = target;
        this.original = stack == null ? null : stack.copy();
        this.working = stack == null ? null : stack.copy();
        ResourceLocation id = stack == null ? null : stack.getItem().getRegistryName();
        this.itemId = stack == null ? null : (id == null ? new ResourceLocation("minecraft", "air") : id);
        this.originalItemId = this.itemId;
        this.originalDamage = stack == null ? 0 : stack.getMetadata();
        this.hasForgeCaps = stack != null && stack.writeToNBT(new NBTTagCompound()).hasKey(KEY_FORGE_CAPS);
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
        return new EditorSession(Mode.ITEM, target, stack, null, null, Component.literal(stack.getDisplayName()));
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
    public static EditorSession forNbt(@Nonnull NbtEditTarget target, @Nonnull NBTTagCompound nbt, @Nonnull Component name) {
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
        // 每帧都会问；按内容版本缓存。名字在副本上取，模组的 getItemStackDisplayName 怎么写都碰不到正在编辑的物品
        if (cachedNameRevision != revision || cachedName == null) {
            cachedName = Component.literal(working.copy().getDisplayName());
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
     * 打开编辑器时那一格的损害值（有子类型的物品，服务端还要靠它确认没被换过）
     */
    public int originalDamage() {
        requireItem(original);
        return originalDamage;
    }

    /**
     * 现在的损害值（1.12.2 的 Damage：耐久，或者羊毛这类物品的子类型）
     */
    public int damage() {
        return working().getItemDamage();
    }

    /**
     * 改损害值（夹到 0..32767）
     *
     * @param damage 损害值
     */
    public void setDamage(int damage) {
        working().setItemDamage(Mth.clamp(damage, 0, ApplyItemEditC2SPacket.MAX_DAMAGE));
    }

    /**
     * 换成另一种物品：NBT 和数量原样带过去
     * <p>
     * 损害值：旧物品有子类型（羊毛的颜色之类）时归零，那个值对新物品没有意义；否则当耐久原样带过去。
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
        changeItem(id, current.getHasSubtypes() ? 0 : current.getItemDamage());
    }

    /**
     * 换成另一种物品并指定损害值（物品选择器选中某个子类型时用）
     * <p>
     * ID 相同只改损害值。其余同 {@link #changeItem(ResourceLocation)}。
     *
     * @param id     新物品的注册名
     * @param damage 损害值
     */
    public void changeItem(@Nonnull ResourceLocation id, int damage) {
        ItemStack current = requireItem(working);
        int clamped = Mth.clamp(damage, 0, ApplyItemEditC2SPacket.MAX_DAMAGE);
        if (id.equals(itemId)) {
            if (current.getItemDamage() != clamped) {
                current.setItemDamage(clamped);
                revision++;
            }
            return;
        }
        Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
        if (item == null || item == Items.AIR) {
            throw new IllegalArgumentException(I18n.format("gui.battlecorrection.editor.item.unknown", id));
        }
        ItemStack next = new ItemStack(item, Mth.clamp(current.getCount(), ApplyItemEditC2SPacket.MIN_COUNT,
                ApplyItemEditC2SPacket.MAX_COUNT), clamped);
        NBTTagCompound tag = current.getTagCompound();
        next.setTagCompound(tag == null ? null : tag.copy());
        working = next;
        itemId = id;
        hasForgeCaps = false;
        revision++;
    }

    /**
     * 物品换过没有（ID 变了，或者有子类型的物品换了子类型）
     */
    public boolean isItemChanged() {
        if (!isItem()) {
            return false;
        }
        if (!Objects.equals(itemId, originalItemId)) {
            return true;
        }
        return working().getHasSubtypes() && working().getMetadata() != originalDamage;
    }

    /**
     * 这件物品带不带 Forge 能力数据（ForgeCaps）
     * <p>
     * 有些模组会把关键数据放在能力里而不是 tag 里。编辑器只改 tag，服务端写回时是在原物品的副本上换 tag，
     * 能力数据原样保留——这正是某些 NBT 编辑器"一保存特效就没了"的原因：它们把物品整个重建了一遍。
     */
    public boolean hasForgeCaps() {
        return hasForgeCaps;
    }

    /**
     * 物品的 tag，没有就建一个空的挂上去
     */
    @Nonnull
    public NBTTagCompound tag() {
        ItemStack stack = working();
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        return tag;
    }

    @Nullable
    public NBTTagCompound tagOrNull() {
        return working().getTagCompound();
    }

    public void setTag(@Nullable NBTTagCompound tag) {
        working().setTagCompound(tag == null || tag.hasNoTags() ? null : tag);
    }

    /**
     * 清理空壳：display 空了就删掉 display，整个 NBT 空了就置空
     */
    public void cleanupEmpty() {
        if (!isItem()) {
            return;
        }
        NBTTagCompound tag = working().getTagCompound();
        if (tag == null) {
            return;
        }
        if (tag.hasKey("display") && tag.getCompoundTag("display").hasNoTags()) {
            tag.removeTag("display");
        }
        if (tag.hasNoTags()) {
            working().setTagCompound(null);
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
    public NBTTagCompound nbtWorking() {
        if (nbtWorking == null) {
            throw new IllegalStateException("不是实体/方块实体编辑会话");
        }
        return nbtWorking;
    }

    // ═══════════════════════════════════════════════════════════════
    // 树 / 原始 NBT 页用的统一入口
    // ═══════════════════════════════════════════════════════════════

    /**
     * 取一份可以随便改的"根"：物品是 {id, Count, Damage, tag}（去掉 ForgeCaps），实体/方块实体就是整份 NBT
     *
     * @return 副本，改完用 {@link #pushRoot} 写回
     */
    @Nonnull
    public NBTTagCompound loadRoot() {
        if (isItem()) {
            NBTTagCompound full = working().writeToNBT(new NBTTagCompound());
            full.removeTag(KEY_FORGE_CAPS);
            // 1.12.2 的 writeToNBT 直接放进物品自己的 tag 对象，不复制；不拆开的话改返回值会直接改到副本物品上
            if (full.hasKey("tag", 10)) {
                full.setTag("tag", full.getCompoundTag("tag").copy());
            }
            return full;
        }
        return nbtWorking().copy();
    }

    /**
     * 把树/原始页改过的根写回：物品取 id、Count、Damage 和 tag，实体/方块实体整份替换
     * <p>
     * id 和现在的不同就换物品；id 写法不对或者不是已注册的物品会抛 IllegalArgumentException（信息可直接给玩家看），
     * 这时 Count、Damage 和 tag 都还没动。没写命名空间的 id 按 minecraft 补全；大写字母照 1.12.2 的规矩转成小写。
     * 删掉了 Count / Damage 的就保持原值不变。
     *
     * @param root 根
     */
    public void pushRoot(@Nonnull NBTTagCompound root) {
        if (isItem()) {
            Integer damage = null;
            if (root.hasKey("Damage", 99)) {
                damage = (int) clampNumber(root.getTag("Damage"), 0, ApplyItemEditC2SPacket.MAX_DAMAGE);
            }
            if (root.hasKey("id", 8)) {
                String text = root.getString("id").trim();
                ResourceLocation id = parseItemId(text);
                if (id == null) {
                    throw new IllegalArgumentException(I18n.format("gui.battlecorrection.editor.item.invalid_id", text));
                }
                if (damage != null) {
                    changeItem(id, damage);
                } else {
                    changeItem(id);
                }
            } else if (damage != null) {
                setDamage(damage);
            }
            if (root.hasKey("Count", 99)) {
                // 先按 long 取再夹：写成 3000000000L 这种超出 int 的数，按 int 取会绕回成负数被夹到 1，而不是 127
                working().setCount((int) clampNumber(root.getTag("Count"), ApplyItemEditC2SPacket.MIN_COUNT,
                        ApplyItemEditC2SPacket.MAX_COUNT));
            }
            setTag(root.hasKey("tag", 10) ? root.getCompoundTag("tag").copy() : null);
            return;
        }
        nbtWorking = root.copy();
    }

    private static long clampNumber(@Nonnull NBTBase tag, long min, long max) {
        long value = tag instanceof NBTPrimitive ? ((NBTPrimitive) tag).getLong() : min;
        return Mth.clamp(value, min, max);
    }

    /**
     * 解析物品 ID：没写命名空间的补 minecraft；命名空间只能是 a-z 0-9 _ - .，路径再多一个 /
     *
     * @param text 文字
     * @return ID；写法不对时为 null
     */
    @Nullable
    public static ResourceLocation parseItemId(@Nonnull String text) {
        String lower = text.trim().toLowerCase(java.util.Locale.ROOT);
        int colon = lower.indexOf(':');
        String namespace = colon >= 0 ? lower.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? lower.substring(colon + 1) : lower;
        if (namespace.isEmpty() || path.isEmpty()) {
            return null;
        }
        for (int i = 0; i < namespace.length(); i++) {
            if (!validChar(namespace.charAt(i), false)) {
                return null;
            }
        }
        for (int i = 0; i < path.length(); i++) {
            if (!validChar(path.charAt(i), true)) {
                return null;
            }
        }
        return new ResourceLocation(namespace, path);
    }

    private static boolean validChar(char c, boolean path) {
        return c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.' || path && c == '/';
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
            NBTTagCompound before = original().writeToNBT(new NBTTagCompound());
            before.removeTag(KEY_FORGE_CAPS);
            NBTTagCompound after = working().writeToNBT(new NBTTagCompound());
            after.removeTag(KEY_FORGE_CAPS);
            return ChangeSummary.diff(before, after, limit);
        }
        return ChangeSummary.diff(Objects.requireNonNull(nbtOriginal), nbtWorking(), limit);
    }

    /**
     * 是否改过
     */
    public boolean isModified() {
        if (isItem()) {
            return !ItemStack.areItemStacksEqual(original(), working());
        }
        return nbtOriginal == null || !nbtOriginal.equals(nbtWorking);
    }

    // ═══════════════════════════════════════════════════════════════
    // 撤销 / 重做
    // ═══════════════════════════════════════════════════════════════

    @Nonnull
    private Snapshot capture() {
        if (isItem()) {
            NBTTagCompound tag = working().getTagCompound();
            return new Snapshot(tag == null ? null : tag.copy(), working().getCount(), itemId, working().getItemDamage());
        }
        return new Snapshot(nbtWorking().copy(), 0, null, 0);
    }

    private void restore(@Nonnull Snapshot snapshot) {
        if (isItem()) {
            ResourceLocation id = snapshot.itemId;
            if (id != null) {
                changeItem(id, snapshot.damage);
            }
            working().setTagCompound(snapshot.tag == null ? null : snapshot.tag.copy());
            working().setCount(snapshot.count);
        } else {
            nbtWorking = snapshot.tag == null ? new NBTTagCompound() : snapshot.tag.copy();
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
            return stack.getCount() != lastKnown.count
                    || stack.getItemDamage() != lastKnown.damage
                    || !Objects.equals(itemId, lastKnown.itemId)
                    || !Objects.equals(stack.getTagCompound(), lastKnown.tag);
        }
        return !Objects.equals(nbtWorking(), lastKnown.tag);
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
        long perStep = Math.max(1, measured.get().bytes);
        while (undoStack.size() > MAX_HISTORY || (undoStack.size() > 4 && undoStack.size() * perStep > MAX_HISTORY_BYTES)) {
            undoStack.removeLast();
        }
    }

    /**
     * 最近一次测出的 NBT 序列化大小（字节）；还没测出来之前是 0
     */
    public int measuredBytes() {
        return measured.get().bytes;
    }

    /**
     * 最近一次测出的结果是不是针对现在这个版本的内容（后台还没测完时是 false）
     */
    public boolean measuredIsCurrent() {
        return measured.get().revision == revision;
    }

    /**
     * 按原版的 2 MB 记账，这份 NBT 会不会让对面解码失败
     */
    public boolean measuredOverQuota() {
        return measured.get().overQuota;
    }

    /**
     * 后台线程测一份快照序列化后多大。快照是私有拷贝，之后不会再被改动，所以后台读是安全的。
     */
    private void measureAsync(@Nonnull Snapshot snapshot) {
        NBTTagCompound tag = snapshot.tag;
        int rev = revision;
        if (tag == null) {
            measured.set(new Measured(rev, 0, false));
            return;
        }
        MEASURE_EXECUTOR.execute(() -> {
            int bytes;
            boolean over = false;
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(4096);
                try (DataOutputStream out = new DataOutputStream(buffer)) {
                    CompressedStreamTools.write(tag, out);
                }
                byte[] data = buffer.toByteArray();
                bytes = data.length;
                // 原版解物品格子 / 方块实体用 2 MB 的内存记账，紧凑的复合标签记账是序列化字节数的 5 倍多，
                // 光看字节数看不出来；照原版的记账方式真读一遍，超了就是这件东西会让对面的客户端解码失败掉线
                try {
                    CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(data)),
                            new NBTSizeTracker(ChunkedPacket.VANILLA_NBT_QUOTA));
                } catch (RuntimeException e) {
                    over = true;
                }
            } catch (IOException | RuntimeException e) {
                return;
            }
            Measured result = new Measured(rev, bytes, over);
            measured.accumulateAndGet(result, (old, now) -> now.revision >= old.revision ? now : old);
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
