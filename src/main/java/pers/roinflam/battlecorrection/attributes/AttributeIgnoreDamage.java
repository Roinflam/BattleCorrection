package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 忽略伤害属性
 * 忽略一定数值的伤害，如果伤害超过该值则减少相应伤害
 */
@Mod.EventBusSubscriber
public class AttributeIgnoreDamage {
    public static final UUID ID = UUID.fromString("d444f13a-b3c7-a700-52b7-47677d723207");
    public static final String NAME = "battlecorrection.ignoreDamage";

    public static final IAttribute IGNORE_DAMAGE = (new RangedAttribute(null, NAME, 0, 0, Float.MAX_VALUE)).setDescription("Ignore Damage");

    /**
     * 处理受伤事件以减少或忽略伤害
     * 在伤害应用之前进行判定
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (!damageSource.canHarmInCreative()) {
                @Nullable EntityLivingBase hurter = evt.getEntityLiving();

                float originalDamage = evt.getAmount();
                float attributeValue = AttributesUtil.getAttributeValue(hurter, IGNORE_DAMAGE);
                float configValue = ConfigAttribute.ignoreDamage;
                double ignore = attributeValue + configValue;

                LogUtil.debugAttribute("忽略伤害", hurter.getName(), attributeValue, configValue, ignore);

                String attackerName = damageSource.getTrueSource() != null ?
                        damageSource.getTrueSource().getName() : "未知";

                if (originalDamage <= ignore) {
                    evt.setCanceled(true);
                    LogUtil.debugEvent("伤害完全忽略", hurter.getName(),
                            String.format("来自 %s 的攻击，伤害 %.2f 被完全忽略 (忽略值: %.2f)",
                                    attackerName, originalDamage, ignore));
                } else {
                    float newDamage = (float) (originalDamage - ignore);
                    evt.setAmount(newDamage);
                    LogUtil.debugDamage("伤害部分忽略", attackerName, hurter.getName(), originalDamage, newDamage,
                            String.format("忽略了 %.2f 点伤害", ignore));
                }
            }
        }
    }
}