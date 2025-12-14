// 文件：AttributeReducedFallDamage.java
// 路径：src/main/java/pers/roinflam/battlecorrection/attributes/AttributeReducedFallDamage.java
package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 减少摔落伤害属性
 * 忽略一定数值的摔落伤害，超过部分减少相应伤害
 */
@Mod.EventBusSubscriber
public class AttributeReducedFallDamage {
    public static final UUID ID = UUID.fromString("a0054dfc-c4ba-b1df-cce7-9526b5981b9c");
    public static final String NAME = "battlecorrection.reducedFallDamage";

    public static final IAttribute REDUCED_FALL_DAMAGE = ModAttributes.REDUCED_FALL_DAMAGE;

    /**
     * 处理受伤事件以减少摔落伤害
     * 仅对摔落伤害有效
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.equals(DamageSource.FALL)) {
                @Nullable EntityLivingBase hurter = evt.getEntityLiving();

                float originalDamage = evt.getAmount();

                // 修复：统一使用三参数版本，避免重复计算
                float equipmentReduction = AttributesUtil.getAttributeValue(hurter, REDUCED_FALL_DAMAGE, 0);
                float configReduction = ConfigAttribute.reducedFallDamage;
                float totalReduction = equipmentReduction + configReduction;

                LogUtil.debugAttribute("减少摔落伤害", hurter.getName(),
                        equipmentReduction, configReduction, totalReduction);

                if (originalDamage <= totalReduction) {
                    evt.setCanceled(true);
                    LogUtil.debugEvent("摔落伤害完全免疫", hurter.getName(),
                            String.format("摔落伤害 %.2f 被完全免疫 (装备减免: %.2f, 配置减免: %.2f, 总计: %.2f)",
                                    originalDamage, equipmentReduction, configReduction, totalReduction));
                } else {
                    float newDamage = originalDamage - totalReduction;
                    evt.setAmount(newDamage);
                    LogUtil.debugDamage("摔落伤害部分减免", "环境", hurter.getName(),
                            originalDamage, newDamage,
                            String.format("减免了 %.2f 点摔落伤害 (装备: %.2f + 配置: %.2f)",
                                    totalReduction, equipmentReduction, configReduction));
                }
            }
        }
    }
}