package pers.roinflam.battlecorrection.utils.util;

import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;

/**
 * 实体工具类
 * 提供实体查找和操作功能
 * <p>
 * 权杖和药水效果的"周围生物"搜索用的是实体碰撞箱外扩（见 {@link MobTargetUtil}），
 * 这里的方法以方块坐标为中心，供其他需要按坐标取范围的调用方使用。
 */
public class EntityUtil {

    /**
     * 获取周围指定范围内的实体
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(@Nonnull Class<? extends T> clazz, @Nonnull Entity entity, double range) {
        return getNearbyEntities(clazz, entity, range, range, range, null);
    }

    /**
     * 获取周围指定范围内的实体（带过滤器）
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(@Nonnull Class<? extends T> clazz, @Nonnull Entity entity, double range, @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity, range, range, range, predicate);
    }

    /**
     * 获取周围指定范围内的实体（水平、垂直范围分开给）
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(@Nonnull Class<? extends T> clazz, @Nonnull Entity entity, double width, double height, @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity, width, width, height, predicate);
    }

    /**
     * 获取周围指定范围内的实体（指定各轴范围，参数顺序为 x、z、y）
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(@Nonnull Class<? extends T> clazz, @Nonnull Entity entity, double x, double z, double y, @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity.world, entity.getPosition(), x, z, y, predicate);
    }

    /**
     * 获取指定位置周围的实体
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(@Nonnull Class<? extends T> clazz, @Nonnull World world, @Nonnull BlockPos blockPos, double x, double z, double y, @Nullable Predicate<? super T> predicate) {
        @Nonnull AxisAlignedBB axisAlignedBB = new AxisAlignedBB(blockPos.getX() - x, blockPos.getY() - y, blockPos.getZ() - z, blockPos.getX() + x, blockPos.getY() + y, blockPos.getZ() + z);
        return world.getEntitiesWithinAABB(clazz, axisAlignedBB, predicate);
    }

    public static int getFire(Entity entity) {
        try {
            @Nonnull Field field = Entity.class.getDeclaredField("fire");
            field.setAccessible(true);
            return (int) field.get(entity);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            try {
                @Nonnull Field field = Entity.class.getDeclaredField("field_190534_ay");
                field.setAccessible(true);
                return (int) field.get(entity);
            } catch (NoSuchFieldException | IllegalAccessException e1) {
                e.printStackTrace();
            }
        }
        return -999;
    }

}
