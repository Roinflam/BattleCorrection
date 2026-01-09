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
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 忽略伤害属性
 * 忽略一定数值的伤害，如果伤害超过该值则减少相应伤害
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeIgnoreDamage {
    public static final UUID ID = UUID.fromString("d444f13a-b3c7-a700-52b7-47677d723207");
    public static final String NAME = "battlecorrection.ignoreDamage";

    /**
     * 处理受伤事件以减少或忽略伤害
     * 在伤害应用之前进行判定
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            DamageSource damageSource = evt.getSource();
            if (!damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                @Nullable LivingEntity hurter = evt.getEntity();

                float originalDamage = evt.getAmount();

                float equipmentIgnore = (float) AttributesUtil.getAttributeValue(hurter, ModAttributes.IGNORE_DAMAGE.get(), 0);
                float configIgnore = ConfigAttribute.IGNORE_DAMAGE.get().floatValue();
                float totalIgnore = equipmentIgnore + configIgnore;

                String attackerName = damageSource.getEntity() != null ?
                        damageSource.getEntity().getName().getString() : "未知";

                LogUtil.debugAttribute("忽略伤害", hurter.getName().getString(),
                        equipmentIgnore, configIgnore, totalIgnore);

                if (originalDamage <= totalIgnore) {
                    evt.setCanceled(true);
                    LogUtil.debugEvent("伤害完全忽略", hurter.getName().getString(),
                            String.format("来自 %s 的攻击，伤害 %.2f 被完全忽略 (装备忽略: %.2f, 配置忽略: %.2f, 总计: %.2f)",
                                    attackerName, originalDamage, equipmentIgnore, configIgnore, totalIgnore));
                } else {
                    float newDamage = originalDamage - totalIgnore;
                    evt.setAmount(newDamage);
                    LogUtil.debugDamage("伤害部分忽略", attackerName, hurter.getName().getString(),
                            originalDamage, newDamage,
                            String.format("忽略了 %.2f 点伤害 (装备: %.2f + 配置: %.2f)",
                                    totalIgnore, equipmentIgnore, configIgnore));
                }
            }
        }
    }
}