// 文件：EntityUtil.java
// 路径：src/main/java/pers/roinflam/battlecorrection/utils/util/EntityUtil.java
package pers.roinflam.battlecorrection.utils.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * 实体工具类
 * 提供实体查找和操作功能
 */
public class EntityUtil {

    /**
     * 获取周围指定范围内的实体
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(@Nonnull Class<T> clazz,
                                                               @Nonnull Entity entity,
                                                               double range) {
        return getNearbyEntities(clazz, entity, range, range, range, null);
    }

    /**
     * 获取周围指定范围内的实体（带过滤器）
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(@Nonnull Class<T> clazz,
                                                               @Nonnull Entity entity,
                                                               double range,
                                                               @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity, range, range, range, predicate);
    }

    /**
     * 获取周围指定范围内的实体（指定各轴范围）
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(@Nonnull Class<T> clazz,
                                                               @Nonnull Entity entity,
                                                               double x, double y, double z,
                                                               @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity.level(), entity.blockPosition(), x, y, z, predicate);
    }

    /**
     * 获取指定位置周围的实体
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(@Nonnull Class<T> clazz,
                                                               @Nonnull Level level,
                                                               @Nonnull BlockPos blockPos,
                                                               double x, double y, double z,
                                                               @Nullable Predicate<? super T> predicate) {
        AABB aabb = new AABB(
                blockPos.getX() - x, blockPos.getY() - y, blockPos.getZ() - z,
                blockPos.getX() + x, blockPos.getY() + y, blockPos.getZ() + z
        );

        return level.getEntitiesOfClass(clazz, aabb, predicate);
    }
}