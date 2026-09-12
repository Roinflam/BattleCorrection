package pers.roinflam.battlecorrection.attributes;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;

/**
 * 弹射物伤害加成属性
 * 增加除箭矢外的弹射物攻击造成的伤害，如枪械模组中的子弹
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeProjectileDamage {

    /**
     * 处理弹射物伤害事件
     * 当实体被弹射物（非箭矢、非魔法）击中时触发
     * 优先级 HIGH：固定加成统一在暴击倍率（NORMAL）之前结算，保证"先加后乘"
     *
     * @param evt 生物受伤事件（护甲计算之前触发）
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide()) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        Entity directEntity = damageSource.getDirectEntity();
        if (!(directEntity instanceof Projectile)
                || directEntity instanceof AbstractArrow
                || damageSource.is(DamageTypeTags.WITCH_RESISTANT_TO)
                || !(damageSource.getEntity() instanceof LivingEntity attacker)) {
            return;
        }

        float originalAmount = evt.getAmount();
        // 雪球、鸡蛋这类原本不造成伤害的命中不加成，避免它们变成武器
        if (originalAmount <= 0) {
            return;
        }

        float attributeValue = (float) AttributesUtil.getAttributeValue(attacker, ModAttributes.PROJECTILE_DAMAGE.get());
        float configDamage = ConfigAttribute.PROJECTILE_DAMAGE.get().floatValue();
        float totalBonus = attributeValue + configDamage;
        if (totalBonus == 0) {
            return;
        }

        float newAmount = originalAmount + totalBonus;
        evt.setAmount(newAmount);

        if (LogUtil.isDetailed()) {
            String projectileType = directEntity.getClass().getSimpleName();
            LogUtil.debugAttribute("弹射物伤害", attacker.getName().getString(), attributeValue, configDamage, totalBonus);
            LogUtil.debugDamage("弹射物攻击", attacker.getName().getString(), evt.getEntity().getName().getString(),
                    originalAmount, newAmount,
                    String.format("弹射物类型: %s, 属性加成: %.2f, 配置加成: %.2f", projectileType, attributeValue, configDamage));
        }
    }
}
