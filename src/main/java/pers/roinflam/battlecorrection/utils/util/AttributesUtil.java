package pers.roinflam.battlecorrection.utils.util;

import net.minecraft.core.Holder;
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
 * <p>
 * 1.21.1：属性一律以 {@link Holder} 传（{@code ModAttributes} 里的 {@code DeferredHolder} 本身就是）。
 */
public class AttributesUtil {

    /**
     * 读取实体的属性值
     *
     * @param entity    实体
     * @param attribute 属性
     * @return 属性值；实体没有这个属性时返回属性默认值
     */
    public static double getAttributeValue(@Nonnull LivingEntity entity, @Nonnull Holder<Attribute> attribute) {
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
    public static double getAttributeValue(@Nonnull LivingEntity entity, @Nonnull Holder<Attribute> attribute,
                                           double extraValue) {
        @Nullable AttributeInstance attributeInstance = entity.getAttribute(attribute);
        double value = attributeInstance != null ? attributeInstance.getValue() : attribute.value().getDefaultValue();
        return value + extraValue;
    }
}
