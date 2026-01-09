// 文件：AdvancedSword.java
// 路径：src/main/java/pers/roinflam/battlecorrection/item/sword/AdvancedSword.java
package pers.roinflam.battlecorrection.item.sword;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

import javax.annotation.Nonnull;

/**
 * 进阶之剑
 * 攻击伤害: 99 (96基础 + 3剑基础)
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