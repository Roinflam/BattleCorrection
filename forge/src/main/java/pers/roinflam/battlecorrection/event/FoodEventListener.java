package pers.roinflam.battlecorrection.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 饥饿恢复事件监听器
 * 处理额外的饱和度和饥饿值恢复
 * <p>
 * 注意：这里每个玩家每 tick 都会执行（每秒 20 次），配置里的回血量也是"每 tick"的量。
 * 旧版本不管开没开详细日志都会用 String.format 拼一遍日志文本，现在只在开启时才拼。
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class FoodEventListener {

    /**
     * 触发额外恢复所需的最低饥饿值
     */
    private static final int MIN_FOOD_LEVEL = 18;

    /**
     * 处理玩家Tick - 饥饿恢复
     *
     * @param evt 玩家 tick 事件
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.player.level().isClientSide()) {
            return;
        }

        Player player = evt.player;
        // 只在玩家需要治疗时处理
        if (!player.isAlive() || player.getHealth() >= player.getMaxHealth()) {
            return;
        }

        FoodData foodData = player.getFoodData();
        // 两类额外恢复都要求饥饿值 >= 18
        if (foodData.getFoodLevel() < MIN_FOOD_LEVEL) {
            return;
        }

        float maxHealth = player.getMaxHealth();
        boolean hasSaturation = foodData.getSaturationLevel() > 0;

        // 饱和度恢复（饱和度 > 0 且 饥饿值 >= 18）
        double saturationFlat = hasSaturation ? ConfigBattle.EXTRA_SATURATION_HEAL.get() : 0;
        double saturationPercent = hasSaturation ? ConfigBattle.EXTRA_SATURATION_PERCENTAGE_HEAL.get() : 0;
        // 饥饿值恢复（饥饿值 >= 18，无论饱和度）
        double hungerFlat = ConfigBattle.EXTRA_HUNGER_HEAL.get();
        double hungerPercent = ConfigBattle.EXTRA_HUNGER_PERCENTAGE_HEAL.get();

        float healAmount = 0;
        if (saturationFlat > 0) {
            healAmount += (float) saturationFlat;
        }
        if (saturationPercent > 0) {
            healAmount += maxHealth * (float) saturationPercent;
        }
        if (hungerFlat > 0) {
            healAmount += (float) hungerFlat;
        }
        if (hungerPercent > 0) {
            healAmount += maxHealth * (float) hungerPercent;
        }

        if (healAmount <= 0) {
            return;
        }

        player.heal(healAmount);

        if (LogUtil.isDetailed()) {
            String healReason = String.format(
                    "饱和度固定: +%.2f, 饱和度百分比: +%.2f (%.2f%%), 饥饿值固定: +%.2f, 饥饿值百分比: +%.2f (%.2f%%)",
                    saturationFlat,
                    maxHealth * saturationPercent, saturationPercent * 100,
                    hungerFlat,
                    maxHealth * hungerPercent, hungerPercent * 100);
            LogUtil.debugHeal(player.getName().getString(), 0, healAmount, healReason);
        }
    }
}
