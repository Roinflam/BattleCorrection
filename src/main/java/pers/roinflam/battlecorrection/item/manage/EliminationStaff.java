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
import pers.roinflam.battlecorrection.utils.util.MobTargetUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 消灭权杖
 * 右键使用，让附近不同种类的生物互相攻击
 */
public class EliminationStaff extends ItemStaff {

    /**
     * 构造消灭权杖
     *
     * @param properties 物品属性
     */
    public EliminationStaff(@Nonnull Properties properties) {
        super(properties);
    }

    /**
     * 对空气右键：给周围 64 格内的所有生物挂上消灭效果，并随机设为不同种类生物的目标
     *
     * @param level  所在世界
     * @param player 使用者
     * @param hand   使用的手
     * @return 服务端主手成功处理时返回 success，否则 pass
     */
    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player,
                                                  @Nonnull InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (hand == InteractionHand.MAIN_HAND && !level.isClientSide()) {
            // 获取周围64格内的所有Mob
            AABB searchBox = player.getBoundingBox().inflate(MobTargetUtil.SEARCH_RADIUS);
            List<Mob> nearbyMobs = level.getEntitiesOfClass(Mob.class, searchBox, Mob::isAlive);

            for (Mob mob : nearbyMobs) {
                // 移除其他暴动类效果
                mob.removeEffect(ModMobEffects.RIOT_EFFECT.get());
                mob.removeEffect(ModMobEffects.BRAWL_EFFECT.get());
                mob.removeEffect(ModMobEffects.ELIMINATION_EFFECT.get());

                // 添加消灭效果
                mob.addEffect(new MobEffectInstance(ModMobEffects.ELIMINATION_EFFECT.get(), 12000, 0, false, false));

                // 只挑不同种类的生物作为目标
                Mob randomTarget = MobTargetUtil.pickRandom(nearbyMobs, mob, true);
                if (randomTarget != null) {
                    MobTargetUtil.setMutualTarget(mob, randomTarget);
                }
            }

            // 设置冷却
            player.getCooldowns().addCooldown(this, 20);
            return InteractionResultHolder.success(itemStack);
        }

        return InteractionResultHolder.pass(itemStack);
    }
}
