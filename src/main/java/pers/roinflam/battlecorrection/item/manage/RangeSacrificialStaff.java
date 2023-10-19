package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.battlecorrection.item.ItemStaff;
import pers.roinflam.battlecorrection.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

@Mod.EventBusSubscriber
public class RangeSacrificialStaff extends ItemStaff {

    public RangeSacrificialStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(@Nonnull ItemTooltipEvent evt) {
        ItemStack itemStack = evt.getItemStack();
        Item item = itemStack.getItem();
        if (item instanceof RangeSacrificialStaff) {
            evt.getToolTip().add(1, TextFormatting.DARK_GRAY + String.valueOf(TextFormatting.ITALIC) + I18n.format("item.range_sacrificial_staff.tooltip"));
        }
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer playerIn, EntityLivingBase target, @Nonnull EnumHand hand) {
        if (hand.equals(EnumHand.MAIN_HAND) && !playerIn.world.isRemote) {
            @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                    EntityLivingBase.class,
                    target,
                    64,
                    entityLiving -> !(entityLiving instanceof EntityPlayer)
            );
            for (@Nonnull EntityLivingBase entityLivingBase : entities) {
                DamageSource damageSource = EntityDamageSource.causePlayerDamage(playerIn);
                entityLivingBase.attackEntityFrom(damageSource, entityLivingBase.getMaxHealth() * 100);
                if (entityLivingBase.isEntityAlive()) {
                    entityLivingBase.onDeath(damageSource);
                    entityLivingBase.setHealth(0);
                }
            }
            playerIn.getCooldownTracker().setCooldown(this, 20);
        }
        return true;
    }
}
