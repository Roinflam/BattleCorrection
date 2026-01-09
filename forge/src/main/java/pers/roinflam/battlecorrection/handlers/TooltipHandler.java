package pers.roinflam.battlecorrection.handlers;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.item.manage.ItemStaff;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 统一的物品工具提示处理器
 * 自动为所有 ItemStaff 添加工具提示
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public class TooltipHandler {

    /**
     * 为继承自 ItemStaff 的物品自动添加工具提示
     * 剑类物品不添加tooltip，因为它们没有特殊效果
     */
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(@Nonnull ItemTooltipEvent evt) {
        ItemStack itemStack = evt.getItemStack();
        Item item = itemStack.getItem();

        // 只为ItemStaff添加tooltip
        if (item instanceof ItemStaff) {
            // 1.20.1使用 getDescriptionId() 替代 getUnlocalizedName()
            String descriptionId = item.getDescriptionId();
            // descriptionId 格式: "item.battlecorrection.enemy_staff"
            // 提取物品名称部分
            String itemName = descriptionId.substring(descriptionId.lastIndexOf('.') + 1);
            String tooltipKey = "item.battlecorrection." + itemName + ".tooltip";

            if (I18n.exists(tooltipKey)) {
                evt.getToolTip().add(1, Component.literal(I18n.get(tooltipKey))
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .withStyle(ChatFormatting.ITALIC));
            }
        }
    }
}