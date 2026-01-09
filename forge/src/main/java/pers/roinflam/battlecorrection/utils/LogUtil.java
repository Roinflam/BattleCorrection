// 文件：LogUtil.java
// 路径：src/main/java/pers/roinflam/battlecorrection/utils/LogUtil.java
package pers.roinflam.battlecorrection.utils;

import pers.roinflam.battlecorrection.BattleCorrection;
import pers.roinflam.battlecorrection.config.ConfigBattle;

/**
 * 日志工具类
 * 提供统一的日志输出接口，支持常规日志和详细日志
 */
public class LogUtil {

    /**
     * 输出信息级别的常规日志
     */
    public static void info(String message) {
        BattleCorrection.LOGGER.info(message);
    }

    /**
     * 输出警告级别的常规日志
     */
    public static void warn(String message) {
        BattleCorrection.LOGGER.warn(message);
    }

    /**
     * 输出错误级别的常规日志
     */
    public static void error(String message) {
        BattleCorrection.LOGGER.error(message);
    }

    /**
     * 输出错误级别的常规日志，包含异常信息
     */
    public static void error(String message, Throwable throwable) {
        BattleCorrection.LOGGER.error(message, throwable);
    }

    /**
     * 输出详细日志（仅在配置文件中启用详细日志时输出）
     */
    public static void debug(String message) {
        if (ConfigBattle.ENABLE_DETAILED_LOGGING.get()) {
            BattleCorrection.LOGGER.info("[详细] " + message);
        }
    }

    /**
     * 输出属性计算的详细日志
     */
    public static void debugAttribute(String attributeName, String entityName,
                                      double baseValue, double configValue, double finalValue) {
        if (ConfigBattle.ENABLE_DETAILED_LOGGING.get()) {
            BattleCorrection.LOGGER.info(String.format(
                    "[详细][属性] %s - 实体: %s, 基础值: %.2f, 配置加成: %.2f, 最终值: %.2f",
                    attributeName, entityName, baseValue, configValue, finalValue));
        }
    }

    /**
     * 输出伤害计算的详细日志
     */
    public static void debugDamage(String damageType, String attackerName, String victimName,
                                   double originalValue, double modifiedValue, String reason) {
        if (ConfigBattle.ENABLE_DETAILED_LOGGING.get()) {
            BattleCorrection.LOGGER.info(String.format(
                    "[详细][伤害] %s - 攻击者: %s, 受害者: %s, 原始: %.2f, 修改后: %.2f, 原因: %s",
                    damageType, attackerName, victimName, originalValue, modifiedValue, reason));
        }
    }

    /**
     * 输出暴击计算的详细日志
     */
    public static void debugCritical(String attackerName, String victimName, double critChance,
                                     int critLayers, double critDamage, double originalValue, double finalValue) {
        if (ConfigBattle.ENABLE_DETAILED_LOGGING.get()) {
            BattleCorrection.LOGGER.info(String.format(
                    "[详细][暴击] 攻击者: %s, 受害者: %s, 暴击率: %.2f%%, 暴击层数: %d, 暴击倍率: %.2fx, 原始伤害: %.2f, 最终伤害: %.2f",
                    attackerName, victimName, critChance, critLayers, critDamage, originalValue, finalValue));
        }
    }

    /**
     * 输出治疗计算的详细日志
     */
    public static void debugHeal(String healerName, double originalValue, double modifiedValue, String reason) {
        if (ConfigBattle.ENABLE_DETAILED_LOGGING.get()) {
            BattleCorrection.LOGGER.info(String.format(
                    "[详细][治疗] 治疗者: %s, 原始: %.2f, 修改后: %.2f, 原因: %s",
                    healerName, originalValue, modifiedValue, reason));
        }
    }

    /**
     * 输出事件触发的详细日志
     */
    public static void debugEvent(String eventName, String entityName, String details) {
        if (ConfigBattle.ENABLE_DETAILED_LOGGING.get()) {
            BattleCorrection.LOGGER.info(String.format(
                    "[详细][事件] %s - 实体: %s, 详情: %s",
                    eventName, entityName, details));
        }
    }
}