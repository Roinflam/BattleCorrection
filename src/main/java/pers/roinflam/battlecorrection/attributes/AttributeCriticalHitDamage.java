// 文件：AttributeCriticalHitDamage.java
// 路径：src/main/java/pers/roinflam/battlecorrection/attributes/AttributeCriticalHitDamage.java
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
     * 原版暴击是指玩家从高处落下时的攻击
     */
    @SubscribeEvent
    public static void onCriticalHit(@Nonnull CriticalHitEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            @Nullable EntityLivingBase attacker = evt.getEntityLiving();

            float attributeValue = AttributesUtil.getAttributeValue(attacker, VANILLA_CRITICAL_HIT_DAMAGE);
            float configValue = ConfigAttribute.vanillaCriticalHitDamage;
            double criticalDamageBonus = attributeValue - 1 + configValue;

            evt.setDamageModifier((float) criticalDamageBonus);

            LogUtil.debugAttribute("原版暴击伤害", attacker.getName(), attributeValue, configValue, criticalDamageBonus);

            if (evt.getTarget() != null) {
                LogUtil.debugEvent("原版暴击触发", attacker.getName(),
                        String.format("对 %s 造成暴击，伤害倍率: %.2fx (原版默认1.5x)",
                                evt.getTarget().getName(), 1 + criticalDamageBonus));
            }
        }
    }
}