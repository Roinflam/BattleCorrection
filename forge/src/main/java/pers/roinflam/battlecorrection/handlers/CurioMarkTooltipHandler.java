package pers.roinflam.battlecorrection.handlers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.compat.CurioMark;
import pers.roinflam.battlecorrection.compat.CuriosIntegration;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 饰品栏标记的物品提示（仅客户端）
 * <p>
 * 带标记的物品在提示框末尾显示：
 * <pre>
 * 可放入饰品栏：戒指、项链
 *
 * 放入饰品栏时：
 * +0.3 暴击几率
 * +4 最大生命值
 * </pre>
 * 属性行的格式和原版"在主手时："下面的一样。
 * Slot 写成原版不认识的值（如 curios）的修饰符原版提示不会显示，只会出现在这里。
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public class CurioMarkTooltipHandler {

    /**
     * 物品提示框事件
     *
     * @param evt 物品提示事件
     */
    @SubscribeEvent
    public static void onItemTooltip(@Nonnull ItemTooltipEvent evt) {
        if (!CuriosIntegration.isCuriosLoaded()) {
            return;
        }

        ItemStack stack = evt.getItemStack();
        if (!CurioMark.isMarked(stack)) {
            return;
        }

        List<Component> tooltip = evt.getToolTip();
        tooltip.add(Component.translatable("tooltip.battlecorrection.curio.slots",
                CurioMark.joinSlotNames(CurioMark.getSlots(stack))).withStyle(ChatFormatting.GOLD));

        List<CurioMark.ModifierEntry> entries = CurioMark.readModifiers(stack);
        if (entries.isEmpty()) {
            return;
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.battlecorrection.curio.when_worn").withStyle(ChatFormatting.GRAY));
        for (CurioMark.ModifierEntry entry : entries) {
            Component line = formatModifier(entry);
            if (line != null) {
                tooltip.add(line);
            }
        }
    }

    /**
     * 按原版格式生成一行属性说明
     *
     * @param entry 属性修饰符
     * @return 蓝色（增加）或红色（减少）的一行文本；数值为 0 时返回 null
     */
    @Nullable
    private static Component formatModifier(@Nonnull CurioMark.ModifierEntry entry) {
        AttributeModifier modifier = entry.modifier();
        double amount = modifier.getAmount();
        if (amount == 0) {
            return null;
        }

        // 与原版一致：乘法类显示百分比；击退抗性按 ×10 显示
        double displayAmount;
        if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
            displayAmount = entry.attribute() == Attributes.KNOCKBACK_RESISTANCE ? amount * 10.0D : amount;
        } else {
            displayAmount = amount * 100.0D;
        }

        Component attributeName = Component.translatable(entry.attribute().getDescriptionId());
        int operationId = modifier.getOperation().toValue();
        if (amount > 0) {
            return Component.translatable("attribute.modifier.plus." + operationId,
                    ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(displayAmount), attributeName)
                    .withStyle(ChatFormatting.BLUE);
        }
        return Component.translatable("attribute.modifier.take." + operationId,
                ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(-displayAmount), attributeName)
                .withStyle(ChatFormatting.RED);
    }
}
