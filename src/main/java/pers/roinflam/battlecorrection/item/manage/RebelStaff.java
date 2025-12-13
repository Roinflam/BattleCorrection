package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

@Mod.EventBusSubscriber
public class RebelStaff extends ItemStaff {

    public RebelStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
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
                    playerIn.getCooldownTracker().setCooldown(this, 20);
                    return true;
                }
            }
        }
        return false;
    }

}
