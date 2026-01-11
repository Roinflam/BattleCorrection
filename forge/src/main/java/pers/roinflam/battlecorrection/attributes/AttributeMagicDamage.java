package pers.roinflam.battlecorrection.attributes;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 魔法伤害加成属性
 * 增加魔法攻击造成的伤害，如药水伤害或模组中的法杖
 * <p>
 * 魔法伤害判定：
 * 1. 原版标签：WITCH_RESISTANT_TO (包含 magic, indirect_magic 等)
 * 2. 伤害类型ID包含 "magic" 字段（兼容模组）
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeMagicDamage {
    public static final UUID ID = UUID.fromString("af42b33d-f767-b71d-9ca4-92fc0ce0f04a");
    public static final String NAME = "battlecorrection.magicDamage";

    /**
     * 处理魔法伤害事件
     * 当实体受到魔法伤害时触发
     */
    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            DamageSource damageSource = evt.getSource();

            // 判断是否为魔法伤害
            if (!isMagicDamage(damageSource)) {
                return;
            }

            @Nullable LivingEntity victim = evt.getEntity();
            @Nullable Entity trueSource = damageSource.getEntity();
            @Nullable Entity immediateSource = damageSource.getDirectEntity();

            // 获取真实攻击者
            LivingEntity attacker = getAttacker(trueSource, immediateSource);

            if (attacker == null) {
                // 无攻击者时仅应用配置加成
                float configDamage = ConfigAttribute.MAGIC_DAMAGE.get().floatValue();
                if (configDamage > 0) {
                    float originalAmount = evt.getAmount();
                    float newAmount = originalAmount + configDamage;
                    evt.setAmount(newAmount);

                    LogUtil.debugDamage("魔法攻击(无攻击者)", "未知",
                            victim.getName().getString(), originalAmount, newAmount,
                            String.format("伤害类型: %s, 仅配置加成: %.2f",
                                    damageSource.getMsgId(), configDamage));
                }
                return;
            }

            // 有攻击者：应用属性加成 + 配置加成
            float originalAmount = evt.getAmount();
            float attributeValue = (float) AttributesUtil.getAttributeValue(attacker, ModAttributes.MAGIC_DAMAGE.get());
            float configDamage = ConfigAttribute.MAGIC_DAMAGE.get().floatValue();
            float totalBonus = attributeValue + configDamage;

            if (totalBonus > 0) {
                float newAmount = originalAmount + totalBonus;
                evt.setAmount(newAmount);

                LogUtil.debugAttribute("魔法伤害", attacker.getName().getString(),
                        attributeValue, configDamage, totalBonus);
                LogUtil.debugDamage("魔法攻击", attacker.getName().getString(),
                        victim.getName().getString(), originalAmount, newAmount,
                        String.format("伤害类型: %s, 直接源: %s, 属性加成: %.2f, 配置加成: %.2f",
                                damageSource.getMsgId(),
                                immediateSource != null ? immediateSource.getType().toString() : "null",
                                attributeValue, configDamage));
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
        if (damageSource.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            return true;
        }

        // 2. 检查伤害类型ID是否包含 "magic" 字段（兼容模组）
        String damageTypeId = damageSource.getMsgId();
        return damageTypeId.toLowerCase().contains("magic");
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
        // 情况1：直接伤害源是生物（玩家直接攻击、生物近战等）
        if (trueSource instanceof LivingEntity) {
            return (LivingEntity) trueSource;
        }

        // 情况2：直接伤害源是抛射物（喷溅药水、模组法术等）
        if (immediateSource instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            if (owner instanceof LivingEntity) {
                return (LivingEntity) owner;
            }
        }

        // 情况3：无法确定攻击者
        return null;
    }
}