package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.random.RandomUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;
import pers.roinflam.battlecorrection.utils.util.DamageSourceUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 自定义暴击率属性
 * 支持溢出转化机制：
 * - 暴击率 <= 1.0（100%）：按概率判定
 * - 暴击率 > 1.0（100%）：必定暴击，溢出部分转化为暴击伤害
 */
@Mod.EventBusSubscriber
public class AttributeCustomCriticalChance {
    public static final UUID ID = UUID.fromString("7c3e9f2a-4b8d-11ef-9c3a-0242ac120002");
    public static final String NAME = "battlecorrection.customCriticalChance";

    public static final IAttribute CUSTOM_CRITICAL_CHANCE = ModAttributes.CUSTOM_CRITICAL_CHANCE;

    private static final String NBT_CRITICAL_CHECKED = "BattleCorrection_CriticalChecked";
    private static final String NBT_IS_CRITICAL = "BattleCorrection_IsCritical";
    private static final String NBT_CRITICAL_OVERFLOW = "BattleCorrection_CriticalOverflow";

    /**
     * 处理受伤事件以判定暴击
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        Entity immediateSource = damageSource.getImmediateSource();
        Entity trueSource = damageSource.getTrueSource();

        if (trueSource == null || !(trueSource instanceof EntityLivingBase)) {
            return;
        }

        @Nullable EntityLivingBase attacker = (EntityLivingBase) trueSource;

        boolean isMeleeAttack = immediateSource != null && immediateSource.equals(trueSource);
        boolean isRangedAttack = immediateSource != null && !immediateSource.equals(trueSource)
                && DamageSourceUtil.isProjectile(immediateSource);

        if (!isMeleeAttack && !isRangedAttack) {
            return;
        }

        if (isRangedAttack && immediateSource != null) {
            NBTTagCompound nbt = immediateSource.getEntityData();
            if (nbt.getBoolean(NBT_CRITICAL_CHECKED)) {
                return;
            }
            nbt.setBoolean(NBT_CRITICAL_CHECKED, true);
        } else {
            // 近战：先清掉上一次可能残留的结果（上一下暴击了但伤害被取消时，标记会留到下一刀）
            clearCritical(attacker.getEntityData());
        }

        double attributeValue = AttributesUtil.getAttributeValue(attacker, CUSTOM_CRITICAL_CHANCE);
        double configValue = ConfigAttribute.customCriticalChance;
        double criticalChance = attributeValue + configValue;
        criticalChance = Math.max(0, criticalChance);

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
            overflow = 0;
        }

        boolean detailed = LogUtil.isDetailed();
        if (detailed) {
            LogUtil.debugAttribute("暴击率", attacker.getName(), attributeValue, configValue, criticalChance);
        }

        if (isCritical) {
            if (isRangedAttack && immediateSource != null) {
                NBTTagCompound nbt = immediateSource.getEntityData();
                nbt.setBoolean(NBT_IS_CRITICAL, true);
                nbt.setDouble(NBT_CRITICAL_OVERFLOW, overflow);
            } else if (isMeleeAttack) {
                NBTTagCompound nbt = attacker.getEntityData();
                nbt.setBoolean(NBT_IS_CRITICAL, true);
                nbt.setDouble(NBT_CRITICAL_OVERFLOW, overflow);
            }

            if (detailed) {
                LogUtil.debugEvent("暴击判定成功", attacker.getName(),
                        String.format("攻击类型: %s, 暴击率: %.2f (%.2f%%), 溢出值: %.2f",
                                isMeleeAttack ? "近战" : "远程", criticalChance, criticalChance * 100, overflow));
            }
        } else if (detailed) {
            LogUtil.debug(String.format("暴击判定失败 - 攻击者: %s, 暴击率: %.2f (%.2f%%)",
                    attacker.getName(), criticalChance, criticalChance * 100));
        }
    }

    /**
     * 检查是否触发了暴击
     */
    public static boolean isCriticalHit(@Nullable Entity immediateSource, @Nullable EntityLivingBase attacker) {
        if (immediateSource != null && !immediateSource.equals(attacker)) {
            NBTTagCompound nbt = immediateSource.getEntityData();
            return nbt.getBoolean(NBT_IS_CRITICAL);
        } else if (attacker != null) {
            NBTTagCompound nbt = attacker.getEntityData();
            return nbt.getBoolean(NBT_IS_CRITICAL);
        }
        return false;
    }

    /**
     * 获取暴击溢出值
     */
    public static double getCriticalOverflow(@Nullable Entity immediateSource, @Nullable EntityLivingBase attacker) {
        if (immediateSource != null && !immediateSource.equals(attacker)) {
            NBTTagCompound nbt = immediateSource.getEntityData();
            return nbt.getDouble(NBT_CRITICAL_OVERFLOW);
        } else if (attacker != null) {
            NBTTagCompound nbt = attacker.getEntityData();
            double overflow = nbt.getDouble(NBT_CRITICAL_OVERFLOW);
            clearCritical(nbt);
            return overflow;
        }
        return 0;
    }

    /**
     * 清除暴击标记
     *
     * @param nbt 攻击者的实体数据
     */
    private static void clearCritical(@Nonnull NBTTagCompound nbt) {
        nbt.removeTag(NBT_IS_CRITICAL);
        nbt.removeTag(NBT_CRITICAL_OVERFLOW);
    }
}