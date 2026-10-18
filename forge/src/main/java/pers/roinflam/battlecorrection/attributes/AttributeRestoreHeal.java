package pers.roinflam.battlecorrection.attributes;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;

/**
 * 治疗恢复加成属性
 * 提高自身受到的治疗效果，如自然恢复、瞬间治疗药水等
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeRestoreHeal {

    /**
     * 正在结算一次不吃治疗加成的吸血回血（只在服务端线程读写）
     */
    private static boolean healingWithoutBonus = false;

    /**
     * 吸血类属性的回血入口
     * <p>
     * 仍然走 {@link LivingEntity#heal(float)}，其他模组的治疗事件照常触发；
     * 配置 lifestealAffectedByHealBonus 关闭时，这次治疗不乘本模组的治疗倍率。
     *
     * @param entity 回血的实体（服务端）
     * @param amount 吸血算出的回血量
     */
    public static void healFromLifesteal(@Nonnull LivingEntity entity, float amount) {
        if (ConfigAttribute.LIFESTEAL_HEAL_BONUS.get()) {
            entity.heal(amount);
            return;
        }
        healingWithoutBonus = true;
        try {
            entity.heal(amount);
        } finally {
            healingWithoutBonus = false;
        }
    }

    /**
     * 处理治疗事件
     * 当实体恢复生命值时触发
     *
     * @param evt 生物治疗事件
     */
    @SubscribeEvent
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide() || healingWithoutBonus) {
            return;
        }

        LivingEntity entity = evt.getEntity();
        float originalAmount = evt.getAmount();

        float equipmentMultiplier = (float) AttributesUtil.getAttributeValue(entity, ModAttributes.RESTORE_HEAL.get());
        float configMultiplier = ConfigAttribute.RESTORE_HEAL.get().floatValue();
        float finalMultiplier = (equipmentMultiplier - 1.0f) + configMultiplier;
        float newAmount = originalAmount * finalMultiplier;

        evt.setAmount(newAmount);

        if (LogUtil.isDetailed()) {
            LogUtil.debugAttribute("治疗恢复", entity.getName().getString(),
                    equipmentMultiplier - 1.0f, configMultiplier, finalMultiplier);
            LogUtil.debugHeal(entity.getName().getString(), originalAmount, newAmount,
                    String.format("治疗倍率: %.2fx (装备: %.2f + 配置: %.2f)",
                            finalMultiplier, equipmentMultiplier - 1.0f, configMultiplier));
        }
    }
}
