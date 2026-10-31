package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 箭矢伤害加成属性
 * 增加弓箭攻击造成的伤害
 */
@Mod.EventBusSubscriber
public class AttributeArrowDamage {
    public static final UUID ID = UUID.fromString("821b6535-b592-6d97-6e89-78d1f76dd7f2");
    public static final String NAME = "battlecorrection.arrowDamage";

    public static final IAttribute ARROW_DAMAGE = ModAttributes.ARROW_DAMAGE;

    /**
     * 处理箭矢伤害事件
     * 优先级 HIGH：固定加成统一在暴击倍率（NORMAL）之前结算，保证"先加后乘"
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }
        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getImmediateSource() instanceof EntityArrow)
                || !(damageSource.getTrueSource() instanceof EntityLivingBase)) {
            return;
        }
        float originalAmount = evt.getAmount();
        // 本来就不造成伤害的命中不加成
        if (originalAmount <= 0) {
            return;
        }
        EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();

        float configDamage = ConfigAttribute.arrowDamage;
        float newAmount = AttributesUtil.getAttributeValue(attacker, ARROW_DAMAGE, originalAmount + configDamage);
        if (newAmount == originalAmount) {
            return;
        }
        evt.setAmount(newAmount);

        if (LogUtil.isDetailed()) {
            float attributeValue = AttributesUtil.getAttributeValue(attacker, ARROW_DAMAGE);
            LogUtil.debugAttribute("箭矢伤害", attacker.getName(), attributeValue, configDamage, newAmount - originalAmount);
            LogUtil.debugDamage("箭矢攻击", attacker.getName(), evt.getEntityLiving().getName(), originalAmount, newAmount,
                    String.format("属性加成: %.2f, 配置加成: %.2f", attributeValue, configDamage));
        }
    }
}