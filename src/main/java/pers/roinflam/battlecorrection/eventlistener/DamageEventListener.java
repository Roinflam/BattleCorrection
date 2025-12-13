package pers.roinflam.battlecorrection.eventlistener;

import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 伤害事件监听器
 * 处理连击修正和全局伤害倍率
 */
@Mod.EventBusSubscriber
public class DamageEventListener {

    /**
     * 处理受伤事件 - 连击修正
     */
    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote && ConfigBattle.comboCorrection) {
            if (evt.getSource().getImmediateSource() instanceof EntityPlayer) {
                @Nullable EntityPlayer attacker = (EntityPlayer) evt.getSource().getImmediateSource();
                float ticksSinceLastSwing = EntityLivingUtil.getTicksSinceLastSwing(attacker);
                float originalDamage = evt.getAmount();
                float modifier = Math.max(ticksSinceLastSwing, 0.5f);
                float newDamage = originalDamage * modifier;

                evt.setAmount(newDamage);

                LogUtil.debugDamage("连击修正", attacker.getName(), evt.getEntityLiving().getName(),
                        originalDamage, newDamage,
                        String.format("攻击蓄力进度: %.2f%%, 伤害倍率: %.2fx", ticksSinceLastSwing * 100, modifier));
            }
        }
    }

    /**
     * 处理伤害事件 - 应用全局伤害倍率和饥饿衰减
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (!damageSource.canHarmInCreative()) {
                @Nullable Entity trueSource = evt.getSource().getTrueSource();
                @Nullable Entity immediateSource = evt.getSource().getImmediateSource();

                if (trueSource != null && immediateSource != null) {
                    float originalDamage = evt.getAmount();
                    float finalDamage = originalDamage;
                    String modificationReason = "";

                    if (!damageSource.isMagicDamage()) {
                        if (immediateSource instanceof EntityPlayer) {
                            finalDamage *= ConfigBattle.playerMeleeAttack;
                            modificationReason = String.format("玩家近战倍率: %.2fx", ConfigBattle.playerMeleeAttack);
                        } else if (!immediateSource.equals(trueSource) && trueSource instanceof EntityPlayer) {
                            if (immediateSource instanceof EntityArrow) {
                                finalDamage *= ConfigBattle.playerArrowAttack;
                                modificationReason = String.format("玩家箭矢倍率: %.2fx", ConfigBattle.playerArrowAttack);
                            } else if (immediateSource instanceof IProjectile) {
                                finalDamage *= ConfigBattle.playerProjectileAttack;
                                modificationReason = String.format("玩家弹射物倍率: %.2fx", ConfigBattle.playerProjectileAttack);
                            }
                        }
                    }

                    if (immediateSource instanceof EntityPlayer || trueSource instanceof EntityPlayer) {
                        if (damageSource.isMagicDamage()) {
                            finalDamage *= ConfigBattle.playerMagicAttack;
                            modificationReason = String.format("玩家魔法倍率: %.2fx", ConfigBattle.playerMagicAttack);
                        }
                    }

                    if (trueSource instanceof EntityPlayer) {
                        EntityPlayer entityPlayer = (EntityPlayer) trueSource;
                        int foodLevel = entityPlayer.getFoodStats().getFoodLevel();
                        float hungerPenalty = (20 - foodLevel) * ConfigBattle.hungerDamageDecay;
                        float hungerMultiplier = 1 - Math.min(hungerPenalty, ConfigBattle.hungerDamageDecayLimit);
                        finalDamage *= hungerMultiplier;

                        if (hungerMultiplier < 1) {
                            modificationReason += String.format(", 饥饿衰减: %.2fx (饥饿值: %d)", hungerMultiplier, foodLevel);
                        }
                    }

                    if (evt.getEntityLiving() instanceof EntityPlayer) {
                        EntityPlayer hurter = (EntityPlayer) evt.getEntityLiving();
                        if (immediateSource.equals(trueSource) && !damageSource.isMagicDamage()) {
                            finalDamage *= ConfigBattle.playerSuffersMelee;
                            modificationReason += String.format(", 玩家承受近战倍率: %.2fx", ConfigBattle.playerSuffersMelee);
                        } else if (!immediateSource.equals(trueSource) && !damageSource.isMagicDamage()) {
                            if (immediateSource instanceof EntityArrow) {
                                finalDamage *= ConfigBattle.playerSuffersArrow;
                                modificationReason += String.format(", 玩家承受箭矢倍率: %.2fx", ConfigBattle.playerSuffersArrow);
                            } else if (immediateSource instanceof IProjectile) {
                                finalDamage *= ConfigBattle.playerSuffersProjectile;
                                modificationReason += String.format(", 玩家承受弹射物倍率: %.2fx", ConfigBattle.playerSuffersProjectile);
                            }
                        }
                        if (damageSource.isMagicDamage()) {
                            finalDamage *= ConfigBattle.playerSuffersMagic;
                            modificationReason += String.format(", 玩家承受魔法倍率: %.2fx", ConfigBattle.playerSuffersMagic);
                        }
                    }

                    if (finalDamage != originalDamage) {
                        evt.setAmount(finalDamage);
                        String attackerName = trueSource.getName();
                        String victimName = evt.getEntityLiving().getName();
                        LogUtil.debugDamage("全局伤害调整", attackerName, victimName, originalDamage, finalDamage, modificationReason);
                    }
                }
            }
        }
    }
}