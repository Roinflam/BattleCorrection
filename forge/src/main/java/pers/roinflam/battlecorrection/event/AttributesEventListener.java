package pers.roinflam.battlecorrection.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 属性事件监听器
 * 处理实体最大生命值调整
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributesEventListener {

    public static final UUID MAX_HEALTH_ID = UUID.fromString("f1322889-6166-78d0-d738-9b12ced95231");
    public static final String MAX_HEALTH_NAME = "battlecorrection.maxhealth";

    /**
     * 处理实体加入世界事件 - 调整最大生命值
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(@Nonnull EntityJoinLevelEvent evt) {
        if (!evt.getLevel().isClientSide() &&
                evt.getEntity() instanceof LivingEntity entity &&
                !(evt.getEntity() instanceof Player)) {

            AttributeInstance attributeInstance = entity.getAttribute(Attributes.MAX_HEALTH);

            if (attributeInstance != null) {
                double extraMaxHealth = ConfigBattle.EXTRA_MAX_HEALTH.get();

                if (extraMaxHealth != 0) {
                    float originalMaxHealth = entity.getMaxHealth();

                    // 移除旧的修改器
                    attributeInstance.removeModifier(MAX_HEALTH_ID);

                    // 添加新的修改器（使用乘法运算）
                    // 这里必须用永久修饰符（会写进存档）：读档时原版先载入属性、再载入血量，血量会被截断到当时的最大生命。
                    // 如果改用临时修饰符，怪物每次重新加载都会先被截成原版血量上限，再被下面的"补差值"直接回满血。
                    attributeInstance.addPermanentModifier(new AttributeModifier(
                            MAX_HEALTH_ID,
                            MAX_HEALTH_NAME,
                            extraMaxHealth,
                            AttributeModifier.Operation.MULTIPLY_TOTAL
                    ));

                    // 计算新的最大生命值并治疗差值
                    float newMaxHealth = entity.getMaxHealth();
                    float healthDifference = newMaxHealth - originalMaxHealth;

                    if (healthDifference > 0) {
                        entity.heal(healthDifference);
                    }

                    // 确保当前生命值不超过最大生命值
                    entity.setHealth(Math.min(entity.getHealth(), newMaxHealth));

                    // 先判断再拼接，关闭详细日志时不产生临时字符串（区块加载时这里调用很频繁）
                    if (LogUtil.isDetailed()) {
                        LogUtil.debugEvent("实体生命值调整", entity.getName().getString(),
                                String.format("原始最大生命: %.2f, 调整后最大生命: %.2f (倍率: %.2fx, 增加: %.2f)",
                                        originalMaxHealth, newMaxHealth, 1 + extraMaxHealth, healthDifference));
                    }
                } else {
                    // 移除修改器
                    attributeInstance.removeModifier(MAX_HEALTH_ID);
                }
            }
        }
    }
}
