package pers.roinflam.battlecorrection.item.sword;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

import javax.annotation.Nonnull;

/**
 * 基础之剑
 * 面板攻击伤害: 10（玩家基础1 + 剑3 + 材质6）
 * 耐久: 1000
 */
public class BaseSword extends SwordItem {

    public BaseSword(@Nonnull Tier tier, int attackDamageModifier, float attackSpeedModifier,
                     @Nonnull Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
    }

    @Override
    @Nonnull
    public Rarity getRarity(@Nonnull ItemStack stack) {
        return Rarity.UNCOMMON;
    }
}
