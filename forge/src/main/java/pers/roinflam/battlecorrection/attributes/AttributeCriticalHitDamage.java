package pers.roinflam.battlecorrection.attributes;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 原版暴击伤害加成属性
 * 只影响原版的下坠暴击（从高处落下攻击）
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeCriticalHitDamage {
    public static final UUID ID = UUID.fromString("d4b4598c-3bf3-6c67-f0d1-419da33d23aa");
    public static final String NAME = "battlecorrection.vanillaCriticalHitDamage";

    /**
     * 处理原版暴击事件
     * 原版暴击是指玩家从高处落下时的攻击
     */
    @SubscribeEvent
    public static void onCriticalHit(@Nonnull CriticalHitEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            @Nullable LivingEntity attacker = evt.getEntity();

            float attributeValue = (float) AttributesUtil.getAttributeValue(attacker, ModAttributes.VANILLA_CRITICAL_HIT_DAMAGE.get());
            float configValue = ConfigAttribute.VANILLA_CRITICAL_HIT_DAMAGE.get().floatValue();
            double criticalDamageBonus = (attributeValue - 1.0f) + configValue;

            evt.setDamageModifier((float) (1.0 + criticalDamageBonus));

            LogUtil.debugAttribute("原版暴击伤害", attacker.getName().getString(),
                    attributeValue - 1.0f, configValue, criticalDamageBonus);

            if (evt.getTarget() != null) {
                LogUtil.debugEvent("原版暴击触发", attacker.getName().getString(),
                        String.format("对 %s 造成暴击，伤害倍率: %.2fx (100%% + %.2f%% = %.2f%%)",
                                evt.getTarget().getName().getString(),
                                (1 + criticalDamageBonus),
                                criticalDamageBonus * 100,
                                (1 + criticalDamageBonus) * 100));
            }
        }
    }
}