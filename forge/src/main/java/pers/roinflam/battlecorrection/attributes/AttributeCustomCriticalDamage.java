package pers.roinflam.battlecorrection.attributes;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import javax.annotation.Nullable;

/**
 * 自定义暴击伤害属性
 * 支持溢出转化机制：
 * - 基础暴击伤害倍率
 * - 溢出暴击率按比例转化为额外暴击伤害
 * - 最终暴击伤害 = 基础倍率 + (溢出值 × 转化比例)
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeCustomCriticalDamage {

    /**
     * 处理受伤事件以应用暴击伤害
     * 优先级 NORMAL：在固定伤害加成（HIGH）之后，先加后乘
     *
     * @param evt 生物受伤事件（护甲计算之前触发）
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide()) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        @Nullable Entity immediateSource = damageSource.getDirectEntity();
        // 只有近战和弹射物会判定暴击；没有直接来源的伤害不读取暴击标记
        if (immediateSource == null || !(damageSource.getEntity() instanceof LivingEntity attacker)) {
            return;
        }

        if (!AttributeCustomCriticalChance.isCriticalHit(immediateSource, attacker)) {
            return;
        }

        double overflow = AttributeCustomCriticalChance.getCriticalOverflow(immediateSource, attacker);

        double attributeValue = AttributesUtil.getAttributeValue(attacker, ModAttributes.CUSTOM_CRITICAL_DAMAGE.get());
        double configValue = ConfigAttribute.CUSTOM_CRITICAL_DAMAGE.get();
        double baseCritDamage = Math.max(1.0, attributeValue + configValue);

        double conversionRatio = ConfigAttribute.CRITICAL_OVERFLOW_CONVERSION.get();
        double overflowBonus = overflow * conversionRatio;
        double finalCritDamage = baseCritDamage + overflowBonus;

        float originalDamage = evt.getAmount();
        float newDamage = (float) (originalDamage * finalCritDamage);
        evt.setAmount(newDamage);

        if (LogUtil.isDetailed()) {
            LogUtil.debugAttribute("暴击伤害倍率", attacker.getName().getString(), attributeValue, configValue, baseCritDamage);
            LogUtil.debugCritical(attacker.getName().getString(), evt.getEntity().getName().getString(),
                    AttributesUtil.getAttributeValue(attacker, ModAttributes.CUSTOM_CRITICAL_CHANCE.get())
                            + ConfigAttribute.CUSTOM_CRITICAL_CHANCE.get(),
                    1, finalCritDamage, originalDamage, newDamage);

            if (overflow > 0) {
                LogUtil.debugEvent("暴击溢出转化", attacker.getName().getString(),
                        String.format("溢出值: %.2f, 转化比例: %.2f, 溢出加成: %.2f, 最终暴击倍率: %.2fx",
                                overflow, conversionRatio, overflowBonus, finalCritDamage));
            }
        }
    }
}
