package pers.roinflam.battlecorrection.utils.util;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.utils.Reference;
import pers.roinflam.battlecorrection.utils.ReflectionCache;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 实体生物工具类
 * 提供挥砍蓄力记录、物品使用加速、强制击杀等功能
 * <p>
 * 挥砍蓄力的记录方式：原版 Player.attack() 会先按蓄力算伤害，然后立刻把蓄力清零，再去调用 target.hurt()。
 * 所以在 LivingHurtEvent / LivingDamageEvent 里直接读 getAttackStrengthScale() 永远是 0。
 * 这里改为在 AttackEntityEvent（attack() 第一行触发，此时还没清零）里记下蓄力值和当时的游戏刻，
 * 同一刻内的伤害事件读取这份记录。
 * <p>
 * 只在服务端记录：旧版本在 PlayerTickEvent 里两端都写同一张静态表，
 * 单人游戏时客户端线程和内置服务端线程会同时修改 HashMap。
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EntityLivingUtil {

    /**
     * 本刻没有挥砍记录时 {@link #getSwingStrength(Player)} 的返回值
     */
    public static final float NO_SWING = -1.0F;

    /**
     * 玩家UUID -> 最近一次挥砍记录（只在服务端线程访问）
     */
    private static final Map<UUID, SwingRecord> SWING_RECORDS = new HashMap<>();

    /**
     * 挥砍记录（每个玩家复用同一个对象，避免每次攻击都新建）
     */
    private static final class SwingRecord {
        /**
         * 挥砍时的蓄力值（0.0-1.0）
         */
        private float strength;
        /**
         * 挥砍发生时的游戏刻
         */
        private long gameTime;
    }

    /**
     * 玩家发起近战攻击 - 记录攻击前的蓄力值
     *
     * @param evt 攻击实体事件（在原版清零蓄力之前触发）
     */
    @SubscribeEvent
    public static void onAttackEntity(@Nonnull AttackEntityEvent evt) {
        Player player = evt.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        // 0.5F 与原版 Player.attack() 计算伤害时使用的参数一致
        float strength = player.getAttackStrengthScale(0.5F);
        if (Float.isNaN(strength)) {
            // 攻速被其他模组放开到极大值时可能出现 0/0，按满蓄力处理
            strength = 1.0F;
        }

        SwingRecord record = SWING_RECORDS.computeIfAbsent(player.getUUID(), uuid -> new SwingRecord());
        record.strength = strength;
        record.gameTime = player.level().getGameTime();
    }

    /**
     * 玩家登出事件 - 清理挥砍记录
     *
     * @param evt 玩家登出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(@Nonnull PlayerEvent.PlayerLoggedOutEvent evt) {
        SWING_RECORDS.remove(evt.getEntity().getUUID());
    }

    /**
     * 获取玩家本刻挥砍时的蓄力值
     * <p>
     * 只有当前这一刻内确实发生过近战挥砍才会返回有效值；荆棘反伤、模组技能等
     * 不经过原版挥砍流程的伤害会返回 {@link #NO_SWING}，由调用方决定如何处理。
     *
     * @param player 玩家（服务端实体）
     * @return 蓄力值（0.0-1.0），本刻没有挥砍时返回 {@link #NO_SWING}
     */
    public static float getSwingStrength(@Nonnull Player player) {
        SwingRecord record = SWING_RECORDS.get(player.getUUID());
        if (record == null || record.gameTime != player.level().getGameTime()) {
            return NO_SWING;
        }
        return record.strength;
    }

    /**
     * 判断玩家这次近战是否满蓄力（吸血类属性用）
     * <p>
     * 本刻有挥砍记录时用挥砍那一刻的蓄力；没有（荆棘、模组技能等）时退回用当前蓄力，与旧逻辑一致。
     *
     * @param player 玩家（服务端实体）
     * @return true = 满蓄力
     */
    public static boolean isFullyCharged(@Nonnull Player player) {
        float strength = getSwingStrength(player);
        if (strength < 0) {
            strength = player.getAttackStrengthScale(0.5F);
        }
        return strength >= 1.0F;
    }

    /**
     * 更新手持物品使用状态（对应1.12的updateActiveHand）
     * 通过反射调用 updatingUsingItem 方法来触发一次额外的物品使用tick
     * <p>
     * 这个方法会：
     * 1. 检查是否正在使用物品
     * 2. 触发物品的onUseTick
     * 3. 减少useItemRemaining计数
     * 4. 触发使用效果（粒子、声音等）
     *
     * @param livingEntity 生物实体
     */
    public static void updateHeld(@Nonnull LivingEntity livingEntity) {
        ReflectionCache.invokeUpdatingUsingItem(livingEntity);
    }

    /**
     * 强制杀死实体
     * 先用大额伤害走正常死亡流程（保留击杀归属和掉落），被免疫或取消时再强制死亡
     *
     * @param livingEntity 要杀死的实体，为 null 时不做任何事
     * @param damageSource 伤害源
     */
    public static void kill(@Nullable LivingEntity livingEntity, @Nonnull DamageSource damageSource) {
        if (livingEntity != null) {
            livingEntity.hurt(damageSource, livingEntity.getMaxHealth() * 100);
            if (livingEntity.isAlive()) {
                livingEntity.die(damageSource);
                livingEntity.setHealth(0);
            }
        }
    }
}
