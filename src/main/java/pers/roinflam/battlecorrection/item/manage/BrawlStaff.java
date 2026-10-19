package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.init.ModPotions;
import pers.roinflam.battlecorrection.utils.util.MobTargetUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 群殴权杖
 * 右键使用，让附近所有生物互相攻击
 */
@Mod.EventBusSubscriber
public class BrawlStaff extends ItemStaff {

    public BrawlStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
    }

    /**
     * 对空气右键：给周围 64 格内的所有生物挂上群殴效果，并两两随机设为目标
     */
    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World worldIn, @Nonnull EntityPlayer playerIn, @Nonnull EnumHand handIn) {
        if (handIn.equals(EnumHand.MAIN_HAND) && !playerIn.world.isRemote) {
            // 获取周围 64 格内所有活着的 EntityLiving
            AxisAlignedBB searchBox = playerIn.getEntityBoundingBox().grow(MobTargetUtil.SEARCH_RADIUS);
            List<EntityLiving> nearbyMobs = playerIn.world.getEntitiesWithinAABB(EntityLiving.class, searchBox, EntitySelectors.IS_ALIVE);

            for (@Nonnull EntityLiving mob : nearbyMobs) {
                // 移除其他暴动类效果
                mob.removeActivePotionEffect(ModPotions.RIOT);
                mob.removeActivePotionEffect(ModPotions.BRAWL);
                mob.removeActivePotionEffect(ModPotions.ELIMINATION);

                // 添加群殴效果（10 分钟，不显示粒子）
                mob.addPotionEffect(new PotionEffect(ModPotions.BRAWL, 12000, 0, false, false));

                // 从同一批生物里挑一个随机目标（不再为每只生物新建一份候选列表）
                EntityLiving randomTarget = MobTargetUtil.pickRandom(nearbyMobs, mob, false);
                if (randomTarget != null) {
                    MobTargetUtil.setMutualTarget(mob, randomTarget);
                }
            }

            // 设置冷却（以前只有搜到目标时才设）
            playerIn.getCooldownTracker().setCooldown(this, 20);
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, playerIn.getHeldItem(handIn));
        }
        return new ActionResult<ItemStack>(EnumActionResult.PASS, playerIn.getHeldItem(handIn));
    }
}
