// 文件：AttributeBowSpeed.java
// 路径：src/main/java/pers/roinflam/battlecorrection/attributes/AttributeBowSpeed.java
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
 * 弓速属性
 * 增加拉弓速度
 */
@Mod.EventBusSubscriber
public class AttributeBowSpeed {
    public static final UUID ID = UUID.fromString("bac6b518-fea5-ea64-bbec-ddbeb0280bb0");
    public static final String NAME = "battlecorrection.bowSpeed";

    public static final IAttribute BOW_SPEED = ModAttributes.BOW_SPEED;

    /**
     * 每 tick 最多额外推进的次数
     * 再多的额外推进也没有意义（物品早就用完了）；封顶是为了防止属性或配置填了离谱的大数时，
     * 在一个 tick 里循环几亿次把服务器卡死
     */
    private static final int MAX_EXTRA_UPDATES_PER_TICK = 64;

    /**
     * 处理实体更新事件以加快拉弓速度
     * 客户端和服务端都执行：物品剩余使用时间不同步，客户端也要推进才能显示正确的动画
     */
    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        EntityLivingBase entityLivingBase = evt.getEntityLiving();
        if (!entityLivingBase.isHandActive()) {
            return;
        }
        ItemStack itemStack = entityLivingBase.getActiveItemStack();
        if (itemStack.isEmpty() || !isTargetItem(itemStack)) {
            return;
        }

        // 最终速度 = (装备 - 1) + 配置；装备默认 1、配置默认 1，合计 1 = 原速
        float attributeValue = AttributesUtil.getAttributeValue(entityLivingBase, BOW_SPEED);
        float configValue = ConfigAttribute.bowSpeed;
        float speed = (attributeValue - 1.0f) + configValue;
        // speed = 1.0 时不做任何处理（正常速度）；只处理加速
        if (Math.abs(speed - 1.0f) < 0.001f || !(speed > 1.0f)) {
            return;
        }

        // 整数部分：固定额外推进几次；物品用完（isHandActive 变 false）就立即停止
        int extraUpdates = (int) Math.min(speed - 1.0f, MAX_EXTRA_UPDATES_PER_TICK);
        for (int i = 0; i < extraUpdates && entityLivingBase.isHandActive(); i++) {
            EntityLivingUtil.updateHeld(entityLivingBase);
        }
        // 小数部分：按概率再推进一次（整数部分已封顶时不再追加）
        float fractionalPart = speed - 1.0f - extraUpdates;
        if (extraUpdates < MAX_EXTRA_UPDATES_PER_TICK && fractionalPart > 0 && entityLivingBase.isHandActive()
                && RandomUtil.percentageChance(fractionalPart * 100)) {
            EntityLivingUtil.updateHeld(entityLivingBase);
        }

        // 只在服务端打印日志，避免客户端重复
        if (!entityLivingBase.world.isRemote && LogUtil.isDetailed()) {
            LogUtil.debugAttribute("弓速加成", entityLivingBase.getName(), attributeValue - 1.0f, configValue, speed);
        }
    }

    /**
     * 处理使用物品事件以减慢拉弓速度（当速度小于1时）
     * 客户端和服务端都执行
     */
    @SubscribeEvent
    public static void onLivingEntityUseItem(@Nonnull LivingEntityUseItemEvent.Tick evt) {
        EntityLivingBase entityLivingBase = evt.getEntityLiving();
        ItemStack itemStack = evt.getItem();
        if (itemStack.isEmpty() || !isTargetItem(itemStack)) {
            return;
        }

        float attributeValue = AttributesUtil.getAttributeValue(entityLivingBase, BOW_SPEED);
        float configValue = ConfigAttribute.bowSpeed;
        float speed = (attributeValue - 1.0f) + configValue;
        // speed = 1.0 时不做任何处理（正常速度）；只处理减速
        if (Math.abs(speed - 1.0f) < 0.001f || !(speed < 1.0f)) {
            return;
        }

        // 按 (1 - speed) 的概率暂停使用进度；speed <= 0 时完全无法拉弓
        if (speed <= 0 || RandomUtil.percentageChance((1.0f - speed) * 100)) {
            evt.setDuration(evt.getDuration() + 1);
            if (!entityLivingBase.world.isRemote && LogUtil.isDetailed()) {
                LogUtil.debugAttribute("弓速减缓", entityLivingBase.getName(), attributeValue - 1.0f, configValue, speed);
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
        return (itemStack.getItem() instanceof ItemBow);
    }
}