package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 权杖物品基类
 * 所有管理类权杖的父类
 */
public abstract class ItemStaff extends Item {

    public ItemStaff(@Nonnull Properties properties) {
        // 1.21.1：稀有度是物品组件，在属性里给（原来覆写的 getRarity 没有了）
        super(properties.rarity(Rarity.EPIC));
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context,
                                @Nonnull List<Component> tooltipComponents,
                                @Nonnull TooltipFlag isAdvanced) {
        // 获取物品的tooltip翻译键
        String tooltipKey = getDescriptionId() + ".tooltip";
        Component tooltip = Component.translatable(tooltipKey);

        // 添加灰色斜体的tooltip
        tooltipComponents.add(tooltip.copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

        super.appendHoverText(stack, context, tooltipComponents, isAdvanced);
    }
}
