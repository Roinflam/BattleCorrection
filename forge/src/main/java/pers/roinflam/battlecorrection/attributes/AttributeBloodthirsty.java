package pers.roinflam.battlecorrection.attributes;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;
import pers.roinflam.battlecorrection.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 吸血属性
 * 根据近战伤害值恢复等量的生命值（需要完整攻击才能触发）
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeBloodthirsty {

    /**
     * 处理伤害事件以触发吸血效果
     * 仅对近战攻击有效
     * <p>
     * 优先级 LOWEST：在所有伤害倍率（LOW）之后执行，按最终实际造成的伤害回血
     *
     * @param evt 生物伤害事件（护甲计算之后触发）
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide()) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getDirectEntity() instanceof LivingEntity attacker)) {
            return;
        }
        LivingEntity victim = evt.getEntity();

        // 玩家需要满蓄力（读挥砍那一刻的蓄力，见 EntityLivingUtil）
        if (attacker instanceof Player player && !EntityLivingUtil.isFullyCharged(player)) {
            if (LogUtil.isDetailed()) {
                LogUtil.debug(String.format("吸血未触发 - 攻击者: %s, 原因: 攻击未蓄力完成",
                        attacker.getName().getString()));
            }
            return;
        }

        float damage = evt.getAmount();
        float attributeValue = (float) AttributesUtil.getAttributeValue(attacker, ModAttributes.BLOODTHIRSTY.get());
        float configValue = ConfigAttribute.BLOODTHIRSTY.get().floatValue();
        float healRatio = (attributeValue - 1) + configValue;
        float healAmount = damage * healRatio;

        if (healAmount > 0) {
            attacker.heal(healAmount);

            if (LogUtil.isDetailed()) {
                LogUtil.debugAttribute("吸血", attacker.getName().getString(), attributeValue, configValue, healRatio);
                LogUtil.debugEvent("吸血触发", attacker.getName().getString(),
                        String.format("对 %s 造成 %.2f 伤害，恢复 %.2f 生命值 (比例: %.2f%%)",
                                victim.getName().getString(), damage, healAmount, healRatio * 100));
            }
        }
    }
}
