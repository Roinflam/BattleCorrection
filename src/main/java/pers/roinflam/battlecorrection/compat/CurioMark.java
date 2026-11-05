package pers.roinflam.battlecorrection.compat;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import pers.roinflam.battlecorrection.utils.Reference;
import pers.roinflam.battlecorrection.config.ConfigAttribute;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.Set;
import java.util.UUID;

/**
 * 饰品栏标记与附加属性
 * <p>
 * 两件事都存在物品自己的数据上，只影响这一件物品（同类的其他物品不受影响），
 * 复制、交易、放箱子都会跟着走。1.21.1 起物品没有 NBT 了，存在 {@code minecraft:custom_data} 组件里，
 * 键名和条目格式和 1.20.1 完全一样（1.20.1 的存档升级上来时，原版修复器正好把这两个键原样搬进 custom_data）：
 * <pre>
 * BattleCorrectionCurioSlots: ["ring", "necklace"]   // 允许放入哪些饰品栏；["any"] = 任意
 * BattleCorrectionCurioModifiers: [                  // 放进饰品栏后额外生效的属性
 *   {AttributeName:"battlecorrection:custom_critical_chance", Amount:0.3d, Operation:0,
 *    Name:"...", UUID:[I;...], Slot:"ring"}          // Slot 省略或写 any = 所有饰品栏都生效
 * ]
 * </pre>
 * <p>
 * 为什么不直接用 Curios 原生的饰品属性（1.21.1 是 {@code curios:attribute_modifiers} 组件）：Curios 一旦发现物品上有它，
 * 就会跳过饰品自己的属性方法，饰品原本的护甲、幸运等属性会被整个替换掉。
 * 用自己的数据 + CurioAttributeModifierEvent 追加（见 {@link CurioMarkEvents}），
 * 原饰品的动态属性、配置属性和特殊逻辑全部保留。
 * <p>
 * 标记过的物品"只当饰品用"（{@link #isCurioOnly(ItemStack)}）：
 * 它自身的属性（防具的护甲、武器的攻击力、属性修饰符组件、其他模组加上去的属性）
 * 只在放进饰品栏时生效，拿在手上、穿在身上一律不给（{@link CurioMarkEvents} 在 NeoForge 的
 * ItemAttributeModifierEvent 里清空）；原本是防具的，标记后不能再穿到盔甲栏（见 {@code LivingEntityMixin}）。
 * <p>
 * 这个类不引用任何 Curios 的类，没装 Curios 时加载也不会出错，服务端和客户端都能用。
 */
public final class CurioMark {

    /**
     * 允许放入的饰品栏列表
     */
    public static final String TAG_SLOTS = "BattleCorrectionCurioSlots";

    /**
     * 附加属性列表
     */
    public static final String TAG_MODIFIERS = "BattleCorrectionCurioModifiers";

    /**
     * Curios 原生饰品属性组件的 ID。本模组只读它来判断"要不要给管理员一句提醒"，从不写入；
     * 按 ID 从注册表查，不引用 Curios 的类
     */
    public static final ResourceLocation CURIOS_NATIVE_MODIFIERS = ResourceLocation.fromNamespaceAndPath("curios", "attribute_modifiers");

    /**
     * 表示"任意饰品栏"的关键字
     */
    public static final String ANY = "any";

    /**
     * Curios 的通用饰品栏 ID：任何饰品都能放，放进去等于"以它自己的身份佩戴"
     */
    public static final String GENERIC_SLOT = "curio";

    /**
     * 一条附加属性
     *
     * @param attribute 属性
     * @param modifier  修饰符（ID 由数据里写的 UUID 换算，见 {@link #modifierId(UUID)}）
     * @param slotId    只在这个饰品栏生效；{@link #ANY} 表示所有饰品栏
     */
    public record ModifierEntry(Holder<Attribute> attribute, AttributeModifier modifier, String slotId) {

        /**
         * 这条属性在指定饰品栏里是否生效
         * <p>
         * 写的是 any → 哪都生效；写的是具体饰品栏 → 只在那个饰品栏生效。
         * 另外 Curios 的通用饰品栏（curio）接受任何饰品，物品放进去等于"以自己的身份佩戴"，
         * 所以放在通用饰品栏里时所有条目都生效——不然项链放进通用栏就成了没属性的摆设。
         *
         * @param targetSlotId 饰品栏ID
         * @return true = 生效
         */
        public boolean appliesTo(@Nonnull String targetSlotId) {
            return ANY.equals(slotId) || slotId.equals(targetSlotId) || GENERIC_SLOT.equals(targetSlotId);
        }
    }

    /**
     * 物品自身的一条属性（防具护甲、武器攻击力、属性修饰符组件、其他模组加的）
     *
     * @param attribute 属性
     * @param modifier  修饰符（ID 是物品原本的，放进饰品栏时由 CurioMarkEvents 换成属于格子的 ID）
     */
    public record ItemModifier(Holder<Attribute> attribute, AttributeModifier modifier) {
    }

    /**
     * 属性去重用的键：属性 + 修饰符 ID
     * <p>
     * 不能只按 ID 去重：原版盔甲的护甲、韧性、击退抗性三条修饰符共用同一个 ID（如 minecraft:armor.chestplate），
     * 只看 ID 会把韧性和击退抗性丢掉。
     *
     * @param attribute 属性
     * @param id        修饰符 ID
     */
    private record ModifierKey(Holder<Attribute> attribute, ResourceLocation id) {
    }

    /**
     * 收集物品自身属性时问的装备槽：1.20.1 的 6 个（主副手 + 4 个盔甲位）。
     * 1.21.1 枚举末尾多了动物盔甲的 BODY，只对马、狼生效，人形的饰品栏不该给它的属性，不算在内
     */
    private static final EquipmentSlot[] EQUIPMENT_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
    };

    /**
     * 当前线程是否正在"为饰品栏收集物品自身属性"
     * <p>
     * 收集时会调 ItemStack#getAttributeModifiers，它内部触发 NeoForge 的 ItemAttributeModifierEvent，
     * 而本模组在那个事件里会把标记物品的属性清空（让它拿手上、穿身上不生效）。
     * 这个标志用来告诉事件处理"这次是饰品栏在读，别清"。
     * 用 ThreadLocal 而不是普通静态变量：单人游戏时客户端和服务端在同一个进程里各跑一个线程，
     * 共用一个布尔值会互相干扰。
     */
    private static final ThreadLocal<Boolean> COLLECTING_ITEM_MODIFIERS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private CurioMark() {
    }

    // 物品数据（custom_data 组件）

    /**
     * 只读地取物品的 custom_data
     * <p>
     * {@code getUnsafe()} 返回组件内部那份标签、不复制：这里只读不改，{@link #isCurioOnly(ItemStack)}
     * 这种高频调用不会每次分配一份拷贝。要改一律走 {@link #editData(ItemStack, Consumer)}。
     *
     * @param stack 物品
     * @return custom_data 的标签；物品没有这个组件时返回 null
     */
    @Nullable
    @SuppressWarnings("deprecation")
    private static CompoundTag readData(@Nonnull ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.getUnsafe();
    }

    /**
     * 改物品的 custom_data：在一份拷贝上改，改完写回；改空了整个组件去掉（和 1.20.1 删空 NBT 的效果一样）
     *
     * @param stack  物品
     * @param editor 修改
     */
    private static void editData(@Nonnull ItemStack stack, @Nonnull Consumer<CompoundTag> editor) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, editor);
    }

    /**
     * 附加属性的修饰符 ID：由数据里写的 UUID 换算，同一条每次算出来都一样（摘下饰品时才能精确移除）
     *
     * @param uuid 数据里的 UUID
     * @return {@code battlecorrection:curio/<uuid>}
     */
    @Nonnull
    public static ResourceLocation modifierId(@Nonnull UUID uuid) {
        return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "curio/" + uuid);
    }

    /**
     * 属性的注册名
     *
     * @param attribute 属性
     * @return 注册名；没注册的返回 null
     */
    @Nullable
    private static ResourceLocation attributeKey(@Nonnull Holder<Attribute> attribute) {
        return attribute.unwrapKey().map(key -> key.location()).orElse(null);
    }

    /**
     * 槽位组是否在 1.20.1 的 6 个装备槽里至少一个生效
     *
     * @param group 槽位组
     * @return true = 生效
     */
    private static boolean appliesToAnySlot(@Nonnull EquipmentSlotGroup group) {
        for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
            if (group.test(slot)) {
                return true;
            }
        }
        return false;
    }

    // 饰品栏标记

    /**
     * 物品是否带有饰品栏标记
     *
     * @param stack 物品
     * @return true = 带有至少一个饰品栏标记
     */
    public static boolean isMarked(@Nonnull ItemStack stack) {
        CompoundTag tag = readData(stack);
        // 先用 contains 判断，避免对没有标记的物品新建空列表
        return tag != null && tag.contains(TAG_SLOTS, Tag.TAG_LIST)
                && !tag.getList(TAG_SLOTS, Tag.TAG_STRING).isEmpty();
    }

    /**
     * 读取物品允许放入的饰品栏列表
     *
     * @param stack 物品
     * @return 饰品栏ID列表（可能包含 {@link #ANY}）；没有标记时返回空列表
     */
    @Nonnull
    public static List<String> getSlots(@Nonnull ItemStack stack) {
        CompoundTag tag = readData(stack);
        if (tag == null || !tag.contains(TAG_SLOTS, Tag.TAG_LIST)) {
            return Collections.emptyList();
        }
        ListTag list = tag.getList(TAG_SLOTS, Tag.TAG_STRING);
        List<String> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getString(i));
        }
        return result;
    }

    /**
     * 物品是否允许放入指定饰品栏
     *
     * @param stack  物品
     * @param slotId 饰品栏ID（如 ring）
     * @return true = 标记里有这个饰品栏或 {@link #ANY}
     */
    public static boolean canEquip(@Nonnull ItemStack stack, @Nonnull String slotId) {
        CompoundTag tag = readData(stack);
        if (tag == null || !tag.contains(TAG_SLOTS, Tag.TAG_LIST)) {
            return false;
        }
        ListTag list = tag.getList(TAG_SLOTS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String markedSlot = list.getString(i);
            if (ANY.equals(markedSlot) || markedSlot.equals(slotId)) {
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
     * @param slotId 饰品栏ID或 {@link #ANY}
     * @return true = 添加成功；false = 已经可以放入该饰品栏，没有改动
     */
    public static boolean addSlot(@Nonnull ItemStack stack, @Nonnull String slotId) {
        List<String> current = getSlots(stack);
        // 已经是"任意"，或者已经有这个饰品栏：不用改
        if (current.contains(ANY) || current.contains(slotId)) {
            return false;
        }

        editData(stack, tag -> {
            // 添加"任意"时直接替换成只有 any 的列表；否则在原列表后面追加
            ListTag list = ANY.equals(slotId) ? new ListTag() : tag.getList(TAG_SLOTS, Tag.TAG_STRING);
            list.add(StringTag.valueOf(slotId));
            tag.put(TAG_SLOTS, list);
        });
        return true;
    }

    /**
     * 从物品上移除一个饰品栏标记，移空后整个标记会被删掉
     *
     * @param stack  物品
     * @param slotId 饰品栏ID或 {@link #ANY}
     * @return true = 移除成功；false = 物品没有这个标记
     */
    public static boolean removeSlot(@Nonnull ItemStack stack, @Nonnull String slotId) {
        CompoundTag tag = readData(stack);
        if (tag == null || !tag.contains(TAG_SLOTS, Tag.TAG_LIST)) {
            return false;
        }
        boolean[] removed = {false};
        editData(stack, edit -> {
            ListTag list = edit.getList(TAG_SLOTS, Tag.TAG_STRING);
            removed[0] = list.removeIf(element -> slotId.equals(element.getAsString()));
            if (list.isEmpty()) {
                edit.remove(TAG_SLOTS);
            }
        });
        return removed[0];
    }

    /**
     * 清除物品上的所有饰品栏标记
     *
     * @param stack 物品
     * @return true = 清除成功；false = 物品本来就没有标记
     */
    public static boolean clearSlots(@Nonnull ItemStack stack) {
        CompoundTag tag = readData(stack);
        if (tag == null || !tag.contains(TAG_SLOTS)) {
            return false;
        }
        editData(stack, edit -> edit.remove(TAG_SLOTS));
        return true;
    }

    // 只当饰品用

    /**
     * 这件物品原本是不是防具类（会穿到头/胸/腿/脚）
     * <p>
     * 不走 LivingEntity.getEquipmentSlotForItem（那个方法被 LivingEntityMixin 改过，标记物品会返回主手），
     * 而是直接看物品自己申明的槽位：先看 NeoForge 的自定义槽位，再看原版的 Equipable（盔甲、鞘翅、头颅/南瓜都实现了它）。
     *
     * @param stack 物品
     * @return true = 原本会穿到盔甲栏
     */
    public static boolean isArmorType(@Nonnull ItemStack stack) {
        EquipmentSlot forgeSlot = stack.getEquipmentSlot();
        if (forgeSlot != null) {
            return forgeSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
        }
        Equipable equipable = Equipable.get(stack);
        return equipable != null && equipable.getEquipmentSlot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    /**
     * 这件物品是否"只当饰品用"：自身属性只在饰品栏生效，原本是防具的也穿不进盔甲栏
     * <p>
     * 条件：装了 Curios（否则标记物品哪都放不进，剥夺了就成废品）、物品带标记、总开关开着。
     * 顺序按开销从小到大排：Curios 是否加载只是读一个静态布尔值，读物品数据次之，配置值最后。
     * 这个方法在背包点击、怪物 AI、每帧的提示框里都会被高频调用，不要在这里做任何遍历或分配。
     * <p>
     * 调用方：LivingEntityMixin（装备槽判定）、CurioMarkEvents（清空原版路径的属性）、CurioMarkTooltipHandler。
     *
     * @param stack 物品
     * @return true = 只当饰品用
     */
    public static boolean isCurioOnly(@Nonnull ItemStack stack) {
        return CuriosIntegration.isCuriosLoaded()
                && isMarked(stack)
                && ConfigAttribute.CURIO_MARK_ENABLED.get();
    }

    // 物品自身属性

    /**
     * 当前线程是否正在为饰品栏收集物品自身属性（供 CurioMarkEvents 的 ItemAttributeModifierEvent 处理判断）
     *
     * @return true = 正在收集，事件处理不要清空属性
     */
    public static boolean isCollectingItemModifiers() {
        return COLLECTING_ITEM_MODIFIERS.get();
    }

    /**
     * 收集物品自身的全部属性（原版格式）
     * <p>
     * 物品的属性是按装备槽给的（护甲只在"胸部"槽有，剑的攻击力只在"主手"槽有），而饰品栏不对应任何装备槽，
     * 所以把 6 个槽位能生效的条目全部收集起来。1.21.1 每条属性带一个槽位组（any、hand、armor……），
     * 一条在任意一个槽位生效就算；同一条（同属性、同 ID）只算一次，和 1.20.1 逐槽位问再按"属性 + UUID"去重的结果一样。
     * <p>
     * getAttributeModifiers 内部会触发 NeoForge 的 ItemAttributeModifierEvent，其他模组动态加的属性也会被带上；
     * 收集期间置起 {@link #COLLECTING_ITEM_MODIFIERS}，本模组自己的清空逻辑会放行。
     * <p>
     * 调用时机：Curios 装备/摘下时重算属性（服务端和客户端各一次）、客户端渲染提示框。
     * 都不是每 tick 的热点，6 次查询的开销可以接受。
     *
     * @param stack 物品
     * @return 属性列表（按槽位顺序，已去重）；没有时返回空列表
     */
    @Nonnull
    public static List<ItemModifier> collectItemModifiers(@Nonnull ItemStack stack) {
        // 大多数标记物品（普通材料、原本就是饰品的）没有原版属性，用到时才创建
        @Nullable List<ItemModifier> result = null;
        @Nullable Set<ModifierKey> seen = null;

        boolean previous = COLLECTING_ITEM_MODIFIERS.get();
        COLLECTING_ITEM_MODIFIERS.set(Boolean.TRUE);
        try {
            ItemAttributeModifiers modifiers = stack.getAttributeModifiers();
            for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
                if (!appliesToAnySlot(entry.slot())) {
                    continue;
                }
                if (result == null) {
                    result = new ArrayList<>();
                    seen = new HashSet<>();
                }
                if (seen.add(new ModifierKey(entry.attribute(), entry.modifier().id()))) {
                    result.add(new ItemModifier(entry.attribute(), entry.modifier()));
                }
            }
        } finally {
            // 恢复而不是直接置 false：万一外层也在收集（理论上不会），不把外层的标志抹掉
            COLLECTING_ITEM_MODIFIERS.set(previous);
        }
        return result == null ? Collections.emptyList() : result;
    }

    // 附加属性

    /**
     * 物品是否带有本模组的附加属性
     *
     * @param stack 物品
     * @return true = 带有至少一条附加属性
     */
    public static boolean hasModifiers(@Nonnull ItemStack stack) {
        CompoundTag tag = readData(stack);
        return tag != null && tag.contains(TAG_MODIFIERS, Tag.TAG_LIST)
                && !tag.getList(TAG_MODIFIERS, Tag.TAG_COMPOUND).isEmpty();
    }

    /**
     * 物品是否带有 Curios 原生的饰品属性组件
     * <p>
     * 带了的话，Curios 会跳过饰品自己的属性方法，原属性会被覆盖。命令里用它给管理员提个醒。
     * 空列表的组件 Curios 也当作没有（它判断的是条目非空），这里只看组件在不在，宁可多提醒一句。
     *
     * @param stack 物品
     * @return true = 带有 curios:attribute_modifiers
     */
    public static boolean hasNativeCuriosModifiers(@Nonnull ItemStack stack) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(CURIOS_NATIVE_MODIFIERS);
        return type != null && stack.has(type);
    }

    /**
     * 读取物品上的全部附加属性
     * <p>
     * 同一 UUID 只取第一条；属性不存在、UUID 缺失、运算方式非法的条目会被跳过。
     *
     * @param stack 物品
     * @return 属性列表；没有时返回空列表
     */
    @Nonnull
    public static List<ModifierEntry> getModifiers(@Nonnull ItemStack stack) {
        CompoundTag tag = readData(stack);
        if (tag == null || !tag.contains(TAG_MODIFIERS, Tag.TAG_LIST)) {
            return Collections.emptyList();
        }

        ListTag list = tag.getList(TAG_MODIFIERS, Tag.TAG_COMPOUND);
        List<ModifierEntry> result = new ArrayList<>(list.size());
        Set<ResourceLocation> seenIds = new HashSet<>();

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            ResourceLocation attributeId = ResourceLocation.tryParse(entry.getString("AttributeName"));
            if (attributeId == null) {
                continue;
            }
            Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(attributeId).orElse(null);
            if (attribute == null) {
                continue;
            }

            AttributeModifier modifier = parseModifier(entry);
            if (modifier == null || !seenIds.add(modifier.id())) {
                continue;
            }

            String slotId = entry.getString("Slot");
            result.add(new ModifierEntry(attribute, modifier, slotId.isEmpty() ? ANY : slotId));
        }
        return result;
    }

    /**
     * 往物品上追加一条属性
     * <p>
     * UUID 由调用方传入并写进 NBT，保证每次读出来都一样：Curios 摘下饰品时会重新算一遍属性去移除，
     * UUID 变了就会移除失败，留下摘不掉的残留属性。
     *
     * @param stack     物品
     * @param attribute 属性
     * @param amount    数值
     * @param operation 运算方式
     * @param slotId    生效的饰品栏；{@link #ANY} 表示所有饰品栏
     * @param uuid      这条属性的唯一ID
     */
    public static void addModifier(@Nonnull ItemStack stack, @Nonnull Holder<Attribute> attribute, double amount,
                                   @Nonnull AttributeModifier.Operation operation, @Nonnull String slotId,
                                   @Nonnull UUID uuid) {
        ResourceLocation attributeId = attributeKey(attribute);
        if (attributeId == null) {
            return;
        }

        CompoundTag entry = new CompoundTag();
        entry.putString("AttributeName", attributeId.toString());
        entry.putString("Name", "battlecorrection.curio");
        entry.putDouble("Amount", amount);
        entry.putInt("Operation", operation.id());
        entry.putUUID("UUID", uuid);
        entry.putString("Slot", slotId);

        editData(stack, tag -> {
            ListTag list = tag.getList(TAG_MODIFIERS, Tag.TAG_COMPOUND);
            list.add(entry);
            tag.put(TAG_MODIFIERS, list);
        });
    }

    /**
     * 移除某个属性的全部条目（可限定饰品栏）
     *
     * @param stack     物品
     * @param attribute 属性
     * @param slotId    只移除该饰品栏的条目；null = 不限饰品栏
     * @return 移除的条目数
     */
    public static int removeModifiers(@Nonnull ItemStack stack, @Nonnull Holder<Attribute> attribute,
                                      @Nullable String slotId) {
        CompoundTag tag = readData(stack);
        if (tag == null || !tag.contains(TAG_MODIFIERS, Tag.TAG_LIST)) {
            return 0;
        }
        ResourceLocation attributeId = attributeKey(attribute);
        if (attributeId == null) {
            return 0;
        }

        String target = attributeId.toString();
        int[] removed = {0};
        editData(stack, edit -> {
            ListTag list = edit.getList(TAG_MODIFIERS, Tag.TAG_COMPOUND);
            int before = list.size();
            list.removeIf(element -> {
                CompoundTag entry = (CompoundTag) element;
                if (!target.equals(entry.getString("AttributeName"))) {
                    return false;
                }
                if (slotId == null) {
                    return true;
                }
                String entrySlot = entry.getString("Slot");
                return slotId.equals(entrySlot.isEmpty() ? ANY : entrySlot);
            });
            if (list.isEmpty()) {
                edit.remove(TAG_MODIFIERS);
            }
            removed[0] = before - list.size();
        });
        return removed[0];
    }

    /**
     * 清除物品上的全部附加属性
     *
     * @param stack 物品
     * @return true = 清除成功；false = 物品本来就没有附加属性
     */
    public static boolean clearModifiers(@Nonnull ItemStack stack) {
        CompoundTag tag = readData(stack);
        if (tag == null || !tag.contains(TAG_MODIFIERS)) {
            return false;
        }
        editData(stack, edit -> edit.remove(TAG_MODIFIERS));
        return true;
    }

    /**
     * 解析一条附加属性（1.20.1 原版的修饰符格式：UUID 整数数组 + Operation 0/1/2）
     * <p>
     * 1.21.1 的修饰符按 ID 区分、没有名字：ID 由 UUID 换算（{@link #modifierId(UUID)}），Name 读了也不用，
     * 写的时候照样写上，存储格式和 1.20.1 保持一致。
     *
     * @param entry 修饰符 NBT
     * @return 修饰符；数据不完整或非法时返回 null
     */
    @Nullable
    private static AttributeModifier parseModifier(@Nonnull CompoundTag entry) {
        if (!entry.hasUUID("UUID")) {
            return null;
        }
        UUID uuid = entry.getUUID("UUID");
        if (uuid.getMostSignificantBits() == 0L && uuid.getLeastSignificantBits() == 0L) {
            return null;
        }

        int operationId = entry.getInt("Operation");
        if (operationId < 0 || operationId > 2) {
            return null;
        }

        return new AttributeModifier(modifierId(uuid), entry.getDouble("Amount"),
                AttributeModifier.Operation.BY_ID.apply(operationId));
    }

    // 显示

    /**
     * 饰品栏的显示名称（客户端按语言翻译）
     *
     * @param slotId 饰品栏ID或 {@link #ANY}
     * @return 可翻译的名称
     */
    @Nonnull
    public static MutableComponent slotName(@Nonnull String slotId) {
        if (ANY.equals(slotId)) {
            return Component.translatable("command.battlecorrection.curio.any");
        }
        // Curios 和添加饰品栏的模组都会提供 curios.identifier.<id> 的翻译
        return Component.translatable("curios.identifier." + slotId);
    }

    /**
     * 把多个饰品栏名称拼成一行（用"、"分隔）
     *
     * @param slotIds 饰品栏ID列表
     * @return 拼接后的文本
     */
    @Nonnull
    public static MutableComponent joinSlotNames(@Nonnull List<String> slotIds) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < slotIds.size(); i++) {
            if (i > 0) {
                result.append(Component.translatable("command.battlecorrection.curio.separator"));
            }
            result.append(slotName(slotIds.get(i)));
        }
        return result;
    }

    /**
     * 生成一个随机 UUID（命令添加属性时用）
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
}
