package pers.roinflam.battlecorrection.tabs;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import pers.roinflam.battlecorrection.init.ModItems;

public class ModCreativeTab extends CreativeTabs {
    private static final ModCreativeTab BATTLE_CORRECTION_TAB = new ModCreativeTab();

    private ModCreativeTab() {
        super("battlecorrection_tab");
    }

    public static CreativeTabs getTab() {
        return BATTLE_CORRECTION_TAB;
    }

    @Override
    public ItemStack getTabIconItem() {
        return new ItemStack(ModItems.ENEMY_STAFF);
    }
}
