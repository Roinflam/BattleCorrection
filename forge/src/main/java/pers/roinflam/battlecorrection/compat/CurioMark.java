package pers.roinflam.battlecorrection.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 饰品栏标记
 * <p>
 * 管理员用命令给某一件物品打上标记后，这一件物品就能放进指定的饰品栏（同类的其他物品不受影响）。
 * 标记直接写在物品自己的 NBT 上，复制、交易、存箱子都会跟着走。
 * <p>
 * NBT 结构：
 * <pre>
 * BattleCorrectionCurioSlots: ["ring", "necklace"]   // 允许放入的饰品栏
 * BattleCorrectionCurioSlots: ["any"]                // 任意饰品栏
 * </pre>
 * <p>
 * 放入饰品栏后生效的属性，读取的是物品上原版格式的 AttributeModifiers（管理员自己写的那些），
 * 不管每条写的 Slot 是什么都会生效；Slot 写成原版不认识的值（例如 "curios"）时，
 * 原版手持/穿戴都不会生效，就只在饰品栏里生效。
 * <p>
 * 这个类不引用任何 Curios 的类，没装 Curios 时加载也不会出错，服务端和客户端都能用。
 */
public final class CurioMark {

    /**
     * 标记在物品 NBT 中的键名
     */
    public static final String TAG_SLOTS = "BattleCorrectionCurioSlots";

    /**
     * 表示"任意饰品栏"的关键字
     */
    public static final String ANY = "any";

    /**
     * 原版属性修饰符列表的键名
     */
    private static final String TAG_ATTRIBUTE_MODIFIERS = "AttributeModifiers";

    /**
     * 一条解析好的属性修饰符
     *
     * @param attribute 属性
     * @param modifier  修饰符（使用物品 NBT 里写的原始 UUID）
     */
    public record ModifierEntry(Attribute attribute, AttributeModifier modifier) {
    }

    private CurioMark() {
    }

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
    public static boolean clear(@Nonnull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_SLOTS)) {
            return false;
        }
        stack.removeTagKey(TAG_SLOTS);
        return true;
    }

    /**
     * 读取物品上原版格式的属性修饰符（忽略每条的 Slot 字段）
     * <p>
     * 同一 UUID 只取第一条；属性不存在、UUID 缺失、运算方式非法的条目会被跳过。
     *
     * @param stack 物品
     * @return 修饰符列表；没有时返回空列表
     */
    @Nonnull
    public static List<ModifierEntry> readModifiers(@Nonnull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_ATTRIBUTE_MODIFIERS, Tag.TAG_LIST)) {
            return Collections.emptyList();
        }

        ListTag list = tag.getList(TAG_ATTRIBUTE_MODIFIERS, Tag.TAG_COMPOUND);
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
            result.add(new ModifierEntry(attribute, modifier));
        }
        return result;
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

        return new AttributeModifier(uuid, entry.getString("Name"), entry.getDouble("Amount"),
                AttributeModifier.Operation.fromValue(operationId));
    }

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
}
