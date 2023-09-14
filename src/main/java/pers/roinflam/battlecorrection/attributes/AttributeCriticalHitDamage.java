package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

@Mod.EventBusSubscriber
public class AttributeCriticalHitDamage {
    public static final UUID ID = UUID.fromString("d4b4598c-3bf3-6c67-f0d1-419da33d23aa");
    public static final String NAME = "battlecorrection.criticalHitDamage";

    public static final IAttribute CRITICAL_HIT_DAMAGE = (new RangedAttribute(null, NAME, 1, 1, Float.MAX_VALUE)).setDescription("CriticalHitDamage");

    @SubscribeEvent
    public static void onCriticalHit(@Nonnull CriticalHitEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            @Nullable EntityLivingBase attacker = evt.getEntityLiving();
            evt.setDamageModifier(evt.getDamageModifier() + ((AttributesUtil.getAttributeValue(attacker, CRITICAL_HIT_DAMAGE) - 1) + ConfigAttribute.criticalHitDamage));
        }
    }

}
