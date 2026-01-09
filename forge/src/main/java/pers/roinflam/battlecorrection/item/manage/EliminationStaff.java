// 文件：EliminationStaff.java
// 路径：src/main/java/pers/roinflam/battlecorrection/item/manage/EliminationStaff.java
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
 * 消灭权杖
 * 右键使用，让不同种类的生物互相攻击
 */
public class EliminationStaff extends ItemStaff {

    public EliminationStaff(@Nonnull Properties properties) {
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

                // 添加消灭效果
                mob.addEffect(new MobEffectInstance(ModMobEffects.ELIMINATION_EFFECT.get(), 12000, 0, false, false));

                // 只攻击不同种类的生物
                List<Mob> differentSpecies = nearbyMobs.stream()
                        .filter(other -> other != mob && other.getType() != mob.getType())
                        .toList();

                if (!differentSpecies.isEmpty()) {
                    Mob randomTarget = differentSpecies.get(RandomUtil.getInt(0, differentSpecies.size() - 1));
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