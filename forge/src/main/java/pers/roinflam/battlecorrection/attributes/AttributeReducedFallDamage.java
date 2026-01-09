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
 * 减少摔落伤害属性
 * 忽略一定数值的摔落伤害，超过部分减少相应伤害
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeReducedFallDamage {
    public static final UUID ID = UUID.fromString("a0054dfc-c4ba-b1df-cce7-9526b5981b9c");
    public static final String NAME = "battlecorrection.reducedFallDamage";

    /**
     * 处理受伤事件以减少摔落伤害
     * 仅对摔落伤害有效
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.is(DamageTypeTags.IS_FALL)) {
                @Nullable LivingEntity hurter = evt.getEntity();

                float originalDamage = evt.getAmount();

                float equipmentReduction = (float) AttributesUtil.getAttributeValue(hurter, ModAttributes.REDUCED_FALL_DAMAGE.get(), 0);
                float configReduction = ConfigAttribute.REDUCED_FALL_DAMAGE.get().floatValue();
                float totalReduction = equipmentReduction + configReduction;

                LogUtil.debugAttribute("减少摔落伤害", hurter.getName().getString(),
                        equipmentReduction, configReduction, totalReduction);

                if (originalDamage <= totalReduction) {
                    evt.setCanceled(true);
                    LogUtil.debugEvent("摔落伤害完全免疫", hurter.getName().getString(),
                            String.format("摔落伤害 %.2f 被完全免疫 (装备减免: %.2f, 配置减免: %.2f, 总计: %.2f)",
                                    originalDamage, equipmentReduction, configReduction, totalReduction));
                } else {
                    float newDamage = originalDamage - totalReduction;
                    evt.setAmount(newDamage);
                    LogUtil.debugDamage("摔落伤害部分减免", "环境", hurter.getName().getString(),
                            originalDamage, newDamage,
                            String.format("减免了 %.2f 点摔落伤害 (装备: %.2f + 配置: %.2f)",
                                    totalReduction, equipmentReduction, configReduction));
                }
            }
        }
    }
}