package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.BattleCorrection;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

@Mod.EventBusSubscriber
public class AttributeMagicDamage {
    public static final UUID ID = UUID.fromString("af42b33d-f767-b71d-9ca4-92fc0ce0f04a");
    public static final String NAME = "battlecorrection.magicDamage";

    public static final IAttribute MAGIC_DAMAGE = (new RangedAttribute(null, NAME, 0, 0, Float.MAX_VALUE)).setDescription("Extra Magic Damage");

    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.isMagicDamage() && damageSource.getTrueSource() instanceof EntityLivingBase) {
                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
                float originalAmount = evt.getAmount();
                float configDamage = ConfigAttribute.magicDamage;

                BattleCorrection.LOGGER.info("魔法伤害处理 - 攻击者: " + attacker.getName() +
                        ", 原始伤害: " + originalAmount +
                        ", 配置伤害加成: " + configDamage);

                float newAmount = AttributesUtil.getAttributeValue(attacker, MAGIC_DAMAGE, originalAmount + configDamage);

                BattleCorrection.LOGGER.info("魔法伤害最终计算 - 从 " + originalAmount + " 变为 " + newAmount);
                evt.setAmount(newAmount);
            }
        }
    }
}