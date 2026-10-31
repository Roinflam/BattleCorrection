package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;
import pers.roinflam.battlecorrection.utils.util.DamageSourceUtil;

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

    public static final IAttribute PROJECTILE_DAMAGE = ModAttributes.PROJECTILE_DAMAGE;

    /**
     * 处理弹射物伤害事件
     * 当实体被弹射物（非箭矢）击中时触发；法术弹射物同样算，魔法加成在 AttributeMagicDamage 里另外叠加
     * 优先级 HIGH：固定加成统一在暴击倍率（NORMAL）之前结算，保证"先加后乘"
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }
        DamageSource damageSource = evt.getSource();
        Entity directEntity = damageSource.getImmediateSource();
        if (!DamageSourceUtil.isProjectile(directEntity)
                || directEntity instanceof EntityArrow
                || !(damageSource.getTrueSource() instanceof EntityLivingBase)) {
            return;
        }
        float originalAmount = evt.getAmount();
        // 雪球、鸡蛋这类原本不造成伤害的命中不加成，避免它们变成武器
        if (originalAmount <= 0) {
            return;
        }
        EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();

        float configDamage = ConfigAttribute.projectileDamage;
        float newAmount = AttributesUtil.getAttributeValue(attacker, PROJECTILE_DAMAGE, originalAmount + configDamage);
        if (newAmount == originalAmount) {
            return;
        }
        evt.setAmount(newAmount);

        if (LogUtil.isDetailed()) {
            float attributeValue = AttributesUtil.getAttributeValue(attacker, PROJECTILE_DAMAGE);
            LogUtil.debugAttribute("弹射物伤害", attacker.getName(), attributeValue, configDamage, newAmount - originalAmount);
            LogUtil.debugDamage("弹射物攻击", attacker.getName(), evt.getEntityLiving().getName(), originalAmount, newAmount,
                    String.format("弹射物类型: %s, 属性加成: %.2f, 配置加成: %.2f",
                            directEntity.getClass().getSimpleName(), attributeValue, configDamage));
        }
    }
}