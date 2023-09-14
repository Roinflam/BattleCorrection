package pers.roinflam.battlecorrection.eventlistener;

import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class DamageEventListener {

    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote && ConfigBattle.comboCorrection) {
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
                            evt.setAmount(evt.getAmount() * ConfigBattle.playerMeleeAttack);
                        } else if (!immediateSource.equals(trueSource) && trueSource instanceof EntityPlayer) {
                            if (immediateSource instanceof EntityArrow) {
                                evt.setAmount(evt.getAmount() * ConfigBattle.playerArrowAttack);
                            } else if (immediateSource instanceof IProjectile) {
                                evt.setAmount(evt.getAmount() * ConfigBattle.playerProjectileAttack);
                            }
                        }
                    }
                    if (immediateSource instanceof EntityPlayer || trueSource instanceof EntityPlayer) {
                        if (damageSource.isMagicDamage()) {
                            evt.setAmount(evt.getAmount() * ConfigBattle.playerMagicAttack);
                        }
                    }
                    if (trueSource instanceof EntityPlayer) {
                        EntityPlayer entityPlayer = (EntityPlayer) trueSource;
                        evt.setAmount(evt.getAmount() * (1 - Math.min((20 - entityPlayer.getFoodStats().getFoodLevel()) * ConfigBattle.hungerDamageDecay, ConfigBattle.hungerDamageDecayLimit)));
                    }
                    if (evt.getEntityLiving() instanceof EntityPlayer) {
                        EntityPlayer hurter = (EntityPlayer) evt.getEntityLiving();
                        if (immediateSource.equals(trueSource) && !damageSource.isMagicDamage()) {
                            evt.setAmount(evt.getAmount() * ConfigBattle.playerSuffersMelee);
                        } else if (!immediateSource.equals(trueSource) && !damageSource.isMagicDamage()) {
                            if (immediateSource instanceof EntityArrow) {
                                evt.setAmount(evt.getAmount() * ConfigBattle.playerSuffersArrow);
                            } else if (immediateSource instanceof IProjectile) {
                                evt.setAmount(evt.getAmount() * ConfigBattle.playerSuffersProjectile);
                            }
                        }
                        if (damageSource.isMagicDamage()) {
                            evt.setAmount(evt.getAmount() * ConfigBattle.playerSuffersMagic);
                        }
                    }
                }
            }
        }
    }
}
