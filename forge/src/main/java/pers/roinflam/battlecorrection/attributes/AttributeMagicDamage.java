package pers.roinflam.battlecorrection.attributes;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;
import pers.roinflam.battlecorrection.utils.util.MagicDamageClassifier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 魔法伤害加成属性
 * 增加魔法攻击造成的伤害，如药水伤害或模组中的法杖
 * <p>
 * 魔法伤害判定统一交给 {@link MagicDamageClassifier}：
 * 原版规则（WITCH_RESISTANT_TO 标签、伤害类型 message_id 含 "magic"）
 * 加上可配置的第三方规则（forge:is_magic 标签、伤害类型命名空间、白/黑名单），
 * Ars Nouveau / Goety / ISB / SweetMagic 这些 message_id 不带 magic 的法术也能被认出来；
 * 召唤物替主人挥砍的命中一律不算魔法。
 * <p>
 * 只对"有攻击者"的魔法攻击加成：中毒这类没有来源的伤害在原版里也是 magic 类型，
 * 旧版本会给它们加上全局加成，全局值设成 5 时中毒每跳会从 1 变成 6。
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeMagicDamage {

    /**
     * 处理魔法伤害事件
     * 优先级 HIGH：固定加成统一在暴击倍率（NORMAL）之前结算，保证"先加后乘"
     *
     * @param evt 生物受伤事件（护甲计算之前触发）
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide()) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        // 判断是否为魔法伤害（原版规则 + 第三方规则，见 MagicDamageClassifier）
        if (!MagicDamageClassifier.isMagic(damageSource)) {
            return;
        }

        float originalAmount = evt.getAmount();
        // 本来就不造成伤害的命中不加成
        if (originalAmount <= 0) {
            return;
        }

        @Nullable Entity immediateSource = damageSource.getDirectEntity();
        // 获取真实攻击者；没有攻击者（中毒、环境伤害等）不算"魔法攻击"，不加成
        @Nullable LivingEntity attacker = getAttacker(damageSource.getEntity(), immediateSource);
        if (attacker == null) {
            return;
        }

        // 有攻击者：应用属性加成 + 配置加成
        float attributeValue = (float) AttributesUtil.getAttributeValue(attacker, ModAttributes.MAGIC_DAMAGE.get());
        float configDamage = ConfigAttribute.MAGIC_DAMAGE.get().floatValue();
        float totalBonus = attributeValue + configDamage;

        if (totalBonus > 0) {
            float newAmount = originalAmount + totalBonus;
            evt.setAmount(newAmount);

            if (LogUtil.isDetailed()) {
                LogUtil.debugAttribute("魔法伤害", attacker.getName().getString(),
                        attributeValue, configDamage, totalBonus);
                LogUtil.debugDamage("魔法攻击", attacker.getName().getString(),
                        evt.getEntity().getName().getString(), originalAmount, newAmount,
                        String.format("伤害类型: %s, 直接源: %s, 属性加成: %.2f, 配置加成: %.2f",
                                damageSource.getMsgId(),
                                immediateSource != null ? immediateSource.getType().toString() : "null",
                                attributeValue, configDamage));
            }
        }
    }

    /**
     * 获取真实攻击者
     * 处理喷溅药水等抛射物的特殊情况
     *
     * @param trueSource      真实伤害源
     * @param immediateSource 直接伤害源
     * @return 攻击者实体，如果无法确定则返回null
     */
    @Nullable
    private static LivingEntity getAttacker(@Nullable Entity trueSource, @Nullable Entity immediateSource) {
        // 情况1：真实伤害源是生物（玩家直接攻击、生物近战等）
        if (trueSource instanceof LivingEntity livingEntity) {
            return livingEntity;
        }

        // 情况2：直接伤害源是抛射物（喷溅药水、模组法术等）
        if (immediateSource instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            if (owner instanceof LivingEntity livingOwner) {
                return livingOwner;
            }
        }

        // 情况3：无法确定攻击者
        return null;
    }
}
