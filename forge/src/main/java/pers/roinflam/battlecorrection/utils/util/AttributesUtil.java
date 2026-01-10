// 文件：AttributesUtil.java
// 路径：src/main/java/pers/roinflam/battlecorrection/utils/util/AttributesUtil.java
package pers.roinflam.battlecorrection.utils.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import pers.roinflam.battlecorrection.compat.CuriosIntegration;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 属性工具类
 * 提供属性值获取和计算功能（支持Curios饰品栏）
 */
public class AttributesUtil {

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
        double baseValue = 0.0D;
        @Nullable AttributeInstance attributeInstance = entity.getAttribute(attribute);

        if (attributeInstance != null) {
            baseValue = attributeInstance.getValue();
        } else {
            baseValue = attribute.getDefaultValue();
        }

        // 2. 如果Curios已加载，添加饰品栏的属性加成
        if (CuriosIntegration.isCuriosLoaded()) {
            double curiosBonus = getCuriosAttributeBonus(entity, attribute);
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
     * 从饰品栏计算属性加成
     * 完全模拟Minecraft的属性计算顺序
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
        double additionSum = modifiers[0].stream().mapToDouble(Double::doubleValue).sum();
        double afterAddition = currentBase + additionSum;

        // 第二步：结果 × (1 + 所有MULTIPLY_BASE修改器之和)
        double multiplyBaseSum = modifiers[1].stream().mapToDouble(Double::doubleValue).sum();
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
     * 这是为了计算饰品栏的增量加成
     */
    private static double getAttributeBaseValueWithEquipment(@Nonnull LivingEntity entity, @Nonnull Attribute attribute) {
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