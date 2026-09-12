package pers.roinflam.battlecorrection.attributes;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;

/**
 * 原版暴击伤害加成属性
 * 只影响原版的下坠暴击(从高处落下攻击)
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeCriticalHitDamage {

    /**
     * 处理原版暴击事件
     * 原版暴击是指玩家从高处落下时的攻击
     *
     * @param evt 暴击事件（每次玩家攻击都会触发，只有真正暴击时修改后的倍率才会生效）
     */
    @SubscribeEvent
    public static void onCriticalHit(@Nonnull CriticalHitEvent evt) {
        if (evt.getEntity().level().isClientSide()) {
            return;
        }

        Player attacker = evt.getEntity();

        // 获取当前的暴击倍率(可能已被其他模组修改)
        float currentModifier = evt.getDamageModifier();

        float attributeValue = (float) AttributesUtil.getAttributeValue(attacker, ModAttributes.VANILLA_CRITICAL_HIT_DAMAGE.get());
        float configValue = ConfigAttribute.VANILLA_CRITICAL_HIT_DAMAGE.get().floatValue();
        double criticalDamageBonus = (attributeValue - 1.0f) + configValue;

        // 直接加法叠加：原版1.5x + 额外0.5 = 2.0x
        // 而不是乘法：1.5x * 1.5 = 2.25x
        float finalModifier = currentModifier + (float) criticalDamageBonus;
        evt.setDamageModifier(finalModifier);

        if (LogUtil.isDetailed()) {
            LogUtil.debugAttribute("原版暴击伤害", attacker.getName().getString(),
                    attributeValue - 1.0f, configValue, criticalDamageBonus);
            if (evt.getTarget() != null) {
                LogUtil.debugEvent("原版暴击倍率", attacker.getName().getString(),
                        String.format("目标 %s，原始倍率: %.2fx, 额外加成: +%.2f, 最终倍率: %.2fx",
                                evt.getTarget().getName().getString(),
                                currentModifier,
                                criticalDamageBonus,
                                finalModifier));
            }
        }
    }
}
