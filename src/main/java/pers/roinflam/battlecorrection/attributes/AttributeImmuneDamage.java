package pers.roinflam.battlecorrection.attributes;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;
import pers.roinflam.battlecorrection.utils.random.RandomUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;

/**
 * 免疫伤害属性
 * 按照当前概率免疫攻击，最高100%
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeImmuneDamage {

    /**
     * 处理攻击事件以触发伤害免疫
     * 在伤害计算之前判定是否免疫
     *
     * @param evt 生物攻击事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide()) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        LivingEntity hurter = evt.getEntity();
        float attributeValue = (float) AttributesUtil.getAttributeValue(hurter, ModAttributes.IMMUNE_DAMAGE.get());
        float equipmentChance = (attributeValue - 1) * 100;
        float configChance = ConfigAttribute.IMMUNE_DAMAGE.get().floatValue();
        float totalChance = equipmentChance + configChance;

        // 没有闪避几率就不用掷骰子（绝大多数生物都走这里）
        if (totalChance <= 0) {
            return;
        }

        boolean isImmune = RandomUtil.percentageChance(totalChance);
        boolean detailed = LogUtil.isDetailed();
        if (detailed) {
            LogUtil.debugAttribute("伤害免疫", hurter.getName().getString(),
                    equipmentChance / 100, configChance / 100, totalChance / 100);
        }

        if (isImmune) {
            evt.setCanceled(true);
            if (detailed) {
                String attackerName = damageSource.getEntity() != null
                        ? damageSource.getEntity().getName().getString() : "未知";
                LogUtil.debugEvent("伤害免疫触发", hurter.getName().getString(),
                        String.format("免疫了来自 %s 的攻击，免疫概率: %.2f%% (装备: %.2f%% + 配置: %.2f%%)",
                                attackerName, totalChance, equipmentChance, configChance));
            }
        } else if (detailed) {
            LogUtil.debug(String.format("伤害免疫判定失败 - 受害者: %s, 免疫概率: %.2f%%",
                    hurter.getName().getString(), totalChance));
        }
    }
}
