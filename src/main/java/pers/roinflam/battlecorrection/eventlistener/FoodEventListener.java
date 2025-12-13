package pers.roinflam.battlecorrection.eventlistener;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.FoodStats;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;

/**
 * 食物事件监听器
 * 处理额外的饥饿恢复效果
 */
@Mod.EventBusSubscriber
public class FoodEventListener {

    /**
     * 处理玩家Tick事件 - 额外饥饿恢复
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (!evt.player.world.isRemote && evt.phase.equals(TickEvent.Phase.START)) {
            @Nonnull EntityPlayer entityPlayer = evt.player;
            @Nonnull FoodStats foodStats = entityPlayer.getFoodStats();
            int foodTimer = 0;

            try {
                foodTimer = ObfuscationReflectionHelper.getPrivateValue(FoodStats.class, foodStats, "foodTimer");
            } catch (Exception e) {
                foodTimer = ObfuscationReflectionHelper.getPrivateValue(FoodStats.class, foodStats, "field_75123_d");
            }

            if (entityPlayer.world.getGameRules().getBoolean("naturalRegeneration") && entityPlayer.shouldHeal()) {
                if (foodStats.getSaturationLevel() > 0.0F && foodStats.getFoodLevel() >= 20 && foodTimer == 9) {
                    float extraHeal = ConfigBattle.extraSaturationHeal;
                    float percentageHeal = entityPlayer.getMaxHealth() * ConfigBattle.extraSaturationPercentageHeal;
                    float totalExtraHeal = extraHeal + percentageHeal;

                    if (totalExtraHeal > 0) {
                        entityPlayer.heal(totalExtraHeal);
                        LogUtil.debugHeal(entityPlayer.getName(), 1.0f, 1.0f + totalExtraHeal,
                                String.format("饱和度恢复 - 固定加成: %.2f, 百分比加成: %.2f (%.2f%%)",
                                        extraHeal, percentageHeal, ConfigBattle.extraSaturationPercentageHeal * 100));
                    }

                    if (!entityPlayer.shouldHeal()) {
                        resetFoodTimer(entityPlayer);
                    }
                } else if (foodStats.getFoodLevel() >= 18 && foodTimer == 79) {
                    float extraHeal = ConfigBattle.extraHungerHeal;
                    float percentageHeal = entityPlayer.getMaxHealth() * ConfigBattle.extraHungerPercentageHeal;
                    float totalExtraHeal = extraHeal + percentageHeal;

                    if (totalExtraHeal > 0) {
                        entityPlayer.heal(totalExtraHeal);
                        LogUtil.debugHeal(entityPlayer.getName(), 1.0f, 1.0f + totalExtraHeal,
                                String.format("饥饿值恢复 - 固定加成: %.2f, 百分比加成: %.2f (%.2f%%)",
                                        extraHeal, percentageHeal, ConfigBattle.extraHungerPercentageHeal * 100));
                    }

                    if (!entityPlayer.shouldHeal()) {
                        resetFoodTimer(entityPlayer);
                    }
                }
            }
        }
    }

    /**
     * 重置食物计时器
     *
     * @param entityPlayer 玩家实体
     */
    private static void resetFoodTimer(@Nonnull EntityPlayer entityPlayer) {
        if (!entityPlayer.shouldHeal()) {
            @Nonnull FoodStats foodStats = entityPlayer.getFoodStats();
            foodStats.addExhaustion(6.0F);

            try {
                ObfuscationReflectionHelper.setPrivateValue(FoodStats.class, foodStats, 0, "foodTimer");
            } catch (Exception e) {
                ObfuscationReflectionHelper.setPrivateValue(FoodStats.class, foodStats, 0, "field_75123_d");
            }

            LogUtil.debug(String.format("重置食物计时器 - 玩家: %s", entityPlayer.getName()));
        }
    }
}