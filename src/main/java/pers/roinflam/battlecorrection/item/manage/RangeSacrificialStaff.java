package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.item.ItemStaff;
import pers.roinflam.battlecorrection.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

@Mod.EventBusSubscriber
public class RangeSacrificialStaff extends ItemStaff {

    public RangeSacrificialStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
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
