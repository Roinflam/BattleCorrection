package pers.roinflam.battlecorrection.handlers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import pers.roinflam.battlecorrection.compat.CurioMark;
import top.theillusivec4.curios.api.CuriosApi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 饰品栏标记与附加属性的物品提示（仅客户端）
 * <p>
 * 标记物品的自身属性已经在原版路径上被清空（见 CurioMarkEvents#onItemAttributeModifiers），
 * 所以原版不会再画"在胸部时："那一段；这里按 Curios 的样式把它们画回来，和附加属性一起按饰品栏分段：
 * <pre>
 * 栏位: 戒指                      ← 只在 Curios 自己不显示时才补（标记物品不在物品标签里）
 *
 * 佩戴戒指时：                     ← 物品自身的属性 + 本模组附加的属性，同一饰品栏合并成一段
 * +8 护甲
 * +2 盔甲韧性
 * +30% 暴击几率
 * </pre>
 * 用 {@link EventPriority#LOWEST}：有些饰品（例如石英戒指）会在自己的提示处理里清掉属性行，
 * 排在最后能保证本模组这几行不被顺手清掉。
 * <p>
 * 这个类引用了 Curios 的类，只能在装了 Curios 时手动注册到事件总线，
 * 不能加 @Mod.EventBusSubscriber（否则没装 Curios 时启动就会找不到类而崩溃）。
 */
public final class CurioMarkTooltipHandler {

    /**
     * 原版"隐藏属性修饰符"的 HideFlags 位（第 2 位）
     */
    private static final int HIDE_ATTRIBUTES_FLAG = 2;

    /**
     * Curios 通用标题"佩戴饰品时："的翻译键
     */
    private static final String HEADER_ANY = "curios.modifiers.curio";

    private CurioMarkTooltipHandler() {
    }

    /**
     * 物品提示框事件
     *
     * @param evt 物品提示事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltip(@Nonnull ItemTooltipEvent evt) {
        ItemStack stack = evt.getItemStack();
        boolean marked = CurioMark.isMarked(stack);
        boolean hasModifiers = CurioMark.hasModifiers(stack);
        if (!marked && !hasModifiers) {
            return;
        }

        List<Component> tooltip = evt.getToolTip();

        if (marked) {
            appendSlotLine(tooltip, stack);
        }
        // Curios 认得这件物品（有 curios:* 标签、实现了 ICurio、或者被本模组注册的校验器放行）时，
        // 它自己就会画"佩戴 xx 时："那一段——而且里面已经包含我们通过事件追加的属性。再画一遍就是两份。
        if (!isHidingAttributes(stack) && !curiosRecognizes(stack)) {
            appendModifierSections(tooltip, stack, marked, hasModifiers);
        }
    }

    /**
     * Curios 自己认不认这件物品是饰品（认的话它会自己画属性段落）
     *
     * @param stack 物品
     * @return true = 认
     */
    private static boolean curiosRecognizes(@Nonnull ItemStack stack) {
        try {
            return !CuriosApi.getItemStackSlots(stack, true).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 输出"栏位: xxx"一行
     * <p>
     * Curios 自己也会显示这一行，但它只认物品标签。所以这里只补 Curios 认不出来的那几个饰品栏，
     * 避免本来就是饰品的物品（例如加过标记的石英戒指）出现两行。
     *
     * @param tooltip 提示框行列表
     * @param stack   物品
     */
    private static void appendSlotLine(@Nonnull List<Component> tooltip, @Nonnull ItemStack stack) {
        List<String> marked = CurioMark.getSlots(stack);
        // Curios 已经认出来的饰品栏（客户端侧数据）
        Set<String> known;
        try {
            known = CuriosApi.getItemStackSlots(stack, true).keySet();
        } catch (Exception e) {
            known = Collections.emptySet();
        }

        List<String> extra = new ArrayList<>(marked.size());
        for (String slotId : marked) {
            if (!known.contains(slotId)) {
                extra.add(slotId);
            }
        }
        if (extra.isEmpty()) {
            return;
        }

        tooltip.add(Component.translatable("curios.tooltip.slot").append(" ")
                .withStyle(ChatFormatting.GOLD)
                .append(CurioMark.joinSlotNames(extra).withStyle(ChatFormatting.YELLOW)));
    }

    /**
     * 输出属性段落：物品自身属性在前，附加属性在后，同一饰品栏的合并成一段
     * <p>
     * 段落按标题翻译键归类（LinkedHashMap 保持先来先画的顺序）。物品自身属性只标记了一个具体饰品栏时
     * 用"佩戴戒指时："这类标题，标记了多个或 any 时用通用的"佩戴饰品时："；附加属性按各自的 Slot 归类。
     * 两边标题相同时自然落到同一段里，不会出现两个"佩戴戒指时："。
     * <p>
     * 物品自身属性只在"只当饰品用"时画：总开关关了原版会自己画"在胸部时："，这里再画就重复了。
     *
     * @param tooltip      提示框行列表
     * @param stack        物品
     * @param marked       物品是否带饰品栏标记
     * @param hasModifiers 物品是否带附加属性
     */
    private static void appendModifierSections(@Nonnull List<Component> tooltip, @Nonnull ItemStack stack,
                                               boolean marked, boolean hasModifiers) {
        Map<String, List<Component>> sections = new LinkedHashMap<>();

        if (marked && CurioMark.isCurioOnly(stack)) {
            String headerKey = ownModifiersHeaderKey(stack);
            for (CurioMark.ItemModifier item : CurioMark.collectItemModifiers(stack)) {
                addLine(sections, headerKey, formatModifier(item.attribute(), item.modifier()));
            }
        }
        if (hasModifiers) {
            for (CurioMark.ModifierEntry entry : CurioMark.getModifiers(stack)) {
                // any 用 Curios 的通用文案"佩戴饰品时："，具体饰品栏用"佩戴戒指时："这类
                String headerKey = CurioMark.ANY.equals(entry.slotId())
                        ? HEADER_ANY
                        : "curios.modifiers." + entry.slotId();
                addLine(sections, headerKey, formatModifier(entry.attribute(), entry.modifier()));
            }
        }

        for (Map.Entry<String, List<Component>> section : sections.entrySet()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(section.getKey()).withStyle(ChatFormatting.GOLD));
            tooltip.addAll(section.getValue());
        }
    }

    /**
     * 物品自身属性段落的标题：只标记了一个具体饰品栏时用那个饰品栏的，否则用通用的
     *
     * @param stack 物品
     * @return 标题翻译键
     */
    @Nonnull
    private static String ownModifiersHeaderKey(@Nonnull ItemStack stack) {
        List<String> slots = CurioMark.getSlots(stack);
        if (slots.size() == 1 && !CurioMark.ANY.equals(slots.get(0))) {
            return "curios.modifiers." + slots.get(0);
        }
        return HEADER_ANY;
    }

    /**
     * 往某个段落里加一行（null 行跳过，段落不存在时创建）
     *
     * @param sections  段落表
     * @param headerKey 段落标题翻译键
     * @param line      属性行；null 表示这条不显示
     */
    private static void addLine(@Nonnull Map<String, List<Component>> sections, @Nonnull String headerKey,
                                @Nullable Component line) {
        if (line == null) {
            return;
        }
        sections.computeIfAbsent(headerKey, key -> new ArrayList<>()).add(line);
    }

    /**
     * 按原版格式生成一行属性说明
     *
     * @param attribute 属性
     * @param modifier  修饰符
     * @return 蓝色（增加）或红色（减少）的一行文本；数值为 0 时返回 null
     */
    @Nullable
    private static Component formatModifier(@Nonnull Attribute attribute, @Nonnull AttributeModifier modifier) {
        double amount = modifier.getAmount();
        if (amount == 0) {
            return null;
        }

        // 与原版一致：乘法类显示百分比，击退抗性按 ×10 显示
        double displayAmount;
        if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
            displayAmount = attribute == Attributes.KNOCKBACK_RESISTANCE ? amount * 10.0D : amount;
        } else {
            displayAmount = amount * 100.0D;
        }

        boolean positive = amount > 0;
        String key = "attribute.modifier." + (positive ? "plus." : "take.") + modifier.getOperation().toValue();
        String value = ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(positive ? displayAmount : -displayAmount);
        return Component.translatable(key, value, Component.translatable(attribute.getDescriptionId()))
                .withStyle(positive ? ChatFormatting.BLUE : ChatFormatting.RED);
    }

    /**
     * 物品是否设置了"隐藏属性修饰符"
     *
     * @param stack 物品
     * @return true = 隐藏，不显示属性行
     */
    private static boolean isHidingAttributes(@Nonnull ItemStack stack) {
        return stack.getTag() != null && (stack.getTag().getInt("HideFlags") & HIDE_ATTRIBUTES_FLAG) != 0;
    }
}
