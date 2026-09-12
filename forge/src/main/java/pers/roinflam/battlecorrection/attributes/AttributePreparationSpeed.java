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

/**
 * 使用速度属性
 * 增加使用物品的速度，如吃食物、喝药水等（不包括弓和弩）
 * <p>
 * 速度机制（完全模拟1.12实现）：
 * - speed = 1.0：正常速度
 * - speed > 1.0：加速（通过额外调用updatingUsingItem方法）
 * - speed < 1.0：减速（通过增加duration跳过更新）
 * - speed = 0.0：完全无法使用
 * <p>
 * 重要：客户端和服务端都会执行，因为useItemRemaining字段不会自动同步
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributePreparationSpeed {

    /**
     * 每 tick 最多额外推进的次数
     * 再多的额外推进也没有意义（物品早就用完了）；封顶是为了防止属性或配置填了离谱的大数时，
     * 在一个 tick 里循环几亿次把服务器卡死
     */
    private static final int MAX_EXTRA_UPDATES_PER_TICK = 64;

    /**
     * 处理实体更新事件以加快使用速度速度
     * 完全模拟1.12的实现：客户端和服务端都执行
     * <p>
     * 为什么两端都执行：
     * 1. useItemRemaining字段不是SynchedEntityData，不会自动同步
     * 2. 客户端需要更新才能显示正确的动画
     * 3. 服务端需要更新才能处理实际的游戏逻辑
     *
     * @param evt 生物 tick 事件
     */
    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingTickEvent evt) {
        LivingEntity entity = evt.getEntity();

        // 检查是否正在使用物品
        if (!entity.isUsingItem()) {
            return;
        }

        ItemStack itemStack = entity.getUseItem();
        if (itemStack.isEmpty() || !isTargetItem(itemStack)) {
            return;
        }

        // 计算总速度
        float attributeValue = (float) AttributesUtil.getAttributeValue(entity, ModAttributes.PREPARATION_SPEED.get());
        float configValue = ConfigAttribute.PREPARATION_SPEED.get().floatValue();
        float speed = (attributeValue - 1.0f) + configValue;

        // speed = 1.0 时不做任何处理（正常速度）；只处理加速
        if (Math.abs(speed - 1.0f) < 0.001f || !(speed > 1.0f)) {
            return;
        }

        // 整数部分：固定额外推进几次；物品用完（isUsingItem 变 false）就立即停止
        int extraUpdates = (int) Math.min(speed - 1.0f, MAX_EXTRA_UPDATES_PER_TICK);
        for (int i = 0; i < extraUpdates && entity.isUsingItem(); i++) {
            EntityLivingUtil.updateHeld(entity);
        }

        // 小数部分：按概率再推进一次（整数部分已封顶时不再追加）
        float fractionalPart = speed - 1.0f - extraUpdates;
        if (extraUpdates < MAX_EXTRA_UPDATES_PER_TICK && fractionalPart > 0 && entity.isUsingItem()
                && RandomUtil.percentageChance(fractionalPart * 100)) {
            EntityLivingUtil.updateHeld(entity);
        }

        // 只在服务端打印日志，避免客户端重复
        if (!entity.level().isClientSide() && LogUtil.isDetailed()) {
            LogUtil.debugAttribute("使用速度加成", entity.getName().getString(),
                    attributeValue - 1.0f, configValue, speed);
        }
    }

    /**
     * 处理使用物品事件以减慢使用速度速度（当速度小于1时）
     * 客户端和服务端都执行
     *
     * @param evt 物品使用 tick 事件
     */
    @SubscribeEvent
    public static void onLivingEntityUseItem(@Nonnull LivingEntityUseItemEvent.Tick evt) {
        LivingEntity entity = evt.getEntity();
        ItemStack itemStack = evt.getItem();
        if (itemStack.isEmpty() || !isTargetItem(itemStack)) {
            return;
        }

        // 计算总速度
        float attributeValue = (float) AttributesUtil.getAttributeValue(entity, ModAttributes.PREPARATION_SPEED.get());
        float configValue = ConfigAttribute.PREPARATION_SPEED.get().floatValue();
        float speed = (attributeValue - 1.0f) + configValue;

        // speed = 1.0 时不做任何处理（正常速度）；只处理减速
        if (Math.abs(speed - 1.0f) < 0.001f || !(speed < 1.0f)) {
            return;
        }

        // 按 (1 - speed) 的概率暂停使用进度
        if (speed <= 0 || RandomUtil.percentageChance((1.0f - speed) * 100)) {
            evt.setDuration(evt.getDuration() + 1);

            // 只在服务端打印日志
            if (!entity.level().isClientSide() && LogUtil.isDetailed()) {
                LogUtil.debugAttribute("使用速度减缓", entity.getName().getString(),
                        attributeValue - 1.0f, configValue, speed);
            }
        }
    }

    /**
     * 判断物品是否归本属性管
     *
     * @param itemStack 正在使用的物品
     * @return true = 归本属性管
     */
    private static boolean isTargetItem(@Nonnull ItemStack itemStack) {
        boolean isBowLike = itemStack.getItem() instanceof BowItem || itemStack.getItem() instanceof CrossbowItem;
        return !isBowLike;
    }
}
