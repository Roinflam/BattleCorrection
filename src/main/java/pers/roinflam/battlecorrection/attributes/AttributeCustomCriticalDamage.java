// 文件：AttributeCustomCriticalDamage.java
// 路径：src/main/java/pers/roinflam/battlecorrection/attributes/AttributeCustomCriticalDamage.java
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
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

@Mod.EventBusSubscriber
public class AttributeCustomCriticalDamage {
    public static final UUID ID = UUID.fromString("8d4fa0b6-4b8d-11ef-9c3a-0242ac120002");
    public static final String NAME = "battlecorrection.customCriticalDamage";

    // 暴击伤害，默认0，通过配置文件和装备加成
    public static final IAttribute CUSTOM_CRITICAL_DAMAGE = (new RangedAttribute(null, NAME, 0, 0, Float.MAX_VALUE)).setDescription("Custom Critical Hit Damage (Multiplier)");

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

        boolean isCritical = AttributeCustomCriticalChance.isCriticalHit(immediateSource);

        if (!isCritical) {
            return;
        }

        // 属性基础值0 + 装备加成 + 配置文件全局加成
        double criticalDamage = AttributesUtil.getAttributeValue(attacker, CUSTOM_CRITICAL_DAMAGE);
        criticalDamage += ConfigAttribute.customCriticalDamage;

        // 确保暴击伤害至少为100%（即不减少伤害）
        criticalDamage = Math.max(1.0, criticalDamage);

        float originalDamage = evt.getAmount();
        float newDamage = (float) (originalDamage * criticalDamage);

        evt.setAmount(newDamage);
    }
}