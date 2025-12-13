package pers.roinflam.battlecorrection.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.roinflam.battlecorrection.config.ConfigBattle;

/**
 * 日志工具类
 * 提供统一的日志输出接口，支持常规日志和详细日志
 */
public class LogUtil {
    private static final Logger LOGGER = LogManager.getLogger("BattleCorrection");

    /**
     * 输出信息级别的常规日志
     *
     * @param message 日志消息
     */
    public static void info(String message) {
        LOGGER.info(message);
    }

    /**
     * 输出警告级别的常规日志
     *
     * @param message 日志消息
     */
    public static void warn(String message) {
        LOGGER.warn(message);
    }

    /**
     * 输出错误级别的常规日志
     *
     * @param message 日志消息
     */
    public static void error(String message) {
        LOGGER.error(message);
    }

    /**
     * 输出错误级别的常规日志，包含异常信息
     *
     * @param message   日志消息
     * @param throwable 异常对象
     */
    public static void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }

    /**
     * 输出详细日志（仅在配置文件中启用详细日志时输出）
     *
     * @param message 日志消息
     */
    public static void debug(String message) {
        if (ConfigBattle.enableDetailedLogging) {
            LOGGER.info("[详细] " + message);
        }
    }

    /**
     * 输出属性计算的详细日志
     *
     * @param attributeName 属性名称
     * @param entityName    实体名称
     * @param baseValue     基础值
     * @param configValue   配置值
     * @param finalValue    最终值
     */
    public static void debugAttribute(String attributeName, String entityName, double baseValue, double configValue, double finalValue) {
        if (ConfigBattle.enableDetailedLogging) {
            LOGGER.info(String.format("[详细][属性] %s - 实体: %s, 基础值: %.2f, 配置加成: %.2f, 最终值: %.2f",
                    attributeName, entityName, baseValue, configValue, finalValue));
        }
    }

    /**
     * 输出伤害计算的详细日志
     *
     * @param damageType    伤害类型
     * @param attackerName  攻击者名称
     * @param victimName    受害者名称
     * @param originalValue 原始伤害
     * @param modifiedValue 修改后伤害
     * @param reason        修改原因
     */
    public static void debugDamage(String damageType, String attackerName, String victimName,
                                   double originalValue, double modifiedValue, String reason) {
        if (ConfigBattle.enableDetailedLogging) {
            LOGGER.info(String.format("[详细][伤害] %s - 攻击者: %s, 受害者: %s, 原始: %.2f, 修改后: %.2f, 原因: %s",
                    damageType, attackerName, victimName, originalValue, modifiedValue, reason));
        }
    }

    /**
     * 输出暴击计算的详细日志
     *
     * @param attackerName  攻击者名称
     * @param victimName    受害者名称
     * @param critChance    暴击率
     * @param critLayers    暴击层数
     * @param critDamage    暴击伤害倍率
     * @param originalValue 原始伤害
     * @param finalValue    最终伤害
     */
    public static void debugCritical(String attackerName, String victimName, double critChance,
                                     int critLayers, double critDamage, double originalValue, double finalValue) {
        if (ConfigBattle.enableDetailedLogging) {
            LOGGER.info(String.format("[详细][暴击] 攻击者: %s, 受害者: %s, 暴击率: %.2f%%, 暴击层数: %d, 暴击倍率: %.2fx, 原始伤害: %.2f, 最终伤害: %.2f",
                    attackerName, victimName, critChance, critLayers, critDamage, originalValue, finalValue));
        }
    }

    /**
     * 输出治疗计算的详细日志
     *
     * @param healerName    治疗者名称
     * @param originalValue 原始治疗量
     * @param modifiedValue 修改后治疗量
     * @param reason        修改原因
     */
    public static void debugHeal(String healerName, double originalValue, double modifiedValue, String reason) {
        if (ConfigBattle.enableDetailedLogging) {
            LOGGER.info(String.format("[详细][治疗] 治疗者: %s, 原始: %.2f, 修改后: %.2f, 原因: %s",
                    healerName, originalValue, modifiedValue, reason));
        }
    }

    /**
     * 输出事件触发的详细日志
     *
     * @param eventName  事件名称
     * @param entityName 实体名称
     * @param details    详细信息
     */
    public static void debugEvent(String eventName, String entityName, String details) {
        if (ConfigBattle.enableDetailedLogging) {
            LOGGER.info(String.format("[详细][事件] %s - 实体: %s, 详情: %s",
                    eventName, entityName, details));
        }
    }
}