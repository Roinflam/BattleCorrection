package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;
import pers.roinflam.battlecorrection.utils.util.MagicDamageClassifier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 魔法伤害加成属性
 * 增加魔法攻击造成的伤害，如药水伤害或模组中的法杖
 */
@Mod.EventBusSubscriber
public class AttributeMagicDamage {
    public static final UUID ID = UUID.fromString("af42b33d-f767-b71d-9ca4-92fc0ce0f04a");
    public static final String NAME = "battlecorrection.magicDamage";

    public static final IAttribute MAGIC_DAMAGE = ModAttributes.MAGIC_DAMAGE;

    /**
     * 处理魔法伤害事件
     * 优先级 HIGH：固定加成统一在暴击倍率（NORMAL）之前结算，保证"先加后乘"
     * <p>
     * 1.12.2 的物品属性按「伤害作底数」结算：底数 = 原伤害 + 配置加成，
     * 加法修饰符直接加，乘法修饰符按伤害放大。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
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
        // 没有攻击者（中毒、环境伤害等）不算"魔法攻击"，不加成
        @Nullable EntityLivingBase attacker = getAttacker(damageSource.getTrueSource(), damageSource.getImmediateSource());
        if (attacker == null) {
            return;
        }

        float configDamage = ConfigAttribute.magicDamage;
        float newAmount = AttributesUtil.getAttributeValue(attacker, MAGIC_DAMAGE, originalAmount + configDamage);
        if (newAmount == originalAmount) {
            return;
        }
        evt.setAmount(newAmount);

        if (LogUtil.isDetailed()) {
            float attributeValue = AttributesUtil.getAttributeValue(attacker, MAGIC_DAMAGE);
            LogUtil.debugAttribute("魔法伤害", attacker.getName(), attributeValue, configDamage, newAmount - originalAmount);
            LogUtil.debugDamage("魔法攻击", attacker.getName(), evt.getEntityLiving().getName(), originalAmount, newAmount,
                    String.format("伤害类型: %s, 属性加成: %.2f, 配置加成: %.2f",
                            damageSource.getDamageType(), attributeValue, configDamage));
        }
    }

    /**
     * 获取真实攻击者
     * 归属实体是生物时直接用它；否则看直接实体是不是有主人的弹射物
     *
     * @param trueSource      真实伤害源
     * @param immediateSource 直接伤害源
     * @return 攻击者实体，如果无法确定则返回null
     */
    @Nullable
    private static EntityLivingBase getAttacker(@Nullable Entity trueSource, @Nullable Entity immediateSource) {
        if (trueSource instanceof EntityLivingBase) {
            return (EntityLivingBase) trueSource;
        }
        Entity owner = null;
        if (immediateSource instanceof EntityThrowable) {
            owner = ((EntityThrowable) immediateSource).getThrower();
        } else if (immediateSource instanceof EntityArrow) {
            owner = ((EntityArrow) immediateSource).shootingEntity;
        } else if (immediateSource instanceof EntityFireball) {
            owner = ((EntityFireball) immediateSource).shootingEntity;
        }
        return owner instanceof EntityLivingBase ? (EntityLivingBase) owner : null;
    }
}