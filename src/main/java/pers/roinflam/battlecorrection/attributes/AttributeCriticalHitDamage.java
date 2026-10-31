package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 原版暴击伤害加成属性
 * 只影响原版的下坠暴击（从高处落下攻击）
 */
@Mod.EventBusSubscriber
public class AttributeCriticalHitDamage {
    public static final UUID ID = UUID.fromString("d4b4598c-3bf3-6c67-f0d1-419da33d23aa");
    public static final String NAME = "battlecorrection.vanillaCriticalHitDamage";

    public static final IAttribute VANILLA_CRITICAL_HIT_DAMAGE = ModAttributes.VANILLA_CRITICAL_HIT_DAMAGE;

    /**
     * 处理原版暴击事件
     * <p>
     * 在当前倍率（原版 1.5，可能已被其他模组改过）上直接加：1.5 + (装备 - 1) + 配置。
     */
    @SubscribeEvent
    public static void onCriticalHit(@Nonnull CriticalHitEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }
        EntityLivingBase attacker = evt.getEntityLiving();

        // 获取当前的暴击倍率(可能已被其他模组修改)
        float currentModifier = evt.getDamageModifier();
        float attributeValue = AttributesUtil.getAttributeValue(attacker, VANILLA_CRITICAL_HIT_DAMAGE);
        float configValue = ConfigAttribute.vanillaCriticalHitDamage;
        double criticalDamageBonus = (attributeValue - 1.0f) + configValue;

        // 直接加法叠加：原版1.5x + 额外0.5 = 2.0x，而不是乘法 1.5x * 1.5 = 2.25x
        float finalModifier = currentModifier + (float) criticalDamageBonus;
        evt.setDamageModifier(finalModifier);

        if (LogUtil.isDetailed()) {
            LogUtil.debugAttribute("原版暴击伤害", attacker.getName(),
                    attributeValue - 1.0f, configValue, criticalDamageBonus);
            if (evt.getTarget() != null) {
                LogUtil.debugEvent("原版暴击倍率", attacker.getName(),
                        String.format("目标 %s，原始倍率: %.2fx, 额外加成: +%.2f, 最终倍率: %.2fx",
                                evt.getTarget().getName(), currentModifier, criticalDamageBonus, finalModifier));
            }
        }
    }
}