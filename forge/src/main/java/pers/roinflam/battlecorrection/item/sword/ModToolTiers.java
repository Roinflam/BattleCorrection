// 文件：ModToolTiers.java
// 路径：src/main/java/pers/roinflam/battlecorrection/item/sword/ModToolTiers.java
package pers.roinflam.battlecorrection.item.sword;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * 自定义工具材质等级
 */
public enum ModToolTiers implements Tier {

    /**
     * 基础材质 - 对应BaseSword
     * 攻击伤害: 6 + 3(基础) = 9
     * 耐久: 1000
     * 附魔等级: 50
     */
    BASE(3, 1000, 100.0F, 6.0F, 50, () -> Ingredient.EMPTY),

    /**
     * 进阶材质 - 对应AdvancedSword
     * 攻击伤害: 96 + 3(基础) = 99
     * 耐久: 10000
     * 附魔等级: 100
     */
    ADVANCED(3, 10000, 200.0F, 96.0F, 100, () -> Ingredient.EMPTY),

    /**
     * 大师材质 - 对应MasterSword
     * 攻击伤害: 996 + 3(基础) = 999
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

    @Override
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

    @Override
    @Nullable
    public TagKey<Block> getTag() {
        return BlockTags.NEEDS_DIAMOND_TOOL;
    }
}