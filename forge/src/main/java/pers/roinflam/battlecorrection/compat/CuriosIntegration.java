//// 文件：CuriosIntegration.java（修复版）
//// 路径：src/main/java/pers/roinflam/battlecorrection/compat/CuriosIntegration.java
package pers.roinflam.battlecorrection.compat;

//
//import com.google.common.collect.Multimap;
//import net.minecraft.core.registries.BuiltInRegistries;
//import net.minecraft.resources.ResourceLocation;
//import net.minecraft.world.entity.EquipmentSlot;
//import net.minecraft.world.entity.LivingEntity;
//import net.minecraft.world.entity.ai.attributes.Attribute;
//import net.minecraft.world.entity.ai.attributes.AttributeModifier;
//import net.minecraft.world.entity.player.Player;
//import net.minecraft.world.item.ItemStack;
//import net.minecraftforge.fml.ModList;
//import pers.roinflam.battlecorrection.utils.LogUtil;
//import top.theillusivec4.curios.api.CuriosApi;
//
//import javax.annotation.Nonnull;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.atomic.AtomicReference;
//
///**
// * Curios模组集成类（替代1.12.2的Baubles）
// * 提供从饰品栏获取物品和属性的功能
// */
public class CuriosIntegration {
//
//    private static boolean curiosLoaded = false;
//
//    /**
//     * 检查Curios是否已加载
//     */
//    public static boolean isCuriosLoaded() {
//        return curiosLoaded;
//    }
//
//    /**
//     * 初始化Curios集成
//     */
//    public static void init() {
//        curiosLoaded = ModList.get().isLoaded("curios");
//        if (curiosLoaded) {
//            LogUtil.info("成功检测到Curios模组，已启用饰品栏属性支持");
//        } else {
//            LogUtil.info("未检测到Curios模组，饰品栏功能将被禁用");
//        }
//    }
//
//    /**
//     * 获取实体的所有饰品栏物品
//     *
//     * @param entity 实体
//     * @return 饰品栏物品列表
//     */
//    @Nonnull
//    public static List<ItemStack> getCurios(@Nonnull LivingEntity entity) {
//        List<ItemStack> curios = new ArrayList<>();
//
//        if (!curiosLoaded) {
//            return curios;
//        }
//
//        try {
//            AtomicReference<List<ItemStack>> result = new AtomicReference<>(new ArrayList<>());
//
//            CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
//                handler.getCurios().forEach((slotId, slotHandler) -> {
//                    for (int i = 0; i < slotHandler.getSlots(); i++) {
//                        ItemStack stack = slotHandler.getStacks().getStackInSlot(i);
//                        if (!stack.isEmpty()) {
//                            result.get().add(stack);
//                        }
//                    }
//                });
//            });
//
//            curios = result.get();
//
//            if (!curios.isEmpty() && entity instanceof Player player) {
//                LogUtil.debug(String.format("玩家 %s 的饰品栏中有 %d 个物品",
//                        player.getName().getString(), curios.size()));
//            }
//        } catch (Exception e) {
//            LogUtil.error("获取饰品栏物品时出错", e);
//        }
//
//        return curios;
//    }
//
//    /**
//     * 从饰品栏物品中收集指定属性的修改器
//     *
//     * @param entity    实体
//     * @param attribute 属性
//     * @return 属性修改器列表数组 [加法, 乘法基础, 乘法总计]
//     */
//    @Nonnull
//    public static List<Double>[] collectModifiersFromCurios(@Nonnull LivingEntity entity,
//                                                            @Nonnull Attribute attribute) {
//        @SuppressWarnings("unchecked")
//        List<Double>[] result = new List[3];
//        result[0] = new ArrayList<>(); // 加法修改器（ADDITION）
//        result[1] = new ArrayList<>(); // 乘法修改器基础（MULTIPLY_BASE）
//        result[2] = new ArrayList<>(); // 乘法修改器总计（MULTIPLY_TOTAL）
//
//        if (!curiosLoaded) {
//            return result;
//        }
//
//        try {
//            List<ItemStack> curios = getCurios(entity);
//
//            for (ItemStack curio : curios) {
//                collectModifiersFromCurio(curio, attribute, result);
//            }
//
//            // 记录调试信息
//            int totalModifiers = result[0].size() + result[1].size() + result[2].size();
//            if (totalModifiers > 0) {
//                LogUtil.debug(String.format("实体 %s 从饰品栏收集到属性 %s: 加法=%d个, 乘法基础=%d个, 乘法总计=%d个",
//                        entity.getName().getString(), attribute.getDescriptionId(),
//                        result[0].size(), result[1].size(), result[2].size()));
//            }
//        } catch (Exception e) {
//            LogUtil.error("从饰品栏收集属性修改器时出错", e);
//        }
//
//        return result;
//    }
//
//    /**
//     * 从单个饰品物品中收集属性修改器
//     *
//     * @param curio     饰品物品
//     * @param attribute 属性
//     * @param result    结果数组 [加法, 乘法基础, 乘法总计]
//     */
//    private static void collectModifiersFromCurio(@Nonnull ItemStack curio,
//                                                  @Nonnull Attribute attribute,
//                                                  @Nonnull List<Double>[] result) {
//        // 尝试所有可能的槽位
//        EquipmentSlot[] allSlots = EquipmentSlot.values();
//
//        for (EquipmentSlot slot : allSlots) {
//            Multimap<Attribute, AttributeModifier> modifiers = curio.getAttributeModifiers(slot);
//
//            for (@Nonnull AttributeModifier modifier : modifiers.get(attribute)) {
//                int operation = modifier.getOperation().ordinal();
//                if (operation >= 0 && operation <= 2) {
//                    // 检查是否已经添加过这个修改器（避免重复）
//                    if (!isModifierAlreadyAdded(modifier, result[operation])) {
//                        result[operation].add(modifier.getAmount());
//                    }
//                }
//            }
//        }
//    }
//
//    /**
//     * 检查修改器是否已经被添加过
//     *
//     * @param modifier       要检查的修改器
//     * @param existingValues 已有的值列表
//     * @return 是否已存在
//     */
//    private static boolean isModifierAlreadyAdded(@Nonnull AttributeModifier modifier,
//                                                  @Nonnull List<Double> existingValues) {
//        double amount = modifier.getAmount();
//        // 使用小误差比较浮点数
//        for (double existing : existingValues) {
//            if (Math.abs(existing - amount) < 0.0001) {
//                return true;
//            }
//        }
//        return false;
//    }
//
//    /**
//     * 检查实体是否装备了特定物品（包括饰品栏）
//     *
//     * @param entity   实体
//     * @param itemName 物品注册名
//     * @return 是否装备
//     */
//    public static boolean hasCurioEquipped(@Nonnull LivingEntity entity, @Nonnull String itemName) {
//        if (!curiosLoaded) {
//            return false;
//        }
//
//        try {
//            List<ItemStack> curios = getCurios(entity);
//            for (ItemStack curio : curios) {
//                // 使用 BuiltInRegistries 获取物品注册名
//                ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(curio.getItem());
//                if (registryName != null && registryName.toString().equals(itemName)) {
//                    return true;
//                }
//            }
//        } catch (Exception e) {
//            LogUtil.error("检查饰品装备时出错", e);
//        }
//
//        return false;
//    }
//
//    /**
//     * 获取实体饰品栏的槽位数量
//     *
//     * @param entity 实体
//     * @return 饰品栏槽位数量，如果不可用则返回0
//     */
//    public static int getCurioSlots(@Nonnull LivingEntity entity) {
//        if (!curiosLoaded) {
//            return 0;
//        }
//
//        try {
//            AtomicReference<Integer> slots = new AtomicReference<>(0);
//            CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
//                handler.getCurios().forEach((slotId, slotHandler) -> {
//                    slots.updateAndGet(v -> v + slotHandler.getSlots());
//                });
//            });
//            return slots.get();
//        } catch (Exception e) {
//            LogUtil.error("获取饰品槽位数量时出错", e);
//        }
//
//        return 0;
//    }
}