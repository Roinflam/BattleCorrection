package pers.roinflam.battlecorrection.utils.util;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import pers.roinflam.battlecorrection.utils.ReflectionCache;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 实体生物工具类
 * 提供挥砍蓄力记录、物品使用加速、强制击杀等功能
 * <p>
 * 挥砍蓄力的记录方式：原版 EntityPlayer.attackTargetEntityWithCurrentItem() 先按蓄力算伤害，
 * 然后立刻 resetCooldown() 把蓄力清零，再去调用 target.attackEntityFrom()。
 * 所以在 LivingHurtEvent / LivingDamageEvent 里直接读蓄力永远是 0。
 * 这里改为在 AttackEntityEvent（方法第一行触发，此时还没清零）里记下蓄力值和当时的游戏刻，
 * 同一刻内的伤害事件读取这份记录。
 * <p>
 * 只在服务端记录：旧版本在 PlayerTickEvent 里两端都写同一张静态表，
 * 单人游戏时客户端线程和内置服务端线程会同时修改 HashMap；
 * 而且记的是「上一刻开始时」的蓄力，和挥砍那一下对不上。
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EntityLivingUtil {

    /**
     * 本刻没有挥砍记录时 {@link #getSwingStrength(EntityPlayer)} 的返回值
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
        EntityPlayer player = evt.getEntityPlayer();
        if (player.world.isRemote) {
            return;
        }

        // 0.5F 与原版 attackTargetEntityWithCurrentItem() 计算伤害时使用的参数一致
        float strength = player.getCooledAttackStrength(0.5F);
        if (Float.isNaN(strength)) {
            // 攻速被其他模组放开到极大值时可能出现 0/0，按满蓄力处理
            strength = 1.0F;
        }

        SwingRecord record = SWING_RECORDS.get(player.getUniqueID());
        if (record == null) {
            record = new SwingRecord();
            SWING_RECORDS.put(player.getUniqueID(), record);
        }
        record.strength = strength;
        record.gameTime = player.world.getTotalWorldTime();
    }

    /**
     * 玩家登出事件 - 清理挥砍记录
     *
     * @param evt 玩家登出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(@Nonnull PlayerEvent.PlayerLoggedOutEvent evt) {
        SWING_RECORDS.remove(evt.player.getUniqueID());
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
    public static float getSwingStrength(@Nonnull EntityPlayer player) {
        SwingRecord record = SWING_RECORDS.get(player.getUniqueID());
        if (record == null || record.gameTime != player.world.getTotalWorldTime()) {
            return NO_SWING;
        }
        return record.strength;
    }

    /**
     * 判断玩家这次近战是否满蓄力（吸血类属性用）
     * <p>
     * 本刻有挥砍记录时用挥砍那一刻的蓄力；没有（荆棘、模组技能等）时退回用当前蓄力。
     *
     * @param player 玩家（服务端实体）
     * @return true = 满蓄力
     */
    public static boolean isFullyCharged(@Nonnull EntityPlayer player) {
        float strength = getSwingStrength(player);
        if (strength < 0) {
            strength = player.getCooledAttackStrength(0.5F);
        }
        return strength >= 1.0F;
    }

    /**
     * 让实体跳跃冷却重新开始计时（跳跃提升属性用）
     *
     * @param entityLivingBase 生物实体
     */
    public static void setJumped(EntityLivingBase entityLivingBase) {
        ReflectionCache.setJumpTicks(entityLivingBase, 10);
    }

    /**
     * 让正在使用的物品额外推进一刻（通过反射调用 updateActiveHand）
     *
     * @param entityLivingBase 生物实体
     */
    public static void updateHeld(EntityLivingBase entityLivingBase) {
        ReflectionCache.invokeUpdateActiveHand(entityLivingBase);
    }

    /**
     * 强制杀死实体
     * 先用大额伤害走正常死亡流程（保留击杀归属和掉落），被免疫或取消时再强制死亡。
     * 伤害取最大生命的 100 倍：只打最大生命值时，护甲和抗性一减就打不死。
     *
     * @param entityLivingBase 要杀死的实体，为 null 时不做任何事
     * @param damageSource     伤害源
     */
    public static void kill(@Nullable EntityLivingBase entityLivingBase, @Nonnull DamageSource damageSource) {
        if (entityLivingBase != null) {
            entityLivingBase.attackEntityFrom(damageSource, entityLivingBase.getMaxHealth() * 100);
            if (entityLivingBase.isEntityAlive()) {
                entityLivingBase.onDeath(damageSource);
                entityLivingBase.setHealth(0);
            }
        }
    }

}
