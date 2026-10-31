package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;

/**
 * 治疗权杖
 * 右键点击生物使其完全恢复生命
 */
@Mod.EventBusSubscriber
public class HealingStaff extends ItemStaff {

    public HealingStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer playerIn, EntityLivingBase target, @Nonnull EnumHand hand) {
        // 副手不处理，交还给原版
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        if (!playerIn.world.isRemote) {
            // 完全恢复目标生命值
            target.setHealth(target.getMaxHealth());

            // 设置冷却
            playerIn.getCooldownTracker().setCooldown(this, 20);
        }
        return true;
    }
}
