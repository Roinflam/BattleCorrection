package pers.roinflam.battlecorrection.utils.util;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 生物互殴目标工具类
 * <p>
 * 暴动 / 群殴 / 消灭三根权杖和对应的三个药水效果共用这里的"找随机目标、互相设为攻击目标"逻辑。
 * 旧版本同样的代码复制了四份，而且药水效果在找不到目标时每 tick 都扫一遍 64 格范围。
 * <p>
 * 所有方法都只应在服务端调用。
 */
public final class MobTargetUtil {

    /**
     * 搜索半径（格）：以生物碰撞箱为中心向外扩展
     */
    public static final double SEARCH_RADIUS = 64.0D;

    /**
     * 定期强制换目标的间隔（tick）：5 秒
     */
    private static final int RETARGET_INTERVAL_TICKS = 100;

    /**
     * 目标丢失后重新搜索的间隔（tick）：1 秒
     * 找不到目标时不再每 tick 搜索，最多晚 1 秒找到新目标
     */
    private static final int LOST_TARGET_RETRY_TICKS = 20;

    private MobTargetUtil() {
    }

    /**
     * 判断这一 tick 是否需要给生物重新挑选目标
     *
     * @param mob 带有暴动/群殴/消灭效果的生物
     * @return true = 需要重新挑选（到了定期换目标的时间，或者目标已丢失且轮到它重试）
     */
    public static boolean shouldRetarget(@Nonnull Mob mob) {
        long gameTime = mob.level().getGameTime();
        if (gameTime % RETARGET_INTERVAL_TICKS == 0) {
            return true;
        }

        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive()) {
            return false;
        }

        // 目标丢失：按实体 ID 错开到不同的 tick，避免一大群生物在同一 tick 集中搜索
        return Math.floorMod(gameTime + mob.getId(), (long) LOST_TARGET_RETRY_TICKS) == 0;
    }

    /**
     * 在生物周围搜索并挑一个随机目标，双方互相设为攻击目标
     *
     * @param attacker             需要找目标的生物
     * @param differentSpeciesOnly true = 只挑不同种类的生物（消灭效果）
     */
    public static void retargetNearby(@Nonnull Mob attacker, boolean differentSpeciesOnly) {
        AABB searchBox = attacker.getBoundingBox().inflate(SEARCH_RADIUS);
        List<Mob> candidates = attacker.level().getEntitiesOfClass(Mob.class, searchBox, Mob::isAlive);

        Mob target = pickRandom(candidates, attacker, differentSpeciesOnly);
        if (target != null) {
            setMutualTarget(attacker, target);
        }
    }

    /**
     * 从候选列表里随机挑一个合格的目标（不新建列表）
     * <p>
     * 用蓄水池抽样：第 n 个合格者以 1/n 的概率替换当前结果，遍历一遍后每个合格者被选中的概率相同。
     *
     * @param candidates           候选生物列表
     * @param self                 发起者自己（会被排除）
     * @param differentSpeciesOnly true = 只挑与发起者不同种类的生物
     * @return 挑中的目标；没有合格者时返回 null
     */
    @Nullable
    public static Mob pickRandom(@Nonnull List<Mob> candidates, @Nonnull Mob self, boolean differentSpeciesOnly) {
        RandomSource random = self.getRandom();
        Mob chosen = null;
        int eligibleCount = 0;

        for (Mob candidate : candidates) {
            if (candidate == self || !candidate.isAlive()) {
                continue;
            }
            if (differentSpeciesOnly && candidate.getType() == self.getType()) {
                continue;
            }
            eligibleCount++;
            if (random.nextInt(eligibleCount) == 0) {
                chosen = candidate;
            }
        }
        return chosen;
    }

    /**
     * 让两个生物互相把对方设为攻击目标
     *
     * @param first  生物 A
     * @param second 生物 B
     */
    public static void setMutualTarget(@Nonnull Mob first, @Nonnull Mob second) {
        first.setTarget(second);
        second.setTarget(first);
    }
}
