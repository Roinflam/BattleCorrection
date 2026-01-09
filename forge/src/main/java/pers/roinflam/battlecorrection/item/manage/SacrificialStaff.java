// 文件：SacrificialStaff.java
// 路径：src/main/java/pers/roinflam/battlecorrection/item/manage/SacrificialStaff.java
package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * 献祭权杖
 * 右键点击生物使其立即死亡
 */
public class SacrificialStaff extends ItemStaff {

    public SacrificialStaff(@Nonnull Properties properties) {
        super(properties);
    }

    @Override
    @Nonnull
    public InteractionResult interactLivingEntity(@Nonnull ItemStack stack, @Nonnull Player player,
                                                  @Nonnull LivingEntity target, @Nonnull InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide()) {
            // 创建玩家攻击伤害源
            DamageSource damageSource = player.damageSources().playerAttack(player);

            // 造成大量伤害
            target.hurt(damageSource, target.getMaxHealth() * 100);

            // 如果还活着，强制死亡
            if (target.isAlive()) {
                target.die(damageSource);
                target.setHealth(0);
            }

            // 设置冷却
            player.getCooldowns().addCooldown(this, 20);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}