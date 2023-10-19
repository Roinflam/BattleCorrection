package pers.roinflam.battlecorrection.tabs;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import pers.roinflam.battlecorrection.init.BattleCorrectionItems;

public class BattleCorrectionTab extends CreativeTabs {
    private static final BattleCorrectionTab BATTLE_CORRECTION_TAB = new BattleCorrectionTab();

    private BattleCorrectionTab() {
        super("battlecorrection_tab");
    }

    public static CreativeTabs getTab() {
        return BATTLE_CORRECTION_TAB;
    }

    @Override
    public ItemStack getTabIconItem() {
        return new ItemStack(BattleCorrectionItems.ENEMY_STAFF);
    }
}
