// 文件：TooltipHandler.java
// 路径：src/main/java/pers/roinflam/battlecorrection/handlers/TooltipHandler.java
package pers.roinflam.battlecorrection.handlers;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.battlecorrection.item.manage.ItemStaff;

import javax.annotation.Nonnull;

/**
 * 统一的物品工具提示处理器
 * 自动为所有 ItemStaff 添加工具提示
 */
@Mod.EventBusSubscriber
public class TooltipHandler {

    /**
     * 为继承自 ItemStaff 的物品自动添加工具提示
     * 剑类物品不添加tooltip，因为它们没有特殊效果
     */
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(@Nonnull ItemTooltipEvent evt) {
        ItemStack itemStack = evt.getItemStack();
        Item item = itemStack.getItem();

        // 只为ItemStaff添加tooltip
        if (item instanceof ItemStaff) {
            String unlocalizedName = item.getUnlocalizedName();
            String tooltipKey = "item." + unlocalizedName.substring(5) + ".tooltip";

            if (I18n.hasKey(tooltipKey)) {
                evt.getToolTip().add(1, TextFormatting.DARK_GRAY +
                        String.valueOf(TextFormatting.ITALIC) +
                        I18n.format(tooltipKey));
            }
        }
    }
}