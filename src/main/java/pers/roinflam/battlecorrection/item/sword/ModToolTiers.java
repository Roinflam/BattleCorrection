package pers.roinflam.battlecorrection.item.sword;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * 自定义工具材质等级
 */
public enum ModToolTiers implements Tier {

    /**
     * 基础材质 - 对应BaseSword
     * 面板攻击伤害: 1(玩家) + 3(剑) + 6(材质) = 10
     * 耐久: 1000
     * 附魔等级: 50
     */
    BASE(3, 1000, 100.0F, 6.0F, 50, () -> Ingredient.EMPTY),

    /**
     * 进阶材质 - 对应AdvancedSword
     * 面板攻击伤害: 1(玩家) + 3(剑) + 96(材质) = 100
     * 耐久: 10000
     * 附魔等级: 100
     */
    ADVANCED(3, 10000, 200.0F, 96.0F, 100, () -> Ingredient.EMPTY),

    /**
     * 大师材质 - 对应MasterSword
     * 面板攻击伤害: 1(玩家) + 3(剑) + 996(材质) = 1000
     * 耐久: 100000
     * 附魔等级: 150
     */
    MASTER(3, 100000, 300.0F, 996.0F, 150, () -> Ingredient.EMPTY);

    private final int level;
    private final int uses;
    private final float speed;
    private final float damage;
    private final int enchantmentValue;
    private final Supplier<Ingredient> repairIngredient;

    ModToolTiers(int level, int uses, float speed, float damage, int enchantmentValue, Supplier<Ingredient> repairIngredient) {
        this.level = level;
        this.uses = uses;
        this.speed = speed;
        this.damage = damage;
        this.enchantmentValue = enchantmentValue;
        this.repairIngredient = repairIngredient;
    }

    @Override
    public int getUses() {
        return this.uses;
    }

    @Override
    public float getSpeed() {
        return this.speed;
    }

    @Override
    public float getAttackDamageBonus() {
        return this.damage;
    }

    /**
     * 挖掘等级（3 = 钻石）。1.21.1 的 {@link Tier} 不再有这个方法，等级改由 {@link #getIncorrectBlocksForDrops()} 表达
     */
    public int getLevel() {
        return this.level;
    }

    @Override
    public int getEnchantmentValue() {
        return this.enchantmentValue;
    }

    @Override
    @Nonnull
    public Ingredient getRepairIngredient() {
        return this.repairIngredient.get();
    }

    /**
     * 挖不出掉落的方块（1.21.1 取代了 1.20.1 的 {@code getTag()} 返回 {@code NEEDS_DIAMOND_TOOL}）：和钻石工具一样
     */
    @Override
    @Nonnull
    public TagKey<Block> getIncorrectBlocksForDrops() {
        return BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
    }
}
