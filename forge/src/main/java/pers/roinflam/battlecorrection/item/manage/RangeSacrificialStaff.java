// 文件：RangeSacrificialStaff.java
// 路径：src/main/java/pers/roinflam/battlecorrection/item/manage/RangeSacrificialStaff.java
package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 范围献祭权杖
 * 右键点击生物，使周围所有非玩家生物立即死亡
 */
public class RangeSacrificialStaff extends ItemStaff {

    public RangeSacrificialStaff(@Nonnull Properties properties) {
        super(properties);
    }

    @Override
    @Nonnull
    public InteractionResult interactLivingEntity(@Nonnull ItemStack stack, @Nonnull Player player,
                                                  @Nonnull LivingEntity target, @Nonnull InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide()) {
            // 获取周围64格内的所有非玩家生物
            AABB searchBox = target.getBoundingBox().inflate(64.0D);
            List<LivingEntity> nearbyEntities = player.level().getEntitiesOfClass(
                    LivingEntity.class,
                    searchBox,
                    entity -> !(entity instanceof Player) && entity.isAlive()
            );

            // 创建玩家攻击伤害源
            DamageSource damageSource = player.damageSources().playerAttack(player);

            // 杀死所有非玩家生物
            for (LivingEntity entity : nearbyEntities) {
                entity.hurt(damageSource, entity.getMaxHealth() * 100);
                if (entity.isAlive()) {
                    entity.die(damageSource);
                    entity.setHealth(0);
                }
            }

            // 设置冷却
            player.getCooldowns().addCooldown(this, 20);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}