// 文件：InjuryEventListener.java
// 路径：src/main/java/pers/roinflam/battlecorrection/event/InjuryEventListener.java
package pers.roinflam.battlecorrection.event;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 受伤事件监听器
 * 处理PVP伤害调整和无敌时间修改
 */
@Mod.EventBusSubscriber
public class InjuryEventListener {
    public static final UUID ID = UUID.fromString("a05141e5-4898-7440-0615-9ac825922a2a");
    public static final String NAME = "battlecorrection.attack_cooldown";

    /**
     * 处理攻击事件 - PVP限制
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getEntity() instanceof EntityPlayer) {
                EntityPlayer victim = (EntityPlayer) evt.getEntity();
                String attackerName = evt.getSource().getTrueSource() != null ?
                        evt.getSource().getTrueSource().getName() : "未知";

                if (ConfigBattle.pvpHurtItself <= 0 && evt.getEntity().equals(evt.getSource().getTrueSource())) {
                    evt.setCanceled(true);
                    LogUtil.debugEvent("PVP自伤阻止", victim.getName(),
                            String.format("阻止了玩家的自我伤害，配置倍率: %.2f", ConfigBattle.pvpHurtItself));
                } else if (ConfigBattle.pvp <= 0 && evt.getSource().getTrueSource() instanceof EntityPlayer) {
                    evt.setCanceled(true);
                    LogUtil.debugEvent("PVP伤害阻止", victim.getName(),
                            String.format("阻止了来自 %s 的PVP伤害，配置倍率: %.2f", attackerName, ConfigBattle.pvp));
                }
            }
        }
    }

    /**
     * 处理伤害事件 - PVP伤害倍率和无敌时间
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            float originalDamage = evt.getAmount();
            float newDamage = originalDamage;
            String damageModification = "";

            if (hurter instanceof EntityPlayer) {
                if (hurter.equals(evt.getSource().getTrueSource())) {
                    newDamage *= ConfigBattle.pvpHurtItself;
                    damageModification = String.format("PVP自伤倍率: %.2fx", ConfigBattle.pvpHurtItself);
                } else if (evt.getSource().getTrueSource() instanceof EntityPlayer) {
                    newDamage *= ConfigBattle.pvp;
                    String attackerName = evt.getSource().getTrueSource().getName();
                    damageModification = String.format("PVP伤害倍率: %.2fx (攻击者: %s)", ConfigBattle.pvp, attackerName);
                }

                // 修复：正确的无敌时间计算
                int originalHurtTime = hurter.maxHurtResistantTime / 2;
                hurter.hurtResistantTime = (int) (originalHurtTime * (1 + ConfigBattle.hurtTimePlayer));

                if (ConfigBattle.hurtTimePlayer != 0f) {
                    LogUtil.debugEvent("玩家无敌时间调整", hurter.getName(),
                            String.format("原始: %d tick, 调整后: %d tick (倍率: 1 + %.2fx = %.2fx)",
                                    originalHurtTime, hurter.hurtResistantTime,
                                    ConfigBattle.hurtTimePlayer, 1 + ConfigBattle.hurtTimePlayer));
                }
            } else {
                // 修复：正确的无敌时间计算
                int originalHurtTime = hurter.maxHurtResistantTime / 2;
                hurter.hurtResistantTime = (int) (originalHurtTime * (1 + ConfigBattle.hurtTimeEntity));

                if (ConfigBattle.hurtTimeEntity != 0f) {
                    LogUtil.debugEvent("实体无敌时间调整", hurter.getName(),
                            String.format("原始: %d tick, 调整后: %d tick (倍率: 1 + %.2fx = %.2fx)",
                                    originalHurtTime, hurter.hurtResistantTime,
                                    ConfigBattle.hurtTimeEntity, 1 + ConfigBattle.hurtTimeEntity));
                }
            }

            if (newDamage != originalDamage) {
                evt.setAmount(newDamage);
                String attackerName = evt.getSource().getTrueSource() != null ?
                        evt.getSource().getTrueSource().getName() : "未知";
                LogUtil.debugDamage("PVP伤害调整", attackerName, hurter.getName(),
                        originalDamage, newDamage, damageModification);
            }
        }
    }

    /**
     * 处理实体加入世界事件 - 攻击冷却设置
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(@Nonnull EntityJoinWorldEvent evt) {
        if (!evt.getWorld().isRemote && evt.getEntity() instanceof EntityPlayer) {
            EntityPlayer entityPlayer = (EntityPlayer) evt.getEntity();
            @Nonnull IAttributeInstance attributeInstance = entityPlayer.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);

            if (!ConfigBattle.attackCooldown) {
                if (attributeInstance.getModifier(ID) == null) {
                    attributeInstance.applyModifier(new AttributeModifier(ID, NAME, Double.MAX_VALUE / 2, 0));
                    LogUtil.debugEvent("攻击冷却禁用", entityPlayer.getName(), "移除了攻击冷却限制");
                }
            } else {
                if (attributeInstance.getModifier(ID) != null) {
                    attributeInstance.removeModifier(ID);
                    LogUtil.debugEvent("攻击冷却启用", entityPlayer.getName(), "恢复了攻击冷却机制");
                }
            }
        }
    }
}