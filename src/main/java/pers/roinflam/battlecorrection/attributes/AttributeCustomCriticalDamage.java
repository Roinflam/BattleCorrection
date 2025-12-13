package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.Entity;
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
 * 自定义暴击伤害属性
 * 支持超额暴击机制：
 * - 每层暴击使伤害翻倍
 * - 最终伤害 = 原始伤害 × 暴击伤害倍率^暴击层数
 */
@Mod.EventBusSubscriber
public class AttributeCustomCriticalDamage {
    public static final UUID ID = UUID.fromString("8d4fa0b6-4b8d-11ef-9c3a-0242ac120002");
    public static final String NAME = "battlecorrection.customCriticalDamage";

    public static final IAttribute CUSTOM_CRITICAL_DAMAGE = (new RangedAttribute(null, NAME, 0, 0, Float.MAX_VALUE)).setDescription("Custom Critical Hit Damage (Multiplier)");

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

        int criticalLayers = AttributeCustomCriticalChance.getCriticalLayers(immediateSource, attacker);

        if (criticalLayers <= 0) {
            return;
        }

        double attributeValue = AttributesUtil.getAttributeValue(attacker, CUSTOM_CRITICAL_DAMAGE);
        double configValue = ConfigAttribute.customCriticalDamage;
        double baseCriticalDamage = attributeValue + configValue;
        baseCriticalDamage = Math.max(1.0, baseCriticalDamage);

        double finalCriticalDamage = Math.pow(baseCriticalDamage, criticalLayers);

        float originalDamage = evt.getAmount();
        float newDamage = (float) (originalDamage * finalCriticalDamage);

        evt.setAmount(newDamage);

        LogUtil.debugAttribute("暴击伤害倍率", attacker.getName(), attributeValue, configValue, baseCriticalDamage);
        LogUtil.debugCritical(attacker.getName(), victim.getName(),
                AttributesUtil.getAttributeValue(attacker, AttributeCustomCriticalChance.CUSTOM_CRITICAL_CHANCE) + ConfigAttribute.customCriticalChance,
                criticalLayers, finalCriticalDamage, originalDamage, newDamage);
    }
}