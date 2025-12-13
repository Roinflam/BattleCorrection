package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.base.potion.HiddenPotionBase;
import pers.roinflam.battlecorrection.utils.random.RandomUtil;
import pers.roinflam.battlecorrection.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

@Mod.EventBusSubscriber
public class RiotStaff extends ItemStaff {

    public RiotStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
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

    static class MobEffectRiot extends HiddenPotionBase {
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
