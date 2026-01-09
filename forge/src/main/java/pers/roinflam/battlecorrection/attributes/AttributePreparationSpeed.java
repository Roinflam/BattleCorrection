// AttributePreparationSpeed.java
// 路径：src/main/java/pers/roinflam/battlecorrection/attributes/AttributePreparationSpeed.java
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
import pers.roinflam.battlecorrection.utils.ReflectionCache;
import pers.roinflam.battlecorrection.utils.random.RandomUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 使用速度属性
 * 增加使用物品的速度，如吃食物、喝药水等（不包括弓和弩）
 * <p>
 * 通过直接减少 useItemRemaining 字段来加速物品使用
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
        LivingEntity entity = evt.getEntity();

        // 只在服务端处理
        if (entity.level().isClientSide()) {
            return;
        }

        // 检查是否正在使用物品
        if (!entity.isUsingItem()) {
            return;
        }

        @Nonnull ItemStack itemStack = entity.getUseItem();

        // 检查物品是否有效
        if (itemStack.isEmpty()) {
            return;
        }

        // 排除弓和弩（它们使用 AttributeBowSpeed）
        if (itemStack.getItem() instanceof BowItem || itemStack.getItem() instanceof CrossbowItem) {
            return;
        }

        // 计算总速度
        float attributeValue = (float) AttributesUtil.getAttributeValue(entity, ModAttributes.PREPARATION_SPEED.get());
        float configValue = ConfigAttribute.PREPARATION_SPEED.get().floatValue();
        float speed = attributeValue + configValue;

        // 速度大于1时加速
        if (speed > 1) {
            // 计算需要额外减少的tick数
            int extraTicks = (int) (speed - 1);

            // 处理小数部分（概率触发额外1 tick）
            float fractionalPart = speed - 1 - extraTicks;
            if (fractionalPart > 0 && RandomUtil.percentageChance(fractionalPart * 100)) {
                extraTicks++;
            }

            // 直接减少 useItemRemaining
            if (extraTicks > 0) {
                boolean success = ReflectionCache.reduceUseItemRemaining(entity, extraTicks);

                if (success) {
                    LogUtil.debugAttribute("使用速度加成", entity.getName().getString(),
                            attributeValue, configValue, speed);
                    LogUtil.debugEvent("使用物品加速", entity.getName().getString(),
                            String.format("物品: %s, 额外减少 %d tick, 剩余: %d tick",
                                    itemStack.getHoverName().getString(), extraTicks,
                                    entity.getUseItemRemainingTicks()));
                }
            }
        }
    }

    /**
     * 处理使用物品事件以减慢使用速度（当速度小于1时）
     */
    @SubscribeEvent
    public static void onLivingEntityUseItem(@Nonnull LivingEntityUseItemEvent.Tick evt) {
        LivingEntity entity = evt.getEntity();

        // 只在服务端处理
        if (entity.level().isClientSide()) {
            return;
        }

        @Nonnull ItemStack itemStack = evt.getItem();

        // 检查物品是否有效
        if (itemStack.isEmpty()) {
            return;
        }

        // 排除弓和弩
        if (itemStack.getItem() instanceof BowItem || itemStack.getItem() instanceof CrossbowItem) {
            return;
        }

        // 计算总速度
        float attributeValue = (float) AttributesUtil.getAttributeValue(entity, ModAttributes.PREPARATION_SPEED.get());
        float configValue = ConfigAttribute.PREPARATION_SPEED.get().floatValue();
        float speed = attributeValue + configValue;

        // 速度小于1时减速
        if (speed > 0 && speed < 1) {
            // 按概率暂停使用进度
            if (RandomUtil.percentageChance((1 - speed) * 100)) {
                evt.setDuration(evt.getDuration() + 1);

                LogUtil.debugAttribute("使用速度减缓", entity.getName().getString(),
                        attributeValue, configValue, speed);
            }
        }
    }
}