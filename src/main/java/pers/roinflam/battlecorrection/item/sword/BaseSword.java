package pers.roinflam.battlecorrection.item.sword;

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
        // 1.21.1：攻击力 / 攻速改成物品属性组件，稀有度也是组件（原来覆写的 getRarity 没有了）
        super(tier, properties.rarity(Rarity.UNCOMMON)
                .attributes(SwordItem.createAttributes(tier, attackDamageModifier, attackSpeedModifier)));
    }
}
