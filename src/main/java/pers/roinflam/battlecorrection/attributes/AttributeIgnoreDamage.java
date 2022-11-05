package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

@Mod.EventBusSubscriber
public class AttributeIgnoreDamage {
    public static final UUID ID = UUID.fromString("d444f13a-b3c7-a700-52b7-47677d723207");
    public static final String NAME = "battlecorrection.ignoreDamage";

    public static final IAttribute IMMUNE_DAMAGE = (new RangedAttribute(null, NAME, 0, 0, Float.MAX_VALUE)).setDescription("Ignore Damage");

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (!damageSource.canHarmInCreative()) {
                @Nullable EntityLivingBase hurter = evt.getEntityLiving();
                double ignore = AttributesUtil.getAttributeValue(hurter, IMMUNE_DAMAGE);
                if (evt.getAmount() <= ignore) {
                    evt.setCanceled(true);
                } else {
                    evt.setAmount((float) (evt.getAmount() - ignore));
                }
            }
        }
    }

}
