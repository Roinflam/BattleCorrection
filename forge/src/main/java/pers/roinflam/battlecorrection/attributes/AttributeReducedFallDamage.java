package pers.roinflam.battlecorrection.attributes;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
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

/**
 * 减少摔落伤害属性
 * 忽略一定数值的摔落伤害，超过部分减少相应伤害
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeReducedFallDamage {

    /**
     * 处理受伤事件以减少摔落伤害
     * 优先级 HIGHEST：减伤最先结算，之后才是各种加成和倍率
     *
     * @param evt 生物受伤事件（护甲计算之前触发）
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide()) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (!damageSource.is(DamageTypeTags.IS_FALL)) {
            return;
        }

        LivingEntity hurter = evt.getEntity();
        float originalDamage = evt.getAmount();

        float equipmentValue = (float) AttributesUtil.getAttributeValue(hurter, ModAttributes.REDUCED_FALL_DAMAGE.get(), 0);
        float configValue = ConfigAttribute.REDUCED_FALL_DAMAGE.get().floatValue();
        float totalValue = equipmentValue + configValue;

        boolean detailed = LogUtil.isDetailed();
        if (detailed) {
            LogUtil.debugAttribute("减少摔落伤害", hurter.getName().getString(), equipmentValue, configValue, totalValue);
        }

        if (originalDamage <= totalValue) {
            evt.setCanceled(true);
            if (detailed) {
                LogUtil.debugEvent("摔落伤害完全免疫", hurter.getName().getString(),
                        String.format("伤害 %.2f 被完全抵消 (装备: %.2f, 配置: %.2f, 总计: %.2f)",
                                originalDamage, equipmentValue, configValue, totalValue));
            }
        } else {
            float newDamage = originalDamage - totalValue;
            evt.setAmount(newDamage);
            if (detailed) {
                String attackerName = damageSource.getEntity() != null
                        ? damageSource.getEntity().getName().getString() : "环境";
                LogUtil.debugDamage("摔落伤害部分减免", attackerName, hurter.getName().getString(),
                        originalDamage, newDamage,
                        String.format("减免了 %.2f 点伤害 (装备: %.2f + 配置: %.2f)",
                                totalValue, equipmentValue, configValue));
            }
        }
    }
}
