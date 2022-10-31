package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.utils.java.random.RandomUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

@Mod.EventBusSubscriber
public class AttributeImmuneDamage {
    public static final UUID ID = UUID.fromString("1d309c38-5240-d9a4-bd56-bc4aed05e140");
    public static final String NAME = "battlecorrection.immuneDamage";

    public static final IAttribute IMMUNE_DAMAGE = (new RangedAttribute(null, NAME, 1, 1, Float.MAX_VALUE)).setDescription("Extra Immune Damage");

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (!damageSource.canHarmInCreative()) {
                @Nullable EntityLivingBase hurter = evt.getEntityLiving();
                if (RandomUtil.percentageChance((AttributesUtil.getAttributeValue(hurter, IMMUNE_DAMAGE) - 1) * 100)) {
                    evt.setCanceled(true);
                }
            }
        }
    }

}
