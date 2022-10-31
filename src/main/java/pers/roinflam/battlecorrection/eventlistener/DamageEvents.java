package pers.roinflam.battlecorrection.eventlistener;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigLoader;
import pers.roinflam.battlecorrection.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class DamageEvents {

    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote && !ConfigLoader.comboCorrection) {
            if (evt.getSource().getImmediateSource() instanceof EntityPlayer) {
                @Nullable EntityPlayer attacker = (EntityPlayer) evt.getSource().getImmediateSource();
                evt.setAmount(evt.getAmount() * Math.max(EntityLivingUtil.getTicksSinceLastSwing(attacker), 0.5f));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (!damageSource.canHarmInCreative()) {
                @Nullable Entity trueSource = evt.getSource().getTrueSource();
                @Nullable Entity immediateSource = evt.getSource().getImmediateSource();
                if (trueSource != null && immediateSource != null) {
                    if (!damageSource.isMagicDamage()) {
                        if (immediateSource instanceof EntityPlayer) {
                            evt.setAmount(evt.getAmount() * ConfigLoader.playerMeleeAttack);
                        } else if (!immediateSource.equals(trueSource) && trueSource instanceof EntityPlayer) {
                            evt.setAmount(evt.getAmount() * ConfigLoader.playerRemoteAttack);
                        }
                    }
                    if (damageSource.isMagicDamage() && (immediateSource instanceof EntityPlayer || trueSource instanceof EntityPlayer)) {
                        evt.setAmount(evt.getAmount() * ConfigLoader.playerMagicAttack);
                    }
                    if (evt.getEntityLiving() instanceof EntityPlayer) {
                        EntityPlayer hurter = (EntityPlayer) evt.getEntityLiving();
                        if (immediateSource.equals(trueSource) && !damageSource.isMagicDamage()) {
                            evt.setAmount(evt.getAmount() * ConfigLoader.playerSuffersMelee);
                        } else if (!immediateSource.equals(trueSource) && !damageSource.isMagicDamage()) {
                            evt.setAmount(evt.getAmount() * ConfigLoader.playerSuffersRemote);
                        }
                        if (damageSource.isMagicDamage()) {
                            evt.setAmount(evt.getAmount() * ConfigLoader.playerSuffersMagic);
                        }
                    }
                }
            }
        }
    }
}
