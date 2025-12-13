package pers.roinflam.battlecorrection.item.sword;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraftforge.common.util.EnumHelper;
import pers.roinflam.battlecorrection.init.ModItems;
import pers.roinflam.battlecorrection.utils.IHasModel;
import pers.roinflam.battlecorrection.utils.util.ItemUtil;

public class AdvancedSword extends ItemSword implements IHasModel {
    public static final ToolMaterial ADVANCED = EnumHelper.addToolMaterial("battlecorrection_advanced", 3, 10000, 200.0f, 96f, 100);

    public AdvancedSword(String name, CreativeTabs creativeTabs) {
        super(ADVANCED);
        ItemUtil.registerItem(this, name, creativeTabs);

        ModItems.ITEMS.add(this);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.RARE;
    }

}
