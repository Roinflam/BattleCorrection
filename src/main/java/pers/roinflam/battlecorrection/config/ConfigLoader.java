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

@Mod.EventBusSubscriber
@Config(modid = Reference.MOD_ID)
@Config.LangKey("config." + Reference.MOD_ID + ".general")
public final class ConfigLoader {
    @Config.Comment("Set the player pvp damage multiplier, adjust this value will affect the final damage, such as 0.5 will be 50% of the original damage, if 0 is equivalent to prohibit pvp.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.pvp")
    @Config.RangeDouble(min = 0)
    public static float pvp = 1;
    @Config.Comment("Set the player pvp damage multiplier, adjust this value will affect the final damage, such as 0.5 will be 50% of the original damage, if 0 is equivalent to prohibit pvp.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.pvpHurtItself")
    @Config.RangeDouble(min = 0)
    public static float pvpHurtItself = 1;
    @Config.Comment("When enabled, attacks when not fully prepared will deal less damage than vanilla.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.comboCorrection")
    public static boolean comboCorrection = false;
    @Config.Comment("Set the duration of the creature's invulnerability. For example, 0.5 is 50% of the original time, and 0 is equivalent to disabling the invincibility time.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.hurtTimeEntity")
    @Config.RangeDouble(min = 0)
    public static float hurtTimeEntity = 1;
    @Config.Comment("Set the duration of the player invulnerability. For example, 0.5 is 50% of the original time, and 0 is equivalent to disabling the invincibility time.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.hurtTimePlayer")
    @Config.RangeDouble(min = 0)
    public static float hurtTimePlayer = 1;
    @Config.Comment("Disable will return the attack to the no attack cooldown in version 1.8.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.attackCooldown")
    public static boolean attackCooldown = true;

    @Config.Comment("Globally adjusted the damage of player melee attacks.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.playerMeleeAttack")
    @Config.RangeDouble(min = 0)
    public static float playerMeleeAttack = 1;
    @Config.Comment("Globally adjusted the damage of player remote attacks.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.playerRemoteAttack")
    @Config.RangeDouble(min = 0)
    public static float playerRemoteAttack = 1;
    @Config.Comment("Globally adjusted the damage of player magic attacks.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.playerMagicAttack")
    @Config.RangeDouble(min = 0)
    public static float playerMagicAttack = 1;

    @Config.Comment("Global adjustment of damage to players from melee attacks.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.playerSuffersMelee")
    @Config.RangeDouble(min = 0)
    public static float playerSuffersMelee = 1;
    @Config.Comment("Global adjustment of damage to players from remote attacks.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.playerSuffersRemote")
    @Config.RangeDouble(min = 0)
    public static float playerSuffersRemote = 1;
    @Config.Comment("Global adjustment of damage to players from magic attacks.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.playerSuffersMagic")
    @Config.RangeDouble(min = 0)
    public static float playerSuffersMagic = 1;

    @Config.Comment("Adjust the base maximum health of the entity, if it is 1, the base health is doubled, and -0.5, the base health is reduced by 50%.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.extraMaxHealth")
    @Config.RangeDouble(min = -0.99f)
    public static float extraMaxHealth = 0;

    @Config.Comment("The effect of the health value recovered by consuming hunger value will be increased additionally. If you fill in 5, you will recover an additional 5 points of health, for a total of 6 points.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.extraSaturationHeal")
    @Config.RangeDouble(min = 0)
    public static float extraSaturationHeal = 0;
    @Config.Comment("Increases the health effect of consuming saturation and recovers additionally. If it is set to 0.05, an additional 5% of the maximum health will be recovered.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.extraSaturationPercentageHeal")
    @Config.RangeDouble(min = 0)
    public static float extraSaturationPercentageHeal = 0;
    @Config.Comment("The effect of the health value recovered by consuming hunger value will be increased additionally. If you fill in 5, you will recover an additional 5 points of health, for a total of 6 points.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.extraHungerHeal")
    @Config.RangeDouble(min = 0)
    public static float extraHungerHeal = 0;
    @Config.Comment("Increases the health effect of consuming hunger and recovers additionally. If it is set to 0.05, an additional 5% of the maximum health will be recovered.")
    @Config.LangKey("config." + Reference.MOD_ID + ".general.extraHungerPercentageHeal")
    @Config.RangeDouble(min = 0)
    public static float extraHungerPercentageHeal = 0;

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onConfigChanged(@Nonnull ConfigChangedEvent.OnConfigChangedEvent evt) {
        if (evt.getModID().equals(Reference.MOD_ID)) {
            ConfigManager.sync(Reference.MOD_ID, Config.Type.INSTANCE);
        }
    }
}
