package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraftforge.event.entity.living.LivingHealEvent;
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
 * 治疗恢复加成属性
 * 提高自身受到的治疗效果，如自然恢复、瞬间治疗药水等
 */
@Mod.EventBusSubscriber
public class AttributeRestoreHeal {
    public static final UUID ID = UUID.fromString("9fbbfcb8-cd9b-6967-a6e1-21aeb72ad066");
    public static final String NAME = "battlecorrection.restoreHeal";

    public static final IAttribute RESTORE_HEAL = ModAttributes.RESTORE_HEAL;

    /**
     * 正在结算一次不吃治疗加成的吸血回血（只在服务端线程读写）
     */
    private static boolean healingWithoutBonus = false;

    /**
     * 吸血类属性的回血入口
     * <p>
     * 仍然走 {@link EntityLivingBase#heal(float)}，其他模组的治疗事件照常触发；
     * 配置 lifestealAffectedByHealBonus 关闭时，这次治疗不乘本模组的治疗倍率。
     *
     * @param entity 回血的实体
     * @param amount 回血量（伤害 × 吸血比例）
     */
    public static void healFromLifesteal(@Nonnull EntityLivingBase entity, float amount) {
        if (ConfigAttribute.lifestealAffectedByHealBonus) {
            entity.heal(amount);
            return;
        }
        healingWithoutBonus = true;
        try {
            entity.heal(amount);
        } finally {
            healingWithoutBonus = false;
        }
    }

    /**
     * 处理治疗事件
     * 当实体恢复生命值时触发
     */
    @SubscribeEvent
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().world.isRemote || healingWithoutBonus) {
            return;
        }
        EntityLivingBase entityLivingBase = evt.getEntityLiving();
        float originalAmount = evt.getAmount();

        // 计算最终倍率：(装备倍率 - 1.0) + 配置倍率
        // 例：装备1.0 + 配置1.0 = 1.0（100%治疗）；装备1.5 + 配置1.0 = 1.5（150%治疗）
        float equipmentMultiplier = AttributesUtil.getAttributeValue(entityLivingBase, RESTORE_HEAL);
        float configMultiplier = ConfigAttribute.restoreHeal;
        float finalMultiplier = (equipmentMultiplier - 1.0f) + configMultiplier;
        float newAmount = originalAmount * finalMultiplier;
        evt.setAmount(newAmount);

        if (LogUtil.isDetailed()) {
            LogUtil.debugAttribute("治疗恢复", entityLivingBase.getName(),
                    equipmentMultiplier - 1.0f, configMultiplier, finalMultiplier);
            LogUtil.debugHeal(entityLivingBase.getName(), originalAmount, newAmount,
                    String.format("治疗倍率: %.2fx (装备: %.2f + 配置: %.2f)",
                            finalMultiplier, equipmentMultiplier - 1.0f, configMultiplier));
        }
    }
}