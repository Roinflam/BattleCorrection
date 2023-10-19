package pers.roinflam.battlecorrection.item.sword;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraftforge.common.util.EnumHelper;
import pers.roinflam.battlecorrection.init.BattleCorrectionItems;
import pers.roinflam.battlecorrection.utils.IHasModel;
import pers.roinflam.battlecorrection.utils.util.ItemUtil;

public class MasterSword extends ItemSword implements IHasModel {
    public static final ToolMaterial MASTER = EnumHelper.addToolMaterial("battlecorrection_master", 3, 100000, 300.0f, 996f, 150);

    public MasterSword(String name, CreativeTabs creativeTabs) {
        super(MASTER);
        ItemUtil.registerItem(this, name, creativeTabs);

        BattleCorrectionItems.ITEMS.add(this);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

}
