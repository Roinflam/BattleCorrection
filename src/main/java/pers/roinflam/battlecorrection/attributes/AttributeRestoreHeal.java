package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 治疗恢复加成属性
 * 提高自身受到的治疗效果，如自然恢复、瞬间治疗药水等
 */
@Mod.EventBusSubscriber
public class AttributeRestoreHeal {
    public static final UUID ID = UUID.fromString("9fbbfcb8-cd9b-6967-a6e1-21aeb72ad066");
    public static final String NAME = "battlecorrection.restoreHeal";

    public static final IAttribute RESTORE_HEAL = (new RangedAttribute(null, NAME, 1, 0, Float.MAX_VALUE)).setDescription("Extra Restore Heal");

    /**
     * 处理治疗事件
     * 当实体恢复生命值时触发
     */
    @SubscribeEvent
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            @Nullable EntityLivingBase entityLivingBase = evt.getEntityLiving();

            float originalAmount = evt.getAmount();
            float attributeValue = AttributesUtil.getAttributeValue(entityLivingBase, RESTORE_HEAL);
            float configValue = ConfigAttribute.restoreHeal;
            float multiplier = attributeValue + configValue;
            float newAmount = originalAmount * multiplier;

            LogUtil.debugAttribute("治疗恢复", entityLivingBase.getName(), attributeValue, configValue, multiplier);
            LogUtil.debugHeal(entityLivingBase.getName(), originalAmount, newAmount,
                    String.format("治疗倍率: %.2fx (属性: %.2f + 配置: %.2f)", multiplier, attributeValue, configValue));

            evt.setAmount(newAmount);
        }
    }
}