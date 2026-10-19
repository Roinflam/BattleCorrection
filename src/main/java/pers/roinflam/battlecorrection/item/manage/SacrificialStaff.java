package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;

/**
 * 献祭权杖
 * 右键点击生物使其立即死亡
 */
@Mod.EventBusSubscriber
public class SacrificialStaff extends ItemStaff {

    public SacrificialStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
    }

    /**
     * 右键生物：以玩家攻击的名义击杀目标（保留击杀归属和掉落），被免疫时强制死亡
     */
    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer playerIn, EntityLivingBase target, @Nonnull EnumHand hand) {
        // 副手不处理，交还给原版
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        if (!playerIn.world.isRemote) {
            kill(target, DamageSource.causePlayerDamage(playerIn));

            // 设置冷却
            playerIn.getCooldownTracker().setCooldown(this, 20);
        }
        return true;
    }
}
