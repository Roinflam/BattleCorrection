// 文件：AttributeBloodthirsty.java
// 路径：src/main/java/pers/roinflam/battlecorrection/attributes/AttributeBloodthirsty.java
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
 * 吸血属性
 * 根据近战伤害值恢复等量的生命值（需要完整攻击才能触发）
 */
@Mod.EventBusSubscriber
public class AttributeBloodthirsty {
    public static final UUID ID = UUID.fromString("843155e7-bbb9-9577-96cb-dcce692b6e62");
    public static final String NAME = "battlecorrection.bloodthirsty";

    public static final IAttribute BLOODTHIRSTY = ModAttributes.BLOODTHIRSTY;

    /**
     * 处理伤害事件以触发吸血效果
     * 仅对近战攻击有效
     * <p>
     * 优先级 LOWEST：在所有伤害倍率（LOW）之后执行，按最终实际造成的伤害回血
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }
        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase attacker = (EntityLivingBase) damageSource.getImmediateSource();
        EntityLivingBase victim = evt.getEntityLiving();

        // 玩家需要满蓄力（读挥砍那一刻的蓄力，见 EntityLivingUtil）
        if (attacker instanceof EntityPlayer && !EntityLivingUtil.isFullyCharged((EntityPlayer) attacker)) {
            if (LogUtil.isDetailed()) {
                LogUtil.debug(String.format("吸血未触发 - 攻击者: %s, 原因: 攻击未蓄力完成", attacker.getName()));
            }
            return;
        }

        float damage = evt.getAmount();
        float attributeValue = AttributesUtil.getAttributeValue(attacker, BLOODTHIRSTY);
        float configValue = ConfigAttribute.bloodthirsty;
        float healRatio = (attributeValue - 1) + configValue;
        float healAmount = damage * healRatio;

        if (healAmount > 0) {
            AttributeRestoreHeal.healFromLifesteal(attacker, healAmount);
            if (LogUtil.isDetailed()) {
                LogUtil.debugAttribute("吸血", attacker.getName(), attributeValue, configValue, healRatio);
                LogUtil.debugEvent("吸血触发", attacker.getName(),
                        String.format("对 %s 造成 %.2f 伤害，恢复 %.2f 生命值 (比例: %.2f%%)",
                                victim.getName(), damage, healAmount, healRatio * 100));
            }
        }
    }
}