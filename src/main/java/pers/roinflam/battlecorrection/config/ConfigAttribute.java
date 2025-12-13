// 文件：ConfigAttribute.java
// 路径：src/main/java/pers/roinflam/battlecorrection/config/ConfigAttribute.java
package pers.roinflam.battlecorrection.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.utils.Reference;

@Mod.EventBusSubscriber
@Config(modid = Reference.MOD_ID, category = "Attribute")
@Config.LangKey("config." + Reference.MOD_ID + ".general.attribute")
public final class ConfigAttribute {

    @Config.Comment("Increases the magic damage dealt, such as damage potions, or other modded staffs.")
    public static float magicDamage = 0;

    @Config.Comment("Increases the arrow damage dealt.")
    public static float arrowDamage = 0;

    @Config.Comment("Which increases the damage dealt by projectiles other than arrows, such as bullets in the gun mod.")
    public static float projectileDamage = 0;

    @Config.Comment("Improve the health of its own recovery, such as natural blood recovery, instant treatment and so on.")
    public static float restoreHeal = 0;

    @Config.Comment("According to the current probability, may be immune to this attack, up to 100%.")
    public static float immuneDamage = 0;

    @Config.Comment("Increase bow drawing speed.")
    public static float bowSpeed = 0;

    @Config.Comment("Increase the speed of using items, such as eating food, drinking potions.")
    public static float preparationSpeed = 0;

    @Config.Comment("Restores health equal to the value of melee damage (requires a full attack to trigger this effect)")
    @Config.RangeDouble(min = 0)
    public static float bloodthirsty = 0;

    @Config.Comment("Restores health equal to the damage dealt (melee attack requires full attack to trigger this effect)")
    @Config.RangeDouble(min = 0)
    public static float almightyBloodthirsty = 0;

    @Config.Comment("Ignore the damage of this value, if the value exceeds the attribute, the corresponding damage will be reduced.")
    @Config.RangeDouble(min = 0)
    public static float ignoreDamage = 0;

    @Config.Comment("Additional damage multiplier for vanilla critical hits (falling attack). Default 0 means vanilla 150% damage (50% bonus).")
    @Config.RangeDouble(min = 0)
    public static float vanillaCriticalHitDamage = 0;

    @Config.Comment("Custom critical hit chance (percentage). Default 2% base chance. Range: 0-100.")
    @Config.RangeDouble(min = 0, max = 100)
    public static float customCriticalChance = 2;

    @Config.Comment("Custom critical hit damage multiplier. Default 2.0 means 200% damage (100% bonus). Minimum is 1.0 (100% damage).")
    @Config.RangeDouble(min = 1)
    public static float customCriticalDamage = 2;

    @Config.Comment("Improve the height of the jump, can refer to the original jump lift effect 0.75 level = 0.1 value.")
    @Config.RangeDouble(min = 0)
    public static float jumpLift = 0;

    @Config.Comment("Ignore this value of fall damage, if the value exceeds the attribute will be reduced corresponding damage.")
    @Config.RangeDouble(min = 0)
    public static float reducedFallDamage = 0;
}