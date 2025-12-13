package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.random.RandomUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 免疫伤害属性
 * 按照当前概率免疫攻击，最高100%
 */
@Mod.EventBusSubscriber
public class AttributeImmuneDamage {
    public static final UUID ID = UUID.fromString("1d309c38-5240-d9a4-bd56-bc4aed05e140");
    public static final String NAME = "battlecorrection.immuneDamage";

    public static final IAttribute IMMUNE_DAMAGE = (new RangedAttribute(null, NAME, 1, 1, Float.MAX_VALUE)).setDescription("Extra Immune Damage");

    /**
     * 处理攻击事件以触发伤害免疫
     * 在伤害计算之前判定是否免疫
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (!damageSource.canHarmInCreative()) {
                @Nullable EntityLivingBase hurter = evt.getEntityLiving();

                float attributeValue = AttributesUtil.getAttributeValue(hurter, IMMUNE_DAMAGE);
                float configValue = ConfigAttribute.immuneDamage;
                float immuneChance = (attributeValue - 1 + configValue) * 100;

                boolean isImmune = RandomUtil.percentageChance(immuneChance);

                LogUtil.debugAttribute("伤害免疫", hurter.getName(), attributeValue, configValue, immuneChance / 100);

                if (isImmune) {
                    evt.setCanceled(true);
                    String attackerName = damageSource.getTrueSource() != null ?
                            damageSource.getTrueSource().getName() : "未知";
                    LogUtil.debugEvent("伤害免疫触发", hurter.getName(),
                            String.format("免疫了来自 %s 的攻击，免疫概率: %.2f%%", attackerName, immuneChance));
                } else {
                    LogUtil.debug(String.format("伤害免疫判定失败 - 受害者: %s, 免疫概率: %.2f%%",
                            hurter.getName(), immuneChance));
                }
            }
        }
    }
}