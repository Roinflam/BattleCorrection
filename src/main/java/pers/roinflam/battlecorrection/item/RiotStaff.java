package pers.roinflam.battlecorrection.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
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
public class RiotStaff extends ItemStaff {

    public RiotStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(@Nonnull ItemTooltipEvent evt) {
        ItemStack itemStack = evt.getItemStack();
        Item item = itemStack.getItem();
        if (item instanceof RiotStaff) {
            evt.getToolTip().add(1, TextFormatting.DARK_GRAY + String.valueOf(TextFormatting.ITALIC) + I18n.format("item.riot_staff.tooltip"));
        }
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer playerIn, EntityLivingBase target, @Nonnull EnumHand hand) {
        if (hand.equals(EnumHand.MAIN_HAND) && !playerIn.world.isRemote) {
            if (target instanceof EntityLiving) {
                EntityLiving targetEntityLiving = (EntityLiving) target;
                if (target.isEntityAlive()) {
                    targetEntityLiving.removeActivePotionEffect(RiotStaff.MobEffectRiot.RIOT);
                    targetEntityLiving.removeActivePotionEffect(BrawlStaff.MobEffectRiot.RIOT);
                    targetEntityLiving.removeActivePotionEffect(EliminationStaff.MobEffectRiot.RIOT);
                    targetEntityLiving.addPotionEffect(new PotionEffect(MobEffectRiot.RIOT, 12000, 0));

                    @Nonnull List<EntityLiving> entities = EntityUtil.getNearbyEntities(EntityLiving.class, targetEntityLiving, 64, entityLiving -> !entityLiving.equals(targetEntityLiving));
                    if (entities.size() > 0) {
                        EntityLiving entityLiving = entities.get(RandomUtil.getInt(0, entities.size() - 1));
                        if (entityLiving.isEntityAlive()) {
                            targetEntityLiving.setAttackTarget(entityLiving);
                            entityLiving.setAttackTarget(targetEntityLiving);
                        }
                    }
                    playerIn.getCooldownTracker().setCooldown(this, 20);
                    return true;
                }
            }
        }
        return false;
    }

    static class MobEffectRiot extends PrivateHideBase {
        public static final MobEffectRiot RIOT = new MobEffectRiot(true, 0, "riot_staff_MobEffectRiot");

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
