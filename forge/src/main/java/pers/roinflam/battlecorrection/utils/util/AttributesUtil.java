// 文件：AttributesUtil.java
// 路径：src/main/java/pers/roinflam/battlecorrection/utils/util/AttributesUtil.java
package pers.roinflam.battlecorrection.utils.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 属性工具类
 * 提供属性值获取和计算功能
 */
public class AttributesUtil {

    /**
     * 获取实体的属性值
     *
     * @param entity    实体
     * @param attribute 属性
     * @return 属性值，如果属性不存在则返回0
     */
    public static double getAttributeValue(@Nonnull LivingEntity entity, @Nonnull Attribute attribute) {
        return getAttributeValue(entity, attribute, 0.0D);
    }

    /**
     * 获取实体的属性值，并加上额外值
     *
     * @param entity     实体
     * @param attribute  属性
     * @param extraValue 额外值
     * @return 属性值 + 额外值
     */
    public static double getAttributeValue(@Nonnull LivingEntity entity, @Nonnull Attribute attribute,
                                           double extraValue) {
        @Nullable AttributeInstance attributeInstance = entity.getAttribute(attribute);

        if (attributeInstance != null) {
            return attributeInstance.getValue() + extraValue;
        }

        // 如果属性实例不存在，返回属性默认值 + 额外值
        return attribute.getDefaultValue() + extraValue;
    }

    /**
     * 获取实体的属性基础值（不含修改器）
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