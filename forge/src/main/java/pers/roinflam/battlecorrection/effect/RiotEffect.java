package pers.roinflam.battlecorrection.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import pers.roinflam.battlecorrection.utils.util.MobTargetUtil;

import javax.annotation.Nonnull;

/**
 * 暴动效果
 * 使生物持续攻击附近的随机目标
 * <p>
 * 每 5 秒强制换一次目标；目标丢失后最多 1 秒内重新找（旧版本找不到目标时每 tick 都扫一遍 64 格）。
 */
public class RiotEffect extends MobEffect {

    /**
     * 构造暴动效果
     *
     * @param category 效果类别
     * @param color    效果颜色
     */
    public RiotEffect(@Nonnull MobEffectCategory category, int color) {
        super(category, color);
    }

    /**
     * 每 tick 调用：按需为生物重新挑选攻击目标（仅服务端）
     *
     * @param entity    带有此效果的实体
     * @param amplifier 效果等级
     */
    @Override
    public void applyEffectTick(@Nonnull LivingEntity entity, int amplifier) {
        if (entity instanceof Mob mob && !entity.level().isClientSide() && MobTargetUtil.shouldRetarget(mob)) {
            MobTargetUtil.retargetNearby(mob, false);
        }
    }

    /**
     * 每 tick 都执行 applyEffectTick
     *
     * @param duration  剩余持续时间
     * @param amplifier 效果等级
     * @return 始终为 true
     */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}
