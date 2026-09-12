package pers.roinflam.battlecorrection.item.sword;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

import javax.annotation.Nonnull;

/**
 * 大师之剑
 * 面板攻击伤害: 1000（玩家基础1 + 剑3 + 材质996）
 * 耐久: 100000
 */
public class MasterSword extends SwordItem {

    public MasterSword(@Nonnull Tier tier, int attackDamageModifier, float attackSpeedModifier,
                       @Nonnull Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
    }

    @Override
    @Nonnull
    public Rarity getRarity(@Nonnull ItemStack stack) {
        return Rarity.EPIC;
    }
}
