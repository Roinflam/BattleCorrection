package pers.roinflam.battlecorrection.init;

import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import pers.roinflam.battlecorrection.utils.LogUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 模组自定义属性注册类
 * 负责将所有自定义属性注册到全局属性系统
 */
public class ModAttributes {

    /**
     * 所有自定义属性列表
     */
    private static final List<IAttribute> CUSTOM_ATTRIBUTES = new ArrayList<>();

    // 伤害增强属性
    public static final IAttribute MAGIC_DAMAGE = register(
            new RangedAttribute(null, "battlecorrection.magicDamage", 0, 0, Float.MAX_VALUE)
                    .setDescription("Extra Magic Damage")
                    .setShouldWatch(true)
    );

    public static final IAttribute ARROW_DAMAGE = register(
            new RangedAttribute(null, "battlecorrection.arrowDamage", 0, 0, Float.MAX_VALUE)
                    .setDescription("Extra Arrow Damage")
                    .setShouldWatch(true)
    );

    public static final IAttribute PROJECTILE_DAMAGE = register(
            new RangedAttribute(null, "battlecorrection.projectileDamage", 0, 0, Float.MAX_VALUE)
                    .setDescription("Extra Projectile Damage")
                    .setShouldWatch(true)
    );

    // 防御与减伤属性
    public static final IAttribute IMMUNE_DAMAGE = register(
            new RangedAttribute(null, "battlecorrection.immuneDamage", 1, 1, Float.MAX_VALUE)
                    .setDescription("Extra Immune Damage")
                    .setShouldWatch(true)
    );

    public static final IAttribute IGNORE_DAMAGE = register(
            new RangedAttribute(null, "battlecorrection.ignoreDamage", 0, 0, Float.MAX_VALUE)
                    .setDescription("Ignore Damage")
                    .setShouldWatch(true)
    );

    public static final IAttribute REDUCED_FALL_DAMAGE = register(
            new RangedAttribute(null, "battlecorrection.reducedFallDamage", 0, 0, Float.MAX_VALUE)
                    .setDescription("Reduced Fall Damage")
                    .setShouldWatch(true)
    );

    // 生命恢复属性
    public static final IAttribute RESTORE_HEAL = register(
            new RangedAttribute(null, "battlecorrection.restoreHeal", 1, 0, Float.MAX_VALUE)
                    .setDescription("Extra Restore Heal")
                    .setShouldWatch(true)
    );

    public static final IAttribute BLOODTHIRSTY = register(
            new RangedAttribute(null, "battlecorrection.bloodthirsty", 1, 1, Float.MAX_VALUE)
                    .setDescription("Bloodthirsty")
                    .setShouldWatch(true)
    );

    public static final IAttribute ALMIGHTY_BLOODTHIRSTY = register(
            new RangedAttribute(null, "battlecorrection.almightyBloodthirsty", 1, 1, Float.MAX_VALUE)
                    .setDescription("Almighty Bloodthirsty")
                    .setShouldWatch(true)
    );

    // 速度与机动性属性
    public static final IAttribute BOW_SPEED = register(
            new RangedAttribute(null, "battlecorrection.bowSpeed", 1, 0, Float.MAX_VALUE)
                    .setDescription("Extra Bow Speed")
                    .setShouldWatch(true)
    );

    public static final IAttribute PREPARATION_SPEED = register(
            new RangedAttribute(null, "battlecorrection.preparationSpeed", 1, 0, Float.MAX_VALUE)
                    .setDescription("Extra Preparation Speed")
                    .setShouldWatch(true)
    );

    public static final IAttribute JUMP_LIFT = register(
            new RangedAttribute(null, "battlecorrection.jumpLift", 0, 0, Float.MAX_VALUE)
                    .setDescription("Jump Lift")
                    .setShouldWatch(true)
    );

    // 暴击系统属性
    public static final IAttribute VANILLA_CRITICAL_HIT_DAMAGE = register(
            new RangedAttribute(null, "battlecorrection.vanillaCriticalHitDamage", 1, 1, Float.MAX_VALUE)
                    .setDescription("Vanilla Critical Hit Damage")
                    .setShouldWatch(true)
    );

    public static final IAttribute CUSTOM_CRITICAL_CHANCE = register(
            new RangedAttribute(null, "battlecorrection.customCriticalChance", 0, 0, Float.MAX_VALUE)
                    .setDescription("Custom Critical Hit Chance (Percentage)")
                    .setShouldWatch(true)
    );

    public static final IAttribute CUSTOM_CRITICAL_DAMAGE = register(
            new RangedAttribute(null, "battlecorrection.customCriticalDamage", 0, 0, Float.MAX_VALUE)
                    .setDescription("Custom Critical Hit Damage (Multiplier)")
                    .setShouldWatch(true)
    );

    /**
     * 注册单个属性
     */
    private static IAttribute register(IAttribute attribute) {
        CUSTOM_ATTRIBUTES.add(attribute);
        return attribute;
    }

    /**
     * 初始化所有自定义属性
     * <p>
     * 1.12.2 没有全局属性注册表：物品 NBT 里的 AttributeModifiers 按属性名字符串保存，
     * 生效时按名字去实体自己的属性表里找。所以这里只需要让静态字段完成初始化，
     * 真正的注册在实体加入世界时做（BattleCorrection#onEntityJoinWorld）。
     */
    public static void init() {
        LogUtil.info("已加载 " + CUSTOM_ATTRIBUTES.size() + " 个自定义属性，实体进入世界时注册");
    }

    /**
     * 获取所有自定义属性
     */
    public static List<IAttribute> getAllAttributes() {
        return new ArrayList<>(CUSTOM_ATTRIBUTES);
    }
}