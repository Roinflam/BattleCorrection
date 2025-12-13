package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 减少摔落伤害属性
 * 忽略一定数值的摔落伤害，超过部分减少相应伤害
 */
@Mod.EventBusSubscriber
public class AttributeReducedFallDamage {
    public static final UUID ID = UUID.fromString("a0054dfc-c4ba-b1df-cce7-9526b5981b9c");
    public static final String NAME = "battlecorrection.reducedFallDamage";

    public static final IAttribute REDUCED_FALL_DAMAGE = (new RangedAttribute(null, NAME, 0, 0, Float.MAX_VALUE)).setDescription("Reduced Fall Damage");

    /**
     * 处理受伤事件以减少摔落伤害
     * 仅对摔落伤害有效
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.equals(DamageSource.FALL)) {
                @Nullable EntityLivingBase hurter = evt.getEntityLiving();

                float originalDamage = evt.getAmount();
                float attributeValue = AttributesUtil.getAttributeValue(hurter, REDUCED_FALL_DAMAGE);
                float configValue = ConfigAttribute.reducedFallDamage;
                double reducedFallDamage = attributeValue + configValue;

                LogUtil.debugAttribute("减少摔落伤害", hurter.getName(), attributeValue, configValue, reducedFallDamage);

                if (originalDamage <= reducedFallDamage) {
                    evt.setCanceled(true);
                    LogUtil.debugEvent("摔落伤害完全免疫", hurter.getName(),
                            String.format("摔落伤害 %.2f 被完全免疫 (减免值: %.2f)", originalDamage, reducedFallDamage));
                } else {
                    float newDamage = (float) (originalDamage - reducedFallDamage);
                    evt.setAmount(newDamage);
                    LogUtil.debugDamage("摔落伤害部分减免", "环境", hurter.getName(), originalDamage, newDamage,
                            String.format("减免了 %.2f 点摔落伤害", reducedFallDamage));
                }
            }
        }
    }
}