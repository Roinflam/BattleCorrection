// 文件：BaublesIntegration.java
// 路径：src/main/java/pers/roinflam/battlecorrection/compat/BaublesIntegration.java
package pers.roinflam.battlecorrection.compat;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.google.common.collect.Multimap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Baubles模组集成类
 * 提供从饰品栏获取物品和属性的功能
 * <p>
 * 注意：Baubles饰品栏仅支持玩家（EntityPlayer）
 */
public class BaublesIntegration {

    private static boolean baublesLoaded = false;

    /**
     * 检查Baubles是否已加载
     */
    public static boolean isBaublesLoaded() {
        return baublesLoaded;
    }

    /**
     * 初始化Baubles集成
     */
    public static void init() {
        try {
            Class.forName("baubles.api.BaublesApi");
            baublesLoaded = true;
            LogUtil.info("成功检测到Baubles模组，已启用饰品栏属性支持");
        } catch (ClassNotFoundException e) {
            baublesLoaded = false;
            LogUtil.info("未检测到Baubles模组，饰品栏功能将被禁用");
        }
    }

    /**
     * 获取实体的所有饰品栏物品
     *
     * @param entity 实体（必须是玩家）
     * @return 饰品栏物品列表，如果不是玩家则返回空列表
     */
    @Nonnull
    public static List<ItemStack> getBaubles(@Nonnull EntityLivingBase entity) {
        List<ItemStack> baubles = new ArrayList<>();

        if (!baublesLoaded) {
            return baubles;
        }

        // Baubles仅支持玩家
        if (!(entity instanceof EntityPlayer)) {
            return baubles;
        }

        try {
            EntityPlayer player = (EntityPlayer) entity;
            IBaublesItemHandler baublesHandler = BaublesApi.getBaublesHandler(player);

            if (baublesHandler != null) {
                int slots = baublesHandler.getSlots();
                for (int i = 0; i < slots; i++) {
                    ItemStack stack = baublesHandler.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        baubles.add(stack);
                    }
                }

                if (!baubles.isEmpty()) {
                    LogUtil.debug(String.format("玩家 %s 的饰品栏中有 %d 个物品",
                            player.getName(), baubles.size()));
                }
            }
        } catch (Exception e) {
            LogUtil.error("获取饰品栏物品时出错", e);
        }

        return baubles;
    }

    /**
     * 从饰品栏物品中收集指定属性的修改器
     *
     * @param entity        实体（必须是玩家）
     * @param attributeName 属性名称
     * @return 属性修改器列表数组 [加法, 乘法1, 乘法2]
     */
    @Nonnull
    public static List<Double>[] collectModifiersFromBaubles(@Nonnull EntityLivingBase entity,
                                                             @Nonnull String attributeName) {
        @SuppressWarnings("unchecked")
        List<Double>[] result = new List[3];
        result[0] = new ArrayList<>(); // 加法修改器（operation 0）
        result[1] = new ArrayList<>(); // 乘法修改器1（operation 1）
        result[2] = new ArrayList<>(); // 乘法修改器2（operation 2）

        if (!baublesLoaded) {
            return result;
        }

        // Baubles仅支持玩家
        if (!(entity instanceof EntityPlayer)) {
            return result;
        }

        try {
            List<ItemStack> baubles = getBaubles(entity);

            for (ItemStack bauble : baubles) {
                // 尝试所有可能的槽位来获取属性修改器
                // 因为饰品可能没有明确的槽位类型
                collectModifiersFromBauble(bauble, attributeName, result);
            }

            // 记录调试信息
            int totalModifiers = result[0].size() + result[1].size() + result[2].size();
            if (totalModifiers > 0) {
                LogUtil.debug(String.format("玩家 %s 从饰品栏收集到属性 %s: 加法=%d个, 乘法1=%d个, 乘法2=%d个",
                        entity.getName(), attributeName,
                        result[0].size(), result[1].size(), result[2].size()));
            }
        } catch (Exception e) {
            LogUtil.error("从饰品栏收集属性修改器时出错", e);
        }

        return result;
    }

    /**
     * 从单个饰品物品中收集属性修改器
     * 尝试所有装备槽位以确保获取所有属性
     *
     * @param bauble        饰品物品
     * @param attributeName 属性名称
     * @param result        结果数组 [加法, 乘法1, 乘法2]
     */
    private static void collectModifiersFromBauble(@Nonnull ItemStack bauble,
                                                   @Nonnull String attributeName,
                                                   @Nonnull List<Double>[] result) {
        // 尝试所有可能的槽位
        EntityEquipmentSlot[] allSlots = {
                EntityEquipmentSlot.MAINHAND,
                EntityEquipmentSlot.OFFHAND,
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        };

        for (EntityEquipmentSlot slot : allSlots) {
            Multimap<String, AttributeModifier> modifiers = bauble.getAttributeModifiers(slot);

            for (@Nonnull AttributeModifier modifier : modifiers.get(attributeName)) {
                int operation = modifier.getOperation();
                if (operation >= 0 && operation <= 2) {
                    // 检查是否已经添加过这个修改器（避免重复）
                    if (!isModifierAlreadyAdded(modifier, result[operation])) {
                        result[operation].add(modifier.getAmount());
                    }
                }
            }
        }
    }

    /**
     * 检查修改器是否已经被添加过
     *
     * @param modifier       要检查的修改器
     * @param existingValues 已有的值列表
     * @return 是否已存在
     */
    private static boolean isModifierAlreadyAdded(@Nonnull AttributeModifier modifier,
                                                  @Nonnull List<Double> existingValues) {
        double amount = modifier.getAmount();
        // 使用小误差比较浮点数
        for (double existing : existingValues) {
            if (Math.abs(existing - amount) < 0.0001) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查玩家是否装备了特定物品（包括饰品栏）
     *
     * @param entity   实体（必须是玩家）
     * @param itemName 物品注册名
     * @return 是否装备
     */
    public static boolean hasBaubleEquipped(@Nonnull EntityLivingBase entity, @Nonnull String itemName) {
        if (!baublesLoaded) {
            return false;
        }

        // Baubles仅支持玩家
        if (!(entity instanceof EntityPlayer)) {
            return false;
        }

        try {
            List<ItemStack> baubles = getBaubles(entity);
            for (ItemStack bauble : baubles) {
                if (bauble.getItem().getRegistryName() != null &&
                        bauble.getItem().getRegistryName().toString().equals(itemName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            LogUtil.error("检查饰品装备时出错", e);
        }

        return false;
    }

    /**
     * 获取玩家饰品栏的槽位数量
     *
     * @param player 玩家
     * @return 饰品栏槽位数量，如果不可用则返回0
     */
    public static int getBaubleSlots(@Nonnull EntityPlayer player) {
        if (!baublesLoaded) {
            return 0;
        }

        try {
            IBaublesItemHandler baublesHandler = BaublesApi.getBaublesHandler(player);
            if (baublesHandler != null) {
                return baublesHandler.getSlots();
            }
        } catch (Exception e) {
            LogUtil.error("获取饰品槽位数量时出错", e);
        }

        return 0;
    }
}