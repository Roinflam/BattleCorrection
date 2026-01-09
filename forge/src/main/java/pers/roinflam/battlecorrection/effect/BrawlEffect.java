// 文件：BrawlEffect.java
// 路径：src/main/java/pers/roinflam/battlecorrection/effect/BrawlEffect.java
package pers.roinflam.battlecorrection.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import pers.roinflam.battlecorrection.utils.random.RandomUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 群殴效果
 * 使生物持续攻击随机目标（与暴动效果相同逻辑）
 */
public class BrawlEffect extends MobEffect {

    public BrawlEffect(@Nonnull MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(@Nonnull LivingEntity entity, int amplifier) {
        if (entity instanceof Mob mob && !entity.level().isClientSide()) {
            if (entity.level().getGameTime() % 100 == 0 ||
                    mob.getTarget() == null ||
                    !mob.getTarget().isAlive()) {

                setRandomTarget(mob);
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // 每tick都执行检查
        return true;
    }

    private void setRandomTarget(@Nonnull Mob attacker) {
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