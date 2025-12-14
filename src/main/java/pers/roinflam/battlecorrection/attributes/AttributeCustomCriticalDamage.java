// 文件：AttributeCustomCriticalDamage.java
// 路径：src/main/java/pers/roinflam/battlecorrection/attributes/AttributeCustomCriticalDamage.java
package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 自定义暴击伤害属性
 * 支持溢出转化机制：
 * - 基础暴击伤害倍率
 * - 溢出暴击率按比例转化为额外暴击伤害
 * - 最终暴击伤害 = 基础倍率 + (溢出值 × 转化比例)
 */
@Mod.EventBusSubscriber
public class AttributeCustomCriticalDamage {
    public static final UUID ID = UUID.fromString("8d4fa0b6-4b8d-11ef-9c3a-0242ac120002");
    public static final String NAME = "battlecorrection.customCriticalDamage";

    public static final IAttribute CUSTOM_CRITICAL_DAMAGE = ModAttributes.CUSTOM_CRITICAL_DAMAGE;

    /**
     * 处理受伤事件以应用暴击伤害
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        Entity immediateSource = damageSource.getImmediateSource();
        Entity trueSource = damageSource.getTrueSource();

        if (trueSource == null || !(trueSource instanceof EntityLivingBase)) {
            return;
        }

        @Nullable EntityLivingBase attacker = (EntityLivingBase) trueSource;
        @Nullable EntityLivingBase victim = evt.getEntityLiving();

        if (!AttributeCustomCriticalChance.isCriticalHit(immediateSource, attacker)) {
            return;
        }

        double overflow = AttributeCustomCriticalChance.getCriticalOverflow(immediateSource, attacker);

        double attributeValue = AttributesUtil.getAttributeValue(attacker, CUSTOM_CRITICAL_DAMAGE);
        double configValue = ConfigAttribute.customCriticalDamage;
        double baseCritDamage = attributeValue + configValue;
        baseCritDamage = Math.max(1.0, baseCritDamage);

        double conversionRatio = ConfigAttribute.criticalOverflowConversion;
        double overflowBonus = overflow * conversionRatio;
        double finalCritDamage = baseCritDamage + overflowBonus;

        float originalDamage = evt.getAmount();
        float newDamage = (float) (originalDamage * finalCritDamage);

        evt.setAmount(newDamage);

        LogUtil.debugAttribute("暴击伤害倍率", attacker.getName(), attributeValue, configValue, baseCritDamage);
        LogUtil.debugCritical(attacker.getName(), victim.getName(),
                AttributesUtil.getAttributeValue(attacker, AttributeCustomCriticalChance.CUSTOM_CRITICAL_CHANCE) + ConfigAttribute.customCriticalChance,
                1, finalCritDamage, originalDamage, newDamage);

        if (overflow > 0) {
            LogUtil.debugEvent("暴击溢出转化", attacker.getName(),
                    String.format("溢出值: %.2f, 转化比例: %.2f, 溢出加成: %.2f, 最终暴击倍率: %.2fx",
                            overflow, conversionRatio, overflowBonus, finalCritDamage));
        }
    }
}