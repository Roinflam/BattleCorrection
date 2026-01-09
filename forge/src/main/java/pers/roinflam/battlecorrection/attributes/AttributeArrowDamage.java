package pers.roinflam.battlecorrection.attributes;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 箭矢伤害加成属性
 * 增加弓箭攻击造成的伤害
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeArrowDamage {
    public static final UUID ID = UUID.fromString("821b6535-b592-6d97-6e89-78d1f76dd7f2");
    public static final String NAME = "battlecorrection.arrowDamage";

    /**
     * 处理箭矢伤害事件
     * 当实体被箭矢击中时触发
     */
    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getDirectEntity() instanceof AbstractArrow && damageSource.getEntity() instanceof @Nullable LivingEntity attacker) {
                @Nullable LivingEntity victim = evt.getEntity();

                float originalAmount = evt.getAmount();
                float attributeValue = (float) AttributesUtil.getAttributeValue(attacker, ModAttributes.ARROW_DAMAGE.get());
                float configDamage = ConfigAttribute.ARROW_DAMAGE.get().floatValue();
                float newAmount = (float) AttributesUtil.getAttributeValue(attacker, ModAttributes.ARROW_DAMAGE.get(), originalAmount + configDamage);

                LogUtil.debugAttribute("箭矢伤害", attacker.getName().getString(), attributeValue, configDamage, newAmount - originalAmount);
                LogUtil.debugDamage("箭矢攻击", attacker.getName().getString(), victim.getName().getString(), originalAmount, newAmount,
                        String.format("属性加成: %.2f, 配置加成: %.2f", attributeValue, configDamage));

                evt.setAmount(newAmount);
            }
        }
    }
}