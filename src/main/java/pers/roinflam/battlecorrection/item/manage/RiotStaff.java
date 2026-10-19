package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.init.ModPotions;
import pers.roinflam.battlecorrection.utils.util.MobTargetUtil;

import javax.annotation.Nonnull;

/**
 * 暴动权杖
 * 右键点击一个生物，使它攻击附近的随机生物（持续效果）
 */
@Mod.EventBusSubscriber
public class RiotStaff extends ItemStaff {

    public RiotStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
    }

    /**
     * 右键生物：给它挂上暴动效果，并立即挑一个随机目标
     */
    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer playerIn, EntityLivingBase target, @Nonnull EnumHand hand) {
        if (hand == EnumHand.MAIN_HAND && target instanceof EntityLiving && target.isEntityAlive()) {
            if (!playerIn.world.isRemote) {
                EntityLiving targetMob = (EntityLiving) target;

                // 移除其他暴动类效果
                targetMob.removeActivePotionEffect(ModPotions.RIOT);
                targetMob.removeActivePotionEffect(ModPotions.BRAWL);
                targetMob.removeActivePotionEffect(ModPotions.ELIMINATION);

                // 添加暴动效果（10 分钟，不显示粒子）
                targetMob.addPotionEffect(new PotionEffect(ModPotions.RIOT, 12000, 0, false, false));

                // 立即设置一次随机目标
                MobTargetUtil.retargetNearby(targetMob, false);

                // 设置冷却
                playerIn.getCooldownTracker().setCooldown(this, 20);
            }
            return true;
        }
        return false;
    }
}
