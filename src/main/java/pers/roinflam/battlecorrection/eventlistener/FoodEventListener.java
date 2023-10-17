package pers.roinflam.battlecorrection.eventlistener;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.FoodStats;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.battlecorrection.config.ConfigBattle;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class FoodEventListener {

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
                    entityPlayer.heal(ConfigBattle.extraSaturationHeal);
                    entityPlayer.heal(entityPlayer.getMaxHealth() * ConfigBattle.extraSaturationPercentageHeal);
                    if (!entityPlayer.shouldHeal()) {
                        resetFoodTimer(entityPlayer);
                    }
                } else if (foodStats.getFoodLevel() >= 18 && foodTimer == 79) {
                    entityPlayer.heal(ConfigBattle.extraHungerHeal);
                    entityPlayer.heal(entityPlayer.getMaxHealth() * ConfigBattle.extraHungerPercentageHeal);
                    if (!entityPlayer.shouldHeal()) {
                        resetFoodTimer(entityPlayer);
                    }
                }
            }
        }
    }

    private static void resetFoodTimer(@Nonnull EntityPlayer entityPlayer) {
        if (!entityPlayer.shouldHeal()) {
            @Nonnull FoodStats foodStats = entityPlayer.getFoodStats();
            foodStats.addExhaustion(6.0F);
            try {
                ObfuscationReflectionHelper.setPrivateValue(FoodStats.class, foodStats, 0, "foodTimer");
            } catch (Exception e) {
                ObfuscationReflectionHelper.setPrivateValue(FoodStats.class, foodStats, 0, "field_75123_d");
            }
        }
    }

}