// 文件：RiotStaff.java
// 路径：src/main/java/pers/roinflam/battlecorrection/item/manage/RiotStaff.java
package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import pers.roinflam.battlecorrection.init.ModMobEffects;
import pers.roinflam.battlecorrection.utils.random.RandomUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 暴动权杖
 * 右键点击一个生物，使它攻击附近的随机生物（持续效果）
 */
public class RiotStaff extends ItemStaff {

    public RiotStaff(@Nonnull Properties properties) {
        super(properties);
    }

    @Override
    @Nonnull
    public InteractionResult interactLivingEntity(@Nonnull ItemStack stack, @Nonnull Player player,
                                                  @Nonnull LivingEntity target, @Nonnull InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide()) {
            if (target instanceof Mob targetMob && target.isAlive()) {
                // 移除其他暴动效果
                targetMob.removeEffect(ModMobEffects.RIOT_EFFECT.get());
                targetMob.removeEffect(ModMobEffects.BRAWL_EFFECT.get());
                targetMob.removeEffect(ModMobEffects.ELIMINATION_EFFECT.get());

                // 添加暴动效果（10分钟）
                targetMob.addEffect(new MobEffectInstance(ModMobEffects.RIOT_EFFECT.get(), 12000, 0, false, false));

                // 立即设置一个随机目标
                setRandomTarget(targetMob);

                // 设置冷却
                player.getCooldowns().addCooldown(this, 20);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * 为生物设置随机攻击目标
     */
    public static void setRandomTarget(@Nonnull Mob attacker) {
        AABB searchBox = attacker.getBoundingBox().inflate(64.0D);
        List<Mob> nearbyMobs = attacker.level().getEntitiesOfClass(
                Mob.class,
                searchBox,
                mob -> mob != attacker && mob.isAlive()
        );

        if (!nearbyMobs.isEmpty()) {
            Mob randomTarget = nearbyMobs.get(RandomUtil.getInt(0, nearbyMobs.size() - 1));
            attacker.setTarget(randomTarget);
            randomTarget.setTarget(attacker);
        }
    }
}