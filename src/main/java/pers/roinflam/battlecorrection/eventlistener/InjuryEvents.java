package pers.roinflam.battlecorrection.eventlistener;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigLoader;

import javax.annotation.Nonnull;
import java.util.UUID;

@Mod.EventBusSubscriber
public class InjuryEvents {
    public static final UUID ID = UUID.fromString("a05141e5-4898-7440-0615-9ac825922a2a");
    public static final String NAME = "battlecorrection.attack_cooldown";

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getEntity() instanceof EntityPlayer) {
                if (ConfigLoader.pvpHurtItself <= 0 && evt.getEntity().equals(evt.getSource().getTrueSource())) {
                    evt.setCanceled(true);
                } else if (ConfigLoader.pvp <= 0 && evt.getSource().getTrueSource() instanceof EntityPlayer) {
                    evt.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            if (hurter instanceof EntityPlayer) {
                if (hurter.equals(evt.getSource().getTrueSource())) {
                    evt.setAmount(evt.getAmount() * ConfigLoader.pvpHurtItself);
                } else if (evt.getSource().getTrueSource() instanceof EntityPlayer) {
                    evt.setAmount(evt.getAmount() * ConfigLoader.pvp);
                }
                hurter.hurtResistantTime = (int) (hurter.maxHurtResistantTime / 2 + hurter.maxHurtResistantTime / 2 * ConfigLoader.hurtTimePlayer);
            } else {
                hurter.hurtResistantTime = (int) (hurter.maxHurtResistantTime / 2 + hurter.maxHurtResistantTime / 2 * ConfigLoader.hurtTimeEntity);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(@Nonnull EntityJoinWorldEvent evt) {
        if (!evt.getWorld().isRemote && evt.getEntity() instanceof EntityPlayer) {
            EntityPlayer entityPlayer = (EntityPlayer) evt.getEntity();
            @Nonnull IAttributeInstance attributeInstance = entityPlayer.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
            if (!ConfigLoader.attackCooldown) {
                if (attributeInstance.getModifier(ID) == null) {
                    attributeInstance.applyModifier(new AttributeModifier(ID, NAME, Double.MAX_VALUE / 2, 0));
                }
            } else {
                attributeInstance.removeModifier(ID);
            }
        }
    }
}
