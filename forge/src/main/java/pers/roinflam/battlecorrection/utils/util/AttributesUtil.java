package pers.roinflam.battlecorrection.utils.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 属性读取工具
 * <p>
 * 就是读 {@link AttributeInstance#getValue()}。原版的属性实例自己带缓存，只有修饰符变了才重算，
 * 所以这个方法可以放心在每 tick 的逻辑里调。
 * <p>
 * 饰品栏的属性（Curios 自己的、命令追加的、物品上原版格式的 AttributeModifiers）都已经由
 * {@code CurioMarkEvents} 在戴上时作为真正的修饰符加到实体身上，读出来的值天然包含它们。
 * 本模组的 15 个属性都是 setSyncable(true)，客户端读到的是服务端同步过来的值，两端一致。
 * 之前这里会额外扫一遍饰品栏把原版格式的属性现算进来（带 10 tick 缓存），饰品多了拉弓吃东西会周期性掉帧，
 * 已经删掉。
 */
public class AttributesUtil {

    /**
     * 读取实体的属性值
     *
     * @param entity    实体
     * @param attribute 属性
     * @return 属性值；实体没有这个属性时返回属性默认值
     */
    public static double getAttributeValue(@Nonnull LivingEntity entity, @Nonnull Attribute attribute) {
        return getAttributeValue(entity, attribute, 0.0D);
    }

    /**
     * 读取实体的属性值并加上一个额外值
     *
     * @param entity     实体
     * @param attribute  属性
     * @param extraValue 额外加上的值
     * @return 属性值 + 额外值
     */
    public static double getAttributeValue(@Nonnull LivingEntity entity, @Nonnull Attribute attribute,
                                           double extraValue) {
        @Nullable AttributeInstance attributeInstance = entity.getAttribute(attribute);
        double value = attributeInstance != null ? attributeInstance.getValue() : attribute.getDefaultValue();
        return value + extraValue;
    }
}
