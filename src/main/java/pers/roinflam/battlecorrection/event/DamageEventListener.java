package pers.roinflam.battlecorrection.event;

import net.minecraft.entity.Entity;
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
import pers.roinflam.battlecorrection.utils.Reference;
import pers.roinflam.battlecorrection.utils.util.DamageSourceUtil;
import pers.roinflam.battlecorrection.utils.util.EntityLivingUtil;
import pers.roinflam.battlecorrection.utils.util.MagicDamageClassifier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 伤害事件监听器
 * 处理连击修正和全局伤害倍率
 * <p>
 * 全局倍率分两层：近战/远程（箭矢、弹射物）按伤害的投递方式判定，是基础层，始终生效；
 * 魔法由 {@link MagicDamageClassifier} 判定，是独立层，在基础层之上再乘一次。
 * 例如玩家的火球 = 弹射物倍率 × 魔法倍率，玩家亲手打出的射线法术 = 近战倍率 × 魔法倍率。
 * 旧版本把魔法和近战/远程做成了互斥，法术只吃魔法倍率，现已改为叠乘。
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
     * 旧版本读的是上一刻开始时记下的蓄力，和这一下对不上。
     * 荆棘反伤、模组技能等不经过原版挥砍流程的伤害不做修正。
     * <p>
     * 优先级 NORMAL：排在固定伤害加成（HIGH）之后，和暴击倍率同一层（都是乘法，先后不影响结果）。
     *
     * @param evt 生物受伤事件（护甲计算之前触发）
     */
    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote || !ConfigBattle.comboCorrection) {
            return;
        }
        if (!(evt.getSource().getImmediateSource() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer attacker = (EntityPlayer) evt.getSource().getImmediateSource();

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
            LogUtil.debugDamage("连击修正", attacker.getName(), evt.getEntityLiving().getName(),
                    originalDamage, newDamage,
                    String.format("攻击蓄力: %.2f%%, 伤害倍率: %.2fx", attackStrength * 100, modifier));
        }
    }

    /**
     * 处理伤害事件 - 应用全局伤害倍率和饥饿衰减
     * <p>
     * 倍率结算顺序：基础层（玩家近战/箭矢/弹射物）→ 魔法层（玩家魔法）→ 饥饿衰减
     * → 承受基础层（玩家承受近战/箭矢/弹射物）→ 承受魔法层（玩家承受魔法）。
     * 全是乘法，先后顺序不影响结果。
     *
     * @param evt 生物伤害事件（护甲计算之后触发）
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        // 跳过无视无敌的伤害（虚空、/kill）
        if (DamageSourceUtil.bypassesInvulnerability(damageSource)) {
            return;
        }

        // 玩家承受的环境伤害：背后没有活物攻击者，单独结算，不走下面按攻击者分的倍率
        if (evt.getEntityLiving() instanceof EntityPlayer && DamageSourceUtil.isEnvironmental(damageSource)) {
            applyEnvironmentMultiplier(evt, damageSource);
            return;
        }

        @Nullable Entity trueSource = damageSource.getTrueSource();
        @Nullable Entity immediateSource = damageSource.getImmediateSource();
        if (trueSource == null || immediateSource == null) {
            return;
        }

        float originalDamage = evt.getAmount();
        float finalDamage = originalDamage;

        // 只有开了详细日志才收集原因文本，关闭时不产生任何临时字符串
        @Nullable StringBuilder modificationReason = LogUtil.isDetailed() ? new StringBuilder() : null;

        // 魔法判定（见 MagicDamageClassifier）；魔法是独立层，不影响下面的基础层判定
        boolean isMagicDamage = MagicDamageClassifier.isMagic(damageSource);

        // ===== 基础层：按投递方式判定近战 / 箭矢 / 弹射物，魔法与否都照常生效 =====
        if (immediateSource instanceof EntityPlayer) {
            // 玩家近战攻击（含玩家亲手打出的射线/触碰法术）
            finalDamage *= ConfigBattle.playerMeleeAttack;
            appendReason(modificationReason, "玩家近战倍率", ConfigBattle.playerMeleeAttack);
        } else if (!immediateSource.equals(trueSource) && trueSource instanceof EntityPlayer) {
            // 玩家远程攻击（含法术弹射物）
            if (immediateSource instanceof EntityArrow) {
                finalDamage *= ConfigBattle.playerArrowAttack;
                appendReason(modificationReason, "玩家箭矢倍率", ConfigBattle.playerArrowAttack);
            } else if (DamageSourceUtil.isProjectile(immediateSource)) {
                finalDamage *= ConfigBattle.playerProjectileAttack;
                appendReason(modificationReason, "玩家弹射物倍率", ConfigBattle.playerProjectileAttack);
            }
        }

        // ===== 魔法层：在基础层之上独立叠乘 =====
        if ((immediateSource instanceof EntityPlayer || trueSource instanceof EntityPlayer) && isMagicDamage) {
            finalDamage *= ConfigBattle.playerMagicAttack;
            appendReason(modificationReason, "玩家魔法倍率", ConfigBattle.playerMagicAttack);
        }

        // 饥饿伤害衰减
        if (trueSource instanceof EntityPlayer && ConfigBattle.hungerDamageDecay > 0) {
            int foodLevel = ((EntityPlayer) trueSource).getFoodStats().getFoodLevel();
            // 上限为 0 表示不设上限（最多把伤害减到 0），与配置说明一致
            float decayCap = ConfigBattle.hungerDamageDecayLimit > 0 ? ConfigBattle.hungerDamageDecayLimit : 1.0F;
            float hungerPenalty = (20 - foodLevel) * ConfigBattle.hungerDamageDecay;
            float hungerMultiplier = 1 - Math.min(hungerPenalty, decayCap);
            finalDamage *= hungerMultiplier;

            if (modificationReason != null && hungerMultiplier < 1) {
                modificationReason.append(String.format(", 饥饿衰减: %.2fx (饥饿值: %d)", hungerMultiplier, foodLevel));
            }
        }

        // 玩家承受伤害倍率：同样是基础层（近战 / 箭矢 / 弹射物）始终生效，魔法层独立叠乘
        if (evt.getEntityLiving() instanceof EntityPlayer) {
            if (immediateSource.equals(trueSource)) {
                finalDamage *= ConfigBattle.playerSuffersMelee;
                appendReason(modificationReason, "玩家承受近战倍率", ConfigBattle.playerSuffersMelee);
            } else if (immediateSource instanceof EntityArrow) {
                finalDamage *= ConfigBattle.playerSuffersArrow;
                appendReason(modificationReason, "玩家承受箭矢倍率", ConfigBattle.playerSuffersArrow);
            } else if (DamageSourceUtil.isProjectile(immediateSource)) {
                finalDamage *= ConfigBattle.playerSuffersProjectile;
                appendReason(modificationReason, "玩家承受弹射物倍率", ConfigBattle.playerSuffersProjectile);
            }
            if (isMagicDamage) {
                finalDamage *= ConfigBattle.playerSuffersMagic;
                appendReason(modificationReason, "玩家承受魔法倍率", ConfigBattle.playerSuffersMagic);
            }
        }

        if (finalDamage != originalDamage) {
            evt.setAmount(finalDamage);
            if (modificationReason != null) {
                LogUtil.debugDamage("全局伤害调整", trueSource.getName(), evt.getEntityLiving().getName(),
                        originalDamage, finalDamage, modificationReason.toString());
            }
        }
    }

    /**
     * 玩家承受环境伤害倍率：总倍率 × 命中的类别倍率（火焰、爆炸可能同时命中），
     * 再用「最大生命百分比 / 固定值取较大者」做下限。倍率全是 1 且下限为 0 时不动伤害；
     * 调用方已经排除了无视无敌的伤害（虚空、/kill）。
     * 1.12.2 没有冰冻伤害，1.20.1 的冰冻倍率在这里没有对应项。
     */
    private static void applyEnvironmentMultiplier(@Nonnull LivingDamageEvent evt, @Nonnull DamageSource source) {
        float multiplier = ConfigBattle.playerSuffersEnvironment;
        if (DamageSourceUtil.isFall(source)) {
            multiplier *= ConfigBattle.playerSuffersFall;
        }
        if (source.isFireDamage()) {
            multiplier *= ConfigBattle.playerSuffersFire;
        }
        if (DamageSourceUtil.isDrowning(source)) {
            multiplier *= ConfigBattle.playerSuffersDrowning;
        }
        if (source.isExplosion()) {
            multiplier *= ConfigBattle.playerSuffersExplosion;
        }

        // 下限：最大生命百分比与固定值取较大者，在倍率之后兜底；倍率为 0 表示免疫，不再套下限
        float floor = multiplier > 0
                ? Math.max(evt.getEntityLiving().getMaxHealth() * ConfigBattle.playerEnvironmentMinPercent,
                ConfigBattle.playerEnvironmentMinFlat)
                : 0.0F;

        float originalDamage = evt.getAmount();
        float newDamage = Math.max(originalDamage * multiplier, floor);
        if (newDamage == originalDamage) {
            return;
        }
        evt.setAmount(newDamage);

        if (LogUtil.isDetailed()) {
            LogUtil.debugDamage("环境伤害调整", source.getDamageType(), evt.getEntityLiving().getName(),
                    originalDamage, newDamage,
                    String.format("玩家承受环境伤害倍率: %.2fx, 下限: %.2f", multiplier, floor));
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
}
