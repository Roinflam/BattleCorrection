package pers.roinflam.battlecorrection.effect;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import pers.roinflam.battlecorrection.base.potion.HiddenPotionBase;
import pers.roinflam.battlecorrection.utils.util.MobTargetUtil;

import javax.annotation.Nonnull;

/**
 * 消灭效果
 * 使生物只攻击附近不同种类的生物
 * <p>
 * 每 5 秒强制换一次目标；目标丢失后最多 1 秒内重新找。
 */
public class EliminationEffect extends HiddenPotionBase {

    /**
     * 构造消灭效果
     *
     * @param isBadEffectIn 是否为负面效果
     * @param liquidColorIn 效果颜色
     * @param name          注册名
     */
    public EliminationEffect(boolean isBadEffectIn, int liquidColorIn, @Nonnull String name) {
        super(isBadEffectIn, liquidColorIn, name);
    }

    /**
     * 每 tick 调用：按需为生物重新挑选攻击目标（仅服务端）
     * <p>
     * 效果会同步给客户端，客户端那份不能再搜索，否则每个带效果的生物每 tick 都会扫一遍。
     *
     * @param entityLivingBaseIn 带有此效果的实体
     * @param amplifier          效果等级
     */
    @Override
    public void performEffect(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {
        if (entityLivingBaseIn instanceof EntityLiving && !entityLivingBaseIn.world.isRemote) {
            EntityLiving mob = (EntityLiving) entityLivingBaseIn;
            if (MobTargetUtil.shouldRetarget(mob)) {
                MobTargetUtil.retargetNearby(mob, true);
            }
        }
    }
}
