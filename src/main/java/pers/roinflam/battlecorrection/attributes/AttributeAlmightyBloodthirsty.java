package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;
import pers.roinflam.battlecorrection.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

@Mod.EventBusSubscriber
public class AttributeAlmightyBloodthirsty {
    public static final UUID ID = UUID.fromString("f8b6bb16-c5e8-69b7-e994-a8f51ce67be5");
    public static final String NAME = "battlecorrection.almightyBloodthirsty";

    public static final IAttribute ALMIGHTY_BLOODTHIRSTY = (new RangedAttribute(null, NAME, 1, 1, Float.MAX_VALUE)).setDescription("Almighty Bloodthirsty");

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getTrueSource() instanceof EntityLivingBase) {
                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
                if (damageSource.getImmediateSource().equals(attacker) && attacker instanceof EntityPlayer) {
                    if (EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker) != 1) {
                        return;
                    }
                }
                attacker.heal(evt.getAmount() * (AttributesUtil.getAttributeValue(attacker, ALMIGHTY_BLOODTHIRSTY) - 1));
            }
        }
    }

}
