package pers.roinflam.battlecorrection.event;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.task.SynchronizationTask;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 属性事件监听器
 * 处理实体最大生命值调整
 */
@Mod.EventBusSubscriber
public class AttributesEventListener {
    public static final UUID ID = UUID.fromString("f1322889-6166-78d0-d738-9b12ced95231");
    public static final String NAME = "battlecorrection.maxhealth";

    /**
     * 处理实体加入世界事件 - 调整最大生命值
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(@Nonnull EntityJoinWorldEvent evt) {
        if (!evt.getWorld().isRemote && evt.getEntity() instanceof EntityLivingBase && !(evt.getEntity() instanceof EntityPlayer)) {
            EntityLivingBase entityLivingBase = (EntityLivingBase) evt.getEntity();
            @Nonnull IAttributeInstance attributeInstance = entityLivingBase.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);

            if (ConfigBattle.extraMaxHealth != 0) {
                float originalMaxHealth = entityLivingBase.getMaxHealth();
                attributeInstance.removeModifier(ID);
                attributeInstance.applyModifier(new AttributeModifier(ID, NAME, ConfigBattle.extraMaxHealth, 2));

                new SynchronizationTask(10) {
                    @Override
                    public void run() {
                        float newMaxHealth = entityLivingBase.getMaxHealth();
                        float healthDifference = newMaxHealth - originalMaxHealth;
                        entityLivingBase.heal(healthDifference);
                        entityLivingBase.setHealth(Math.min(entityLivingBase.getHealth(), newMaxHealth));

                        LogUtil.debugEvent("实体生命值调整", entityLivingBase.getName(),
                                String.format("原始最大生命: %.2f, 调整后最大生命: %.2f (倍率: %.2fx, 增加: %.2f)",
                                        originalMaxHealth, newMaxHealth, 1 + ConfigBattle.extraMaxHealth, healthDifference));
                    }
                }.start();
            } else {
                attributeInstance.removeModifier(ID);
            }
        }
    }
}