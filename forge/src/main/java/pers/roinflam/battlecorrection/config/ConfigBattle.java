package pers.roinflam.battlecorrection.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.List;

/**
 * 战斗配置类 - Battle Configuration Class (1.20.1)
 * 包含所有战斗相关的配置选项
 */
public class ConfigBattle {

    public static final ForgeConfigSpec SPEC;

    // ===== PVP设置 =====
    public static final ForgeConfigSpec.DoubleValue PVP;
    public static final ForgeConfigSpec.DoubleValue PVP_HURT_ITSELF;

    // ===== 战斗机制 =====
    public static final ForgeConfigSpec.BooleanValue COMBO_CORRECTION;
    public static final ForgeConfigSpec.BooleanValue ATTACK_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue HURT_TIME_ENTITY;
    public static final ForgeConfigSpec.DoubleValue HURT_TIME_PLAYER;

    // ===== 玩家攻击倍率 =====
    public static final ForgeConfigSpec.DoubleValue PLAYER_MELEE_ATTACK;
    public static final ForgeConfigSpec.DoubleValue PLAYER_ARROW_ATTACK;
    public static final ForgeConfigSpec.DoubleValue PLAYER_PROJECTILE_ATTACK;
    public static final ForgeConfigSpec.DoubleValue PLAYER_MAGIC_ATTACK;

    // ===== 玩家承受伤害倍率 =====
    public static final ForgeConfigSpec.DoubleValue PLAYER_SUFFERS_MELEE;
    public static final ForgeConfigSpec.DoubleValue PLAYER_SUFFERS_ARROW;
    public static final ForgeConfigSpec.DoubleValue PLAYER_SUFFERS_PROJECTILE;
    public static final ForgeConfigSpec.DoubleValue PLAYER_SUFFERS_MAGIC;

    // ===== 实体属性 =====
    public static final ForgeConfigSpec.DoubleValue EXTRA_MAX_HEALTH;

    // ===== 饥饿恢复 =====
    public static final ForgeConfigSpec.DoubleValue EXTRA_SATURATION_HEAL;
    public static final ForgeConfigSpec.DoubleValue EXTRA_SATURATION_PERCENTAGE_HEAL;
    public static final ForgeConfigSpec.DoubleValue EXTRA_HUNGER_HEAL;
    public static final ForgeConfigSpec.DoubleValue EXTRA_HUNGER_PERCENTAGE_HEAL;

    // ===== 饥饿伤害衰减 =====
    public static final ForgeConfigSpec.DoubleValue HUNGER_DAMAGE_DECAY;
    public static final ForgeConfigSpec.DoubleValue HUNGER_DAMAGE_DECAY_LIMIT;

    // ===== 第三方魔法识别 =====
    /** 是否把第三方魔法模组的法术伤害识别为魔法伤害（总开关） */
    public static final ForgeConfigSpec.BooleanValue ENABLE_THIRD_PARTY_MAGIC_RECOGNITION;
    /** 按命名空间整体算魔法的模组 id 列表 */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MAGIC_DAMAGE_NAMESPACES;
    /** 精确算魔法的伤害类型 id 列表 */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MAGIC_DAMAGE_TYPE_WHITELIST;
    /** 精确不算魔法的伤害类型 id 列表（优先级最高） */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MAGIC_DAMAGE_TYPE_BLACKLIST;

    /** 命名空间默认值：目前确认过伤害源结构的五个魔法模组 */
    public static final List<String> DEFAULT_MAGIC_DAMAGE_NAMESPACES = Arrays.asList(
            "irons_spellbooks", "gtbcs_geomancy_plus", "goety", "ars_nouveau", "sweetmagic");

    /** 白名单默认值：空 */
    public static final List<String> DEFAULT_MAGIC_DAMAGE_TYPE_WHITELIST = Arrays.asList();

    /** 黑名单默认值：Goety 的召唤物攻击、镰刀/末影刃斩击、战利品爆炸、遣散 */
    public static final List<String> DEFAULT_MAGIC_DAMAGE_TYPE_BLACKLIST = Arrays.asList(
            "goety:summon", "goety:sword", "goety:loot_explode", "goety:loot_explode_owned", "goety:dismissed");

    // ===== 调试 =====
    public static final ForgeConfigSpec.BooleanValue ENABLE_DETAILED_LOGGING;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        // ═══════════════════════════════════════════════════════════════
        // PVP设置
        // ═══════════════════════════════════════════════════════════════
        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "PVP Settings - PVP设置",
                "═══════════════════════════════════════════════════════════════"
        ).push("pvp");

        PVP = builder
                .comment(
                        "[EN] PVP Damage Multiplier",
                        "     Controls the damage dealt between players in PVP combat",
                        "     0.5 = 50% damage, 1.0 = Normal (default), 2.0 = 200% damage, 0.0 = Disable PVP",
                        "[中文] PVP伤害倍率",
                        "     控制玩家之间PVP战斗造成的伤害",
                        "     0.5 = 50%伤害, 1.0 = 正常(默认), 2.0 = 200%伤害, 0.0 = 禁用PVP"
                )
                .defineInRange("pvp", 1.0D, 0.0D, Double.MAX_VALUE);

        PVP_HURT_ITSELF = builder
                .comment(
                        "[EN] Self-Damage Multiplier",
                        "     Controls damage when a player hurts themselves (own arrows, own TNT, own splash potions, etc.)",
                        "     0.0 = Prevent self-damage, 1.0 = Normal (default)",
                        "[中文] 自伤倍率",
                        "     控制玩家对自己造成伤害时的倍率(自己射的箭、自己点燃的TNT、自己的喷溅药水等)",
                        "     0.0 = 防止自伤, 1.0 = 正常(默认)"
                )
                .defineInRange("pvpHurtItself", 1.0D, 0.0D, Double.MAX_VALUE);

        builder.pop();

        // ═══════════════════════════════════════════════════════════════
        // 战斗机制
        // ═══════════════════════════════════════════════════════════════
        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "Combat Mechanics - 战斗机制",
                "═══════════════════════════════════════════════════════════════"
        ).push("combat");

        COMBO_CORRECTION = builder
                .comment(
                        "[EN] Combo Correction System",
                        "     true = Uncharged attacks deal less damage (discourages spam-clicking)",
                        "     false = No extra correction, vanilla charge penalty still applies (default)",
                        "[中文] 连击修正系统",
                        "     true = 未蓄力攻击伤害降低(防止无脑连点)",
                        "     false = 不做额外修正，原版自身的蓄力惩罚照常(默认)"
                )
                .define("comboCorrection", false);

        ATTACK_COOLDOWN = builder
                .comment(
                        "[EN] Attack Cooldown System",
                        "     true = 1.9+ combat with attack cooldown (default)",
                        "     false = 1.8 combat with no attack cooldown",
                        "[中文] 攻击冷却系统",
                        "     true = 1.9+战斗系统，有攻击冷却(默认)",
                        "     false = 1.8战斗系统，无攻击冷却"
                )
                .define("attackCooldown", true);

        HURT_TIME_ENTITY = builder
                .comment(
                        "[EN] Entity Invulnerability Time Multiplier",
                        "     Formula: New Time = Original Time × (1 + This Value), Original Time = vanilla 10 ticks",
                        "     -0.5 = Half time, 0.0 = Normal (default), 1.0 = Double time",
                        "[中文] 实体无敌时间倍率",
                        "     公式: 新时间 = 原始时间 × (1 + 此值)，原始时间 = 原版的 10 tick",
                        "     -0.5 = 一半时间, 0.0 = 正常(默认), 1.0 = 双倍时间"
                )
                .defineInRange("hurtTimeEntity", 0.0D, -0.99D, Double.MAX_VALUE);

        HURT_TIME_PLAYER = builder
                .comment(
                        "[EN] Player Invulnerability Time Multiplier",
                        "     Formula: New Time = Original Time × (1 + This Value), Original Time = vanilla 10 ticks",
                        "     -0.5 = Half time, 0.0 = Normal (default), 1.0 = Double time",
                        "[中文] 玩家无敌时间倍率",
                        "     公式: 新时间 = 原始时间 × (1 + 此值)，原始时间 = 原版的 10 tick",
                        "     -0.5 = 一半时间, 0.0 = 正常(默认), 1.0 = 双倍时间"
                )
                .defineInRange("hurtTimePlayer", 0.0D, -0.99D, Double.MAX_VALUE);

        builder.pop();

        // ═══════════════════════════════════════════════════════════════
        // 玩家攻击倍率
        // ═══════════════════════════════════════════════════════════════
        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "Player Attack Multipliers - 玩家攻击倍率",
                "═══════════════════════════════════════════════════════════════"
        ).push("playerAttack");

        PLAYER_MELEE_ATTACK = builder
                .comment(
                        "[EN] Player Melee Attack Damage Multiplier",
                        "     0.5 = 50% damage, 1.0 = Normal (default), 2.0 = 200% damage",
                        "[中文] 玩家近战攻击伤害倍率",
                        "     0.5 = 50%伤害, 1.0 = 正常(默认), 2.0 = 200%伤害"
                )
                .defineInRange("playerMeleeAttack", 1.0D, 0.0D, Double.MAX_VALUE);

        PLAYER_ARROW_ATTACK = builder
                .comment(
                        "[EN] Player Arrow Attack Damage Multiplier",
                        "[中文] 玩家箭矢攻击伤害倍率"
                )
                .defineInRange("playerArrowAttack", 1.0D, 0.0D, Double.MAX_VALUE);

        PLAYER_PROJECTILE_ATTACK = builder
                .comment(
                        "[EN] Player Projectile Attack Damage Multiplier (non-arrow)",
                        "[中文] 玩家弹射物攻击伤害倍率(非箭矢)"
                )
                .defineInRange("playerProjectileAttack", 1.0D, 0.0D, Double.MAX_VALUE);

        PLAYER_MAGIC_ATTACK = builder
                .comment(
                        "[EN] Player Magic Attack Damage Multiplier",
                        "[中文] 玩家魔法攻击伤害倍率"
                )
                .defineInRange("playerMagicAttack", 1.0D, 0.0D, Double.MAX_VALUE);

        builder.pop();

        // ═══════════════════════════════════════════════════════════════
        // 玩家承受伤害倍率
        // ═══════════════════════════════════════════════════════════════
        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "Player Damage Resistance - 玩家承受伤害倍率",
                "═══════════════════════════════════════════════════════════════"
        ).push("playerDefense");

        PLAYER_SUFFERS_MELEE = builder
                .comment(
                        "[EN] Player Melee Damage Resistance Multiplier",
                        "     0.5 = Take 50% damage (more resistant), 1.0 = Normal (default)",
                        "[中文] 玩家近战伤害抗性倍率",
                        "     0.5 = 承受50%伤害(更强抗性), 1.0 = 正常(默认)"
                )
                .defineInRange("playerSuffersMelee", 1.0D, 0.0D, Double.MAX_VALUE);

        PLAYER_SUFFERS_ARROW = builder
                .comment(
                        "[EN] Player Arrow Damage Resistance Multiplier",
                        "[中文] 玩家箭矢伤害抗性倍率"
                )
                .defineInRange("playerSuffersArrow", 1.0D, 0.0D, Double.MAX_VALUE);

        PLAYER_SUFFERS_PROJECTILE = builder
                .comment(
                        "[EN] Player Projectile Damage Resistance Multiplier",
                        "[中文] 玩家弹射物伤害抗性倍率"
                )
                .defineInRange("playerSuffersProjectile", 1.0D, 0.0D, Double.MAX_VALUE);

        PLAYER_SUFFERS_MAGIC = builder
                .comment(
                        "[EN] Player Magic Damage Resistance Multiplier",
                        "[中文] 玩家魔法伤害抗性倍率"
                )
                .defineInRange("playerSuffersMagic", 1.0D, 0.0D, Double.MAX_VALUE);

        builder.pop();

        // ═══════════════════════════════════════════════════════════════
        // 实体属性
        // ═══════════════════════════════════════════════════════════════
        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "Entity Attributes - 实体属性",
                "═══════════════════════════════════════════════════════════════"
        ).push("entity");

        EXTRA_MAX_HEALTH = builder
                .comment(
                        "[EN] Entity Maximum Health Multiplier (Non-Player Only)",
                        "     This is a MULTIPLIER: 1.0 = 200% health, 0.0 = Normal (default), -0.5 = 50% health",
                        "[中文] 实体最大生命值倍率(仅非玩家)",
                        "     这是倍率: 1.0 = 200%生命, 0.0 = 正常(默认), -0.5 = 50%生命"
                )
                .defineInRange("extraMaxHealth", 0.0D, -0.99D, Double.MAX_VALUE);

        builder.pop();

        // ═══════════════════════════════════════════════════════════════
        // 饥饿恢复
        // ═══════════════════════════════════════════════════════════════
        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "Hunger Regeneration - 饥饿恢复",
                "═══════════════════════════════════════════════════════════════"
        ).push("hunger");

        EXTRA_SATURATION_HEAL = builder
                .comment(
                        "[EN] Extra Saturation Healing (Flat Bonus)",
                        "     Extra health restored when regenerating from saturation",
                        "[中文] 饱和度恢复额外治疗(固定加成)"
                )
                .defineInRange("extraSaturationHeal", 0.0D, 0.0D, Double.MAX_VALUE);

        EXTRA_SATURATION_PERCENTAGE_HEAL = builder
                .comment(
                        "[EN] Extra Saturation Healing (Percentage of Max Health)",
                        "     0.05 = Restore 5% of max health per tick",
                        "[中文] 饱和度恢复额外治疗(最大生命百分比)",
                        "     0.05 = 每刻恢复5%最大生命"
                )
                .defineInRange("extraSaturationPercentageHeal", 0.0D, 0.0D, Double.MAX_VALUE);

        EXTRA_HUNGER_HEAL = builder
                .comment(
                        "[EN] Extra Hunger Healing (Flat Bonus)",
                        "[中文] 饥饿值恢复额外治疗(固定加成)"
                )
                .defineInRange("extraHungerHeal", 0.0D, 0.0D, Double.MAX_VALUE);

        EXTRA_HUNGER_PERCENTAGE_HEAL = builder
                .comment(
                        "[EN] Extra Hunger Healing (Percentage of Max Health)",
                        "[中文] 饥饿值恢复额外治疗(最大生命百分比)"
                )
                .defineInRange("extraHungerPercentageHeal", 0.0D, 0.0D, Double.MAX_VALUE);

        HUNGER_DAMAGE_DECAY = builder
                .comment(
                        "[EN] Hunger Damage Decay (Damage Reduction Per Missing Hunger Point)",
                        "     0.05 = 5% less damage per missing hunger point",
                        "[中文] 饥饿伤害衰减(每点缺失饥饿值的伤害减少)",
                        "     0.05 = 每少1点饥饿值减少5%伤害"
                )
                .defineInRange("hungerDamageDecay", 0.0D, 0.0D, Double.MAX_VALUE);

        HUNGER_DAMAGE_DECAY_LIMIT = builder
                .comment(
                        "[EN] Maximum Hunger Damage Decay (Maximum Damage Reduction Cap)",
                        "     0.70 = Maximum 70% damage reduction, 0.0 = No limit (default)",
                        "[中文] 最大饥饿伤害衰减上限",
                        "     0.70 = 最多减少70%伤害, 0.0 = 不设上限(默认)"
                )
                .defineInRange("hungerDamageDecayLimit", 0.0D, 0.0D, 1.0D);

        builder.pop();

        // ═══════════════════════════════════════════════════════════════
        // 第三方魔法识别
        // ═══════════════════════════════════════════════════════════════
        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "Third-party Magic Recognition - 第三方魔法识别",
                "  Decides which damage counts as 'magic' for the magicDamage attribute and the player magic attack / resistance multipliers.",
                "  Magic is an independent layer on top of the base melee/arrow/projectile layer: base bonuses and multipliers always apply, magic stacks on top.",
                "  Vanilla rule always applies: message_id contains 'magic', or damage type is in minecraft:witch_resistant_to.",
                "  Summon/pet melee hits (direct entity is someone else's living minion) are never counted as magic.",
                "  决定哪些伤害算「魔法伤害」（用于 magicDamage 属性、玩家魔法攻击/承受倍率）。",
                "  魔法是叠在近战/箭矢/弹射物基础层之上的独立层：基础加成和倍率始终生效，魔法在其上叠加。",
                "  原版规则始终生效：message_id 含 magic，或伤害类型在 minecraft:witch_resistant_to 里。",
                "  召唤物/宠物替主人挥砍的命中一律不算魔法。",
                "═══════════════════════════════════════════════════════════════"
        ).push("magicRecognition");

        ENABLE_THIRD_PARTY_MAGIC_RECOGNITION = builder
                .comment(
                        "[EN] Enable third-party magic recognition (forge:is_magic tag + namespace/whitelist/blacklist rules)",
                        "     true = Enabled (default), false = vanilla rule only",
                        "[中文] 启用第三方魔法识别（forge:is_magic tag + 命名空间/白名单/黑名单规则）",
                        "     true = 启用(默认), false = 只保留原版规则"
                )
                .define("enableThirdPartyMagicRecognition", true);

        MAGIC_DAMAGE_NAMESPACES = builder
                .comment(
                        "[EN] Damage-type namespaces treated as magic as a whole",
                        "     e.g. 'goety' covers goety:direct_shock, goety:hellfire, ... every goety damage type",
                        "[中文] 整个命名空间都算魔法的模组 id",
                        "     例如 goety 覆盖 goety:direct_shock、goety:hellfire 等全部类型"
                )
                .defineList("magicDamageNamespaces", DEFAULT_MAGIC_DAMAGE_NAMESPACES, o -> o instanceof String);

        MAGIC_DAMAGE_TYPE_WHITELIST = builder
                .comment(
                        "[EN] Exact damage-type ids always treated as magic, e.g. 'somemod:arcane_bolt'",
                        "[中文] 精确算魔法的伤害类型 id，例如 somemod:arcane_bolt"
                )
                .defineList("magicDamageTypeWhitelist", DEFAULT_MAGIC_DAMAGE_TYPE_WHITELIST, o -> o instanceof String);

        MAGIC_DAMAGE_TYPE_BLACKLIST = builder
                .comment(
                        "[EN] Exact damage-type ids never treated as magic (highest priority, overrides every other rule)",
                        "[中文] 精确不算魔法的伤害类型 id（优先级最高，压过其他所有规则）"
                )
                .defineList("magicDamageTypeBlacklist", DEFAULT_MAGIC_DAMAGE_TYPE_BLACKLIST, o -> o instanceof String);

        builder.pop();

        // ═══════════════════════════════════════════════════════════════
        // 调试
        // ═══════════════════════════════════════════════════════════════
        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "Debug Settings - 调试设置",
                "═══════════════════════════════════════════════════════════════"
        ).push("debug");

        ENABLE_DETAILED_LOGGING = builder
                .comment(
                        "[EN] Enable Detailed Debug Logging",
                        "     WARNING: This generates EXTENSIVE log output!",
                        "[中文] 启用详细调试日志",
                        "     警告: 这会产生大量日志输出!"
                )
                .define("enableDetailedLogging", false);

        builder.pop();

        SPEC = builder.build();
    }
}
