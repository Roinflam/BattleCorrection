package pers.roinflam.battlecorrection.compat;

import com.google.common.collect.Multimap;
import com.google.common.primitives.Doubles;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import pers.roinflam.battlecorrection.config.ConfigBaubles;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 饰品栏标记与附加属性
 * <p>
 * 两件事都存在物品自己的 NBT 上，只影响这一件物品（同类的其他物品不受影响），
 * 复制、交易、放箱子都会跟着走：
 * <pre>
 * BattleCorrectionBaubleSlots: ["ring", "amulet"]    // 允许放入哪些饰品栏；["any"] = 任意
 * BattleCorrectionBaubleModifiers: [                 // 放进饰品栏后额外生效的属性
 *   {AttributeName:"battlecorrection.customCriticalChance", Amount:0.3d, Operation:0,
 *    Name:"...", UUIDMost:...L, UUIDLeast:...L, Slot:"ring"}   // Slot 省略或写 any = 所有饰品栏都生效
 * ]
 * </pre>
 * 属性条目和原版 AttributeModifiers 一样：属性名用 1.12.2 的名字（generic.maxHealth、battlecorrection.xxx），
 * UUID 拆成 UUIDMost / UUIDLeast 两个 long。
 * <p>
 * 饰品栏 ID 就是 Baubles 的饰品类型小写：amulet（护身符）、ring（指环，两格）、belt（腰带）、head（头部）、
 * body（身体）、charm（坠饰）。trinket 在 Baubles 里就是"哪都能放"，按 any 处理。
 * Baubles 1.5.2 的饰品栏固定 7 格（0 护身符、1/2 指环、3 腰带、4 头部、5 身体、6 坠饰），
 * 每件饰品只能报一种类型，所以标了多种时对 Baubles 报 TRINKET，再由 {@link BaubleMarkEvents} 把放错格子的挪走。
 * <p>
 * 标记过的物品"只当饰品用"（{@link #isBaubleOnly(ItemStack)}）：
 * 它自身的属性（防具的护甲、武器的攻击力、AttributeModifiers NBT、其他模组加上去的属性）
 * 只在放进饰品栏时生效，拿在手上、穿在身上一律不给；原本是防具的，穿进盔甲栏会被挪回饰品栏或背包。
 * 1.12.2 的 Forge 没有 ItemAttributeModifierEvent，这些都由 {@link BaubleMarkEvents} 在原版加上属性之后撤掉。
 * <p>
 * 这个类不引用任何 Baubles 的类，没装 Baubles 时加载也不会出错，服务端和客户端都能用。
 */
public final class BaubleMark {

    /**
     * 允许放入的饰品栏列表
     */
    public static final String TAG_SLOTS = "BattleCorrectionBaubleSlots";

    /**
     * 附加属性列表
     */
    public static final String TAG_MODIFIERS = "BattleCorrectionBaubleModifiers";

    /**
     * 表示"任意饰品栏"的关键字
     */
    public static final String ANY = "any";

    /**
     * Baubles 的"任意"类型名，读到时当成 {@link #ANY}
     */
    public static final String TRINKET = "trinket";

    /**
     * 命令写入的属性条目默认名字
     */
    public static final String MODIFIER_NAME = "battlecorrection.bauble";

    /**
     * 全部饰品栏类型，按 Baubles 界面里的格子顺序
     */
    public static final List<String> SLOT_IDS = Collections.unmodifiableList(
            Arrays.asList("amulet", "ring", "belt", "head", "body", "charm"));

    /**
     * {@link #SLOT_IDS} 对应的 BaubleType 枚举名
     */
    private static final String[] BAUBLE_TYPE_NAMES = {"AMULET", "RING", "BELT", "HEAD", "BODY", "CHARM"};

    /**
     * Baubles 1.5.2 每一格的类型（下标 = 格子序号）
     */
    private static final String[] SLOT_TYPE_BY_INDEX = {"amulet", "ring", "ring", "belt", "head", "body", "charm"};

    /**
     * 每一格的 UUID（缓存起来，避免每次都算一遍哈希）
     */
    private static final UUID[] SLOT_UUIDS = new UUID[SLOT_TYPE_BY_INDEX.length];

    /**
     * 原版装备槽列表（缓存起来，避免每次调用 values() 都新建数组）
     */
    private static final EntityEquipmentSlot[] EQUIPMENT_SLOTS = EntityEquipmentSlot.values();

    static {
        for (int i = 0; i < SLOT_UUIDS.length; i++) {
            SLOT_UUIDS[i] = computeSlotUuid(i);
        }
    }

    /**
     * 一条附加属性
     */
    public static final class ModifierEntry {
        private final String attributeName;
        private final AttributeModifier modifier;
        private final String slotId;

        /**
         * @param attributeName 属性名（1.12.2 的属性名，如 generic.armor）
         * @param modifier      修饰符（UUID 用 NBT 里写的）
         * @param slotId        只在这个饰品栏生效；{@link #ANY} 表示所有饰品栏
         */
        public ModifierEntry(@Nonnull String attributeName, @Nonnull AttributeModifier modifier, @Nonnull String slotId) {
            this.attributeName = attributeName;
            this.modifier = modifier;
            this.slotId = slotId;
        }

        @Nonnull
        public String attributeName() {
            return attributeName;
        }

        @Nonnull
        public AttributeModifier modifier() {
            return modifier;
        }

        @Nonnull
        public String slotId() {
            return slotId;
        }

        /**
         * 这条属性在指定饰品栏里是否生效
         * <p>
         * 写的是 any（或 trinket）→ 哪都生效；写的是具体饰品栏 → 只在那个饰品栏生效。
         * Baubles 没有"通用饰品栏"，每一格都有固定类型，所以不需要额外的放行规则。
         *
         * @param targetSlotType 物品所在格子的类型；null = 未知的格子，只有 any 的条目生效
         * @return true = 生效
         */
        public boolean appliesTo(@Nullable String targetSlotType) {
            return isAnyLike(slotId) || slotId.equals(targetSlotType);
        }
    }

    /**
     * 物品自身的一条属性（防具护甲、武器攻击力、AttributeModifiers NBT、其他模组加的）
     */
    public static final class ItemModifier {
        private final String attributeName;
        private final AttributeModifier modifier;

        /**
         * @param attributeName 属性名
         * @param modifier      修饰符（UUID 是物品原本的，放进饰品栏时混入格子 UUID）
         */
        public ItemModifier(@Nonnull String attributeName, @Nonnull AttributeModifier modifier) {
            this.attributeName = attributeName;
            this.modifier = modifier;
        }

        @Nonnull
        public String attributeName() {
            return attributeName;
        }

        @Nonnull
        public AttributeModifier modifier() {
            return modifier;
        }
    }

    private BaubleMark() {
    }

    // ═══════════════════════════════════════════════════════════════
    // 饰品栏 ID 与格子
    // ═══════════════════════════════════════════════════════════════

    /**
     * 是否是"任意饰品栏"（any 或 Baubles 的 trinket）
     *
     * @param slotId 饰品栏ID
     * @return true = 任意
     */
    public static boolean isAnyLike(@Nullable String slotId) {
        return ANY.equals(slotId) || TRINKET.equals(slotId);
    }

    /**
     * 是否是认识的饰品栏 ID（具体类型或 any / trinket）
     *
     * @param slotId 饰品栏ID
     * @return true = 认识
     */
    public static boolean isKnownSlot(@Nullable String slotId) {
        return isAnyLike(slotId) || SLOT_IDS.contains(slotId);
    }

    /**
     * 统一命令、界面输入的饰品栏 ID：转小写，trinket 换成 any
     *
     * @param slotId 输入的 ID
     * @return 统一后的 ID
     */
    @Nonnull
    public static String normalizeSlotId(@Nonnull String slotId) {
        String lower = slotId.toLowerCase(Locale.ROOT);
        return TRINKET.equals(lower) ? ANY : lower;
    }

    /**
     * 饰品栏第 index 格的类型
     *
     * @param index 格子序号
     * @return 类型 ID；超出 Baubles 默认 7 格时返回 null
     */
    @Nullable
    public static String slotTypeAt(int index) {
        return index >= 0 && index < SLOT_TYPE_BY_INDEX.length ? SLOT_TYPE_BY_INDEX[index] : null;
    }

    /**
     * 饰品栏第 index 格的 UUID
     * <p>
     * 由格子序号算出来，永远不变：放进去时加的属性和摘下来时撤的属性 UUID 对得上。
     *
     * @param index 格子序号
     * @return UUID
     */
    @Nonnull
    public static UUID slotUuid(int index) {
        return index >= 0 && index < SLOT_UUIDS.length ? SLOT_UUIDS[index] : computeSlotUuid(index);
    }

    @Nonnull
    private static UUID computeSlotUuid(int index) {
        return UUID.nameUUIDFromBytes((Reference.MOD_ID + ":bauble_slot/" + index).getBytes(StandardCharsets.UTF_8));
    }

    // ═══════════════════════════════════════════════════════════════
    // 饰品栏标记
    // ═══════════════════════════════════════════════════════════════

    /**
     * 物品是否带有饰品栏标记
     *
     * @param stack 物品
     * @return true = 带有至少一个饰品栏标记
     */
    public static boolean isMarked(@Nonnull ItemStack stack) {
        return isMarked(stack.getTagCompound());
    }

    /**
     * NBT 是否带有饰品栏标记
     *
     * @param tag 物品的 tag
     * @return true = 带有至少一个饰品栏标记
     */
    public static boolean isMarked(@Nullable NBTTagCompound tag) {
        // 先用 hasKey 判断，避免对没有标记的物品新建空列表
        return tag != null && tag.hasKey(TAG_SLOTS, 9) && !tag.getTagList(TAG_SLOTS, 8).hasNoTags();
    }

    /**
     * 读取物品允许放入的饰品栏列表
     *
     * @param stack 物品
     * @return 饰品栏ID列表（原样返回，可能包含 any / trinket）；没有标记时返回空列表
     */
    @Nonnull
    public static List<String> getSlots(@Nonnull ItemStack stack) {
        return getSlots(stack.getTagCompound());
    }

    /**
     * 读取 NBT 里允许放入的饰品栏列表
     *
     * @param tag 物品的 tag
     * @return 饰品栏ID列表；没有标记时返回空列表
     */
    @Nonnull
    public static List<String> getSlots(@Nullable NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(TAG_SLOTS, 9)) {
            return Collections.emptyList();
        }
        NBTTagList list = tag.getTagList(TAG_SLOTS, 8);
        List<String> result = new ArrayList<>(list.tagCount());
        for (int i = 0; i < list.tagCount(); i++) {
            result.add(list.getStringTagAt(i));
        }
        return result;
    }

    /**
     * 物品是否允许放入指定饰品栏
     *
     * @param stack    物品
     * @param slotType 饰品栏类型（如 ring）；null = 未知的格子
     * @return true = 标记里有这个饰品栏或 any
     */
    public static boolean canEquip(@Nonnull ItemStack stack, @Nullable String slotType) {
        return canEquip(stack.getTagCompound(), slotType);
    }

    /**
     * NBT 标记是否允许放入指定饰品栏
     *
     * @param tag      物品的 tag
     * @param slotType 饰品栏类型；null = 未知的格子，只有 any 放行
     * @return true = 允许
     */
    public static boolean canEquip(@Nullable NBTTagCompound tag, @Nullable String slotType) {
        if (tag == null || !tag.hasKey(TAG_SLOTS, 9)) {
            return false;
        }
        NBTTagList list = tag.getTagList(TAG_SLOTS, 8);
        for (int i = 0; i < list.tagCount(); i++) {
            String markedSlot = list.getStringTagAt(i);
            if (isAnyLike(markedSlot) || markedSlot.equals(slotType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 给物品添加一个允许放入的饰品栏
     * <p>
     * 添加 {@link #ANY} 时会把已有的具体饰品栏合并掉（"任意"已经包含它们）。
     *
     * @param stack  物品
     * @param slotId 饰品栏ID或 {@link #ANY}（trinket 会当成 any）
     * @return true = 添加成功；false = 已经可以放入该饰品栏，没有改动
     */
    public static boolean addSlot(@Nonnull ItemStack stack, @Nonnull String slotId) {
        NBTTagCompound tag = stack.getTagCompound();
        boolean created = tag == null;
        if (created) {
            tag = new NBTTagCompound();
        }
        boolean added = addSlot(tag, slotId);
        if (added && created) {
            stack.setTagCompound(tag);
        }
        return added;
    }

    /**
     * 在 NBT 上添加一个允许放入的饰品栏
     *
     * @param tag    物品的 tag
     * @param slotId 饰品栏ID或 {@link #ANY}
     * @return true = 添加成功
     */
    public static boolean addSlot(@Nonnull NBTTagCompound tag, @Nonnull String slotId) {
        String id = TRINKET.equals(slotId) ? ANY : slotId;
        List<String> current = getSlots(tag);
        // 已经是"任意"，或者已经有这个饰品栏：不用改
        if (current.contains(id) || current.contains(ANY) || current.contains(TRINKET)) {
            return false;
        }
        // 添加"任意"时直接替换成只有 any 的列表；否则在原列表后面追加
        NBTTagList list = ANY.equals(id) ? new NBTTagList() : tag.getTagList(TAG_SLOTS, 8);
        list.appendTag(new NBTTagString(id));
        tag.setTag(TAG_SLOTS, list);
        return true;
    }

    /**
     * 从物品上移除一个饰品栏标记，移空后整个标记会被删掉
     *
     * @param stack  物品
     * @param slotId 饰品栏ID或 {@link #ANY}（移除 any 时 trinket 也一起移除）
     * @return true = 移除成功；false = 物品没有这个标记
     */
    public static boolean removeSlot(@Nonnull ItemStack stack, @Nonnull String slotId) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return false;
        }
        boolean removed = removeSlot(tag, slotId);
        dropEmptyTag(stack);
        return removed;
    }

    /**
     * 从 NBT 上移除一个饰品栏标记
     *
     * @param tag    物品的 tag
     * @param slotId 饰品栏ID或 {@link #ANY}
     * @return true = 移除成功
     */
    public static boolean removeSlot(@Nonnull NBTTagCompound tag, @Nonnull String slotId) {
        if (!tag.hasKey(TAG_SLOTS, 9)) {
            return false;
        }
        NBTTagList list = tag.getTagList(TAG_SLOTS, 8);
        boolean anyLike = isAnyLike(slotId);
        boolean removed = false;
        for (int i = list.tagCount() - 1; i >= 0; i--) {
            String marked = list.getStringTagAt(i);
            if (anyLike ? isAnyLike(marked) : slotId.equals(marked)) {
                list.removeTag(i);
                removed = true;
            }
        }
        if (list.hasNoTags()) {
            tag.removeTag(TAG_SLOTS);
        }
        return removed;
    }

    /**
     * 清除物品上的所有饰品栏标记
     *
     * @param stack 物品
     * @return true = 清除成功；false = 物品本来就没有标记
     */
    public static boolean clearSlots(@Nonnull ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_SLOTS)) {
            return false;
        }
        tag.removeTag(TAG_SLOTS);
        dropEmptyTag(stack);
        return true;
    }

    /**
     * 物品对 Baubles 报的饰品类型（BaubleType 的枚举名）
     * <p>
     * 标了 any / trinket → TRINKET；只标了一种 → 那一种；标了多种 → TRINKET
     * （Baubles 的每件饰品只能有一种类型，判定格子时也不告诉物品是哪一格，
     * 能同时容纳多种类型的只有 TRINKET；放错的格子由 {@link BaubleMarkEvents} 纠正）。
     * 标记里只有不认识的 ID（手写 NBT）时不算饰品。
     *
     * @param stack 物品
     * @return 枚举名（大写）；不是饰品时返回 null
     */
    @Nullable
    public static String baubleTypeName(@Nonnull ItemStack stack) {
        return baubleTypeName(stack.getTagCompound());
    }

    /**
     * NBT 标记对应的 Baubles 饰品类型
     *
     * @param tag 物品的 tag
     * @return 枚举名；不是饰品时返回 null
     */
    @Nullable
    public static String baubleTypeName(@Nullable NBTTagCompound tag) {
        // 饰品能力每次被问都会走到这里（Baubles 每 tick 问一遍戴着的每一格），所以直接扫 NBT，不建临时集合
        if (tag == null || !tag.hasKey(TAG_SLOTS, 9)) {
            return null;
        }
        NBTTagList list = tag.getTagList(TAG_SLOTS, 8);
        int single = -1;
        for (int i = 0; i < list.tagCount(); i++) {
            String slotId = list.getStringTagAt(i);
            if (isAnyLike(slotId)) {
                return "TRINKET";
            }
            int index = SLOT_IDS.indexOf(slotId);
            if (index < 0) {
                continue;
            }
            if (single < 0) {
                single = index;
            } else if (single != index) {
                return "TRINKET";
            }
        }
        return single < 0 ? null : BAUBLE_TYPE_NAMES[single];
    }

    // ═══════════════════════════════════════════════════════════════
    // 只当饰品用
    // ═══════════════════════════════════════════════════════════════

    /**
     * 这件物品原本是不是防具类（会穿到头/胸/腿/脚）
     * <p>
     * 用原版判断装备槽的总入口（含 Forge 的 Item#getEquipmentSlot、南瓜、头颅、鞘翅）。
     *
     * @param stack 物品
     * @return true = 原本会穿到盔甲栏
     */
    public static boolean isArmorType(@Nonnull ItemStack stack) {
        return EntityLiving.getSlotForItemStack(stack).getSlotType() == EntityEquipmentSlot.Type.ARMOR;
    }

    /**
     * 这件物品是否"只当饰品用"：自身属性只在饰品栏生效，原本是防具的也穿不进盔甲栏
     * <p>
     * 条件：装了 Baubles（否则标记物品哪都放不进，剥夺了就成废品）、物品带标记、总开关开着。
     * 顺序按开销从小到大排：Baubles 是否加载只是读一个静态布尔值，读 NBT 次之。
     * 这个方法在属性计算、装备变动、每帧的提示框里都会被高频调用，不要在这里做任何遍历或分配。
     * <p>
     * 调用方：AttributesUtil（跳过手上、身上的标记物品）、BaubleMarkEvents、BaubleMarkTooltipHandler。
     *
     * @param stack 物品
     * @return true = 只当饰品用
     */
    public static boolean isBaubleOnly(@Nonnull ItemStack stack) {
        return BaublesIntegration.isBaublesLoaded()
                && isMarked(stack)
                && ConfigBaubles.baubleMarkEnabled;
    }

    /**
     * 物品本身就是 Baubles 饰品（实现了 IBauble，或者别的模组给它挂了饰品能力）
     * <p>
     * 这类物品能放进哪个格子由它自己决定，饰品栏标记改变不了（本模组不会在它们身上再挂一份饰品能力）；
     * 附加属性和"只当饰品用"照常生效。命令和编辑器用它给管理员提个醒。
     *
     * @param stack 物品
     * @return true = 原生饰品；没装 Baubles 时恒为 false
     */
    public static boolean isNativeBauble(@Nonnull ItemStack stack) {
        return BaublesIntegration.isBaublesLoaded() && !stack.isEmpty() && BaublesAccess.isNativeBauble(stack);
    }

    // ═══════════════════════════════════════════════════════════════
    // 物品自身属性
    // ═══════════════════════════════════════════════════════════════

    /**
     * 收集物品自身的全部属性（原版格式）
     * <p>
     * 物品的属性是按装备槽给的（护甲只在"胸部"槽有，剑的攻击力只在"主手"槽有），而饰品栏不对应任何装备槽，
     * 所以把 6 个槽位都问一遍，把问到的全部收集起来。没写 Slot 的 AttributeModifiers NBT 会在每个槽位
     * 都返回同一条（同一个 UUID），按"属性 + UUID"去重后只算一次。
     * 不能只按 UUID 去重：原版盔甲的护甲、韧性两条修饰符共用同一个 UUID。
     * <p>
     * 调用时机：饰品栏里的物品变动时（服务端和客户端各算一次，结果缓存）、客户端渲染提示框。
     * 都不是每 tick 的热点，6 次查询的开销可以接受。
     *
     * @param stack 物品
     * @return 属性列表（按槽位顺序，已去重）；没有时返回空列表
     */
    @Nonnull
    public static List<ItemModifier> collectItemModifiers(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            return Collections.emptyList();
        }
        // 大多数饰品没有原版属性，用到时才创建
        List<ItemModifier> result = null;
        Set<String> seen = null;

        for (EntityEquipmentSlot slot : EQUIPMENT_SLOTS) {
            Multimap<String, AttributeModifier> modifiers = stack.getAttributeModifiers(slot);
            if (modifiers.isEmpty()) {
                continue;
            }
            if (result == null) {
                result = new ArrayList<>();
                seen = new HashSet<>();
            }
            for (Map.Entry<String, AttributeModifier> entry : modifiers.entries()) {
                if (seen.add(entry.getKey() + '|' + entry.getValue().getID())) {
                    result.add(new ItemModifier(entry.getKey(), entry.getValue()));
                }
            }
        }
        return result == null ? Collections.<ItemModifier>emptyList() : result;
    }

    /**
     * 是否是本模组注册的属性
     *
     * @param attributeName 属性名
     * @return true = 以 battlecorrection. 开头
     */
    public static boolean isModAttribute(@Nonnull String attributeName) {
        return attributeName.startsWith(Reference.MOD_ID + ".");
    }

    // ═══════════════════════════════════════════════════════════════
    // 附加属性
    // ═══════════════════════════════════════════════════════════════

    /**
     * 物品是否带有本模组的附加属性
     *
     * @param stack 物品
     * @return true = 带有至少一条附加属性
     */
    public static boolean hasModifiers(@Nonnull ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(TAG_MODIFIERS, 9) && !tag.getTagList(TAG_MODIFIERS, 10).hasNoTags();
    }

    /**
     * 读取物品上的全部附加属性
     *
     * @param stack 物品
     * @return 属性列表；没有时返回空列表
     */
    @Nonnull
    public static List<ModifierEntry> getModifiers(@Nonnull ItemStack stack) {
        return getModifiers(stack.getTagCompound());
    }

    /**
     * 读取 NBT 上的全部附加属性
     * <p>
     * 同一 UUID 只取第一条；属性名为空、UUID 缺失、运算方式或数值非法的条目会被跳过。
     * 这里不检查属性存不存在：1.12.2 没有全局属性注册表，属性挂在各个实体的属性表上，
     * 加到实体身上时找不到的属性自然不生效。
     *
     * @param tag 物品的 tag
     * @return 属性列表；没有时返回空列表
     */
    @Nonnull
    public static List<ModifierEntry> getModifiers(@Nullable NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(TAG_MODIFIERS, 9)) {
            return Collections.emptyList();
        }

        NBTTagList list = tag.getTagList(TAG_MODIFIERS, 10);
        List<ModifierEntry> result = new ArrayList<>(list.tagCount());
        Set<UUID> seenIds = new HashSet<>();

        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);

            String attributeName = entry.getString("AttributeName");
            if (attributeName.isEmpty()) {
                continue;
            }

            AttributeModifier modifier = parseModifier(entry);
            if (modifier == null || !seenIds.add(modifier.getID())) {
                continue;
            }

            String slotId = entry.getString("Slot");
            result.add(new ModifierEntry(attributeName, modifier, slotId.isEmpty() ? ANY : slotId));
        }
        return result;
    }

    /**
     * 往物品上追加一条属性
     * <p>
     * UUID 由调用方传入并写进 NBT，保证每次读出来都一样：摘下饰品时按同一个 UUID 撤掉属性，
     * UUID 变了就会撤不掉，留下摘不掉的残留属性。
     *
     * @param stack         物品
     * @param attributeName 属性名
     * @param amount        数值
     * @param operation     运算方式（0 加法 / 1 基础乘 / 2 总计乘）
     * @param slotId        生效的饰品栏；{@link #ANY} 表示所有饰品栏
     * @param uuid          这条属性的唯一ID
     */
    public static void addModifier(@Nonnull ItemStack stack, @Nonnull String attributeName, double amount,
                                   int operation, @Nonnull String slotId, @Nonnull UUID uuid) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        addModifier(stack.getTagCompound(), attributeName, amount, operation, slotId, uuid);
    }

    /**
     * 往 NBT 上追加一条属性
     *
     * @param tag           物品的 tag
     * @param attributeName 属性名
     * @param amount        数值
     * @param operation     运算方式
     * @param slotId        生效的饰品栏
     * @param uuid          这条属性的唯一ID
     */
    public static void addModifier(@Nonnull NBTTagCompound tag, @Nonnull String attributeName, double amount,
                                   int operation, @Nonnull String slotId, @Nonnull UUID uuid) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("AttributeName", attributeName);
        entry.setString("Name", MODIFIER_NAME);
        entry.setDouble("Amount", amount);
        entry.setInteger("Operation", operation);
        entry.setUniqueId("UUID", uuid);
        entry.setString("Slot", TRINKET.equals(slotId) ? ANY : slotId);

        NBTTagList list = tag.getTagList(TAG_MODIFIERS, 10);
        list.appendTag(entry);
        tag.setTag(TAG_MODIFIERS, list);
    }

    /**
     * 移除某个属性的全部条目（可限定饰品栏）
     *
     * @param stack         物品
     * @param attributeName 属性名
     * @param slotId        只移除该饰品栏的条目；null = 不限饰品栏
     * @return 移除的条目数
     */
    public static int removeModifiers(@Nonnull ItemStack stack, @Nonnull String attributeName,
                                      @Nullable String slotId) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return 0;
        }
        int removed = removeModifiers(tag, attributeName, slotId);
        dropEmptyTag(stack);
        return removed;
    }

    /**
     * 从 NBT 上移除某个属性的全部条目（可限定饰品栏）
     *
     * @param tag           物品的 tag
     * @param attributeName 属性名
     * @param slotId        只移除该饰品栏的条目；null = 不限饰品栏
     * @return 移除的条目数
     */
    public static int removeModifiers(@Nonnull NBTTagCompound tag, @Nonnull String attributeName,
                                      @Nullable String slotId) {
        if (!tag.hasKey(TAG_MODIFIERS, 9)) {
            return 0;
        }
        NBTTagList list = tag.getTagList(TAG_MODIFIERS, 10);
        int before = list.tagCount();

        for (int i = list.tagCount() - 1; i >= 0; i--) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!attributeName.equals(entry.getString("AttributeName"))) {
                continue;
            }
            if (slotId != null) {
                String entrySlot = entry.getString("Slot");
                if (!slotId.equals(entrySlot.isEmpty() ? ANY : entrySlot)) {
                    continue;
                }
            }
            list.removeTag(i);
        }

        if (list.hasNoTags()) {
            tag.removeTag(TAG_MODIFIERS);
        }
        return before - list.tagCount();
    }

    /**
     * 清除物品上的全部附加属性
     *
     * @param stack 物品
     * @return true = 清除成功；false = 物品本来就没有附加属性
     */
    public static boolean clearModifiers(@Nonnull ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_MODIFIERS)) {
            return false;
        }
        tag.removeTag(TAG_MODIFIERS);
        dropEmptyTag(stack);
        return true;
    }

    /**
     * 按原版格式解析一条修饰符
     *
     * @param entry 修饰符 NBT
     * @return 修饰符；数据不完整或非法时返回 null
     */
    @Nullable
    private static AttributeModifier parseModifier(@Nonnull NBTTagCompound entry) {
        if (!entry.hasUniqueId("UUID")) {
            return null;
        }
        UUID uuid = entry.getUniqueId("UUID");
        if (uuid == null || (uuid.getMostSignificantBits() == 0L && uuid.getLeastSignificantBits() == 0L)) {
            return null;
        }

        int operation = entry.getInteger("Operation");
        if (operation < 0 || operation > 2) {
            return null;
        }

        // 手写 NBT 里的 NaN / 无穷大会让属性值（比如最大生命值）整个变成 NaN
        double amount = entry.getDouble("Amount");
        if (!Doubles.isFinite(amount)) {
            return null;
        }

        String name = entry.getString("Name");
        return new AttributeModifier(uuid, name.isEmpty() ? MODIFIER_NAME : name, amount, operation);
    }

    /**
     * 把修饰符改成"属于这个格子"的版本
     * <p>
     * 把格子的 UUID 混进修饰符 UUID：同一格子每次算出来都一样（摘下时才能精确移除），
     * 不同格子互不相同（两件一样的饰品戴在两个指环格里会分别生效，不会互相覆盖）。
     * 对物品自身属性尤其重要：所有胸甲共用同一个护甲修饰符 UUID，不混的话，饰品栏里的胸甲会顶掉
     * 身上真正穿着的胸甲的护甲，摘下饰品时还会把它一起删掉。
     * <p>
     * 生成的修饰符不存盘（setSaved(false)）：重进游戏时由 {@link BaubleWornModifiers} 按饰品栏重新加上。
     *
     * @param slotUuid 当前格子的 UUID
     * @param original 原修饰符
     * @return 新修饰符（数值、运算方式、名字不变）
     */
    @Nonnull
    public static AttributeModifier forSlot(@Nonnull UUID slotUuid, @Nonnull AttributeModifier original) {
        return new AttributeModifier(
                mixUuid(slotUuid, original.getID()),
                original.getName(),
                original.getAmount(),
                original.getOperation()
        ).setSaved(false);
    }

    /**
     * 把两个 UUID 按位异或，得到稳定且互不冲突的新 UUID
     *
     * @param slotUuid     饰品栏格子的 UUID
     * @param modifierUuid 修饰符原本的 UUID
     * @return 混合后的 UUID
     */
    @Nonnull
    public static UUID mixUuid(@Nonnull UUID slotUuid, @Nonnull UUID modifierUuid) {
        return new UUID(slotUuid.getMostSignificantBits() ^ modifierUuid.getMostSignificantBits(),
                slotUuid.getLeastSignificantBits() ^ modifierUuid.getLeastSignificantBits());
    }

    /**
     * 生成一个随机 UUID（命令、编辑器添加属性时用）
     *
     * @return 随机 UUID
     */
    @Nonnull
    public static UUID newModifierUuid() {
        UUID uuid = UUID.randomUUID();
        // 极小概率拿到全 0 的 UUID，会被 parseModifier 当成非法数据跳过
        while (uuid.getMostSignificantBits() == 0L && uuid.getLeastSignificantBits() == 0L) {
            uuid = UUID.randomUUID();
        }
        return uuid;
    }

    /**
     * tag 被删空时整个去掉
     * <p>
     * 1.12.2 里 tag 为 null 和 tag 为 {} 的两个物品叠不到一起，删空了不清掉的话，
     * 加过又撤掉标记的物品就和同类的普通物品叠不上了。
     *
     * @param stack 物品
     */
    private static void dropEmptyTag(@Nonnull ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasNoTags()) {
            stack.setTagCompound(null);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 显示
    // ═══════════════════════════════════════════════════════════════

    /**
     * 饰品栏显示名称的翻译键
     *
     * @param slotId 饰品栏ID或 {@link #ANY}
     * @return 翻译键；不认识的 ID 返回 null
     */
    @Nullable
    public static String slotNameKey(@Nonnull String slotId) {
        if (isAnyLike(slotId)) {
            return "command.battlecorrection.bauble.any";
        }
        return SLOT_IDS.contains(slotId) ? "command.battlecorrection.bauble.slot." + slotId : null;
    }

    /**
     * 饰品栏的显示名称（客户端按语言翻译）
     *
     * @param slotId 饰品栏ID或 {@link #ANY}
     * @return 可翻译的名称；不认识的 ID 原样显示
     */
    @Nonnull
    public static ITextComponent slotName(@Nonnull String slotId) {
        String key = slotNameKey(slotId);
        return key == null ? new TextComponentString(slotId) : new TextComponentTranslation(key);
    }

    /**
     * 把多个饰品栏名称拼成一行（用"、"分隔）
     *
     * @param slotIds 饰品栏ID列表
     * @return 拼接后的文本
     */
    @Nonnull
    public static ITextComponent joinSlotNames(@Nonnull List<String> slotIds) {
        ITextComponent result = new TextComponentString("");
        for (int i = 0; i < slotIds.size(); i++) {
            if (i > 0) {
                result.appendSibling(new TextComponentTranslation("command.battlecorrection.bauble.separator"));
            }
            result.appendSibling(slotName(slotIds.get(i)));
        }
        return result;
    }
}
