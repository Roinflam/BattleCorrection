// 文件：ConfigAttribute.java
// 路径：src/main/java/pers/roinflam/battlecorrection/config/ConfigAttribute.java
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
            "     This is a MULTIPLIER applied on top of equipment bonuses:",
            "     • 0.0 = No healing received (dangerous!)",
            "     • 0.5 = Receive 50% of healing",
            "     • 1.0 = Normal healing (default)",
            "     • 1.5 = Receive 150% of healing",
            "     • 2.0 = Receive 200% of healing (doubled)",
            "     Formula: Final healing = Original × ((Equipment - 1.0) + Config)",
            "     Example: Equipment 1.5× + Config 1.0 = (1.5 - 1.0) + 1.0 = 1.5× total",
            "     Lower values increase difficulty, higher values make survival easier",
            "",
            "[中文] 受到的治疗倍率",
            "     玩家受到的所有治疗的倍率",
            "     影响: 自然恢复、瞬间治疗药水、食物恢复",
            "     这是在装备加成基础上应用的倍率:",
            "     • 0.0 = 不受到任何治疗(危险!)",
            "     • 0.5 = 受到50%的治疗",
            "     • 1.0 = 正常治疗(默认)",
            "     • 1.5 = 受到150%的治疗",
            "     • 2.0 = 受到200%的治疗(双倍)",
            "     公式: 最终治疗 = 原始 × ((装备 - 1.0) + 配置)",
            "     例: 装备1.5倍 + 配置1.0 = (1.5 - 1.0) + 1.0 = 1.5倍总计",
            "     较低的值增加难度，较高的值使生存更容易",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.restoreHeal")
    @Config.RangeDouble(min = 0)
    public static float restoreHeal = 1.0f;

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
            "     Formula: Final Speed = Equipment Bonus + Config Value",
            "     IMPORTANT: You need equipment with bow speed bonus OR config > 0 to use bows!",
            "     • 0.0 = Cannot draw bow (default when no equipment bonus)",
            "     • 1.0 = Normal draw speed (1× speed)",
            "     • 2.0 = Twice as fast (2× speed)",
            "     • 3.0 = Three times as fast (3× speed)",
            "     Does NOT affect arrow damage, only draw speed",
            "     Useful for rapid-fire bow builds",
            "",
            "[中文] 拉弓速度倍率",
            "     提高弓拉满的速度",
            "     公式: 最终速度 = 装备加成 + 配置值",
            "     重要: 需要有拉弓速度加成的装备或配置 > 0 才能使用弓!",
            "     • 0.0 = 无法拉弓(默认，无装备加成时)",
            "     • 1.0 = 正常速度(1倍速)",
            "     • 2.0 = 快一倍(2倍速)",
            "     • 3.0 = 快两倍(3倍速)",
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
            "     Formula: Final Speed = Equipment Bonus + Config Value",
            "     IMPORTANT: You need equipment with use speed bonus OR config > 0 to use items!",
            "     • 0.0 = Cannot use items (default when no equipment bonus)",
            "     • 1.0 = Normal use speed (1× speed)",
            "     • 2.0 = Twice as fast (2× speed)",
            "     • 3.0 = Three times as fast (3× speed)",
            "     Useful for combat builds that rely on quick potion use",
            "",
            "[中文] 物品使用速度倍率(食物、药水、盾牌)",
            "     提高物品消耗或使用的速度",
            "     影响: 吃食物、喝药水、使用盾牌等",
            "     不影响: 弓(使用bowSpeed调整)",
            "     公式: 最终速度 = 装备加成 + 配置值",
            "     重要: 需要有使用速度加成的装备或配置 > 0 才能使用物品!",
            "     • 0.0 = 无法使用物品(默认，无装备加成时)",
            "     • 1.0 = 正常速度(1倍速)",
            "     • 2.0 = 快一倍(2倍速)",
            "     • 3.0 = 快两倍(3倍速)",
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
    public static float vanillaCriticalHitDamage = 0.5f;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Custom Critical Hit Chance (Decimal)",
            "     Base chance to land a critical hit (0.0 to 1.0)",
            "     OVERFLOW CONVERSION SYSTEM:",
            "     • Values from 0.0 to 1.0 work as normal critical chance",
            "     • Values above 1.0 will overflow and convert to critical damage",
            "     Examples:",
            "     • 0.0 = 0% critical chance (default)",
            "     • 0.2 = 20% critical chance",
            "     • 0.5 = 50% critical chance",
            "     • 1.0 = 100% critical chance (always crit)",
            "     • 1.5 = 100% crit + 0.5 overflow converted to crit damage",
            "     • 2.0 = 100% crit + 1.0 overflow converted to crit damage",
            "     Overflow conversion ratio controlled by criticalOverflowConversion",
            "     Independent from vanilla critical hit system",
            "",
            "[中文] 自定义暴击几率(小数)",
            "     打出暴击的基础几率(0.0到1.0)",
            "     溢出转化系统:",
            "     • 0.0到1.0之间的值正常作为暴击几率",
            "     • 超过1.0的值会溢出并转化为暴击伤害",
            "     示例:",
            "     • 0.0 = 0%暴击几率(默认)",
            "     • 0.2 = 20%暴击几率",
            "     • 0.5 = 50%暴击几率",
            "     • 1.0 = 100%暴击几率(必定暴击)",
            "     • 1.5 = 100%暴击 + 0.5溢出转化为暴击伤害",
            "     • 2.0 = 100%暴击 + 1.0溢出转化为暴击伤害",
            "     溢出转化比例由criticalOverflowConversion控制",
            "     独立于原版暴击系统",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.customCriticalChance")
    @Config.RangeDouble(min = 0)
    public static float customCriticalChance = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Custom Critical Hit Damage Multiplier",
            "     Damage multiplier applied when critical hit occurs",
            "     Minimum value: 1.0 (100% damage, no bonus)",
            "     Examples:",
            "     • 1.0 = 100% damage (no bonus, default would be pointless)",
            "     • 1.5 = 150% damage",
            "     • 2.0 = 200% damage (double damage, recommended default)",
            "     • 3.0 = 300% damage (triple damage)",
            "     This value is increased by overflow critical chance",
            "     Formula: Final Crit Damage = This Value + (Overflow × Conversion Ratio)",
            "     Example: 2.0 base, 1.5 total chance (0.5 overflow), 1.0 ratio",
            "              → Final = 2.0 + (0.5 × 1.0) = 2.5× damage",
            "",
            "[中文] 自定义暴击伤害倍率",
            "     暴击触发时应用的伤害倍率",
            "     最小值: 1.0 (100%伤害，无加成)",
            "     示例:",
            "     • 1.0 = 100%伤害(无加成，作为默认值无意义)",
            "     • 1.5 = 150%伤害",
            "     • 2.0 = 200%伤害(双倍伤害，推荐默认值)",
            "     • 3.0 = 300%伤害(三倍伤害)",
            "     此值会被溢出暴击率增加",
            "     公式: 最终暴击伤害 = 此值 + (溢出值 × 转化比例)",
            "     例: 2.0基础, 1.5总几率(0.5溢出), 1.0比例",
            "         → 最终 = 2.0 + (0.5 × 1.0) = 2.5倍伤害",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.customCriticalDamage")
    @Config.RangeDouble(min = 1)
    public static float customCriticalDamage = 2;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Critical Chance Overflow to Damage Conversion Ratio",
            "     Controls how overflow critical chance converts to critical damage",
            "     When customCriticalChance > 1.0, the overflow is converted",
            "     Formula: Extra Crit Damage = Overflow Amount × This Ratio",
            "     Examples:",
            "     • 0.0 = No conversion (overflow is wasted)",
            "     • 0.5 = 50% conversion (0.5 overflow → +0.25 crit damage)",
            "     • 1.0 = 100% conversion (0.5 overflow → +0.5 crit damage) [DEFAULT]",
            "     • 2.0 = 200% conversion (0.5 overflow → +1.0 crit damage)",
            "     Complete Example:",
            "     - customCriticalChance = 1.8 (80% overflow)",
            "     - customCriticalDamage = 2.0 (base 200%)",
            "     - criticalOverflowConversion = 1.0 (1:1 ratio)",
            "     → Result: 100% crit chance + 2.8× crit damage (2.0 + 0.8)",
            "",
            "[中文] 暴击率溢出转暴击伤害转化比例",
            "     控制溢出的暴击率如何转化为暴击伤害",
            "     当customCriticalChance > 1.0时，溢出部分会被转化",
            "     公式: 额外暴击伤害 = 溢出量 × 此比例",
            "     示例:",
            "     • 0.0 = 无转化(溢出被浪费)",
            "     • 0.5 = 50%转化(0.5溢出 → +0.25暴击伤害)",
            "     • 1.0 = 100%转化(0.5溢出 → +0.5暴击伤害) [默认]",
            "     • 2.0 = 200%转化(0.5溢出 → +1.0暴击伤害)",
            "     完整示例:",
            "     - customCriticalChance = 1.8 (溢出0.8)",
            "     - customCriticalDamage = 2.0 (基础200%)",
            "     - criticalOverflowConversion = 1.0 (1:1比例)",
            "     → 结果: 100%暴击几率 + 2.8倍暴击伤害 (2.0 + 0.8)",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".attribute.criticalOverflowConversion")
    @Config.RangeDouble(min = 0)
    public static float criticalOverflowConversion = 1.0F;

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