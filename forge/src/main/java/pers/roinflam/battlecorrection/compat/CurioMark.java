package pers.roinflam.battlecorrection.compat;

import com.google.common.collect.Multimap;
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
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.battlecorrection.config.ConfigAttribute;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 饰品栏标记与附加属性
 * <p>
 * 两件事都存在物品自己的 NBT 上，只影响这一件物品（同类的其他物品不受影响），
 * 复制、交易、放箱子都会跟着走：
 * <pre>
 * BattleCorrectionCurioSlots: ["ring", "necklace"]   // 允许放入哪些饰品栏；["any"] = 任意
 * BattleCorrectionCurioModifiers: [                  // 放进饰品栏后额外生效的属性
 *   {AttributeName:"battlecorrection:custom_critical_chance", Amount:0.3d, Operation:0,
 *    Name:"...", UUID:[I;...], Slot:"ring"}          // Slot 省略或写 any = 所有饰品栏都生效
 * ]
 * </pre>
 * <p>
 * 为什么不直接用 Curios 原生的 CurioAttributeModifiers：Curios 1.20.1 一旦发现物品上有那个 NBT，
 * 就会**跳过**物品自己的 getAttributeModifiers()，饰品原本的护甲、幸运等属性会被整个替换掉。
 * 用自己的 NBT + CurioAttributeModifierEvent 追加（见 {@link CurioMarkEvents}），
 * 原饰品的动态属性、配置属性和特殊逻辑全部保留。
 * <p>
 * 标记过的物品"只当饰品用"（{@link #isCurioOnly(ItemStack)}）：
 * 它自身的属性（防具的护甲、武器的攻击力、AttributeModifiers NBT、其他模组加上去的属性）
 * 只在放进饰品栏时生效，拿在手上、穿在身上一律不给（{@link CurioMarkEvents} 在 Forge 的
 * ItemAttributeModifierEvent 里清空）；原本是防具的，标记后不能再穿到盔甲栏（见 {@code MobMixin}）。
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
     * Curios 原生饰品属性列表的键名。本模组只读它来判断"要不要给管理员一句提醒"，从不写入
     */
    public static final String TAG_CURIOS_NATIVE_MODIFIERS = "CurioAttributeModifiers";

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
     * @param modifier  修饰符（UUID 用 NBT 里写的）
     * @param slotId    只在这个饰品栏生效；{@link #ANY} 表示所有饰品栏
     */
    public record ModifierEntry(Attribute attribute, AttributeModifier modifier, String slotId) {

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
     * 物品自身的一条属性（防具护甲、武器攻击力、AttributeModifiers NBT、其他模组加的）
     *
     * @param attribute 属性
     * @param modifier  修饰符（UUID 是物品原本的，放进饰品栏时由 CurioMarkEvents 混入格子 UUID）
     */
    public record ItemModifier(Attribute attribute, AttributeModifier modifier) {
    }

    /**
     * 属性去重用的键：属性 + 修饰符 UUID
     * <p>
     * 不能只按 UUID 去重：原版 ArmorItem 的护甲、韧性、击退抗性三条修饰符共用同一个 UUID，
     * 只看 UUID 会把韧性和击退抗性丢掉。
     *
     * @param attribute 属性
     * @param id        修饰符 UUID
     */
    private record ModifierKey(Attribute attribute, UUID id) {
    }

    /**
     * 原版装备槽列表（缓存起来，避免每次调用 values() 都新建数组）
     */
    private static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();

    /**
     * 当前线程是否正在"为饰品栏收集物品自身属性"
     * <p>
     * 收集时会调 ItemStack#getAttributeModifiers，它内部触发 Forge 的 ItemAttributeModifierEvent，
     * 而本模组在那个事件里会把标记物品的属性清空（让它拿手上、穿身上不生效）。
     * 这个标志用来告诉事件处理"这次是饰品栏在读，别清"。
     * 用 ThreadLocal 而不是普通静态变量：单人游戏时客户端和服务端在同一个进程里各跑一个线程，
     * 共用一个布尔值会互相干扰。
     */
    private static final ThreadLocal<Boolean> COLLECTING_ITEM_MODIFIERS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private CurioMark() {
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
        CompoundTag tag = stack.getTag();
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
        CompoundTag tag = stack.getTag();
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
        CompoundTag tag = stack.getTag();
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

        CompoundTag tag = stack.getOrCreateTag();
        // 添加"任意"时直接替换成只有 any 的列表；否则在原列表后面追加
        ListTag list = ANY.equals(slotId) ? new ListTag() : tag.getList(TAG_SLOTS, Tag.TAG_STRING);
        list.add(StringTag.valueOf(slotId));
        tag.put(TAG_SLOTS, list);
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
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_SLOTS, Tag.TAG_LIST)) {
            return false;
        }
        ListTag list = tag.getList(TAG_SLOTS, Tag.TAG_STRING);
        boolean removed = list.removeIf(element -> slotId.equals(element.getAsString()));
        if (list.isEmpty()) {
            stack.removeTagKey(TAG_SLOTS);
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
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_SLOTS)) {
            return false;
        }
        stack.removeTagKey(TAG_SLOTS);
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // 只当饰品用
    // ═══════════════════════════════════════════════════════════════

    /**
     * 这件物品原本是不是防具类（会穿到头/胸/腿/脚）
     * <p>
     * 不走 Mob.getEquipmentSlotForItem（那个方法被 MobMixin 改过，标记物品会返回主手），
     * 而是直接看物品自己申明的槽位：先看 Forge 的自定义槽位，再看原版的 Equipable（盔甲、鞘翅、头颅/南瓜都实现了它）。
     *
     * @param stack 物品
     * @return true = 原本会穿到盔甲栏
     */
    public static boolean isArmorType(@Nonnull ItemStack stack) {
        EquipmentSlot forgeSlot = stack.getEquipmentSlot();
        if (forgeSlot != null) {
            return forgeSlot.getType() == EquipmentSlot.Type.ARMOR;
        }
        Equipable equipable = Equipable.get(stack);
        return equipable != null && equipable.getEquipmentSlot().getType() == EquipmentSlot.Type.ARMOR;
    }

    /**
     * 这件物品是否"只当饰品用"：自身属性只在饰品栏生效，原本是防具的也穿不进盔甲栏
     * <p>
     * 条件：装了 Curios（否则标记物品哪都放不进，剥夺了就成废品）、物品带标记、总开关开着。
     * 顺序按开销从小到大排：Curios 是否加载只是读一个静态布尔值，读 NBT 次之，配置值最后。
     * 这个方法在背包点击、怪物 AI、每帧的提示框里都会被高频调用，不要在这里做任何遍历或分配。
     * <p>
     * 调用方：MobMixin（装备槽判定）、CurioMarkEvents（清空原版路径的属性）、CurioMarkTooltipHandler。
     *
     * @param stack 物品
     * @return true = 只当饰品用
     */
    public static boolean isCurioOnly(@Nonnull ItemStack stack) {
        return CuriosIntegration.isCuriosLoaded()
                && isMarked(stack)
                && ConfigAttribute.CURIO_MARK_ENABLED.get();
    }

    // ═══════════════════════════════════════════════════════════════
    // 物品自身属性
    // ═══════════════════════════════════════════════════════════════

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
     * 所以把 6 个槽位都问一遍，把问到的全部收集起来。没写 Slot 的 AttributeModifiers NBT 会在每个槽位
     * 都返回同一条（同一个 UUID），按"属性 + UUID"去重后只算一次。
     * <p>
     * getAttributeModifiers 内部会触发 Forge 的 ItemAttributeModifierEvent，其他模组动态加的属性也会被带上；
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
            for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
                Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(slot);
                if (modifiers.isEmpty()) {
                    continue;
                }
                if (result == null) {
                    result = new ArrayList<>();
                    seen = new HashSet<>();
                }
                for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
                    if (seen.add(new ModifierKey(entry.getKey(), entry.getValue().getId()))) {
                        result.add(new ItemModifier(entry.getKey(), entry.getValue()));
                    }
                }
            }
        } finally {
            // 恢复而不是直接置 false：万一外层也在收集（理论上不会），不把外层的标志抹掉
            COLLECTING_ITEM_MODIFIERS.set(previous);
        }
        return result == null ? Collections.emptyList() : result;
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
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_MODIFIERS, Tag.TAG_LIST)
                && !tag.getList(TAG_MODIFIERS, Tag.TAG_COMPOUND).isEmpty();
    }

    /**
     * 物品是否带有 Curios 原生的属性 NBT
     * <p>
     * 带了的话，Curios 会跳过饰品自己的属性方法，原属性会被覆盖。命令里用它给管理员提个醒。
     *
     * @param stack 物品
     * @return true = 带有 CurioAttributeModifiers
     */
    public static boolean hasNativeCuriosModifiers(@Nonnull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_CURIOS_NATIVE_MODIFIERS, Tag.TAG_LIST)
                && !tag.getList(TAG_CURIOS_NATIVE_MODIFIERS, Tag.TAG_COMPOUND).isEmpty();
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
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_MODIFIERS, Tag.TAG_LIST)) {
            return Collections.emptyList();
        }

        ListTag list = tag.getList(TAG_MODIFIERS, Tag.TAG_COMPOUND);
        List<ModifierEntry> result = new ArrayList<>(list.size());
        Set<UUID> seenIds = new HashSet<>();

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            ResourceLocation attributeId = ResourceLocation.tryParse(entry.getString("AttributeName"));
            if (attributeId == null) {
                continue;
            }
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(attributeId);
            if (attribute == null) {
                continue;
            }

            AttributeModifier modifier = parseModifier(entry);
            if (modifier == null || !seenIds.add(modifier.getId())) {
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
    public static void addModifier(@Nonnull ItemStack stack, @Nonnull Attribute attribute, double amount,
                                   @Nonnull AttributeModifier.Operation operation, @Nonnull String slotId,
                                   @Nonnull UUID uuid) {
        ResourceLocation attributeId = ForgeRegistries.ATTRIBUTES.getKey(attribute);
        if (attributeId == null) {
            return;
        }

        CompoundTag entry = new CompoundTag();
        entry.putString("AttributeName", attributeId.toString());
        entry.putString("Name", "battlecorrection.curio");
        entry.putDouble("Amount", amount);
        entry.putInt("Operation", operation.toValue());
        entry.putUUID("UUID", uuid);
        entry.putString("Slot", slotId);

        CompoundTag tag = stack.getOrCreateTag();
        ListTag list = tag.getList(TAG_MODIFIERS, Tag.TAG_COMPOUND);
        list.add(entry);
        tag.put(TAG_MODIFIERS, list);
    }

    /**
     * 移除某个属性的全部条目（可限定饰品栏）
     *
     * @param stack     物品
     * @param attribute 属性
     * @param slotId    只移除该饰品栏的条目；null = 不限饰品栏
     * @return 移除的条目数
     */
    public static int removeModifiers(@Nonnull ItemStack stack, @Nonnull Attribute attribute,
                                      @Nullable String slotId) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_MODIFIERS, Tag.TAG_LIST)) {
            return 0;
        }
        ResourceLocation attributeId = ForgeRegistries.ATTRIBUTES.getKey(attribute);
        if (attributeId == null) {
            return 0;
        }

        ListTag list = tag.getList(TAG_MODIFIERS, Tag.TAG_COMPOUND);
        String target = attributeId.toString();
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
            stack.removeTagKey(TAG_MODIFIERS);
        }
        return before - list.size();
    }

    /**
     * 清除物品上的全部附加属性
     *
     * @param stack 物品
     * @return true = 清除成功；false = 物品本来就没有附加属性
     */
    public static boolean clearModifiers(@Nonnull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_MODIFIERS)) {
            return false;
        }
        stack.removeTagKey(TAG_MODIFIERS);
        return true;
    }

    /**
     * 按原版格式解析一条修饰符
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

        String name = entry.getString("Name");
        return new AttributeModifier(uuid, name.isEmpty() ? "battlecorrection.curio" : name,
                entry.getDouble("Amount"), AttributeModifier.Operation.fromValue(operationId));
    }

    // ═══════════════════════════════════════════════════════════════
    // 显示
    // ═══════════════════════════════════════════════════════════════

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
