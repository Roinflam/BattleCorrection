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
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;
import pers.roinflam.battlecorrection.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

@Mod.EventBusSubscriber
public class AttributeBloodthirsty {
    public static final UUID ID = UUID.fromString("843155e7-bbb9-9577-96cb-dcce692b6e62");
    public static final String NAME = "battlecorrection.bloodthirsty";

    public static final IAttribute BLOODTHIRSTY = (new RangedAttribute(null, NAME, 1, 1, Float.MAX_VALUE)).setDescription("Bloodthirsty");

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getImmediateSource() instanceof EntityLivingBase) {
                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getImmediateSource();
                if (attacker instanceof EntityPlayer) {
                    if (EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker) != 1) {
                        return;
                    }
                }
                attacker.heal(evt.getAmount() * ((AttributesUtil.getAttributeValue(attacker, BLOODTHIRSTY) - 1) + ConfigAttribute.bloodthirsty));
            }
        }
    }

}
