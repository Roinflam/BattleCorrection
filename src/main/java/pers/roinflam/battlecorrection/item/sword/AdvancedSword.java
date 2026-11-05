package pers.roinflam.battlecorrection.item.sword;

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
        // 1.21.1：攻击力 / 攻速改成物品属性组件，稀有度也是组件（原来覆写的 getRarity 没有了）
        super(tier, properties.rarity(Rarity.RARE)
                .attributes(SwordItem.createAttributes(tier, attackDamageModifier, attackSpeedModifier)));
    }
}
