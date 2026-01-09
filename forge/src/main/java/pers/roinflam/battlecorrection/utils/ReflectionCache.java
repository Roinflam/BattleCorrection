// ReflectionCache.java
// 路径：src/main/java/pers/roinflam/battlecorrection/utils/ReflectionCache.java
package pers.roinflam.battlecorrection.utils;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;

/**
 * 反射缓存工具类
 * 缓存反射对象以提高性能
 * <p>
 * 1.20.1 Forge使用SRG映射名访问私有成员
 * 映射参考: https://linkie.shedaniel.dev/mappings
 */
public class ReflectionCache {

    // ===== 字段缓存 =====

    /**
     * LivingEntity.noJumpDelay 字段
     * 控制跳跃延迟冷却时间
     * <p>
     * Mojang映射: noJumpDelay
     * SRG映射: f_20954_
     * Yarn映射: jumpingCooldown
     */
    private static Field noJumpDelayField = null;

    /**
     * LivingEntity.useItemRemaining 字段
     * 物品使用剩余时间（tick）
     * <p>
     * Mojang映射: useItemRemaining
     * SRG映射: f_20936_
     * Yarn映射: itemUseTimeLeft
     */
    private static Field useItemRemainingField = null;

    // ===== 初始化状态 =====

    private static boolean initialized = false;
    private static boolean noJumpDelayAvailable = false;
    private static boolean useItemRemainingAvailable = false;

    // ===== 静态初始化 =====

    static {
        initialize();
    }

    /**
     * 初始化反射缓存
     * 使用SRG名称查找字段和方法
     */
    private static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        // 获取 noJumpDelay 字段
        // SRG名: f_20954_
        try {
            noJumpDelayField = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20954_");
            noJumpDelayField.setAccessible(true);
            noJumpDelayAvailable = true;
            LogUtil.info("成功获取 noJumpDelay 字段 (SRG: f_20954_)");
        } catch (Exception e) {
            LogUtil.error("无法获取 noJumpDelay 字段 (SRG: f_20954_)", e);
            noJumpDelayAvailable = false;
        }

        // 获取 useItemRemaining 字段
        // SRG名: f_20936_
        try {
            useItemRemainingField = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20936_");
            useItemRemainingField.setAccessible(true);
            useItemRemainingAvailable = true;
            LogUtil.info("成功获取 useItemRemaining 字段 (SRG: f_20936_)");
        } catch (Exception e) {
            LogUtil.error("无法获取 useItemRemaining 字段 (SRG: f_20936_)", e);
            useItemRemainingAvailable = false;
        }
    }

    /**
     * 设置实体的跳跃延迟（阻止连续跳跃）
     * <p>
     * 当 noJumpDelay > 0 时，实体无法跳跃
     * 每tick该值会自动减1
     *
     * @param livingEntity 生物实体
     * @param ticks        延迟tick数（通常设置为10）
     */
    public static void setJumpTicks(@Nonnull LivingEntity livingEntity, int ticks) {
        if (!noJumpDelayAvailable || noJumpDelayField == null) {
            return;
        }

        try {
            noJumpDelayField.setInt(livingEntity, ticks);
        } catch (Exception e) {
            LogUtil.error("设置跳跃延迟失败: " + livingEntity.getName().getString(), e);
        }
    }

    /**
     * 获取实体的跳跃延迟
     *
     * @param livingEntity 生物实体
     * @return 跳跃延迟tick数，失败返回0
     */
    public static int getJumpTicks(@Nonnull LivingEntity livingEntity) {
        if (!noJumpDelayAvailable || noJumpDelayField == null) {
            return 0;
        }

        try {
            return noJumpDelayField.getInt(livingEntity);
        } catch (Exception e) {
            LogUtil.error("获取跳跃延迟失败: " + livingEntity.getName().getString(), e);
        }
        return 0;
    }

    /**
     * 获取物品使用剩余时间
     *
     * @param livingEntity 生物实体
     * @return 剩余时间tick数，失败返回-1
     */
    public static int getUseItemRemaining(@Nonnull LivingEntity livingEntity) {
        if (!useItemRemainingAvailable || useItemRemainingField == null) {
            return -1;
        }

        try {
            return useItemRemainingField.getInt(livingEntity);
        } catch (Exception e) {
            LogUtil.error("获取物品使用剩余时间失败: " + livingEntity.getName().getString(), e);
        }
        return -1;
    }

    /**
     * 设置物品使用剩余时间
     * <p>
     * 减少此值可以加速物品使用（吃食物、拉弓等）
     *
     * @param livingEntity 生物实体
     * @param ticks        剩余时间tick数
     */
    public static void setUseItemRemaining(@Nonnull LivingEntity livingEntity, int ticks) {
        if (!useItemRemainingAvailable || useItemRemainingField == null) {
            return;
        }

        try {
            useItemRemainingField.setInt(livingEntity, Math.max(0, ticks));
        } catch (Exception e) {
            LogUtil.error("设置物品使用剩余时间失败: " + livingEntity.getName().getString(), e);
        }
    }

    /**
     * 减少物品使用剩余时间（加速物品使用）
     * <p>
     * 每次调用相当于推进指定tick的使用进度
     * 用于加速吃食物、拉弓等操作
     *
     * @param livingEntity  生物实体
     * @param ticksToReduce 要减少的tick数
     * @return 是否成功
     */
    public static boolean reduceUseItemRemaining(@Nonnull LivingEntity livingEntity, int ticksToReduce) {
        if (!useItemRemainingAvailable || useItemRemainingField == null) {
            return false;
        }

        try {
            int current = useItemRemainingField.getInt(livingEntity);
            int newValue = Math.max(0, current - ticksToReduce);
            useItemRemainingField.setInt(livingEntity, newValue);
            return true;
        } catch (Exception e) {
            LogUtil.error("减少物品使用剩余时间失败: " + livingEntity.getName().getString(), e);
        }
        return false;
    }

    /**
     * 检查跳跃延迟字段是否可用
     *
     * @return 是否可用
     */
    public static boolean isNoJumpDelayAvailable() {
        return noJumpDelayAvailable;
    }

    /**
     * 检查物品使用剩余时间字段是否可用
     *
     * @return 是否可用
     */
    public static boolean isUseItemRemainingAvailable() {
        return useItemRemainingAvailable;
    }
}