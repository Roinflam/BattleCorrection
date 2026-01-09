// 文件：EliminationEffect.java
// 路径：src/main/java/pers/roinflam/battlecorrection/effect/EliminationEffect.java
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
 * 消灭效果
 * 使生物只攻击不同种类的目标
 */
public class EliminationEffect extends MobEffect {

    public EliminationEffect(@Nonnull MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(@Nonnull LivingEntity entity, int amplifier) {
        if (entity instanceof Mob mob && !entity.level().isClientSide()) {
            if (entity.level().getGameTime() % 100 == 0 ||
                    mob.getTarget() == null ||
                    !mob.getTarget().isAlive()) {

                setDifferentSpeciesTarget(mob);
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // 每tick都执行检查
        return true;
    }

    /**
     * 为生物设置不同种类的攻击目标
     */
    private void setDifferentSpeciesTarget(@Nonnull Mob attacker) {
        AABB searchBox = attacker.getBoundingBox().inflate(64.0D);
        List<Mob> differentSpecies = attacker.level().getEntitiesOfClass(
                Mob.class,
                searchBox,
                mob -> mob != attacker && mob.isAlive() && mob.getType() != attacker.getType()
        );

        if (!differentSpecies.isEmpty()) {
            Mob randomTarget = differentSpecies.get(RandomUtil.getInt(0, differentSpecies.size() - 1));
            attacker.setTarget(randomTarget);
            randomTarget.setTarget(attacker);
        }
    }
}