package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.battlecorrection.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 献祭权杖
 * 右键点击生物使其立即死亡
 */
public class SacrificialStaff extends ItemStaff {

    /**
     * 构造献祭权杖
     *
     * @param properties 物品属性
     */
    public SacrificialStaff(@Nonnull Properties properties) {
        super(properties);
    }

    /**
     * 右键生物：以玩家攻击的名义击杀目标（保留击杀归属和掉落），被免疫时强制死亡
     *
     * @param stack  手中的权杖
     * @param player 使用者
     * @param target 被右键的生物
     * @param hand   使用的手
     * @return 服务端主手成功处理时返回 SUCCESS，否则 PASS
     */
    @Override
    @Nonnull
    public InteractionResult interactLivingEntity(@Nonnull ItemStack stack, @Nonnull Player player,
                                                  @Nonnull LivingEntity target, @Nonnull InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide()) {
            EntityLivingUtil.kill(target, player.damageSources().playerAttack(player));

            // 设置冷却
            player.getCooldowns().addCooldown(this, 20);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
