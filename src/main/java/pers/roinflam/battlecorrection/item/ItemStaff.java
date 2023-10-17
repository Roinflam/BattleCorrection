package pers.roinflam.battlecorrection.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import pers.roinflam.battlecorrection.init.BattleCorrectionItems;
import pers.roinflam.battlecorrection.utils.IHasModel;
import pers.roinflam.battlecorrection.utils.util.ItemUtil;

import javax.annotation.Nonnull;

public abstract class ItemStaff extends Item implements IHasModel {

    public ItemStaff(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        ItemUtil.registerItem(this, name, creativeTabs);

        setMaxStackSize(1);

        BattleCorrectionItems.ITEMS.add(this);
    }

    @Nonnull
    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }
}
