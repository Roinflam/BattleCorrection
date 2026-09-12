package pers.roinflam.battlecorrection.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 受伤事件监听器
 * 处理PVP伤害调整、受击无敌时间修改和攻击冷却开关
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class InjuryEventListener {

    public static final UUID ATTACK_COOLDOWN_ID = UUID.fromString("a05141e5-4898-7440-0615-9ac825922a2a");
    public static final String ATTACK_COOLDOWN_NAME = "battlecorrection.attack_cooldown";

    /**
     * 关闭攻击冷却时加到攻击速度上的数值
     * 原版攻击速度属性的上限就是 1024，再大也会被截断；用它代替原来的 Double.MAX_VALUE / 2，
     * 避免遇到放开属性上限的模组时算出 0/0 这类异常值
     */
    private static final double NO_COOLDOWN_ATTACK_SPEED = 1024.0D;

    /**
     * 原版在"新的一击"后写入 invulnerableTime 的值
     */
    private static final int VANILLA_FRESH_HURT_TIME = 20;

    /**
     * 原版的"补差价"门槛：invulnerableTime 大于它时，新伤害只结算比上一下高出的部分
     * 所以原版真正的无敌时间 = 20 - 10 = 10 tick
     */
    private static final int VANILLA_HURT_THRESHOLD = 10;

    /**
     * 有效无敌时间的上限（tick），防止配置填了极大值导致整数溢出
     */
    private static final long MAX_EFFECTIVE_HURT_TIME = 72000L;

    /**
     * 处理攻击事件 - 自伤与PVP拦截
     * <p>
     * 自伤（伤害来源就是自己）只看自伤倍率，不再参与PVP判定：
     * 旧版本在 pvp = 0 时会把自伤也一起拦掉，导致 pvpHurtItself 失效。
     *
     * @param evt 生物攻击事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide() || !(evt.getEntity() instanceof Player victim)) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        @Nullable Entity sourceEntity = damageSource.getEntity();

        // 自伤检测
        if (victim.equals(sourceEntity)) {
            if (ConfigBattle.PVP_HURT_ITSELF.get() <= 0) {
                evt.setCanceled(true);
                if (LogUtil.isDetailed()) {
                    LogUtil.debugEvent("PVP自伤阻止", victim.getName().getString(),
                            String.format("阻止了玩家的自我伤害，配置倍率: %.2f",
                                    ConfigBattle.PVP_HURT_ITSELF.get()));
                }
            }
            return;
        }

        // PVP检测
        if (ConfigBattle.PVP.get() <= 0 && sourceEntity instanceof Player attacker) {
            evt.setCanceled(true);
            if (LogUtil.isDetailed()) {
                LogUtil.debugEvent("PVP伤害阻止", victim.getName().getString(),
                        String.format("阻止了来自 %s 的PVP伤害，配置倍率: %.2f",
                                attacker.getName().getString(), ConfigBattle.PVP.get()));
            }
        }
    }

    /**
     * 处理伤害事件 - PVP伤害倍率和受击无敌时间
     *
     * @param evt 生物伤害事件（护甲计算之后触发）
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        LivingEntity hurter = evt.getEntity();
        if (hurter.level().isClientSide()) {
            return;
        }

        if (hurter instanceof Player) {
            applyPvpMultiplier(evt, hurter);
            adjustHurtTime(hurter, ConfigBattle.HURT_TIME_PLAYER.get(), "玩家无敌时间调整");
        } else {
            adjustHurtTime(hurter, ConfigBattle.HURT_TIME_ENTITY.get(), "实体无敌时间调整");
        }
    }

    /**
     * 对玩家受到的伤害应用 自伤倍率 / PVP倍率
     *
     * @param evt    生物伤害事件
     * @param hurter 受伤的玩家
     */
    private static void applyPvpMultiplier(@Nonnull LivingDamageEvent evt, @Nonnull LivingEntity hurter) {
        @Nullable Entity sourceEntity = evt.getSource().getEntity();

        float multiplier;
        String modificationName;
        if (hurter.equals(sourceEntity)) {
            multiplier = ConfigBattle.PVP_HURT_ITSELF.get().floatValue();
            modificationName = "PVP自伤倍率";
        } else if (sourceEntity instanceof Player) {
            multiplier = ConfigBattle.PVP.get().floatValue();
            modificationName = "PVP伤害倍率";
        } else {
            return;
        }

        float originalDamage = evt.getAmount();
        float newDamage = originalDamage * multiplier;
        if (newDamage == originalDamage) {
            return;
        }
        evt.setAmount(newDamage);

        if (LogUtil.isDetailed()) {
            LogUtil.debugDamage("PVP伤害调整", sourceEntity.getName().getString(), hurter.getName().getString(),
                    originalDamage, newDamage, String.format("%s: %.2fx", modificationName, multiplier));
        }
    }

    /**
     * 按配置调整受击无敌时间
     * <p>
     * 原版机制：新的一击会把 invulnerableTime 设为 20，之后每 tick 减 1；只要它还大于 10，
     * 新伤害只能"补差价"。所以真正的无敌时间是 20 - 10 = 10 tick。
     * <p>
     * 配置含义：有效无敌时间 = 10 tick × (1 + 配置值)，写回字段时要加上 10 的门槛。
     * 旧版本直接写成 当前值/2 × (1+配置)，默认配置 0 时刚好等于门槛 10，无敌时间被整个清零。
     * <p>
     * 只在"新的一击"（字段恰好是原版刚写入的 20）时调整，无敌期内补差价的伤害不重复调整；
     * 配置为 0 时完全不动原版数值。
     *
     * @param hurter  受伤的实体
     * @param config  配置值（-0.99 ~ 无穷大）
     * @param logName 详细日志里的事件名
     */
    private static void adjustHurtTime(@Nonnull LivingEntity hurter, double config, @Nonnull String logName) {
        if (config == 0 || hurter.invulnerableTime != VANILLA_FRESH_HURT_TIME) {
            return;
        }

        long effective = Math.round(VANILLA_HURT_THRESHOLD * (1.0D + config));
        effective = Math.max(0L, Math.min(effective, MAX_EFFECTIVE_HURT_TIME));
        hurter.invulnerableTime = VANILLA_HURT_THRESHOLD + (int) effective;

        if (LogUtil.isDetailed()) {
            LogUtil.debugEvent(logName, hurter.getName().getString(),
                    String.format("有效无敌时间: 原版 %d tick -> %d tick (倍率: 1 + %.2f)",
                            VANILLA_HURT_THRESHOLD, effective, config));
        }
    }

    /**
     * 处理实体加入世界事件 - 攻击冷却开关
     * <p>
     * 现在使用临时修饰符（不写进存档），卸载模组后玩家不会残留"永远没有攻击冷却"。
     * 旧版本用的是永久修饰符，这里无论开关状态都先按同一个 UUID 移除一次，
     * 老存档里残留的永久修饰符会在玩家下次进入世界时被自动清掉。
     *
     * @param evt 实体加入世界事件
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(@Nonnull EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide() || !(evt.getEntity() instanceof Player player)) {
            return;
        }

        AttributeInstance attributeInstance = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attributeInstance == null) {
            return;
        }

        boolean hadModifier = attributeInstance.getModifier(ATTACK_COOLDOWN_ID) != null;
        if (hadModifier) {
            attributeInstance.removeModifier(ATTACK_COOLDOWN_ID);
        }

        if (!ConfigBattle.ATTACK_COOLDOWN.get()) {
            // 禁用攻击冷却
            attributeInstance.addTransientModifier(new AttributeModifier(
                    ATTACK_COOLDOWN_ID,
                    ATTACK_COOLDOWN_NAME,
                    NO_COOLDOWN_ATTACK_SPEED,
                    AttributeModifier.Operation.ADDITION
            ));
            if (LogUtil.isDetailed()) {
                LogUtil.debugEvent("攻击冷却禁用", player.getName().getString(), "移除了攻击冷却限制");
            }
        } else if (hadModifier && LogUtil.isDetailed()) {
            LogUtil.debugEvent("攻击冷却启用", player.getName().getString(), "恢复了攻击冷却机制");
        }
    }
}
