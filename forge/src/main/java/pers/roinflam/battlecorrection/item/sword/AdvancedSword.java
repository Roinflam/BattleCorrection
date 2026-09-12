package pers.roinflam.battlecorrection.item.sword;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

import javax.annotation.Nonnull;

/**
 * 进阶之剑
 * 面板攻击伤害: 100（玩家基础1 + 剑3 + 材质96）
 * 耐久: 10000
 */
public class AdvancedSword extends SwordItem {

    public AdvancedSword(@Nonnull Tier tier, int attackDamageModifier, float attackSpeedModifier,
                         @Nonnull Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
    }

    @Override
    @Nonnull
    public Rarity getRarity(@Nonnull ItemStack stack) {
        return Rarity.RARE;
    }
}
