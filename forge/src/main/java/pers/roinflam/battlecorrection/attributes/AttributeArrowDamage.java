package pers.roinflam.battlecorrection.attributes;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
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
 * 箭矢伤害加成属性
 * 增加弓箭攻击造成的伤害（投掷三叉戟在原版里也属于箭矢类）
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeArrowDamage {

    /**
     * 处理箭矢伤害事件
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
        if (!(damageSource.getDirectEntity() instanceof AbstractArrow)
                || !(damageSource.getEntity() instanceof LivingEntity attacker)) {
            return;
        }

        float originalAmount = evt.getAmount();
        // 本来就不造成伤害的命中不加成
        if (originalAmount <= 0) {
            return;
        }

        float attributeValue = (float) AttributesUtil.getAttributeValue(attacker, ModAttributes.ARROW_DAMAGE.get());
        float configDamage = ConfigAttribute.ARROW_DAMAGE.get().floatValue();
        float totalBonus = attributeValue + configDamage;
        if (totalBonus == 0) {
            return;
        }

        float newAmount = originalAmount + totalBonus;
        evt.setAmount(newAmount);

        if (LogUtil.isDetailed()) {
            LogUtil.debugAttribute("箭矢伤害", attacker.getName().getString(), attributeValue, configDamage, totalBonus);
            LogUtil.debugDamage("箭矢攻击", attacker.getName().getString(), evt.getEntity().getName().getString(),
                    originalAmount, newAmount,
                    String.format("属性加成: %.2f, 配置加成: %.2f", attributeValue, configDamage));
        }
    }
}
