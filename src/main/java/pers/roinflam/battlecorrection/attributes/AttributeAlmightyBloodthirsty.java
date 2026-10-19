// 文件：AttributeAlmightyBloodthirsty.java
// 路径：src/main/java/pers/roinflam/battlecorrection/attributes/AttributeAlmightyBloodthirsty.java
package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
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

    public static final IAttribute ALMIGHTY_BLOODTHIRSTY = ModAttributes.ALMIGHTY_BLOODTHIRSTY;

    /**
     * 处理伤害事件以触发全能吸血效果
     * 对所有类型的伤害都有效
     * <p>
     * 优先级 LOWEST：在所有伤害倍率（LOW）之后执行，按最终实际造成的伤害回血
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }
        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getTrueSource() instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
        EntityLivingBase victim = evt.getEntityLiving();
        boolean isMelee = attacker.equals(damageSource.getImmediateSource());

        // 玩家近战需要满蓄力（读挥砍那一刻的蓄力，见 EntityLivingUtil）
        if (isMelee && attacker instanceof EntityPlayer && !EntityLivingUtil.isFullyCharged((EntityPlayer) attacker)) {
            if (LogUtil.isDetailed()) {
                LogUtil.debug(String.format("全能吸血未触发 - 攻击者: %s, 原因: 近战攻击未蓄力完成", attacker.getName()));
            }
            return;
        }

        float damage = evt.getAmount();
        float attributeValue = AttributesUtil.getAttributeValue(attacker, ALMIGHTY_BLOODTHIRSTY);
        float configValue = ConfigAttribute.almightyBloodthirsty;
        float healRatio = (attributeValue - 1) + configValue;
        float healAmount = damage * healRatio;

        if (healAmount > 0) {
            AttributeRestoreHeal.healFromLifesteal(attacker, healAmount);
            if (LogUtil.isDetailed()) {
                LogUtil.debugAttribute("全能吸血", attacker.getName(), attributeValue, configValue, healRatio);
                LogUtil.debugEvent("全能吸血触发", attacker.getName(),
                        String.format("类型: %s, 对 %s 造成 %.2f 伤害，恢复 %.2f 生命值 (比例: %.2f%%)",
                                isMelee ? "近战" : "远程", victim.getName(), damage, healAmount, healRatio * 100));
            }
        }
    }
}