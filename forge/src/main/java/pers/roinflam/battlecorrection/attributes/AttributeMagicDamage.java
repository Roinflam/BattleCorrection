package pers.roinflam.battlecorrection.attributes;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
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
 * 魔法伤害加成属性
 * 增加魔法攻击造成的伤害，如药水伤害或模组中的法杖
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeMagicDamage {
    public static final UUID ID = UUID.fromString("af42b33d-f767-b71d-9ca4-92fc0ce0f04a");
    public static final String NAME = "battlecorrection.magicDamage";

    /**
     * 处理魔法伤害事件
     * 当实体受到魔法伤害时触发
     */
    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.is(DamageTypeTags.WITCH_RESISTANT_TO) && damageSource.getEntity() instanceof @Nullable LivingEntity attacker) {
                @Nullable LivingEntity victim = evt.getEntity();

                float originalAmount = evt.getAmount();
                float attributeValue = (float) AttributesUtil.getAttributeValue(attacker, ModAttributes.MAGIC_DAMAGE.get());
                float configDamage = ConfigAttribute.MAGIC_DAMAGE.get().floatValue();
                float newAmount = (float) AttributesUtil.getAttributeValue(attacker, ModAttributes.MAGIC_DAMAGE.get(), originalAmount + configDamage);

                LogUtil.debugAttribute("魔法伤害", attacker.getName().getString(), attributeValue, configDamage, newAmount - originalAmount);
                LogUtil.debugDamage("魔法攻击", attacker.getName().getString(), victim.getName().getString(), originalAmount, newAmount,
                        String.format("属性加成: %.2f, 配置加成: %.2f", attributeValue, configDamage));

                evt.setAmount(newAmount);
            }
        }
    }
}