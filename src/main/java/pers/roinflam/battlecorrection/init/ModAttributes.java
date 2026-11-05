package pers.roinflam.battlecorrection.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import pers.roinflam.battlecorrection.utils.Reference;

/**
 * 模组自定义属性注册类
 * 使用DeferredRegister进行延迟注册
 */
public class ModAttributes {

    /**
     * 属性延迟注册器
     */
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, Reference.MOD_ID);

    // 伤害增强属性

    /**
     * 魔法伤害加成
     */
    public static final DeferredHolder<Attribute, Attribute> MAGIC_DAMAGE = ATTRIBUTES.register(
            "magic_damage",
            () -> new RangedAttribute("attribute.name.battlecorrection.magic_damage", 0.0D, 0.0D, Double.MAX_VALUE)
                    .setSyncable(true)
    );

    /**
     * 箭矢伤害加成
     */
    public static final DeferredHolder<Attribute, Attribute> ARROW_DAMAGE = ATTRIBUTES.register(
            "arrow_damage",
            () -> new RangedAttribute("attribute.name.battlecorrection.arrow_damage", 0.0D, 0.0D, Double.MAX_VALUE)
                    .setSyncable(true)
    );

    /**
     * 弹射物伤害加成
     */
    public static final DeferredHolder<Attribute, Attribute> PROJECTILE_DAMAGE = ATTRIBUTES.register(
            "projectile_damage",
            () -> new RangedAttribute("attribute.name.battlecorrection.projectile_damage", 0.0D, 0.0D, Double.MAX_VALUE)
                    .setSyncable(true)
    );

    // 防御与减伤属性

    /**
     * 伤害免疫几率（默认1.0代表0%，每+0.1 = +10%闪避）
     */
    public static final DeferredHolder<Attribute, Attribute> IMMUNE_DAMAGE = ATTRIBUTES.register(
            "immune_damage",
            () -> new RangedAttribute("attribute.name.battlecorrection.immune_damage", 1.0D, 1.0D, Double.MAX_VALUE)
                    .setSyncable(true)
    );

    /**
     * 忽略固定伤害值
     */
    public static final DeferredHolder<Attribute, Attribute> IGNORE_DAMAGE = ATTRIBUTES.register(
            "ignore_damage",
            () -> new RangedAttribute("attribute.name.battlecorrection.ignore_damage", 0.0D, 0.0D, Double.MAX_VALUE)
                    .setSyncable(true)
    );

    /**
     * 减少摔落伤害
     */
    public static final DeferredHolder<Attribute, Attribute> REDUCED_FALL_DAMAGE = ATTRIBUTES.register(
            "reduced_fall_damage",
            () -> new RangedAttribute("attribute.name.battlecorrection.reduced_fall_damage", 0.0D, 0.0D, Double.MAX_VALUE)
                    .setSyncable(true)
    );

    // 生命恢复属性

    /**
     * 治疗恢复倍率（默认1.0 = 100%）
     */
    public static final DeferredHolder<Attribute, Attribute> RESTORE_HEAL = ATTRIBUTES.register(
            "restore_heal",
            () -> new RangedAttribute("attribute.name.battlecorrection.restore_heal", 1.0D, 0.0D, Double.MAX_VALUE)
                    .setSyncable(true)
    );

    /**
     * 近战吸血倍率（默认1.0表示无吸血，额外加成）
     */
    public static final DeferredHolder<Attribute, Attribute> BLOODTHIRSTY = ATTRIBUTES.register(
            "bloodthirsty",
            () -> new RangedAttribute("attribute.name.battlecorrection.bloodthirsty", 1.0D, 1.0D, Double.MAX_VALUE)
                    .setSyncable(true)
    );

    /**
     * 全能吸血倍率（默认1.0表示无吸血，额外加成）
     */
    public static final DeferredHolder<Attribute, Attribute> ALMIGHTY_BLOODTHIRSTY = ATTRIBUTES.register(
            "almighty_bloodthirsty",
            () -> new RangedAttribute("attribute.name.battlecorrection.almighty_bloodthirsty", 1.0D, 1.0D, Double.MAX_VALUE)
                    .setSyncable(true)
    );

    // 速度与机动性属性

    /**
     * 拉弓速度倍率（默认1.0 = 正常速度）
     */
    public static final DeferredHolder<Attribute, Attribute> BOW_SPEED = ATTRIBUTES.register(
            "bow_speed",
            () -> new RangedAttribute("attribute.name.battlecorrection.bow_speed", 1.0D, 0.0D, Double.MAX_VALUE)
                    .setSyncable(true)
    );

    /**
     * 物品使用速度倍率（默认1.0 = 正常速度）
     */
    public static final DeferredHolder<Attribute, Attribute> PREPARATION_SPEED = ATTRIBUTES.register(
            "preparation_speed",
            () -> new RangedAttribute("attribute.name.battlecorrection.preparation_speed", 1.0D, 0.0D, Double.MAX_VALUE)
                    .setSyncable(true)
    );

    /**
     * 跳跃提升（额外跳跃高度）
     */
    public static final DeferredHolder<Attribute, Attribute> JUMP_LIFT = ATTRIBUTES.register(
            "jump_lift",
            () -> new RangedAttribute("attribute.name.battlecorrection.jump_lift", 0.0D, 0.0D, Double.MAX_VALUE)
                    .setSyncable(true)
    );

    // 暴击系统属性

    /**
     * 原版暴击伤害加成（默认1.0表示无额外加成）
     */
    public static final DeferredHolder<Attribute, Attribute> VANILLA_CRITICAL_HIT_DAMAGE = ATTRIBUTES.register(
            "vanilla_critical_hit_damage",
            () -> new RangedAttribute("attribute.name.battlecorrection.vanilla_critical_hit_damage", 1.0D, 1.0D, Double.MAX_VALUE)
                    .setSyncable(true)
    );

    /**
     * 自定义暴击几率（小数，0.0-1.0+，支持溢出转化）
     */
    public static final DeferredHolder<Attribute, Attribute> CUSTOM_CRITICAL_CHANCE = ATTRIBUTES.register(
            "custom_critical_chance",
            () -> new RangedAttribute("attribute.name.battlecorrection.custom_critical_chance", 0.0D, 0.0D, Double.MAX_VALUE)
                    .setSyncable(true)
    );

    /**
     * 自定义暴击伤害倍率加成（默认0，与配置值相加，最终倍率不低于1.0）
     */
    public static final DeferredHolder<Attribute, Attribute> CUSTOM_CRITICAL_DAMAGE = ATTRIBUTES.register(
            "custom_critical_damage",
            () -> new RangedAttribute("attribute.name.battlecorrection.custom_critical_damage", 0.0D, 0.0D, Double.MAX_VALUE)
                    .setSyncable(true)
    );
}
