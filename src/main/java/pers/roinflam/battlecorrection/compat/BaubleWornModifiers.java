package pers.roinflam.battlecorrection.compat;

import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.config.ConfigBaubles;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 饰品栏里的物品给玩家多少属性：算一次、缓存起来，两处共用
 * <p>
 * 每一格要生效的属性 = 本模组的附加属性（按格子类型筛选）+ 物品自身的属性（防具护甲、武器攻击力、
 * AttributeModifiers NBT、其他模组给的），全部换成"属于这一格"的 UUID（见 {@link BaubleMark#forSlot}）。
 * 标记物品的自身属性一律算上；没标记的普通饰品按配置 baubleNbtAttributes 决定（全部 / 只本模组属性 / 不算）。
 * <ol>
 *   <li>服务端每 tick 末尾（{@link #syncAttributeMap}）把它们作为真正的修饰符加到玩家的属性表上，
 *   格子里的物品换了就撤掉旧的、加上新的。原版属性（护甲、生命、移速……）就是这样生效的，
 *   通过原版的属性同步包到客户端；本模组的属性也一起加，和原版处理手上、身上装备的方式一致。</li>
 *   <li>AttributesUtil 算本模组属性时（{@link #collect}）直接读缓存里的数值，客户端、服务端都能用。
 *   AttributesUtil 只读属性的基础值再自己加装备，不读属性表上的修饰符，所以第 1 条加上去的不会被算两次。</li>
 * </ol>
 * 缓存按格子比较物品（数量、损害值、NBT 全比），物品没变就不重算：
 * 拉弓时每 tick 都要读好几次属性，不缓存的话每次都要把 7 格饰品的属性重新解析一遍。
 * <p>
 * 这个类只用 Forge 的物品栏接口，不引用 Baubles 的类；饰品栏由调用方从 {@link BaublesAccess} 拿。
 */
public final class BaubleWornModifiers {

    /**
     * 服务端各玩家的缓存（只在服务端线程读写）
     */
    private static final Map<EntityPlayer, WornState> SERVER = new WeakHashMap<>();

    /**
     * 客户端各玩家的缓存（只在客户端线程读写；单人游戏时两边是不同的实体对象，分开存互不干扰）
     */
    private static final Map<EntityPlayer, WornState> CLIENT = new WeakHashMap<>();

    private BaubleWornModifiers() {
    }

    /**
     * 一条要生效的属性
     */
    private static final class Worn {
        final String attributeName;
        final AttributeModifier modifier;

        Worn(@Nonnull String attributeName, @Nonnull AttributeModifier modifier) {
            this.attributeName = attributeName;
            this.modifier = modifier;
        }
    }

    /**
     * 一名玩家的缓存
     */
    private static final class WornState {
        /**
         * 缓存属于哪个玩家对象
         * <p>
         * 实体的 equals 比的是实体 ID，而重生（含从末地回来）时新玩家对象沿用旧 ID，
         * 光靠 map 查会把旧玩家的缓存拿给新玩家，以为属性已经加过了。这里再按对象本身核对一次。
         * 用弱引用，不然缓存反过来拽住玩家对象，WeakHashMap 就永远清不掉了。
         */
        final WeakReference<EntityPlayer> owner;
        /**
         * 每一格上次算属性时的物品（副本）
         */
        final List<ItemStack> seen = new ArrayList<>();
        /**
         * 每一格算出来的属性
         */
        final List<List<Worn>> computed = new ArrayList<>();
        /**
         * 每一格的版本号，重算一次加一
         */
        final List<Integer> versions = new ArrayList<>();
        /**
         * 每一格已经加到属性表上的属性（只有服务端用）
         */
        final List<List<Worn>> applied = new ArrayList<>();
        /**
         * 每一格加到属性表上时的版本号
         */
        final List<Integer> appliedVersions = new ArrayList<>();
        /**
         * 上次算的时候用的配置，配置改了就全部重算
         */
        @Nullable
        ConfigBaubles.BaubleNbtAttributes mode;

        WornState(@Nonnull EntityPlayer owner) {
            this.owner = new WeakReference<>(owner);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 对外
    // ═══════════════════════════════════════════════════════════════

    /**
     * 汇总饰品栏里某个属性的全部修饰符数值（给 AttributesUtil）
     *
     * @param player        玩家
     * @param handler       玩家的饰品栏
     * @param attributeName 属性名
     * @return 数组 [加法, 基础乘, 总计乘]，每个都是新建的列表
     */
    @Nonnull
    public static List<Double>[] collect(@Nonnull EntityPlayer player, @Nonnull IItemHandler handler,
                                         @Nonnull String attributeName) {
        List<Double>[] result = emptyResult();
        WornState state = refresh(player, handler);
        for (List<Worn> slot : state.computed) {
            for (Worn worn : slot) {
                if (worn.attributeName.equals(attributeName)) {
                    result[worn.modifier.getOperation()].add(worn.modifier.getAmount());
                }
            }
        }
        return result;
    }

    /**
     * 新建一个空结果
     *
     * @return 数组 [加法, 基础乘, 总计乘]
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    public static List<Double>[] emptyResult() {
        List<Double>[] result = new List[3];
        result[0] = new ArrayList<>();
        result[1] = new ArrayList<>();
        result[2] = new ArrayList<>();
        return result;
    }

    /**
     * 把饰品栏的属性同步到玩家的属性表上（服务端每 tick 末尾调用）
     * <p>
     * 只动版本号变了的格子：撤掉这一格上次加的，再加上这次算的。
     * 加的时候先看属性表上有没有这个属性（玩家没有的属性跳过），以及同一 UUID 是不是已经被占用（跳过，
     * 原版遇到重复 UUID 会直接抛异常）。撤的时候按 UUID 撤，只撤自己加过的。
     *
     * @param player  玩家
     * @param handler 玩家的饰品栏
     */
    public static void syncAttributeMap(@Nonnull EntityPlayer player, @Nonnull IItemHandler handler) {
        WornState state = refresh(player, handler);
        AbstractAttributeMap map = player.getAttributeMap();

        // 饰品栏变小了：多出来的格子上加过的属性全部撤掉
        for (int i = state.computed.size(); i < state.applied.size(); i++) {
            removeApplied(map, state.applied.get(i));
        }
        while (state.applied.size() > state.computed.size()) {
            state.applied.remove(state.applied.size() - 1);
            state.appliedVersions.remove(state.appliedVersions.size() - 1);
        }
        while (state.applied.size() < state.computed.size()) {
            state.applied.add(Collections.<Worn>emptyList());
            state.appliedVersions.add(-1);
        }

        for (int i = 0; i < state.computed.size(); i++) {
            int version = state.versions.get(i);
            if (state.appliedVersions.get(i) == version) {
                continue;
            }
            removeApplied(map, state.applied.get(i));
            List<Worn> nowApplied = apply(map, state.computed.get(i));
            state.applied.set(i, nowApplied);
            state.appliedVersions.set(i, version);

            if (ConfigBattle.enableDetailedLogging) {
                LogUtil.debug(String.format("饰品栏属性更新 - 玩家: %s, 格子: %d, 物品: %s, 生效条数: %d",
                        player.getName(), i, state.seen.get(i).getDisplayName(), nowApplied.size()));
            }
        }
    }

    /**
     * 玩家下线：撤掉缓存（属性表跟着玩家对象一起没了，不用逐条撤）
     *
     * @param player 玩家
     */
    public static void forget(@Nonnull EntityPlayer player) {
        Map<EntityPlayer, WornState> states = player.world.isRemote ? CLIENT : SERVER;
        WornState state = states.get(player);
        if (state != null && state.owner.get() == player) {
            states.remove(player);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 缓存
    // ═══════════════════════════════════════════════════════════════

    /**
     * 按饰品栏当前内容刷新缓存：物品变了的格子重算，其余不动
     *
     * @param player  玩家
     * @param handler 饰品栏
     * @return 缓存
     */
    @Nonnull
    private static WornState refresh(@Nonnull EntityPlayer player, @Nonnull IItemHandler handler) {
        Map<EntityPlayer, WornState> states = player.world.isRemote ? CLIENT : SERVER;
        WornState state = states.get(player);
        if (state == null || state.owner.get() != player) {
            state = new WornState(player);
            // 先删再放：put 遇到"相等"的旧键会保留旧的玩家对象当键，旧对象被回收时新缓存也跟着没了
            states.remove(player);
            states.put(player, state);
        }

        ConfigBaubles.BaubleNbtAttributes mode = ConfigBaubles.baubleNbtAttributes;
        boolean modeChanged = state.mode != mode;
        state.mode = mode;

        int slots = handler.getSlots();
        while (state.seen.size() > slots) {
            int last = state.seen.size() - 1;
            state.seen.remove(last);
            state.computed.remove(last);
            state.versions.remove(last);
        }
        while (state.seen.size() < slots) {
            state.seen.add(ItemStack.EMPTY);
            state.computed.add(Collections.<Worn>emptyList());
            state.versions.add(0);
        }

        for (int i = 0; i < slots; i++) {
            ItemStack current = handler.getStackInSlot(i);
            if (!modeChanged && ItemStack.areItemStacksEqual(current, state.seen.get(i))) {
                continue;
            }
            state.seen.set(i, current.isEmpty() ? ItemStack.EMPTY : current.copy());
            state.computed.set(i, compute(current, i, mode));
            state.versions.set(i, state.versions.get(i) + 1);
        }
        return state;
    }

    /**
     * 算一格要生效的属性
     *
     * @param stack 格子里的物品
     * @param index 格子序号
     * @param mode  未标记饰品的原版属性生效范围
     * @return 属性列表（已换成这一格的 UUID、已去重）
     */
    @Nonnull
    private static List<Worn> compute(@Nonnull ItemStack stack, int index, @Nonnull ConfigBaubles.BaubleNbtAttributes mode) {
        if (stack.isEmpty()) {
            return Collections.emptyList();
        }
        String slotType = BaubleMark.slotTypeAt(index);
        UUID slotUuid = BaubleMark.slotUuid(index);
        List<Worn> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // a) 命令写上去的附加属性
        for (BaubleMark.ModifierEntry entry : BaubleMark.getModifiers(stack)) {
            if (entry.appliesTo(slotType)) {
                add(result, seen, entry.attributeName(), BaubleMark.forSlot(slotUuid, entry.modifier()));
            }
        }

        // b) 物品自身的属性：标记物品一律算上，普通饰品按配置
        ConfigBaubles.BaubleNbtAttributes ownMode = BaubleMark.isMarked(stack) ? ConfigBaubles.BaubleNbtAttributes.ALL : mode;
        if (ownMode != ConfigBaubles.BaubleNbtAttributes.OFF) {
            boolean modOnly = ownMode == ConfigBaubles.BaubleNbtAttributes.MOD_ONLY;
            for (BaubleMark.ItemModifier item : BaubleMark.collectItemModifiers(stack)) {
                if (modOnly && !BaubleMark.isModAttribute(item.attributeName())) {
                    continue;
                }
                add(result, seen, item.attributeName(), BaubleMark.forSlot(slotUuid, item.modifier()));
            }
        }
        return result.isEmpty() ? Collections.<Worn>emptyList() : result;
    }

    /**
     * 加一条，同一属性的同一 UUID 只留第一条
     *
     * @param result        结果
     * @param seen          已有的"属性名|UUID"
     * @param attributeName 属性名
     * @param modifier      修饰符
     */
    private static void add(@Nonnull List<Worn> result, @Nonnull Set<String> seen,
                            @Nonnull String attributeName, @Nonnull AttributeModifier modifier) {
        if (modifier.getAmount() == 0) {
            return;
        }
        if (seen.add(attributeName + '|' + modifier.getID())) {
            result.add(new Worn(attributeName, modifier));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 属性表
    // ═══════════════════════════════════════════════════════════════

    /**
     * 把一格的属性加到属性表上
     * <p>
     * 属性表上已经有同一 UUID 的修饰符时不重复加（原版会直接抛异常）：
     * 数值和运算方式都一样的，就是这一格之前加的（比如缓存丢过一次），记成已加，摘下时照样撤；
     * 不一样的是别人的，不碰。
     *
     * @param map       属性表
     * @param modifiers 要加的属性
     * @return 由这一格负责撤掉的那些
     */
    @Nonnull
    private static List<Worn> apply(@Nonnull AbstractAttributeMap map, @Nonnull List<Worn> modifiers) {
        if (modifiers.isEmpty()) {
            return Collections.emptyList();
        }
        List<Worn> applied = new ArrayList<>(modifiers.size());
        for (Worn worn : modifiers) {
            IAttributeInstance instance = map.getAttributeInstanceByName(worn.attributeName);
            if (instance == null) {
                continue;
            }
            AttributeModifier existing = instance.getModifier(worn.modifier.getID());
            if (existing == null) {
                instance.applyModifier(worn.modifier);
                applied.add(worn);
            } else if (existing.getOperation() == worn.modifier.getOperation()
                    && Double.compare(existing.getAmount(), worn.modifier.getAmount()) == 0) {
                applied.add(worn);
            }
        }
        return applied;
    }

    /**
     * 撤掉之前加上的属性
     *
     * @param map     属性表
     * @param applied 之前加上的
     */
    private static void removeApplied(@Nonnull AbstractAttributeMap map, @Nonnull List<Worn> applied) {
        for (Worn worn : applied) {
            IAttributeInstance instance = map.getAttributeInstanceByName(worn.attributeName);
            // 先查再撤：撤一条不存在的修饰符也会把属性标脏，每次都会多发一个属性同步包
            if (instance != null && instance.getModifier(worn.modifier.getID()) != null) {
                instance.removeModifier(worn.modifier.getID());
            }
        }
    }
}
