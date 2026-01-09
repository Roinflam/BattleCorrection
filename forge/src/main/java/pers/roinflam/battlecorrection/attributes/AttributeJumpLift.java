package pers.roinflam.battlecorrection.attributes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
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
import java.util.UUID;

/**
 * 跳跃提升属性
 * 提高跳跃高度，参考原版跳跃提升效果：0.75级 = 0.1值
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeJumpLift {
    public static final UUID ID = UUID.fromString("b448d2b1-9c3d-bf3d-efdb-59681992bc4f");
    public static final String NAME = "battlecorrection.jumpLift";

    /**
     * 处理跳跃事件以增加跳跃高度
     * jumpBoost 代表增加的格数
     */
    @SubscribeEvent
    public static void onLivingJump(@Nonnull LivingEvent.LivingJumpEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            LivingEntity entityLivingBase = evt.getEntity();

            AttributeInstance attributeInstance = entityLivingBase.getAttribute(ModAttributes.JUMP_LIFT.get());
            double jumpBoost = 0;

            if (attributeInstance != null) {
                jumpBoost = attributeInstance.getValue();
            } else {
                jumpBoost = AttributesUtil.getAttributeValue(entityLivingBase, ModAttributes.JUMP_LIFT.get(), 0.0);
            }

            double configBoost = ConfigAttribute.JUMP_LIFT.get();
            jumpBoost += configBoost;

            if (jumpBoost > 0) {
                Vec3 motion = entityLivingBase.getDeltaMovement();
                double currentMotionY = motion.y;
                double newMotionY = Math.sqrt(currentMotionY * currentMotionY + 0.16 * jumpBoost);

                entityLivingBase.setDeltaMovement(motion.x, newMotionY, motion.z);

                if (entityLivingBase instanceof ServerPlayer serverPlayer) {
                    serverPlayer.hurtMarked = true;
                }

                LogUtil.debugAttribute("跳跃提升", entityLivingBase.getName().getString(),
                        jumpBoost - configBoost, configBoost, jumpBoost);
            }
        }
    }
}