package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

@Mod.EventBusSubscriber
public class AttributeRestoreHeal {
    public static final UUID ID = UUID.fromString("9fbbfcb8-cd9b-6967-a6e1-21aeb72ad066");
    public static final String NAME = "battlecorrection.restoreHeal";

    public static final IAttribute RESTORE_HEAL = (new RangedAttribute(null, NAME, 1, 0, Float.MAX_VALUE)).setDescription("Extra Restore Heal");

    @SubscribeEvent
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            @Nullable EntityLivingBase entityLivingBase = evt.getEntityLiving();
            evt.setAmount(evt.getAmount() * (AttributesUtil.getAttributeValue(entityLivingBase, RESTORE_HEAL) + ConfigAttribute.restoreHeal));
        }
    }

}
