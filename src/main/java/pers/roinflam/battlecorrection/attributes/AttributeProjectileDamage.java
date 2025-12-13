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
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 弹射物伤害加成属性
 * 增加除箭矢外的弹射物攻击造成的伤害，如枪械模组中的子弹
 */
@Mod.EventBusSubscriber
public class AttributeProjectileDamage {
    public static final UUID ID = UUID.fromString("ab4edc53-93b4-c83c-c498-0c0be85d458e");
    public static final String NAME = "battlecorrection.projectileDamage";

    public static final IAttribute PROJECTILE_DAMAGE = (new RangedAttribute(null, NAME, 0, 0, Float.MAX_VALUE)).setDescription("Extra Projectile Damage");

    /**
     * 处理弹射物伤害事件
     * 当实体被弹射物（非箭矢、非魔法）击中时触发
     */
    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getImmediateSource() instanceof IProjectile &&
                    !(damageSource.getImmediateSource() instanceof EntityArrow) &&
                    !damageSource.isMagicDamage() &&
                    damageSource.getTrueSource() instanceof EntityLivingBase) {

                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
                @Nullable EntityLivingBase victim = evt.getEntityLiving();

                float originalAmount = evt.getAmount();
                float attributeValue = AttributesUtil.getAttributeValue(attacker, PROJECTILE_DAMAGE);
                float configDamage = ConfigAttribute.projectileDamage;
                float newAmount = AttributesUtil.getAttributeValue(attacker, PROJECTILE_DAMAGE, originalAmount + configDamage);

                String projectileType = damageSource.getImmediateSource().getClass().getSimpleName();
                LogUtil.debugAttribute("弹射物伤害", attacker.getName(), attributeValue, configDamage, newAmount - originalAmount);
                LogUtil.debugDamage("弹射物攻击", attacker.getName(), victim.getName(), originalAmount, newAmount,
                        String.format("弹射物类型: %s, 属性加成: %.2f, 配置加成: %.2f", projectileType, attributeValue, configDamage));

                evt.setAmount(newAmount);
            }
        }
    }
}