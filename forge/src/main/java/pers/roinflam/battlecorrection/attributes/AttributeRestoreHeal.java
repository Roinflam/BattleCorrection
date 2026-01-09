package pers.roinflam.battlecorrection.attributes;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHealEvent;
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
 * 治疗恢复加成属性
 * 提高自身受到的治疗效果，如自然恢复、瞬间治疗药水等
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeRestoreHeal {
    public static final UUID ID = UUID.fromString("9fbbfcb8-cd9b-6967-a6e1-21aeb72ad066");
    public static final String NAME = "battlecorrection.restoreHeal";

    /**
     * 处理治疗事件
     * 当实体恢复生命值时触发
     */
    @SubscribeEvent
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            @Nullable LivingEntity entityLivingBase = evt.getEntity();

            float originalAmount = evt.getAmount();

            float equipmentMultiplier = (float) AttributesUtil.getAttributeValue(entityLivingBase, ModAttributes.RESTORE_HEAL.get());
            float configMultiplier = ConfigAttribute.RESTORE_HEAL.get().floatValue();
            float finalMultiplier = (equipmentMultiplier - 1.0f) + configMultiplier;
            float newAmount = originalAmount * finalMultiplier;

            LogUtil.debugAttribute("治疗恢复", entityLivingBase.getName().getString(),
                    equipmentMultiplier - 1.0f, configMultiplier, finalMultiplier);
            LogUtil.debugHeal(entityLivingBase.getName().getString(), originalAmount, newAmount,
                    String.format("治疗倍率: %.2fx (装备: %.2f + 配置: %.2f)",
                            finalMultiplier, equipmentMultiplier - 1.0f, configMultiplier));

            evt.setAmount(newAmount);
        }
    }
}