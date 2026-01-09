// 文件：ItemStaff.java
// 路径：src/main/java/pers/roinflam/battlecorrection/item/manage/ItemStaff.java
package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 权杖物品基类
 * 所有管理类权杖的父类
 */
public abstract class ItemStaff extends Item {

    public ItemStaff(@Nonnull Properties properties) {
        super(properties);
    }

    @Override
    @Nonnull
    public Rarity getRarity(@Nonnull ItemStack stack) {
        return Rarity.EPIC;
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level,
                                @Nonnull List<Component> tooltipComponents,
                                @Nonnull TooltipFlag isAdvanced) {
        // 获取物品的tooltip翻译键
        String tooltipKey = getDescriptionId() + ".tooltip";
        Component tooltip = Component.translatable(tooltipKey);

        // 添加灰色斜体的tooltip
        tooltipComponents.add(tooltip.copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
    }
}