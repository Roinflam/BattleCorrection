package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class SacrificialStaff extends ItemStaff {

    public SacrificialStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer playerIn, EntityLivingBase target, @Nonnull EnumHand hand) {
        if (hand.equals(EnumHand.MAIN_HAND) && !playerIn.world.isRemote) {
            System.out.println(stack.getItem().getRegistryName() + ":" + stack.getItem().getUnlocalizedName());
            DamageSource damageSource = EntityDamageSource.causePlayerDamage(playerIn);
            target.attackEntityFrom(damageSource, target.getMaxHealth() * 100);
            if (target.isEntityAlive()) {
                target.onDeath(damageSource);
                target.setHealth(0);
            }
            playerIn.getCooldownTracker().setCooldown(this, 20);
        }
        return true;
    }
}
