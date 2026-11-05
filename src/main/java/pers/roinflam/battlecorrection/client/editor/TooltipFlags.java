package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import javax.annotation.Nonnull;

/**
 * 1.20.1 的 HideFlags 在 1.21.1 的对应：每一位拆成了某个组件自己的"在提示框里显示"开关
 * <p>
 * 对应关系照原版数据修复器（{@code ItemStackComponentizationFix}）：
 * <ul>
 *   <li>1 附魔：{@code minecraft:enchantments} 的 show_in_tooltip</li>
 *   <li>2 属性：{@code minecraft:attribute_modifiers} 的 show_in_tooltip</li>
 *   <li>4 不可破坏：{@code minecraft:unbreakable} 的 show_in_tooltip</li>
 *   <li>8 可破坏：{@code minecraft:can_break} 的 show_in_tooltip</li>
 *   <li>16 可放置于：{@code minecraft:can_place_on} 的 show_in_tooltip</li>
 *   <li>32 其他信息：{@code minecraft:hide_additional_tooltip}；附魔书的存储附魔也归这一位（{@code minecraft:stored_enchantments}）</li>
 *   <li>64 染色：{@code minecraft:dyed_color} 的 show_in_tooltip</li>
 *   <li>128 盔甲纹饰：{@code minecraft:trim} 的 show_in_tooltip</li>
 * </ul>
 * 和 1.20.1 不同的是，开关长在组件上：物品上没有那个组件（不可破坏、可破坏、可放置于、染色、纹饰）时就没东西可藏，
 * 也存不下"隐藏"这个状态，界面上把这种开关灰掉（{@link #available}）。附魔、属性、其他信息三项任何物品都能设。
 */
public final class TooltipFlags {

    public static final int ENCHANTMENTS = 1;
    public static final int MODIFIERS = 2;
    public static final int UNBREAKABLE = 4;
    public static final int CAN_BREAK = 8;
    public static final int CAN_PLACE_ON = 16;
    public static final int ADDITIONAL = 32;
    public static final int DYE = 64;
    public static final int TRIM = 128;

    private TooltipFlags() {
    }

    /**
     * 物品上现在隐藏了哪些信息，按 1.20.1 HideFlags 的位拼起来
     *
     * @param stack 物品
     * @return 位
     */
    public static int read(@Nonnull ItemStack stack) {
        int flags = 0;
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null && hidden(enchantments)) {
            flags |= ENCHANTMENTS;
        }
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers != null && !modifiers.showInTooltip()) {
            flags |= MODIFIERS;
        }
        Unbreakable unbreakable = stack.get(DataComponents.UNBREAKABLE);
        if (unbreakable != null && !unbreakable.showInTooltip()) {
            flags |= UNBREAKABLE;
        }
        AdventureModePredicate canBreak = stack.get(DataComponents.CAN_BREAK);
        if (canBreak != null && !canBreak.showInTooltip()) {
            flags |= CAN_BREAK;
        }
        AdventureModePredicate canPlace = stack.get(DataComponents.CAN_PLACE_ON);
        if (canPlace != null && !canPlace.showInTooltip()) {
            flags |= CAN_PLACE_ON;
        }
        if (stack.has(DataComponents.HIDE_ADDITIONAL_TOOLTIP)) {
            flags |= ADDITIONAL;
        }
        DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
        if (dyed != null && !dyed.showInTooltip()) {
            flags |= DYE;
        }
        ArmorTrim trim = stack.get(DataComponents.TRIM);
        if (trim != null && trim.equals(trim.withTooltip(false))) {
            flags |= TRIM;
        }
        return flags;
    }

    /**
     * 这一类信息在这件物品上能不能藏（有没有对应的组件）
     *
     * @param stack 物品
     * @param flag  1.20.1 HideFlags 的一位
     * @return true = 能
     */
    public static boolean available(@Nonnull ItemStack stack, int flag) {
        return switch (flag) {
            case UNBREAKABLE -> stack.has(DataComponents.UNBREAKABLE);
            case CAN_BREAK -> stack.has(DataComponents.CAN_BREAK);
            case CAN_PLACE_ON -> stack.has(DataComponents.CAN_PLACE_ON);
            case DYE -> stack.has(DataComponents.DYED_COLOR);
            case TRIM -> stack.has(DataComponents.TRIM);
            default -> true;
        };
    }

    /**
     * 隐藏 / 显示一类信息
     * <p>
     * 改的是组件自己的显示开关；组件值和物品默认值一样时补丁项自动去掉（比如把附魔重新设成显示）。
     * 物品上没有对应组件的那几位什么都不做。
     *
     * @param stack 物品
     * @param flag  1.20.1 HideFlags 的一位
     * @param hide  true = 隐藏
     */
    public static void set(@Nonnull ItemStack stack, int flag, boolean hide) {
        boolean show = !hide;
        switch (flag) {
            case ENCHANTMENTS -> stack.set(DataComponents.ENCHANTMENTS,
                    stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).withTooltip(show));
            case MODIFIERS -> stack.set(DataComponents.ATTRIBUTE_MODIFIERS,
                    stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).withTooltip(show));
            case UNBREAKABLE -> {
                Unbreakable unbreakable = stack.get(DataComponents.UNBREAKABLE);
                if (unbreakable != null) {
                    stack.set(DataComponents.UNBREAKABLE, unbreakable.withTooltip(show));
                }
            }
            case CAN_BREAK -> {
                AdventureModePredicate predicate = stack.get(DataComponents.CAN_BREAK);
                if (predicate != null) {
                    stack.set(DataComponents.CAN_BREAK, predicate.withTooltip(show));
                }
            }
            case CAN_PLACE_ON -> {
                AdventureModePredicate predicate = stack.get(DataComponents.CAN_PLACE_ON);
                if (predicate != null) {
                    stack.set(DataComponents.CAN_PLACE_ON, predicate.withTooltip(show));
                }
            }
            case ADDITIONAL -> {
                if (hide) {
                    stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
                } else {
                    stack.remove(DataComponents.HIDE_ADDITIONAL_TOOLTIP);
                }
                // 附魔书的存储附魔在 1.20.1 也归这一位管（修复器同样这么搬）
                ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
                if (stored != null || stack.is(Items.ENCHANTED_BOOK)) {
                    stack.set(DataComponents.STORED_ENCHANTMENTS,
                            (stored == null ? ItemEnchantments.EMPTY : stored).withTooltip(show));
                }
            }
            case DYE -> {
                DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
                if (dyed != null) {
                    stack.set(DataComponents.DYED_COLOR, dyed.withTooltip(show));
                }
            }
            case TRIM -> {
                ArmorTrim trim = stack.get(DataComponents.TRIM);
                if (trim != null) {
                    stack.set(DataComponents.TRIM, trim.withTooltip(show));
                }
            }
            default -> {
            }
        }
    }

    /**
     * 附魔组件的显示开关没有公开的读法，拿"设成隐藏后的样子"和自己比：一样就是已经隐藏了
     */
    private static boolean hidden(@Nonnull ItemEnchantments enchantments) {
        return enchantments.equals(enchantments.withTooltip(false));
    }
}
