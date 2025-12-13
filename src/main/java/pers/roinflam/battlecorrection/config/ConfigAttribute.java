// 文件：ConfigAttribute.java
package pers.roinflam.battlecorrection.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.utils.Reference;

/**
 * 属性配置类 - Attribute Configuration Class
 * 包含所有RPG属性相关的配置选项
 * Contains all RPG attribute-related configuration options
 */
@Mod.EventBusSubscriber
@Config(modid = Reference.MOD_ID, category = "Attribute")
@Config.LangKey("config." + Reference.MOD_ID + ".attribute")
public final class ConfigAttribute {

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Global Magic Damage Bonus (Flat Amount)",
            "     Flat damage amount added to all magic attacks",
            "     Affects: Damage potions, lingering potions, modded magic weapons",
            "     This value is directly ADDED to the damage:",
            "     • 0 = No bonus damage (default)",
            "     • 5 = +5 damage to all magic attacks",
            "     • 10 = +10 damage to all magic attacks",
            "     Stacks with item-specific magic damage attributes",
            "",
            "[中文] 全局魔法伤害加成(固定值)",
            "     所有魔法攻击增加的固定伤害值",
            "     影响: 伤害药水、滞留药水、模组魔法武器",
            "     此值直接叠加到伤害上:",
            "     • 0 = 无加成伤害(默认)",
            "     • 5 = 所有魔法攻击+5伤害",
            "     • 10 = 所有魔法攻击+10伤害",
            "     与物品特定魔法伤害属性叠加",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.magicDamage")
    @Config.RangeDouble(min = 0)
    public static float magicDamage = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Global Arrow Damage Bonus (Flat Amount)",
            "     Flat damage amount added to all arrow attacks",
            "     Affects: Bows, crossbows, and any arrow-based weapons",
            "     This value is directly ADDED to the damage:",
            "     • 0 = No bonus damage (default)",
            "     • 5 = +5 damage to all arrow attacks",
            "     • 10 = +10 damage to all arrow attacks",
            "     Stacks with item-specific arrow damage attributes",
            "     Also stacks with Power enchantment and bow charge",
            "",
            "[中文] 全局箭矢伤害加成(固定值)",
            "     所有箭矢攻击增加的固定伤害值",
            "     影响: 弓、弩和任何基于箭矢的武器",
            "     此值直接叠加到伤害上:",
            "     • 0 = 无加成伤害(默认)",
            "     • 5 = 所有箭矢攻击+5伤害",
            "     • 10 = 所有箭矢攻击+10伤害",
            "     与物品特定箭矢伤害属性叠加",
            "     也与力量附魔和弓蓄力叠加",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.arrowDamage")
    @Config.RangeDouble(min = 0)
    public static float arrowDamage = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Global Projectile Damage Bonus (Flat Amount)",
            "     Flat damage amount added to non-arrow projectile attacks",
            "     Affects: Snowballs, eggs, splash potions, modded projectiles",
            "     Does NOT affect: Arrows (use arrowDamage for that)",
            "     This value is directly ADDED to the damage:",
            "     • 0 = No bonus damage (default)",
            "     • 5 = +5 damage to all projectiles",
            "     • 10 = +10 damage to all projectiles",
            "     Useful for making snowballs or eggs viable weapons",
            "",
            "[中文] 全局弹射物伤害加成(固定值)",
            "     非箭矢弹射物攻击增加的固定伤害值",
            "     影响: 雪球、鸡蛋、喷溅药水、模组弹射物",
            "     不影响: 箭矢(使用arrowDamage调整)",
            "     此值直接叠加到伤害上:",
            "     • 0 = 无加成伤害(默认)",
            "     • 5 = 所有弹射物+5伤害",
            "     • 10 = 所有弹射物+10伤害",
            "     适用于让雪球或鸡蛋成为可用武器",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.projectileDamage")
    @Config.RangeDouble(min = 0)
    public static float projectileDamage = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Healing Received Multiplier",
            "     Multiplier for all healing received by the player",
            "     Affects: Natural regeneration, instant health potions, food healing",
            "     This is a MULTIPLIER, not addition:",
            "     • 0.0 = No healing received (dangerous!)",
            "     • 0.5 = Receive 50% of healing",
            "     • 1.0 = Normal healing (default)",
            "     • 1.5 = Receive 150% of healing",
            "     • 2.0 = Receive 200% of healing (doubled)",
            "     Lower values increase difficulty, higher values make survival easier",
            "",
            "[中文] 受到的治疗倍率",
            "     玩家受到的所有治疗的倍率",
            "     影响: 自然恢复、瞬间治疗药水、食物恢复",
            "     这是倍率，不是加法:",
            "     • 0.0 = 不受到任何治疗(危险!)",
            "     • 0.5 = 受到50%的治疗",
            "     • 1.0 = 正常治疗(默认)",
            "     • 1.5 = 受到150%的治疗",
            "     • 2.0 = 受到200%的治疗(双倍)",
            "     较低的值增加难度，较高的值使生存更容易",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.restoreHeal")
    @Config.RangeDouble(min = 0)
    public static float restoreHeal = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Damage Immunity Chance (Dodge Chance)",
            "     Percentage chance to completely avoid incoming damage",
            "     • 0 = No dodge chance (default)",
            "     • 25 = 25% chance to dodge attacks",
            "     • 50 = 50% chance to dodge attacks",
            "     • 100 = Always dodge all attacks (invincible)",
            "     When triggered, the attack deals NO damage at all",
            "     Works against all damage types except creative mode damage",
            "     Visual/sound effects of the attack still occur",
            "",
            "[中文] 伤害免疫几率(闪避几率)",
            "     完全避免受到伤害的百分比几率",
            "     • 0 = 无闪避几率(默认)",
            "     • 25 = 25%几率闪避攻击",
            "     • 50 = 50%几率闪避攻击",
            "     • 100 = 总是闪避所有攻击(无敌)",
            "     触发时，攻击完全不造成伤害",
            "     对所有伤害类型有效，除了创造模式伤害",
            "     攻击的视觉/音效仍会发生",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.immuneDamage")
    @Config.RangeDouble(min = 0, max = 100)
    public static float immuneDamage = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Bow Draw Speed Multiplier",
            "     Increases how fast bows can be drawn to full charge",
            "     • -0.5 = 50% slower draw speed",
            "     • 0.0 = Normal draw speed (default)",
            "     • 0.5 = 50% faster draw speed",
            "     • 1.0 = 100% faster (twice as fast)",
            "     • 2.0 = 200% faster (three times as fast)",
            "     Does NOT affect arrow damage, only draw speed",
            "     Useful for rapid-fire bow builds",
            "",
            "[中文] 拉弓速度倍率",
            "     提高弓拉满的速度",
            "     • -0.5 = 慢50%",
            "     • 0.0 = 正常速度(默认)",
            "     • 0.5 = 快50%",
            "     • 1.0 = 快100%(两倍速)",
            "     • 2.0 = 快200%(三倍速)",
            "     不影响箭矢伤害，仅影响拉弓速度",
            "     适用于快速射击弓构建",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.bowSpeed")
    @Config.RangeDouble(min = 0)
    public static float bowSpeed = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Item Use Speed Multiplier (Food, Potions, Shields)",
            "     Increases how fast items can be consumed or used",
            "     Affects: Eating food, drinking potions, using shields, etc.",
            "     Does NOT affect: Bows (use bowSpeed for that)",
            "     • -0.5 = 50% slower use speed",
            "     • 0.0 = Normal use speed (default)",
            "     • 0.5 = 50% faster use speed",
            "     • 1.0 = 100% faster (twice as fast)",
            "     • 2.0 = 200% faster (three times as fast)",
            "     Useful for combat builds that rely on quick potion use",
            "",
            "[中文] 物品使用速度倍率(食物、药水、盾牌)",
            "     提高物品消耗或使用的速度",
            "     影响: 吃食物、喝药水、使用盾牌等",
            "     不影响: 弓(使用bowSpeed调整)",
            "     • -0.5 = 慢50%",
            "     • 0.0 = 正常速度(默认)",
            "     • 0.5 = 快50%",
            "     • 1.0 = 快100%(两倍速)",
            "     • 2.0 = 快200%(三倍速)",
            "     适用于依赖快速使用药水的战斗构建",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.preparationSpeed")
    @Config.RangeDouble(min = 0)
    public static float preparationSpeed = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Melee Lifesteal Ratio",
            "     Percentage of melee damage converted to health",
            "     IMPORTANT: Requires FULL attack charge to activate!",
            "     • 0.0 = No lifesteal (default)",
            "     • 0.2 = Restore 20% of melee damage as health",
            "     • 0.5 = Restore 50% of melee damage as health",
            "     • 1.0 = Restore 100% of melee damage as health",
            "     Example: Deal 10 damage with 0.2 lifesteal = heal 2 HP",
            "     Only works with melee attacks, not ranged or magic",
            "     Stacks with almightyBloodthirsty (universal lifesteal)",
            "",
            "[中文] 近战吸血比例",
            "     近战伤害转化为生命值的百分比",
            "     重要: 需要完全蓄力才能触发!",
            "     • 0.0 = 无吸血(默认)",
            "     • 0.2 = 恢复20%近战伤害为生命",
            "     • 0.5 = 恢复50%近战伤害为生命",
            "     • 1.0 = 恢复100%近战伤害为生命",
            "     例: 造成10点伤害，0.2吸血 = 恢复2点生命",
            "     仅对近战攻击有效，不对远程或魔法有效",
            "     与almightyBloodthirsty(全能吸血)叠加",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.bloodthirsty")
    @Config.RangeDouble(min = 0)
    public static float bloodthirsty = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Universal Lifesteal Ratio (All Damage Types)",
            "     Percentage of ALL damage converted to health",
            "     Works with: Melee, arrows, projectiles, and magic damage",
            "     IMPORTANT: Melee attacks still require FULL charge to activate!",
            "     • 0.0 = No lifesteal (default)",
            "     • 0.2 = Restore 20% of damage as health",
            "     • 0.5 = Restore 50% of damage as health",
            "     • 1.0 = Restore 100% of damage as health",
            "     Example: Deal 10 damage (any type) with 0.2 lifesteal = heal 2 HP",
            "     Stacks with bloodthirsty (melee-only lifesteal)",
            "",
            "[中文] 全能吸血比例(所有伤害类型)",
            "     所有伤害转化为生命值的百分比",
            "     对以下有效: 近战、箭矢、弹射物和魔法伤害",
            "     重要: 近战攻击仍需完全蓄力才能触发!",
            "     • 0.0 = 无吸血(默认)",
            "     • 0.2 = 恢复20%伤害为生命",
            "     • 0.5 = 恢复50%伤害为生命",
            "     • 1.0 = 恢复100%伤害为生命",
            "     例: 造成10点伤害(任何类型)，0.2吸血 = 恢复2点生命",
            "     与bloodthirsty(仅近战吸血)叠加",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.almightyBloodthirsty")
    @Config.RangeDouble(min = 0)
    public static float almightyBloodthirsty = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Flat Damage Reduction (All Sources)",
            "     Flat amount of damage ignored from all sources",
            "     How it works:",
            "     • If incoming damage ≤ this value: damage is COMPLETELY NEGATED",
            "     • If incoming damage > this value: damage is REDUCED by this amount",
            "     Examples with 5.0 reduction:",
            "     - Incoming 3 damage → blocked completely (3 ≤ 5)",
            "     - Incoming 10 damage → reduced to 5 damage (10 - 5)",
            "     - Incoming 100 damage → reduced to 95 damage (100 - 5)",
            "     • 0 = No damage reduction (default)",
            "     • 5 = Block 5 damage or reduce damage by 5",
            "     • 10 = Block 10 damage or reduce damage by 10",
            "     Useful for reducing chip damage from weak sources",
            "",
            "[中文] 固定伤害减免(所有来源)",
            "     忽略的固定伤害值(所有来源)",
            "     工作原理:",
            "     • 如果受到伤害 ≤ 此值: 伤害完全抵消",
            "     • 如果受到伤害 > 此值: 伤害减少此值",
            "     以5.0减免为例:",
            "     - 受到3点伤害 → 完全格挡 (3 ≤ 5)",
            "     - 受到10点伤害 → 减少到5点伤害 (10 - 5)",
            "     - 受到100点伤害 → 减少到95点伤害 (100 - 5)",
            "     • 0 = 无伤害减免(默认)",
            "     • 5 = 格挡5点伤害或减少5点伤害",
            "     • 10 = 格挡10点伤害或减少10点伤害",
            "     适用于减少来自弱小来源的零碎伤害",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.ignoreDamage")
    @Config.RangeDouble(min = 0)
    public static float ignoreDamage = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Vanilla Critical Hit Damage Bonus (Fall Attack Only)",
            "     Extra damage bonus for vanilla fall-attack critical hits",
            "     This ONLY affects the vanilla falling critical hit mechanic!",
            "     Vanilla default: 0.5 (deals 150% total damage = 100% base + 50% bonus)",
            "     • 0.0 = 100% damage (no critical bonus, just normal damage)",
            "     • 0.5 = 150% damage (vanilla default)",
            "     • 1.0 = 200% damage (double damage)",
            "     • 2.0 = 300% damage (triple damage)",
            "     This is independent from the custom critical hit system",
            "     Both can trigger at the same time for massive damage!",
            "",
            "[中文] 原版暴击伤害加成(仅下坠攻击)",
            "     原版下坠暴击的额外伤害加成",
            "     这仅影响原版的下坠暴击机制!",
            "     原版默认: 0.5 (造成150%总伤害 = 100%基础 + 50%加成)",
            "     • 0.0 = 100%伤害(无暴击加成，仅正常伤害)",
            "     • 0.5 = 150%伤害(原版默认)",
            "     • 1.0 = 200%伤害(双倍伤害)",
            "     • 2.0 = 300%伤害(三倍伤害)",
            "     这独立于自定义暴击系统",
            "     两者可以同时触发造成巨额伤害!",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.vanillaCriticalHitDamage")
    @Config.RangeDouble(min = 0)
    public static float vanillaCriticalHitDamage = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Custom Critical Hit Chance (%)",
            "     Base percentage chance to land a critical hit (independent from vanilla)",
            "     OVERFLOW SYSTEM - Supports values above 100%!",
            "     How overflow works:",
            "     • Each full 100% = guaranteed +1 critical layer",
            "     • Remaining % = chance for an additional layer",
            "     Examples:",
            "     • 25% = 25% chance for 1 crit layer",
            "     • 100% = guaranteed 1 crit layer",
            "     • 150% = guaranteed 1 layer + 50% chance for 2nd layer",
            "     • 250% = guaranteed 2 layers + 50% chance for 3rd layer",
            "     Each crit layer multiplies damage by customCriticalDamage value!",
            "     Formula: Damage × (CritDamage)^(CritLayers)",
            "     Example: 100 base, 2.0x crit, 2 layers = 100 × 2^2 = 400 damage",
            "",
            "[中文] 自定义暴击率(%)",
            "     打出暴击的基础百分比几率(独立于原版)",
            "     超额系统 - 支持100%以上的数值!",
            "     超额机制:",
            "     • 每整100% = 必定+1暴击层数",
            "     • 剩余% = 额外层数的几率",
            "     示例:",
            "     • 25% = 25%几率触发1层暴击",
            "     • 100% = 必定1层暴击",
            "     • 150% = 必定1层 + 50%几率触发第2层",
            "     • 250% = 必定2层 + 50%几率触发第3层",
            "     每层暴击都会用customCriticalDamage值乘以伤害!",
            "     公式: 伤害 × (暴击伤害)^(暴击层数)",
            "     例: 100基础, 2.0倍暴击, 2层 = 100 × 2^2 = 400伤害",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.customCriticalChance")
    @Config.RangeDouble(min = 0, max = 100)
    public static float customCriticalChance = 2;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Custom Critical Hit Damage Multiplier (Per Layer)",
            "     Damage multiplier applied for EACH critical hit layer",
            "     Minimum value: 1.0 (100% damage, no bonus)",
            "     How multi-layer crits work:",
            "     • Formula: Base Damage × (This Value)^(Number of Crit Layers)",
            "     • 1 layer: damage × multiplier",
            "     • 2 layers: damage × multiplier²",
            "     • 3 layers: damage × multiplier³",
            "     Examples with different settings:",
            "     • 1.0x multiplier: No bonus damage regardless of layers",
            "     • 2.0x multiplier, 1 layer: 100 base → 200 damage (100 × 2¹)",
            "     • 2.0x multiplier, 2 layers: 100 base → 400 damage (100 × 2²)",
            "     • 2.0x multiplier, 3 layers: 100 base → 800 damage (100 × 2³)",
            "     • 3.0x multiplier, 2 layers: 100 base → 900 damage (100 × 3²)",
            "     Default: 2.0 (200% damage per layer, standard double damage)",
            "",
            "[中文] 自定义暴击伤害倍率(每层)",
            "     每个暴击层数应用的伤害倍率",
            "     最小值: 1.0 (100%伤害，无加成)",
            "     多层暴击工作原理:",
            "     • 公式: 基础伤害 × (此值)^(暴击层数)",
            "     • 1层: 伤害 × 倍率",
            "     • 2层: 伤害 × 倍率²",
            "     • 3层: 伤害 × 倍率³",
            "     不同设置示例:",
            "     • 1.0倍: 无论多少层都无加成伤害",
            "     • 2.0倍, 1层: 100基础 → 200伤害 (100 × 2¹)",
            "     • 2.0倍, 2层: 100基础 → 400伤害 (100 × 2²)",
            "     • 2.0倍, 3层: 100基础 → 800伤害 (100 × 2³)",
            "     • 3.0倍, 2层: 100基础 → 900伤害 (100 × 3²)",
            "     默认: 2.0 (每层200%伤害，标准双倍伤害)",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.customCriticalDamage")
    @Config.RangeDouble(min = 1)
    public static float customCriticalDamage = 2;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Jump Height Boost",
            "     Increases the height of jumps",
            "     Reference for comparison:",
            "     • Vanilla Jump Boost I potion effect ≈ 0.1 value",
            "     • Vanilla Jump Boost II potion effect ≈ 0.2 value",
            "     Approximate results:",
            "     • 0.0 = Normal jump height (default)",
            "     • 0.1 = Roughly +1 block jump height",
            "     • 0.2 = Roughly +2 blocks jump height",
            "     • 0.3 = Roughly +3 blocks jump height",
            "     Higher values allow jumping over walls and obstacles",
            "     Can be combined with items that have Jump Boost attributes",
            "",
            "[中文] 跳跃高度提升",
            "     增加跳跃的高度",
            "     对比参考:",
            "     • 原版跳跃提升I药水效果 ≈ 0.1值",
            "     • 原版跳跃提升II药水效果 ≈ 0.2值",
            "     大致效果:",
            "     • 0.0 = 正常跳跃高度(默认)",
            "     • 0.1 = 大约+1格跳跃高度",
            "     • 0.2 = 大约+2格跳跃高度",
            "     • 0.3 = 大约+3格跳跃高度",
            "     较高的值允许跳过墙壁和障碍物",
            "     可以与拥有跳跃提升属性的物品叠加",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.jumpLift")
    @Config.RangeDouble(min = 0)
    public static float jumpLift = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Fall Damage Reduction (Flat Amount)",
            "     Flat amount of fall damage ignored",
            "     How it works:",
            "     • If fall damage ≤ this value: damage is COMPLETELY NEGATED",
            "     • If fall damage > this value: damage is REDUCED by this amount",
            "     Examples with 10.0 reduction:",
            "     - Fall damage 5 → blocked completely (5 ≤ 10)",
            "     - Fall damage 15 → reduced to 5 damage (15 - 10)",
            "     - Fall damage 30 → reduced to 20 damage (30 - 10)",
            "     • 0 = No fall damage reduction (default)",
            "     • 3 = Ignore ~5 blocks of fall (soft landing)",
            "     • 10 = Ignore ~15 blocks of fall",
            "     • 20 = Ignore ~30 blocks of fall (very safe)",
            "     Does NOT affect other damage types, only falling",
            "",
            "[中文] 摔落伤害减免(固定值)",
            "     忽略的固定摔落伤害值",
            "     工作原理:",
            "     • 如果摔落伤害 ≤ 此值: 伤害完全抵消",
            "     • 如果摔落伤害 > 此值: 伤害减少此值",
            "     以10.0减免为例:",
            "     - 摔落伤害5 → 完全格挡 (5 ≤ 10)",
            "     - 摔落伤害15 → 减少到5点伤害 (15 - 10)",
            "     - 摔落伤害30 → 减少到20点伤害 (30 - 10)",
            "     • 0 = 无摔落伤害减免(默认)",
            "     • 3 = 忽略约5格的坠落(软着陆)",
            "     • 10 = 忽略约15格的坠落",
            "     • 20 = 忽略约30格的坠落(非常安全)",
            "     不影响其他伤害类型，仅摔落",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.reducedFallDamage")
    @Config.RangeDouble(min = 0)
    public static float reducedFallDamage = 0;
}