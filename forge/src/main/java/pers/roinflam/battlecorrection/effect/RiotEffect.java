// 文件：RiotEffect.java
// 路径：src/main/java/pers/roinflam/battlecorrection/effect/RiotEffect.java
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
 * 暴动效果
 * 使生物持续攻击随机目标
 */
public class RiotEffect extends MobEffect {

    public RiotEffect(@Nonnull MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(@Nonnull LivingEntity entity, int amplifier) {
        if (entity instanceof Mob mob && !entity.level().isClientSide()) {
            // 每100刻或目标死亡时重新选择目标
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

    /**
     * 为生物设置随机攻击目标
     */
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