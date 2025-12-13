package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.projectile.EntityArrow;
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
public class AttributeProjectileDamage {
    public static final UUID ID = UUID.fromString("ab4edc53-93b4-c83c-c498-0c0be85d458e");
    public static final String NAME = "battlecorrection.projectileDamage";

    public static final IAttribute PROJECTILE_DAMAGE = (new RangedAttribute(null, NAME, 0, 0, Float.MAX_VALUE)).setDescription("Extra Projectile Damage");

    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getImmediateSource() instanceof IProjectile &&
                    !(damageSource.getImmediateSource() instanceof EntityArrow) &&
                    !damageSource.isMagicDamage() &&
                    damageSource.getTrueSource() instanceof EntityLivingBase) {

                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
                float originalAmount = evt.getAmount();
                float configDamage = ConfigAttribute.projectileDamage;

                BattleCorrection.LOGGER.info("弹射物伤害处理 - 攻击者: " + attacker.getName() +
                        ", 弹射物类型: " + damageSource.getImmediateSource().getClass().getSimpleName() +
                        ", 原始伤害: " + originalAmount +
                        ", 配置伤害加成: " + configDamage);

                float newAmount = AttributesUtil.getAttributeValue(attacker, PROJECTILE_DAMAGE, originalAmount + configDamage);

                BattleCorrection.LOGGER.info("弹射物伤害最终计算 - 从 " + originalAmount + " 变为 " + newAmount);
                evt.setAmount(newAmount);
            }
        }
    }
}