// 文件：FoodEventListener.java
// 路径：src/main/java/pers/roinflam/battlecorrection/event/FoodEventListener.java
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
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class FoodEventListener {

    /**
     * 处理玩家Tick - 饥饿恢复
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.phase == TickEvent.Phase.END && !evt.player.level().isClientSide()) {
            Player player = evt.player;
            FoodData foodData = player.getFoodData();

            // 只在玩家需要治疗且满足条件时处理
            if (player.getHealth() < player.getMaxHealth() && player.isAlive()) {
                float healAmount = 0;
                String healReason = "";

                // 饱和度恢复（饱和度 > 0 且 饥饿值 >= 18）
                if (foodData.getSaturationLevel() > 0 && foodData.getFoodLevel() >= 18) {
                    // 额外固定治疗
                    double extraSaturationHeal = ConfigBattle.EXTRA_SATURATION_HEAL.get();
                    if (extraSaturationHeal > 0) {
                        healAmount += extraSaturationHeal;
                        healReason += String.format("饱和度固定恢复: +%.2f, ", extraSaturationHeal);
                    }

                    // 额外百分比治疗
                    double extraSaturationPercentHeal = ConfigBattle.EXTRA_SATURATION_PERCENTAGE_HEAL.get();
                    if (extraSaturationPercentHeal > 0) {
                        float percentHeal = player.getMaxHealth() * (float) extraSaturationPercentHeal;
                        healAmount += percentHeal;
                        healReason += String.format("饱和度百分比恢复: +%.2f (%.2f%%), ", percentHeal, extraSaturationPercentHeal * 100);
                    }
                }

                // 饥饿值恢复（饥饿值 >= 18，无论饱和度）
                if (foodData.getFoodLevel() >= 18) {
                    // 额外固定治疗
                    double extraHungerHeal = ConfigBattle.EXTRA_HUNGER_HEAL.get();
                    if (extraHungerHeal > 0) {
                        healAmount += extraHungerHeal;
                        healReason += String.format("饥饿值固定恢复: +%.2f, ", extraHungerHeal);
                    }

                    // 额外百分比治疗
                    double extraHungerPercentHeal = ConfigBattle.EXTRA_HUNGER_PERCENTAGE_HEAL.get();
                    if (extraHungerPercentHeal > 0) {
                        float percentHeal = player.getMaxHealth() * (float) extraHungerPercentHeal;
                        healAmount += percentHeal;
                        healReason += String.format("饥饿值百分比恢复: +%.2f (%.2f%%)", percentHeal, extraHungerPercentHeal * 100);
                    }
                }

                // 应用治疗
                if (healAmount > 0) {
                    player.heal(healAmount);
                    LogUtil.debugHeal(player.getName().getString(), 0, healAmount, healReason);
                }
            }
        }
    }
}