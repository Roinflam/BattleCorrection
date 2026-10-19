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
 * 进阶之剑
 * 面板攻击伤害: 100（玩家基础1 + 剑3 + 材质96）
 * 耐久: 10000
 * <p>
 * 材质没有修复材料，不能在铁砧里用材料修复。
 */
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
