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
 * 弓速属性
 * 增加拉弓速度
 * <p>
 * 速度机制（完全模拟1.12实现）：
 * - speed = 1.0：正常速度
 * - speed > 1.0：加速（通过额外调用updatingUsingItem方法）
 * - speed < 1.0：减速（通过增加duration跳过更新）
 * - speed = 0.0：完全无法拉弓
 * <p>
 * 重要：客户端和服务端都会执行，因为useItemRemaining字段不会自动同步
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeBowSpeed {

    public static final UUID ID = UUID.fromString("bac6b518-fea5-ea64-bbec-ddbeb0280bb0");
    public static final String NAME = "battlecorrection.bowSpeed";

    /**
     * 处理实体更新事件以加快拉弓速度
     * 完全模拟1.12的实现：客户端和服务端都执行
     * <p>
     * 为什么两端都执行：
     * 1. useItemRemaining字段不是SynchedEntityData，不会自动同步
     * 2. 客户端需要更新才能显示正确的拉弓动画
     * 3. 服务端需要更新才能处理实际的游戏逻辑
     */
    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingTickEvent evt) {
        LivingEntity entity = evt.getEntity();

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
        float speed = (attributeValue - 1.0f) + configValue;

        // speed = 1.0 时不做任何处理（正常速度）
        if (Math.abs(speed - 1.0f) < 0.001f) {
            return;
        }

        // 速度大于1.0时加速
        if (speed > 1.0f) {
            // 计算需要额外调用updatingUsingItem的次数
            int extraUpdates = (int) (speed - 1.0f);

            // 固定调用次数
            for (int i = 0; i < extraUpdates; i++) {
                EntityLivingUtil.updateHeld(entity);
            }

            // 处理小数部分（概率触发额外调用）
            float fractionalPart = speed - 1.0f - extraUpdates;
            if (fractionalPart > 0 && RandomUtil.percentageChance(fractionalPart * 100)) {
                EntityLivingUtil.updateHeld(entity);
            }

            // 只在服务端打印日志，避免客户端重复
            if (!entity.level().isClientSide() && (extraUpdates > 0 || fractionalPart > 0)) {
                LogUtil.debugAttribute("弓速加成", entity.getName().getString(),
                        attributeValue - 1.0f, configValue, speed);
            }
        }
    }

    /**
     * 处理使用物品事件以减慢拉弓速度（当速度小于1时）
     * 客户端和服务端都执行
     */
    @SubscribeEvent
    public static void onLivingEntityUseItem(@Nonnull LivingEntityUseItemEvent.Tick evt) {
        LivingEntity entity = evt.getEntity();

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
        float speed = (attributeValue - 1.0f) + configValue;

        // speed = 1.0 时不做任何处理（正常速度）
        if (Math.abs(speed - 1.0f) < 0.001f) {
            return;
        }

        // 速度小于1.0时减速
        if (speed < 1.0f) {
            // 按 (1 - speed) 的概率暂停使用进度
            if (speed <= 0 || RandomUtil.percentageChance((1.0f - speed) * 100)) {
                evt.setDuration(evt.getDuration() + 1);

                // 只在服务端打印日志
                if (!entity.level().isClientSide()) {
                    LogUtil.debugAttribute("弓速减缓", entity.getName().getString(),
                            attributeValue - 1.0f, configValue, speed);
                }
            }
        }
    }
}