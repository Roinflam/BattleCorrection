package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.battlecorrection.init.ModMobEffects;
import pers.roinflam.battlecorrection.utils.util.MobTargetUtil;

import javax.annotation.Nonnull;

/**
 * 暴动权杖
 * 右键点击一个生物，使它攻击附近的随机生物（持续效果）
 */
public class RiotStaff extends ItemStaff {

    /**
     * 构造暴动权杖
     *
     * @param properties 物品属性
     */
    public RiotStaff(@Nonnull Properties properties) {
        super(properties);
    }

    /**
     * 右键生物：给它挂上暴动效果，并立即挑一个随机目标
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
            if (target instanceof Mob targetMob && target.isAlive()) {
                // 移除其他暴动类效果
                targetMob.removeEffect(ModMobEffects.RIOT_EFFECT.get());
                targetMob.removeEffect(ModMobEffects.BRAWL_EFFECT.get());
                targetMob.removeEffect(ModMobEffects.ELIMINATION_EFFECT.get());

                // 添加暴动效果（10分钟，不显示粒子和图标）
                targetMob.addEffect(new MobEffectInstance(ModMobEffects.RIOT_EFFECT.get(), 12000, 0, false, false));

                // 立即设置一次随机目标
                MobTargetUtil.retargetNearby(targetMob, false);

                // 设置冷却
                player.getCooldowns().addCooldown(this, 20);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}
