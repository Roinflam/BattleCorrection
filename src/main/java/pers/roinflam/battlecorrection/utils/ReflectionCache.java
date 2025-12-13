// 文件：ReflectionCache.java
// 路径：src/main/java/pers/roinflam/battlecorrection/utils/ReflectionCache.java
package pers.roinflam.battlecorrection.utils;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.FoodStats;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 反射缓存类
 * 缓存反射获取的 Field 和 Method，避免重复反射影响性能
 */
public class ReflectionCache {

    private static Field foodTimerField;
    private static Field jumpTicksField;
    private static Method updateActiveHandMethod;

    /**
     * 获取 FoodStats 的 foodTimer 字段
     */
    public static Field getFoodTimerField() {
        if (foodTimerField == null) {
            try {
                foodTimerField = ObfuscationReflectionHelper.findField(FoodStats.class, "foodTimer");
            } catch (Exception e) {
                try {
                    foodTimerField = ObfuscationReflectionHelper.findField(FoodStats.class, "field_75123_d");
                } catch (Exception ex) {
                    LogUtil.error("无法找到 FoodStats.foodTimer 字段", ex);
                }
            }
            if (foodTimerField != null) {
                foodTimerField.setAccessible(true);
            }
        }
        return foodTimerField;
    }

    /**
     * 获取 EntityLivingBase 的 jumpTicks 字段
     */
    public static Field getJumpTicksField() {
        if (jumpTicksField == null) {
            try {
                jumpTicksField = ObfuscationReflectionHelper.findField(EntityLivingBase.class, "jumpTicks");
            } catch (Exception e) {
                try {
                    jumpTicksField = ObfuscationReflectionHelper.findField(EntityLivingBase.class, "field_70773_bE");
                } catch (Exception ex) {
                    LogUtil.error("无法找到 EntityLivingBase.jumpTicks 字段", ex);
                }
            }
            if (jumpTicksField != null) {
                jumpTicksField.setAccessible(true);
            }
        }
        return jumpTicksField;
    }
    
    /**
     * 获取 EntityLivingBase 的 updateActiveHand 方法
     */
    public static Method getUpdateActiveHandMethod() {
        if (updateActiveHandMethod == null) {
            try {
                // 使用 ReflectionHelper 而不是 ObfuscationReflectionHelper
                updateActiveHandMethod = net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(
                        EntityLivingBase.class,
                        "updateActiveHand",   // 开发环境方法名
                        "func_184608_ct"      // 混淆后方法名
                );
                updateActiveHandMethod.setAccessible(true);
            } catch (Exception e) {
                LogUtil.error("无法找到 EntityLivingBase.updateActiveHand 方法", e);
            }
        }
        return updateActiveHandMethod;
    }

    /**
     * 获取 foodTimer 的值
     */
    public static int getFoodTimer(FoodStats foodStats) {
        try {
            Field field = getFoodTimerField();
            if (field != null) {
                return field.getInt(foodStats);
            }
        } catch (Exception e) {
            LogUtil.error("获取 foodTimer 失败", e);
        }
        return -1;
    }

    /**
     * 设置 foodTimer 的值
     */
    public static void setFoodTimer(FoodStats foodStats, int value) {
        try {
            Field field = getFoodTimerField();
            if (field != null) {
                field.setInt(foodStats, value);
            }
        } catch (Exception e) {
            LogUtil.error("设置 foodTimer 失败", e);
        }
    }

    /**
     * 设置 jumpTicks 的值
     */
    public static void setJumpTicks(EntityLivingBase entity, int value) {
        try {
            Field field = getJumpTicksField();
            if (field != null) {
                field.setInt(entity, value);
            }
        } catch (Exception e) {
            LogUtil.error("设置 jumpTicks 失败", e);
        }
    }

    /**
     * 调用 updateActiveHand 方法
     */
    public static void invokeUpdateActiveHand(EntityLivingBase entity) {
        try {
            Method method = getUpdateActiveHandMethod();
            if (method != null) {
                method.invoke(entity);
            }
        } catch (Exception e) {
            LogUtil.error("调用 updateActiveHand 失败", e);
        }
    }
}