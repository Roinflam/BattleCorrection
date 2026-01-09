// 文件：ClothConfigScreen.java
// 路径：src/main/java/pers/roinflam/battlecorrection/config/ClothConfigScreen.java
package pers.roinflam.battlecorrection.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config 配置界面
 */
public class ClothConfigScreen {

    public static Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.battlecorrection.title"))
                .setSavingRunnable(() -> {
                    ConfigBattle.SPEC.save();
                    ConfigAttribute.SPEC.save();
                });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // ===== PVP设置 =====
        ConfigCategory pvpCategory = builder.getOrCreateCategory(
                Component.translatable("config.battlecorrection.category.pvp"));

        pvpCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.pvp"),
                        ConfigBattle.PVP.get())
                .setDefaultValue(1.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.battlecorrection.pvp.tooltip"))
                .setSaveConsumer(ConfigBattle.PVP::set)
                .build());

        pvpCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.pvpHurtItself"),
                        ConfigBattle.PVP_HURT_ITSELF.get())
                .setDefaultValue(1.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.battlecorrection.pvpHurtItself.tooltip"))
                .setSaveConsumer(ConfigBattle.PVP_HURT_ITSELF::set)
                .build());

        // ===== 战斗机制 =====
        ConfigCategory combatCategory = builder.getOrCreateCategory(
                Component.translatable("config.battlecorrection.category.combat"));

        combatCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.battlecorrection.comboCorrection"),
                        ConfigBattle.COMBO_CORRECTION.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.battlecorrection.comboCorrection.tooltip"))
                .setSaveConsumer(ConfigBattle.COMBO_CORRECTION::set)
                .build());

        combatCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.battlecorrection.attackCooldown"),
                        ConfigBattle.ATTACK_COOLDOWN.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.battlecorrection.attackCooldown.tooltip"))
                .setSaveConsumer(ConfigBattle.ATTACK_COOLDOWN::set)
                .build());

        combatCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.hurtTimeEntity"),
                        ConfigBattle.HURT_TIME_ENTITY.get())
                .setDefaultValue(0.0)
                .setMin(-0.99)
                .setTooltip(Component.translatable("config.battlecorrection.hurtTimeEntity.tooltip"))
                .setSaveConsumer(ConfigBattle.HURT_TIME_ENTITY::set)
                .build());

        combatCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.hurtTimePlayer"),
                        ConfigBattle.HURT_TIME_PLAYER.get())
                .setDefaultValue(0.0)
                .setMin(-0.99)
                .setTooltip(Component.translatable("config.battlecorrection.hurtTimePlayer.tooltip"))
                .setSaveConsumer(ConfigBattle.HURT_TIME_PLAYER::set)
                .build());

        // ===== 玩家攻击 =====
        ConfigCategory attackCategory = builder.getOrCreateCategory(
                Component.translatable("config.battlecorrection.category.attack"));

        attackCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.playerMeleeAttack"),
                        ConfigBattle.PLAYER_MELEE_ATTACK.get())
                .setDefaultValue(1.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigBattle.PLAYER_MELEE_ATTACK::set)
                .build());

        attackCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.playerArrowAttack"),
                        ConfigBattle.PLAYER_ARROW_ATTACK.get())
                .setDefaultValue(1.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigBattle.PLAYER_ARROW_ATTACK::set)
                .build());

        attackCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.playerProjectileAttack"),
                        ConfigBattle.PLAYER_PROJECTILE_ATTACK.get())
                .setDefaultValue(1.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigBattle.PLAYER_PROJECTILE_ATTACK::set)
                .build());

        attackCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.playerMagicAttack"),
                        ConfigBattle.PLAYER_MAGIC_ATTACK.get())
                .setDefaultValue(1.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigBattle.PLAYER_MAGIC_ATTACK::set)
                .build());

        // ===== 玩家防御 =====
        ConfigCategory defenseCategory = builder.getOrCreateCategory(
                Component.translatable("config.battlecorrection.category.defense"));

        defenseCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.playerSuffersMelee"),
                        ConfigBattle.PLAYER_SUFFERS_MELEE.get())
                .setDefaultValue(1.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigBattle.PLAYER_SUFFERS_MELEE::set)
                .build());

        defenseCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.playerSuffersArrow"),
                        ConfigBattle.PLAYER_SUFFERS_ARROW.get())
                .setDefaultValue(1.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigBattle.PLAYER_SUFFERS_ARROW::set)
                .build());

        defenseCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.playerSuffersProjectile"),
                        ConfigBattle.PLAYER_SUFFERS_PROJECTILE.get())
                .setDefaultValue(1.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigBattle.PLAYER_SUFFERS_PROJECTILE::set)
                .build());

        defenseCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.playerSuffersMagic"),
                        ConfigBattle.PLAYER_SUFFERS_MAGIC.get())
                .setDefaultValue(1.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigBattle.PLAYER_SUFFERS_MAGIC::set)
                .build());

        // ===== 实体属性 =====
        ConfigCategory entityCategory = builder.getOrCreateCategory(
                Component.translatable("config.battlecorrection.category.entity"));

        entityCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.extraMaxHealth"),
                        ConfigBattle.EXTRA_MAX_HEALTH.get())
                .setDefaultValue(0.0)
                .setMin(-0.99)
                .setTooltip(Component.translatable("config.battlecorrection.extraMaxHealth.tooltip"))
                .setSaveConsumer(ConfigBattle.EXTRA_MAX_HEALTH::set)
                .build());

        // ===== 饥饿系统 =====
        ConfigCategory hungerCategory = builder.getOrCreateCategory(
                Component.translatable("config.battlecorrection.category.hunger"));

        hungerCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.extraSaturationHeal"),
                        ConfigBattle.EXTRA_SATURATION_HEAL.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigBattle.EXTRA_SATURATION_HEAL::set)
                .build());

        hungerCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.extraSaturationPercentageHeal"),
                        ConfigBattle.EXTRA_SATURATION_PERCENTAGE_HEAL.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigBattle.EXTRA_SATURATION_PERCENTAGE_HEAL::set)
                .build());

        hungerCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.extraHungerHeal"),
                        ConfigBattle.EXTRA_HUNGER_HEAL.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigBattle.EXTRA_HUNGER_HEAL::set)
                .build());

        hungerCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.extraHungerPercentageHeal"),
                        ConfigBattle.EXTRA_HUNGER_PERCENTAGE_HEAL.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigBattle.EXTRA_HUNGER_PERCENTAGE_HEAL::set)
                .build());

        hungerCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.hungerDamageDecay"),
                        ConfigBattle.HUNGER_DAMAGE_DECAY.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigBattle.HUNGER_DAMAGE_DECAY::set)
                .build());

        hungerCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.hungerDamageDecayLimit"),
                        ConfigBattle.HUNGER_DAMAGE_DECAY_LIMIT.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setMax(1.0)
                .setSaveConsumer(ConfigBattle.HUNGER_DAMAGE_DECAY_LIMIT::set)
                .build());

        // ===== RPG属性 - 伤害 =====
        ConfigCategory damageCategory = builder.getOrCreateCategory(
                Component.translatable("config.battlecorrection.category.damage"));

        damageCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.magicDamage"),
                        ConfigAttribute.MAGIC_DAMAGE.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigAttribute.MAGIC_DAMAGE::set)
                .build());

        damageCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.arrowDamage"),
                        ConfigAttribute.ARROW_DAMAGE.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigAttribute.ARROW_DAMAGE::set)
                .build());

        damageCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.projectileDamage"),
                        ConfigAttribute.PROJECTILE_DAMAGE.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigAttribute.PROJECTILE_DAMAGE::set)
                .build());

        // ===== RPG属性 - 治疗 =====
        ConfigCategory healingCategory = builder.getOrCreateCategory(
                Component.translatable("config.battlecorrection.category.healing"));

        healingCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.restoreHeal"),
                        ConfigAttribute.RESTORE_HEAL.get())
                .setDefaultValue(1.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigAttribute.RESTORE_HEAL::set)
                .build());

        healingCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.bloodthirsty"),
                        ConfigAttribute.BLOODTHIRSTY.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigAttribute.BLOODTHIRSTY::set)
                .build());

        healingCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.almightyBloodthirsty"),
                        ConfigAttribute.ALMIGHTY_BLOODTHIRSTY.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigAttribute.ALMIGHTY_BLOODTHIRSTY::set)
                .build());

        // ===== RPG属性 - 防御 =====
        ConfigCategory rpgDefenseCategory = builder.getOrCreateCategory(
                Component.translatable("config.battlecorrection.category.rpgDefense"));

        rpgDefenseCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.immuneDamage"),
                        ConfigAttribute.IMMUNE_DAMAGE.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setMax(100.0)
                .setSaveConsumer(ConfigAttribute.IMMUNE_DAMAGE::set)
                .build());

        rpgDefenseCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.ignoreDamage"),
                        ConfigAttribute.IGNORE_DAMAGE.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigAttribute.IGNORE_DAMAGE::set)
                .build());

        rpgDefenseCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.reducedFallDamage"),
                        ConfigAttribute.REDUCED_FALL_DAMAGE.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigAttribute.REDUCED_FALL_DAMAGE::set)
                .build());

        // ===== RPG属性 - 速度 =====
        ConfigCategory speedCategory = builder.getOrCreateCategory(
                Component.translatable("config.battlecorrection.category.speed"));

        speedCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.bowSpeed"),
                        ConfigAttribute.BOW_SPEED.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigAttribute.BOW_SPEED::set)
                .build());

        speedCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.preparationSpeed"),
                        ConfigAttribute.PREPARATION_SPEED.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigAttribute.PREPARATION_SPEED::set)
                .build());

        speedCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.jumpLift"),
                        ConfigAttribute.JUMP_LIFT.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigAttribute.JUMP_LIFT::set)
                .build());

        // ===== RPG属性 - 暴击 =====
        ConfigCategory criticalCategory = builder.getOrCreateCategory(
                Component.translatable("config.battlecorrection.category.critical"));

        criticalCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.vanillaCriticalHitDamage"),
                        ConfigAttribute.VANILLA_CRITICAL_HIT_DAMAGE.get())
                .setDefaultValue(0.5)
                .setMin(0.0)
                .setSaveConsumer(ConfigAttribute.VANILLA_CRITICAL_HIT_DAMAGE::set)
                .build());

        criticalCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.customCriticalChance"),
                        ConfigAttribute.CUSTOM_CRITICAL_CHANCE.get())
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigAttribute.CUSTOM_CRITICAL_CHANCE::set)
                .build());

        criticalCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.customCriticalDamage"),
                        ConfigAttribute.CUSTOM_CRITICAL_DAMAGE.get())
                .setDefaultValue(2.0)
                .setMin(1.0)
                .setSaveConsumer(ConfigAttribute.CUSTOM_CRITICAL_DAMAGE::set)
                .build());

        criticalCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.battlecorrection.criticalOverflowConversion"),
                        ConfigAttribute.CRITICAL_OVERFLOW_CONVERSION.get())
                .setDefaultValue(1.0)
                .setMin(0.0)
                .setSaveConsumer(ConfigAttribute.CRITICAL_OVERFLOW_CONVERSION::set)
                .build());

        // ===== 调试 =====
        ConfigCategory debugCategory = builder.getOrCreateCategory(
                Component.translatable("config.battlecorrection.category.debug"));

        debugCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.battlecorrection.enableDetailedLogging"),
                        ConfigBattle.ENABLE_DETAILED_LOGGING.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.battlecorrection.enableDetailedLogging.tooltip"))
                .setSaveConsumer(ConfigBattle.ENABLE_DETAILED_LOGGING::set)
                .build());

        return builder.build();
    }
}