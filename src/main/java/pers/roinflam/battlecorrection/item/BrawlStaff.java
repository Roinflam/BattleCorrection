package pers.roinflam.battlecorrection.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.battlecorrection.base.potion.hide.PrivateHideBase;
import pers.roinflam.battlecorrection.utils.java.random.RandomUtil;
import pers.roinflam.battlecorrection.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

@Mod.EventBusSubscriber
public class BrawlStaff extends ItemStaff {

    public BrawlStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(@Nonnull ItemTooltipEvent evt) {
        ItemStack itemStack = evt.getItemStack();
        Item item = itemStack.getItem();
        if (item instanceof BrawlStaff) {
            evt.getToolTip().add(1, TextFormatting.DARK_GRAY + String.valueOf(TextFormatting.ITALIC) + I18n.format("item.brawl_staff.tooltip"));
        }
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World worldIn, @Nonnull EntityPlayer playerIn, @Nonnull EnumHand handIn) {
        if (handIn.equals(EnumHand.MAIN_HAND) && !playerIn.world.isRemote) {
            @Nonnull List<EntityLiving> entities = EntityUtil.getNearbyEntities(EntityLiving.class, playerIn, 64);
            for (@Nonnull EntityLiving entityLiving : entities) {
                entityLiving.removeActivePotionEffect(RiotStaff.MobEffectRiot.RIOT);
                entityLiving.removeActivePotionEffect(BrawlStaff.MobEffectRiot.RIOT);
                entityLiving.removeActivePotionEffect(EliminationStaff.MobEffectRiot.RIOT);
                entityLiving.addPotionEffect(new PotionEffect(MobEffectRiot.RIOT, 12000, 0));

                @Nonnull List<EntityLiving> nearbyEntities = EntityUtil.getNearbyEntities(
                        EntityLiving.class,
                        entityLiving,
                        64,
                        otherEntityLiving -> !otherEntityLiving.equals(entityLiving)
                );
                if (nearbyEntities.size() > 0) {
                    EntityLiving otherEntityLiving = nearbyEntities.get(RandomUtil.getInt(0, nearbyEntities.size() - 1));
                    if (otherEntityLiving.isEntityAlive()) {
                        entityLiving.setAttackTarget(otherEntityLiving);
                        otherEntityLiving.setAttackTarget(entityLiving);
                    }
                    playerIn.getCooldownTracker().setCooldown(this, 20);
                }
            }
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, playerIn.getHeldItem(handIn));
        }
        return new ActionResult<ItemStack>(EnumActionResult.PASS, playerIn.getHeldItem(handIn));
    }

    static class MobEffectRiot extends PrivateHideBase {
        public static final MobEffectRiot RIOT = new MobEffectRiot(true, 0, "brawl_staff_MobEffectRiot");

        protected MobEffectRiot(boolean isBadEffectIn, int liquidColorIn, @Nonnull String name) {
            super(isBadEffectIn, liquidColorIn, name);
        }

        @Override
        public void performEffect(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {
            if (entityLivingBaseIn instanceof EntityLiving) {
                EntityLiving attacker = (EntityLiving) entityLivingBaseIn;
                if (attacker.world.getTotalWorldTime() % 100 == 0 || (attacker.getAttackTarget() == null || !attacker.getAttackTarget().isEntityAlive())) {
                    @Nonnull List<EntityLiving> entities = EntityUtil.getNearbyEntities(EntityLiving.class, attacker, 64, entityLiving -> !entityLiving.equals(attacker));
                    if (entities.size() > 0) {
                        EntityLiving entityLiving = entities.get(RandomUtil.getInt(0, entities.size() - 1));
                        if (entityLiving.isEntityAlive()) {
                            attacker.setAttackTarget(entityLiving);
                            entityLiving.setAttackTarget(attacker);
                        }
                    }
                }
            }
        }
    }
}
