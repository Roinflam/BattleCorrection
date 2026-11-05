package pers.roinflam.battlecorrection.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;

import javax.annotation.Nonnull;

/**
 * 属性配置类
 * 包含所有RPG属性相关的配置选项
 */
public class ConfigAttribute {

    public static final ModConfigSpec SPEC;

    // 伤害加成
    public static final ModConfigSpec.DoubleValue MAGIC_DAMAGE;
    public static final ModConfigSpec.DoubleValue ARROW_DAMAGE;
    public static final ModConfigSpec.DoubleValue PROJECTILE_DAMAGE;

    // 治疗与吸血
    public static final ModConfigSpec.DoubleValue RESTORE_HEAL;
    public static final ModConfigSpec.DoubleValue BLOODTHIRSTY;
    public static final ModConfigSpec.DoubleValue ALMIGHTY_BLOODTHIRSTY;
    public static final ModConfigSpec.BooleanValue LIFESTEAL_HEAL_BONUS;

    // 防御属性
    public static final ModConfigSpec.DoubleValue IMMUNE_DAMAGE;
    public static final ModConfigSpec.DoubleValue IGNORE_DAMAGE;
    public static final ModConfigSpec.DoubleValue REDUCED_FALL_DAMAGE;

    // 速度属性
    public static final ModConfigSpec.DoubleValue BOW_SPEED;
    public static final ModConfigSpec.DoubleValue PREPARATION_SPEED;
    public static final ModConfigSpec.DoubleValue JUMP_LIFT;

    // 暴击系统
    public static final ModConfigSpec.DoubleValue VANILLA_CRITICAL_HIT_DAMAGE;
    public static final ModConfigSpec.DoubleValue CUSTOM_CRITICAL_CHANCE;
    public static final ModConfigSpec.DoubleValue CUSTOM_CRITICAL_DAMAGE;
    public static final ModConfigSpec.DoubleValue CRITICAL_OVERFLOW_CONVERSION;

    // 饰品栏标记
    public static final ModConfigSpec.BooleanValue CURIO_MARK_ENABLED;

    /**
     * 未标记饰品上原版格式属性（AttributeModifiers NBT、物品自带的、其他模组追加的）的生效范围
     */
    public enum CurioNbtAttributes {
        /**
         * 不生效（Curios 原样）
         */
        OFF,
        /**
         * 只有本模组的属性生效（battlecorrection:*）
         */
        MOD_ONLY,
        /**
         * 全部属性生效
         */
        ALL
    }

    public static final ModConfigSpec.EnumValue<CurioNbtAttributes> CURIO_NBT_ATTRIBUTES;

    // 开发测试：给玩家 Curios 自带的 10 种默认饰品栏
    public static final ModConfigSpec.BooleanValue DEV_CURIO_SLOTS;

    // 物品编辑器
    public static final ModConfigSpec.BooleanValue ITEM_EDITOR_ENABLED;
    public static final ModConfigSpec.IntValue ITEM_EDITOR_MAX_NBT_KB;
    public static final ModConfigSpec.BooleanValue ITEM_EDITOR_QUALITY_TAGS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        // 伤害加成
        builder.comment(
                "===============================================================",
                "Damage Bonuses - 伤害加成",
                "Values in this file are GLOBAL bonuses for ALL living entities, including mobs",
                "本文件中的数值是对所有生物（包括怪物）生效的全局加成",
                "==============================================================="
        ).push("damage");

        MAGIC_DAMAGE = builder
                .comment(
                        "[EN] Global Magic Damage Bonus (Flat Amount)",
                        "     5 = +5 damage to all magic attacks",
                        "     Only magic attacks with an attacker are boosted (poison ticks etc. are not)",
                        "[中文] 全局魔法伤害加成(固定值)",
                        "     5 = 所有魔法攻击+5伤害",
                        "     只对有攻击者的魔法攻击生效（中毒等无来源伤害不加成）"
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
                        "     Hits that originally deal no damage (snowballs, eggs) are not boosted",
                        "[中文] 全局弹射物伤害加成(固定值，非箭矢)",
                        "     原本不造成伤害的命中（雪球、鸡蛋）不加成"
                )
                .defineInRange("projectileDamage", 0.0D, 0.0D, Double.MAX_VALUE);

        builder.pop();

        // 治疗与吸血
        builder.comment(
                "===============================================================",
                "Healing & Lifesteal - 治疗与吸血",
                "==============================================================="
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

        LIFESTEAL_HEAL_BONUS = builder
                .comment(
                        "[EN] Whether lifesteal (melee and universal) is boosted by healing bonus",
                        "     true = Lifesteal heals are multiplied by restoreHeal and the Restore Heal attribute (default)",
                        "     false = Lifesteal always heals exactly damage x ratio, healing bonus is ignored",
                        "[中文] 吸血（近战吸血和全能吸血）是否吃治疗加成",
                        "     true = 吸血回的血也乘 restoreHeal 和「治疗恢复」属性(默认)",
                        "     false = 吸血固定回 伤害 × 比例，不吃治疗加成"
                )
                .define("lifestealAffectedByHealBonus", true);

        builder.pop();

        // 防御属性
        builder.comment(
                "===============================================================",
                "Defense Attributes - 防御属性",
                "==============================================================="
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
                        "     10 = Ignore ~13 blocks of fall",
                        "[中文] 摔落伤害减免(固定值)",
                        "     10 = 忽略约13格的坠落"
                )
                .defineInRange("reducedFallDamage", 0.0D, 0.0D, Double.MAX_VALUE);

        builder.pop();

        // 速度属性
        builder.comment(
                "===============================================================",
                "Speed Attributes - 速度属性",
                "==============================================================="
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

        // 暴击系统
        builder.comment(
                "===============================================================",
                "Critical Hit System - 暴击系统",
                "==============================================================="
        ).push("critical");

        VANILLA_CRITICAL_HIT_DAMAGE = builder
                .comment(
                        "[EN] Vanilla Critical Hit Damage Bonus (Fall Attack Only)",
                        "     Formula: Damage × (1.5 + This Value)",
                        "     0.0 = 150% damage (vanilla default, 1.5×), 0.5 = 200% damage (2.0×), 1.0 = 250% damage (2.5×)",
                        "[中文] 原版暴击伤害加成(仅下坠攻击)",
                        "     公式: 伤害 × (1.5 + 此值)",
                        "     0.0 = 150%伤害(原版默认, 1.5倍), 0.5 = 200%伤害(2.0倍), 1.0 = 250%伤害(2.5倍)"
                )
                .defineInRange("vanillaCriticalHitDamage", 0.0D, 0.0D, Double.MAX_VALUE);

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

        // 饰品栏标记
        builder.comment(
                "===============================================================",
                "Curio Mark - 饰品栏标记（需要 Curios）",
                "==============================================================="
        ).push("curios");

        CURIO_MARK_ENABLED = builder
                .comment(
                        "[EN] Enable the curio mark feature (/battlecorrection curio)",
                        "     false = command disabled, marked items can no longer be put into curio slots",
                        "     Items already equipped keep their attributes until taken off",
                        "[中文] 启用饰品栏标记功能（/battlecorrection curio）",
                        "     false = 命令不可用，标记过的物品不能再放进饰品栏",
                        "     已经戴着的物品在摘下前属性照常生效"
                )
                .define("curioMarkEnabled", true);

        CURIO_NBT_ATTRIBUTES = builder
                .comment(
                        "[EN] Which vanilla-format attributes on an UNMARKED curio take effect while it is worn",
                        "     (AttributeModifiers NBT, the item's own attributes, attributes added by other mods)",
                        "     ALL = every attribute, MOD_ONLY = only battlecorrection:* attributes, OFF = none",
                        "     Marked items always apply everything. Takes effect on next equip / relogin",
                        "[中文] 未标记饰品上原版格式的属性戴着时哪些生效",
                        "     （AttributeModifiers NBT、物品自带的、其他模组追加的）",
                        "     ALL = 全部生效，MOD_ONLY = 只有 battlecorrection:* 的属性生效，OFF = 都不生效",
                        "     标记过的物品始终全部生效。改完要重新戴上或重进才生效"
                )
                .defineEnum("curioNbtAttributes", CurioNbtAttributes.ALL);

        DEV_CURIO_SLOTS = builder
                .comment(
                        "[EN] DEV / TESTING ONLY: give every player Curios' 10 default slot types",
                        "     (head, necklace, back, body, bracelet, hands, ring, belt, charm, curio), 1 each",
                        "     Curios only attaches a curios inventory to entities that have at least one slot type,",
                        "     so in a bare dev environment players have none and nothing can be edited. Run /reload after changing",
                        "     Keep this false in normal play: slots are usually assigned by other accessory mods",
                        "[中文] 仅开发测试用：给所有玩家 Curios 自带的 10 种默认饰品栏各 1 格",
                        "     （头、项链、背、身体、手镯、手、戒指、腰带、护符、通用）",
                        "     Curios 只给至少有一种饰品栏的实体装饰品栏能力，开发环境只装 Curios 时玩家一种都没有，什么都改不了",
                        "     改完要 /reload。正常游戏保持 false：饰品栏一般由别的模组分配"
                )
                .define("devCurioSlots", false);

        builder.pop();

        // 物品编辑器
        builder.comment(
                "===============================================================",
                "Item Editor - 物品编辑器（OP + 创造模式）",
                "==============================================================="
        ).push("itemEditor");

        ITEM_EDITOR_ENABLED = builder
                .comment(
                        "[EN] Enable the in-game item editor (/battlecorrection edit, hotkeys I / O / P)",
                        "     Only players with OP level 2 in creative mode can use it",
                        "     false = command and hotkeys are rejected, saving is refused on the server",
                        "[中文] 启用游戏内物品编辑器（/battlecorrection edit，快捷键 I / O / P）",
                        "     只有 OP 2 级且处于创造模式的玩家能用",
                        "     false = 命令和快捷键都不可用，服务端也会拒绝保存"
                )
                .define("itemEditorEnabled", true);

        ITEM_EDITOR_MAX_NBT_KB = builder
                .comment(
                        "[EN] Max NBT size (KB) the editor may save onto one item, entity or block entity",
                        "     The editor sends large NBT in chunks, so the network is no longer the limit. The real",
                        "     limit is the vanilla client: it refuses to decode an item slot whose NBT is around 2 MB or",
                        "     more and disconnects, so an oversized item can lock its holder out of the server",
                        "     Raise it with care; anything above 2048 is likely to break item sync",
                        "[中文] 编辑器允许保存到单个物品 / 实体 / 方块实体上的 NBT 大小上限（KB）",
                        "     编辑器的大 NBT 是分片发送的，网络本身不再限制大小。真正的上限在原版客户端：",
                        "     物品格子的 NBT 到 2 MB 左右客户端就解不出来，会直接断线，超大物品能把持有者锁在服务器外面",
                        "     调大要谨慎，超过 2048 基本一定会出问题"
                )
                .defineInRange("itemEditorMaxNbtKb", 1024, 16, 8192);

        ITEM_EDITOR_QUALITY_TAGS = builder
                .comment(
                        "[EN] Show the 'minimum quality' selector on the editor's Tags page (feature in development)",
                        "     Writes an Epic / Legendary / Myth / Genesis mark at the root of the item tag",
                        "[中文] 在编辑器\"标签\"页显示\"最低品质\"选择（功能开发中）",
                        "     写的是物品 tag 根下的 Epic / Legendary / Myth / Genesis 标记"
                )
                .define("itemEditorQualityTags", true);

        builder.pop();

        SPEC = builder.build();
    }

    /**
     * 编辑器 NBT 大小上限的旧默认值（KB）：3.0.0 的默认就是它，配置文件里留着这个值的基本都是没动过的老配置
     */
    private static final int LEGACY_NBT_KB_DEFAULT = 256;

    /**
     * 编辑器 NBT 大小上限的新默认值（KB），和上面 {@link #ITEM_EDITOR_MAX_NBT_KB} 的默认一致
     */
    private static final int NBT_KB_DEFAULT = 1024;

    /**
     * 配置加载时的一次性迁移：旧版默认的 256 KB 换成新默认
     * <p>
     * 默认值改了以后，已经生成过配置文件的玩家和服主不会自动拿到新值（文件里存的还是旧的 256），
     * 又不能让他们为这个自己去翻配置。256 是旧的默认值，当作没动过处理；换过后文件里是 1024，下次加载不会再触发。
     *
     * @param event 配置加载事件
     */
    public static void onConfigLoading(@Nonnull ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        if (ITEM_EDITOR_MAX_NBT_KB.get() == LEGACY_NBT_KB_DEFAULT) {
            ITEM_EDITOR_MAX_NBT_KB.set(NBT_KB_DEFAULT);
            ITEM_EDITOR_MAX_NBT_KB.save();
        }
    }
}
