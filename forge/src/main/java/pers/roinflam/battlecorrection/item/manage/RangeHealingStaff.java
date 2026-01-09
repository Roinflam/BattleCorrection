// 文件：RangeHealingStaff.java
// 路径：src/main/java/pers/roinflam/battlecorrection/item/manage/RangeHealingStaff.java
package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 范围治疗权杖
 * 右键点击生物，使周围所有生物完全恢复生命
 */
public class RangeHealingStaff extends ItemStaff {

    public RangeHealingStaff(@Nonnull Properties properties) {
        super(properties);
    }

    @Override
    @Nonnull
    public InteractionResult interactLivingEntity(@Nonnull ItemStack stack, @Nonnull Player player,
                                                  @Nonnull LivingEntity target, @Nonnull InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide()) {
            // 获取周围64格内的所有生物
            AABB searchBox = target.getBoundingBox().inflate(64.0D);
            List<LivingEntity> nearbyEntities = player.level().getEntitiesOfClass(
                    LivingEntity.class,
                    searchBox,
                    LivingEntity::isAlive
            );

            // 治疗所有生物
            for (LivingEntity entity : nearbyEntities) {
                entity.setHealth(entity.getMaxHealth());
            }

            // 设置冷却
            player.getCooldowns().addCooldown(this, 20);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}