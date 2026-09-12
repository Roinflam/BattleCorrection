package pers.roinflam.battlecorrection.utils;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;

/**
 * 反射缓存工具类
 * 缓存反射对象以提高性能
 * <p>
 * 目前只剩 updatingUsingItem 一个反射目标（弓速、使用速度的加速效果用）。
 * 1.12 移植时留下的 noJumpDelay、useItemRemaining 两个字段已经没有任何地方使用，已删除。
 * <p>
 * 注意：ObfuscationReflectionHelper 要求传入 SRG 名（如 m_21329_），它会在开发环境里自动映射成可读名，
 * 这是该 API 的正确用法（和 Mixin 必须写可读名的规则正好相反）。
 */
public class ReflectionCache {

    // ===== 方法缓存 =====
    private static Method updatingUsingItemMethod = null;

    // ===== 可用状态 =====
    private static boolean updatingUsingItemAvailable = false;

    static {
        initialize();
    }

    /**
     * 初始化反射缓存
     * 找不到目标方法时只记录错误日志并关闭对应功能，不会让游戏崩溃
     */
    private static void initialize() {
        LogUtil.info("开始初始化反射缓存...");

        // 获取 updatingUsingItem 方法
        // 可读名: updatingUsingItem, SRG名: m_21329_
        try {
            updatingUsingItemMethod = ObfuscationReflectionHelper.findMethod(
                    LivingEntity.class,
                    "m_21329_"
            );
            updatingUsingItemMethod.setAccessible(true);
            updatingUsingItemAvailable = true;
            LogUtil.info("✓ 成功获取 updatingUsingItem 方法");
        } catch (Exception e) {
            LogUtil.error("✗ 无法获取 updatingUsingItem 方法，弓速/使用速度的加速效果将失效", e);
            updatingUsingItemAvailable = false;
        }
    }

    /**
     * 调用 updatingUsingItem 方法（让实体额外推进一次物品使用进度）
     *
     * @param livingEntity 生物实体
     * @return true = 调用成功；方法不可用或调用出错时返回 false（出错会记录日志）
     */
    public static boolean invokeUpdatingUsingItem(@Nonnull LivingEntity livingEntity) {
        if (!updatingUsingItemAvailable || updatingUsingItemMethod == null) {
            return false;
        }

        try {
            updatingUsingItemMethod.invoke(livingEntity);
            return true;
        } catch (Exception e) {
            LogUtil.error("调用 updatingUsingItem 失败，本次跳过: " + livingEntity.getName().getString(), e);
        }
        return false;
    }

    /**
     * updatingUsingItem 反射是否可用
     *
     * @return true = 可用
     */
    public static boolean isUpdatingUsingItemAvailable() {
        return updatingUsingItemAvailable;
    }
}
