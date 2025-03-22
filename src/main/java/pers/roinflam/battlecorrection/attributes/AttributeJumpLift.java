package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;
import java.util.UUID;

@Mod.EventBusSubscriber
public class AttributeJumpLift {
    public static final UUID ID = UUID.fromString("b448d2b1-9c3d-bf3d-efdb-59681992bc4f");
    public static final String NAME = "battlecorrection.jumpLift";

    public static final IAttribute JUMP_LIFT = (new RangedAttribute(null, NAME, 0, 0, Float.MAX_VALUE)).setDescription("Jump Lift");

    @SubscribeEvent
    public static void onLivingJump(@Nonnull LivingEvent.LivingJumpEvent evt) {
        new SynchronizationTask(1) {
            @Override
            public void run() {
                EntityLivingBase entityLivingBase = evt.getEntityLiving();

                // 获取注册的属性实例
                IAttributeInstance attributeInstance = entityLivingBase.getAttributeMap().getAttributeInstance(JUMP_LIFT);
                double jumpBoost = 0;

                // 如果属性已注册，使用它的值
                if (attributeInstance != null) {
                    jumpBoost = attributeInstance.getAttributeValue();
                }
                // 否则使用原始计算方法
                else {
                    jumpBoost = pers.roinflam.battlecorrection.utils.util.AttributesUtil.getAttributeValue(entityLivingBase, JUMP_LIFT, 0.0);
                }

                // 添加配置中的跳跃提升值
                jumpBoost += ConfigAttribute.jumpLift;

                // 将修改后的跳跃力应用到实体
                if (jumpBoost > 0) {
                    entityLivingBase.motionY += jumpBoost;
                }
            }
        }.start();
    }
}