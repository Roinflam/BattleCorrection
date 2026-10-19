package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import pers.roinflam.battlecorrection.init.ModItems;
import pers.roinflam.battlecorrection.utils.IHasModel;
import pers.roinflam.battlecorrection.utils.util.ItemUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 权杖物品基类
 * 所有管理类权杖的父类
 */
public abstract class ItemStaff extends Item implements IHasModel {
    public ItemStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        ItemUtil.registerItem(this, name, creativeTabs);

        setMaxStackSize(1);

        ModItems.ITEMS.add(this);
    }

    @Nonnull
    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    /**
     * 强制杀死实体
     * 先用大额伤害走正常死亡流程（保留击杀归属和掉落），被免疫或取消时再强制死亡
     *
     * @param livingEntity 要杀死的实体，为 null 时不做任何事
     * @param damageSource 伤害源
     */
    protected static void kill(@Nullable EntityLivingBase livingEntity, @Nonnull DamageSource damageSource) {
        if (livingEntity != null) {
            livingEntity.attackEntityFrom(damageSource, livingEntity.getMaxHealth() * 100);
            if (livingEntity.isEntityAlive()) {
                livingEntity.onDeath(damageSource);
                livingEntity.setHealth(0);
            }
        }
    }
}
