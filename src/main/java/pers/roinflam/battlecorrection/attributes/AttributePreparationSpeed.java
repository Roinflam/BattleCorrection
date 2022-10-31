package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.utils.java.random.RandomUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;
import pers.roinflam.battlecorrection.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

@Mod.EventBusSubscriber
public class AttributePreparationSpeed {
    public static final UUID ID = UUID.fromString("585cc013-a0b6-c8e2-9001-6b55c10caf6d");
    public static final String NAME = "battlecorrection.preparationSpeed";

    public static final IAttribute PREPARATION_SPEED = (new RangedAttribute(null, NAME, 1, 0, Float.MAX_VALUE)).setDescription("Extra Preparation Speed");

    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        EntityLivingBase entityLivingBase = evt.getEntityLiving();
        if (entityLivingBase.isHandActive()) {
            @Nonnull ItemStack itemStack = entityLivingBase.getHeldItem(entityLivingBase.getActiveHand());
            if (!itemStack.isEmpty() && !(itemStack.getItem() instanceof ItemBow)) {
                float speed = AttributesUtil.getAttributeValue(entityLivingBase, PREPARATION_SPEED);
                if (speed > 1) {
                    int number = (int) (speed - 1);
                    for (int i = 0; i < number; i++) {
                        EntityLivingUtil.updateHeld(entityLivingBase);
                    }
                    if (RandomUtil.percentageChance((speed - 1 - number) * 100)) {
                        EntityLivingUtil.updateHeld(entityLivingBase);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingEntityUseItem(@Nonnull LivingEntityUseItemEvent.Tick evt) {
        EntityLivingBase entityLivingBase = evt.getEntityLiving();
        @Nonnull ItemStack itemStack = evt.getItem();
        if (!itemStack.isEmpty() && !(itemStack.getItem() instanceof ItemBow)) {
            float speed = AttributesUtil.getAttributeValue(entityLivingBase, PREPARATION_SPEED);
            if (speed < 1) {
                if (RandomUtil.percentageChance((1 - speed) * 100)) {
                    evt.setDuration(evt.getDuration() + 1);
                }
            }
        }
    }

}
