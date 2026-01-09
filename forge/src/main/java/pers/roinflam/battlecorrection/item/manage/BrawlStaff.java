// 文件：BrawlStaff.java
// 路径：src/main/java/pers/roinflam/battlecorrection/item/manage/BrawlStaff.java
package pers.roinflam.battlecorrection.item.manage;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import pers.roinflam.battlecorrection.init.ModMobEffects;
import pers.roinflam.battlecorrection.utils.random.RandomUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 群殴权杖
 * 右键使用，让附近所有生物互相攻击
 */
public class BrawlStaff extends ItemStaff {

    public BrawlStaff(@Nonnull Properties properties) {
        super(properties);
    }

    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player,
                                                  @Nonnull InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (hand == InteractionHand.MAIN_HAND && !level.isClientSide()) {
            // 获取周围64格内的所有Mob
            AABB searchBox = player.getBoundingBox().inflate(64.0D);
            List<Mob> nearbyMobs = level.getEntitiesOfClass(
                    Mob.class,
                    searchBox,
                    Mob::isAlive
            );

            for (Mob mob : nearbyMobs) {
                // 移除其他暴动效果
                mob.removeEffect(ModMobEffects.RIOT_EFFECT.get());
                mob.removeEffect(ModMobEffects.BRAWL_EFFECT.get());
                mob.removeEffect(ModMobEffects.ELIMINATION_EFFECT.get());

                // 添加群殴效果
                mob.addEffect(new MobEffectInstance(ModMobEffects.BRAWL_EFFECT.get(), 12000, 0, false, false));

                // 设置随机目标
                List<Mob> potentialTargets = nearbyMobs.stream()
                        .filter(other -> other != mob)
                        .toList();

                if (!potentialTargets.isEmpty()) {
                    Mob randomTarget = potentialTargets.get(RandomUtil.getInt(0, potentialTargets.size() - 1));
                    mob.setTarget(randomTarget);
                    randomTarget.setTarget(mob);
                }
            }

            // 设置冷却
            player.getCooldowns().addCooldown(this, 20);
            return InteractionResultHolder.success(itemStack);
        }

        return InteractionResultHolder.pass(itemStack);
    }
}