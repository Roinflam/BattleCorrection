// 文件：RebelStaff.java
// 路径：src/main/java/pers/roinflam/battlecorrection/item/manage/RebelStaff.java
package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 叛军权杖
 * 右键点击一个生物，使周围所有生物攻击它
 */
public class RebelStaff extends ItemStaff {

    public RebelStaff(@Nonnull Properties properties) {
        super(properties);
    }

    @Override
    @Nonnull
    public InteractionResult interactLivingEntity(@Nonnull ItemStack stack, @Nonnull Player player,
                                                  @Nonnull LivingEntity target, @Nonnull InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide()) {
            if (target instanceof Mob targetMob && target.isAlive()) {
                // 获取周围64格内的所有Mob
                AABB searchBox = target.getBoundingBox().inflate(64.0D);
                List<Mob> nearbyMobs = player.level().getEntitiesOfClass(
                        Mob.class,
                        searchBox,
                        mob -> mob != targetMob && mob.isAlive()
                );

                // 让所有周围的生物攻击目标
                for (Mob mob : nearbyMobs) {
                    mob.setTarget(targetMob);
                }

                // 设置冷却
                player.getCooldowns().addCooldown(this, 20);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}