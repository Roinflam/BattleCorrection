// 文件：AttributePreparationSpeed.java
// 路径：forge/src/main/java/pers/roinflam/battlecorrection/attributes/AttributePreparationSpeed.java
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
 * 速度机制：
 * - speed = 1.0：正常速度
 * - speed > 1.0：加速（例如 2.3 = 固定额外更新1次 + 30%概率额外更新1次）
 * - speed < 1.0：减速（例如 0.5 = 50%概率跳过更新）
 * - speed = 0.0：完全无法使用（100%跳过更新）
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributePreparationSpeed {

    public static final UUID ID = UUID.fromString("585cc013-a0b6-c8e2-9001-6b55c10caf6d");
    public static final String NAME = "battlecorrection.preparationSpeed";

    /**
     * 处理实体更新事件以加快使用物品速度
     * 通过直接减少 useItemRemaining 字段值来加速
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
        // 属性默认1.0，配置默认1.0
        // 最终速度 = (属性值 - 1.0) + 配置值
        // 例如：属性1.0 + 配置1.0 = (1.0 - 1.0) + 1.0 = 1.0（正常速度）
        // 例如：属性1.0 + 配置2.0 = (1.0 - 1.0) + 2.0 = 2.0（2倍速度）
        float attributeValue = (float) AttributesUtil.getAttributeValue(entity, ModAttributes.PREPARATION_SPEED.get());
        float configValue = ConfigAttribute.PREPARATION_SPEED.get().floatValue();
        float speed = (attributeValue - 1.0f) + configValue;

        // speed = 1.0 时不做任何处理（正常速度）
        if (Math.abs(speed - 1.0f) < 0.001f) {
            return;
        }

        // 速度大于1.0时加速
        if (speed > 1.0f) {
            // 计算需要额外减少的tick数
            // 例如 speed = 2.3：
            // - 整数部分 = 1（固定额外更新1次）
            // - 小数部分 = 0.3（30%概率额外更新1次）
            int extraTicks = (int) (speed - 1.0f);

            // 处理小数部分（概率触发额外1 tick）
            float fractionalPart = speed - 1.0f - extraTicks;
            if (fractionalPart > 0 && RandomUtil.percentageChance(fractionalPart * 100)) {
                extraTicks++;
            }

            // 直接减少 useItemRemaining
            if (extraTicks > 0) {
                boolean success = ReflectionCache.reduceUseItemRemaining(entity, extraTicks);

                if (success) {
                    LogUtil.debugAttribute("使用速度加成", entity.getName().getString(),
                            attributeValue - 1.0f, configValue, speed);
                    LogUtil.debugEvent("使用物品加速", entity.getName().getString(),
                            String.format("物品: %s, 速度: %.2fx, 额外减少 %d tick, 剩余: %d tick",
                                    itemStack.getHoverName().getString(), speed, extraTicks,
                                    entity.getUseItemRemainingTicks()));
                }
            }
        }
    }

    /**
     * 处理使用物品事件以减慢使用速度（当速度小于1时）
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
        float speed = (attributeValue - 1.0f) + configValue;

        // speed = 1.0 时不做任何处理（正常速度）
        if (Math.abs(speed - 1.0f) < 0.001f) {
            return;
        }

        // 速度小于1.0时减速
        if (speed < 1.0f) {
            // 按 (1 - speed) 的概率暂停使用进度
            // 例如 speed = 0.5：有 50% 概率暂停
            // 例如 speed = 0.0：有 100% 概率暂停（完全无法使用物品）
            if (speed <= 0 || RandomUtil.percentageChance((1.0f - speed) * 100)) {
                evt.setDuration(evt.getDuration() + 1);

                LogUtil.debugAttribute("使用速度减缓", entity.getName().getString(),
                        attributeValue - 1.0f, configValue, speed);
                LogUtil.debug(String.format("使用物品减速 - 物品: %s, 速度: %.2fx, 暂停更新",
                        itemStack.getHoverName().getString(), speed));
            }
        }
    }
}