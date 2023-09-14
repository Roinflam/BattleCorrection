package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

@Mod.EventBusSubscriber
public class AttributeReducedFallDamage {
    public static final UUID ID = UUID.fromString("a0054dfc-c4ba-b1df-cce7-9526b5981b9c");
    public static final String NAME = "battlecorrection.reducedFallDamage";

    public static final IAttribute REDUCED_FALL_DAMAGE = (new RangedAttribute(null, NAME, 0, 0, Float.MAX_VALUE)).setDescription("Reduced Fall Damage");

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.equals(DamageSource.FALL)) {
                @Nullable EntityLivingBase hurter = evt.getEntityLiving();
                double reducedFallDamage = AttributesUtil.getAttributeValue(hurter, REDUCED_FALL_DAMAGE) + ConfigAttribute.reducedFallDamage;
                if (evt.getAmount() <= reducedFallDamage) {
                    evt.setCanceled(true);
                } else {
                    evt.setAmount((float) (evt.getAmount() - reducedFallDamage));
                }
            }
        }
    }

}
