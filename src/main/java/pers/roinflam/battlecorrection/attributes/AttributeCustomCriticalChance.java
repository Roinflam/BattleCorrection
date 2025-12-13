// 文件：AttributeCustomCriticalChance.java
// 路径：src/main/java/pers/roinflam/battlecorrection/attributes/AttributeCustomCriticalChance.java
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
import pers.roinflam.battlecorrection.utils.java.random.RandomUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

@Mod.EventBusSubscriber
public class AttributeCustomCriticalChance {
    public static final UUID ID = UUID.fromString("7c3e9f2a-4b8d-11ef-9c3a-0242ac120002");
    public static final String NAME = "battlecorrection.customCriticalChance";

    // 暴击率，默认0，通过配置文件和装备加成
    public static final IAttribute CUSTOM_CRITICAL_CHANCE = (new RangedAttribute(null, NAME, 0, 0, 100)).setDescription("Custom Critical Hit Chance (Percentage)");

    private static final String NBT_CRITICAL_CHECKED = "BattleCorrection_CriticalChecked";
    private static final String NBT_IS_CRITICAL = "BattleCorrection_IsCritical";

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

        // 属性基础值0 + 装备加成 + 配置文件全局加成
        double criticalChance = AttributesUtil.getAttributeValue(attacker, CUSTOM_CRITICAL_CHANCE);
        criticalChance += ConfigAttribute.customCriticalChance;
        criticalChance = Math.max(0, Math.min(100, criticalChance));

        boolean isCritical = RandomUtil.percentageChance(criticalChance);

        if (isCritical && isRangedAttack && immediateSource != null) {
            NBTTagCompound nbt = immediateSource.getEntityData();
            nbt.setBoolean(NBT_IS_CRITICAL, true);
        }
    }

    public static boolean isCriticalHit(@Nullable Entity immediateSource) {
        if (immediateSource == null) {
            return false;
        }
        NBTTagCompound nbt = immediateSource.getEntityData();
        return nbt.getBoolean(NBT_IS_CRITICAL);
    }
}