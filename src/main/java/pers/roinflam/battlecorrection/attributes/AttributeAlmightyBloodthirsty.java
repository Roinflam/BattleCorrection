package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;
import pers.roinflam.battlecorrection.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 全能吸血属性
 * 根据造成的任何伤害恢复等量生命值（近战需要完整攻击）
 */
@Mod.EventBusSubscriber
public class AttributeAlmightyBloodthirsty {
    public static final UUID ID = UUID.fromString("f8b6bb16-c5e8-69b7-e994-a8f51ce67be5");
    public static final String NAME = "battlecorrection.almightyBloodthirsty";

    public static final IAttribute ALMIGHTY_BLOODTHIRSTY = (new RangedAttribute(null, NAME, 1, 1, Float.MAX_VALUE)).setDescription("Almighty Bloodthirsty");

    /**
     * 处理伤害事件以触发全能吸血效果
     * 对所有类型的伤害都有效
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getTrueSource() instanceof EntityLivingBase) {
                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
                @Nullable EntityLivingBase victim = evt.getEntityLiving();

                if (attacker.equals(damageSource.getImmediateSource()) && attacker instanceof EntityPlayer) {
                    float ticksSinceLastSwing = EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker);
                    if (ticksSinceLastSwing != 1) {
                        LogUtil.debug(String.format("全能吸血未触发 - 攻击者: %s, 原因: 近战攻击未蓄力完成 (%.2f)",
                                attacker.getName(), ticksSinceLastSwing));
                        return;
                    }
                }

                float damage = evt.getAmount();
                float attributeValue = AttributesUtil.getAttributeValue(attacker, ALMIGHTY_BLOODTHIRSTY);
                float configValue = ConfigAttribute.almightyBloodthirsty;
                float healRatio = (attributeValue - 1) + configValue;
                float healAmount = damage * healRatio;

                if (healAmount > 0) {
                    attacker.heal(healAmount);

                    String damageType = damageSource.getImmediateSource().equals(attacker) ? "近战" : "远程";
                    LogUtil.debugAttribute("全能吸血", attacker.getName(), attributeValue, configValue, healRatio);
                    LogUtil.debugEvent("全能吸血触发", attacker.getName(),
                            String.format("类型: %s, 对 %s 造成 %.2f 伤害，恢复 %.2f 生命值 (比例: %.2f%%)",
                                    damageType, victim.getName(), damage, healAmount, healRatio * 100));
                }
            }
        }
    }
}