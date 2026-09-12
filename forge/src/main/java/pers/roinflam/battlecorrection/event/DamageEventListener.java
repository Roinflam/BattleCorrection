package pers.roinflam.battlecorrection.event;

import net.minecraft.tags.DamageTypeTags;
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
import pers.roinflam.battlecorrection.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 伤害事件监听器
 * 处理连击修正和全局伤害倍率
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class DamageEventListener {

    /**
     * 连击修正的最低倍率：再怎么没蓄力也至少保留一半伤害
     */
    private static final float MIN_COMBO_MODIFIER = 0.5F;

    /**
     * 处理受伤事件 - 连击修正
     * <p>
     * 只对玩家真正的近战挥砍生效，伤害乘以 max(挥砍时的蓄力, 0.5)。
     * 蓄力值取自挥砍发生那一刻（见 {@link EntityLivingUtil#getSwingStrength}）：
     * 原版在调用 hurt() 之前就把蓄力清零了，旧版本在这里直接读蓄力永远是 0，导致每一刀都 ×0.5。
     * 荆棘反伤、模组技能等不经过原版挥砍流程的伤害不做修正。
     * <p>
     * 优先级 NORMAL：排在固定伤害加成（HIGH）之后，和暴击倍率同一层（都是乘法，先后不影响结果）。
     *
     * @param evt 生物受伤事件（护甲计算之前触发）
     */
    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide() || !ConfigBattle.COMBO_CORRECTION.get()) {
            return;
        }
        if (!(evt.getSource().getDirectEntity() instanceof Player attacker)) {
            return;
        }

        float attackStrength = EntityLivingUtil.getSwingStrength(attacker);
        if (attackStrength < 0) {
            // 本刻没有真正的挥砍
            return;
        }

        float modifier = Math.max(attackStrength, MIN_COMBO_MODIFIER);
        if (modifier >= 1.0F) {
            return;
        }

        float originalDamage = evt.getAmount();
        float newDamage = originalDamage * modifier;
        evt.setAmount(newDamage);

        if (LogUtil.isDetailed()) {
            LogUtil.debugDamage("连击修正", attacker.getName().getString(),
                    evt.getEntity().getName().getString(),
                    originalDamage, newDamage,
                    String.format("攻击蓄力: %.2f%%, 伤害倍率: %.2fx", attackStrength * 100, modifier));
        }
    }

    /**
     * 处理伤害事件 - 应用全局伤害倍率和饥饿衰减
     *
     * @param evt 生物伤害事件（护甲计算之后触发）
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide()) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        // 跳过无视无敌的伤害
        if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        @Nullable Entity trueSource = damageSource.getEntity();
        @Nullable Entity immediateSource = damageSource.getDirectEntity();
        if (trueSource == null || immediateSource == null) {
            return;
        }

        float originalDamage = evt.getAmount();
        float finalDamage = originalDamage;

        // 只有开了详细日志才收集原因文本，关闭时不产生任何临时字符串
        @Nullable StringBuilder modificationReason = LogUtil.isDetailed() ? new StringBuilder() : null;

        // 判断伤害类型并应用倍率
        boolean isMagicDamage = isMagicDamage(damageSource);

        if (!isMagicDamage) {
            if (immediateSource instanceof Player) {
                // 玩家近战攻击
                float multiplier = ConfigBattle.PLAYER_MELEE_ATTACK.get().floatValue();
                finalDamage *= multiplier;
                appendReason(modificationReason, "玩家近战倍率", multiplier);
            } else if (!immediateSource.equals(trueSource) && trueSource instanceof Player) {
                // 玩家远程攻击
                if (immediateSource instanceof AbstractArrow) {
                    float multiplier = ConfigBattle.PLAYER_ARROW_ATTACK.get().floatValue();
                    finalDamage *= multiplier;
                    appendReason(modificationReason, "玩家箭矢倍率", multiplier);
                } else if (immediateSource instanceof Projectile) {
                    float multiplier = ConfigBattle.PLAYER_PROJECTILE_ATTACK.get().floatValue();
                    finalDamage *= multiplier;
                    appendReason(modificationReason, "玩家弹射物倍率", multiplier);
                }
            }
        }

        // 玩家魔法攻击
        if ((immediateSource instanceof Player || trueSource instanceof Player) && isMagicDamage) {
            float multiplier = ConfigBattle.PLAYER_MAGIC_ATTACK.get().floatValue();
            finalDamage *= multiplier;
            appendReason(modificationReason, "玩家魔法倍率", multiplier);
        }

        // 饥饿伤害衰减
        if (trueSource instanceof Player player) {
            float hungerDecay = ConfigBattle.HUNGER_DAMAGE_DECAY.get().floatValue();
            if (hungerDecay > 0) {
                int foodLevel = player.getFoodData().getFoodLevel();
                float hungerDecayLimit = ConfigBattle.HUNGER_DAMAGE_DECAY_LIMIT.get().floatValue();
                // 上限为 0 表示不设上限（最多把伤害减到 0），与配置说明一致
                float decayCap = hungerDecayLimit > 0 ? hungerDecayLimit : 1.0F;
                float hungerPenalty = (20 - foodLevel) * hungerDecay;
                float hungerMultiplier = 1 - Math.min(hungerPenalty, decayCap);
                finalDamage *= hungerMultiplier;

                if (modificationReason != null && hungerMultiplier < 1) {
                    modificationReason.append(String.format(", 饥饿衰减: %.2fx (饥饿值: %d)",
                            hungerMultiplier, foodLevel));
                }
            }
        }

        // 玩家承受伤害倍率
        if (evt.getEntity() instanceof Player) {
            if (immediateSource.equals(trueSource) && !isMagicDamage) {
                float multiplier = ConfigBattle.PLAYER_SUFFERS_MELEE.get().floatValue();
                finalDamage *= multiplier;
                appendReason(modificationReason, "玩家承受近战倍率", multiplier);
            } else if (!immediateSource.equals(trueSource) && !isMagicDamage) {
                if (immediateSource instanceof AbstractArrow) {
                    float multiplier = ConfigBattle.PLAYER_SUFFERS_ARROW.get().floatValue();
                    finalDamage *= multiplier;
                    appendReason(modificationReason, "玩家承受箭矢倍率", multiplier);
                } else if (immediateSource instanceof Projectile) {
                    float multiplier = ConfigBattle.PLAYER_SUFFERS_PROJECTILE.get().floatValue();
                    finalDamage *= multiplier;
                    appendReason(modificationReason, "玩家承受弹射物倍率", multiplier);
                }
            }
            if (isMagicDamage) {
                float multiplier = ConfigBattle.PLAYER_SUFFERS_MAGIC.get().floatValue();
                finalDamage *= multiplier;
                appendReason(modificationReason, "玩家承受魔法倍率", multiplier);
            }
        }

        if (finalDamage != originalDamage) {
            evt.setAmount(finalDamage);
            if (modificationReason != null) {
                LogUtil.debugDamage("全局伤害调整",
                        trueSource.getName().getString(),
                        evt.getEntity().getName().getString(),
                        originalDamage, finalDamage,
                        modificationReason.toString());
            }
        }
    }

    /**
     * 往日志原因里追加一条倍率说明（未开启详细日志时 builder 为 null，直接跳过）
     *
     * @param builder    原因文本，未开启详细日志时为 null
     * @param name       倍率名称
     * @param multiplier 倍率数值
     */
    private static void appendReason(@Nullable StringBuilder builder, @Nonnull String name, float multiplier) {
        if (builder == null) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(String.format("%s: %.2fx", name, multiplier));
    }

    /**
     * 判断是否为魔法伤害
     *
     * @param damageSource 伤害源
     * @return true=魔法伤害, false=非魔法伤害
     */
    private static boolean isMagicDamage(@Nonnull DamageSource damageSource) {
        // 1. 检查原版魔法伤害标签（女巫免疫的伤害类型）
        if (damageSource.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            return true;
        }

        // 2. 检查伤害类型ID是否包含 "magic" 字段（兼容模组）
        String damageTypeId = damageSource.getMsgId();
        return damageTypeId.toLowerCase().contains("magic");
    }
}
