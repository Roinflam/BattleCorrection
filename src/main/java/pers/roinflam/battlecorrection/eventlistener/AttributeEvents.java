package pers.roinflam.battlecorrection.eventlistener;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigLoader;

import javax.annotation.Nonnull;
import java.util.UUID;

@Mod.EventBusSubscriber
public class AttributeEvents {
    public static final UUID ID = UUID.fromString("f1322889-6166-78d0-d738-9b12ced95231");
    public static final String NAME = "battlecorrection.maxhealth";

    @SubscribeEvent
    public static void onEntityJoinWorld(@Nonnull EntityJoinWorldEvent evt) {
        if (!evt.getWorld().isRemote && evt.getEntity() instanceof EntityLivingBase && !(evt.getEntity() instanceof EntityPlayer)) {
            EntityLivingBase entityLivingBase = (EntityLivingBase) evt.getEntity();
            @Nonnull IAttributeInstance attributeInstance = entityLivingBase.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
            if (ConfigLoader.extraMaxHealth != 0) {
                float maxHealth = entityLivingBase.getMaxHealth();
                attributeInstance.removeModifier(ID);
                attributeInstance.applyModifier(new AttributeModifier(ID, NAME, ConfigLoader.extraMaxHealth, 2));
                entityLivingBase.heal(entityLivingBase.getMaxHealth() - maxHealth);
                entityLivingBase.setHealth(Math.min(entityLivingBase.getHealth(), entityLivingBase.getMaxHealth()));
            } else {
                attributeInstance.removeModifier(ID);
            }
        }
    }
}