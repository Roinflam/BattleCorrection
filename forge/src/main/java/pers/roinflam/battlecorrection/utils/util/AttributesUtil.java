// AttributesUtil.java
// 路径：src/main/java/pers/roinflam/battlecorrection/utils/util/AttributesUtil.java
package pers.roinflam.battlecorrection.utils.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import pers.roinflam.battlecorrection.compat.CuriosIntegration;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 属性工具类
 * 提供属性值获取和计算功能（支持Curios饰品栏）
 *
 * <p>包含tick级缓存，避免同一tick内对同一实体+属性的重复计算</p>
 */
public class AttributesUtil {

    // ========== Curios属性加成缓存 ==========
    // 缓存过期的tick
    private static long bonusCachedTick = -1;

    // 缓存有效期（tick数），与CuriosIntegration保持一致
    private static final int BONUS_CACHE_TTL = 10;

    // 缓存key：(entityId << 32) | attributeHash -> 加成值
    private static final Map<Long, Double> curiosBonusCache = new HashMap<>();

    /**
     * 检查并在需要时清理过期的加成缓存
     *
     * @param entity 用于获取当前世界tick的实体
     */
    private static void checkBonusCache(@Nonnull LivingEntity entity) {
        long currentTick = entity.level().getGameTime();
        if (currentTick - bonusCachedTick >= BONUS_CACHE_TTL) {
            bonusCachedTick = currentTick;
            curiosBonusCache.clear();
        }
    }

    /**
     * 生成加成缓存的key
     *
     * @param entityId  实体ID
     * @param attribute 属性对象
     * @return 缓存key
     */
    private static long makeBonusCacheKey(int entityId, @Nonnull Attribute attribute) {
        return ((long) entityId << 32) | (attribute.hashCode() & 0xFFFFFFFFL);
    }

    /**
     * 获取实体的属性值（包含装备和饰品栏的所有加成）
     *
     * @param entity    实体
     * @param attribute 属性
     * @return 属性值，如果属性不存在则返回默认值
     */
    public static double getAttributeValue(@Nonnull LivingEntity entity, @Nonnull Attribute attribute) {
        return getAttributeValue(entity, attribute, 0.0D);
    }

    /**
     * 获取实体的属性值，并加上额外值（包含装备和饰品栏的所有加成）
     *
     * @param entity     实体
     * @param attribute  属性
     * @param extraValue 额外值
     * @return 属性值 + 额外值
     */
    public static double getAttributeValue(@Nonnull LivingEntity entity, @Nonnull Attribute attribute,
                                           double extraValue) {
        // 1. 获取基础属性值（包含装备槽的修改器）
        double baseValue;
        @Nullable AttributeInstance attributeInstance = entity.getAttribute(attribute);

        if (attributeInstance != null) {
            baseValue = attributeInstance.getValue();
        } else {
            baseValue = attribute.getDefaultValue();
        }

        // 2. 如果Curios已加载，添加饰品栏的属性加成（带缓存）
        if (CuriosIntegration.isCuriosLoaded()) {
            double curiosBonus = getCuriosAttributeBonusCached(entity, attribute);
            if (curiosBonus != 0) {
                baseValue += curiosBonus;
                LogUtil.debug(String.format("饰品栏加成 - 实体: %s, 属性: %s, 加成: %.2f",
                        entity.getName().getString(),
                        attribute.getDescriptionId(),
                        curiosBonus));
            }
        }

        // 3. 加上额外值并返回
        return baseValue + extraValue;
    }

    /**
     * 从饰品栏计算属性加成（带缓存）
     * 同一缓存周期内，对同一实体+属性的查询直接返回缓存结果
     *
     * @param entity    实体
     * @param attribute 属性
     * @return 饰品栏提供的属性加成值
     */
    private static double getCuriosAttributeBonusCached(@Nonnull LivingEntity entity,
                                                        @Nonnull Attribute attribute) {
        checkBonusCache(entity);

        long cacheKey = makeBonusCacheKey(entity.getId(), attribute);

        // 命中缓存直接返回
        Double cached = curiosBonusCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 缓存未命中，执行实际计算
        double bonus = getCuriosAttributeBonus(entity, attribute);

        // 写入缓存
        curiosBonusCache.put(cacheKey, bonus);

        return bonus;
    }

    /**
     * 从饰品栏计算属性加成（实际计算逻辑）
     * 完全模拟Minecraft的属性计算顺序
     *
     * @param entity    实体
     * @param attribute 属性
     * @return 饰品栏提供的属性加成值
     */
    private static double getCuriosAttributeBonus(@Nonnull LivingEntity entity, @Nonnull Attribute attribute) {
        List<Double>[] modifiers = CuriosIntegration.collectModifiersFromCurios(entity, attribute);

        // modifiers[0] = ADDITION (加法)
        // modifiers[1] = MULTIPLY_BASE (乘法基础)
        // modifiers[2] = MULTIPLY_TOTAL (乘法总计)

        // 如果没有任何修改器，直接返回0
        if (modifiers[0].isEmpty() && modifiers[1].isEmpty() && modifiers[2].isEmpty()) {
            return 0.0D;
        }

        // 获取当前基础值（不含饰品栏加成，只有装备槽的）
        double currentBase = getAttributeBaseValueWithEquipment(entity, attribute);

        // Minecraft属性计算顺序：
        // 第一步：基础值 + 所有ADDITION修改器之和
        double additionSum = 0;
        for (double v : modifiers[0]) {
            additionSum += v;
        }
        double afterAddition = currentBase + additionSum;

        // 第二步：结果 × (1 + 所有MULTIPLY_BASE修改器之和)
        double multiplyBaseSum = 0;
        for (double v : modifiers[1]) {
            multiplyBaseSum += v;
        }
        double afterMultiplyBase = afterAddition * (1.0D + multiplyBaseSum);

        // 第三步：对每个MULTIPLY_TOTAL修改器，结果 × (1 + 修改器值)
        double result = afterMultiplyBase;
        for (double multiplier : modifiers[2]) {
            result *= (1.0D + multiplier);
        }

        // 返回饰品栏提供的总加成（最终值 - 原始基础值）
        return result - currentBase;
    }

    /**
     * 获取属性的当前值（包含装备槽修改器，不含饰品栏）
     *
     * @param entity    实体
     * @param attribute 属性
     * @return 包含装备修改器的属性值
     */
    private static double getAttributeBaseValueWithEquipment(@Nonnull LivingEntity entity,
                                                             @Nonnull Attribute attribute) {
        @Nullable AttributeInstance attributeInstance = entity.getAttribute(attribute);

        if (attributeInstance != null) {
            return attributeInstance.getValue();
        }

        return attribute.getDefaultValue();
    }

    /**
     * 获取实体的属性基础值（不含任何修改器）
     *
     * @param entity    实体
     * @param attribute 属性
     * @return 基础值
     */
    public static double getAttributeBaseValue(@Nonnull LivingEntity entity, @Nonnull Attribute attribute) {
        @Nullable AttributeInstance attributeInstance = entity.getAttribute(attribute);

        if (attributeInstance != null) {
            return attributeInstance.getBaseValue();
        }

        return attribute.getDefaultValue();
    }

    /**
     * 设置实体的属性基础值
     *
     * @param entity    实体
     * @param attribute 属性
     * @param value     新的基础值
     */
    public static void setAttributeBaseValue(@Nonnull LivingEntity entity, @Nonnull Attribute attribute,
                                             double value) {
        @Nullable AttributeInstance attributeInstance = entity.getAttribute(attribute);

        if (attributeInstance != null) {
            attributeInstance.setBaseValue(value);
        }
    }
}