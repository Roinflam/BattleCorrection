package pers.roinflam.battlecorrection.compat;

import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import pers.roinflam.battlecorrection.utils.LogUtil;
import top.theillusivec4.curios.api.CuriosApi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Curios模组集成类（替代1.12.2的Baubles）
 * <p>
 * 饰品提供属性有两条路：
 * 1. 饰品自带的 Curios 属性（物品实现了 Curios 接口，或写在 CurioAttributeModifiers 里）：
 * 由 Curios 自己加到实体身上，本来就包含在属性值里，这里不重复统计；
 * 2. 放进饰品栏的物品上的原版 AttributeModifiers（不管写的是哪个装备槽）：
 * Curios 不处理这种格式，由这里统计后补进本模组的 15 个属性。
 * 用命令打过饰品栏标记的物品除外：它们的属性交给 Curios 生效（见 {@link CurioMarkEvents}），这里跳过，避免算两遍。
 * <p>
 * 包含短时缓存，避免同一时间段内重复遍历饰品栏和触发事件总线。
 * 客户端和服务端各用一份缓存：单人游戏时两端在同一个进程里，共用一张 HashMap 会出现两个线程同时写的问题。
 */
public class CuriosIntegration {

    private static boolean curiosLoaded = false;

    /**
     * 缓存有效期（tick数）
     * 默认10tick（0.5秒），饰品栏不会频繁变化，足够安全
     */
    private static final int CACHE_TTL_TICKS = 10;

    /**
     * 原版装备槽列表（缓存起来，避免每次调用 values() 都新建数组）
     */
    private static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();

    /**
     * Curios 未加载时返回的空结果（只读，调用方不会修改）
     */
    private static final List<Double>[] EMPTY_MODIFIERS = createEmptyModifierLists();

    /**
     * 客户端缓存（只在客户端线程访问）
     */
    private static final SideCache CLIENT_CACHE = new SideCache();

    /**
     * 服务端缓存（只在服务端线程访问）
     */
    private static final SideCache SERVER_CACHE = new SideCache();

    /**
     * 单端缓存
     */
    private static final class SideCache {
        /**
         * 上次清空缓存时的游戏刻
         */
        private long cachedTick = -1;
        /**
         * getCurios缓存：entityId -> 饰品列表
         */
        private final Map<Integer, List<ItemStack>> curios = new HashMap<>();
        /**
         * collectModifiersFromCurios缓存：(entityId << 32) | attributeHash -> 结果
         */
        private final Map<Long, List<Double>[]> modifiers = new HashMap<>();
    }

    /**
     * 按实体所在的端取对应缓存，过期时先清空
     *
     * @param entity 用于判断端和获取当前世界tick的实体
     * @return 该端的缓存
     */
    @Nonnull
    private static SideCache getValidCache(@Nonnull LivingEntity entity) {
        SideCache cache = entity.level().isClientSide() ? CLIENT_CACHE : SERVER_CACHE;
        long currentTick = entity.level().getGameTime();
        // 时间倒退（单人游戏切换存档）时也要清空，否则缓存会很久都不过期
        if (currentTick - cache.cachedTick >= CACHE_TTL_TICKS || currentTick < cache.cachedTick) {
            cache.cachedTick = currentTick;
            cache.curios.clear();
            cache.modifiers.clear();
        }
        return cache;
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
     * 检查Curios是否已加载
     *
     * @return true = 已加载 Curios
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
            // 只有装了 Curios 才注册，CurioMarkEvents 引用了 Curios 的类
            MinecraftForge.EVENT_BUS.register(CurioMarkEvents.class);
            LogUtil.info("成功检测到Curios模组，已启用饰品栏属性支持和饰品栏标记功能");
        } else {
            LogUtil.info("未检测到Curios模组，饰品栏功能将被禁用");
        }
    }

    /**
     * 获取实体的所有饰品栏物品（带缓存）
     * 同一缓存周期内（默认10tick），对同一实体的多次调用直接返回缓存结果
     *
     * @param entity 实体
     * @return 饰品栏物品列表（调用方不要修改）；Curios 未加载或读取出错时返回空列表
     */
    @Nonnull
    public static List<ItemStack> getCurios(@Nonnull LivingEntity entity) {
        if (!curiosLoaded) {
            return Collections.emptyList();
        }

        SideCache cache = getValidCache(entity);
        int entityId = entity.getId();

        // 命中缓存直接返回
        List<ItemStack> cached = cache.curios.get(entityId);
        if (cached != null) {
            return cached;
        }

        // 缓存未命中，执行实际查询
        List<ItemStack> curios = new ArrayList<>();

        try {
            CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
                handler.getCurios().forEach((slotId, slotHandler) -> {
                    for (int i = 0; i < slotHandler.getSlots(); i++) {
                        ItemStack stack = slotHandler.getStacks().getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            curios.add(stack);
                        }
                    }
                });
            });

            if (LogUtil.isDetailed() && !curios.isEmpty() && entity instanceof Player player) {
                LogUtil.debug(String.format("玩家 %s 的饰品栏中有 %d 个物品",
                        player.getName().getString(), curios.size()));
            }
        } catch (Exception e) {
            LogUtil.error("获取饰品栏物品时出错，本次按已读到的物品计算", e);
        }

        // 写入缓存
        cache.curios.put(entityId, curios);

        return curios;
    }

    /**
     * 从饰品栏物品中收集指定属性的原版格式修改器（带缓存）
     * 同一缓存周期内，对同一实体+同一属性的多次调用直接返回缓存结果
     *
     * <p>这是性能热点方法。实际计算时会：
     * <ol>
     *   <li>遍历所有饰品栏物品</li>
     *   <li>对每个物品尝试所有EquipmentSlot</li>
     *   <li>getAttributeModifiers会触发Forge事件总线（ItemAttributeModifierEvent）</li>
     * </ol>
     * 缓存后，同一周期内的重复调用开销几乎为零</p>
     *
     * @param entity    实体
     * @param attribute 属性
     * @return 属性修改器列表数组 [加法, 乘法基础, 乘法总计]（调用方不要修改）
     */
    @Nonnull
    public static List<Double>[] collectModifiersFromCurios(@Nonnull LivingEntity entity,
                                                            @Nonnull Attribute attribute) {
        if (!curiosLoaded) {
            return EMPTY_MODIFIERS;
        }

        SideCache cache = getValidCache(entity);
        long cacheKey = makeModifierCacheKey(entity.getId(), attribute);

        // 命中缓存直接返回
        List<Double>[] cached = cache.modifiers.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 缓存未命中，执行实际计算
        List<Double>[] result = createModifierLists();

        try {
            for (ItemStack curio : getCurios(entity)) {
                collectModifiersFromCurio(curio, attribute, result);
            }

            // 记录调试信息
            if (LogUtil.isDetailed()) {
                int totalModifiers = result[0].size() + result[1].size() + result[2].size();
                if (totalModifiers > 0) {
                    LogUtil.debug(String.format("实体 %s 从饰品栏收集到属性 %s: 加法=%d个, 乘法基础=%d个, 乘法总计=%d个",
                            entity.getName().getString(), attribute.getDescriptionId(),
                            result[0].size(), result[1].size(), result[2].size()));
                }
            }
        } catch (Exception e) {
            LogUtil.error("从饰品栏收集属性修改器时出错，本次按已收集到的修改器计算", e);
        }

        // 写入缓存
        cache.modifiers.put(cacheKey, result);

        return result;
    }

    /**
     * 从单个饰品物品中收集属性修改器
     * <p>
     * 没写 Slot 的原版修饰符会在每个装备槽各返回一次，所以需要去重。
     * 去重按"修饰符 UUID"判断，而且只在这一件饰品内部去重：
     * 旧版本按"数值是否已存在"去重、并且所有饰品共用一个判断列表，
     * 导致两件数值相同的饰品只算一件。
     *
     * @param curio     饰品物品
     * @param attribute 属性
     * @param result    结果数组 [加法, 乘法基础, 乘法总计]
     */
    private static void collectModifiersFromCurio(@Nonnull ItemStack curio,
                                                  @Nonnull Attribute attribute,
                                                  @Nonnull List<Double>[] result) {
        // 打过饰品栏标记的物品，属性由 Curios 直接加到身上，这里不再重复统计
        if (CurioMark.isMarked(curio)) {
            return;
        }

        // 大多数饰品没有目标属性，用到时才创建，避免无谓分配
        @Nullable Set<UUID> countedIds = null;

        for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
            Multimap<Attribute, AttributeModifier> allModifiers = curio.getAttributeModifiers(slot);
            Collection<AttributeModifier> modifiers = allModifiers.get(attribute);
            if (modifiers.isEmpty()) {
                continue;
            }
            if (countedIds == null) {
                countedIds = new HashSet<>();
            }

            for (AttributeModifier modifier : modifiers) {
                // 同一条修饰符（同一 UUID）在这件饰品上只算一次
                if (!countedIds.add(modifier.getId())) {
                    continue;
                }
                int operation = modifier.getOperation().ordinal();
                if (operation >= 0 && operation <= 2) {
                    result[operation].add(modifier.getAmount());
                }
            }
        }
    }

    /**
     * 创建一组新的空修改器列表
     *
     * @return 长度为 3 的数组 [加法, 乘法基础, 乘法总计]
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    private static List<Double>[] createModifierLists() {
        List<Double>[] lists = new List[3];
        lists[0] = new ArrayList<>(); // 加法修改器（ADDITION）
        lists[1] = new ArrayList<>(); // 乘法修改器基础（MULTIPLY_BASE）
        lists[2] = new ArrayList<>(); // 乘法修改器总计（MULTIPLY_TOTAL）
        return lists;
    }

    /**
     * 创建只读的空修改器列表（Curios 未加载时共用）
     *
     * @return 长度为 3、元素都是空列表的数组
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    private static List<Double>[] createEmptyModifierLists() {
        List<Double>[] lists = new List[3];
        lists[0] = Collections.emptyList();
        lists[1] = Collections.emptyList();
        lists[2] = Collections.emptyList();
        return lists;
    }
}
