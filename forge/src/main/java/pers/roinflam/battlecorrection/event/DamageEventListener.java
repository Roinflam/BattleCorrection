package pers.roinflam.battlecorrection.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 伤害事件监听器
 * 处理连击修正和全局伤害倍率
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class DamageEventListener {

    /**
     * 处理受伤事件 - 连击修正
     */
    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().level().isClientSide() && ConfigBattle.COMBO_CORRECTION.get()) {
            DamageSource damageSource = evt.getSource();
            Entity immediateSource = damageSource.getDirectEntity();

            if (immediateSource instanceof Player attacker) {
                // 直接获取当前攻击蓄力值
                float attackStrength = attacker.getAttackStrengthScale(0);
                float originalDamage = evt.getAmount();
                float modifier = Math.max(attackStrength, 0.5f);
                float newDamage = originalDamage * modifier;

                evt.setAmount(newDamage);

                LogUtil.debugDamage("连击修正", attacker.getName().getString(),
                        evt.getEntity().getName().getString(),
                        originalDamage, newDamage,
                        String.format("攻击蓄力: %.2f%%, 伤害倍率: %.2fx", attackStrength * 100, modifier));
            }
        }
    }

    /**
     * 处理伤害事件 - 应用全局伤害倍率和饥饿衰减
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            DamageSource damageSource = evt.getSource();

            // 跳过无视无敌的伤害
            if (damageSource.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return;
            }

            @Nullable Entity trueSource = damageSource.getEntity();
            @Nullable Entity immediateSource = damageSource.getDirectEntity();

            if (trueSource != null && immediateSource != null) {
                float originalDamage = evt.getAmount();
                float finalDamage = originalDamage;
                StringBuilder modificationReason = new StringBuilder();

                // 判断伤害类型并应用倍率
                boolean isMagicDamage = isMagicDamage(damageSource);

                if (!isMagicDamage) {
                    if (immediateSource instanceof Player) {
                        // 玩家近战攻击
                        finalDamage *= ConfigBattle.PLAYER_MELEE_ATTACK.get().floatValue();
                        modificationReason.append(String.format("玩家近战倍率: %.2fx",
                                ConfigBattle.PLAYER_MELEE_ATTACK.get()));
                    } else if (!immediateSource.equals(trueSource) && trueSource instanceof Player) {
                        // 玩家远程攻击
                        if (immediateSource instanceof AbstractArrow) {
                            finalDamage *= ConfigBattle.PLAYER_ARROW_ATTACK.get().floatValue();
                            modificationReason.append(String.format("玩家箭矢倍率: %.2fx",
                                    ConfigBattle.PLAYER_ARROW_ATTACK.get()));
                        } else if (immediateSource instanceof Projectile) {
                            finalDamage *= ConfigBattle.PLAYER_PROJECTILE_ATTACK.get().floatValue();
                            modificationReason.append(String.format("玩家弹射物倍率: %.2fx",
                                    ConfigBattle.PLAYER_PROJECTILE_ATTACK.get()));
                        }
                    }
                }

                // 玩家魔法攻击
                if ((immediateSource instanceof Player || trueSource instanceof Player) && isMagicDamage) {
                    finalDamage *= ConfigBattle.PLAYER_MAGIC_ATTACK.get().floatValue();
                    modificationReason.append(String.format("玩家魔法倍率: %.2fx",
                            ConfigBattle.PLAYER_MAGIC_ATTACK.get()));
                }

                // 饥饿伤害衰减
                if (trueSource instanceof Player player) {
                    int foodLevel = player.getFoodData().getFoodLevel();
                    float hungerDecay = ConfigBattle.HUNGER_DAMAGE_DECAY.get().floatValue();
                    float hungerDecayLimit = ConfigBattle.HUNGER_DAMAGE_DECAY_LIMIT.get().floatValue();

                    if (hungerDecay > 0) {
                        float hungerPenalty = (20 - foodLevel) * hungerDecay;
                        float hungerMultiplier = 1 - Math.min(hungerPenalty, hungerDecayLimit);
                        finalDamage *= hungerMultiplier;

                        if (hungerMultiplier < 1) {
                            modificationReason.append(String.format(", 饥饿衰减: %.2fx (饥饿值: %d)",
                                    hungerMultiplier, foodLevel));
                        }
                    }
                }

                // 玩家承受伤害倍率
                if (evt.getEntity() instanceof Player) {
                    if (immediateSource.equals(trueSource) && !isMagicDamage) {
                        finalDamage *= ConfigBattle.PLAYER_SUFFERS_MELEE.get().floatValue();
                        modificationReason.append(String.format(", 玩家承受近战倍率: %.2fx",
                                ConfigBattle.PLAYER_SUFFERS_MELEE.get()));
                    } else if (!immediateSource.equals(trueSource) && !isMagicDamage) {
                        if (immediateSource instanceof AbstractArrow) {
                            finalDamage *= ConfigBattle.PLAYER_SUFFERS_ARROW.get().floatValue();
                            modificationReason.append(String.format(", 玩家承受箭矢倍率: %.2fx",
                                    ConfigBattle.PLAYER_SUFFERS_ARROW.get()));
                        } else if (immediateSource instanceof Projectile) {
                            finalDamage *= ConfigBattle.PLAYER_SUFFERS_PROJECTILE.get().floatValue();
                            modificationReason.append(String.format(", 玩家承受弹射物倍率: %.2fx",
                                    ConfigBattle.PLAYER_SUFFERS_PROJECTILE.get()));
                        }
                    }
                    if (isMagicDamage) {
                        finalDamage *= ConfigBattle.PLAYER_SUFFERS_MAGIC.get().floatValue();
                        modificationReason.append(String.format(", 玩家承受魔法倍率: %.2fx",
                                ConfigBattle.PLAYER_SUFFERS_MAGIC.get()));
                    }
                }

                if (finalDamage != originalDamage) {
                    evt.setAmount(finalDamage);
                    LogUtil.debugDamage("全局伤害调整",
                            trueSource.getName().getString(),
                            evt.getEntity().getName().getString(),
                            originalDamage, finalDamage,
                            modificationReason.toString());
                }
            }
        }
    }

    /**
     * 判断是否为魔法伤害
     *
     * @param damageSource 伤害源
     * @return true=魔法伤害, false=非魔法伤害
     */
    private static boolean isMagicDamage(DamageSource damageSource) {
        // 1. 检查原版魔法伤害标签（女巫免疫的伤害类型）
        if (damageSource.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO)) {
            return true;
        }

        // 2. 检查伤害类型ID是否包含 "magic" 字段（兼容模组）
        String damageTypeId = damageSource.getMsgId();
        return damageTypeId.toLowerCase().contains("magic");
    }
}