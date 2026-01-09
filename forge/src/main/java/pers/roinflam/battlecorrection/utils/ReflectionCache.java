// 文件：ReflectionCache.java
// 路径：src/main/java/pers/roinflam/battlecorrection/utils/ReflectionCache.java
package pers.roinflam.battlecorrection.utils;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 反射缓存工具类
 * 缓存反射对象以提高性能
 * <p>
 * 注意：1.20.1使用Mojang官方映射，字段名与1.12.2不同
 */
public class ReflectionCache {

    // ===== 字段缓存 =====

    /**
     * LivingEntity.noJumpDelay 字段
     * 控制跳跃延迟（原1.12.2的jumpTicks）
     */
    private static Field noJumpDelayField = null;

    // ===== 方法缓存 =====

    /**
     * LivingEntity.updatingUsingItem 方法
     * 更新物品使用状态（原1.12.2的updateActiveHand）
     */
    private static Method updatingUsingItemMethod = null;

    // ===== 静态初始化 =====

    static {
        try {
            // 获取 noJumpDelay 字段
            // 1.20.1 Mojang映射名: noJumpDelay
            noJumpDelayField = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20899_");
            noJumpDelayField.setAccessible(true);
        } catch (Exception e) {
            LogUtil.error("无法获取 noJumpDelay 字段", e);
        }

        try {
            // 获取 updatingUsingItem 方法
            // 1.20.1 Mojang映射名: updatingUsingItem
            updatingUsingItemMethod = ObfuscationReflectionHelper.findMethod(LivingEntity.class, "m_21009_");
            updatingUsingItemMethod.setAccessible(true);
        } catch (Exception e) {
            LogUtil.error("无法获取 updatingUsingItem 方法", e);
        }
    }

    /**
     * 设置实体的跳跃延迟（阻止连续跳跃）
     *
     * @param livingEntity 生物实体
     * @param ticks        延迟tick数
     */
    public static void setJumpTicks(@Nonnull LivingEntity livingEntity, int ticks) {
        if (noJumpDelayField != null) {
            try {
                noJumpDelayField.setInt(livingEntity, ticks);
            } catch (Exception e) {
                LogUtil.error("设置跳跃延迟失败: " + livingEntity.getName().getString(), e);
            }
        }
    }

    /**
     * 获取实体的跳跃延迟
     *
     * @param livingEntity 生物实体
     * @return 跳跃延迟tick数，失败返回0
     */
    public static int getJumpTicks(@Nonnull LivingEntity livingEntity) {
        if (noJumpDelayField != null) {
            try {
                return noJumpDelayField.getInt(livingEntity);
            } catch (Exception e) {
                LogUtil.error("获取跳跃延迟失败: " + livingEntity.getName().getString(), e);
            }
        }
        return 0;
    }

    /**
     * 调用实体的物品使用更新方法
     *
     * @param livingEntity 生物实体
     */
    public static void invokeUpdatingUsingItem(@Nonnull LivingEntity livingEntity) {
        if (updatingUsingItemMethod != null) {
            try {
                updatingUsingItemMethod.invoke(livingEntity);
            } catch (Exception e) {
                LogUtil.error("调用物品使用更新方法失败: " + livingEntity.getName().getString(), e);
            }
        }
    }
}