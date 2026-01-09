// AttributeBowSpeed.java
// 路径：src/main/java/pers/roinflam/battlecorrection/attributes/AttributeBowSpeed.java
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
 * 弓速属性
 * 增加拉弓速度
 * <p>
 * 通过直接减少 useItemRemaining 字段来加速拉弓
 * 弓的拉弓进度 = getUseDuration() - useItemRemaining
 * 所以减少 useItemRemaining 会增加拉弓进度
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeBowSpeed {

    public static final UUID ID = UUID.fromString("bac6b518-fea5-ea64-bbec-ddbeb0280bb0");
    public static final String NAME = "battlecorrection.bowSpeed";

    /**
     * 处理实体更新事件以加快拉弓速度
     * <p>
     * 原理：直接减少 useItemRemaining 字段值
     * 弓的拉弓进度计算：useDuration - useItemRemaining
     * 减少 useItemRemaining 相当于增加拉弓进度
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

        // 检查是否是弓或弩
        if (itemStack.isEmpty()) {
            return;
        }

        if (!(itemStack.getItem() instanceof BowItem) && !(itemStack.getItem() instanceof CrossbowItem)) {
            return;
        }

        // 计算总速度
        float attributeValue = (float) AttributesUtil.getAttributeValue(entity, ModAttributes.BOW_SPEED.get());
        float configValue = ConfigAttribute.BOW_SPEED.get().floatValue();
        float speed = attributeValue + configValue;

        // 速度大于1时加速
        if (speed > 1) {
            // 计算需要额外减少的tick数
            // speed = 2.0 表示2倍速，需要额外减少1 tick（加上原本的1 tick = 2 tick/帧）
            // speed = 3.0 表示3倍速，需要额外减少2 tick
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
                    LogUtil.debugAttribute("弓速加成", entity.getName().getString(),
                            attributeValue, configValue, speed);
                    LogUtil.debug(String.format("弓速加速 - 额外减少 %d tick, 剩余: %d tick",
                            extraTicks, entity.getUseItemRemainingTicks()));
                }
            }
        }
    }

    /**
     * 处理使用物品事件以减慢拉弓速度（当速度小于1时）
     * <p>
     * 通过增加 duration 来减慢使用速度
     */
    @SubscribeEvent
    public static void onLivingEntityUseItem(@Nonnull LivingEntityUseItemEvent.Tick evt) {
        LivingEntity entity = evt.getEntity();

        // 只在服务端处理
        if (entity.level().isClientSide()) {
            return;
        }

        @Nonnull ItemStack itemStack = evt.getItem();

        // 检查是否是弓或弩
        if (itemStack.isEmpty()) {
            return;
        }

        if (!(itemStack.getItem() instanceof BowItem) && !(itemStack.getItem() instanceof CrossbowItem)) {
            return;
        }

        // 计算总速度
        float attributeValue = (float) AttributesUtil.getAttributeValue(entity, ModAttributes.BOW_SPEED.get());
        float configValue = ConfigAttribute.BOW_SPEED.get().floatValue();
        float speed = attributeValue + configValue;

        // 速度小于1时减速
        if (speed > 0 && speed < 1) {
            // 按概率暂停使用进度
            // speed = 0.5 表示50%速度，有50%几率暂停
            if (RandomUtil.percentageChance((1 - speed) * 100)) {
                evt.setDuration(evt.getDuration() + 1);

                LogUtil.debugAttribute("弓速减缓", entity.getName().getString(),
                        attributeValue, configValue, speed);
            }
        }
    }
}