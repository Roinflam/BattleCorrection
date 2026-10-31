package pers.roinflam.battlecorrection.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
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
    public static final UUID ID = UUID.fromString("a05141e5-4898-7440-0615-9ac825922a2a");
    public static final String NAME = "battlecorrection.attack_cooldown";

    /**
     * 关闭攻击冷却时加到攻击速度上的数值
     * 原版攻击速度属性的上限就是 1024，再大也会被截断；不用 Double.MAX_VALUE 这类极大值，
     * 避免遇到放开属性上限的模组时算出 0/0 这类异常值
     */
    private static final double NO_COOLDOWN_ATTACK_SPEED = 1024.0D;

    /**
     * 有效无敌时间的上限（tick），防止配置填了极大值导致整数溢出
     */
    private static final long MAX_EFFECTIVE_HURT_TIME = 72000L;

    /**
     * 处理攻击事件 - 自伤与PVP拦截
     * <p>
     * 自伤（伤害来源就是自己）只看自伤倍率，不参与PVP判定，
     * 否则 pvp = 0 时会把自伤也一起拦掉，pvpHurtItself 就失效了。
     *
     * @param evt 生物攻击事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote || !(evt.getEntity() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer victim = (EntityPlayer) evt.getEntity();

        DamageSource damageSource = evt.getSource();
        @Nullable Entity sourceEntity = damageSource.getTrueSource();

        // 自伤检测
        if (victim.equals(sourceEntity)) {
            if (ConfigBattle.pvpHurtItself <= 0) {
                evt.setCanceled(true);
                if (LogUtil.isDetailed()) {
                    LogUtil.debugEvent("PVP自伤阻止", victim.getName(),
                            String.format("阻止了玩家的自我伤害，配置倍率: %.2f", ConfigBattle.pvpHurtItself));
                }
            }
            return;
        }

        // PVP检测
        if (ConfigBattle.pvp <= 0 && sourceEntity instanceof EntityPlayer) {
            evt.setCanceled(true);
            if (LogUtil.isDetailed()) {
                LogUtil.debugEvent("PVP伤害阻止", victim.getName(),
                        String.format("阻止了来自 %s 的PVP伤害，配置倍率: %.2f", sourceEntity.getName(), ConfigBattle.pvp));
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
        EntityLivingBase hurter = evt.getEntityLiving();
        if (hurter.world.isRemote) {
            return;
        }

        if (hurter instanceof EntityPlayer) {
            applyPvpMultiplier(evt, hurter);
            adjustHurtTime(hurter, ConfigBattle.hurtTimePlayer, "玩家无敌时间调整");
        } else {
            adjustHurtTime(hurter, ConfigBattle.hurtTimeEntity, "实体无敌时间调整");
        }
    }

    /**
     * 对玩家受到的伤害应用 自伤倍率 / PVP倍率
     *
     * @param evt    生物伤害事件
     * @param hurter 受伤的玩家
     */
    private static void applyPvpMultiplier(@Nonnull LivingDamageEvent evt, @Nonnull EntityLivingBase hurter) {
        @Nullable Entity sourceEntity = evt.getSource().getTrueSource();

        float multiplier;
        String modificationName;
        if (hurter.equals(sourceEntity)) {
            multiplier = ConfigBattle.pvpHurtItself;
            modificationName = "PVP自伤倍率";
        } else if (sourceEntity instanceof EntityPlayer) {
            multiplier = ConfigBattle.pvp;
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
            LogUtil.debugDamage("PVP伤害调整", sourceEntity.getName(), hurter.getName(),
                    originalDamage, newDamage, String.format("%s: %.2fx", modificationName, multiplier));
        }
    }

    /**
     * 按配置调整受击无敌时间
     * <p>
     * 原版机制：新的一击会把 hurtResistantTime 设为 maxHurtResistantTime（默认 20），之后每 tick 减 1；
     * 只要它还大于 maxHurtResistantTime / 2（10），新伤害只能"补差价"。所以真正的无敌时间是 20 - 10 = 10 tick。
     * <p>
     * 配置含义：有效无敌时间 = 门槛 × (1 + 配置值)，写回字段时要加上门槛。
     * 门槛按实体自己的 maxHurtResistantTime 算，改过这个字段的模组生物同样按比例缩放。
     * 直接写成 maxHurtResistantTime/2 × (1+配置) 的话，默认配置 0 时刚好等于门槛 10，无敌时间会被整个清零。
     * <p>
     * LivingDamageEvent 在 damageEntity() 里触发，此时 attackEntityFrom() 已经写好了字段：
     * 只在"新的一击"（字段恰好是原版刚写入的 20）时调整，无敌期内补差价的伤害不重复调整；
     * 配置为 0 时完全不动原版数值。
     *
     * @param hurter  受伤的实体
     * @param config  配置值（-0.99 ~ 无穷大）
     * @param logName 详细日志里的事件名
     */
    private static void adjustHurtTime(@Nonnull EntityLivingBase hurter, double config, @Nonnull String logName) {
        int freshHurtTime = hurter.maxHurtResistantTime;
        if (config == 0 || freshHurtTime <= 0 || hurter.hurtResistantTime != freshHurtTime) {
            return;
        }

        int threshold = freshHurtTime / 2;
        long effective = Math.round(threshold * (1.0D + config));
        effective = Math.max(0L, Math.min(effective, MAX_EFFECTIVE_HURT_TIME));
        hurter.hurtResistantTime = threshold + (int) effective;

        if (LogUtil.isDetailed()) {
            LogUtil.debugEvent(logName, hurter.getName(),
                    String.format("有效无敌时间: 原版 %d tick -> %d tick (倍率: 1 + %.2f)",
                            freshHurtTime - threshold, effective, config));
        }
    }

    /**
     * 处理实体加入世界事件 - 攻击冷却开关
     * <p>
     * 使用不写进存档的修饰符（setSaved(false)），卸载模组后玩家不会残留"永远没有攻击冷却"。
     * 早期版本用的是会存档的修饰符，所以无论开关状态都先按同一个 UUID 移除一次，
     * 老存档里残留的修饰符会在玩家下次进入世界时被清掉。
     *
     * @param evt 实体加入世界事件
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(@Nonnull EntityJoinWorldEvent evt) {
        if (evt.getWorld().isRemote || !(evt.getEntity() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) evt.getEntity();

        IAttributeInstance attributeInstance = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
        if (attributeInstance == null) {
            return;
        }

        boolean hadModifier = attributeInstance.getModifier(ID) != null;
        if (hadModifier) {
            attributeInstance.removeModifier(ID);
        }

        if (!ConfigBattle.attackCooldown) {
            // 禁用攻击冷却
            AttributeModifier modifier = new AttributeModifier(ID, NAME, NO_COOLDOWN_ATTACK_SPEED, 0);
            modifier.setSaved(false);
            attributeInstance.applyModifier(modifier);
            if (LogUtil.isDetailed()) {
                LogUtil.debugEvent("攻击冷却禁用", player.getName(), "移除了攻击冷却限制");
            }
        } else if (hadModifier && LogUtil.isDetailed()) {
            LogUtil.debugEvent("攻击冷却启用", player.getName(), "恢复了攻击冷却机制");
        }
    }
}
