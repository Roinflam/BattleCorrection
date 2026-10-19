package pers.roinflam.battlecorrection.handlers;

import com.google.common.collect.Multimap;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.battlecorrection.compat.BaubleMark;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 饰品栏标记与附加属性的物品提示（仅客户端）
 * <p>
 * 1.12.2 没有 ItemAttributeModifierEvent，原版照样会给"只当饰品用"的物品画"在主手时："这一段，
 * 这里先把那几段删掉，再按饰品栏分段把物品自身的属性和附加属性画回来：
 * <pre>
 * 饰品 (指环、护身符)               ← 标了多种饰品栏时替换 Baubles 的"饰品 (任意)"那一行
 *
 * 佩戴指环时：                     ← 物品自身的属性 + 本模组附加的属性，同一饰品栏合并成一段
 *  +8 护甲
 *  +2 盔甲韧性
 *  +30% 暴击几率
 * </pre>
 * Baubles 自己只显示饰品类型，不显示任何属性，所以属性段落总是由这里画。
 * 用 {@link EventPriority#LOWEST}：排在 Baubles 和其他模组之后，能找到 Baubles 那一行，
 * 也保证本模组这几行不被别的处理顺手清掉。
 * <p>
 * 这个类只能在装了 Baubles 的客户端手动注册到事件总线（{@code BaublesIntegration.init()}），
 * 不能加 @Mod.EventBusSubscriber。
 */
@SideOnly(Side.CLIENT)
public final class BaubleMarkTooltipHandler {

    /**
     * 原版"隐藏属性修饰符"的 HideFlags 位（第 2 位）
     */
    private static final int HIDE_ATTRIBUTES_FLAG = 2;

    /**
     * 通用标题"佩戴饰品时："的翻译键
     */
    private static final String HEADER_ANY = "tooltip.battlecorrection.bauble.modifiers.any";

    private static final EntityEquipmentSlot[] EQUIPMENT_SLOTS = EntityEquipmentSlot.values();

    private BaubleMarkTooltipHandler() {
    }

    /**
     * 物品提示框事件
     *
     * @param evt 物品提示事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltip(@Nonnull ItemTooltipEvent evt) {
        ItemStack stack = evt.getItemStack();
        boolean marked = BaubleMark.isMarked(stack);
        boolean hasModifiers = BaubleMark.hasModifiers(stack);
        if (!marked && !hasModifiers) {
            return;
        }

        List<String> tooltip = evt.getToolTip();
        boolean baubleOnly = BaubleMark.isBaubleOnly(stack);
        boolean hiding = isHidingAttributes(stack);

        if (baubleOnly && !hiding) {
            removeVanillaModifierLines(tooltip, stack);
        }
        if (marked) {
            fixSlotLine(tooltip, stack);
        }
        if (!hiding) {
            appendModifierSections(tooltip, stack, baubleOnly, hasModifiers);
        }
    }

    /**
     * 标了多种饰品栏时，把 Baubles 的"饰品 (任意)"换成实际标的那几种
     * <p>
     * 只标了一种或 any 时 Baubles 那一行本来就是对的，不动；原生饰品能放哪由它自己决定，标记不起作用，也不动。
     *
     * @param tooltip 提示框行列表
     * @param stack   物品
     */
    private static void fixSlotLine(@Nonnull List<String> tooltip, @Nonnull ItemStack stack) {
        if (!"TRINKET".equals(BaubleMark.baubleTypeName(stack)) || BaubleMark.isNativeBauble(stack)) {
            return;
        }
        List<String> marked = BaubleMark.getSlots(stack);
        for (String slotId : marked) {
            if (BaubleMark.isAnyLike(slotId)) {
                return;
            }
        }

        String baublesLine = TextFormatting.GOLD + I18n.format("name.TRINKET");
        int index = tooltip.indexOf(baublesLine);
        String line = TextFormatting.GOLD + I18n.format("tooltip.battlecorrection.bauble.slots", joinLocalNames(marked));
        if (index >= 0) {
            tooltip.set(index, line);
        } else {
            tooltip.add(line);
        }
    }

    /**
     * 删掉原版给"只当饰品用"物品画的"在主手时："等段落
     * <p>
     * 原版每个装备槽画三部分：一个空行、标题（item.modifiers.槽位）、每条属性最多一行（数值为 0 的不画）。
     * 找到标题后，往后最多删"属性条数"行，而且只删看起来是属性行的（原版的属性行以空格、蓝色或红色加空格开头），
     * 碰到别的行（下一段的空行、"无法破坏"、别的模组的行）就停下，不会误删。
     *
     * @param tooltip 提示框行列表
     * @param stack   物品
     */
    @SuppressWarnings("deprecation")
    private static void removeVanillaModifierLines(@Nonnull List<String> tooltip, @Nonnull ItemStack stack) {
        for (EntityEquipmentSlot slot : EQUIPMENT_SLOTS) {
            Multimap<String, AttributeModifier> modifiers = stack.getAttributeModifiers(slot);
            if (modifiers.isEmpty()) {
                continue;
            }
            // 原版用的是服务端那套翻译（util.text.translation.I18n），这里用同一套，字符串才对得上
            String header = net.minecraft.util.text.translation.I18n.translateToLocal("item.modifiers." + slot.getName());
            int headerIndex = tooltip.indexOf(header);
            if (headerIndex < 0) {
                continue;
            }

            int end = headerIndex + 1;
            while (end < tooltip.size() && end <= headerIndex + modifiers.size() && isVanillaModifierLine(tooltip.get(end))) {
                end++;
            }
            int start = headerIndex > 0 && tooltip.get(headerIndex - 1).isEmpty() ? headerIndex - 1 : headerIndex;
            tooltip.subList(start, end).clear();
        }
    }

    /**
     * 是不是原版画的属性行
     *
     * @param line 行
     * @return true = 是
     */
    private static boolean isVanillaModifierLine(@Nonnull String line) {
        return line.startsWith(" ")
                || line.startsWith(TextFormatting.BLUE + " ")
                || line.startsWith(TextFormatting.RED + " ");
    }

    /**
     * 输出属性段落：物品自身属性在前，附加属性在后，同一饰品栏的合并成一段
     * <p>
     * 段落按标题翻译键归类（LinkedHashMap 保持先来先画的顺序）。物品自身属性只标记了一个具体饰品栏时
     * 用"佩戴指环时："这类标题，标记了多个或 any 时用通用的"佩戴饰品时："；附加属性按各自的 Slot 归类。
     * 两边标题相同时自然落到同一段里，不会出现两个"佩戴指环时："。
     * <p>
     * 物品自身属性只在"只当饰品用"时画：总开关关了原版会自己画"在胸部时："，这里再画就重复了。
     *
     * @param tooltip      提示框行列表
     * @param stack        物品
     * @param baubleOnly   物品是否只当饰品用
     * @param hasModifiers 物品是否带附加属性
     */
    private static void appendModifierSections(@Nonnull List<String> tooltip, @Nonnull ItemStack stack,
                                               boolean baubleOnly, boolean hasModifiers) {
        Map<String, List<String>> sections = new LinkedHashMap<>();

        if (baubleOnly) {
            String header = ownModifiersHeader(stack);
            for (BaubleMark.ItemModifier item : BaubleMark.collectItemModifiers(stack)) {
                addLine(sections, header, formatModifier(item.attributeName(), item.modifier()));
            }
        }
        if (hasModifiers) {
            for (BaubleMark.ModifierEntry entry : BaubleMark.getModifiers(stack)) {
                addLine(sections, sectionHeader(entry.slotId()), formatModifier(entry.attributeName(), entry.modifier()));
            }
        }

        for (Map.Entry<String, List<String>> section : sections.entrySet()) {
            tooltip.add("");
            tooltip.add(TextFormatting.GOLD + section.getKey());
            tooltip.addAll(section.getValue());
        }
    }

    /**
     * 物品自身属性段落的标题：只标记了一个具体饰品栏时用那个饰品栏的，否则用通用的
     *
     * @param stack 物品
     * @return 标题（已翻译）
     */
    @Nonnull
    private static String ownModifiersHeader(@Nonnull ItemStack stack) {
        List<String> slots = BaubleMark.getSlots(stack);
        if (slots.size() == 1) {
            return sectionHeader(slots.get(0));
        }
        return I18n.format(HEADER_ANY);
    }

    /**
     * 某个饰品栏的段落标题
     *
     * @param slotId 饰品栏ID
     * @return 标题（已翻译）；不认识的 ID 原样写进通用句式
     */
    @Nonnull
    private static String sectionHeader(@Nonnull String slotId) {
        if (BaubleMark.isAnyLike(slotId)) {
            return I18n.format(HEADER_ANY);
        }
        String key = "tooltip.battlecorrection.bauble.modifiers." + slotId;
        if (BaubleMark.SLOT_IDS.contains(slotId) && I18n.hasKey(key)) {
            return I18n.format(key);
        }
        return I18n.format("tooltip.battlecorrection.bauble.modifiers.other", slotId);
    }

    /**
     * 往某个段落里加一行（null 行跳过，段落不存在时创建）
     *
     * @param sections 段落表
     * @param header   段落标题
     * @param line     属性行；null 表示这条不显示
     */
    private static void addLine(@Nonnull Map<String, List<String>> sections, @Nonnull String header,
                                @Nullable String line) {
        if (line == null) {
            return;
        }
        List<String> lines = sections.get(header);
        if (lines == null) {
            lines = new ArrayList<>();
            sections.put(header, lines);
        }
        lines.add(line);
    }

    /**
     * 按原版 1.12.2 的格式生成一行属性说明
     * <p>
     * 乘法类显示百分比；数值不换算成总值（佩戴时加上去的就是这个数）。
     *
     * @param attributeName 属性名
     * @param modifier      修饰符
     * @return 蓝色（增加）或红色（减少）的一行文本；数值为 0 时返回 null
     */
    @Nullable
    private static String formatModifier(@Nonnull String attributeName, @Nonnull AttributeModifier modifier) {
        double amount = modifier.getAmount();
        if (amount == 0) {
            return null;
        }
        int operation = modifier.getOperation();
        double displayAmount = operation == 0 ? amount : amount * 100.0D;

        boolean positive = amount > 0;
        String key = "attribute.modifier." + (positive ? "plus." : "take.") + operation;
        String value = ItemStack.DECIMALFORMAT.format(positive ? displayAmount : -displayAmount);
        String name = I18n.format("attribute.name." + attributeName);
        return (positive ? TextFormatting.BLUE : TextFormatting.RED) + " " + I18n.format(key, value, name);
    }

    /**
     * 把饰品栏 ID 列表翻译成一行（用"、"分隔）
     *
     * @param slotIds 饰品栏ID列表
     * @return 已翻译的文本
     */
    @Nonnull
    private static String joinLocalNames(@Nonnull List<String> slotIds) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < slotIds.size(); i++) {
            if (i > 0) {
                builder.append(I18n.format("command.battlecorrection.bauble.separator"));
            }
            String key = BaubleMark.slotNameKey(slotIds.get(i));
            builder.append(key == null ? slotIds.get(i) : I18n.format(key));
        }
        return builder.toString();
    }

    /**
     * 物品是否设置了"隐藏属性修饰符"
     *
     * @param stack 物品
     * @return true = 隐藏，不显示属性行
     */
    private static boolean isHidingAttributes(@Nonnull ItemStack stack) {
        return stack.getTagCompound() != null && (stack.getTagCompound().getInteger("HideFlags") & HIDE_ATTRIBUTES_FLAG) != 0;
    }
}
