// 文件：AttributeJumpLift.java
// 路径：src/main/java/pers/roinflam/battlecorrection/attributes/AttributeJumpLift.java
package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;

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

    public static final IAttribute JUMP_LIFT = ModAttributes.JUMP_LIFT;

    /**
     * 处理跳跃事件以增加跳跃高度
     * jumpBoost 代表增加的格数
     */
    @SubscribeEvent
    public static void onLivingJump(@Nonnull LivingEvent.LivingJumpEvent evt) {
        // ✅ 只在服务端执行
        if (!evt.getEntity().world.isRemote) {
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
                // ✅ 正确的物理计算：增加格数 → 增加 motionY
                // 跳跃高度 h = motionY² / (2g)，其中 g ≈ 0.08
                // 当前高度 h1 = motionY² / 0.16
                // 目标高度 h2 = h1 + jumpBoost
                // 新的 motionY = sqrt(motionY² + 0.16 × jumpBoost)

                double currentMotionY = entityLivingBase.motionY;
                entityLivingBase.motionY = Math.sqrt(
                        currentMotionY * currentMotionY + 0.16 * jumpBoost
                );

                // ✅ 发送速度包到客户端（关键！）
                if (entityLivingBase instanceof EntityPlayerMP) {
                    EntityPlayerMP playerMP = (EntityPlayerMP) entityLivingBase;
                    playerMP.connection.sendPacket(
                            new net.minecraft.network.play.server.SPacketEntityVelocity(
                                    entityLivingBase.getEntityId(),
                                    entityLivingBase.motionX,
                                    entityLivingBase.motionY,
                                    entityLivingBase.motionZ
                            )
                    );
                }

                LogUtil.debugAttribute("跳跃提升", entityLivingBase.getName(),
                        jumpBoost - configBoost, configBoost, jumpBoost);
            }
        }
    }
}