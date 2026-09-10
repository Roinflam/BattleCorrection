// CuriosIntegration.java
// 路径：src/main/java/pers/roinflam/battlecorrection/compat/CuriosIntegration.java
package pers.roinflam.battlecorrection.compat;

import com.google.common.collect.Multimap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import pers.roinflam.battlecorrection.utils.LogUtil;
import top.theillusivec4.curios.api.CuriosApi;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Curios模组集成类（替代1.12.2的Baubles）
 * 提供从饰品栏获取物品和属性的功能
 *
 * <p>包含tick级缓存机制，避免同一tick内重复遍历饰品栏和触发事件总线</p>
 */
public class CuriosIntegration {

    private static boolean curiosLoaded = false;

    // ========== tick级缓存 ==========
    // 缓存过期的tick（全局），用于判断缓存是否过期
    private static long cachedTick = -1;

    // getCurios缓存：entityId -> 饰品列表
    private static final Map<Integer, List<ItemStack>> curiosCache = new HashMap<>();

    // collectModifiersFromCurios缓存：(entityId * 31 + attributeHash) -> 结果
    private static final Map<Long, List<Double>[]> modifierCache = new HashMap<>();

    /**
     * 缓存有效期（tick数）
     * 默认10tick（0.5秒），饰品栏不会频繁变化，足够安全
     */
    private static final int CACHE_TTL_TICKS = 10;

    /**
     * 检查并在需要时清理过期缓存
     * 基于游戏tick判断，超过TTL就清空所有缓存
     *
     * @param entity 用于获取当前世界tick的实体
     */
    private static void checkAndInvalidateCache(@Nonnull LivingEntity entity) {
        long currentTick = entity.level().getGameTime();
        if (currentTick - cachedTick >= CACHE_TTL_TICKS) {
            cachedTick = currentTick;
            curiosCache.clear();
            modifierCache.clear();
        }
    }

    /**
     * 生成modifier缓存的key
     * 将entityId和attribute的哈希值组合成唯一的long型key
     *
     * @param entityId  实体ID
     * @param attribute 属性对象
     * @return 缓存key
     */
    private static long makeModifierCacheKey(int entityId, @Nonnull Attribute attribute) {
        return ((long) entityId << 32) | (attribute.hashCode() & 0xFFFFFFFFL);
    }

    /**
     * 手动清除所有缓存
     * 可在玩家装备变化等场景下调用
     */
    public static void invalidateCache() {
        cachedTick = -1;
        curiosCache.clear();
        modifierCache.clear();
    }

    /**
     * 清除指定实体的缓存
     * 当已知某个实体的饰品发生变化时调用
     *
     * @param entity 需要清除缓存的实体
     */
    public static void invalidateCacheFor(@Nonnull LivingEntity entity) {
        int entityId = entity.getId();
        curiosCache.remove(entityId);
        // 移除该实体相关的所有modifier缓存
        modifierCache.entrySet().removeIf(entry -> (entry.getKey() >> 32) == entityId);
    }

    /**
     * 检查Curios是否已加载
     */
    public static boolean isCuriosLoaded() {
        return curiosLoaded;
    }

    /**
     * 初始化Curios集成
     */
    public static void init() {
        curiosLoaded = ModList.get().isLoaded("curios");
        if (curiosLoaded) {
            LogUtil.info("成功检测到Curios模组，已启用饰品栏属性支持");
        } else {
            LogUtil.info("未检测到Curios模组，饰品栏功能将被禁用");
        }
    }

    /**
     * 获取实体的所有饰品栏物品（带缓存）
     * 同一缓存周期内（默认10tick），对同一实体的多次调用直接返回缓存结果
     *
     * @param entity 实体
     * @return 饰品栏物品列表
     */
    @Nonnull
    public static List<ItemStack> getCurios(@Nonnull LivingEntity entity) {
        if (!curiosLoaded) {
            return new ArrayList<>();
        }

        // 检查缓存有效性
        checkAndInvalidateCache(entity);

        int entityId = entity.getId();

        // 命中缓存直接返回
        List<ItemStack> cached = curiosCache.get(entityId);
        if (cached != null) {
            return cached;
        }

        // 缓存未命中，执行实际查询
        List<ItemStack> curios = new ArrayList<>();

        try {
            AtomicReference<List<ItemStack>> result = new AtomicReference<>(curios);

            CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
                handler.getCurios().forEach((slotId, slotHandler) -> {
                    for (int i = 0; i < slotHandler.getSlots(); i++) {
                        ItemStack stack = slotHandler.getStacks().getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            result.get().add(stack);
                        }
                    }
                });
            });

            curios = result.get();

            if (!curios.isEmpty() && entity instanceof Player player) {
                LogUtil.debug(String.format("玩家 %s 的饰品栏中有 %d 个物品",
                        player.getName().getString(), curios.size()));
            }
        } catch (Exception e) {
            LogUtil.error("获取饰品栏物品时出错", e);
        }

        // 写入缓存
        curiosCache.put(entityId, curios);

        return curios;
    }

    /**
     * 从饰品栏物品中收集指定属性的修改器（带缓存）
     * 同一缓存周期内，对同一实体+同一属性的多次调用直接返回缓存结果
     *
     * <p>这是性能热点方法。原始实现每次调用都会：
     * <ol>
     *   <li>遍历所有饰品栏物品</li>
     *   <li>对每个物品尝试所有EquipmentSlot</li>
     *   <li>getAttributeModifiers会触发Forge事件总线（ItemAttributeModifierEvent）</li>
     * </ol>
     * 缓存后，同一tick内的重复调用开销几乎为零</p>
     *
     * @param entity    实体
     * @param attribute 属性
     * @return 属性修改器列表数组 [加法, 乘法基础, 乘法总计]
     */
    @Nonnull
    public static List<Double>[] collectModifiersFromCurios(@Nonnull LivingEntity entity,
                                                            @Nonnull Attribute attribute) {
        if (!curiosLoaded) {
            @SuppressWarnings("unchecked")
            List<Double>[] empty = new List[3];
            empty[0] = new ArrayList<>();
            empty[1] = new ArrayList<>();
            empty[2] = new ArrayList<>();
            return empty;
        }

        // 检查缓存有效性
        checkAndInvalidateCache(entity);

        long cacheKey = makeModifierCacheKey(entity.getId(), attribute);

        // 命中缓存直接返回
        List<Double>[] cached = modifierCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 缓存未命中，执行实际计算
        @SuppressWarnings("unchecked")
        List<Double>[] result = new List[3];
        result[0] = new ArrayList<>(); // 加法修改器（ADDITION）
        result[1] = new ArrayList<>(); // 乘法修改器基础（MULTIPLY_BASE）
        result[2] = new ArrayList<>(); // 乘法修改器总计（MULTIPLY_TOTAL）

        try {
            List<ItemStack> curios = getCurios(entity);

            for (ItemStack curio : curios) {
                collectModifiersFromCurio(curio, attribute, result);
            }

            // 记录调试信息
            int totalModifiers = result[0].size() + result[1].size() + result[2].size();
            if (totalModifiers > 0) {
                LogUtil.debug(String.format("实体 %s 从饰品栏收集到属性 %s: 加法=%d个, 乘法基础=%d个, 乘法总计=%d个",
                        entity.getName().getString(), attribute.getDescriptionId(),
                        result[0].size(), result[1].size(), result[2].size()));
            }
        } catch (Exception e) {
            LogUtil.error("从饰品栏收集属性修改器时出错", e);
        }

        // 写入缓存
        modifierCache.put(cacheKey, result);

        return result;
    }

    /**
     * 从单个饰品物品中收集属性修改器
     *
     * @param curio     饰品物品
     * @param attribute 属性
     * @param result    结果数组 [加法, 乘法基础, 乘法总计]
     */
    private static void collectModifiersFromCurio(@Nonnull ItemStack curio,
                                                  @Nonnull Attribute attribute,
                                                  @Nonnull List<Double>[] result) {
        EquipmentSlot[] allSlots = EquipmentSlot.values();

        for (EquipmentSlot slot : allSlots) {
            Multimap<Attribute, AttributeModifier> modifiers = curio.getAttributeModifiers(slot);

            for (@Nonnull AttributeModifier modifier : modifiers.get(attribute)) {
                int operation = modifier.getOperation().ordinal();
                if (operation >= 0 && operation <= 2) {
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
        for (double existing : existingValues) {
            if (Math.abs(existing - amount) < 0.0001) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查实体是否装备了特定物品（包括饰品栏）
     *
     * @param entity   实体
     * @param itemName 物品注册名
     * @return 是否装备
     */
    public static boolean hasCurioEquipped(@Nonnull LivingEntity entity, @Nonnull String itemName) {
        if (!curiosLoaded) {
            return false;
        }

        try {
            // getCurios已带缓存，这里直接调用即可
            List<ItemStack> curios = getCurios(entity);
            for (ItemStack curio : curios) {
                ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(curio.getItem());
                if (registryName != null && registryName.toString().equals(itemName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            LogUtil.error("检查饰品装备时出错", e);
        }

        return false;
    }

    /**
     * 获取实体饰品栏的槽位数量
     *
     * @param entity 实体
     * @return 饰品栏槽位数量，如果不可用则返回0
     */
    public static int getCurioSlots(@Nonnull LivingEntity entity) {
        if (!curiosLoaded) {
            return 0;
        }

        try {
            AtomicReference<Integer> slots = new AtomicReference<>(0);
            CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
                handler.getCurios().forEach((slotId, slotHandler) -> {
                    slots.updateAndGet(v -> v + slotHandler.getSlots());
                });
            });
            return slots.get();
        } catch (Exception e) {
            LogUtil.error("获取饰品槽位数量时出错", e);
        }

        return 0;
    }
}