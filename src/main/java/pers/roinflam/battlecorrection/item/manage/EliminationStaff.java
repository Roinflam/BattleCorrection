package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.base.potion.HiddenPotionBase;
import pers.roinflam.battlecorrection.utils.random.RandomUtil;
import pers.roinflam.battlecorrection.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

@Mod.EventBusSubscriber
public class EliminationStaff extends ItemStaff {

    public EliminationStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World worldIn, @Nonnull EntityPlayer playerIn, @Nonnull EnumHand handIn) {
        if (handIn.equals(EnumHand.MAIN_HAND) && !playerIn.world.isRemote) {
            @Nonnull List<EntityLiving> entities = EntityUtil.getNearbyEntities(
                    EntityLiving.class,
                    playerIn,
                    64
            );
            for (@Nonnull EntityLiving entityLiving : entities) {
                entityLiving.removeActivePotionEffect(RiotStaff.MobEffectRiot.RIOT);
                entityLiving.removeActivePotionEffect(BrawlStaff.MobEffectRiot.RIOT);
                entityLiving.removeActivePotionEffect(EliminationStaff.MobEffectRiot.RIOT);
                entityLiving.addPotionEffect(new PotionEffect(MobEffectRiot.RIOT, 12000, 0));

                @Nonnull List<EntityLiving> nearbyEntities = EntityUtil.getNearbyEntities(
                        EntityLiving.class,
                        entityLiving,
                        64,
                        otherEntityLiving -> !otherEntityLiving.equals(entityLiving) && otherEntityLiving.getClass() != entityLiving.getClass()
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

    static class MobEffectRiot extends HiddenPotionBase {
        public static final MobEffectRiot RIOT = new MobEffectRiot(true, 0, "elimination_staff_MobEffectRiot");

        protected MobEffectRiot(boolean isBadEffectIn, int liquidColorIn, @Nonnull String name) {
            super(isBadEffectIn, liquidColorIn, name);
        }

        @Override
        public void performEffect(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {
            if (entityLivingBaseIn instanceof EntityLiving) {
                EntityLiving attacker = (EntityLiving) entityLivingBaseIn;
                if (attacker.world.getTotalWorldTime() % 100 == 0 || (attacker.getAttackTarget() == null || !attacker.getAttackTarget().isEntityAlive())) {
                    @Nonnull List<EntityLiving> entities = EntityUtil.getNearbyEntities(
                            EntityLiving.class,
                            attacker,
                            64,
                            entityLiving -> !entityLiving.equals(attacker) && entityLiving.getClass() != attacker.getClass()
                    );
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
