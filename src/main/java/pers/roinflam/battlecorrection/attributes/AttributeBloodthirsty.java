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
 * 吸血属性
 * 根据近战伤害值恢复等量的生命值（需要完整攻击才能触发）
 */
@Mod.EventBusSubscriber
public class AttributeBloodthirsty {
    public static final UUID ID = UUID.fromString("843155e7-bbb9-9577-96cb-dcce692b6e62");
    public static final String NAME = "battlecorrection.bloodthirsty";

    public static final IAttribute BLOODTHIRSTY = (new RangedAttribute(null, NAME, 1, 1, Float.MAX_VALUE)).setDescription("Bloodthirsty");

    /**
     * 处理伤害事件以触发吸血效果
     * 仅对近战攻击有效
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getImmediateSource() instanceof EntityLivingBase) {
                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getImmediateSource();
                @Nullable EntityLivingBase victim = evt.getEntityLiving();

                if (attacker instanceof EntityPlayer) {
                    float ticksSinceLastSwing = EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker);
                    if (ticksSinceLastSwing != 1) {
                        LogUtil.debug(String.format("吸血未触发 - 攻击者: %s, 原因: 攻击未蓄力完成 (%.2f)",
                                attacker.getName(), ticksSinceLastSwing));
                        return;
                    }
                }

                float damage = evt.getAmount();
                float attributeValue = AttributesUtil.getAttributeValue(attacker, BLOODTHIRSTY);
                float configValue = ConfigAttribute.bloodthirsty;
                float healRatio = (attributeValue - 1) + configValue;
                float healAmount = damage * healRatio;

                if (healAmount > 0) {
                    attacker.heal(healAmount);

                    LogUtil.debugAttribute("吸血", attacker.getName(), attributeValue, configValue, healRatio);
                    LogUtil.debugEvent("吸血触发", attacker.getName(),
                            String.format("对 %s 造成 %.2f 伤害，恢复 %.2f 生命值 (比例: %.2f%%)",
                                    victim.getName(), damage, healAmount, healRatio * 100));
                }
            }
        }
    }
}