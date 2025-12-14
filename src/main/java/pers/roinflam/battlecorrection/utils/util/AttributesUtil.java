// 文件：AttributesUtil.java
// 路径：src/main/java/pers/roinflam/battlecorrection/utils/util/AttributesUtil.java
package pers.roinflam.battlecorrection.utils.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import pers.roinflam.battlecorrection.compat.BaublesIntegration;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 属性工具类
 * 提供属性值计算和修改器管理功能（支持Baubles饰品栏）
 */
public class AttributesUtil {

    /**
     * 获取实体的属性值（包含装备加成，含饰品栏）
     */
    public static float getAttributeValue(@Nonnull EntityLivingBase entityLivingBase,
                                          @Nonnull IAttribute attribute) {
        return getAttributeValue(entityLivingBase, attribute, 0);
    }

    /**
     * 获取实体的属性值（包含装备加成和额外值，含饰品栏）
     */
    public static float getAttributeValue(@Nonnull EntityLivingBase entityLivingBase,
                                          @Nonnull IAttribute attribute,
                                          double value) {
        if (entityLivingBase.getEntityAttribute(attribute) != null) {
            double attributeValue = entityLivingBase.getEntityAttribute(attribute).getAttributeValue();
            return (float) (attributeValue + value);
        }

        double base = attribute.getDefaultValue() + value;
        List<Double>[] modifiers = collectModifiersFromEquipment(entityLivingBase, attribute);

        return (float) calculateFinalValue(base, modifiers[0], modifiers[1], modifiers[2]);
    }

    /**
     * 从装备中收集属性修改器（包括饰品栏）
     * 返回三个列表：[0]=加法修改器, [1]=乘法修改器1, [2]=乘法修改器2
     */
    @Nonnull
    private static List<Double>[] collectModifiersFromEquipment(@Nonnull EntityLivingBase entity,
                                                                @Nonnull IAttribute attribute) {
        @SuppressWarnings("unchecked")
        List<Double>[] result = new List[3];
        result[0] = new ArrayList<>(); // 加法修改器（operation 0）
        result[1] = new ArrayList<>(); // 乘法修改器1（operation 1）
        result[2] = new ArrayList<>(); // 乘法修改器2（operation 2）

        // 主手和副手
        collectFromSlot(entity.getHeldItemMainhand(), EntityEquipmentSlot.MAINHAND, attribute, result);
        collectFromSlot(entity.getHeldItemOffhand(), EntityEquipmentSlot.OFFHAND, attribute, result);

        // 盔甲槽
        EntityEquipmentSlot[] armorSlots = {
                EntityEquipmentSlot.FEET,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.HEAD
        };

        int index = 0;
        for (@Nonnull ItemStack armorPiece : entity.getArmorInventoryList()) {
            if (index < armorSlots.length) {
                collectFromSlot(armorPiece, armorSlots[index], attribute, result);
                index++;
            }
        }

        // 饰品栏（如果Baubles已加载）
        if (BaublesIntegration.isBaublesLoaded()) {
            List<Double>[] baublesModifiers = BaublesIntegration.collectModifiersFromBaubles(
                    entity, attribute.getName()
            );

            // 合并饰品栏的修改器
            result[0].addAll(baublesModifiers[0]);
            result[1].addAll(baublesModifiers[1]);
            result[2].addAll(baublesModifiers[2]);

            if (!baublesModifiers[0].isEmpty() || !baublesModifiers[1].isEmpty() || !baublesModifiers[2].isEmpty()) {
                LogUtil.debug(String.format("实体 %s 从饰品栏获取到属性 %s 的加成",
                        entity.getName(), attribute.getName()));
            }
        }

        return result;
    }

    /**
     * 从单个装备槽收集属性修改器
     */
    private static void collectFromSlot(@Nonnull ItemStack itemStack,
                                        @Nonnull EntityEquipmentSlot slot,
                                        @Nonnull IAttribute attribute,
                                        @Nonnull List<Double>[] result) {
        if (!itemStack.isEmpty()) {
            Multimap<String, AttributeModifier> modifiers = itemStack.getAttributeModifiers(slot);
            for (@Nonnull AttributeModifier modifier : modifiers.get(attribute.getName())) {
                int operation = modifier.getOperation();
                if (operation >= 0 && operation <= 2) {
                    result[operation].add(modifier.getAmount());
                }
            }
        }
    }

    /**
     * 计算最终属性值
     * 根据 Minecraft 的属性计算规则：先加法，再乘法1，最后乘法2
     */
    private static double calculateFinalValue(double base,
                                              @Nonnull List<Double> additive,
                                              @Nonnull List<Double> multiplicative1,
                                              @Nonnull List<Double> multiplicative2) {
        // 加法修改器
        for (double amount : additive) {
            base += amount;
        }

        // 乘法修改器1（基于原始基础值）
        double result = base;
        for (double amount : multiplicative1) {
            result += base * amount;
        }

        // 乘法修改器2（基于当前值）
        for (double amount : multiplicative2) {
            result *= (1.0D + amount);
        }

        return result;
    }

    /**
     * 获取物品上的自定义属性修改器
     */
    @Nonnull
    public static Multimap<String, AttributeModifier> getAnyAttributeModifiers(@Nonnull ItemStack itemStack) {
        Multimap<String, AttributeModifier> multimap = HashMultimap.create();
        NBTTagCompound nbtTagCompound = itemStack.getTagCompound();

        if (itemStack.hasTagCompound() && nbtTagCompound.hasKey("AttributeModifiers", 9)) {
            NBTTagList nbttaglist = nbtTagCompound.getTagList("AttributeModifiers", 10);
            for (int i = nbttaglist.tagCount() - 1; i >= 0; --i) {
                NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(i);
                AttributeModifier attributemodifier = SharedMonsterAttributes.readAttributeModifierFromNBT(nbttagcompound);
                multimap.put(nbttagcompound.getString("AttributeName"), attributemodifier);
            }
        }

        return multimap;
    }
}