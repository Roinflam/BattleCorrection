package pers.roinflam.battlecorrection.attributes;

import net.minecraft.nbt.CompoundTag;
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
import pers.roinflam.battlecorrection.utils.random.RandomUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 自定义暴击率属性
 * 支持溢出转化机制：
 * - 暴击率 <= 1.0（100%）：按概率判定
 * - 暴击率 > 1.0（100%）：必定暴击，溢出部分转化为暴击伤害
 * <p>
 * 近战的判定结果临时记在攻击者身上，由 {@link AttributeCustomCriticalDamage} 读取后立即清除；
 * 每次近战判定前都会先清掉上一次的结果：如果上一次的伤害事件在两次读写之间被别的模组取消，
 * 残留的暴击标记不会再"白送"给下一次攻击。
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeCustomCriticalChance {

    private static final String NBT_CRITICAL_CHECKED = "BattleCorrection_CriticalChecked";
    private static final String NBT_IS_CRITICAL = "BattleCorrection_IsCritical";
    private static final String NBT_CRITICAL_OVERFLOW = "BattleCorrection_CriticalOverflow";

    /**
     * 处理受伤事件以判定暴击
     *
     * @param evt 生物受伤事件（护甲计算之前触发）
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide()) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        @Nullable Entity immediateSource = damageSource.getDirectEntity();
        if (!(damageSource.getEntity() instanceof LivingEntity attacker)) {
            return;
        }

        boolean isMeleeAttack = immediateSource != null && immediateSource.equals(attacker);
        boolean isRangedAttack = immediateSource != null && !immediateSource.equals(attacker)
                && immediateSource instanceof Projectile;

        if (!isMeleeAttack && !isRangedAttack) {
            return;
        }

        if (isRangedAttack) {
            CompoundTag nbt = immediateSource.getPersistentData();
            if (nbt.getBoolean(NBT_CRITICAL_CHECKED)) {
                return;
            }
            nbt.putBoolean(NBT_CRITICAL_CHECKED, true);
        } else {
            // 近战：先清掉上一次可能残留的结果
            clearCritical(attacker.getPersistentData());
        }

        double attributeValue = AttributesUtil.getAttributeValue(attacker, ModAttributes.CUSTOM_CRITICAL_CHANCE.get());
        double configValue = ConfigAttribute.CUSTOM_CRITICAL_CHANCE.get();
        double criticalChance = Math.max(0, attributeValue + configValue);

        // 没有暴击率就不用掷骰子
        if (criticalChance <= 0) {
            return;
        }

        boolean isCritical;
        double overflow = 0;

        if (criticalChance >= 1.0) {
            isCritical = true;
            overflow = criticalChance - 1.0;
        } else {
            isCritical = RandomUtil.percentageChance(criticalChance * 100);
        }

        boolean detailed = LogUtil.isDetailed();
        if (detailed) {
            LogUtil.debugAttribute("暴击率", attacker.getName().getString(), attributeValue, configValue, criticalChance);
        }

        if (isCritical) {
            CompoundTag nbt = isRangedAttack ? immediateSource.getPersistentData() : attacker.getPersistentData();
            nbt.putBoolean(NBT_IS_CRITICAL, true);
            nbt.putDouble(NBT_CRITICAL_OVERFLOW, overflow);

            if (detailed) {
                LogUtil.debugEvent("暴击判定成功", attacker.getName().getString(),
                        String.format("攻击类型: %s, 暴击率: %.2f (%.2f%%), 溢出值: %.2f",
                                isMeleeAttack ? "近战" : "远程", criticalChance, criticalChance * 100, overflow));
            }
        } else if (detailed) {
            LogUtil.debug(String.format("暴击判定失败 - 攻击者: %s, 暴击率: %.2f (%.2f%%)",
                    attacker.getName().getString(), criticalChance, criticalChance * 100));
        }
    }

    /**
     * 检查是否触发了暴击
     *
     * @param immediateSource 直接伤害源（弹射物或攻击者本人）
     * @param attacker        攻击者
     * @return true = 本次攻击暴击
     */
    public static boolean isCriticalHit(@Nullable Entity immediateSource, @Nullable LivingEntity attacker) {
        if (immediateSource != null && !immediateSource.equals(attacker)) {
            return immediateSource.getPersistentData().getBoolean(NBT_IS_CRITICAL);
        } else if (attacker != null) {
            return attacker.getPersistentData().getBoolean(NBT_IS_CRITICAL);
        }
        return false;
    }

    /**
     * 获取暴击溢出值
     * 近战读取后会立即清除攻击者身上的暴击标记；弹射物的标记保留（穿透的箭所有目标共用同一结果）
     *
     * @param immediateSource 直接伤害源（弹射物或攻击者本人）
     * @param attacker        攻击者
     * @return 溢出值（暴击率超过 100% 的部分），没有时为 0
     */
    public static double getCriticalOverflow(@Nullable Entity immediateSource, @Nullable LivingEntity attacker) {
        if (immediateSource != null && !immediateSource.equals(attacker)) {
            return immediateSource.getPersistentData().getDouble(NBT_CRITICAL_OVERFLOW);
        } else if (attacker != null) {
            CompoundTag nbt = attacker.getPersistentData();
            double overflow = nbt.getDouble(NBT_CRITICAL_OVERFLOW);
            clearCritical(nbt);
            return overflow;
        }
        return 0;
    }

    /**
     * 清除暴击标记
     *
     * @param nbt 实体的持久化数据
     */
    private static void clearCritical(@Nonnull CompoundTag nbt) {
        nbt.remove(NBT_IS_CRITICAL);
        nbt.remove(NBT_CRITICAL_OVERFLOW);
    }
}
