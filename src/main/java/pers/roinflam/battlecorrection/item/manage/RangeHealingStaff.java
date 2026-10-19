package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 范围治疗权杖
 * 右键点击生物，使周围所有生物完全恢复生命
 */
@Mod.EventBusSubscriber
public class RangeHealingStaff extends ItemStaff {

    public RangeHealingStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer playerIn, EntityLivingBase target, @Nonnull EnumHand hand) {
        // 副手不处理，交还给原版
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        if (!playerIn.world.isRemote) {
            // 以目标的碰撞箱为中心，获取周围 64 格内所有活着的生物
            AxisAlignedBB searchBox = target.getEntityBoundingBox().grow(64.0D);
            List<EntityLivingBase> nearbyEntities = playerIn.world.getEntitiesWithinAABB(
                    EntityLivingBase.class,
                    searchBox,
                    EntitySelectors.IS_ALIVE
            );

            // 治疗所有生物
            for (@Nonnull EntityLivingBase entityLivingBase : nearbyEntities) {
                entityLivingBase.setHealth(entityLivingBase.getMaxHealth());
            }

            // 设置冷却
            playerIn.getCooldownTracker().setCooldown(this, 20);
        }
        return true;
    }
}
