package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 范围献祭权杖
 * 右键点击生物，使周围所有非玩家生物立即死亡
 */
@Mod.EventBusSubscriber
public class RangeSacrificialStaff extends ItemStaff {

    public RangeSacrificialStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        super(name, creativeTabs);
    }

    /**
     * 右键生物：击杀以目标为中心 64 格内的所有非玩家生物
     */
    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer playerIn, EntityLivingBase target, @Nonnull EnumHand hand) {
        // 副手不处理，交还给原版
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        if (!playerIn.world.isRemote) {
            // 获取周围 64 格内所有活着的非玩家生物
            AxisAlignedBB searchBox = target.getEntityBoundingBox().grow(64.0D);
            List<EntityLivingBase> nearbyEntities = playerIn.world.getEntitiesWithinAABB(
                    EntityLivingBase.class,
                    searchBox,
                    entity -> entity != null && !(entity instanceof EntityPlayer) && entity.isEntityAlive()
            );

            // 创建玩家攻击伤害源，杀死所有非玩家生物
            DamageSource damageSource = DamageSource.causePlayerDamage(playerIn);
            for (@Nonnull EntityLivingBase entityLivingBase : nearbyEntities) {
                kill(entityLivingBase, damageSource);
            }

            // 设置冷却
            playerIn.getCooldownTracker().setCooldown(this, 20);
        }
        return true;
    }
}
