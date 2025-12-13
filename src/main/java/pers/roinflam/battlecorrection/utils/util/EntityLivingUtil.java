package pers.roinflam.battlecorrection.utils.util;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.battlecorrection.utils.ReflectionCache;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EntityLivingUtil {
    private final static HashMap<UUID, Float> NOW_TICKS_SINCE_LAST_SWING_LIST = new HashMap<>();
    private final static HashMap<UUID, Float> LAST_TICKS_SINCE_LAST_SWING_LIST = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.phase.equals(TickEvent.Phase.START)) {
            LAST_TICKS_SINCE_LAST_SWING_LIST.put(evt.player.getUniqueID(), NOW_TICKS_SINCE_LAST_SWING_LIST.getOrDefault(evt.player.getUniqueID(), 1f));
            NOW_TICKS_SINCE_LAST_SWING_LIST.put(evt.player.getUniqueID(), evt.player.getCooledAttackStrength(0));
        }
    }

    public static float getTicksSinceLastSwing(@Nonnull EntityPlayer entityPlayer) {
        return LAST_TICKS_SINCE_LAST_SWING_LIST.getOrDefault(entityPlayer.getUniqueID(), 1f);
    }

    public static void setJumped(EntityLivingBase entityLivingBase) {
        ReflectionCache.setJumpTicks(entityLivingBase, 10);
    }

    public static void updateHeld(EntityLivingBase entityLivingBase) {
        ReflectionCache.invokeUpdateActiveHand(entityLivingBase);
    }

    public static void kill(@Nullable EntityLivingBase entityLivingBase, @Nonnull DamageSource damageSource) {
        if (entityLivingBase != null) {
            entityLivingBase.attackEntityFrom(damageSource, entityLivingBase.getMaxHealth());
            if (entityLivingBase.isEntityAlive()) {
                entityLivingBase.onDeath(damageSource);
                entityLivingBase.setHealth(0);
            }
        }
    }

}
