package pers.roinflam.battlecorrection.item.sword;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraftforge.common.util.EnumHelper;
import pers.roinflam.battlecorrection.init.ModItems;
import pers.roinflam.battlecorrection.utils.IHasModel;
import pers.roinflam.battlecorrection.utils.util.ItemUtil;

public class BaseSword extends ItemSword implements IHasModel {
    public static final ToolMaterial BASE = EnumHelper.addToolMaterial("battlecorrection_base", 3, 1000, 100.0f, 6f, 50);

    public BaseSword(String name, CreativeTabs creativeTabs) {
        super(BASE);
        ItemUtil.registerItem(this, name, creativeTabs);

        ModItems.ITEMS.add(this);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.UNCOMMON;
    }

}
