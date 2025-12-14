// 文件：AttributePreparationSpeed.java
// 路径：src/main/java/pers/roinflam/battlecorrection/attributes/AttributePreparationSpeed.java
package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.random.RandomUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;
import pers.roinflam.battlecorrection.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 使用速度属性
 * 增加使用物品的速度，如吃食物、喝药水等（不包括弓）
 */
@Mod.EventBusSubscriber
public class AttributePreparationSpeed {
    public static final UUID ID = UUID.fromString("585cc013-a0b6-c8e2-9001-6b55c10caf6d");
    public static final String NAME = "battlecorrection.preparationSpeed";

    public static final IAttribute PREPARATION_SPEED = ModAttributes.PREPARATION_SPEED;

    /**
     * 处理实体更新事件以加快使用物品速度
     */
    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        EntityLivingBase entityLivingBase = evt.getEntityLiving();
        if (entityLivingBase.isHandActive()) {
            @Nonnull ItemStack itemStack = entityLivingBase.getHeldItem(entityLivingBase.getActiveHand());
            if (!itemStack.isEmpty() && !(itemStack.getItem() instanceof ItemBow)) {
                float attributeValue = AttributesUtil.getAttributeValue(entityLivingBase, PREPARATION_SPEED);
                float configValue = ConfigAttribute.preparationSpeed;
                float speed = attributeValue + configValue;

                if (speed > 1) {
                    int number = (int) (speed - 1);
                    for (int i = 0; i < number; i++) {
                        EntityLivingUtil.updateHeld(entityLivingBase);
                    }
                    if (RandomUtil.percentageChance((speed - 1 - number) * 100)) {
                        EntityLivingUtil.updateHeld(entityLivingBase);
                    }

                    LogUtil.debugAttribute("使用速度加成", entityLivingBase.getName(), attributeValue, configValue, speed);
                    LogUtil.debugEvent("使用物品加速", entityLivingBase.getName(),
                            String.format("物品: %s, 速度倍率: %.2fx", itemStack.getDisplayName(), speed));
                }
            }
        }
    }

    /**
     * 处理使用物品事件以减慢使用速度（当速度小于1时）
     */
    @SubscribeEvent
    public static void onLivingEntityUseItem(@Nonnull LivingEntityUseItemEvent.Tick evt) {
        EntityLivingBase entityLivingBase = evt.getEntityLiving();
        @Nonnull ItemStack itemStack = evt.getItem();
        if (!itemStack.isEmpty() && !(itemStack.getItem() instanceof ItemBow)) {
            float attributeValue = AttributesUtil.getAttributeValue(entityLivingBase, PREPARATION_SPEED);
            float configValue = ConfigAttribute.preparationSpeed;
            float speed = attributeValue + configValue;

            if (speed < 1) {
                if (RandomUtil.percentageChance((1 - speed) * 100)) {
                    evt.setDuration(evt.getDuration() + 1);

                    LogUtil.debugAttribute("使用速度减缓", entityLivingBase.getName(), attributeValue, configValue, speed);
                }
            }
        }
    }
}