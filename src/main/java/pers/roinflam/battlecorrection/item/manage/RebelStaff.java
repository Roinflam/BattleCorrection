package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.utils.util.MobTargetUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 叛军权杖
 * 右键点击一个生物，使周围所有生物攻击它
 */
@Mod.EventBusSubscriber
public class RebelStaff extends ItemStaff {

    public RebelStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer playerIn, EntityLivingBase target, @Nonnull EnumHand hand) {
        if (hand == EnumHand.MAIN_HAND && target instanceof EntityLiving && target.isEntityAlive()) {
            if (!playerIn.world.isRemote) {
                EntityLiving targetMob = (EntityLiving) target;

                // 获取目标周围 64 格内所有活着的 EntityLiving（不含目标自己）
                AxisAlignedBB searchBox = targetMob.getEntityBoundingBox().grow(MobTargetUtil.SEARCH_RADIUS);
                List<EntityLiving> nearbyMobs = playerIn.world.getEntitiesWithinAABB(
                        EntityLiving.class,
                        searchBox,
                        mob -> mob != null && mob != targetMob && mob.isEntityAlive()
                );

                // 让所有周围的生物攻击目标
                for (@Nonnull EntityLiving mob : nearbyMobs) {
                    mob.setAttackTarget(targetMob);
                }

                // 设置冷却
                playerIn.getCooldownTracker().setCooldown(this, 20);
            }
            return true;
        }
        return false;
    }

}
