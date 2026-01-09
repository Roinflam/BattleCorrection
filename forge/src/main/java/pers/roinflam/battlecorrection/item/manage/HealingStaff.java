// 文件：HealingStaff.java
// 路径：src/main/java/pers/roinflam/battlecorrection/item/manage/HealingStaff.java
package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * 治疗权杖
 * 右键点击生物使其完全恢复生命
 */
public class HealingStaff extends ItemStaff {

    public HealingStaff(@Nonnull Properties properties) {
        super(properties);
    }

    @Override
    @Nonnull
    public InteractionResult interactLivingEntity(@Nonnull ItemStack stack, @Nonnull Player player,
                                                  @Nonnull LivingEntity target, @Nonnull InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide()) {
            // 完全恢复目标生命值
            target.setHealth(target.getMaxHealth());

            // 设置冷却
            player.getCooldowns().addCooldown(this, 20);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}