// 文件：InjuryEventListener.java
// 路径：src/main/java/pers/roinflam/battlecorrection/event/InjuryEventListener.java
package pers.roinflam.battlecorrection.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 受伤事件监听器
 * 处理PVP伤害调整和无敌时间修改
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class InjuryEventListener {

    public static final UUID ATTACK_COOLDOWN_ID = UUID.fromString("a05141e5-4898-7440-0615-9ac825922a2a");
    public static final String ATTACK_COOLDOWN_NAME = "battlecorrection.attack_cooldown";

    /**
     * 处理攻击事件 - PVP限制
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            if (evt.getEntity() instanceof Player victim) {
                DamageSource damageSource = evt.getSource();
                String attackerName = damageSource.getEntity() != null ?
                        damageSource.getEntity().getName().getString() : "未知";

                // 自伤检测
                if (ConfigBattle.PVP_HURT_ITSELF.get() <= 0 &&
                        evt.getEntity().equals(damageSource.getEntity())) {
                    evt.setCanceled(true);
                    LogUtil.debugEvent("PVP自伤阻止", victim.getName().getString(),
                            String.format("阻止了玩家的自我伤害，配置倍率: %.2f",
                                    ConfigBattle.PVP_HURT_ITSELF.get()));
                    return;
                }

                // PVP检测
                if (ConfigBattle.PVP.get() <= 0 && damageSource.getEntity() instanceof Player) {
                    evt.setCanceled(true);
                    LogUtil.debugEvent("PVP伤害阻止", victim.getName().getString(),
                            String.format("阻止了来自 %s 的PVP伤害，配置倍率: %.2f",
                                    attackerName, ConfigBattle.PVP.get()));
                }
            }
        }
    }

    /**
     * 处理伤害事件 - PVP伤害倍率和无敌时间
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            LivingEntity hurter = evt.getEntity();
            DamageSource damageSource = evt.getSource();
            float originalDamage = evt.getAmount();
            float newDamage = originalDamage;
            String damageModification = "";

            if (hurter instanceof Player) {
                // PVP自伤倍率
                if (hurter.equals(damageSource.getEntity())) {
                    newDamage *= ConfigBattle.PVP_HURT_ITSELF.get().floatValue();
                    damageModification = String.format("PVP自伤倍率: %.2fx",
                            ConfigBattle.PVP_HURT_ITSELF.get());
                }
                // PVP伤害倍率
                else if (damageSource.getEntity() instanceof Player attacker) {
                    newDamage *= ConfigBattle.PVP.get().floatValue();
                    damageModification = String.format("PVP伤害倍率: %.2fx (攻击者: %s)",
                            ConfigBattle.PVP.get(), attacker.getName().getString());
                }

                // 玩家无敌时间调整
                int originalHurtTime = hurter.invulnerableTime / 2;
                hurter.invulnerableTime = (int) (originalHurtTime *
                        (1 + ConfigBattle.HURT_TIME_PLAYER.get().floatValue()));

                if (ConfigBattle.HURT_TIME_PLAYER.get() != 0) {
                    LogUtil.debugEvent("玩家无敌时间调整", hurter.getName().getString(),
                            String.format("原始: %d tick, 调整后: %d tick (倍率: 1 + %.2fx)",
                                    originalHurtTime, hurter.invulnerableTime,
                                    ConfigBattle.HURT_TIME_PLAYER.get()));
                }
            } else {
                // 实体无敌时间调整
                int originalHurtTime = hurter.invulnerableTime / 2;
                hurter.invulnerableTime = (int) (originalHurtTime *
                        (1 + ConfigBattle.HURT_TIME_ENTITY.get().floatValue()));

                if (ConfigBattle.HURT_TIME_ENTITY.get() != 0) {
                    LogUtil.debugEvent("实体无敌时间调整", hurter.getName().getString(),
                            String.format("原始: %d tick, 调整后: %d tick (倍率: 1 + %.2fx)",
                                    originalHurtTime, hurter.invulnerableTime,
                                    ConfigBattle.HURT_TIME_ENTITY.get()));
                }
            }

            if (newDamage != originalDamage) {
                evt.setAmount(newDamage);
                String attackerName = damageSource.getEntity() != null ?
                        damageSource.getEntity().getName().getString() : "未知";
                LogUtil.debugDamage("PVP伤害调整", attackerName, hurter.getName().getString(),
                        originalDamage, newDamage, damageModification);
            }
        }
    }

    /**
     * 处理实体加入世界事件 - 攻击冷却设置
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(@Nonnull EntityJoinLevelEvent evt) {
        if (!evt.getLevel().isClientSide() && evt.getEntity() instanceof Player player) {
            AttributeInstance attributeInstance = player.getAttribute(Attributes.ATTACK_SPEED);

            if (attributeInstance != null) {
                if (!ConfigBattle.ATTACK_COOLDOWN.get()) {
                    // 禁用攻击冷却
                    if (attributeInstance.getModifier(ATTACK_COOLDOWN_ID) == null) {
                        attributeInstance.addPermanentModifier(new AttributeModifier(
                                ATTACK_COOLDOWN_ID,
                                ATTACK_COOLDOWN_NAME,
                                Double.MAX_VALUE / 2,
                                AttributeModifier.Operation.ADDITION
                        ));
                        LogUtil.debugEvent("攻击冷却禁用", player.getName().getString(),
                                "移除了攻击冷却限制");
                    }
                } else {
                    // 启用攻击冷却
                    if (attributeInstance.getModifier(ATTACK_COOLDOWN_ID) != null) {
                        attributeInstance.removeModifier(ATTACK_COOLDOWN_ID);
                        LogUtil.debugEvent("攻击冷却启用", player.getName().getString(),
                                "恢复了攻击冷却机制");
                    }
                }
            }
        }
    }
}