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
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 全能吸血属性
 * 根据造成的任何伤害恢复等量生命值（近战需要完整攻击）
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeAlmightyBloodthirsty {
    public static final UUID ID = UUID.fromString("f8b6bb16-c5e8-69b7-e994-a8f51ce67be5");
    public static final String NAME = "battlecorrection.almightyBloodthirsty";

    /**
     * 处理伤害事件以触发全能吸血效果
     * 对所有类型的伤害都有效
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getEntity() instanceof @Nullable LivingEntity attacker) {
                @Nullable LivingEntity victim = evt.getEntity();

                if (attacker.equals(damageSource.getDirectEntity()) && attacker instanceof Player player) {
                    float ticksSinceLastSwing = EntityLivingUtil.getTicksSinceLastSwing(player);
                    if (ticksSinceLastSwing != 1) {
                        LogUtil.debug(String.format("全能吸血未触发 - 攻击者: %s, 原因: 近战攻击未蓄力完成 (%.2f)",
                                attacker.getName().getString(), ticksSinceLastSwing));
                        return;
                    }
                }

                float damage = evt.getAmount();
                float attributeValue = (float) AttributesUtil.getAttributeValue(attacker, ModAttributes.ALMIGHTY_BLOODTHIRSTY.get());
                float configValue = ConfigAttribute.ALMIGHTY_BLOODTHIRSTY.get().floatValue();
                float healRatio = (attributeValue - 1) + configValue;
                float healAmount = damage * healRatio;

                if (healAmount > 0) {
                    attacker.heal(healAmount);

                    String damageType = damageSource.getDirectEntity() != null && damageSource.getDirectEntity().equals(attacker) ? "近战" : "远程";
                    LogUtil.debugAttribute("全能吸血", attacker.getName().getString(), attributeValue, configValue, healRatio);
                    LogUtil.debugEvent("全能吸血触发", attacker.getName().getString(),
                            String.format("类型: %s, 对 %s 造成 %.2f 伤害，恢复 %.2f 生命值 (比例: %.2f%%)",
                                    damageType, victim.getName().getString(), damage, healAmount, healRatio * 100));
                }
            }
        }
    }
}