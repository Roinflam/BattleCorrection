// 文件：ConfigBattle.java
package pers.roinflam.battlecorrection.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 战斗配置类 - Battle Configuration Class
 * 包含所有战斗相关的配置选项
 * Contains all combat-related configuration options
 */
@Mod.EventBusSubscriber
@Config(modid = Reference.MOD_ID, category = "Battle")
@Config.LangKey("config." + Reference.MOD_ID + ".battle")
public final class ConfigBattle {

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] PVP Damage Multiplier",
            "     Controls the damage dealt between players in PVP combat",
            "     • 0.5 = Players deal 50% damage to each other",
            "     • 1.0 = Normal vanilla damage (default)",
            "     • 2.0 = Players deal 200% damage to each other",
            "     • 0.0 = Completely disable PVP damage",
            "     This only affects player vs player damage, not PVE",
            "",
            "[中文] PVP伤害倍率",
            "     控制玩家之间PVP战斗造成的伤害",
            "     • 0.5 = 玩家互相造成50%伤害",
            "     • 1.0 = 正常原版伤害(默认)",
            "     • 2.0 = 玩家互相造成200%伤害",
            "     • 0.0 = 完全禁用PVP伤害",
            "     此选项仅影响玩家对玩家的伤害，不影响PVE",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.pvp")
    @Config.RangeDouble(min = 0)
    public static float pvp = 1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Self-Damage Multiplier",
            "     Controls damage when a player hurts themselves",
            "     Affects: TNT explosions, splash potions, thorns, etc.",
            "     • 0.5 = Take 50% self-damage",
            "     • 1.0 = Normal self-damage (default)",
            "     • 2.0 = Take 200% self-damage",
            "     • 0.0 = Completely prevent self-damage",
            "",
            "[中文] 自伤倍率",
            "     控制玩家对自己造成伤害时的倍率",
            "     影响: TNT爆炸、喷溅药水、荆棘等",
            "     • 0.5 = 承受50%自伤",
            "     • 1.0 = 正常自伤(默认)",
            "     • 2.0 = 承受200%自伤",
            "     • 0.0 = 完全防止自伤",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.pvpHurtItself")
    @Config.RangeDouble(min = 0)
    public static float pvpHurtItself = 1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Combo Correction System",
            "     When enabled, attacks deal reduced damage if not fully charged",
            "     • true = Uncharged attacks deal less damage (discourages spam-clicking)",
            "     • false = All attacks deal full damage regardless of charge (1.8 style)",
            "     Damage scales with attack charge: 50% charge = 50% damage",
            "     Encourages timing attacks properly instead of mindless clicking",
            "",
            "[中文] 连击修正系统",
            "     启用时，未完全蓄力的攻击将造成较低伤害",
            "     • true = 未蓄力攻击伤害降低(防止无脑连点)",
            "     • false = 所有攻击造成全额伤害，无视蓄力(1.8风格)",
            "     伤害随攻击蓄力缩放: 50%蓄力 = 50%伤害",
            "     鼓励正确把握攻击时机而非无脑点击",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.comboCorrection")
    public static boolean comboCorrection = false;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Entity Invulnerability Time Multiplier",
            "     Controls how long non-player entities are invulnerable after being hit",
            "     • 0.5 = Half the normal invulnerability time (faster damage application)",
            "     • 1.0 = Normal vanilla invulnerability time (default)",
            "     • 2.0 = Double the invulnerability time (slower damage application)",
            "     • 0.0 = No invulnerability frames (can be hit rapidly)",
            "     Lower values make entities easier to kill but may feel unfair",
            "",
            "[中文] 实体无敌时间倍率",
            "     控制非玩家实体受击后的无敌时间长度",
            "     • 0.5 = 正常无敌时间的一半(更快造成伤害)",
            "     • 1.0 = 正常原版无敌时间(默认)",
            "     • 2.0 = 双倍无敌时间(更慢造成伤害)",
            "     • 0.0 = 无无敌帧(可以快速连续受击)",
            "     较低的值使实体更容易击杀，但可能不太公平",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.hurtTimeEntity")
    @Config.RangeDouble(min = 0)
    public static float hurtTimeEntity = 1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Player Invulnerability Time Multiplier",
            "     Controls how long players are invulnerable after being hit",
            "     • 0.5 = Half the normal invulnerability time",
            "     • 1.0 = Normal vanilla invulnerability time (default)",
            "     • 2.0 = Double the invulnerability time",
            "     • 0.0 = No invulnerability frames (very dangerous!)",
            "     Lower values increase difficulty, higher values make combat safer",
            "",
            "[中文] 玩家无敌时间倍率",
            "     控制玩家受击后的无敌时间长度",
            "     • 0.5 = 正常无敌时间的一半",
            "     • 1.0 = 正常原版无敌时间(默认)",
            "     • 2.0 = 双倍无敌时间",
            "     • 0.0 = 无无敌帧(非常危险!)",
            "     较低的值增加难度，较高的值使战斗更安全",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.hurtTimePlayer")
    @Config.RangeDouble(min = 0)
    public static float hurtTimePlayer = 1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Attack Cooldown System",
            "     Controls whether the 1.9+ attack cooldown mechanic is enabled",
            "     • true = 1.9+ combat system with attack cooldown (default)",
            "     • false = 1.8 combat system with no attack cooldown",
            "     When disabled, you can attack as fast as you can click",
            "     When enabled, attacks require charging for maximum damage",
            "",
            "[中文] 攻击冷却系统",
            "     控制是否启用1.9+的攻击冷却机制",
            "     • true = 1.9+战斗系统，有攻击冷却(默认)",
            "     • false = 1.8战斗系统，无攻击冷却",
            "     禁用时，可以以最快速度进行攻击",
            "     启用时，攻击需要蓄力以造成最大伤害",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.attackCooldown")
    public static boolean attackCooldown = true;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Player Melee Attack Damage Multiplier",
            "     Globally adjusts all melee damage dealt by players",
            "     Affects: Swords, axes, hand attacks, and other melee weapons",
            "     • 0.5 = Players deal 50% melee damage",
            "     • 1.0 = Normal vanilla melee damage (default)",
            "     • 2.0 = Players deal 200% melee damage",
            "     Does not affect arrow, projectile, or magic damage",
            "",
            "[中文] 玩家近战攻击伤害倍率",
            "     全局调整玩家造成的所有近战伤害",
            "     影响: 剑、斧、徒手攻击和其他近战武器",
            "     • 0.5 = 玩家造成50%近战伤害",
            "     • 1.0 = 正常原版近战伤害(默认)",
            "     • 2.0 = 玩家造成200%近战伤害",
            "     不影响箭矢、弹射物或魔法伤害",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.playerMeleeAttack")
    @Config.RangeDouble(min = 0)
    public static float playerMeleeAttack = 1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Player Arrow Attack Damage Multiplier",
            "     Globally adjusts all arrow damage dealt by players",
            "     Affects: Bows, crossbows, and any arrow-based weapons",
            "     • 0.5 = Players deal 50% arrow damage",
            "     • 1.0 = Normal vanilla arrow damage (default)",
            "     • 2.0 = Players deal 200% arrow damage",
            "     Does not affect other projectiles like snowballs or eggs",
            "",
            "[中文] 玩家箭矢攻击伤害倍率",
            "     全局调整玩家造成的所有箭矢伤害",
            "     影响: 弓、弩和任何基于箭矢的武器",
            "     • 0.5 = 玩家造成50%箭矢伤害",
            "     • 1.0 = 正常原版箭矢伤害(默认)",
            "     • 2.0 = 玩家造成200%箭矢伤害",
            "     不影响其他弹射物如雪球或鸡蛋",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.playerArrowAttack")
    @Config.RangeDouble(min = 0)
    public static float playerArrowAttack = 1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Player Projectile Attack Damage Multiplier",
            "     Globally adjusts non-arrow projectile damage dealt by players",
            "     Affects: Snowballs, eggs, splash potions, and modded projectiles",
            "     Does NOT affect: Arrows (use playerArrowAttack for that)",
            "     • 0.5 = Players deal 50% projectile damage",
            "     • 1.0 = Normal vanilla projectile damage (default)",
            "     • 2.0 = Players deal 200% projectile damage",
            "",
            "[中文] 玩家弹射物攻击伤害倍率",
            "     全局调整玩家造成的非箭矢弹射物伤害",
            "     影响: 雪球、鸡蛋、喷溅药水和模组弹射物",
            "     不影响: 箭矢(使用playerArrowAttack调整)",
            "     • 0.5 = 玩家造成50%弹射物伤害",
            "     • 1.0 = 正常原版弹射物伤害(默认)",
            "     • 2.0 = 玩家造成200%弹射物伤害",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.playerProjectileAttack")
    @Config.RangeDouble(min = 0)
    public static float playerProjectileAttack = 1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Player Magic Attack Damage Multiplier",
            "     Globally adjusts all magic damage dealt by players",
            "     Affects: Damage potions, lingering potions, modded magic weapons",
            "     • 0.5 = Players deal 50% magic damage",
            "     • 1.0 = Normal vanilla magic damage (default)",
            "     • 2.0 = Players deal 200% magic damage",
            "     Useful for balancing magic-focused mods",
            "",
            "[中文] 玩家魔法攻击伤害倍率",
            "     全局调整玩家造成的所有魔法伤害",
            "     影响: 伤害药水、滞留药水、模组魔法武器",
            "     • 0.5 = 玩家造成50%魔法伤害",
            "     • 1.0 = 正常原版魔法伤害(默认)",
            "     • 2.0 = 玩家造成200%魔法伤害",
            "     适用于平衡魔法类模组",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.playerMagicAttack")
    @Config.RangeDouble(min = 0)
    public static float playerMagicAttack = 1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Player Melee Damage Resistance Multiplier",
            "     Adjusts how much melee damage players receive",
            "     • 0.5 = Players take 50% melee damage (more resistant)",
            "     • 1.0 = Players take normal melee damage (default)",
            "     • 2.0 = Players take 200% melee damage (less resistant)",
            "     Lower values make players tankier against melee attacks",
            "     Higher values increase difficulty in melee combat",
            "",
            "[中文] 玩家近战伤害抗性倍率",
            "     调整玩家承受的近战伤害量",
            "     • 0.5 = 玩家承受50%近战伤害(更强抗性)",
            "     • 1.0 = 玩家承受正常近战伤害(默认)",
            "     • 2.0 = 玩家承受200%近战伤害(更弱抗性)",
            "     较低的值使玩家在近战中更耐打",
            "     较高的值增加近战战斗难度",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.playerSuffersMelee")
    @Config.RangeDouble(min = 0)
    public static float playerSuffersMelee = 1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Player Arrow Damage Resistance Multiplier",
            "     Adjusts how much arrow damage players receive",
            "     • 0.5 = Players take 50% arrow damage (more resistant)",
            "     • 1.0 = Players take normal arrow damage (default)",
            "     • 2.0 = Players take 200% arrow damage (less resistant)",
            "     Lower values make players tankier against ranged attacks",
            "     Useful for balancing against skeleton spawners",
            "",
            "[中文] 玩家箭矢伤害抗性倍率",
            "     调整玩家承受的箭矢伤害量",
            "     • 0.5 = 玩家承受50%箭矢伤害(更强抗性)",
            "     • 1.0 = 玩家承受正常箭矢伤害(默认)",
            "     • 2.0 = 玩家承受200%箭矢伤害(更弱抗性)",
            "     较低的值使玩家在远程攻击中更耐打",
            "     适用于平衡骷髅刷怪笼等场景",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.playerSuffersArrow")
    @Config.RangeDouble(min = 0)
    public static float playerSuffersArrow = 1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Player Projectile Damage Resistance Multiplier",
            "     Adjusts how much projectile damage players receive",
            "     • 0.5 = Players take 50% projectile damage (more resistant)",
            "     • 1.0 = Players take normal projectile damage (default)",
            "     • 2.0 = Players take 200% projectile damage (less resistant)",
            "     Affects damage from ghast fireballs, blaze shots, etc.",
            "",
            "[中文] 玩家弹射物伤害抗性倍率",
            "     调整玩家承受的弹射物伤害量",
            "     • 0.5 = 玩家承受50%弹射物伤害(更强抗性)",
            "     • 1.0 = 玩家承受正常弹射物伤害(默认)",
            "     • 2.0 = 玩家承受200%弹射物伤害(更弱抗性)",
            "     影响恶魂火球、烈焰人火球等造成的伤害",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.playerSuffersProjectile")
    @Config.RangeDouble(min = 0)
    public static float playerSuffersProjectile = 1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Player Magic Damage Resistance Multiplier",
            "     Adjusts how much magic damage players receive",
            "     • 0.5 = Players take 50% magic damage (more resistant)",
            "     • 1.0 = Players take normal magic damage (default)",
            "     • 2.0 = Players take 200% magic damage (less resistant)",
            "     Affects damage from potions, modded magic, etc.",
            "",
            "[中文] 玩家魔法伤害抗性倍率",
            "     调整玩家承受的魔法伤害量",
            "     • 0.5 = 玩家承受50%魔法伤害(更强抗性)",
            "     • 1.0 = 玩家承受正常魔法伤害(默认)",
            "     • 2.0 = 玩家承受200%魔法伤害(更弱抗性)",
            "     影响来自药水、模组魔法等的伤害",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.playerSuffersMagic")
    @Config.RangeDouble(min = 0)
    public static float playerSuffersMagic = 1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Entity Maximum Health Multiplier (Non-Player Only)",
            "     Adjusts the maximum health of all non-player entities",
            "     This is a MULTIPLIER, not addition:",
            "     • 1.0 = Entities have 200% max health (doubled)",
            "     • 0.5 = Entities have 150% max health (+50%)",
            "     • 0.0 = Entities have 100% max health (normal, default)",
            "     • -0.5 = Entities have 50% max health (halved)",
            "     Minimum value: -0.99 (prevents entities from having 0 or negative health)",
            "     Entities will be healed to match their new maximum health",
            "",
            "[中文] 实体最大生命值倍率(仅非玩家)",
            "     调整所有非玩家实体的最大生命值",
            "     这是倍率，不是加法:",
            "     • 1.0 = 实体拥有200%最大生命(双倍)",
            "     • 0.5 = 实体拥有150%最大生命(+50%)",
            "     • 0.0 = 实体拥有100%最大生命(正常，默认)",
            "     • -0.5 = 实体拥有50%最大生命(减半)",
            "     最小值: -0.99 (防止实体生命值为0或负数)",
            "     实体将被治疗以匹配新的最大生命值",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.extraMaxHealth")
    @Config.RangeDouble(min = -0.99f)
    public static float extraMaxHealth = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Extra Saturation Healing (Flat Bonus)",
            "     Flat amount of extra health restored when regenerating from saturation",
            "     Vanilla: Restores 1 HP per tick when saturated",
            "     This value is ADDED to the vanilla amount:",
            "     • 0 = Restore 1 HP (vanilla, default)",
            "     • 5 = Restore 6 HP (1 vanilla + 5 bonus)",
            "     • 10 = Restore 11 HP (1 vanilla + 10 bonus)",
            "     Stacks with percentage-based saturation healing bonus",
            "",
            "[中文] 饱和度恢复额外治疗(固定加成)",
            "     饱和度恢复时额外恢复的固定生命值",
            "     原版: 饱和时每刻恢复1点生命",
            "     此值会叠加到原版数值上:",
            "     • 0 = 恢复1点生命(原版，默认)",
            "     • 5 = 恢复6点生命(1原版 + 5加成)",
            "     • 10 = 恢复11点生命(1原版 + 10加成)",
            "     与百分比饱和度治疗加成叠加",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.extraSaturationHeal")
    @Config.RangeDouble(min = 0)
    public static float extraSaturationHeal = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Extra Saturation Healing (Percentage Bonus)",
            "     Percentage of maximum health restored when regenerating from saturation",
            "     This is a percentage of YOUR MAX HEALTH, not the base healing:",
            "     • 0.00 = No extra healing (default)",
            "     • 0.05 = Restore 5% of max health per tick",
            "     • 0.10 = Restore 10% of max health per tick",
            "     Example: Player with 100 max HP and 0.05 setting restores 5 HP",
            "     Stacks with flat saturation healing bonus",
            "",
            "[中文] 饱和度恢复额外治疗(百分比加成)",
            "     饱和度恢复时额外恢复的最大生命百分比",
            "     这是你最大生命值的百分比，不是基础治疗的百分比:",
            "     • 0.00 = 无额外治疗(默认)",
            "     • 0.05 = 每刻恢复5%最大生命",
            "     • 0.10 = 每刻恢复10%最大生命",
            "     例: 最大生命100的玩家，设置0.05时恢复5点生命",
            "     与固定饱和度治疗加成叠加",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.extraSaturationPercentageHeal")
    @Config.RangeDouble(min = 0)
    public static float extraSaturationPercentageHeal = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Extra Hunger Healing (Flat Bonus)",
            "     Flat amount of extra health restored when regenerating from hunger",
            "     Vanilla: Restores 1 HP per tick when hunger ≥ 18",
            "     This value is ADDED to the vanilla amount:",
            "     • 0 = Restore 1 HP (vanilla, default)",
            "     • 5 = Restore 6 HP (1 vanilla + 5 bonus)",
            "     • 10 = Restore 11 HP (1 vanilla + 10 bonus)",
            "     Stacks with percentage-based hunger healing bonus",
            "",
            "[中文] 饥饿值恢复额外治疗(固定加成)",
            "     饥饿值恢复时额外恢复的固定生命值",
            "     原版: 饥饿值≥18时每刻恢复1点生命",
            "     此值会叠加到原版数值上:",
            "     • 0 = 恢复1点生命(原版，默认)",
            "     • 5 = 恢复6点生命(1原版 + 5加成)",
            "     • 10 = 恢复11点生命(1原版 + 10加成)",
            "     与百分比饥饿值治疗加成叠加",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.extraHungerHeal")
    @Config.RangeDouble(min = 0)
    public static float extraHungerHeal = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Extra Hunger Healing (Percentage Bonus)",
            "     Percentage of maximum health restored when regenerating from hunger",
            "     This is a percentage of YOUR MAX HEALTH:",
            "     • 0.00 = No extra healing (default)",
            "     • 0.05 = Restore 5% of max health per tick",
            "     • 0.10 = Restore 10% of max health per tick",
            "     Example: Player with 100 max HP and 0.05 setting restores 5 HP",
            "     Stacks with flat hunger healing bonus",
            "",
            "[中文] 饥饿值恢复额外治疗(百分比加成)",
            "     饥饿值恢复时额外恢复的最大生命百分比",
            "     这是你最大生命值的百分比:",
            "     • 0.00 = 无额外治疗(默认)",
            "     • 0.05 = 每刻恢复5%最大生命",
            "     • 0.10 = 每刻恢复10%最大生命",
            "     例: 最大生命100的玩家，设置0.05时恢复5点生命",
            "     与固定饥饿值治疗加成叠加",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.extraHungerPercentageHeal")
    @Config.RangeDouble(min = 0)
    public static float extraHungerPercentageHeal = 0;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Hunger Damage Decay (Damage Reduction Per Missing Hunger Point)",
            "     Percentage of damage reduced for each hunger point below 20",
            "     • 0.00 = No damage reduction from hunger (default)",
            "     • 0.05 = 5% less damage per missing hunger point",
            "     • 0.10 = 10% less damage per missing hunger point",
            "     Example with 0.05 setting:",
            "     - 20 hunger = 100% damage (0 missing points)",
            "     - 15 hunger = 75% damage (5 missing × 5% = 25% reduction)",
            "     - 10 hunger = 50% damage (10 missing × 5% = 50% reduction)",
            "     Maximum reduction is capped by hungerDamageDecayLimit",
            "",
            "[中文] 饥饿伤害衰减(每点缺失饥饿值的伤害减少)",
            "     每少于20的饥饿值点数减少的伤害百分比",
            "     • 0.00 = 饥饿不减少伤害(默认)",
            "     • 0.05 = 每少1点饥饿值减少5%伤害",
            "     • 0.10 = 每少1点饥饿值减少10%伤害",
            "     以0.05设置为例:",
            "     - 20饥饿值 = 100%伤害(缺失0点)",
            "     - 15饥饿值 = 75%伤害(缺失5点 × 5% = 减少25%)",
            "     - 10饥饿值 = 50%伤害(缺失10点 × 5% = 减少50%)",
            "     最大减少量受hungerDamageDecayLimit限制",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.hungerDamageDecay")
    @Config.RangeDouble(min = 0)
    public static float hungerDamageDecay = 0.05f;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Maximum Hunger Damage Decay (Maximum Damage Reduction Cap)",
            "     Maximum percentage of damage that can be reduced by hunger",
            "     Prevents damage from becoming too low:",
            "     • 0.00 = No maximum reduction (can reduce to 0 damage)",
            "     • 0.50 = Maximum 50% damage reduction",
            "     • 0.70 = Maximum 70% damage reduction (default)",
            "     Example: With 0.70 limit and 0.05 decay per point:",
            "     - Even at 0 hunger, you still deal at least 30% damage",
            "     - Without this limit, 0 hunger would deal 0% damage",
            "",
            "[中文] 最大饥饿伤害衰减(最大伤害减少上限)",
            "     饥饿可以减少的最大伤害百分比",
            "     防止伤害降得过低:",
            "     • 0.00 = 无上限(可减少到0伤害)",
            "     • 0.50 = 最多减少50%伤害",
            "     • 0.70 = 最多减少70%伤害(默认)",
            "     例: 上限0.70，每点衰减0.05:",
            "     - 即使0饥饿值，仍能造成至少30%伤害",
            "     - 没有此上限，0饥饿值会造成0%伤害",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.hungerDamageDecayLimit")
    @Config.RangeDouble(min = 0)
    public static float hungerDamageDecayLimit = 0.05f;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Enable Detailed Debug Logging",
            "     Enables comprehensive logging of all combat mechanics and calculations",
            "     When enabled, logs include:",
            "     • All damage calculations (before and after modifiers)",
            "     • Critical hit calculations and results",
            "     • Attribute value calculations",
            "     • Healing calculations",
            "     • Event triggers (invulnerability, lifesteal, etc.)",
            "     WARNING: This generates EXTENSIVE log output!",
            "     Only enable this for debugging or troubleshooting issues",
            "     Performance impact is minimal but log files will grow rapidly",
            "",
            "[中文] 启用详细调试日志",
            "     启用所有战斗机制和计算的详细日志记录",
            "     启用时，日志包括:",
            "     • 所有伤害计算(修改前后)",
            "     • 暴击计算和结果",
            "     • 属性值计算",
            "     • 治疗计算",
            "     • 事件触发(无敌时间、吸血等)",
            "     警告: 这会产生大量日志输出!",
            "     仅在调试或排查问题时启用",
            "     对性能影响很小，但日志文件会快速增长",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".battle.enableDetailedLogging")
    public static boolean enableDetailedLogging = false;

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onConfigChanged(@Nonnull ConfigChangedEvent.OnConfigChangedEvent evt) {
        if (evt.getModID().equals(Reference.MOD_ID)) {
            ConfigManager.sync(Reference.MOD_ID, Config.Type.INSTANCE);
        }
    }
}