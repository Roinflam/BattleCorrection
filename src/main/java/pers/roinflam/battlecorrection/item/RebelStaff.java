package pers.roinflam.battlecorrection.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.battlecorrection.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

@Mod.EventBusSubscriber
public class RebelStaff extends ItemStaff {

    public RebelStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(@Nonnull ItemTooltipEvent evt) {
        ItemStack itemStack = evt.getItemStack();
        Item item = itemStack.getItem();
        if (item instanceof RebelStaff) {
            evt.getToolTip().add(1, TextFormatting.DARK_GRAY + String.valueOf(TextFormatting.ITALIC) + I18n.format("item.rebel_staff.tooltip"));
        }
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer playerIn, EntityLivingBase target, @Nonnull EnumHand hand) {
        if (hand.equals(EnumHand.MAIN_HAND) && !playerIn.world.isRemote) {
            if (target instanceof EntityLiving) {
                EntityLiving targetEntityLiving = (EntityLiving) target;
                if (target.isEntityAlive()) {
                    @Nonnull List<EntityLiving> entities = EntityUtil.getNearbyEntities(
                            EntityLiving.class,
                            targetEntityLiving,
                            64,
                            entityLiving -> !entityLiving.equals(targetEntityLiving)
                    );
                    for (@Nonnull EntityLiving entityLiving : entities) {
                        entityLiving.setAttackTarget(targetEntityLiving);
                    }
                    return true;
                }
            }
        }
        return false;
    }

}
