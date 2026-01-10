// 文件：ConfigAttribute.java
// 路径：forge/src/main/java/pers/roinflam/battlecorrection/config/ConfigAttribute.java
package pers.roinflam.battlecorrection.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 属性配置类 - Attribute Configuration Class (1.20.1)
 * 包含所有RPG属性相关的配置选项
 */
public class ConfigAttribute {

    public static final ForgeConfigSpec SPEC;

    // ===== 伤害加成 =====
    public static final ForgeConfigSpec.DoubleValue MAGIC_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue ARROW_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue PROJECTILE_DAMAGE;

    // ===== 治疗与吸血 =====
    public static final ForgeConfigSpec.DoubleValue RESTORE_HEAL;
    public static final ForgeConfigSpec.DoubleValue BLOODTHIRSTY;
    public static final ForgeConfigSpec.DoubleValue ALMIGHTY_BLOODTHIRSTY;

    // ===== 防御属性 =====
    public static final ForgeConfigSpec.DoubleValue IMMUNE_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue IGNORE_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue REDUCED_FALL_DAMAGE;

    // ===== 速度属性 =====
    public static final ForgeConfigSpec.DoubleValue BOW_SPEED;
    public static final ForgeConfigSpec.DoubleValue PREPARATION_SPEED;
    public static final ForgeConfigSpec.DoubleValue JUMP_LIFT;

    // ===== 暴击系统 =====
    public static final ForgeConfigSpec.DoubleValue VANILLA_CRITICAL_HIT_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue CUSTOM_CRITICAL_CHANCE;
    public static final ForgeConfigSpec.DoubleValue CUSTOM_CRITICAL_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue CRITICAL_OVERFLOW_CONVERSION;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        // ═══════════════════════════════════════════════════════════════
        // 伤害加成
        // ═══════════════════════════════════════════════════════════════
        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "Damage Bonuses - 伤害加成",
                "═══════════════════════════════════════════════════════════════"
        ).push("damage");

        MAGIC_DAMAGE = builder
                .comment(
                        "[EN] Global Magic Damage Bonus (Flat Amount)",
                        "     5 = +5 damage to all magic attacks",
                        "[中文] 全局魔法伤害加成(固定值)",
                        "     5 = 所有魔法攻击+5伤害"
                )
                .defineInRange("magicDamage", 0.0D, 0.0D, Double.MAX_VALUE);

        ARROW_DAMAGE = builder
                .comment(
                        "[EN] Global Arrow Damage Bonus (Flat Amount)",
                        "[中文] 全局箭矢伤害加成(固定值)"
                )
                .defineInRange("arrowDamage", 0.0D, 0.0D, Double.MAX_VALUE);

        PROJECTILE_DAMAGE = builder
                .comment(
                        "[EN] Global Projectile Damage Bonus (Flat Amount, non-arrow)",
                        "[中文] 全局弹射物伤害加成(固定值，非箭矢)"
                )
                .defineInRange("projectileDamage", 0.0D, 0.0D, Double.MAX_VALUE);

        builder.pop();

        // ═══════════════════════════════════════════════════════════════
        // 治疗与吸血
        // ═══════════════════════════════════════════════════════════════
        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "Healing & Lifesteal - 治疗与吸血",
                "═══════════════════════════════════════════════════════════════"
        ).push("healing");

        RESTORE_HEAL = builder
                .comment(
                        "[EN] Healing Received Multiplier",
                        "     1.0 = Normal healing (default), 2.0 = 200% healing",
                        "[中文] 受到的治疗倍率",
                        "     1.0 = 正常治疗(默认), 2.0 = 200%治疗"
                )
                .defineInRange("restoreHeal", 1.0D, 0.0D, Double.MAX_VALUE);

        BLOODTHIRSTY = builder
                .comment(
                        "[EN] Melee Lifesteal Ratio (requires FULL attack charge)",
                        "     0.2 = Restore 20% of melee damage as health",
                        "[中文] 近战吸血比例(需要完全蓄力)",
                        "     0.2 = 恢复20%近战伤害为生命"
                )
                .defineInRange("bloodthirsty", 0.0D, 0.0D, Double.MAX_VALUE);

        ALMIGHTY_BLOODTHIRSTY = builder
                .comment(
                        "[EN] Universal Lifesteal Ratio (All Damage Types)",
                        "     Works with melee, arrows, projectiles, and magic",
                        "[中文] 全能吸血比例(所有伤害类型)",
                        "     对近战、箭矢、弹射物和魔法伤害有效"
                )
                .defineInRange("almightyBloodthirsty", 0.0D, 0.0D, Double.MAX_VALUE);

        builder.pop();

        // ═══════════════════════════════════════════════════════════════
        // 防御属性
        // ═══════════════════════════════════════════════════════════════
        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "Defense Attributes - 防御属性",
                "═══════════════════════════════════════════════════════════════"
        ).push("defense");

        IMMUNE_DAMAGE = builder
                .comment(
                        "[EN] Damage Immunity Chance (Percentage, 0-100)",
                        "     25 = 25% chance to dodge attacks",
                        "[中文] 伤害免疫几率(百分比，0-100)",
                        "     25 = 25%几率闪避攻击"
                )
                .defineInRange("immuneDamage", 0.0D, 0.0D, 100.0D);

        IGNORE_DAMAGE = builder
                .comment(
                        "[EN] Flat Damage Reduction (All Sources)",
                        "     5 = Block 5 damage or reduce damage by 5",
                        "[中文] 固定伤害减免(所有来源)",
                        "     5 = 格挡5点伤害或减少5点伤害"
                )
                .defineInRange("ignoreDamage", 0.0D, 0.0D, Double.MAX_VALUE);

        REDUCED_FALL_DAMAGE = builder
                .comment(
                        "[EN] Fall Damage Reduction (Flat Amount)",
                        "     10 = Ignore ~15 blocks of fall",
                        "[中文] 摔落伤害减免(固定值)",
                        "     10 = 忽略约15格的坠落"
                )
                .defineInRange("reducedFallDamage", 0.0D, 0.0D, Double.MAX_VALUE);

        builder.pop();

        // ═══════════════════════════════════════════════════════════════
        // 速度属性
        // ═══════════════════════════════════════════════════════════════
        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "Speed Attributes - 速度属性",
                "═══════════════════════════════════════════════════════════════"
        ).push("speed");

        BOW_SPEED = builder
                .comment(
                        "[EN] Bow Draw Speed Multiplier",
                        "     1.0 = Normal speed (default), 2.0 = Twice as fast, 0.5 = Half speed, 0.0 = Cannot draw",
                        "[中文] 拉弓速度倍率",
                        "     1.0 = 正常速度(默认), 2.0 = 快一倍, 0.5 = 减半速度, 0.0 = 无法拉弓"
                )
                .defineInRange("bowSpeed", 1.0D, 0.0D, Double.MAX_VALUE);

        PREPARATION_SPEED = builder
                .comment(
                        "[EN] Item Use Speed Multiplier (Food, Potions, Shields)",
                        "     1.0 = Normal speed (default), 2.0 = Twice as fast, 0.5 = Half speed, 0.0 = Cannot use",
                        "[中文] 物品使用速度倍率(食物、药水、盾牌)",
                        "     1.0 = 正常速度(默认), 2.0 = 快一倍, 0.5 = 减半速度, 0.0 = 无法使用"
                )
                .defineInRange("preparationSpeed", 1.0D, 0.0D, Double.MAX_VALUE);

        JUMP_LIFT = builder
                .comment(
                        "[EN] Jump Height Boost",
                        "     1 = Roughly +1 block jump height",
                        "[中文] 跳跃高度提升",
                        "     1 = 大约+1格跳跃高度"
                )
                .defineInRange("jumpLift", 0.0D, 0.0D, Double.MAX_VALUE);

        builder.pop();

        // ═══════════════════════════════════════════════════════════════
        // 暴击系统
        // ═══════════════════════════════════════════════════════════════
        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "Critical Hit System - 暴击系统",
                "═══════════════════════════════════════════════════════════════"
        ).push("critical");

        VANILLA_CRITICAL_HIT_DAMAGE = builder
                .comment(
                        "[EN] Vanilla Critical Hit Damage Bonus (Fall Attack Only)",
                        "     0.5 = 150% damage (vanilla default), 1.0 = 200% damage",
                        "[中文] 原版暴击伤害加成(仅下坠攻击)",
                        "     0.5 = 150%伤害(原版默认), 1.0 = 200%伤害"
                )
                .defineInRange("vanillaCriticalHitDamage", 0.5D, 0.0D, Double.MAX_VALUE);

        CUSTOM_CRITICAL_CHANCE = builder
                .comment(
                        "[EN] Custom Critical Hit Chance (Decimal, supports overflow)",
                        "     0.5 = 50% crit chance, 1.5 = 100% crit + overflow to damage",
                        "[中文] 自定义暴击几率(小数，支持溢出转化)",
                        "     0.5 = 50%暴击几率, 1.5 = 100%暴击 + 溢出转化为暴击伤害"
                )
                .defineInRange("customCriticalChance", 0.0D, 0.0D, Double.MAX_VALUE);

        CUSTOM_CRITICAL_DAMAGE = builder
                .comment(
                        "[EN] Custom Critical Hit Damage Multiplier (minimum 1.0)",
                        "     2.0 = 200% damage (recommended default)",
                        "[中文] 自定义暴击伤害倍率(最小1.0)",
                        "     2.0 = 200%伤害(推荐默认值)"
                )
                .defineInRange("customCriticalDamage", 2.0D, 1.0D, Double.MAX_VALUE);

        CRITICAL_OVERFLOW_CONVERSION = builder
                .comment(
                        "[EN] Critical Chance Overflow to Damage Conversion Ratio",
                        "     1.0 = 100% conversion (0.5 overflow = +0.5 crit damage)",
                        "[中文] 暴击率溢出转暴击伤害转化比例",
                        "     1.0 = 100%转化(0.5溢出 = +0.5暴击伤害)"
                )
                .defineInRange("criticalOverflowConversion", 1.0D, 0.0D, Double.MAX_VALUE);

        builder.pop();

        SPEC = builder.build();
    }
}