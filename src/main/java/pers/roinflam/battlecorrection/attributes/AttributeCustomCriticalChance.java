package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.random.RandomUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 自定义暴击率属性
 * 支持超额暴击机制：
 * - 暴击率 <= 100%：按概率判定
 * - 暴击率 > 100%：固定暴击，超出部分触发额外暴击层数
 * - 每100%暴击率增加1层暴击（伤害翻倍）
 */
@Mod.EventBusSubscriber
public class AttributeCustomCriticalChance {
    public static final UUID ID = UUID.fromString("7c3e9f2a-4b8d-11ef-9c3a-0242ac120002");
    public static final String NAME = "battlecorrection.customCriticalChance";

    public static final IAttribute CUSTOM_CRITICAL_CHANCE = (new RangedAttribute(null, NAME, 0, 0, Float.MAX_VALUE)).setDescription("Custom Critical Hit Chance (Percentage)");

    private static final String NBT_CRITICAL_CHECKED = "BattleCorrection_CriticalChecked";
    private static final String NBT_IS_CRITICAL = "BattleCorrection_IsCritical";
    private static final String NBT_CRITICAL_LAYERS = "BattleCorrection_CriticalLayers";

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
        boolean isRangedAttack = immediateSource != null && !immediateSource.equals(trueSource) && immediateSource instanceof IProjectile;

        if (!isMeleeAttack && !isRangedAttack) {
            return;
        }

        if (isRangedAttack && immediateSource != null) {
            NBTTagCompound nbt = immediateSource.getEntityData();
            if (nbt.getBoolean(NBT_CRITICAL_CHECKED)) {
                return;
            }
            nbt.setBoolean(NBT_CRITICAL_CHECKED, true);
        }

        double attributeValue = AttributesUtil.getAttributeValue(attacker, CUSTOM_CRITICAL_CHANCE);
        double configValue = ConfigAttribute.customCriticalChance;
        double criticalChance = attributeValue + configValue;
        criticalChance = Math.max(0, criticalChance);

        int criticalLayers = calculateCriticalLayers(criticalChance);

        LogUtil.debugAttribute("暴击率", attacker.getName(), attributeValue, configValue, criticalChance);

        if (criticalLayers > 0) {
            if (isRangedAttack && immediateSource != null) {
                NBTTagCompound nbt = immediateSource.getEntityData();
                nbt.setBoolean(NBT_IS_CRITICAL, true);
                nbt.setInteger(NBT_CRITICAL_LAYERS, criticalLayers);
            } else if (isMeleeAttack) {
                NBTTagCompound nbt = attacker.getEntityData();
                nbt.setBoolean(NBT_IS_CRITICAL, true);
                nbt.setInteger(NBT_CRITICAL_LAYERS, criticalLayers);
            }

            String attackType = isMeleeAttack ? "近战" : "远程";
            LogUtil.debugEvent("暴击判定成功", attacker.getName(),
                    String.format("攻击类型: %s, 暴击率: %.2f%%, 暴击层数: %d", attackType, criticalChance, criticalLayers));
        } else {
            LogUtil.debug(String.format("暴击判定失败 - 攻击者: %s, 暴击率: %.2f%%", attacker.getName(), criticalChance));
        }
    }

    /**
     * 计算暴击层数
     *
     * @param criticalChance 暴击率
     * @return 暴击层数（0表示未暴击）
     */
    private static int calculateCriticalLayers(double criticalChance) {
        if (criticalChance <= 0) {
            return 0;
        }

        int guaranteedLayers = (int) (criticalChance / 100);
        double remainingChance = criticalChance % 100;
        boolean baseCrit = remainingChance > 0 && RandomUtil.percentageChance(remainingChance);

        if (!baseCrit && guaranteedLayers == 0) {
            return 0;
        }

        int totalLayers = guaranteedLayers + (baseCrit ? 1 : 0);
        return totalLayers;
    }

    /**
     * 检查是否触发了暴击
     *
     * @param immediateSource 直接伤害源
     * @param attacker        攻击者（用于近战攻击）
     * @return 是否为暴击
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
     * 获取暴击层数
     *
     * @param immediateSource 直接伤害源
     * @param attacker        攻击者（用于近战攻击）
     * @return 暴击层数（0表示未暴击）
     */
    public static int getCriticalLayers(@Nullable Entity immediateSource, @Nullable EntityLivingBase attacker) {
        if (immediateSource != null && !immediateSource.equals(attacker)) {
            NBTTagCompound nbt = immediateSource.getEntityData();
            return nbt.getInteger(NBT_CRITICAL_LAYERS);
        } else if (attacker != null) {
            NBTTagCompound nbt = attacker.getEntityData();
            int layers = nbt.getInteger(NBT_CRITICAL_LAYERS);
            nbt.removeTag(NBT_IS_CRITICAL);
            nbt.removeTag(NBT_CRITICAL_LAYERS);
            return layers;
        }
        return 0;
    }
}