package pers.roinflam.battlecorrection.attributes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;

/**
 * 跳跃提升属性
 * 提高跳跃高度，数值约等于额外的跳跃格数（1 ≈ +1格）
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeJumpLift {

    /**
     * 处理跳跃事件以增加跳跃高度
     * <p>
     * 属性值统一通过 AttributesUtil 读取，包含饰品栏物品上的原版属性修饰符
     * （旧版本这里直接读属性实例，饰品栏那部分加成算不进来）。
     *
     * @param evt 生物跳跃事件
     */
    @SubscribeEvent
    public static void onLivingJump(@Nonnull LivingEvent.LivingJumpEvent evt) {
        LivingEntity entity = evt.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }

        double attributeBoost = AttributesUtil.getAttributeValue(entity, ModAttributes.JUMP_LIFT.get());
        double configBoost = ConfigAttribute.JUMP_LIFT.get();
        double jumpBoost = attributeBoost + configBoost;

        if (jumpBoost > 0) {
            // 跳跃高度 ≈ 初速度² / 0.16，所以初速度² 加上 0.16 × 格数 就约等于多跳这么多格
            Vec3 motion = entity.getDeltaMovement();
            double newMotionY = Math.sqrt(motion.y * motion.y + 0.16 * jumpBoost);
            entity.setDeltaMovement(motion.x, newMotionY, motion.z);

            // 玩家的移动由客户端计算，需要把新速度推送给客户端
            if (entity instanceof ServerPlayer serverPlayer) {
                serverPlayer.hurtMarked = true;
            }

            if (LogUtil.isDetailed()) {
                LogUtil.debugAttribute("跳跃提升", entity.getName().getString(),
                        attributeBoost, configBoost, jumpBoost);
            }
        }
    }
}
