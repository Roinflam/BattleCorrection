package pers.roinflam.battlecorrection.attributes;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;
import pers.roinflam.battlecorrection.utils.random.RandomUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;
import pers.roinflam.battlecorrection.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 使用速度属性
 * 增加使用物品的速度，如吃食物、喝药水等（不包括弓）
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributePreparationSpeed {
    public static final UUID ID = UUID.fromString("585cc013-a0b6-c8e2-9001-6b55c10caf6d");
    public static final String NAME = "battlecorrection.preparationSpeed";

    /**
     * 处理实体更新事件以加快使用物品速度
     */
    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingTickEvent evt) {
        LivingEntity entityLivingBase = evt.getEntity();
        if (!entityLivingBase.level().isClientSide() && entityLivingBase.isUsingItem()) {
            @Nonnull ItemStack itemStack = entityLivingBase.getUseItem();
            if (!itemStack.isEmpty() &&
                    !(itemStack.getItem() instanceof BowItem) &&
                    !(itemStack.getItem() instanceof CrossbowItem)) {

                float attributeValue = (float) AttributesUtil.getAttributeValue(entityLivingBase, ModAttributes.PREPARATION_SPEED.get());
                float configValue = ConfigAttribute.PREPARATION_SPEED.get().floatValue();
                float speed = attributeValue + configValue;

                if (speed > 1) {
                    int number = (int) (speed - 1);
                    for (int i = 0; i < number; i++) {
                        EntityLivingUtil.updateHeld(entityLivingBase);
                    }
                    if (RandomUtil.percentageChance((speed - 1 - number) * 100)) {
                        EntityLivingUtil.updateHeld(entityLivingBase);
                    }

                    LogUtil.debugAttribute("使用速度加成", entityLivingBase.getName().getString(), attributeValue, configValue, speed);
                    LogUtil.debugEvent("使用物品加速", entityLivingBase.getName().getString(),
                            String.format("物品: %s, 速度倍率: %.2fx", itemStack.getHoverName().getString(), speed));
                }
            }
        }
    }

    /**
     * 处理使用物品事件以减慢使用速度（当速度小于1时）
     */
    @SubscribeEvent
    public static void onLivingEntityUseItem(@Nonnull LivingEntityUseItemEvent.Tick evt) {
        LivingEntity entityLivingBase = evt.getEntity();
        if (!entityLivingBase.level().isClientSide()) {
            @Nonnull ItemStack itemStack = evt.getItem();
            if (!itemStack.isEmpty() &&
                    !(itemStack.getItem() instanceof BowItem) &&
                    !(itemStack.getItem() instanceof CrossbowItem)) {

                float attributeValue = (float) AttributesUtil.getAttributeValue(entityLivingBase, ModAttributes.PREPARATION_SPEED.get());
                float configValue = ConfigAttribute.PREPARATION_SPEED.get().floatValue();
                float speed = attributeValue + configValue;

                if (speed < 1) {
                    if (RandomUtil.percentageChance((1 - speed) * 100)) {
                        evt.setDuration(evt.getDuration() + 1);

                        LogUtil.debugAttribute("使用速度减缓", entityLivingBase.getName().getString(), attributeValue, configValue, speed);
                    }
                }
            }
        }
    }
}