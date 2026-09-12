package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import pers.roinflam.battlecorrection.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 范围献祭权杖
 * 右键点击生物，使周围所有非玩家生物立即死亡
 */
public class RangeSacrificialStaff extends ItemStaff {

    /**
     * 构造范围献祭权杖
     *
     * @param properties 物品属性
     */
    public RangeSacrificialStaff(@Nonnull Properties properties) {
        super(properties);
    }

    /**
     * 右键生物：击杀以目标为中心 64 格内的所有非玩家生物
     *
     * @param stack  手中的权杖
     * @param player 使用者
     * @param target 被右键的生物（范围中心）
     * @param hand   使用的手
     * @return 服务端主手成功处理时返回 SUCCESS，否则 PASS
     */
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
                EntityLivingUtil.kill(entity, damageSource);
            }

            // 设置冷却
            player.getCooldowns().addCooldown(this, 20);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
