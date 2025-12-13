package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 跳跃提升属性
 * 提高跳跃高度，参考原版跳跃提升效果：0.75级 = 0.1值
 */
@Mod.EventBusSubscriber
public class AttributeJumpLift {
    public static final UUID ID = UUID.fromString("b448d2b1-9c3d-bf3d-efdb-59681992bc4f");
    public static final String NAME = "battlecorrection.jumpLift";

    public static final IAttribute JUMP_LIFT = (new RangedAttribute(null, NAME, 0, 0, Float.MAX_VALUE)).setDescription("Jump Lift");

    /**
     * 处理跳跃事件以增加跳跃高度
     */
    @SubscribeEvent
    public static void onLivingJump(@Nonnull LivingEvent.LivingJumpEvent evt) {
        new SynchronizationTask(1) {
            @Override
            public void run() {
                EntityLivingBase entityLivingBase = evt.getEntityLiving();

                IAttributeInstance attributeInstance = entityLivingBase.getAttributeMap().getAttributeInstance(JUMP_LIFT);
                double jumpBoost = 0;

                if (attributeInstance != null) {
                    jumpBoost = attributeInstance.getAttributeValue();
                } else {
                    jumpBoost = pers.roinflam.battlecorrection.utils.util.AttributesUtil.getAttributeValue(entityLivingBase, JUMP_LIFT, 0.0);
                }

                double configBoost = ConfigAttribute.jumpLift;
                jumpBoost += configBoost;

                if (jumpBoost > 0) {
                    entityLivingBase.motionY += jumpBoost;

                    LogUtil.debugAttribute("跳跃提升", entityLivingBase.getName(),
                            jumpBoost - configBoost, configBoost, jumpBoost);
                    LogUtil.debugEvent("跳跃增强触发", entityLivingBase.getName(),
                            String.format("额外跳跃高度: %.3f (相当于 %.2f 级跳跃提升效果)",
                                    jumpBoost, jumpBoost / 0.1 * 0.75));
                }
            }
        }.start();
    }
}