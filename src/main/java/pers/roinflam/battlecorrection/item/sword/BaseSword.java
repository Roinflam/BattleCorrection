package pers.roinflam.battlecorrection.item.sword;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraftforge.common.util.EnumHelper;
import pers.roinflam.battlecorrection.init.ModItems;
import pers.roinflam.battlecorrection.utils.IHasModel;
import pers.roinflam.battlecorrection.utils.util.ItemUtil;

/**
 * 基础之剑
 * 面板攻击伤害: 10（玩家基础1 + 剑3 + 材质6）
 * 耐久: 1000
 * <p>
 * 材质没有修复材料，不能在铁砧里用材料修复。
 */
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
