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
import java.util.UUID;

/**
 * 自定义暴击率属性
 * 支持溢出转化机制：
 * - 暴击率 <= 1.0（100%）：按概率判定
 * - 暴击率 > 1.0（100%）：必定暴击，溢出部分转化为暴击伤害
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeCustomCriticalChance {
    public static final UUID ID = UUID.fromString("7c3e9f2a-4b8d-11ef-9c3a-0242ac120002");
    public static final String NAME = "battlecorrection.customCriticalChance";

    private static final String NBT_CRITICAL_CHECKED = "BattleCorrection_CriticalChecked";
    private static final String NBT_IS_CRITICAL = "BattleCorrection_IsCritical";
    private static final String NBT_CRITICAL_OVERFLOW = "BattleCorrection_CriticalOverflow";

    /**
     * 处理受伤事件以判定暴击
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide()) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        Entity immediateSource = damageSource.getDirectEntity();
        Entity trueSource = damageSource.getEntity();

        if (trueSource == null || !(trueSource instanceof @Nullable LivingEntity attacker)) {
            return;
        }

        boolean isMeleeAttack = immediateSource != null && immediateSource.equals(trueSource);
        boolean isRangedAttack = immediateSource != null && !immediateSource.equals(trueSource) && immediateSource instanceof Projectile;

        if (!isMeleeAttack && !isRangedAttack) {
            return;
        }

        if (isRangedAttack && immediateSource != null) {
            CompoundTag nbt = immediateSource.getPersistentData();
            if (nbt.getBoolean(NBT_CRITICAL_CHECKED)) {
                return;
            }
            nbt.putBoolean(NBT_CRITICAL_CHECKED, true);
        }

        double attributeValue = AttributesUtil.getAttributeValue(attacker, ModAttributes.CUSTOM_CRITICAL_CHANCE.get());
        double configValue = ConfigAttribute.CUSTOM_CRITICAL_CHANCE.get();
        double criticalChance = attributeValue + configValue;
        criticalChance = Math.max(0, criticalChance);

        boolean isCritical;
        double overflow = 0;

        if (criticalChance >= 1.0) {
            isCritical = true;
            overflow = criticalChance - 1.0;
        } else {
            isCritical = RandomUtil.percentageChance(criticalChance * 100);
            overflow = 0;
        }

        LogUtil.debugAttribute("暴击率", attacker.getName().getString(), attributeValue, configValue, criticalChance);

        if (isCritical) {
            if (isRangedAttack && immediateSource != null) {
                CompoundTag nbt = immediateSource.getPersistentData();
                nbt.putBoolean(NBT_IS_CRITICAL, true);
                nbt.putDouble(NBT_CRITICAL_OVERFLOW, overflow);
            } else if (isMeleeAttack) {
                CompoundTag nbt = attacker.getPersistentData();
                nbt.putBoolean(NBT_IS_CRITICAL, true);
                nbt.putDouble(NBT_CRITICAL_OVERFLOW, overflow);
            }

            String attackType = isMeleeAttack ? "近战" : "远程";
            LogUtil.debugEvent("暴击判定成功", attacker.getName().getString(),
                    String.format("攻击类型: %s, 暴击率: %.2f (%.2f%%), 溢出值: %.2f",
                            attackType, criticalChance, criticalChance * 100, overflow));
        } else {
            LogUtil.debug(String.format("暴击判定失败 - 攻击者: %s, 暴击率: %.2f (%.2f%%)",
                    attacker.getName().getString(), criticalChance, criticalChance * 100));
        }
    }

    /**
     * 检查是否触发了暴击
     */
    public static boolean isCriticalHit(@Nullable Entity immediateSource, @Nullable LivingEntity attacker) {
        if (immediateSource != null && !immediateSource.equals(attacker)) {
            CompoundTag nbt = immediateSource.getPersistentData();
            return nbt.getBoolean(NBT_IS_CRITICAL);
        } else if (attacker != null) {
            CompoundTag nbt = attacker.getPersistentData();
            return nbt.getBoolean(NBT_IS_CRITICAL);
        }
        return false;
    }

    /**
     * 获取暴击溢出值
     */
    public static double getCriticalOverflow(@Nullable Entity immediateSource, @Nullable LivingEntity attacker) {
        if (immediateSource != null && !immediateSource.equals(attacker)) {
            CompoundTag nbt = immediateSource.getPersistentData();
            return nbt.getDouble(NBT_CRITICAL_OVERFLOW);
        } else if (attacker != null) {
            CompoundTag nbt = attacker.getPersistentData();
            double overflow = nbt.getDouble(NBT_CRITICAL_OVERFLOW);
            nbt.remove(NBT_IS_CRITICAL);
            nbt.remove(NBT_CRITICAL_OVERFLOW);
            return overflow;
        }
        return 0;
    }
}